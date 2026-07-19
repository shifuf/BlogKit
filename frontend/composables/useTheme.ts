export const useTheme = () => {
  const theme = useState<'light' | 'dark'>('theme', () => 'light');
  const applyTheme = (value: 'light' | 'dark') => {
    if (!import.meta.client) return;
    document.documentElement.dataset.theme = value;
    localStorage.setItem('theme', value);
  };
  onMounted(() => {
    const stored = localStorage.getItem('theme');
    theme.value = stored === 'dark' || stored === 'light' ? stored : 'light';
    applyTheme(theme.value);
  });
  watch(theme, applyTheme);
  return {
    theme,
    isDark: computed(() => theme.value === 'dark'),
    toggleTheme: () => {
      theme.value = theme.value === 'dark' ? 'light' : 'dark';
    }
  };
};
