"use client";
// Sự kiện đã lưu: backend không có API yêu thích nên giữ trong localStorage của trình duyệt (chỉ là tiện ích cá nhân,
// mất khi xóa dữ liệu trình duyệt). Mọi truy cập bọc try/catch để chế độ riêng tư vẫn chạy được.
import { useSyncExternalStore } from "react";

const KEY = "eventhub.favorites";
const EMPTY: string[] = [];
const listeners = new Set<() => void>();
let cache: string[] | null = null;

function read(): string[] {
  if (cache) return cache;
  try {
    const parsed: unknown = JSON.parse(window.localStorage.getItem(KEY) ?? "[]");
    cache = Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === "string") : [];
  } catch {
    cache = [];
  }
  return cache;
}

function write(next: string[]) {
  cache = next;
  try {
    window.localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    // Không lưu được thì vẫn giữ trong bộ nhớ cho phiên hiện tại.
  }
  listeners.forEach((fn) => fn());
}

function subscribe(fn: () => void) {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
}

export function toggleFavorite(eventId: string) {
  const current = read();
  write(current.includes(eventId) ? current.filter((id) => id !== eventId) : [...current, eventId]);
}

export function useFavorites() {
  return useSyncExternalStore(subscribe, read, () => EMPTY);
}
