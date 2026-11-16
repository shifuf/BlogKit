export default defineNuxtPlugin(async () => {
  if (!('caches' in window)) return;
  const names = await caches.keys();
  await Promise.all(names.filter(name => ['pages', 'images', 'assets'].includes(name)).map(name => caches.delete(name)));
});
