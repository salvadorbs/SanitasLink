import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';

import en from '@/locales/en/translation.json';
import it from '@/locales/it/translation.json';

/**
 * i18next bootstrap with Italian and English. The language is auto-detected from the browser
 * (query/cookie/localStorage/navigator) and falls back to Italian, which is also the language
 * pinned by the unit tests (see src/test/setup.ts) and by the Playwright suite (it-IT locale).
 */
i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      it: { translation: it },
      en: { translation: en },
    },
    supportedLngs: ['it', 'en'],
    // it-IT/it-CH/en-US/en-GB all resolve to their base language, and only the base is loaded.
    nonExplicitSupportedLngs: true,
    load: 'languageOnly',
    fallbackLng: 'it',
    // Re-detect from the browser on every load instead of caching the choice in web storage,
    // keeping the SPA storage-free (the E2E suite asserts an empty localStorage).
    detection: {
      caches: [],
    },
    interpolation: {
      escapeValue: false,
    },
    // Synchronous init so t() resolves immediately in tests and on first render.
    initAsync: false,
  });

export default i18n;
