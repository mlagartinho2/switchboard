package com.llizzard.switchboard.sample.refunds;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.llizzard.switchboard.SwitchboardDispatcher;

/**
 * One endpoint for every refund use case: no per-use-case branching.
 * Present only while Switchboard is on, so the application still starts with
 * {@code switchboard.enabled=false} (research §R11).
 */
@RestController
@ConditionalOnProperty(prefix = "switchboard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RefundController {

    private final SwitchboardDispatcher dispatcher;

    public RefundController(SwitchboardDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/api/refunds")
    public RefundResponse refund(HttpServletRequest http, @RequestBody RefundRequest body) {
        return dispatcher.dispatch(RefundGroup.REFUNDS, http, body);
    }
}
