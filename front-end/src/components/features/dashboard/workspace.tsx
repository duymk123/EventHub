"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  CalendarDays,
  Users,
  BarChart3,
  ScanLine,
  ChevronRight,
  ArrowUpRight,
  FolderTree,
  FileText,
  RotateCcw,
  ShieldCheck,
  Ticket,
} from "lucide-react";
import { useAdminEvents, useAdminRefunds, useSession } from "@/hooks/use-api";
import { RoleGuard } from "../shared";
import { cn } from "@/lib/utils";
const organizerNav = [
  { href: "/organizer", name: "Tổng quan", Icon: LayoutDashboard },
  { href: "/organizer/events", name: "Sự kiện của tôi", Icon: CalendarDays },
  { href: "/organizer/attendees", name: "Người tham dự", Icon: Users },
  { href: "/organizer/reports", name: "Báo cáo doanh thu", Icon: BarChart3 },
  { href: "/organizer/checkin", name: "Kiểm soát vé", Icon: ScanLine },
];
const adminNav = [
  { href: "/admin", name: "Tổng quan", Icon: LayoutDashboard },
  { href: "/admin/analytics", name: "Phân tích", Icon: BarChart3 },
  { href: "/admin/events", name: "Duyệt sự kiện", Icon: CalendarDays },
  { href: "/admin/refunds", name: "Yêu cầu hoàn tiền", Icon: RotateCcw },
  { href: "/admin/users", name: "Tài khoản", Icon: Users },
  { href: "/admin/categories", name: "Danh mục", Icon: FolderTree },
  { href: "/admin/templates", name: "Mẫu thông báo", Icon: FileText },
];
export function Workspace({
  admin = false,
  children,
}: {
  admin?: boolean;
  children: React.ReactNode;
}) {
  return (
    <RoleGuard roles={[admin ? "ADMIN" : "ORGANIZER"]}>
      <WorkspaceBody admin={admin}>{children}</WorkspaceBody>
    </RoleGuard>
  );
}
/** Số việc đang chờ Admin xử lý, chỉ gọi API khi đã chắc là Admin (nằm trong RoleGuard). */
function PendingCount({ kind }: { kind: "refunds" | "events" }) {
  const refunds = useAdminRefunds(kind === "refunds" ? "REQUESTED" : undefined);
  const events = useAdminEvents(kind === "events" ? "SUBMITTED" : undefined);
  const count = kind === "refunds" ? (refunds.data?.length ?? 0) : (events.data?.length ?? 0);
  return count > 0 ? <span>{count}</span> : null;
}
function WorkspaceBody({
  admin,
  children,
}: {
  admin: boolean;
  children: React.ReactNode;
}) {
  const path = usePathname();
  const { user } = useSession();
  const nav = admin ? adminNav : organizerNav;
  return (
      <div className="workspace">
        <aside className="workspace-sidebar">
          <div className="workspace-identity">
            <span>
              {admin ? <ShieldCheck size={20} /> : <Ticket size={20} />}
            </span>
            <div>
              <strong>{admin ? "EventHub Admin" : "EventHub Live"}</strong>
              <small>
                {admin ? "Không gian quản trị" : "Không gian ban tổ chức"}
              </small>
            </div>
          </div>
          <p className="sidebar-label">KHÔNG GIAN LÀM VIỆC</p>
          <nav aria-label="Menu quản lý">
            {nav.map(({ href, name, Icon }) => {
              const active =
                href === (admin ? "/admin" : "/organizer")
                  ? path === href
                  : path.startsWith(href);
              return (
                <Link key={href} href={href} className={cn(active && "active")}>
                  <Icon size={18} />
                  {name}
                  {href === "/admin/refunds" && <PendingCount kind="refunds" />}
                  {href === "/admin/events" && <PendingCount kind="events" />}
                </Link>
              );
            })}
          </nav>
          <div className="sidebar-bottom">
            <Link href="/" className="text-link">
              Về trang khám phá
              <ArrowUpRight size={15} />
            </Link>
            <div>
              <span className="avatar">
                {user?.name
                  .split(" ")
                  .slice(-2)
                  .map((n) => n[0])
                  .join("")}
              </span>
              <p>
                <strong>{user?.name}</strong>
                <small>{admin ? "Quản trị viên" : "Ban tổ chức"}</small>
              </p>
            </div>
          </div>
        </aside>
        <div className="workspace-main">
          <div className="workspace-breadcrumb">
            {admin ? "Quản trị" : "Ban tổ chức"}
            <ChevronRight size={12} />
            <span>
              {[...nav].reverse().find((n) => path.startsWith(n.href))?.name ??
                "Tổng quan"}
            </span>
          </div>
          {children}
        </div>
      </div>
  );
}
