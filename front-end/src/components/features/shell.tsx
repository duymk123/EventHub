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
import { useSession, useUnreadCount } from "@/hooks/use-api";
import { session } from "@/lib/auth/session";
import { roleNames } from "@/lib/labels";
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
  const { user } = useSession();
  const unread = useUnreadCount();
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
                  {(unread.data ?? 0) > 0 && <span />}
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
                      onClick={async () => {
                        await session.logout();
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
                141 Đường Chiến Thắng, Hà Nội
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
