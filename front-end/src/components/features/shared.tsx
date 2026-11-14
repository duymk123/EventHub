"use client";
import { useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  Inbox,
  RefreshCw,
  ShieldCheck,
  LoaderCircle,
  AlertCircle,
  ChevronDown,
  ChevronUp,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
  DialogHeader,
} from "@/components/ui/dialog";
import { useExperience } from "@/components/providers";
import { useDemo } from "@/hooks/use-demo";
import { statusLabels } from "@/lib/mock/seed";
import type { Role } from "@/types";
import { cn } from "@/lib/utils";
export function Status({ value }: { value: string }) {
  return (
    <span
      className={cn(
        "status",
        [
          "PAID",
          "PUBLISHED",
          "APPROVED",
          "ACTIVE",
          "ISSUED",
          "REFUNDED",
        ].includes(value)
          ? "status-green"
          : [
                "SUBMITTED",
                "PENDING_PAYMENT",
                "REQUESTED",
                "CHECKED_IN",
              ].includes(value)
            ? "status-amber"
            : ["REJECTED", "FAILED", "LOCKED", "CANCELLED"].includes(value)
              ? "status-red"
              : "status-muted",
      )}
    >
      {statusLabels[value] ?? value}
    </span>
  );
}
export function PageTitle({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p className="muted mt-3">{description}</p>}
      </div>
      {action}
    </div>
  );
}
export function Empty({
  title = "Chưa có dữ liệu",
  description = "Nội dung sẽ xuất hiện tại đây khi có cập nhật.",
  action,
}: {
  title?: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="empty-state">
      <span className="empty-icon">
        <Inbox size={30} />
      </span>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}
export function DataBoundary({
  children,
  empty = false,
  title,
  description,
}: {
  children: React.ReactNode;
  empty?: boolean;
  title?: string;
  description?: string;
}) {
  const { mode, setMode } = useExperience();
  if (mode === "loading")
    return (
      <div aria-label="Đang tải dữ liệu" className="skeleton-grid">
        {Array.from({ length: 4 }, (_, i) => (
          <div className="skeleton" key={i} />
        ))}
      </div>
    );
  if (mode === "error")
    return (
      <div className="empty-state">
        <AlertCircle size={36} />
        <h3>Chưa tải được nội dung</h3>
        <p>Hãy thử lại để tiếp tục khám phá.</p>
        <Button onClick={() => setMode("ready")}>
          <RefreshCw size={16} />
          Thử lại
        </Button>
      </div>
    );
  if (mode === "empty" || empty)
    return <Empty title={title} description={description} />;
  return <>{children}</>;
}
export function RoleGuard({
  roles,
  children,
}: {
  roles: Role[];
  children: React.ReactNode;
}) {
  const { user, ready } = useDemo();
  if (!ready)
    return (
      <div
        className="container py-16"
        role="status"
        aria-label="Đang khôi phục phiên trải nghiệm"
      >
        <div className="skeleton" />
      </div>
    );
  if (!user)
    return (
      <div className="container py-20">
        <Empty
          title="Đăng nhập để tiếp tục"
          description="Đăng nhập để lưu vé, theo dõi đơn hàng và quản lý trải nghiệm của bạn."
          action={
            <Button asChild>
              <Link href="/login">
                Đăng nhập
                <ArrowRight size={16} />
              </Link>
            </Button>
          }
        />
      </div>
    );
  if (!roles.includes(user.role) || user.status === "LOCKED")
    return (
      <div className="container py-20">
        <Empty
          title="Khu vực dành cho vai trò khác"
          description="Bạn có thể đổi vai trò ở bảng Trải nghiệm để khám phá màn hình này."
          action={
            <Button asChild variant="secondary">
              <Link href="/">Về trang chủ</Link>
            </Button>
          }
        />
      </div>
    );
  return <>{children}</>;
}
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  onConfirm,
  busy = false,
  destructive = true,
  children,
  confirmLabel = "Xác nhận",
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  onConfirm: () => void;
  busy?: boolean;
  destructive?: boolean;
  children?: React.ReactNode;
  confirmLabel?: string;
}) {
  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!busy) onOpenChange(v);
      }}
    >
      <DialogContent className="modal">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {children}
        <div className="flex justify-end gap-3 mt-4">
          <Button
            disabled={busy}
            variant="secondary"
            onClick={() => onOpenChange(false)}
          >
            Quay lại
          </Button>
          <Button
            disabled={busy}
            variant={destructive ? "destructive" : "default"}
            onClick={onConfirm}
          >
            {busy && <LoaderCircle className="animate-spin" size={16} />}{" "}
            {confirmLabel}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
export function Pagination({
  page,
  total,
  onChange,
  pageSize = 5,
}: {
  page: number;
  total: number;
  onChange: (page: number) => void;
  pageSize?: number;
}) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  return (
    <div className="pagination">
      <span>
        {total
          ? `${Math.min(page * pageSize + 1, total)}-${Math.min((page + 1) * pageSize, total)} trong ${total} kết quả`
          : "0 kết quả"}
      </span>
      <div>
        <Button
          size="sm"
          variant="secondary"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
        >
          Trước
        </Button>
        <span>
          {page + 1} / {pages}
        </span>
        <Button
          size="sm"
          variant="secondary"
          disabled={page + 1 >= pages}
          onClick={() => onChange(page + 1)}
        >
          Sau
        </Button>
      </div>
    </div>
  );
}
export function Benefit({ children }: { children: React.ReactNode }) {
  return (
    <span className="benefit">
      <ShieldCheck size={15} />
      {children}
    </span>
  );
}
export function Disclosure({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="disclosure">
      <button onClick={() => setOpen(!open)} aria-expanded={open}>
        {title}
        <span>
          {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
        </span>
      </button>
      {open && <div className="muted pb-5 leading-7">{children}</div>}
    </div>
  );
}
