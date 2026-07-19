import type { Config } from 'tailwindcss';
export default <Partial<Config>> {
  content: ['./app/**/*.{vue,ts}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['HarmonyOS_Regular', 'Inter', 'system-ui', 'sans-serif']
      },
      colors: {
        primary: '#409eff',
        ink: '#1c1f23'
      }
    }
  }
};
