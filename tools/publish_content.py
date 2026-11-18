#!/usr/bin/env python3
"""Публикация контента стенда AEROCELL в Yandex Object Storage.

Сканирует папку с audio/, video/, apk/ (и необязательным content.json),
считает sha256 и размер, собирает manifest.json и заливает объекты в бакет.

Учётные данные только из окружения или --env-file. В лог и в репозиторий
ключи не пишутся.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

MSK = timezone(timedelta(hours=3))

DEFAULT_ENV_CANDIDATES = [
    Path("/home/box/secrets/aerocell-s3.env"),
]

GENRE_BY_STEM = {
    "club": "CLUB",
    "rock": "ROCK",
    "classic": "CLASSIC",
    "classical": "CLASSIC",
}

TITLE_BY_STEM = {
    "club": "Alright",
    "rock": "Big Rock",
    "classic": "Вивальди, Весна",
    "classical": "Вивальди, Весна",
}


def load_env_file(path: Path) -> None:
    if not path.is_file():
        raise SystemExit(f"Файл с ключами не найден: {path}")
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise SystemExit(f"Не задана переменная окружения {name}")
    return value


def sha256_file(path: Path) -> str:
    md = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(64 * 1024), b""):
            md.update(chunk)
    return md.hexdigest()


def content_type_for(path: Path) -> str:
    suffix = path.suffix.lower()
    mapping = {
        ".mp3": "audio/mpeg",
        ".wav": "audio/wav",
        ".m4a": "audio/mp4",
        ".mp4": "video/mp4",
        ".webm": "video/webm",
        ".apk": "application/vnd.android.package-archive",
        ".json": "application/json",
        ".png": "image/png",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
    }
    if suffix in mapping:
        return mapping[suffix]
    guessed, _ = mimetypes.guess_type(path.name)
    return guessed or "application/octet-stream"


def infer_genre(stem: str) -> str | None:
    return GENRE_BY_STEM.get(stem.lower())


def public_url(base: str, key: str) -> str:
    return base.rstrip("/") + "/" + key.lstrip("/")


def fetch_content_version(url: str) -> int | None:
    try:
        req = Request(url, headers={"Accept": "application/json", "Cache-Control": "no-cache"})
        with urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        version = data.get("contentVersion")
        return int(version) if version is not None else None
    except (HTTPError, URLError, TimeoutError, ValueError, json.JSONDecodeError, OSError):
        return None


def s3_client(endpoint: str, region: str):
    try:
        import boto3
    except ImportError:
        raise SystemExit(
            "Нужен boto3. Установите: python -m pip install boto3\n"
            "или используйте виртуальное окружение."
        )
    return boto3.client(
        "s3",
        endpoint_url=endpoint,
        region_name=region,
        aws_access_key_id=required_env("AWS_ACCESS_KEY_ID"),
        aws_secret_access_key=required_env("AWS_SECRET_ACCESS_KEY"),
    )


def load_meta(source: Path) -> dict[str, Any]:
    for name in ("content.json", "config.json"):
        path = source / name
        if path.is_file():
            return json.loads(path.read_text(encoding="utf-8"))
    return {}


def collect_files(source: Path, meta: dict[str, Any]) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    track_meta = meta.get("tracks") or meta.get("audio") or {}
    video_meta = meta.get("videos") or meta.get("video") or {}

    audio_dir = source / "audio"
    if audio_dir.is_dir():
        for path in sorted(audio_dir.iterdir()):
            if not path.is_file() or path.name.startswith("."):
                continue
            extra = track_meta.get(path.name) or track_meta.get(path.stem) or {}
            genre = extra.get("genre") or infer_genre(path.stem)
            items.append(
                {
                    "id": extra.get("id") or f"track-{path.stem.lower()}",
                    "type": "audio",
                    "role": extra.get("role") or "track",
                    "title": extra.get("title") or TITLE_BY_STEM.get(path.stem.lower()),
                    "genre": genre,
                    "local": path,
                    "key": f"audio/{path.name}",
                }
            )

    video_dir = source / "video"
    if video_dir.is_dir():
        for path in sorted(video_dir.iterdir()):
            if not path.is_file() or path.name.startswith("."):
                continue
            extra = video_meta.get(path.name) or video_meta.get(path.stem) or {}
            items.append(
                {
                    "id": extra.get("id") or f"idle-{path.stem.lower()}",
                    "type": "video",
                    "role": extra.get("role") or "idle",
                    "title": extra.get("title") or path.stem,
                    "genre": extra.get("genre"),
                    "local": path,
                    "key": f"video/{path.name}",
                }
            )
    return items


def find_apk(source: Path, meta: dict[str, Any]) -> dict[str, Any] | None:
    apk_meta = meta.get("apk") or {}
    explicit = apk_meta.get("file")
    path = None
    if explicit:
        path = Path(explicit)
        if not path.is_absolute():
            path = source / path
    else:
        apk_dir = source / "apk"
        if apk_dir.is_dir():
            apks = sorted(p for p in apk_dir.iterdir() if p.suffix.lower() == ".apk")
            if apks:
                path = apks[-1]
    if path is None or not path.is_file():
        return None
    return {
        "local": path,
        "key": f"apk/{path.name}",
        "versionCode": int(apk_meta.get("versionCode") or 0),
        "versionName": str(apk_meta.get("versionName") or path.stem.replace("AEROCELL-", "")),
        "applicationId": apk_meta.get("applicationId") or "com.aerocell.stand",
    }


def upload(client, bucket: str, key: str, path: Path, cache: str) -> None:
    extra: dict[str, Any] = {
        "ContentType": content_type_for(path),
        "CacheControl": cache,
    }
    client.upload_file(str(path), bucket, key, ExtraArgs=extra)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Публикация контента AEROCELL в Object Storage")
    parser.add_argument("--source", type=Path, required=True, help="Папка с audio/, video/, apk/ и content.json")
    parser.add_argument("--env-file", type=Path, help="Файл с ключами AWS_* / BUCKET / ENDPOINT")
    parser.add_argument("--content-version", type=int, help="Явная версия контента, иначе текущая+1")
    parser.add_argument("--dry-run", action="store_true", help="Только собрать манифест, ничего не заливать")
    parser.add_argument("--no-apk", action="store_true", help="Не включать APK в манифест")
    args = parser.parse_args(argv)

    source = args.source.expanduser().resolve()
    if not source.is_dir():
        raise SystemExit(f"Папка не найдена: {source}")

    env_file = args.env_file
    if env_file is None:
        env_hint = os.environ.get("AEROCELL_S3_ENV")
        if env_hint:
            env_file = Path(env_hint)
        else:
            env_file = next((p for p in DEFAULT_ENV_CANDIDATES if p.is_file()), None)
    if env_file is not None:
        load_env_file(env_file)

    bucket = required_env("BUCKET")
    endpoint = os.environ.get("ENDPOINT", "https://storage.yandexcloud.net").strip()
    region = os.environ.get("AWS_DEFAULT_REGION", "ru-central1").strip()
    public_base = os.environ.get("PUBLIC_BASE_URL", f"{endpoint.rstrip('/')}/{bucket}").strip()

    meta = load_meta(source)
    files = collect_files(source, meta)
    apk = None if args.no_apk else find_apk(source, meta)
    if not files and apk is None:
        raise SystemExit("В папке нет audio/, video/ или apk/ для публикации")

    current = args.content_version
    if current is None:
        fetched = fetch_content_version(public_url(public_base, "manifest.json"))
        current = 1 if fetched is None else fetched + 1

    published_at = datetime.now(MSK).replace(microsecond=0).isoformat()
    manifest_files = []
    uploads: list[tuple[str, Path, str]] = []

    for item in files:
        path: Path = item["local"]
        digest = sha256_file(path)
        size = path.stat().st_size
        entry = {
            "id": item["id"],
            "type": item["type"],
            "role": item["role"],
            "url": public_url(public_base, item["key"]),
            "sha256": digest,
            "size": size,
        }
        if item.get("title"):
            entry["title"] = item["title"]
        if item.get("genre"):
            entry["genre"] = item["genre"]
        manifest_files.append(entry)
        cache = "public, max-age=31536000, immutable"
        uploads.append((item["key"], path, cache))
        print(f"файл {item['key']}: {size} байт, sha256={digest}")

    apk_block = None
    if apk is not None:
        path = apk["local"]
        digest = sha256_file(path)
        size = path.stat().st_size
        version_code = apk["versionCode"]
        if version_code <= 0:
            raise SystemExit("В content.json у apk нужно указать versionCode")
        apk_block = {
            "applicationId": apk["applicationId"],
            "versionCode": version_code,
            "versionName": apk["versionName"],
            "url": public_url(public_base, apk["key"]),
            "sha256": digest,
            "size": size,
        }
        uploads.append((apk["key"], path, "public, max-age=3600"))
        print(f"apk {apk['key']}: {size} байт, versionCode={version_code}, sha256={digest}")

    manifest = {
        "manifestVersion": 1,
        "contentVersion": current,
        "publishedAt": published_at,
        "minAppVersionCode": int((meta.get("minAppVersionCode") or 1)),
        "files": manifest_files,
    }
    if apk_block is not None:
        manifest["apk"] = apk_block
    if meta.get("config"):
        manifest["config"] = meta["config"]

    out_manifest = source / "manifest.json"
    out_manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"манифест: contentVersion={current}, файлов={len(manifest_files)}, записан в {out_manifest}")

    if args.dry_run:
        print("dry-run: загрузка пропущена")
        return 0

    client = s3_client(endpoint, region)
    for key, path, cache in uploads:
        print(f"загружаю {key}")
        upload(client, bucket, key, path, cache)
    print("загружаю manifest.json")
    upload(client, bucket, "manifest.json", out_manifest, "public, max-age=60, must-revalidate")
    print(f"готово: {public_url(public_base, 'manifest.json')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
