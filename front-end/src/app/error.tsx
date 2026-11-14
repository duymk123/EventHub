"use client";
import { Button } from "@/components/ui/button";
import { AlertCircle } from "lucide-react";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <div className="container empty-state py-24">
      <AlertCircle size={40} />
      <h2>Trang chưa tải được</h2>
      <p>Thử tải lại để tiếp tục trải nghiệm của bạn.</p>
      <Button onClick={reset}>Thử lại</Button>
    </div>
  );
}
