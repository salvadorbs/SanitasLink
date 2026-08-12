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
          useQuery: true,
          useInfinite: true,
          useMutation: true,
          shouldSplitQueryKey: true,
        },
      },
    },
  },
});
