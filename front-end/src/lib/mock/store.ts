"use client";
import type { DemoState } from "@/types";
import { seedState } from "./seed";
const KEY = "eventhub-ui-demo-v2";
const initial = seedState();
let state = initial;
let hydrated = false;
const listeners = new Set<() => void>();
export const demoStore = {
  get: () => state,
  server: () => initial,
  isHydrated: () => hydrated,
  subscribe: (fn: () => void) => {
    listeners.add(fn);
    return () => {
      listeners.delete(fn);
    };
  },
  hydrate: () => {
    if (hydrated) return;
    hydrated = true;
    try {
      const raw = localStorage.getItem(KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as DemoState;
        if (
          parsed.version === 1 &&
          Array.isArray(parsed.events) &&
          Array.isArray(parsed.users)
        )
          state = parsed;
      }
    } catch {
      /* Keep the demo usable when storage is unavailable. */
    }
    listeners.forEach((fn) => fn());
  },
  set: (update: (current: DemoState) => DemoState) => {
    state = update(state);
    try {
      localStorage.setItem(KEY, JSON.stringify(state));
    } catch {
      /* Memory state remains usable in private browsing. */
    }
    listeners.forEach((fn) => fn());
  },
  reset: () => demoStore.set(() => seedState()),
};
