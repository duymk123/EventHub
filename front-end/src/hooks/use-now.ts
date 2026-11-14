"use client";
import { useSyncExternalStore } from "react";
let timestamp = 0;
let timer: ReturnType<typeof setInterval> | undefined;
const listeners = new Set<() => void>();
const snapshot = () => timestamp;
const serverSnapshot = () => 0;
function subscribe(listener: () => void) {
  listeners.add(listener);
  if (!timer) {
    timestamp = Date.now();
    timer = setInterval(() => {
      timestamp = Date.now();
      listeners.forEach((fn) => fn());
    }, 30000);
  }
  return () => {
    listeners.delete(listener);
    if (!listeners.size) {
      clearInterval(timer);
      timer = undefined;
    }
  };
}
export function useNow() {
  return useSyncExternalStore(subscribe, snapshot, serverSnapshot);
}
