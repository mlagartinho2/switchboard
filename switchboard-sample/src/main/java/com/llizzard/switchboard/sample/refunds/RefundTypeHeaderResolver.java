package com.llizzard.switchboard.sample.refunds;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardResolver;

/** Reads the refund use case from the {@code X-Refund-Type} header. Consulted first. */
@Component
@Order(1)
public class RefundTypeHeaderResolver implements SwitchboardResolver<RefundRequest> {

    static final String HEADER = "X-Refund-Type";

    @Override
    public SwitchboardGroup<RefundRequest, ?> group() {
        return RefundGroup.REFUNDS;
    }

    @Override
    public String name() {
        return "refund-type-header";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest httpRequest, RefundRequest payload) {
        return Optional.ofNullable(httpRequest.getHeader(HEADER));
    }
}
