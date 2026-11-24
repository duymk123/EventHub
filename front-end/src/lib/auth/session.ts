// Phiên đăng nhập phía trình duyệt. Token nằm trong api client (bộ nhớ); ở đây chỉ giữ người dùng hiện tại.
// Tải lại trang: gọi refresh bằng cookie eh_refresh (C13) rồi GET /auth/me để khôi phục phiên.
"use client";
import { api, exchangeToken, onTokenChange, refreshAccessToken, revokeSession } from "@/lib/api/client";
import { toUser } from "@/lib/api/adapters";
import type { User } from "@/types";
import type { UserDto } from "@/types/api";

export type SessionStatus = "loading" | "anonymous" | "authenticated";
export interface SessionState {
  status: SessionStatus;
  user: User | null;
}

let state: SessionState = { status: "loading", user: null };
const listeners = new Set<() => void>();
let bootstrapped = false;

function set(next: SessionState) {
  state = next;
  listeners.forEach((fn) => fn());
}

const SERVER_STATE: SessionState = { status: "loading", user: null };

export const session = {
  get: () => state,
  server: () => SERVER_STATE,
  subscribe(fn: () => void) {
    listeners.add(fn);
    return () => {
      listeners.delete(fn);
    };
  },

  /** Gọi một lần khi app chạy trên trình duyệt. */
  async bootstrap() {
    if (bootstrapped) return;
    bootstrapped = true;
    // Token mất hẳn (refresh thất bại giữa chừng) → về trạng thái chưa đăng nhập.
    onTokenChange((signedIn) => {
      if (!signedIn && state.status === "authenticated") set({ status: "anonymous", user: null });
    });
    const ok = await refreshAccessToken();
    if (!ok) {
      set({ status: "anonymous", user: null });
      return;
    }
    await session.reloadUser();
  },

  async reloadUser() {
    try {
      const me = await api<UserDto>("/auth/me");
      set({ status: "authenticated", user: toUser(me) });
    } catch {
      set({ status: "anonymous", user: null });
    }
  },

  /** Lấy token mới (claim email_verified cập nhật sau khi xác minh email) rồi đọc lại tài khoản. */
  async revalidate() {
    if (await refreshAccessToken()) await session.reloadUser();
    return state.user;
  },

  async login(email: string, password: string): Promise<User> {
    await exchangeToken("/auth/login", { email, password });
    await session.reloadUser();
    if (!state.user) throw new Error("Không tải được thông tin tài khoản.");
    return state.user;
  },

  async logout() {
    await revokeSession();
    set({ status: "anonymous", user: null });
  },
};
