import { resolveTheme, type SiteTheme } from '~/utils/theme';

export const useTheme = () => {
  const theme = useState<SiteTheme>('theme', () => 'light');
  // Only an explicit user action becomes a saved preference. Keep it shared
  // between the header and article consumers even if browser storage is blocked.
  const preference = useState<SiteTheme | null>('theme-preference', () => null);
  let removeSystemListener: (() => void) | undefined;
  const applyTheme = (value: SiteTheme) => {
    if (!import.meta.client) return;
    document.documentElement.dataset.theme = value;
  };
  onMounted(() => {
    try {
      const stored = localStorage.getItem('theme');
      if (stored === 'dark' || stored === 'light') preference.value = stored;
    } catch { /* Private browsing can disable storage without disabling themes. */ }
    const media = window.matchMedia?.('(prefers-color-scheme: dark)');
    const syncSystemTheme = () => {
      theme.value = resolveTheme(preference.value, Boolean(media?.matches));
      applyTheme(theme.value);
    };
    syncSystemTheme();
    if (media?.addEventListener) {
      media.addEventListener('change', syncSystemTheme);
      removeSystemListener = () => media.removeEventListener('change', syncSystemTheme);
    } else if (media?.addListener) {
      media.addListener(syncSystemTheme);
      removeSystemListener = () => media.removeListener(syncSystemTheme);
    }
  });
  onUnmounted(() => removeSystemListener?.());
  watch(theme, applyTheme);
  return {
    theme,
    isDark: computed(() => theme.value === 'dark'),
    toggleTheme: () => {
      preference.value = theme.value === 'dark' ? 'light' : 'dark';
      theme.value = preference.value;
      applyTheme(theme.value);
      if (import.meta.client) {
        try { localStorage.setItem('theme', theme.value); } catch { /* Keep the in-memory choice. */ }
      }
    }
  };
};
