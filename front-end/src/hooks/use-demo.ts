"use client";
import { useSyncExternalStore, useState, useRef } from "react";
import { demoStore } from "@/lib/mock/store";
import { toast } from "sonner";
export function useDemo() {
  const state = useSyncExternalStore(
    demoStore.subscribe,
    demoStore.get,
    demoStore.server,
  );
  const ready = useSyncExternalStore(
    demoStore.subscribe,
    demoStore.isHydrated,
    () => false,
  );
  return {
    ...state,
    ready,
    user: state.users.find((u) => u.id === state.userId) ?? null,
  };
}
export function useAction() {
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  async function run<T>(
    action: () => Promise<T>,
    success?: (value: T) => void,
  ) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    try {
      const result = await action();
      success?.(result);
      return result;
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Chưa thể thực hiện thao tác.",
      );
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return { busy, run };
}
