# AEROCELL — аудиостенд

PWA-стенд стерео-баланса: три трека уже лежат в приложении, переключаются с одной кнопки и кэшируются для быстрого старта.

## Треки

| Кнопка | Файл | Композиция |
|---|---|---|
| Клубная | `audio/club.mp3` | Club Diver — Kevin MacLeod |
| Классика | `audio/classical.mp3` | Air Prelude — Kevin MacLeod |
| Рок | `audio/rock.mp3` | Iron Horse — Kevin MacLeod |

Музыка: [Kevin MacLeod](https://incompetech.com/), [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/).

## Запуск локально

```bash
python -m http.server 8080
```

Откройте `http://127.0.0.1:8080/`. Для установки PWA нужен HTTPS (GitHub Pages) или localhost.

## GitHub Pages

После публикации страница будет по адресу:

https://paulos99.github.io/aerocell-car-audio/
