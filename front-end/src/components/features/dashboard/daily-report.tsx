"use client";
import { useState } from "react";
import { Download } from "lucide-react";
import { Button } from "@/components/ui/button";
import { money } from "@/lib/utils";
import { Pagination } from "../shared";
import type { AnalyticsSeriesPoint } from "@/types/analytics";
/** Bảng số liệu theo kỳ (ngày/tháng) từ series của GET /analytics/overview, kỳ mới nhất lên đầu. */
export function DailyReport({
  points,
  monthly,
  exportRows,
}: {
  points: AnalyticsSeriesPoint[];
  monthly: boolean;
  exportRows: (rows: string[][], name: string) => void;
}) {
  const rows = [...points]
    .filter((p) => p.ticketsSold || p.ticketsRefunded || p.grossRevenue || p.refundedAmount)
    .reverse();
  const [requestedPage, setPage] = useState(0);
  const page = Math.min(
    requestedPage,
    Math.max(0, Math.ceil(rows.length / 8) - 1),
  );
  const label = (period: string) => period.split("-").reverse().join("/");
  return (
    <section className="panel mt-6">
      <div className="section-heading">
        <div>
          <h2>Báo cáo theo {monthly ? "tháng" : "ngày"}</h2>
          <p className="muted text-xs mt-2">
            Ngày thanh toán / hoàn tiền · Giờ Việt Nam
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          onClick={() =>
            exportRows(
              [
                [
                  monthly ? "Tháng" : "Ngày",
                  "Vé bán",
                  "Vé hoàn",
                  "Doanh thu gộp",
                  "Hoàn tiền",
                  "Doanh thu thuần",
                ],
                ...rows.map((r) => [
                  r.period,
                  String(r.ticketsSold),
                  String(r.ticketsRefunded),
                  String(r.grossRevenue),
                  String(r.refundedAmount),
                  String(r.netRevenue),
                ]),
              ],
              "eventhub-bao-cao-theo-ky.csv",
            )
          }
        >
          <Download size={14} />
          CSV
        </Button>
      </div>
      {rows.length ? (
        <>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>{monthly ? "THÁNG" : "NGÀY"}</th>
                  <th>VÉ BÁN</th>
                  <th>VÉ HOÀN</th>
                  <th>DOANH THU GỘP</th>
                  <th>HOÀN TIỀN</th>
                  <th>DOANH THU THUẦN</th>
                </tr>
              </thead>
              <tbody>
                {rows.slice(page * 8, (page + 1) * 8).map((r) => (
                  <tr key={r.period}>
                    <td>{label(r.period)}</td>
                    <td>{r.ticketsSold}</td>
                    <td>{r.ticketsRefunded}</td>
                    <td>{money(r.grossRevenue)}</td>
                    <td>{money(r.refundedAmount)}</td>
                    <td>{money(r.netRevenue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            total={rows.length}
            pageSize={8}
            onChange={setPage}
          />
        </>
      ) : (
        <p className="muted text-sm text-center py-8">
          Chưa có dữ liệu trong khoảng thời gian này.
        </p>
      )}
    </section>
  );
}
