const CACHE = 'aerocell-v7';
const SHELL = [
  './',
  './index.html',
  './privacy.html',
  './manifest.webmanifest',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-192.png',
  './icons/icon-maskable-512.png',
  './icons/apple-touch-icon.png',
  './icons/favicon.ico',
  './icons/stp-logo.png'
];
const AUDIO = [
  './audio/club.mp3',
  './audio/classical.mp3',
  './audio/rock.mp3'
];

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    await cache.addAll(SHELL);
    await Promise.all(AUDIO.map(async (url) => {
      try {
        const response = await fetch(url, { cache: 'reload' });
        if (response.ok && response.status === 200) {
          await cache.put(url, response);
        }
      } catch (error) {
        console.warn('audio precache skipped', url, error);
      }
    }));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

function isSameOrigin(request) {
  try {
    return new URL(request.url).origin === self.location.origin;
  } catch {
    return false;
  }
}

async function matchFullAudio(url) {
  const cache = await caches.open(CACHE);
  const candidates = [
    url.href,
    url.pathname,
    `./audio/${url.pathname.split('/').pop()}`,
    `/aerocell-car-audio/audio/${url.pathname.split('/').pop()}`
  ];
  for (const key of candidates) {
    const hit = await cache.match(key, { ignoreSearch: true, ignoreVary: true });
    if (!hit) continue;
    const clone = hit.clone();
    const blob = await clone.blob();
    if (blob.size > 80000) return hit;
  }
  return null;
}

async function respondWithRange(request, cached) {
  const blob = await cached.blob();
  const size = blob.size;
  const rangeHeader = request.headers.get('range');
  const match = /bytes=(\d*)-(\d*)/.exec(rangeHeader || '');
  if (!match) {
    return new Response(blob, {
      status: 200,
      headers: {
        'Content-Type': 'audio/mpeg',
        'Content-Length': String(size),
        'Accept-Ranges': 'bytes'
      }
    });
  }

  const start = match[1] ? Number(match[1]) : 0;
  const end = match[2] ? Number(match[2]) : size - 1;
  if (start >= size || end >= size || start > end) {
    return new Response(null, {
      status: 416,
      headers: { 'Content-Range': `bytes */${size}` }
    });
  }

  const slice = blob.slice(start, end + 1);
  return new Response(slice, {
    status: 206,
    headers: {
      'Content-Type': 'audio/mpeg',
      'Content-Length': String(slice.size),
      'Content-Range': `bytes ${start}-${end}/${size}`,
      'Accept-Ranges': 'bytes'
    }
  });
}

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET' || !isSameOrigin(request)) return;

  const url = new URL(request.url);
  const isAudio = /\.mp3$/i.test(url.pathname);

  if (isAudio) {
    event.respondWith((async () => {
      try {
        const fresh = await fetch(request);
        if (fresh && (fresh.status === 200 || fresh.status === 206)) return fresh;
      } catch {
        /* offline: fall through to cache */
      }
      const cached = await matchFullAudio(url);
      if (!cached) throw new Error('audio miss');
      return request.headers.has('range') ? respondWithRange(request, cached) : respondWithRange(new Request(url.href), cached);
    })());
    return;
  }

  event.respondWith((async () => {
    const cached = await caches.match(request, { ignoreSearch: true });
    if (cached) return cached;
    try {
      const response = await fetch(request);
      if (response && response.ok && response.type === 'basic' && response.status === 200) {
        const cache = await caches.open(CACHE);
        cache.put(request, response.clone());
      }
      return response;
    } catch (error) {
      if (request.mode === 'navigate') {
        const fallback = await caches.match('./index.html');
        if (fallback) return fallback;
      }
      throw error;
    }
  })());
});
