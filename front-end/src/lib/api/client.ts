// Api client duy nhất của frontend (AGENTS.md mục 3). Mọi request đi qua API Gateway.
// - Access token chỉ nằm trong bộ nhớ của module này (C13), không ghi localStorage/sessionStorage.
// - Refresh token nằm trong cookie HttpOnly eh_refresh do Auth đặt; trình duyệt tự gửi khi credentials: "include".
// - Gặp 401: gọi refresh đúng một lần (gộp các request đang chờ), thành công thì gọi lại request cũ.
import { ApiError } from "./errors";

/** Địa chỉ API Gateway. Mặc định cho chạy local; môi trường khác đặt NEXT_PUBLIC_API_BASE_URL. */
export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
const PREFIX = "/api/v1";

let accessToken: string | null = null;
let expiresAt = 0;
let refreshing: Promise<boolean> | null = null;
const sessionListeners = new Set<(signedIn: boolean) => void>();

/** Session (lib/auth/session.ts) nghe để biết khi token mất hiệu lực hẳn. */
export function onTokenChange(listener: (signedIn: boolean) => void) {
  sessionListeners.add(listener);
  return () => {
    sessionListeners.delete(listener);
  };
}

export function setAccessToken(token: string | null, expiresInSeconds = 0) {
  accessToken = token;
  expiresAt = token ? Date.now() + expiresInSeconds * 1000 : 0;
  sessionListeners.forEach((fn) => fn(Boolean(token)));
}

export const hasAccessToken = () => Boolean(accessToken);

interface TokenBody {
  accessToken: string;
  expiresIn: number;
}

/** POST /auth/refresh bằng cookie; trả false khi phiên đã hết (không ném lỗi). Gộp các lần gọi đồng thời. */
export function refreshAccessToken(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const res = await fetch(`${API_BASE_URL}${PREFIX}/auth/refresh`, { method: "POST", credentials: "include" });
        if (!res.ok) {
          setAccessToken(null);
          return false;
        }
        const body = (await res.json()) as TokenBody;
        setAccessToken(body.accessToken, body.expiresIn);
        return true;
      } catch {
        setAccessToken(null);
        return false;
      } finally {
        refreshing = null;
      }
    })();
  }
  return refreshing;
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  headers?: Record<string, string>;
  /** false cho API công khai: không gắn token, không thử refresh. */
  auth?: boolean;
  signal?: AbortSignal;
}

function buildUrl(path: string, query?: RequestOptions["query"]) {
  const url = new URL(`${API_BASE_URL}${PREFIX}${path}`);
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
  });
  return url.toString();
}

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as { code?: string; takenSeatIds?: string[] };
    if (body?.code) return new ApiError(body.code, body.takenSeatIds ?? [], res.status);
  } catch {
    /* Body không phải ErrorResponse. */
  }
  if (res.status === 401) return new ApiError("UNAUTHORIZED", [], 401);
  if (res.status === 403) return new ApiError("FORBIDDEN", [], 403);
  if (res.status === 404) return new ApiError("NOT_FOUND", [], 404);
  if (res.status === 503 || res.status === 502) return new ApiError("SERVICE_UNAVAILABLE", [], res.status);
  if (res.status === 504) return new ApiError("GATEWAY_TIMEOUT", [], 504);
  return new ApiError("INTERNAL_ERROR", [], res.status);
}

async function send(path: string, options: RequestOptions, withToken: boolean): Promise<Response> {
  const headers: Record<string, string> = { Accept: "application/json", ...(options.headers ?? {}) };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (withToken && accessToken) headers.Authorization = `Bearer ${accessToken}`;
  try {
    return await fetch(buildUrl(path, options.query), {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: "include",
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new ApiError("NETWORK_ERROR", [], 0);
  }
}

/** Gọi API qua Gateway. Trả body JSON (hoặc undefined với 204). Lỗi luôn là ApiError mang `code`. */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const auth = options.auth !== false;
  if (auth && accessToken && Date.now() > expiresAt - 15_000) {
    // Sắp hết hạn: làm mới trước để khỏi tốn một vòng 401.
    await refreshAccessToken();
  }
  let res = await send(path, options, auth);
  if (res.status === 401 && auth) {
    const refreshed = await refreshAccessToken();
    if (refreshed) res = await send(path, options, true);
  }
  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Login trả access token trong body, refresh token trong cookie (C13). */
export async function exchangeToken(path: "/auth/login", body: unknown): Promise<void> {
  const res = await send(path, { method: "POST", body }, false);
  if (!res.ok) throw await toApiError(res);
  const token = (await res.json()) as TokenBody;
  setAccessToken(token.accessToken, token.expiresIn);
}

/** Đăng xuất: Auth xóa cookie và kết thúc phiên Keycloak; luôn xóa token trong bộ nhớ. */
export async function revokeSession(): Promise<void> {
  try {
    await send("/auth/logout", { method: "POST" }, false);
  } finally {
    setAccessToken(null);
  }
}
