"use client";
import { useState } from "react";
import { Download } from "lucide-react";
import { Button } from "@/components/ui/button";
import { money } from "@/lib/utils";
import { dailySales } from "@/lib/mock/analytics";
import { Pagination } from "../shared";
import type { Order, Refund } from "@/types";
export function DailyReport({
  orders,
  refunds,
  minDate,
  exportRows,
}: {
  orders: Order[];
  refunds: Refund[];
  minDate: number;
  exportRows: (rows: string[][], name: string) => void;
}) {
  const rows = dailySales(orders, refunds, minDate);
  const [requestedPage, setPage] = useState(0);
  const page = Math.min(
    requestedPage,
    Math.max(0, Math.ceil(rows.length / 8) - 1),
  );
  return (
    <section className="panel mt-6">
      <div className="section-heading">
        <div>
          <h2>Báo cáo theo ngày và hạng vé</h2>
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
                  "Ngày",
                  "Hạng vé",
                  "Vé bán",
                  "Vé hoàn",
                  "Doanh thu gộp",
                  "Hoàn tiền",
                  "Doanh thu thuần",
                ],
                ...rows.map((r) => [
                  r.day,
                  r.tier,
                  String(r.sold),
                  String(r.refundedTickets),
                  String(r.gross),
                  String(r.refund),
                  String(r.net),
                ]),
              ],
              "eventhub-bao-cao-theo-ngay.csv",
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
                  <th>NGÀY</th>
                  <th>HẠNG VÉ</th>
                  <th>VÉ BÁN</th>
                  <th>VÉ HOÀN</th>
                  <th>DOANH THU GỘP</th>
                  <th>HOÀN TIỀN</th>
                  <th>DOANH THU THUẦN</th>
                </tr>
              </thead>
              <tbody>
                {rows.slice(page * 8, (page + 1) * 8).map((r) => (
                  <tr key={`${r.day}-${r.tier}`}>
                    <td>{r.day.split("-").reverse().join("/")}</td>
                    <td>{r.tier}</td>
                    <td>{r.sold}</td>
                    <td>{r.refundedTickets}</td>
                    <td>{money(r.gross)}</td>
                    <td>{money(r.refund)}</td>
                    <td>{money(r.net)}</td>
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
