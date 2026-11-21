package com.eventhub.ticket.controller;

import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.common.ScanType;
import com.eventhub.ticket.dto.request.GateScanRequest;
import com.eventhub.ticket.dto.response.GateScanResponse;
import com.eventhub.ticket.service.GateService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gate")
public class GateController {
    private final GateService gateService;

    public GateController(GateService gateService) {
        this.gateService = gateService;
    }

    @PostMapping("/check-in")
    public GateScanResponse checkIn(@AuthenticationPrincipal Actor actor, @Valid @RequestBody GateScanRequest request) {
        return gateService.scan(actor, request, ScanType.CHECK_IN);
    }

    @PostMapping("/check-out")
    public GateScanResponse checkOut(@AuthenticationPrincipal Actor actor, @Valid @RequestBody GateScanRequest request) {
        return gateService.scan(actor, request, ScanType.CHECK_OUT);
    }
}
