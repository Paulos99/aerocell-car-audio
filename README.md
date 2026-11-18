# AEROCELL — стенд стерео-баланса

Нативное Android-приложение для ландшафтного планшета. Сравнение короба с обработкой AEROCELL и короба без обработки. Три трека уже внутри APK, интернет не нужен.

## Установка

1. Отсканируйте QR `AEROCELL-download-qr.png` с планшета **или** скачайте [AEROCELL-debug.apk](https://raw.githubusercontent.com/Paulos99/aerocell-car-audio/main/AEROCELL-debug.apk).
2. Разрешите установку из этого источника.
3. Откройте **AEROCELL**.

Прямая ссылка для QR: `https://raw.githubusercontent.com/Paulos99/aerocell-car-audio/main/AEROCELL-debug.apk`

При первом запуске Android может спросить доступ к микрофону. Это нужно только визуализатору, запись не ведётся.

Экран не гаснет, ориентация — ландшафт.

С версии 1.1.0 для планшетов стенда лучше ставить подписанную release-сборку `app/build/outputs/apk/release/app-release.apk` (пакет `com.aerocell.stand`). Дальше приложение сможет обновляться само из облака.

## Как пользоваться

- Внизу выберите жанр **CLUB / ROCK / CLASSIC** — трек сразу стартует.
- Большая круглая кнопка — play / пауза; слева — предыдущий жанр; справа — стоп.
- Ползунок баланса посередине: **L** — короб с AEROCELL, **R** — без обработки.
- Справа внизу — громкость.
- Если на планшете есть Wi-Fi, при запуске приложение тихо проверяет облако и в фоне докачивает новые ролики и треки. Без сети всё работает из кэша и встроенных треков.
- Когда доступна новая версия приложения, внизу появляется баннер «Доступно обновление» и кнопка «Обновить». Установка только после подтверждения на системном экране.
- Служебное меню: длинное нажатие на логотип. Там текущие версии, статус контента и кнопка «Проверить обновления».

## Треки

| Жанр | Что играет | Лицензия |
|---|---|---|
| CLUB | Beat Doctor — *Alright* | CC BY 2.0 UK |
| CLASSIC | Вивальди, «Весна», I. Allegro | CC0 |
| ROCK | Kevin MacLeod — *Big Rock* | CC BY 3.0 |

Если в облаке есть более новый файл того же жанра и он уже скачан, играет он. Иначе — встроенный трек.

## Облачные обновления

Контент лежит в Yandex Object Storage, бакет `aerocell-stand`. Публичный манифест:

`https://storage.yandexcloud.net/aerocell-stand/manifest.json`

Список бакета закрыт, отдельные файлы читаются по прямой ссылке. Ключи для загрузки в репозиторий не кладутся.

Чтобы опубликовать новые ролики, треки или APK:

1. Соберите папку: `audio/`, `video/`, при необходимости `apk/` и `content.json` с названиями, жанрами и `versionCode` для APK.
2. Задайте ключи через `--env-file` (или переменную `AEROCELL_S3_ENV`). Файл с ключами должен лежать вне репозитория.
3. Запустите скрипт:

```bat
python tools\publish_content.py --source C:\path\to\content-folder --env-file C:\path\to\aerocell-s3.env
```

```bash
python tools/publish_content.py --source /path/to/content-folder --env-file /path/to/aerocell-s3.env
```

Скрипт посчитает sha256 и размер, увеличит `contentVersion`, зальёт файлы и `manifest.json`. Проверка без загрузки: добавьте `--dry-run`. Без APK: `--no-apk`.

Пример `content.json`:

```json
{
  "tracks": {
    "club.mp3": {"id": "track-club", "genre": "CLUB", "title": "Alright"}
  },
  "apk": {
    "file": "apk/AEROCELL-1.1.0.apk",
    "versionCode": 2,
    "versionName": "1.1.0",
    "applicationId": "com.aerocell.stand"
  },
  "config": { "location": "sparta" }
}
```

## Подпись release-сборки

Ключ и пароли не должны попадать в git. В корне проекта нужен файл `keystore.properties` (он в `.gitignore`) и хранилище `keystore/aerocell-release.jks`:

```
storeFile=keystore/aerocell-release.jks
storePassword=...
keyAlias=aerocell
keyPassword=...
```

Без этого файла `assembleRelease` соберётся, но APK не будет подписан ключом стенда, и обновление с планшета не встанет.

## Сборка

Нужны JDK 17+ (подойдёт JBR из Android Studio) и Android SDK 34.

```bat
gradlew.bat assembleDebug
gradlew.bat assembleRelease
gradlew.bat testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (пакет `com.aerocell.stand.debug`).
Release APK: `app/build/outputs/apk/release/app-release.apk` (пакет `com.aerocell.stand`).
