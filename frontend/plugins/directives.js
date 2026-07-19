export default defineNuxtPlugin(nuxtApp => {
  nuxtApp.vueApp.directive('transform-xm-txt', {
    mounted(el) {
      if (el.tagName.toLowerCase() !== 'xm-txt') {
        return;
      }
      const replacement = document.createElement('div');
      replacement.className = 'custom-markdown-block';
      replacement.innerHTML = el.innerHTML;
      el.parentNode?.replaceChild(replacement, el);
    }
  });
});
