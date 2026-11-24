"use client";
import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
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
  X,
  ChevronRight,
  SlidersHorizontal,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  useCategories,
  useEventSearch,
  useEventsById,
  usePublicEvent,
  useSession,
  useTicketTypes,
} from "@/hooks/use-api";
import { useNow } from "@/hooks/use-now";
import { toggleFavorite, useFavorites } from "@/lib/favorites";
import { VENUE } from "@/lib/labels";
import { ApiError } from "@/lib/api/errors";
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
import { parseSeatId } from "@/lib/seats";
import { SeatSelector } from "./venue";
import { toast } from "sonner";
const categoryIcons = [Sparkles, Music2, Palette, Mic2, Ticket];
const PAGE_SIZE = 6;
const SORTS = [
  { value: "startTime,asc", label: "Sắp diễn ra" },
  { value: "createdAt,desc", label: "Mới đăng" },
  { value: "title,asc", label: "Tên A → Z" },
];
export function EventCard({ event }: { event: Event }) {
  const favorites = useFavorites();
  // Danh sách sự kiện không kèm hạng vé: đọc riêng (có cache) để hiện giá thấp nhất.
  const types = useTicketTypes(event.ticketTypes.length ? undefined : event.id);
  const tiers = event.ticketTypes.length ? event.ticketTypes : (types.data ?? []);
  const prices = tiers.filter((t) => t.active).map((t) => t.price);
  const price = prices.length ? Math.min(...prices) : null;
  const saved = favorites.includes(event.id);
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
          aria-label={saved ? `Bỏ lưu ${event.title}` : `Lưu ${event.title}`}
          className={cn("favorite-button", saved && "is-favorite")}
          onClick={() => toggleFavorite(event.id)}
        >
          <Heart size={17} fill={saved ? "currentColor" : "none"} />
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
          Hà Nội
        </p>
        <div className="event-bottom">
          <span>
            {price === null ? (
              <small>{types.isLoading ? "Đang cập nhật giá" : "Sắp mở bán"}</small>
            ) : price === 0 ? (
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
function heroDate(value: string) {
  const d = new Date(value);
  return { day: d.getDate(), month: `THÁNG ${d.getMonth() + 1}` };
}
export function Home() {
  const categories = useCategories();
  const [category, setCategory] = useState("");
  const [search, setSearch] = useState("");
  const router = useRouter();
  const top = useEventSearch({ page: 0, size: 4, sort: "startTime,asc" });
  const feed = useEventSearch({
    category: category || undefined,
    page: 0,
    size: 4,
    sort: "startTime,asc",
  });
  const featured = top.data?.events[0];
  const spotlight = top.data?.events[1] ?? featured;
  const visible = feed.data?.events ?? [];
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
          {featured && (
            <Link href={`/events/${featured.id}`} className="hero-feature">
              <span className="hero-feature-date">
                <b>{heroDate(featured.startTime).day}</b>
                <span>{heroDate(featured.startTime).month}</span>
              </span>
              <div>
                <p>Sắp diễn ra</p>
                <h2>{featured.title.toLocaleUpperCase("vi")}</h2>
                <span>
                  {timeLabel(featured.startTime)} · {featured.category}
                </span>
              </div>
              <ArrowUpRight size={22} />
            </Link>
          )}
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
            Hà Nội
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
            {[{ id: "", name: "Tất cả" }, ...(categories.data ?? [])].map(
              (c, i) => {
                const Icon = categoryIcons[i % categoryIcons.length];
                return (
                  <button
                    key={c.id || "all"}
                    className={cn(category === c.id && "selected")}
                    onClick={() => setCategory(c.id)}
                  >
                    <Icon size={16} />
                    {c.name}
                  </button>
                );
              },
            )}
          </div>
          <DataBoundary
            loading={feed.isLoading}
            error={feed.isError}
            onRetry={() => void feed.refetch()}
            empty={!visible.length}
            title="Chưa có sự kiện đang mở bán"
            description="Các sự kiện mới sẽ xuất hiện tại đây ngay khi được công bố."
          >
            <div className="event-grid">
              {visible.map((e) => (
                <EventCard key={e.id} event={e} />
              ))}
            </div>
          </DataBoundary>
        </section>
        <section className="spotlight">
          <div className="spotlight-image">
            <Image
              src={spotlight?.image ?? "/images/festival.jpg"}
              alt="Không gian sự kiện âm nhạc"
              fill
              sizes="60vw"
            />
          </div>
          <div className="spotlight-copy">
            <span className="eyebrow">
              {spotlight?.title.toLocaleUpperCase("vi") ?? "EVENTHUB LIVE"}
            </span>
            <h2>
              Có những đêm
              <br />
              chỉ đến một lần.
            </h2>
            <p>
              {spotlight?.subtitle ||
                "Âm nhạc, bạn bè và cả một bầu trời cảm xúc. Cuộc hẹn tiếp theo của bạn đang chờ."}
            </p>
            <Link
              href={spotlight ? `/events/${spotlight.id}` : "/explore"}
              className="text-link"
            >
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
                description: "Chọn ghế, điền thông tin và thanh toán qua VNPay.",
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
/** Giá trị trễ một nhịp để không gọi API theo từng phím gõ. */
function useDebounced<T>(value: T, delay = 350) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}
export function Explore({ saved = false }: { saved?: boolean }) {
  const params = useSearchParams();
  return <ExploreContent key={`${saved}-${params.toString()}`} saved={saved} />;
}
function ExploreContent({ saved }: { saved: boolean }) {
  const params = useSearchParams();
  const categories = useCategories();
  const favorites = useFavorites();
  const [search, setSearch] = useState(params.get("q") ?? "");
  const keyword = useDebounced(search.trim());
  // ?category= nhận cả id lẫn tên danh mục (menu đầu trang dùng tên); null = chưa chọn tay, lấy theo URL.
  const [picked, setPicked] = useState<string | null>(null);
  const fromUrl = params.get("category");
  const category =
    picked ??
    categories.data?.find((c) => c.id === fromUrl || c.name === fromUrl)?.id ??
    "";
  const [sort, setSort] = useState(SORTS[0].value);
  const [date, setDate] = useState("");
  const [page, setPage] = useState(0);
  const remote = useEventSearch({
    keyword: keyword || undefined,
    category: category || undefined,
    startDate: date || undefined,
    page,
    size: PAGE_SIZE,
    sort,
  });
  const favoriteEvents = useEventsById(saved ? favorites : []);
  const savedList = [...favoriteEvents.events.values()]
    .filter(
      (e) =>
        (!category || e.categoryId === category) &&
        e.title.toLocaleLowerCase("vi").includes(keyword.toLocaleLowerCase("vi")) &&
        (!date || e.startTime.slice(0, 10) >= date),
    )
    .sort((a, b) =>
      sort === "title,asc"
        ? a.title.localeCompare(b.title, "vi")
        : sort === "createdAt,desc"
          ? (b.createdAt ?? "").localeCompare(a.createdAt ?? "")
          : a.startTime.localeCompare(b.startTime),
    );
  const list = saved
    ? savedList.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
    : (remote.data?.events ?? []);
  const total = saved ? savedList.length : (remote.data?.total ?? 0);
  const loading = saved ? favoriteEvents.isLoading : remote.isLoading;
  return (
    <div className="container page-section">
      <PageTitle
        eyebrow="TÌM CẢM HỨNG TIẾP THEO"
        title={saved ? "Những cuộc hẹn đã lưu" : "Thế giới trải nghiệm của bạn"}
        description={
          saved
            ? "Lưu lại điều bạn thích trên trình duyệt này. Quay lại khi bạn sẵn sàng."
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
          {[{ id: "", name: "Tất cả" }, ...(categories.data ?? [])].map((c) => (
            <button
              key={c.id || "all"}
              className={cn("filter-option", c.id === category && "selected")}
              onClick={() => {
                setPicked(c.id);
                setPage(0);
              }}
            >
              <span>{c.name}</span>
              {c.id === category && <Check size={15} />}
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
              setPicked("");
              setDate("");
              setSearch("");
              setPage(0);
            }}
          >
            Xóa bộ lọc
          </Button>
          <div className="filter-note">
            <MapPin size={18} />
            <p>Mọi sự kiện trên EventHub diễn ra tại {VENUE}.</p>
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
              {SORTS.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </div>
          <p className="muted text-xs mb-6">{total} sự kiện dành cho bạn</p>
          <DataBoundary
            loading={loading}
            error={!saved && remote.isError}
            onRetry={() => void remote.refetch()}
            empty={!list.length}
            title={
              saved ? "Bạn chưa lưu sự kiện nào" : "Chưa tìm thấy sự kiện phù hợp"
            }
            description={
              saved
                ? "Bấm biểu tượng trái tim trên một sự kiện để lưu lại."
                : "Thử từ khóa khác hoặc bỏ bớt bộ lọc để khám phá thêm."
            }
          >
            <div className="event-grid explore-grid">
              {list.map((e) => (
                <EventCard key={e.id} event={e} />
              ))}
            </div>
            <Pagination
              page={page}
              total={total}
              onChange={setPage}
              pageSize={PAGE_SIZE}
            />
          </DataBoundary>
        </div>
      </div>
    </div>
  );
}
export function EventDetail({
  id,
  initialSeats = [],
}: {
  id: string;
  initialSeats?: string[];
}) {
  const { user } = useSession();
  const favorites = useFavorites();
  const now = useNow();
  // Sơ đồ ghế tự làm mới mỗi vài giây để thấy ghế người khác vừa giữ.
  const query = usePublicEvent(id, { withSeats: true });
  const event = query.data;
  const router = useRouter();
  const [picked, setPicked] = useState<string[]>(initialSeats);
  if (query.isLoading)
    return (
      <div className="container page-section">
        <DataBoundary loading>{null}</DataBoundary>
      </div>
    );
  const notFound = query.error instanceof ApiError && query.error.status === 404;
  if (query.isError && !notFound)
    return (
      <div className="container page-section">
        <DataBoundary error onRetry={() => void query.refetch()}>
          {null}
        </DataBoundary>
      </div>
    );
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
  // Ghế vừa bị người khác giữ vẫn nằm trong `picked` để báo cho khách, nhưng không được đưa sang checkout.
  const groups = event.ticketTypes
    .map((t) => ({
      type: t,
      seats: picked
        .map(parseSeatId)
        .filter((p) => p.tierId === t.id && !t.taken[p.code])
        .map((p) => p.code)
        .sort(),
    }))
    .filter((g) => g.seats.length > 0);
  const valid = groups.flatMap((g) => g.seats.map((c) => `${g.type.id}:${c}`));
  const total = groups.reduce((s, g) => s + g.type.price * g.seats.length, 0);
  const count = valid.length;
  const minutesLeft = Math.max(
    0,
    Math.floor((Date.parse(event.startTime) - now) / 60000),
  );
  const salesClosed = event.status !== "PUBLISHED" || minutesLeft === 0;
  function checkout() {
    if (!user) {
      // Đăng nhập xong quay lại đúng sự kiện với các ghế đã chọn.
      const back = `/events/${id}?${new URLSearchParams({ seats: valid.join(",") })}`;
      router.push(`/login?next=${encodeURIComponent(back)}`);
      return;
    }
    if (user.role !== "CUSTOMER") {
      toast.info("Chỉ tài khoản Khách hàng mới đặt được vé.");
      return;
    }
    const query = new URLSearchParams({ event: id, seats: valid.join(",") });
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
          <span className="eyebrow">{event.category} · EventHub Live</span>
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
            <a href="#seats">Chọn chỗ</a>
            <a href="#about">Giới thiệu</a>
            <a href="#schedule">Lịch trình</a>
            <a href="#policy">Thông tin cần biết</a>
            <button aria-label="Lưu sự kiện" onClick={() => toggleFavorite(event.id)}>
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
          <section id="seats">
            <h2>Chọn khu và ghế</h2>
            <p>
              Chọn khu ngồi trên sơ đồ để xem góc nhìn ra sân khấu, sau đó chọn
              từng ghế. Mỗi đơn tối đa 10 ghế, ghế được giữ 10 phút khi bạn tiếp
              tục sang bước thông tin.
            </p>
            <SeatSelector
              event={event}
              selected={picked}
              onChange={setPicked}
              salesClosed={salesClosed}
            />
          </section>
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
                  timeLabel(
                    new Date(Date.parse(event.startTime) - 3_600_000).toISOString(),
                  ),
                  "Mở cổng soát vé",
                  "Cổng check-in mở trước giờ diễn 60 phút. Chuẩn bị sẵn mã QR trong Ví vé.",
                ],
                [
                  timeLabel(event.startTime),
                  "Bắt đầu chương trình",
                  "Cùng hòa nhịp với những màn trình diễn mở đầu.",
                ],
                [
                  timeLabel(event.endTime),
                  "Khép lại cuộc hẹn",
                  "Cảm ơn bạn đã tạo nên một đêm đáng nhớ.",
                ],
              ].map(([time, title, desc]) => (
                <div key={title}>
                  <time>{time}</time>
                  <div>
                    <h4>{title}</h4>
                    <p>{desc}</p>
                  </div>
                </div>
              ))}
            </div>
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
            Chọn khu và ghế ở mục “Chọn khu và ghế”. Ghế đã chọn hiện ở đây.
          </p>
          {!groups.length && (
            <a className="seat-summary-empty" href="#seats">
              Chưa chọn ghế nào
              <ArrowRight size={14} />
            </a>
          )}
          {groups.map(({ type: t, seats }) => (
            <motion.div layout key={t.id} className="ticket-choice selected">
              <div>
                <div className="flex items-center gap-2">
                  <h3>{t.name}</h3>
                  {t.name === "VIP" && <Sparkles size={12} />}
                </div>
                <strong>{money(t.price * seats.length)}</strong>
                <div className="seat-tags">
                  {seats.map((code) => (
                    <button
                      key={code}
                      type="button"
                      aria-label={`Bỏ ghế ${code}`}
                      onClick={() =>
                        setPicked(picked.filter((x) => x !== `${t.id}:${code}`))
                      }
                    >
                      {code}
                      <X size={10} />
                    </button>
                  ))}
                </div>
              </div>
              <small>
                {seats.length} × {money(t.price)}
              </small>
            </motion.div>
          ))}
          <div className="booking-total">
            <span>
              Tạm tính <small>({count} ghế)</small>
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
              Giữ ghế 10 phút
            </span>
          </div>
          <p className="text-[10px] muted text-center mt-3">
            Tổng tiền cuối cùng do hệ thống xác nhận khi giữ ghế.
          </p>
        </aside>
      </div>
    </>
  );
}
