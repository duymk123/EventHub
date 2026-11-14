"use client";
import { createContext, useContext, useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MotionConfig } from "framer-motion";
import { Toaster } from "sonner";
import { demoStore } from "@/lib/mock/store";
import { expireOrders } from "@/lib/mock/engine";
import type { DataMode } from "@/types";
const ExperienceContext = createContext<{
  mode: DataMode;
  setMode: (mode: DataMode) => void;
}>({ mode: "ready", setMode: () => {} });
export const useExperience = () => useContext(ExperienceContext);
export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(() => new QueryClient());
  const [mode, setMode] = useState<DataMode>("ready");
  useEffect(() => {
    demoStore.hydrate();
    expireOrders();
    const timer = setInterval(expireOrders, 1000);
    return () => clearInterval(timer);
  }, []);
  return (
    <QueryClientProvider client={queryClient}>
      <MotionConfig reducedMotion="user">
        <ExperienceContext.Provider value={{ mode, setMode }}>
          {children}
          <Toaster theme="light" richColors position="top-right" closeButton />
        </ExperienceContext.Provider>
      </MotionConfig>
    </QueryClientProvider>
  );
}
