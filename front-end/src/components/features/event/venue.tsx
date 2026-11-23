"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { Armchair, Eye, RotateCcw, Sparkles, TriangleAlert } from "lucide-react";
import { toast } from "sonner";
import type { Event, TicketType, TierName } from "@/types";
import { cn, money } from "@/lib/utils";
import {
  MAX_SEATS_PER_ORDER,
  parseSeatId,
  seatRows,
  zoneHint,
  type SeatCell,
} from "@/lib/seats";

/*
 * Màn chọn khu ngồi dạng 3D (C12). Một địa điểm chung (C03) nên hình học cố định:
 * sân khấu ở một đầu, ba khu hình quạt VIP → PREMIUM → STANDARD xa dần và cao dần.
 * Kích thước khu trên hình không đổi theo số ghế của từng sự kiện; số ghế thật nằm ở sơ đồ ghế bên dưới.
 */
const ORDER: TierName[] = ["VIP", "PREMIUM", "STANDARD"];
const FLOOR_W = 800;
const FLOOR_H = 560;
const CX = 400;
const CY = 34;
const RINGS: Record<TierName, [number, number]> = {
  VIP: [96, 176],
  PREMIUM: [192, 300],
  STANDARD: [316, 452],
};
const SPAN: [number, number] = [24, 156];
/** Độ cao bậc ghế (px theo trục Z) để khu phía sau nằm cao hơn. */
const LIFT: Record<TierName, number> = { VIP: 0, PREMIUM: 26, STANDARD: 58 };
type Camera = { rotateX: number; scale: number; y: number; originY: number };
const OVERVIEW: Camera = { rotateX: 54, scale: 0.94, y: -6, originY: 0.5 };
/** Camera "bay" tới khu: nghiêng thấp hơn, phóng to quanh khu và đẩy khu xuống gần người xem. */
const CAMERA: Record<TierName, Camera> = {
  VIP: { rotateX: 72, scale: 2.3, y: 150, originY: 0.3 },
  PREMIUM: { rotateX: 69, scale: 1.8, y: 135, originY: 0.48 },
  STANDARD: { rotateX: 64, scale: 1.42, y: 105, originY: 0.72 },
};

/** Làm tròn 2 chữ số để server và trình duyệt ra cùng chuỗi tọa độ (tránh lệch hydration). */
const round = (n: number) => Math.round(n * 100) / 100;
const polar = (r: number, deg: number): [number, number] => [
  round(CX + r * Math.cos((deg * Math.PI) / 180)),
  round(CY + r * Math.sin((deg * Math.PI) / 180)),
];

function sectorPath([r0, r1]: [number, number]) {
  const [x0, y0] = polar(r1, SPAN[0]);
  const [x1, y1] = polar(r1, SPAN[1]);
  const [x2, y2] = polar(r0, SPAN[1]);
  const [x3, y3] = polar(r0, SPAN[0]);
  return `M${x0},${y0} A${r1},${r1} 0 0 1 ${x1},${y1} L${x2},${y2} A${r0},${r0} 0 0 0 ${x3},${y3} Z`;
}

/** Chấm ghế trang trí theo vòng cung; tỉ lệ chấm mờ phản ánh mức đã bán của khu. */
function decorativeSeats([r0, r1]: [number, number], soldRatio: number) {
  const dots: { x: number; y: number; taken: boolean }[] = [];
  let i = 0;
  for (let r = r0 + 10; r <= r1 - 8; r += 15) {
    const arc = ((SPAN[1] - SPAN[0] - 4) * Math.PI * r) / 180;
    const count = Math.max(4, Math.floor(arc / 15));
    for (let k = 0; k < count; k++) {
      const deg = SPAN[0] + 2 + ((SPAN[1] - SPAN[0] - 4) * (k + 0.5)) / count;
      const [x, y] = polar(r, deg);
      dots.push({ x, y, taken: (i * 37) % 100 < soldRatio * 100 });
      i++;
    }
  }
  return dots;
}

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(FLOOR_W);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) =>
      setWidth(entry.contentRect.width),
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return { ref, width };
}

