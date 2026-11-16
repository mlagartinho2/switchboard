package com.llizzard.switchboard.sample.payments;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.llizzard.switchboard.SwitchboardDispatcher;

/** One endpoint for every payment use case: no per-use-case branching. */
@RestController
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
