import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios';

const TOKEN_KEY = 'sanitaslink.access_token';

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
});

// Only the Authorization Bearer header is sent; the tenant (office) is never sent by the client.
instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

instance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
    }
    return Promise.reject(error);
  },
);

/** Orval custom axios mutator used by the generated React Query client. */
export const mutator = <T>(config: AxiosRequestConfig, options?: AxiosRequestConfig): Promise<T> => {
  const source = axios.CancelToken.source();
  const promise = instance({ ...config, ...options, cancelToken: source.token }).then(
    ({ data }: AxiosResponse<T>) => data,
  );
  (promise as Promise<T> & { cancel?: () => void }).cancel = () => {
    source.cancel('Query was cancelled');
  };
  return promise;
};
