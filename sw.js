const SHELL_CACHE = 'memoryvault-shell-v1';
const SHELL_URLS = ['/', '/index.html', '/manifest.json'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE).then((cache) => cache.addAll(SHELL_URLS)).catch(() => {})
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // The Web Share Target API delivers the OS share sheet's data as a POST navigation
  // straight to /share-target, with no way to attach an Authorization header — this is
  // the one request this service worker actively rewrites rather than just caching.
  // We read the shared fields here, then hand off to the React /share-target route as a
  // normal authenticated GET (with the SPA's own stored JWT) via query params, instead of
  // letting the native POST hit the network directly with no user identity attached.
  if (event.request.method === 'POST' && url.pathname === '/share-target') {
    event.respondWith(handleShareTarget(event.request));
    return;
  }

  // Shell-only offline support: only ever serve cached responses for the app shell
  // itself, never for API calls — a stale cached API response would be worse than no
  // offline support at all for a data-freshness-sensitive app like this one.
  if (event.request.method === 'GET' && SHELL_URLS.includes(url.pathname)) {
    event.respondWith(
      caches.match(event.request).then((cached) => cached || fetch(event.request))
    );
  }
});

async function handleShareTarget(request) {
  const formData = await request.formData();
  const title = formData.get('title') || '';
  const text = formData.get('text') || '';
  const sharedUrl = formData.get('url') || '';

  const params = new URLSearchParams({ title, text, url: sharedUrl });
  return Response.redirect(`/share-target?${params.toString()}`, 303);
}

// Upgrade 5: web push notifications.
self.addEventListener('push', (event) => {
  if (!event.data) return;
  let payload;
  try {
    payload = event.data.json();
  } catch {
    payload = { title: 'MemoryVault', body: event.data.text() };
  }

  event.waitUntil(
    self.registration.showNotification(payload.title || 'MemoryVault', {
      body: payload.body,
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      data: { vaultItemId: payload.vaultItemId },
    })
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const itemId = event.notification.data?.vaultItemId;
  const targetPath = itemId ? `/vault?open=${itemId}` : '/vault';

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client) {
          client.navigate(targetPath);
          return client.focus();
        }
      }
      return self.clients.openWindow(targetPath);
    })
  );
});
