package com.aerocell.stand.upload

object UploadPageHtml {
    fun render(): String = PAGE

    private const val PAGE = """<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="color-scheme" content="dark">
<title>AEROCELL — свой трек</title>
<style>
  :root { --red:#FF1A1A; --glow:#FF3B3B; --bg:#070707; --card:#141414; --muted:rgba(255,255,255,.72); }
  * { box-sizing: border-box; }
  html, body { margin:0; padding:0; background:var(--bg); color:#fff; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
  body { min-height: 100dvh; display:flex; align-items:center; justify-content:center; padding: 28px 20px; }
  .wrap { width:100%; max-width: 420px; }
  h1 { margin:0 0 8px; font-size: 28px; letter-spacing: 6px; color: var(--glow); font-weight: 800; text-align:center; }
  h2 { margin:0 0 16px; font-size: 22px; font-weight: 650; text-align:center; }
  p { margin:0 0 22px; color: var(--muted); line-height: 1.45; font-size: 16px; text-align:center; }
  .card { background: var(--card); border-radius: 20px; padding: 28px 22px 32px; box-shadow: 0 0 80px rgba(255,26,26,.12); }
  button, .file-btn {
    appearance:none; border:0; display:block; width:100%; text-align:center;
    border-radius: 14px; padding: 16px 18px;
    background: var(--red); color:#fff; font-size: 17px; font-weight: 650; letter-spacing: .3px;
    cursor:pointer;
  }
  button:disabled, .file-btn[aria-disabled="true"] { opacity:.55; cursor:default; }
  input[type=file] { display:none; }
  .progress-wrap { display:none; margin-top: 18px; }
  .bar { height: 6px; background:#2a2a2a; border-radius: 99px; overflow:hidden; }
  .bar > i { display:block; height:100%; width:0; background: var(--glow); }
  .status { margin-top: 12px; text-align:center; font-size: 15px; color: var(--muted); min-height: 1.2em; }
  .status.ok { color:#fff; }
  .status.err { color:#ffb4b4; }
</style>
</head>
<body>
  <div class="wrap">
    <div class="card">
      <h1>AEROCELL</h1>
      <h2>Загрузите свой трек</h2>
      <p>Выберите аудио с телефона — оно сразу заиграет на стенде. Подойдут mp3, m4a, aac, wav, flac и ogg, до 50 МБ.</p>
      <label class="file-btn" id="pickLabel" for="file">Выбрать файл</label>
      <input id="file" type="file" accept=".mp3,.m4a,.aac,.wav,.flac,.ogg,audio/*">
      <div class="progress-wrap" id="progressWrap">
        <div class="bar"><i id="bar"></i></div>
      </div>
      <div class="status" id="status"></div>
    </div>
  </div>
<script>
(function () {
  var fileInput = document.getElementById('file');
  var pickLabel = document.getElementById('pickLabel');
  var statusEl = document.getElementById('status');
  var wrap = document.getElementById('progressWrap');
  var bar = document.getElementById('bar');
  var busy = false;
  var maxBytes = 50 * 1024 * 1024;
  var types = ['mp3','m4a','aac','wav','flac','ogg'];

  function setStatus(text, kind) {
    statusEl.textContent = text;
    statusEl.className = 'status' + (kind ? ' ' + kind : '');
  }
  function extOf(name) {
    var i = name.lastIndexOf('.');
    if (i <= 0) return '';
    return name.slice(i + 1).toLowerCase();
  }
  function messages(code) {
    if (code === 'token') return 'Эта ссылка уже не действует. Наведите камеру на QR на планшете ещё раз.';
    if (code === 'type') return 'Этот файл не подойдёт. Нужен mp3, m4a, aac, wav, flac или ogg.';
    if (code === 'size') return 'Файл больше 50 МБ. Выберите другой или сожмите его.';
    if (code === 'empty') return 'Файл пустой. Выберите другой.';
    return 'Не получилось загрузить. Проверьте, что телефон в той же Wi-Fi сети, что и стенд, и попробуйте ещё раз.';
  }

  fileInput.addEventListener('change', function () {
    if (busy) return;
    var file = fileInput.files && fileInput.files[0];
    fileInput.value = '';
    if (!file) return;
    if (file.size === 0) { setStatus(messages('empty'), 'err'); return; }
    if (file.size > maxBytes) { setStatus(messages('size'), 'err'); return; }
    var ext = extOf(file.name || '');
    if (ext && types.indexOf(ext) < 0) { setStatus(messages('type'), 'err'); return; }
    upload(file);
  });

  function upload(file) {
    busy = true;
    pickLabel.setAttribute('aria-disabled', 'true');
    wrap.style.display = 'block';
    bar.style.width = '0%';
    setStatus('Загрузка… 0%', '');
    var xhr = new XMLHttpRequest();
    xhr.open('POST', location.pathname);
    xhr.upload.onprogress = function (e) {
      if (!e.lengthComputable) return;
      var p = Math.round(e.loaded / e.total * 100);
      bar.style.width = p + '%';
      setStatus('Загрузка… ' + p + '%', '');
    };
    xhr.onerror = function () { done(false, 'server'); };
    xhr.onload = function () {
      var code = 'server';
      try {
        var json = JSON.parse(xhr.responseText || '{}');
        if (json.ok) { done(true); return; }
        if (json.error) code = json.error;
      } catch (err) {}
      if (xhr.status === 410 || xhr.status === 403) code = 'token';
      if (xhr.status === 413) code = 'size';
      if (xhr.status === 415) code = 'type';
      done(false, code);
    };
    var data = new FormData();
    data.append('file', file, file.name);
    xhr.send(data);
  }

  function done(ok, code) {
    busy = false;
    pickLabel.removeAttribute('aria-disabled');
    if (ok) {
      bar.style.width = '100%';
      setStatus('Готово. Трек уже на стенде, можно слушать.', 'ok');
    } else {
      setStatus(messages(code), 'err');
    }
  }
})();
</script>
</body>
</html>
"""
}
