package com.eventhub.order.service;

import com.eventhub.order.entity.OrderItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * C04: hoàn 70% giá mua theo snapshot order_items. Tổng = round(Σ line_total × 0.70, 2, HALF_UP); phân bổ theo 70%
 * line_total từng hạng, cắt xuống 0,01 rồi chia phần lẻ cho hạng có phần dư lớn nhất (hòa thì ticketTypeId tăng dần).
 * Tính lại từ snapshot luôn ra cùng kết quả, nên thử lại không đổi số tiền.
 */
public final class RefundAllocation {
    public static final BigDecimal RATE = new BigDecimal("0.70");
    private static final BigDecimal CENT = new BigDecimal("0.01");

    public record Line(UUID ticketTypeId, String ticketTypeName, int quantity, BigDecimal refundedAmount) {
    }

    private RefundAllocation() {
    }

    public static BigDecimal total(List<OrderItem> items) {
        BigDecimal sum = items.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.multiply(RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public static List<Line> allocate(List<OrderItem> items) {
        BigDecimal total = total(items);
        List<OrderItem> sorted = items.stream().sorted(Comparator.comparing(OrderItem::getTicketTypeId)).toList();
        Map<OrderItem, BigDecimal> base = new LinkedHashMap<>();
        Map<OrderItem, BigDecimal> remainder = new LinkedHashMap<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (OrderItem item : sorted) {
            BigDecimal raw = item.getLineTotal().multiply(RATE);
            BigDecimal floor = raw.setScale(2, RoundingMode.DOWN);
            base.put(item, floor);
            remainder.put(item, raw.subtract(floor));
            allocated = allocated.add(floor);
        }
        int cents = total.subtract(allocated).divide(CENT, 0, RoundingMode.UNNECESSARY).intValueExact();
        List<OrderItem> byRemainder = new ArrayList<>(sorted);
        byRemainder.sort(Comparator.comparing((OrderItem item) -> remainder.get(item)).reversed()
                .thenComparing(OrderItem::getTicketTypeId));
        for (int i = 0; i < cents; i++) {
            OrderItem item = byRemainder.get(i % byRemainder.size());
            base.put(item, base.get(item).add(CENT));
        }
        return sorted.stream().map(item -> new Line(item.getTicketTypeId(), item.getTicketTypeName(),
                item.getQuantity(), base.get(item))).toList();
    }
}
