"""AEROCELL visitor-track upload function.

Public HTTP API. S3 keys come from the environment / Lockbox, never from the client.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import re
import secrets
import time
import uuid
from typing import Any, Optional
from urllib.parse import parse_qs, urlparse

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

BUCKET = os.environ.get("BUCKET", "aerocell-stand")
ENDPOINT = os.environ.get("ENDPOINT", "https://storage.yandexcloud.net")
REGION = os.environ.get("AWS_DEFAULT_REGION", "ru-central1")
PUBLIC_BASE = os.environ.get("PUBLIC_BASE_URL", "https://storage.yandexcloud.net/aerocell-stand/").rstrip("/") + "/"
PAGE_URL_BASE = os.environ.get(
    "PAGE_URL_BASE", PUBLIC_BASE + "u/index.html"
)
MAX_BYTES = int(os.environ.get("MAX_BYTES", str(50 * 1024 * 1024)))
UPLOAD_URL_TTL = int(os.environ.get("UPLOAD_URL_TTL_SEC", "600"))
SESSION_TTL = int(os.environ.get("SESSION_TTL_SEC", "1800"))
TOKEN_LENGTH = int(os.environ.get("TOKEN_LENGTH", "20"))

ALLOWED_EXT = {"mp3", "m4a", "aac", "wav", "flac", "ogg"}
EXT_MIME = {
    "mp3": "audio/mpeg",
    "m4a": "audio/mp4",
    "aac": "audio/aac",
    "wav": "audio/wav",
    "flac": "audio/flac",
    "ogg": "audio/ogg",
}
ALLOWED_MIME = {
    "audio/mpeg",
    "audio/mp3",
    "audio/mp4",
    "audio/x-m4a",
    "audio/aac",
    "audio/wav",
    "audio/x-wav",
    "audio/wave",
    "audio/flac",
    "audio/x-flac",
    "audio/ogg",
    "audio/vorbis",
    "application/ogg",
}

CORS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, X-Stand-Secret",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Max-Age": "3600",
    "Cache-Control": "no-store",
}

TOKEN_RE = re.compile(r"^[A-Za-z0-9]{16,64}$")
SESSION_RE = re.compile(r"^[A-Za-z0-9-]{8,64}$")
SAFE_NAME_RE = re.compile(r"[^A-Za-z0-9._ ()\-\[\]]+")

_s3 = None


def s3():
    global _s3
    if _s3 is None:
        _s3 = boto3.client(
            "s3",
            aws_access_key_id=os.environ["AWS_ACCESS_KEY_ID"],
            aws_secret_access_key=os.environ["AWS_SECRET_ACCESS_KEY"],
            endpoint_url=ENDPOINT,
            region_name=REGION,
            config=Config(signature_version="s3v4", s3={"addressing_style": "path"}),
        )
    return _s3


def stand_secret() -> str:
    return os.environ.get("STAND_SECRET") or ""


def json_response(status: int, body: Any, extra_headers: Optional[dict] = None) -> dict:
    headers = {"Content-Type": "application/json; charset=utf-8", **CORS}
    if extra_headers:
        headers.update(extra_headers)
    return {
        "statusCode": status,
        "headers": headers,
        "body": json.dumps(body, ensure_ascii=False),
    }


def text_response(status: int, body: str, content_type: str) -> dict:
    return {
        "statusCode": status,
        "headers": {"Content-Type": content_type, **CORS},
        "body": body,
    }


def event_method(event: dict) -> str:
    return (
        event.get("httpMethod")
        or event.get("http_method")
        or (event.get("requestContext") or {}).get("httpMethod")
        or "GET"
    ).upper()


def event_path(event: dict) -> str:
    path = event.get("path") or event.get("pathInfo") or ""
    if not path:
        url = event.get("url") or event.get("originalUrl") or ""
        if url:
            path = urlparse(url).path
    # Drop a leading function-id segment if the platform includes it.
    parts = [p for p in path.split("/") if p]
    if parts and re.fullmatch(r"[a-z0-9]{20,}", parts[0]) and len(parts) > 1:
        parts = parts[1:]
    return "/" + "/".join(parts) if parts else "/"


def event_query(event: dict) -> dict:
    qs = event.get("queryStringParameters") or event.get("query") or {}
    if isinstance(qs, dict):
        return {str(k): (v if isinstance(v, str) else (v[0] if v else "")) for k, v in qs.items()}
    url = event.get("url") or ""
    if url and "?" in url:
        parsed = parse_qs(urlparse(url).query, keep_blank_values=True)
        return {k: (v[0] if v else "") for k, v in parsed.items()}
    return {}


def event_headers(event: dict) -> dict:
    raw = event.get("headers") or {}
    return {str(k).lower(): v for k, v in raw.items()}


def event_body(event: dict) -> dict:
    body = event.get("body")
    if not body:
        return {}
    if event.get("isBase64Encoded"):
        import base64

        body = base64.b64decode(body).decode("utf-8", errors="replace")
    if isinstance(body, (bytes, bytearray)):
        body = body.decode("utf-8", errors="replace")
    if isinstance(body, dict):
        return body
    try:
        data = json.loads(body)
        return data if isinstance(data, dict) else {}
    except json.JSONDecodeError:
        return {}


def header_secret(headers: dict) -> str:
    return headers.get("x-stand-secret") or headers.get("x-stand_secret") or ""


def secrets_equal(expected: str, actual: str) -> bool:
    if not expected or not actual:
        return False
    if len(expected) != len(actual):
        # still compare to keep timing flatter
        hmac.compare_digest(expected, expected)
        return False
    return hmac.compare_digest(expected, actual)


def require_stand(headers: dict) -> Optional[dict]:
    if secrets_equal(stand_secret(), header_secret(headers)):
        return None
    return json_response(401, {"ok": False, "error": "auth"})


def new_token() -> str:
    alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return "".join(secrets.choice(alphabet) for _ in range(TOKEN_LENGTH))


def extension_of(filename: str) -> Optional[str]:
    base = filename.replace("\\", "/").split("/")[-1]
    if "." not in base or base.startswith("."):
        return None
    ext = base.rsplit(".", 1)[-1].lower()
    return ext if ext in ALLOWED_EXT else None


def safe_filename(name: str, ext: str) -> str:
    base = name.replace("\\", "/").split("/")[-1].strip()
    if not base:
        base = f"track.{ext}"
    base = SAFE_NAME_RE.sub("_", base)[:180]
    if not base.lower().endswith("." + ext):
        stem = base.rsplit(".", 1)[0] if "." in base else base
        base = f"{stem}.{ext}"
    return base or f"track.{ext}"


def mime_for(ext: str, provided: Optional[str]) -> str:
    provided_norm = (provided or "").split(";")[0].strip().lower()
    if provided_norm in ALLOWED_MIME:
        return provided_norm
    return EXT_MIME.get(ext, "application/octet-stream")


def key_session(session_id: str) -> str:
    return f"uploads/sessions/{session_id}.json"


def key_token(token: str) -> str:
    return f"uploads/tokens/{token}"


def key_file(session_id: str, file_id: str, ext: str) -> str:
    return f"uploads/files/{session_id}/{file_id}.{ext}"


def put_json(key: str, data: dict) -> None:
    s3().put_object(
        Bucket=BUCKET,
        Key=key,
        Body=json.dumps(data, ensure_ascii=False).encode("utf-8"),
        ContentType="application/json",
        ACL="private",
    )


def put_bytes(key: str, data: bytes, content_type: str) -> None:
    s3().put_object(
        Bucket=BUCKET,
        Key=key,
        Body=data,
        ContentType=content_type,
        ACL="private",
    )


def get_json(key: str) -> Optional[dict]:
    try:
        resp = s3().get_object(Bucket=BUCKET, Key=key)
        return json.loads(resp["Body"].read().decode("utf-8"))
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "")
        if code in {"NoSuchKey", "404", "NotFound"}:
            return None
        raise


def delete_keys(keys: list[str]) -> None:
    keys = [k for k in keys if k]
    if not keys:
        return
    s3().delete_objects(
        Bucket=BUCKET,
        Delete={"Objects": [{"Key": k} for k in keys], "Quiet": True},
    )


def load_session(session_id: str) -> Optional[dict]:
    if not SESSION_RE.fullmatch(session_id or ""):
        return None
    return get_json(key_session(session_id))


def load_session_by_token(token: str) -> Optional[dict]:
    if not TOKEN_RE.fullmatch(token or ""):
        return None
    pointer = get_json(key_token(token))
    if not pointer:
        # pointer stored as raw session id
        try:
            resp = s3().get_object(Bucket=BUCKET, Key=key_token(token))
            raw = resp["Body"].read().decode("utf-8").strip()
            if raw.startswith("{"):
                pointer = json.loads(raw)
            else:
                pointer = {"sessionId": raw}
        except ClientError:
            return None
    session_id = (pointer or {}).get("sessionId") or ""
    return load_session(session_id)


def expired(meta: dict, now: Optional[float] = None) -> bool:
    now = now or time.time()
    return float(meta.get("expiresAt") or 0) <= now


def page_html() -> str:
    here = os.path.join(os.path.dirname(__file__), "page.html")
    with open(here, "r", encoding="utf-8") as f:
        html = f.read()
    function_base = os.environ.get("FUNCTION_BASE_URL", "").rstrip("/")
    return html.replace("__FUNCTION_BASE_URL__", function_base)


def presign_put(key: str, content_type: str, size: int) -> str:
    # Sign only Content-Type. Content-Length and ACL must not be signed:
    # browsers/simple clients will not send x-amz-acl, and a mismatch yields 403.
    # Size is still checked before issuing the URL and again on complete().
    return s3().generate_presigned_url(
        ClientMethod="put_object",
        Params={
            "Bucket": BUCKET,
            "Key": key,
            "ContentType": content_type,
        },
        ExpiresIn=UPLOAD_URL_TTL,
        HttpMethod="PUT",
    )


def presign_get(key: str) -> str:
    return s3().generate_presigned_url(
        ClientMethod="get_object",
        Params={"Bucket": BUCKET, "Key": key},
        ExpiresIn=UPLOAD_URL_TTL,
        HttpMethod="GET",
    )


def object_exists(key: str) -> bool:
    try:
        s3().head_object(Bucket=BUCKET, Key=key)
        return True
    except ClientError:
        return False


def handle_session() -> dict:
    session_id = str(uuid.uuid4())
    token = new_token()
    now = time.time()
    meta = {
        "sessionId": session_id,
        "tokenHash": hashlib.sha256(token.encode()).hexdigest(),
        "status": "waiting",
        "createdAt": now,
        "expiresAt": now + SESSION_TTL,
        "filename": None,
        "extension": None,
        "contentType": None,
        "size": None,
        "objectKey": None,
        "uploadIssuedAt": None,
    }
    put_json(key_session(session_id), meta)
    put_json(key_token(token), {"sessionId": session_id})
    page_url = f"{PAGE_URL_BASE}?t={token}"
    return json_response(
        200,
        {
            "ok": True,
            "sessionId": session_id,
            "token": token,
            "pageUrl": page_url,
            "expiresIn": SESSION_TTL,
        },
    )


def handle_upload_url(token: str, body: dict) -> dict:
    meta = load_session_by_token(token)
    if not meta or expired(meta):
        return json_response(410, {"ok": False, "error": "token"})
    if meta.get("status") == "ready":
        return json_response(410, {"ok": False, "error": "token"})

    filename = str(body.get("filename") or body.get("name") or "")
    size = body.get("size")
    try:
        size_i = int(size)
    except (TypeError, ValueError):
        return json_response(400, {"ok": False, "error": "size"})
    if size_i <= 0:
        return json_response(400, {"ok": False, "error": "empty"})
    if size_i > MAX_BYTES:
        return json_response(413, {"ok": False, "error": "size"})

    ext = extension_of(filename)
    provided_mime = str(body.get("contentType") or body.get("type") or "")
    if not ext:
        mime_ext = None
        provided_norm = provided_mime.split(";")[0].strip().lower()
        for e, m in EXT_MIME.items():
            if provided_norm == m:
                mime_ext = e
                break
        ext = mime_ext
    if not ext:
        return json_response(415, {"ok": False, "error": "type"})

    safe = safe_filename(filename or f"track.{ext}", ext)
    content_type = mime_for(ext, provided_mime)
    file_id = uuid.uuid4().hex
    object_key = key_file(meta["sessionId"], file_id, ext)
    now = time.time()
    meta.update(
        {
            "status": "waiting",
            "filename": safe,
            "extension": ext,
            "contentType": content_type,
            "size": size_i,
            "objectKey": object_key,
            "uploadIssuedAt": now,
        }
    )
    put_json(key_session(meta["sessionId"]), meta)
    put_url = presign_put(object_key, content_type, size_i)
    return json_response(
        200,
        {
            "ok": True,
            "putUrl": put_url,
            "objectKey": object_key,
            "contentType": content_type,
            "size": size_i,
            "expiresIn": UPLOAD_URL_TTL,
        },
    )


def handle_complete(token: str, body: dict) -> dict:
    meta = load_session_by_token(token)
    if not meta or expired(meta):
        return json_response(410, {"ok": False, "error": "token"})
    object_key = meta.get("objectKey") or body.get("objectKey")
    if not object_key or not str(object_key).startswith(f"uploads/files/{meta['sessionId']}/"):
        return json_response(400, {"ok": False, "error": "object"})
    try:
        head = s3().head_object(Bucket=BUCKET, Key=object_key)
    except ClientError:
        return json_response(404, {"ok": False, "error": "missing"})
    actual = int(head.get("ContentLength") or 0)
    if actual <= 0:
        delete_keys([object_key])
        return json_response(400, {"ok": False, "error": "empty"})
    if actual > MAX_BYTES:
        delete_keys([object_key])
        return json_response(413, {"ok": False, "error": "size"})
    meta["size"] = actual
    meta["status"] = "ready"
    meta["objectKey"] = object_key
    put_json(key_session(meta["sessionId"]), meta)
    # one-time token: drop the token pointer so the page cannot upload again
    token_key = key_token(token)
    delete_keys([token_key])
    return json_response(200, {"ok": True})


def refresh_ready(meta: dict) -> dict:
    if meta.get("status") == "ready":
        return meta
    object_key = meta.get("objectKey")
    if object_key and object_exists(object_key):
        meta["status"] = "ready"
        put_json(key_session(meta["sessionId"]), meta)
    return meta


def handle_status(session_id: str) -> dict:
    meta = load_session(session_id)
    if not meta:
        return json_response(404, {"ok": False, "error": "missing", "status": "missing"})
    if expired(meta) and meta.get("status") != "ready":
        return json_response(200, {"ok": True, "status": "expired"})
    meta = refresh_ready(meta)
    body = {
        "ok": True,
        "status": meta.get("status") or "waiting",
        "filename": meta.get("filename"),
        "extension": meta.get("extension"),
        "size": meta.get("size"),
    }
    return json_response(200, body)


def handle_download_url(session_id: str) -> dict:
    meta = load_session(session_id)
    if not meta:
        return json_response(404, {"ok": False, "error": "missing"})
    meta = refresh_ready(meta)
    if meta.get("status") != "ready" or not meta.get("objectKey"):
        return json_response(409, {"ok": False, "error": "not_ready", "status": meta.get("status")})
    url = presign_get(meta["objectKey"])
    return json_response(
        200,
        {
            "ok": True,
            "url": url,
            "filename": meta.get("filename"),
            "extension": meta.get("extension"),
            "size": meta.get("size"),
        },
    )


def handle_delete(session_id: str) -> dict:
    meta = load_session(session_id) or {}
    keys = [key_session(session_id)]
    if meta.get("objectKey"):
        keys.append(meta["objectKey"])
    # token pointer may already be gone
    delete_keys(keys)
    # try to also remove leftover files under the session prefix
    try:
        prefix = f"uploads/files/{session_id}/"
        resp = s3().list_objects_v2(Bucket=BUCKET, Prefix=prefix)
        extra = [o["Key"] for o in resp.get("Contents") or []]
        delete_keys(extra)
    except ClientError:
        pass
    return json_response(200, {"ok": True})


def last_segment(path: str) -> str:
    parts = [p for p in path.split("/") if p]
    return parts[-1].lower() if parts else ""


def handler(event, context):
    event = event or {}
    method = event_method(event)
    path = event_path(event)
    query = event_query(event)
    headers = event_headers(event)
    body = event_body(event)
    action = (query.get("action") or body.get("action") or last_segment(path) or "").lower()

    if method == "OPTIONS":
        return json_response(204, {"ok": True})

    if method == "GET" and (path in {"/", "/u", "/u/", "/u/index.html"} or action in {"u", "page", ""}):
        if "t" in query or path.startswith("/u"):
            try:
                return text_response(200, page_html(), "text/html; charset=utf-8")
            except FileNotFoundError:
                pass

    if method == "GET" and action in {"health", "ping"}:
        return json_response(200, {"ok": True})

    try:
        if method == "POST" and action in {"session", "sessions"}:
            denied = require_stand(headers)
            if denied:
                return denied
            return handle_session()

        if method in {"GET", "POST"} and action in {"upload-url", "upload_url", "uploadurl"}:
            token = query.get("token") or body.get("token") or ""
            if not token:
                return json_response(400, {"ok": False, "error": "token"})
            return handle_upload_url(token, body if body else query)

        if method == "POST" and action in {"complete", "done"}:
            token = query.get("token") or body.get("token") or ""
            if not token:
                return json_response(400, {"ok": False, "error": "token"})
            return handle_complete(token, body)

        if method == "GET" and action in {"status"}:
            denied = require_stand(headers)
            if denied:
                return denied
            session_id = query.get("session") or query.get("sessionId") or ""
            if not session_id:
                return json_response(400, {"ok": False, "error": "session"})
            return handle_status(session_id)

        if method in {"GET", "POST"} and action in {"download-url", "download_url", "download"}:
            denied = require_stand(headers)
            if denied:
                return denied
            session_id = query.get("session") or body.get("session") or body.get("sessionId") or ""
            if not session_id:
                return json_response(400, {"ok": False, "error": "session"})
            return handle_download_url(session_id)

        if method in {"POST", "DELETE"} and action in {"delete", "session-delete"}:
            denied = require_stand(headers)
            if denied:
                return denied
            session_id = query.get("session") or body.get("session") or body.get("sessionId") or ""
            if not session_id:
                return json_response(400, {"ok": False, "error": "session"})
            return handle_delete(session_id)

        return json_response(404, {"ok": False, "error": "not_found"})
    except Exception:
        # Do not leak internals
        return json_response(500, {"ok": False, "error": "server"})
