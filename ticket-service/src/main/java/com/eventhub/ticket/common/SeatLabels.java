package com.eventhub.ticket.common;

/** Nhãn hàng/ghế theo C12: hàng 1 → A, 26 → Z, 27 → AA; ghế hiển thị "B-07". */
public final class SeatLabels {
    /** Giới hạn sơ đồ mỗi zone (docs/project-clarifications.md, C12). */
    public static final int MAX_ROWS = 30;
    public static final int MAX_SEATS_PER_ROW = 60;
    public static final int MAX_SEATS_PER_ORDER = 10;

    private SeatLabels() {
    }

    public static String rowLabel(int rowIndex) {
        StringBuilder label = new StringBuilder();
        int n = rowIndex;
        while (n > 0) {
            int remainder = (n - 1) % 26;
            label.insert(0, (char) ('A' + remainder));
            n = (n - 1) / 26;
        }
        return label.toString();
    }

    public static String seatLabel(String rowLabel, int seatNumber) {
        return rowLabel + "-" + (seatNumber < 10 ? "0" + seatNumber : String.valueOf(seatNumber));
    }
}
