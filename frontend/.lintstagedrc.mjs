/** @type {import('lint-staged').Config} */
const config = {
  '*.{js,jsx,ts,tsx}': ['prettier --write', 'oxlint --fix', () => 'tsc --noEmit --incremental'],
  '*.{css,scss}': ['stylelint --fix', 'prettier --write'],
  '*.{json,md,yml,yaml,html}': ['prettier --write'],
};

export default config;
