import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
export const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
    value,
  );
export const dateLabel = (value: string, short = false) =>
  new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: short ? "short" : "long",
    ...(!short ? { year: "numeric" as const } : {}),
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date(value));
export const timeLabel = (value: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date(value));
export const wait = (ms = 500) =>
  new Promise<void>((resolve) => setTimeout(resolve, ms));
