import { useCallback, useState } from 'react';

import { THEME_STORAGE_KEY, type Theme, applyTheme, resolveTheme } from '@/lib/theme';

/**
 * Minimal theme state: resolves the initial value once and applies every change immediately
 * (the startup value is applied by main.tsx before rendering to avoid a flash).
 */
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(resolveTheme);

  const toggle = useCallback(() => {
    setTheme((current) => {
      const next: Theme = current === 'dark' ? 'light' : 'dark';
      applyTheme(next);
      localStorage.setItem(THEME_STORAGE_KEY, next);
      return next;
    });
  }, []);

  return { theme, toggle };
}
