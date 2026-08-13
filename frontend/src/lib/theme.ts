export type Theme = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'sanitaslink.theme';

/** Stored choice, falling back to the OS preference (defaults to light). */
export function resolveTheme(): Theme {
  const stored = localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  const system = window.matchMedia?.('(prefers-color-scheme: dark)');
  return system?.matches ? 'dark' : 'light';
}

/** Toggles the `.dark` class consumed by the shadcn tokens and matches native widgets. */
export function applyTheme(theme: Theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark');
  document.documentElement.style.colorScheme = theme;
}
