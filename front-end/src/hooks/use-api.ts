"use client";
// Hook dữ liệu cho component: TanStack Query trên lib/api/endpoints, trả kiểu giao diện qua adapters.
import { useRef, useState, useSyncExternalStore } from "react";
import { useQueries, useQuery, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { toast } from "sonner";
import { session } from "@/lib/auth/session";
import {
  adminUserApi, analyticsApi, authApi, eventApi, notificationApi, orderApi, ticketApi,
} from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/errors";
import {
  toEvent, toNotice, toOrder, toRefund, toTemplate, toTicket, toTicketType, toUser, withTicketTypes,
} from "@/lib/api/adapters";
import type { Event, EventStatus, Role } from "@/types";
import type { RefundStatusDto } from "@/types/api";

// ---------------------------------------------------------------- session
export function useSession() {
  const state = useSyncExternalStore(session.subscribe, session.get, session.server);
  return { ...state, user: state.user, ready: state.status !== "loading" };
}

/** Chạy một thao tác, chặn bấm đúp, toast lỗi theo `code` (ApiError.message đã là câu tiếng Việt). */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  async function run<T>(action: () => Promise<T>, success?: (value: T) => void) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    try {
      const result = await action();
      success?.(result);
      return result;
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Chưa thể thực hiện thao tác.");
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return { busy, run };
}

/** Làm mới các truy vấn liên quan sau một thao tác ghi. */
export function useRefresh() {
  const client = useQueryClient();
  return (...keys: QueryKey[]) => Promise.all(keys.map((queryKey) => client.invalidateQueries({ queryKey })));
}

/** true khi đã đăng nhập; đăng ký theo dõi phiên để query tự chạy lại sau khi đăng nhập/đăng xuất. */
function useSignedIn() {
  return useSyncExternalStore(session.subscribe, () => session.get().status === "authenticated", () => false);
}

// ---------------------------------------------------------------- public events
export function useCategories() {
  return useQuery({ queryKey: ["categories"], queryFn: eventApi.categories, staleTime: 60_000 });
}

export function useEventSearch(params: { keyword?: string; category?: string; startDate?: string; page: number; size: number; sort?: string }) {
  return useQuery({
    queryKey: ["events", "search", params],
    queryFn: async () => {
      const page = await eventApi.search(params);
      return { events: page.content.map(toEvent), total: page.page.totalElements };
    },
    placeholderData: (previous) => previous,
  });
}

export function useTicketTypes(eventId: string | undefined) {
  return useQuery({
    queryKey: ["ticket-types", eventId],
    queryFn: async () => (await ticketApi.typesOf(eventId!)).map((t) => toTicketType(t)),
    enabled: Boolean(eventId),
    staleTime: 15_000,
  });
}

/**
 * Sự kiện công khai kèm hạng vé và (tùy chọn) sơ đồ ghế từng khu. Sơ đồ ghế tự làm mới mỗi 4 giây (C12) để thấy
 * ghế người khác vừa giữ.
 */
export function usePublicEvent(id: string | undefined, { withSeats = false } = {}) {
  const event = useQuery({ queryKey: ["event", id], queryFn: () => eventApi.get(id!), enabled: Boolean(id) });
  const types = useQuery({
    queryKey: ["ticket-types", id, "raw"],
    queryFn: () => ticketApi.typesOf(id!),
    enabled: Boolean(id),
    refetchInterval: withSeats ? 4000 : false,
  });
  const maps = useQueries({
    queries: (withSeats ? types.data ?? [] : []).map((t) => ({
      queryKey: ["seat-map", t.id],
      queryFn: () => ticketApi.seatMap(t.id),
      refetchInterval: 4000,
    })),
  });
  let data: Event | undefined;
  if (event.data && types.data) {
    const typed = types.data.map((t, i) => toTicketType(t, withSeats ? maps[i]?.data : undefined));
    data = withTicketTypes(toEvent(event.data), typed);
  }
  const seatsLoading = withSeats && maps.some((m) => m.isLoading);
  return {
    data,
    isLoading: event.isLoading || types.isLoading || seatsLoading,
    isError: event.isError || types.isError,
    error: event.error ?? types.error,
    refetch: () => Promise.all([event.refetch(), types.refetch(), ...maps.map((m) => m.refetch())]),
  };
}

/** Tiêu đề/ảnh sự kiện cho danh sách đơn và vé (chỉ sự kiện đang công khai mới đọc được). */
export function useEventsById(ids: string[]) {
  const unique = [...new Set(ids)];
  const results = useQueries({
    queries: unique.map((id) => ({
      queryKey: ["event", id],
      queryFn: () => eventApi.get(id),
      retry: false,
      staleTime: 60_000,
    })),
  });
  const events = new Map<string, Event>();
  results.forEach((r) => {
    if (r.data) events.set(r.data.id, toEvent(r.data));
  });
  return { events, isLoading: results.some((r) => r.isLoading) };
}

// ---------------------------------------------------------------- customer
export function useMyOrders(page = 0, size = 20) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["orders", "me", page, size],
    queryFn: async () => {
      const result = await orderApi.mine({ page, size });
      return { orders: result.content.map(toOrder), total: result.page.totalElements };
    },
    enabled: authed,
  });
}

