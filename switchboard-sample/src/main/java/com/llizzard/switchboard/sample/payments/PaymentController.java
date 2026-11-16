package com.llizzard.switchboard.sample.payments;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.llizzard.switchboard.SwitchboardDispatcher;

/**
 * One endpoint for every payment use case: no per-use-case branching.
 * Present only while Switchboard is on, so the application still starts with
 * {@code switchboard.enabled=false} (research §R11).
 */
@RestController
@ConditionalOnProperty(prefix = "switchboard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentController {

    private final SwitchboardDispatcher dispatcher;

    public PaymentController(SwitchboardDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/api/payments")
    public PaymentResponse pay(HttpServletRequest http, @RequestBody PaymentRequest body) {
        return dispatcher.dispatch(PaymentGroup.PAYMENTS, http, body);
    }
}
