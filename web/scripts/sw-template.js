/* Generated only for production builds; an incomplete update never replaces a working shell. */
const VERSION = __VERSION__;
const SHELL = `fieldguard-shell-${VERSION}`;
const URLS = __PRECACHE__;
self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(SHELL);
    try { await cache.addAll(URLS); }
    catch (error) { await caches.delete(SHELL); throw error; }
  })());
});
// New versions wait until all old app windows are closed. No forced mid-conversation reload.
self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) if (key.startsWith('fieldguard-shell-') && key !== SHELL) await caches.delete(key);
    await self.clients.claim();
  })());
});
self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) return;
  // Authentication and shared work records must always come from the server.
  if (new URL(request.url).pathname.startsWith('/api/')) return;
  event.respondWith((async () => {
    const shell = await caches.open(SHELL);
    if (request.mode === 'navigate') return (await shell.match(new URL('./index.html',self.registration.scope), { ignoreVary: true })) || fetch(request);
    // These are immutable, same-origin build files. A preview server's Vary:
    // Origin must not make cached modules disappear on a CORS module request.
    const appAsset = await shell.match(request, { ignoreSearch: true, ignoreVary: true });
    if (appAsset) return appAsset;
    // Ask this version's shell manifest which verified large-file cache belongs to it.
    const manifestResponse = await shell.match(new URL('./asset-manifest.json',self.registration.scope), { ignoreVary: true });
    if (manifestResponse) {
      const manifest = await manifestResponse.json();
      const assets = await caches.open(`fieldguard-assets-${manifest.version}`);
      const prepared = await assets.match(request, { ignoreSearch: true, ignoreVary: true });
      if (prepared) return prepared;
    }
    return fetch(request);
  })());
});
