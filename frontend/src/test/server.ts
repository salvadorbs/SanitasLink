import { setupServer } from 'msw/node';

import { handlers } from './handlers';

/** MSW server intercepting all network traffic for Vitest component/integration tests. */
export const server = setupServer(...handlers);
