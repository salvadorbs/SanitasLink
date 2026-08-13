import { defineConfig } from 'orval';

export default defineConfig({
  sanitaslink: {
    input: {
      target: process.env.OPENAPI_TARGET ?? 'src/api/openapi.json',
    },
    output: {
      mode: 'tags-split',
      formatter: 'prettier',
      indexFiles: false,
      target: 'src/api/endpoints/',
      schemas: 'src/api/models/',
      client: 'react-query',
      httpClient: 'axios',
      clean: true,
      override: {
        mutator: {
          path: './src/api/mutator.ts',
          name: 'mutator',
        },
        query: {
          // Defaults are correct: GET operations become useQuery hooks, non-GET become
          // useMutation. Overriding useQuery/useMutation globally would force the same hook
          // kind for every verb.
          useInfinite: false,
          shouldSplitQueryKey: true,
        },
      },
    },
  },
});
