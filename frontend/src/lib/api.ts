import axios, { AxiosError } from "axios";

const TOKEN_KEY = "g360.token";

/** API base URL is injected at build time; it must point at the public HTTPS backend in production. */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";

export const tokenStore = {
  // sessionStorage: cleared when the tab closes, not shared across tabs. See docs/SECURITY.md.
  get: (): string | null => {
    try {
      return sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (t: string) => {
    try {
      sessionStorage.setItem(TOKEN_KEY, t);
    } catch {
      /* storage unavailable */
    }
  },
  clear: () => {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      /* storage unavailable */
    }
  },
};

export const api = axios.create({ baseURL: API_BASE_URL, timeout: 20000 });

api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

api.interceptors.response.use(
  (r) => r,
  (error: AxiosError) => {
    const url = error.config?.url ?? "";
    if (error.response?.status === 401 && !url.includes("/auth/login") && tokenStore.get()) {
      tokenStore.clear();
      onUnauthorized?.();
    }
    return Promise.reject(error);
  },
);

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
}

export function apiError(err: unknown): ApiErrorBody {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as Partial<ApiErrorBody> | undefined;
    if (data && typeof data === "object" && data.message) {
      return {
        status: err.response?.status ?? 0,
        code: data.code ?? "ERROR",
        message: data.message,
        fieldErrors: data.fieldErrors,
      };
    }
    if (!err.response) {
      return { status: 0, code: "NETWORK", message: "Cannot reach the server. Check your connection and try again." };
    }
    return { status: err.response.status, code: "ERROR", message: `Request failed (${err.response.status})` };
  }
  return { status: 0, code: "ERROR", message: "Something went wrong" };
}

export function errorMessage(err: unknown): string {
  const e = apiError(err);
  if (e.fieldErrors?.length) return e.fieldErrors.map((f) => f.message).join(". ");
  return e.message;
}

/** Builds a query string, skipping empty values. Arrays become repeated params. */
export function toParams(obj: Record<string, unknown>): URLSearchParams {
  const p = new URLSearchParams();
  Object.entries(obj).forEach(([k, v]) => {
    if (v === undefined || v === null || v === "" || v === false) return;
    if (Array.isArray(v)) v.forEach((x) => p.append(k, String(x)));
    else p.append(k, String(v));
  });
  return p;
}
