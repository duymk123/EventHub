"use client";
import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { motion } from "framer-motion";
import {
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  MapPin,
  Heart,
  Search,
  Music2,
  Palette,
  Sparkles,
  Mic2,
  Ticket,
  Clock3,
  Share2,
  Check,
  Minus,
  Plus,
  ChevronRight,
  SlidersHorizontal,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useDemo } from "@/hooks/use-demo";
import { useNow } from "@/hooks/use-now";
import { demoStore } from "@/lib/mock/store";
import { VENUE } from "@/lib/mock/seed";
import { money, dateLabel, timeLabel, cn } from "@/lib/utils";
import {
  DataBoundary,
  Empty,
  PageTitle,
  Benefit,
  Disclosure,
  Pagination,
} from "../shared";
import type { Event } from "@/types";
import { toast } from "sonner";
const categoryIcons = [Sparkles, Music2, Palette, Mic2, Ticket];
function favorite(eventId: string) {
  demoStore.set((s) => ({
    ...s,
    favorites: s.favorites.includes(eventId)
      ? s.favorites.filter((id) => id !== eventId)
      : [...s.favorites, eventId],
  }));
}
export function EventCard({ event }: { event: Event }) {
  const { favorites } = useDemo();
  const price = Math.min(
    ...event.ticketTypes.filter((t) => t.active).map((t) => t.price),
  );
  return (
    <article className="event-card">
      <div className="event-image">
        <Link href={`/events/${event.id}`} tabIndex={-1} aria-hidden>
          <Image
            src={event.image}
            alt=""
            fill
            sizes="(max-width: 760px) 90vw, (max-width:1100px) 45vw, 28vw"
          />
        </Link>
        <button
          aria-label={
            favorites.includes(event.id)
              ? `Bỏ lưu ${event.title}`
              : `Lưu ${event.title}`
          }
          className={cn(
            "favorite-button",
            favorites.includes(event.id) && "is-favorite",
          )}
          onClick={() => favorite(event.id)}
        >
          <Heart
            size={17}
            fill={favorites.includes(event.id) ? "currentColor" : "none"}
          />
        </button>
      </div>
      <div className="event-info">
        <div className="event-meta">
          <span>{event.category}</span>
          <span>{dateLabel(event.startTime, true)}</span>
        </div>
        <h3>
          <Link href={`/events/${event.id}`}>{event.title}</Link>
        </h3>
        <p className="event-location">
          <MapPin size={13} />
          TP. Hồ Chí Minh
        </p>
        <div className="event-bottom">
          <span>
            {price === 0 ? (
              "Miễn phí"
            ) : (
              <>
                <small>Từ</small> {money(price)}
              </>
            )}
          </span>
          <Link
            href={`/events/${event.id}`}
            aria-label={`Xem sự kiện ${event.title}`}
            className="event-arrow"
          >
            <ArrowUpRight size={18} />
          </Link>
        </div>
      </div>
    </article>
  );
}
export function Home() {
  const { events, categories } = useDemo();
  const [category, setCategory] = useState("Tất cả");
  const [search, setSearch] = useState("");
  const router = useRouter();
  const visible = events.filter(
    (e) =>
      e.status === "PUBLISHED" &&
      (category === "Tất cả" || e.category === category),
  );
  return (
    <>
      <section className="hero">
        <div className="hero-image">
          <Image
            src="/images/concert.jpg"
            alt="Khán giả hòa mình trong ánh đèn sân khấu concert"
            fill
            priority
            sizes="100vw"
          />
        </div>
        <div className="hero-overlay" />
        <div className="container hero-content">
          <motion.div
            initial={{ opacity: 0, y: 25 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.65 }}
          >
            <p className="eyebrow">Ở đây, cảm xúc được kết nối</p>
            <h1>
              Sống trọn
              <br />
              <span>khoảnh khắc.</span>
            </h1>
            <p className="hero-description">
              Bước ra khỏi thường ngày.
              <br />
              Tìm những trải nghiệm dành riêng cho bạn.
            </p>
            <Button asChild className="hero-cta">
              <Link href="/explore">
                Khám phá sự kiện
                <ArrowRight size={17} />
              </Link>
            </Button>
          </motion.div>
          <Link href="/events/echoes" className="hero-feature">
            <span className="hero-feature-date">
              <b>21</b>
              <span>THÁNG 11</span>
            </span>
            <div>
              <p>Đêm nhạc nổi bật</p>
              <h2>ECHOES OF THE NIGHT</h2>
              <span>19:00 · EventHub Live</span>
            </div>
            <ArrowUpRight size={22} />
          </Link>
        </div>
      </section>
      <div className="container">
        <form
          className="discovery-search"
          onSubmit={(e) => {
            e.preventDefault();
            router.push(`/explore?q=${encodeURIComponent(search)}`);
          }}
        >
          <Search size={21} />
          <input
            aria-label="Tìm kiếm sự kiện"
            placeholder="Bạn muốn trải nghiệm điều gì?"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <div className="search-location">
            <MapPin size={16} />
            TP. Hồ Chí Minh
          </div>
          <Button type="submit" aria-label="Tìm sự kiện">
            <span>Tìm sự kiện</span>
            <ArrowRight size={16} />
          </Button>
        </form>
        <section className="discover-section">
          <div className="section-heading">
            <h2>Lên lịch cho những điều hay</h2>
            <Link className="text-link" href="/explore">
              Xem tất cả
              <ArrowRight size={15} />
            </Link>
          </div>
          <div className="category-tabs">
            {["Tất cả", ...categories].map((name, i) => {
              const Icon = categoryIcons[i % categoryIcons.length];
              return (
                <button
                  key={name}
                  className={cn(category === name && "selected")}
                  onClick={() => setCategory(name)}
                >
                  <Icon size={16} />
                  {name}
                </button>
              );
            })}
          </div>
          <DataBoundary empty={!visible.length}>
            <div className="event-grid">
              {visible.slice(0, 4).map((e) => (
                <EventCard key={e.id} event={e} />
              ))}
            </div>
          </DataBoundary>
        </section>
        <section className="spotlight">
          <div className="spotlight-image">
            <Image
              src="/images/festival.jpg"
              alt="Không gian lễ hội âm nhạc ngoài trời"
              fill
              sizes="60vw"
            />
          </div>
          <div className="spotlight-copy">
            <span className="eyebrow">AFTERGLOW FESTIVAL</span>
            <h2>
              Có những đêm
              <br />
              chỉ đến một lần.
            </h2>
            <p>
              Âm nhạc, bạn bè và cả một bầu trời cảm xúc. Cuộc hẹn tiếp theo của
              bạn đang chờ.
            </p>
            <Link href="/events/afterglow" className="text-link">
              Khám phá sự kiện
              <ArrowUpRight size={17} />
            </Link>
          </div>
        </section>
        <section className="how-section">
          <div>
            <h2>
              Từ một cuộc hẹn,
              <br />
              đến một kỷ niệm.
            </h2>
            <p className="muted">
              Bạn chỉ cần chọn điều mình thích.
              <br />
              EventHub đồng hành trong phần còn lại.
            </p>
          </div>
          <div className="how-steps">
            {[
              {
                Icon: Search,
                title: "Tìm đúng cảm hứng",
                description: "Khám phá những sự kiện phù hợp với bạn.",
              },
              {
                Icon: Ticket,
                title: "Giữ chỗ thật dễ dàng",
                description: "Chọn vé, điền thông tin và hoàn tất thanh toán.",
              },
              {
                Icon: Sparkles,
                title: "Sẵn sàng tận hưởng",
                description: "Vé điện tử luôn sẵn trong Ví vé của tôi.",
              },
            ].map(({ Icon, title, description }) => (
              <div key={title}>
                <span className="how-icon">
                  <Icon size={20} />
                </span>
                <div>
                  <h3>{title}</h3>
                  <p>{description}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>
    </>
  );
}
export function Explore({ saved = false }: { saved?: boolean }) {
  const params = useSearchParams();
  return <ExploreContent key={`${saved}-${params.toString()}`} saved={saved} />;
}
function ExploreContent({ saved }: { saved: boolean }) {
  const params = useSearchParams();
  const { events, categories, favorites } = useDemo();
  const [search, setSearch] = useState(params.get("q") ?? "");
  const [category, setCategory] = useState(params.get("category") ?? "Tất cả");
  const [sort, setSort] = useState("soon");
  const [date, setDate] = useState("");
  const [page, setPage] = useState(0);
  const list = events
    .filter(
      (e) =>
        e.status === "PUBLISHED" &&
        (!saved || favorites.includes(e.id)) &&
        (category === "Tất cả" || e.category === category) &&
        e.title
          .toLocaleLowerCase("vi")
          .includes(search.toLocaleLowerCase("vi")) &&
        (!date || e.startTime.slice(0, 10) >= date),
    )
    .sort((a, b) =>
      sort === "price"
        ? Math.min(...a.ticketTypes.map((t) => t.price)) -
          Math.min(...b.ticketTypes.map((t) => t.price))
        : a.startTime.localeCompare(b.startTime),
    );
  return (
    <div className="container page-section">
      <PageTitle
        eyebrow="TÌM CẢM HỨNG TIẾP THEO"
        title={saved ? "Những cuộc hẹn đã lưu" : "Thế giới trải nghiệm của bạn"}
        description={
          saved
            ? "Lưu lại điều bạn thích. Quay lại khi bạn sẵn sàng."
            : "Âm nhạc, nghệ thuật và những cuộc gặp gỡ đáng nhớ."
        }
      />
      <div className="explore-layout">
        <aside className="filter-panel">
          <h3>
            <SlidersHorizontal size={17} />
            Bộ lọc
          </h3>
          <label className="field-label mt-7">Loại sự kiện</label>
          {["Tất cả", ...categories].map((c) => (
            <button
              key={c}
              className={cn("filter-option", c === category && "selected")}
              onClick={() => {
                setCategory(c);
                setPage(0);
              }}
            >
              <span>{c}</span>
              {c === category && <Check size={15} />}
            </button>
          ))}
          <label className="field-label mt-7" htmlFor="event-date">
            Từ ngày
          </label>
          <Input
            id="event-date"
            type="date"
            value={date}
            onChange={(e) => {
              setDate(e.target.value);
              setPage(0);
            }}
          />
          <Button
            variant="ghost"
            className="mt-5 w-full"
            onClick={() => {
              setCategory("Tất cả");
              setDate("");
              setSearch("");
              setPage(0);
            }}
          >
            Xóa bộ lọc
          </Button>
          <div className="filter-note">
            <MapPin size={18} />
            <p>
              Các sự kiện trong bản trải nghiệm diễn ra tại địa điểm minh họa ở
              TP. Hồ Chí Minh.
            </p>
          </div>
        </aside>
        <div className="min-w-0">
          <div className="filters">
            <div className="input-wrap">
              <Search />
              <Input
                aria-label="Tìm theo tên sự kiện"
                placeholder="Tìm theo tên sự kiện..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
              />
            </div>
            <select
              aria-label="Sắp xếp sự kiện"
              value={sort}
              onChange={(e) => {
                setSort(e.target.value);
                setPage(0);
              }}
            >
              <option value="soon">Sắp diễn ra</option>
              <option value="price">Giá tăng dần</option>
            </select>
          </div>
          <p className="muted text-xs mb-6">
            {list.length} sự kiện dành cho bạn
          </p>
          <DataBoundary
            empty={!list.length}
            title="Chưa tìm thấy sự kiện phù hợp"
            description="Thử từ khóa khác hoặc bỏ bớt bộ lọc để khám phá thêm."
          >
            <div className="event-grid explore-grid">
              {list.slice(page * 6, (page + 1) * 6).map((e) => (
                <EventCard key={e.id} event={e} />
              ))}
            </div>
            <Pagination
              page={page}
              total={list.length}
              onChange={setPage}
              pageSize={6}
            />
          </DataBoundary>
        </div>
      </div>
    </div>
  );
}
export function EventDetail({ id }: { id: string }) {
  const { events, user, favorites } = useDemo();
  const now = useNow();
  const event = events.find(
    (e) => e.id === id && ["PUBLISHED", "CLOSED"].includes(e.status),
  );
  const router = useRouter();
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  if (!event)
    return (
      <div className="container page-section">
        <Empty
          title="Sự kiện chưa được công bố"
          description="Sự kiện này chưa mở công khai hoặc không còn tồn tại."
          action={
            <Button asChild>
              <Link href="/explore">Khám phá sự kiện khác</Link>
            </Button>
          }
        />
      </div>
    );
  const total = event.ticketTypes.reduce(
    (s, t) => s + t.price * (quantities[t.id] ?? 0),
    0,
  );
  const count = Object.values(quantities).reduce((s, q) => s + q, 0);
  const minutesLeft = Math.max(
    0,
    Math.floor((Date.parse(event.startTime) - now) / 60000),
  );
  const salesClosed = event.status !== "PUBLISHED" || minutesLeft === 0;
  function checkout() {
    if (!user) {
      router.push("/login");
      return;
    }
    if (user.role !== "CUSTOMER") {
      toast.info(
        "Hãy chuyển sang vai trò Khách hàng ở bảng Trải nghiệm để đặt vé.",
      );
      return;
    }
    const query = new URLSearchParams({
      event: id,
      ...Object.fromEntries(
        Object.entries(quantities)
          .filter(([, q]) => q > 0)
          .map(([k, q]) => [k, String(q)]),
      ),
    });
    router.push(`/checkout?${query}`);
  }
  return (
    <>
      <div className="detail-banner">
        <Image
          src={event.image}
          alt={event.title}
          fill
          priority
          sizes="100vw"
        />
        <div className="detail-shade" />
        <div className="container detail-banner-content">
          <div className="breadcrumb">
            <Link href="/explore">Khám phá</Link>
            <ChevronRight size={14} />
            {event.category}
          </div>
          <span className="eyebrow">
            {event.category} · {event.organizerName}
          </span>
          <h1>{event.title}</h1>
          <p>{event.subtitle}</p>
          <div className="detail-facts">
            <span>
              <CalendarDays size={17} />
              {dateLabel(event.startTime)} · {timeLabel(event.startTime)}
            </span>
            <span>
              <MapPin size={17} />
              {VENUE}
            </span>
          </div>
        </div>
      </div>
      <div className="container detail-layout">
        <div className="detail-body">
          <div className="detail-tabs">
            <a href="#about">Giới thiệu</a>
            <a href="#schedule">Lịch trình</a>
            <a href="#policy">Thông tin cần biết</a>
            <button aria-label="Lưu sự kiện" onClick={() => favorite(event.id)}>
              <Heart
                size={18}
                fill={favorites.includes(id) ? "currentColor" : "none"}
              />
            </button>
            <button
              aria-label="Chia sẻ sự kiện"
              onClick={() => {
                navigator.clipboard
                  ?.writeText(window.location.href)
                  .then(() => toast.success("Đã sao chép liên kết sự kiện"));
              }}
            >
              <Share2 size={18} />
            </button>
          </div>
          <section id="about">
            <h2>Một cuộc hẹn với cảm xúc</h2>
            <p>{event.description}</p>
            <Image
              className="about-image"
              src={event.image}
              alt="Không khí sân khấu của sự kiện"
              width={1000}
              height={560}
            />
            <h3>Trải nghiệm dành cho bạn</h3>
            <div className="perks">
              <span>
                <Music2 size={18} />
                Âm nhạc & trình diễn trực tiếp
              </span>
              <span>
                <Sparkles size={18} />
                Không gian ánh sáng ấn tượng
              </span>
              <span>
                <Ticket size={18} />
                Vé điện tử, vào cổng thuận tiện
              </span>
            </div>
          </section>
          <section id="schedule">
            <h2>Lịch trình buổi diễn</h2>
            <div className="timeline">
              {[
                [
                  "18:00",
                  "Mở cửa đón khách",
                  "Đổi vòng tay, tìm vị trí và khám phá không gian.",
                ],
                [
                  "19:00",
                  "Bắt đầu chương trình",
                  "Cùng hòa nhịp với những màn trình diễn mở đầu.",
                ],
                [
                  "20:00",
                  "Khoảnh khắc chính",
                  "Thưởng thức trọn vẹn những tiết mục đặc biệt.",
                ],
                [
                  "23:00",
                  "Khép lại cuộc hẹn",
                  "Cảm ơn bạn đã tạo nên một đêm đáng nhớ.",
                ],
              ].map(([time, title, desc]) => (
                <div key={time}>
                  <time>{time}</time>
                  <div>
                    <h4>{title}</h4>
                    <p>{desc}</p>
                  </div>
                </div>
              ))}
            </div>
            <p className="text-xs muted mt-4">
              Lịch trình minh họa cho bản trải nghiệm UI/UX.
            </p>
          </section>
          <section id="policy">
            <h2>Trước khi đến, bạn cần biết</h2>
            <Disclosure title="Tôi nhận vé bằng cách nào?">
              Sau khi thanh toán thành công, vé xuất hiện trong Ví vé của tôi.
              Vé được gửi đến email người sử dụng; nếu không có, sử dụng email
              người mua.
            </Disclosure>
            <Disclosure title="Tôi có thể yêu cầu hoàn tiền không?">
              Bạn có thể yêu cầu hoàn cả đơn khi sự kiện còn ít nhất 48 giờ và
              mọi vé chưa sử dụng. Admin xét duyệt; số tiền hoàn là 70% giá mua,
              giữ lại 30%.
            </Disclosure>
            <Disclosure title="Tôi có thể ra rồi vào lại không?">
              Vé chỉ được check-in một lần. Sau khi check-out, bạn không thể
              dùng vé đó để vào lại.
            </Disclosure>
          </section>
        </div>
        <aside className="booking-panel">
          <div className="event-countdown">
            <Clock3 size={15} />
            <span>
              {!now
                ? "Đang cập nhật thời gian"
                : minutesLeft > 0
                  ? `Bắt đầu sau ${Math.floor(minutesLeft / 1440)} ngày ${Math.floor((minutesLeft % 1440) / 60)} giờ ${minutesLeft % 60} phút`
                  : "Sự kiện đã bắt đầu"}
            </span>
          </div>
          <div className="booking-heading">
            <Ticket size={21} />
            <h2>Chọn trải nghiệm của bạn</h2>
          </div>
          <p className="muted text-xs mb-5">
            Chọn hạng vé và số lượng bạn muốn.
          </p>
          {event.ticketTypes.map((t) => {
            const unavailable = !t.active || !t.available || salesClosed;
            const qty = quantities[t.id] ?? 0;
            return (
              <motion.div
                layout
                key={t.id}
                className={cn(
                  "ticket-choice",
                  qty > 0 && "selected",
                  unavailable && "unavailable",
                )}
              >
                <div>
                  <div className="flex items-center gap-2">
                    <h3>{t.name}</h3>
                    {t.name === "VIP" && <Sparkles size={12} />}
                  </div>
                  <strong>{t.price ? money(t.price) : "Miễn phí"}</strong>
                  <small>
                    {unavailable
                      ? !t.available
                        ? "Hết vé"
                        : "Tạm ngừng bán"
                      : t.available <= 20
                        ? `Chỉ còn ${t.available} vé`
                        : t.name === "VIP"
                          ? "Vị trí gần sân khấu"
                          : t.name === "PREMIUM"
                            ? "Tầm nhìn trọn vẹn"
                            : "Hòa nhịp cùng đám đông"}
                  </small>
                </div>
                <div className="quantity-control">
                  <button
                    aria-label={`Giảm vé ${t.name}`}
                    disabled={!qty || unavailable}
                    onClick={() =>
                      setQuantities({ ...quantities, [t.id]: qty - 1 })
                    }
                  >
                    <Minus size={13} />
                  </button>
                  <span>{qty}</span>
                  <button
                    aria-label={`Thêm vé ${t.name}`}
                    disabled={unavailable || qty >= Math.min(6, t.available)}
                    onClick={() =>
                      setQuantities({ ...quantities, [t.id]: qty + 1 })
                    }
                  >
                    <Plus size={13} />
                  </button>
                </div>
              </motion.div>
            );
          })}
          <div className="booking-total">
            <span>
              Tạm tính <small>({count} vé)</small>
            </span>
            <strong>{money(total)}</strong>
          </div>
          <Button
            className="w-full h-12"
            disabled={!count || salesClosed}
            onClick={checkout}
          >
            Mua vé ngay
            <ArrowRight size={17} />
          </Button>
          <div className="booking-assurance">
            <Benefit>Thanh toán an toàn</Benefit>
            <span>
              <Clock3 size={14} />
              Giữ vé 3 phút
            </span>
          </div>
          <p className="text-[10px] muted text-center mt-3">
            Giá hiển thị là dữ liệu minh họa. Không phát sinh giao dịch thật.
          </p>
        </aside>
      </div>
    </>
  );
}
