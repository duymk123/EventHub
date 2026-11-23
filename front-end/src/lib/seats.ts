import type { TakenSeatState, TicketType, TierName } from "@/types";

/** Giới hạn theo quyết định C12 (docs/project-clarifications.md). */
export const MAX_SEATS_PER_ORDER = 10;
export const MAX_ROWS = 30;
export const MAX_SEATS_PER_ROW = 60;

export type SeatState = "AVAILABLE" | TakenSeatState;

export interface SeatCell {
  id: string;
  code: string;
  number: number;
  state: SeatState;
}

export interface SeatRow {
  index: number;
  label: string;
  seats: SeatCell[];
}

/** 1 → A, 26 → Z, 27 → AA. */
export function rowLabel(index: number): string {
  let n = index;
  let label = "";
  while (n > 0) {
    const r = (n - 1) % 26;
    label = String.fromCharCode(65 + r) + label;
    n = Math.floor((n - 1) / 26);
  }
  return label;
}

export const seatCode = (row: number, seat: number) =>
  `${rowLabel(row)}-${String(seat).padStart(2, "0")}`;

export const seatId = (tierId: string, code: string) => `${tierId}:${code}`;

export function parseSeatId(id: string): { tierId: string; code: string } {
  const at = id.lastIndexOf(":");
  return { tierId: id.slice(0, at), code: id.slice(at + 1) };
}

/** Mọi mã ghế hợp lệ của một zone theo cấu hình hàng × ghế. */
export function seatCodes(t: Pick<TicketType, "rowCount" | "seatsPerRow">) {
  const codes: string[] = [];
  for (let r = 1; r <= t.rowCount; r++)
    for (let c = 1; c <= t.seatsPerRow; c++) codes.push(seatCode(r, c));
  return codes;
}

/** A → 1, Z → 26, AA → 27. */
function rowIndexOf(label: string) {
  let n = 0;
  for (const ch of label) n = n * 26 + (ch.charCodeAt(0) - 64);
  return n;
}

export function hasSeat(t: TicketType, code: string) {
  const match = /^([A-Z]+)-(\d+)$/.exec(code);
  if (!match) return false;
  const row = rowIndexOf(match[1]);
  const seat = Number(match[2]);
  return (
    row >= 1 &&
    row <= t.rowCount &&
    seat >= 1 &&
    seat <= t.seatsPerRow &&
    seatCode(row, seat) === code
  );
}

export function seatState(t: TicketType, code: string): SeatState {
  return t.taken[code] ?? "AVAILABLE";
}

export function seatRows(t: TicketType): SeatRow[] {
  return Array.from({ length: t.rowCount }, (_, i) => {
    const index = i + 1;
    return {
      index,
      label: rowLabel(index),
      seats: Array.from({ length: t.seatsPerRow }, (_, j) => {
        const code = seatCode(index, j + 1);
        return {
          id: seatId(t.id, code),
          code,
          number: j + 1,
          state: seatState(t, code),
        };
      }),
    };
  });
}

/** Trả hạng vé với `taken` mới, đồng bộ lại quota và số ghế trống. */
export function withTaken(
  t: TicketType,
  taken: Record<string, TakenSeatState>,
): TicketType {
  const quota = t.rowCount * t.seatsPerRow;
  return {
    ...t,
    taken,
    quota,
    available: Math.max(0, quota - Object.keys(taken).length),
  };
}

export const zoneHint: Record<TierName, string> = {
  VIP: "Sát sân khấu",
  PREMIUM: "Tầm nhìn trọn vẹn",
  STANDARD: "Hòa nhịp cùng đám đông",
};