export function useOrder(id: string | null | undefined) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["order", id],
    queryFn: async () => toOrder(await orderApi.get(id!)),
    enabled: Boolean(id) && authed,
  });
}

/**
 * Trang kết quả VNPay (C06): không tin tham số trên URL, hỏi server đối soát giao dịch rồi đọc trạng thái đơn.
 * Chạy một lần mỗi lần mở trang; lỗi tạm thời (VNPay bận) để người dùng bấm thử lại.
 */
export function usePaymentVerify(orderId: string | null) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["payment-verify", orderId],
    queryFn: async () => toOrder(await orderApi.verify(orderId!)),
    enabled: Boolean(orderId) && authed,
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
  });
}

export function useOrderRefunds(orderId: string | undefined) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["refunds", orderId],
    queryFn: async () => (await orderApi.refundsOf(orderId!)).map(toRefund),
    enabled: Boolean(orderId) && authed,
  });
}

export function useMyTickets() {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["tickets", "me"],
    queryFn: async () => (await ticketApi.mine()).map(toTicket),
    enabled: authed,
  });
}

export function useTicketQr(ticketId: string | null) {
  return useQuery({
    queryKey: ["ticket-qr", ticketId],
    queryFn: () => ticketApi.qr(ticketId!),
    enabled: Boolean(ticketId),
    staleTime: Infinity,
  });
}

export function useNotifications(page = 0, size = 20, unreadOnly = false) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["notifications", page, size, unreadOnly],
    queryFn: async () => {
      const result = await notificationApi.mine({ page, size, unreadOnly });
      return { notices: result.content.filter((n) => n.channel === "IN_APP").map(toNotice), total: result.page.totalElements };
    },
    enabled: authed,
  });
}

export function useUnreadCount() {
  const { status } = useSession();
  return useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: async () => (await notificationApi.unreadCount()).count,
    enabled: status === "authenticated",
    refetchInterval: 30_000,
  });
}

/** Hồ sơ cá nhân; null khi tài khoản chưa có hồ sơ (404 PROFILE_NOT_FOUND). */
export function useProfile() {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["profile"],
    queryFn: async () => {
      try {
        return await authApi.profile();
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
    enabled: authed,
  });
}

// ---------------------------------------------------------------- organizer / admin
export function useOrganizerEvents(status?: EventStatus) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["events", "mine", status],
    queryFn: async () => (await eventApi.mine({ status, page: 0, size: 100 })).content.map(toEvent),
    enabled: authed,
  });
}

export function useOrganizerEvent(id: string | undefined) {
  const list = useOrganizerEvents();
  const types = useTicketTypes(id);
  const found = list.data?.find((e) => e.id === id);
  return {
    data: found ? withTicketTypes(found, types.data ?? []) : undefined,
    isLoading: list.isLoading || (Boolean(id) && types.isLoading),
    isError: list.isError,
    refetch: () => Promise.all([list.refetch(), types.refetch()]),
  };
}

export function useAdminEvents(status?: EventStatus) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["events", "admin", status],
    queryFn: async () => (await eventApi.adminList({ status, page: 0, size: 100 })).content.map(toEvent),
    enabled: authed,
  });
}

export function useAdminRefunds(status?: RefundStatusDto) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["refunds", "admin", status],
    queryFn: async () => (await orderApi.adminRefunds(status)).map(toRefund),
    enabled: authed,
  });
}

export function useReconciliation() {
  const authed = useSignedIn();
  return useQuery({ queryKey: ["reconciliation"], queryFn: orderApi.reconciliation, enabled: authed });
}

export function useAdminUsers(query: { page: number; size: number; search?: string; role?: Role; status?: "ACTIVE" | "LOCKED" }) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["admin-users", query],
    queryFn: async () => {
      const result = await adminUserApi.list(query);
      return { users: result.content.map(toUser), total: result.totalElements };
    },
    enabled: authed,
    placeholderData: (previous) => previous,
  });
}

export function useTemplates() {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["templates"],
    queryFn: async () => (await notificationApi.templates()).map(toTemplate),
    enabled: authed,
  });
}

/** query = null khi chưa xác định được khoảng ngày (chưa chạy trên trình duyệt). */
export function useAnalyticsOverview(query: { from: string; to: string; granularity: "DAY" | "MONTH"; eventId?: string } | null) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["analytics", "overview", query],
    queryFn: () => analyticsApi.overview(query!),
    enabled: authed && Boolean(query),
    placeholderData: (previous) => previous,
  });
}

export function useAnalyticsEvents(query: { from: string; to: string; page: number; size: number; sort?: string } | null) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["analytics", "events", query],
    queryFn: () => analyticsApi.events(query!),
    enabled: authed && Boolean(query),
    placeholderData: (previous) => previous,
  });
}

export function useEventAttendance(eventId: string | undefined) {
  const authed = useSignedIn();
  return useQuery({
    queryKey: ["analytics", "attendance", eventId],
    queryFn: () => analyticsApi.attendance(eventId!),
    enabled: Boolean(eventId) && authed,
    refetchInterval: 10_000,
  });
}
