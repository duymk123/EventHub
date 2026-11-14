"use client";
import { useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  ArrowUpRight,
  Bell,
  ChevronDown,
  LogOut,
  Menu,
  Ticket,
  UserRound,
  X,
  SlidersHorizontal,
  RotateCcw,
  LayoutDashboard,
  Sparkles,
  Search,
  Heart,
  Mail,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import { useDemo } from "@/hooks/use-demo";
import { useExperience } from "@/components/providers";
import { demoStore } from "@/lib/mock/store";
import { mock } from "@/lib/mock/engine";
import { roleNames } from "@/lib/mock/seed";
import { ConfirmDialog } from "./shared";
import type { Role, DataMode } from "@/types";
import { toast } from "sonner";
export function Brand() {
  return (
    <Link href="/" className="brand" aria-label="EventHub trang chủ">
      <span className="brand-icon">
        <Ticket size={23} strokeWidth={2.5} />
      </span>
      <span>
        event<span className="brand-light">hub</span>
        <span className="brand-dot">.</span>
      </span>
    </Link>
  );
}
export function Shell({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  const router = useRouter();
  const { user, notices } = useDemo();
  const [menu, setMenu] = useState(false);
  const workspace = path.startsWith("/organizer") || path.startsWith("/admin");
  return (
    <>
      <a href="#main" className="skip-link">
        Đến nội dung chính
      </a>
      <header className="site-header">
        <div className="header-inner">
          <Brand />
          <nav className="desktop-nav" aria-label="Điều hướng chính">
            <Link
              className={path === "/" || path === "/explore" ? "active" : ""}
              href="/explore"
            >
              Khám phá
            </Link>
            <Link href="/explore?category=Âm+nhạc">Âm nhạc</Link>
            <Link href="/explore?category=Nghệ+thuật">Nghệ thuật</Link>
            <Link href="/explore?category=Hội+thảo">Hội thảo</Link>
          </nav>
          <div className="header-actions">
            <Link className="organize-link" href="/organizer">
              Tổ chức sự kiện
              <ArrowUpRight size={14} />
            </Link>
            {user ? (
              <>
                <Link
                  href="/notifications"
                  className="icon-button notification-bell"
                  aria-label="Thông báo"
                >
                  <Bell size={19} />
                  {notices.some((n) => n.userId === user.id && !n.read) && (
                    <span />
                  )}
                </Link>
                <DropdownMenu>
                  <DropdownMenuTrigger
                    className="avatar-trigger"
                    aria-label="Menu tài khoản"
                  >
                    <span className="avatar">
                      {user.name
                        .split(" ")
                        .slice(-2)
                        .map((x) => x[0])
                        .join("")}
                    </span>
                    <ChevronDown size={13} />
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" className="account-menu">
                    <p className="px-2 py-2 text-sm font-semibold">
                      {user.name}
                      <span className="block text-xs muted mt-1">
                        {roleNames[user.role]}
                      </span>
                    </p>
                    <DropdownMenuSeparator />
                    {user.role === "CUSTOMER" ? (
                      <>
                        <DropdownMenuItem asChild>
                          <Link href="/tickets">
                            <Ticket />
                            Vé của tôi
                          </Link>
                        </DropdownMenuItem>
                        <DropdownMenuItem asChild>
                          <Link href="/orders">Đơn hàng của tôi</Link>
                        </DropdownMenuItem>
                        <DropdownMenuItem asChild>
                          <Link href="/favorites">
                            <Heart />
                            Đã lưu
                          </Link>
                        </DropdownMenuItem>
                      </>
                    ) : (
                      <DropdownMenuItem asChild>
                        <Link
                          href={user.role === "ADMIN" ? "/admin" : "/organizer"}
                        >
                          <LayoutDashboard />
                          Không gian quản lý
                        </Link>
                      </DropdownMenuItem>
                    )}
                    <DropdownMenuItem asChild>
                      <Link href="/profile">
                        <UserRound />
                        Tài khoản
                      </Link>
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      onClick={() => {
                        demoStore.set((s) => ({ ...s, userId: null }));
                        router.push("/");
                        toast.success("Đã đăng xuất");
                      }}
                    >
                      <LogOut />
                      Đăng xuất
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </>
            ) : (
              <Button asChild size="sm">
                <Link href="/login">Đăng nhập</Link>
              </Button>
            )}
            <button
              className="mobile-menu-toggle icon-button"
              aria-label={menu ? "Đóng menu" : "Mở menu"}
              onClick={() => setMenu(!menu)}
            >
              {menu ? <X /> : <Menu />}
            </button>
          </div>
        </div>
        {menu && (
          <nav className="mobile-nav">
            {[
              ["/explore", "Khám phá sự kiện"],
              ["/tickets", "Vé của tôi"],
              ["/orders", "Đơn hàng"],
              ["/organizer", "Ban tổ chức"],
              ["/admin", "Quản trị"],
            ]
              .filter(
                ([href]) =>
                  href === "/explore" ||
                  (href === "/admin"
                    ? user?.role === "ADMIN"
                    : href === "/organizer"
                      ? user?.role === "ORGANIZER"
                      : user?.role === "CUSTOMER"),
              )
              .map(([href, label]) => (
                <Link key={href} href={href} onClick={() => setMenu(false)}>
                  {label}
                  <ArrowUpRight size={16} />
                </Link>
              ))}
          </nav>
        )}
      </header>
      <main id="main">{children}</main>
      {!workspace && (
        <footer className="site-footer">
          <div className="container footer-grid">
            <div>
              <Brand />
              <p>
                Những trải nghiệm đáng nhớ
                <br />
                bắt đầu từ đây.
              </p>
            </div>
            <div>
              <h4>Khám phá</h4>
              <Link href="/explore">Sự kiện sắp diễn ra</Link>
              <Link href="/favorites">Sự kiện đã lưu</Link>
            </div>
            <div>
              <h4>Đồng hành cùng bạn</h4>
              <Link href="/help">Hướng dẫn đặt vé</Link>
              <Link href="/help#refund">Chính sách hoàn tiền</Link>
            </div>
            <div>
              <h4>Cùng tạo nên khoảnh khắc</h4>
              <Link href="/organizer">
                Dành cho ban tổ chức
                <ArrowUpRight size={14} />
              </Link>
              <span className="muted text-xs mt-3 block">
                Bản trải nghiệm UI/UX · Dữ liệu minh họa
              </span>
            </div>
          </div>
          <div className="container footer-bottom">
            <span>© 2026 EventHub</span>
            <span>Âm nhạc kết nối. Khoảnh khắc ở lại.</span>
            <Mail size={16} />
          </div>
        </footer>
      )}
      <DemoControls />
      <nav className="mobile-bottom-nav">
        <Link href="/">
          <Sparkles size={19} />
          Trang chủ
        </Link>
        <Link href="/explore">
          <Search size={19} />
          Khám phá
        </Link>
        <Link
          href={
            user?.role === "ADMIN"
              ? "/admin"
              : user?.role === "ORGANIZER"
                ? "/organizer"
                : "/tickets"
          }
        >
          {user?.role === "ADMIN" || user?.role === "ORGANIZER" ? (
            <LayoutDashboard size={19} />
          ) : (
            <Ticket size={19} />
          )}
          {user?.role === "ADMIN" || user?.role === "ORGANIZER"
            ? "Quản lý"
            : "Vé của tôi"}
        </Link>
        <Link href="/profile">
          <UserRound size={19} />
          Tài khoản
        </Link>
      </nav>
    </>
  );
}
function DemoControls() {
  const [open, setOpen] = useState(false);
  const [reset, setReset] = useState(false);
  const { mode, setMode } = useExperience();
  const { user } = useDemo();
  const router = useRouter();
  return (
    <div className="demo-control">
      {open && (
        <div className="demo-panel">
          <div className="flex items-center justify-between">
            <strong>Không gian trải nghiệm</strong>
            <button
              className="icon-button"
              aria-label="Đóng bảng trải nghiệm"
              onClick={() => setOpen(false)}
            >
              <X size={16} />
            </button>
          </div>
          <p className="text-xs muted my-3">
            Mọi thanh toán và dữ liệu đều là mô phỏng, được lưu trên trình duyệt
            này.
          </p>
          <label className="field-label" htmlFor="demo-role">
            Trải nghiệm với vai trò
          </label>
          <select
            id="demo-role"
            value={user?.role ?? ""}
            onChange={(e) => {
              const role = e.target.value as Role;
              if (!mock.switchRole(role)) {
                toast.error(
                  "Chưa có tài khoản đang hoạt động cho vai trò này. Hãy mở khóa hoặc khôi phục dữ liệu mẫu.",
                );
                return;
              }
              setMode("ready");
              router.push(
                role === "CUSTOMER"
                  ? "/"
                  : role === "ADMIN"
                    ? "/admin"
                    : "/organizer",
              );
              setOpen(false);
            }}
          >
            <option value="" disabled>
              Chọn vai trò
            </option>
            {Object.entries(roleNames).map(([r, name]) => (
              <option key={r} value={r}>
                {name}
              </option>
            ))}
          </select>
          <label className="field-label mt-4" htmlFor="demo-state">
            Trạng thái màn hình dữ liệu
          </label>
          <select
            id="demo-state"
            value={mode}
            onChange={(e) => setMode(e.target.value as DataMode)}
          >
            <option value="ready">Có dữ liệu</option>
            <option value="loading">Đang tải</option>
            <option value="empty">Trống</option>
            <option value="error">Lỗi kết nối</option>
          </select>
          <Button
            variant="secondary"
            className="w-full mt-4"
            size="sm"
            onClick={() => setReset(true)}
          >
            <RotateCcw size={14} />
            Khôi phục dữ liệu mẫu
          </Button>
        </div>
      )}
      <button className="demo-trigger" onClick={() => setOpen(!open)}>
        <SlidersHorizontal size={14} />
        <span>Trải nghiệm</span>
        <span className="demo-tag">DEMO</span>
      </button>
      <ConfirmDialog
        open={reset}
        onOpenChange={setReset}
        title="Bắt đầu lại bản trải nghiệm?"
        description="Các đơn hàng, vé và thay đổi bạn đã tạo trên trình duyệt này sẽ được thay bằng dữ liệu mẫu ban đầu."
        onConfirm={() => {
          demoStore.reset();
          setMode("ready");
          setReset(false);
          setOpen(false);
          router.push("/");
          toast.success("Đã khôi phục dữ liệu mẫu");
        }}
      />
    </div>
  );
}
