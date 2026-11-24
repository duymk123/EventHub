"use client";
import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MotionConfig } from "framer-motion";
import { Toaster } from "sonner";
import { session } from "@/lib/auth/session";
import { ApiError } from "@/lib/api/errors";

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 10_000,
        refetchOnWindowFocus: false,
        // Lỗi nghiệp vụ (4xx) không thử lại; lỗi mạng/5xx thử lại một lần.
        retry: (count, error) =>
          count < 1 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      },
    },
  });
}

const PUBLIC_KEYS = new Set(["event", "categories", "ticket-types", "seat-map"]);
/** Dữ liệu công khai giữ lại khi đăng xuất; dữ liệu riêng của người dùng thì xóa. */
function isPublicQuery(key: readonly unknown[]) {
  return PUBLIC_KEYS.has(String(key[0])) || (key[0] === "events" && key[1] === "search");
}

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(makeQueryClient);
  useEffect(() => {
    // Khôi phục phiên bằng cookie refresh (C13) khi tải trang; đăng xuất thì xóa cache dữ liệu riêng tư.
    void session.bootstrap();
    return session.subscribe(() => {
      if (session.get().status === "anonymous") {
        queryClient.removeQueries({ predicate: (q) => !isPublicQuery(q.queryKey) });
      }
    });
  }, [queryClient]);
  return (
    <QueryClientProvider client={queryClient}>
      <MotionConfig reducedMotion="user">
        {children}
        <Toaster theme="light" richColors position="top-right" closeButton />
      </MotionConfig>
    </QueryClientProvider>
  );
}
