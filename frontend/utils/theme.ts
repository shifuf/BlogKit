export type SiteTheme = 'light' | 'dark';

export const resolveTheme = (preference: unknown, prefersDark: boolean): SiteTheme => {
  if (preference === 'light' || preference === 'dark') return preference;
  return prefersDark ? 'dark' : 'light';
};
