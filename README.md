# AEROCELL — аудиостенд

PWA-стенд стерео-баланса: три трека уже лежат в приложении, переключаются с одной кнопки и кэшируются для быстрого старта.

## Треки

Все файлы в приложении — MP3 **320 kbps CBR**.

| Кнопка | Файл | Композиция | Лицензия |
|---|---|---|---|
| Клубная | `audio/club.mp3` | Beat Doctor — *Alright* (electro house) | [CC BY 2.0 UK](https://creativecommons.org/licenses/by/2.0/uk/) |
| Классика | `audio/classical.mp3` | Вивальди, «Времена года»: Весна, I. Allegro | запись CC0 |
| Рок | `audio/rock.mp3` | Kevin MacLeod — *Big Rock* (incompetech.com) | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) |

AC/DC и другой коммерческий каталог в приложение не входят: его нельзя легально скачивать и раздавать вместе с сайтом. Рок-кнопка играет свободный hard rock в том же духе.

Загрузки своего файла нет: на стенде только эти три трека.

## Запуск локально

```bash
python -m http.server 8080
```

Откройте `http://127.0.0.1:8080/`. Для установки PWA нужен HTTPS (GitHub Pages) или localhost.

## GitHub Pages

После публикации страница будет по адресу:

https://paulos99.github.io/aerocell-car-audio/
