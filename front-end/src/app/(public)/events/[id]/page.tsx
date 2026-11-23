import { EventDetail } from "@/components/features/event/public";
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ seats?: string }>;
}) {
  const { id } = await params;
  const { seats } = await searchParams;
  // Quay lại từ checkout (ví dụ sau SEAT_TAKEN) giữ nguyên các ghế còn hợp lệ.
  const initialSeats = (seats ?? "").split(",").filter(Boolean).slice(0, 10);
  return <EventDetail id={id} initialSeats={initialSeats} />;
}
