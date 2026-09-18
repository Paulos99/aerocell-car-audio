# COMPLIANCE REPORT — AEROCELL PWA

Дата: 2026-09-18  
Проект: веб-приложение / GitHub Pages  
Статус production (технический): READY_WITH_WARNINGS

Это технический аудит, не юридическое заключение.

## Фактическая реализация

- HTML/PWA, без бэкенда и без своей БД
- Нет форм ПД, нет логина
- Нет загрузки пользовательских файлов: только три предустановленных трека
- Нет Google Analytics / Метрики / рекламы
- Музыка: Вивальди CC0; Beat Doctor CC BY 2.0 UK; Kevin MacLeod CC BY 3.0. Attribution в интерфейсе и README
- Хостинг предполагается GitHub Pages (США)

## Реестр РКН

ИНН не найден. Карточка не сверена. См. `RKN_CHANGES.md`.

## Риски

### LC-001
Risk: 🔵 CLIENT ACTION / 🟣 LEGAL REVIEW  
Category: Personal Data / RKN  
Issue: Нет ИНН, проверка реестра операторов не закрыта.  
Action: Сообщить ИНН; юрист квалифицирует, является ли проект оператором при отсутствии форм.  
Owner: Client + Legal  
Status: OPEN  
Required before production: желательно для коммерческого бренда, не блокирует демо-стенд без сбора ПД

### LC-002
Risk: 🟡 MEDIUM  
Category: Hosting  
Issue: GitHub Pages может логировать IP посетителей; трансграничная передача.  
Action: Для публичного коммерческого сайта рассмотреть хостинг в РФ или раскрыть это в политике.  
Owner: Client + Legal  
Status: OPEN

### LC-003
Risk: 🟢 LOW  
Category: IP  
Issue: Треки с открытыми лицензиями. Attribution добавлен в UI, README, `audio/CREDITS.md`.  
Action: Не убирать указание автора. Не добавлять коммерческий каталог (AC/DC и т.п.).  
Owner: Dev  
Status: CLOSED (технически)

### LC-004
Risk: 🟢 LOW  
Category: Cookies  
Issue: Своих cookie нет; есть Cache Storage PWA.  
Action: Описано в `privacy.html`.  
Owner: Dev  
Status: CLOSED (технически)

### LC-005
Risk: 🟠 HIGH только если бренд чужой  
Category: Trademark  
Issue: Название AEROCELL / CarAudio в коде; право на товарный знак не проверялось.  
Action: Подтвердить, что бренд принадлежит заказчику.  
Owner: Client  
Status: OPEN  
Required before production: YES, если публикация от чужого имени

## Исправлено технически

- Страница `privacy.html`
- Attribution музыки
- Нет скрытой аналитики
- Нет пользовательской загрузки файлов

## Production gate

BLOCKER: нет  
HIGH: LC-005 (бренд) — уточнить у клиента  
LEGAL_REVIEW_REQUIRED: операторство / GitHub / политика

Технический compliance-аудит завершён. Обнаруженные риски перечислены. Юридическая проверка требуется для пунктов, отмеченных LEGAL_REVIEW_REQUIRED.