function zoneState(t: TicketType | undefined, salesClosed: boolean) {
  if (!t || t.quota === 0) return "Chưa mở bán";
  if (!t.active) return "Tạm ngừng bán";
  if (salesClosed) return "Đã đóng bán";
  if (!t.available) return "Hết ghế";
  return null;
}

export function SeatSelector({
  event,
  selected,
  onChange,
  salesClosed,
}: {
  event: Event;
  selected: string[];
  onChange: (next: string[]) => void;
  salesClosed: boolean;
}) {
  const reduceMotion = useReducedMotion();
  const types = useMemo(
    () =>
      Object.fromEntries(event.ticketTypes.map((t) => [t.name, t])) as Partial<
        Record<TierName, TicketType>
      >,
    [event.ticketTypes],
  );
  const firstPicked = selected[0]
    ? event.ticketTypes.find((t) => t.id === parseSeatId(selected[0]).tierId)
        ?.name
    : undefined;
  const [focus, setFocus] = useState<TierName | null>(firstPicked ?? null);
  const [hover, setHover] = useState<TierName | null>(null);
  const { ref, width } = useWidth<HTMLDivElement>();
  const fit = Math.min(1, (width - 16) / FLOOR_W);
  const camera = focus ? CAMERA[focus] : OVERVIEW;
  const focusType = focus ? types[focus] : undefined;

  function choose(name: TierName) {
    const t = types[name];
    const blocked = zoneState(t, salesClosed);
    setFocus(name);
    if (blocked) toast.info(`Khu ${name}: ${blocked.toLowerCase()}.`);
  }

  return (
    <div className="seat-selector">
      <div className="venue-3d" ref={ref}>
        <div className="venue-hud">
          <span className="venue-view-label">
            <Eye size={14} />
            {focus ? `Góc nhìn từ khu ${focus}` : "Toàn cảnh khán phòng"}
          </span>
          {focus && (
            <button
              type="button"
              className="venue-reset"
              onClick={() => setFocus(null)}
            >
              <RotateCcw size={13} />
              Toàn cảnh
            </button>
          )}
        </div>
        <motion.div
          className="venue-scene"
          style={{ width: FLOOR_W, height: FLOOR_H }}
          initial={false}
          animate={{ ...camera, scale: camera.scale * fit }}
          transition={
            reduceMotion
              ? { duration: 0 }
              : { duration: 1.15, ease: [0.22, 1, 0.36, 1] }
          }
        >
          <svg
            className="venue-floor"
            viewBox={`0 0 ${FLOOR_W} ${FLOOR_H}`}
            aria-hidden="true"
          >
            <defs>
              <radialGradient id="venue-glow" cx="50%" cy="0%" r="90%">
                <stop offset="0%" stopColor="#8b5cf6" stopOpacity="0.45" />
                <stop offset="60%" stopColor="#8b5cf6" stopOpacity="0.06" />
                <stop offset="100%" stopColor="#8b5cf6" stopOpacity="0" />
              </radialGradient>
              <linearGradient id="venue-beam" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#fde68a" stopOpacity="0.5" />
                <stop offset="100%" stopColor="#fde68a" stopOpacity="0" />
              </linearGradient>
            </defs>
            <rect width={FLOOR_W} height={FLOOR_H} rx="28" fill="#0b0b14" />
            <rect width={FLOOR_W} height={FLOOR_H} rx="28" fill="url(#venue-glow)" />
            {[300, 400, 500].map((x) => (
              <polygon
                key={x}
                points={`${x - 10},${CY + 8} ${x + 10},${CY + 8} ${x + 120 * Math.sign(x - 400) + 70},${FLOOR_H} ${x + 120 * Math.sign(x - 400) - 70},${FLOOR_H}`}
                fill="url(#venue-beam)"
                opacity="0.35"
              />
            ))}
            {(["PREMIUM", "STANDARD"] as const).map((name) => (
              <path
                key={name}
                d={sectorPath(RINGS[name])}
                fill="#000"
                opacity="0.55"
                transform="translate(0 10)"
              />
            ))}
            <rect x={CX - 150} y={CY - 26} width="300" height="44" rx="10" fill="#1f1f2e" stroke="#8b5cf6" strokeWidth="2" />
            <text x={CX} y={CY + 3} textAnchor="middle" className="venue-stage-text">
              SÂN KHẤU
            </text>
          </svg>
          {ORDER.map((name) => {
            const t = types[name];
            const blocked = zoneState(t, salesClosed);
            const sold = t && t.quota ? 1 - t.available / t.quota : 0;
            const [r0, r1] = RINGS[name];
            const [lx, ly] = polar((r0 + r1) / 2, 90);
            // Đứng ở khu đang xem thì các khu phía sau nằm sau lưng người xem: ẩn đi.
            const behind =
              focus !== null && ORDER.indexOf(name) > ORDER.indexOf(focus);
            return (
              <svg
                key={name}
                className={cn(
                  "venue-zone",
                  `zone-${name.toLowerCase()}`,
                  blocked && "blocked",
                  focus === name && "focused",
                  hover === name && "hovered",
                  behind && "behind",
                )}
                aria-hidden={behind || undefined}
                style={{ transform: `translateZ(${LIFT[name]}px)` }}
                viewBox={`0 0 ${FLOOR_W} ${FLOOR_H}`}
              >
                <path
                  className="venue-zone-shape"
                  d={sectorPath(RINGS[name])}
                  role="button"
                  tabIndex={-1}
                  aria-label={`Khu ${name}`}
                  onClick={() => choose(name)}
                  onPointerEnter={() => setHover(name)}
                  onPointerLeave={() => setHover(null)}
                />
                {decorativeSeats(RINGS[name], sold).map((d, i) => (
                  <circle
                    key={i}
                    cx={d.x}
                    cy={d.y}
                    r="3.4"
                    className={d.taken ? "dot-taken" : "dot-free"}
                  />
                ))}
                <text x={lx} y={ly - 6} textAnchor="middle" className="venue-zone-name">
                  {name}
                </text>
                <text x={lx} y={ly + 20} textAnchor="middle" className="venue-zone-price">
                  {blocked ?? (t ? money(t.price) : "")}
                </text>
              </svg>
            );
          })}
        </motion.div>
      </div>

      <div className="zone-chips" role="group" aria-label="Chọn khu ngồi">
        {ORDER.map((name) => {
          const t = types[name];
          const blocked = zoneState(t, salesClosed);
          const picked = selected.filter(
            (s) => t && parseSeatId(s).tierId === t.id,
          ).length;
          return (
            <button
              type="button"
              key={name}
              className={cn(
                "zone-chip",
                `zone-${name.toLowerCase()}`,
                focus === name && "active",
                blocked && "blocked",
              )}
              aria-pressed={focus === name}
              onClick={() => choose(name)}
              onPointerEnter={() => setHover(name)}
              onPointerLeave={() => setHover(null)}
            >
              <span className="zone-chip-dot" />
              <span className="zone-chip-body">
                <b>
                  {name}
                  {name === "VIP" && <Sparkles size={11} />}
                </b>
                <small>{t ? money(t.price) : "—"}</small>
              </span>
              <span className="zone-chip-meta">
                {blocked ??
                  (t ? `Còn ${t.available}/${t.quota} ghế` : "Chưa mở bán")}
                {picked > 0 && <em>Đã chọn {picked}</em>}
              </span>
            </button>
          );
        })}
      </div>

      <AnimatePresence mode="wait" initial={false}>
        {focus && focusType ? (
          <motion.div
            key={focus}
            initial={reduceMotion ? false : { opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            exit={reduceMotion ? { opacity: 0 } : { opacity: 0, y: -8 }}
            transition={{ duration: reduceMotion ? 0 : 0.28 }}
          >
            <SeatGrid
              type={focusType}
              selected={selected}
              onChange={onChange}
              blocked={zoneState(focusType, salesClosed)}
            />
          </motion.div>
        ) : (
          <motion.p
            key="hint"
            className="seat-hint"
            initial={false}
            animate={{ opacity: 1 }}
          >
            <Armchair size={15} />
            Chọn một khu trên sơ đồ để xem góc nhìn và chọn ghế.
          </motion.p>
        )}
      </AnimatePresence>
    </div>
  );
}

function SeatGrid({
  type,
  selected,
  onChange,
  blocked,
}: {
  type: TicketType;
  selected: string[];
  onChange: (next: string[]) => void;
  blocked: string | null;
}) {
  const rows = seatRows(type);
  const aisleAfter =
    type.seatsPerRow >= 8 ? Math.ceil(type.seatsPerRow / 2) : null;
  const lost = selected.filter((id) => {
    const { tierId, code } = parseSeatId(id);
    return tierId === type.id && type.taken[code];
  });

  function toggle(seat: SeatCell) {
    if (selected.includes(seat.id)) {
      onChange(selected.filter((s) => s !== seat.id));
      return;
    }
    if (blocked || seat.state !== "AVAILABLE") return;
    if (selected.length >= MAX_SEATS_PER_ORDER) {
      toast.info(`Mỗi đơn được chọn tối đa ${MAX_SEATS_PER_ORDER} ghế.`);
      return;
    }
    onChange([...selected, seat.id]);
  }

  return (
    <section
      className={cn("seat-panel", `zone-${type.name.toLowerCase()}`)}
      aria-label={`Sơ đồ ghế khu ${type.name}`}
    >
      <header className="seat-panel-head">
        <div>
          <span className={cn("zone-badge", `zone-${type.name.toLowerCase()}`)}>
            {type.name}
          </span>
          <h3>
            {money(type.price)}
            <small> / ghế</small>
          </h3>
          <p>
            {zoneHint[type.name]} · {type.rowCount} hàng × {type.seatsPerRow}{" "}
            ghế · còn {type.available} ghế trống
          </p>
        </div>
        {blocked && <span className="seat-blocked">{blocked}</span>}
      </header>
      {lost.length > 0 && (
        <p className="seat-lost" role="status">
          <TriangleAlert size={15} />
          Ghế {lost.map((id) => parseSeatId(id).code).join(", ")} vừa có người
          giữ. Bấm vào ghế đó để bỏ chọn rồi chọn ghế khác.
        </p>
      )}
      <div className="seat-stage-bar">SÂN KHẤU</div>
      <div className="seat-grid-scroll">
        <div className="seat-grid">
          {rows.map((row) => (
            <div className="seat-row" key={row.index}>
              <span className="seat-row-label">{row.label}</span>
              {row.seats.map((seat) => {
                const isPicked = selected.includes(seat.id);
                const lostSeat = isPicked && seat.state !== "AVAILABLE";
                return (
                  <span key={seat.id} className="seat-slot">
                    <button
                      type="button"
                      className={cn(
                        "seat",
                        seat.state === "AVAILABLE" ? "free" : "taken",
                        isPicked && "picked",
                        lostSeat && "lost",
                      )}
                      aria-pressed={isPicked}
                      aria-label={`Ghế ${seat.code}${seat.state === "SOLD" ? ", đã bán" : seat.state === "HELD" ? ", đang có người giữ" : isPicked ? ", bạn đã chọn" : ", còn trống"}`}
                      title={`Ghế ${seat.code}`}
                      disabled={
                        !isPicked &&
                        (Boolean(blocked) || seat.state !== "AVAILABLE")
                      }
                      onClick={() => toggle(seat)}
                    >
                      {seat.number}
                    </button>
                    {aisleAfter === seat.number && (
                      <span className="seat-aisle" aria-hidden="true" />
                    )}
                  </span>
                );
              })}
              <span className="seat-row-label">{row.label}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="seat-legend">
        <span>
          <i className="seat free" /> Còn trống
        </span>
        <span>
          <i className="seat taken" /> Đã có người
        </span>
        <span>
          <i className="seat picked" /> Bạn chọn
        </span>
      </div>
    </section>
  );
}
