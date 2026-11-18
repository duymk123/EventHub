package com.eventhub.ticket.dto;

import java.util.List;

public record ConfirmRequest(
        List<Holder> holders
) {
}
