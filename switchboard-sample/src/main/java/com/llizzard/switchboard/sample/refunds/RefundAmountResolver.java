package com.llizzard.switchboard.sample.refunds;

import java.math.BigDecimal;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardResolver;

/** Routes refunds above 1000 to {@code EXPEDITED}. Consulted after the header resolver. */
@Component
@Order(2)
public class RefundAmountResolver implements SwitchboardResolver<RefundRequest> {

    private static final BigDecimal EXPEDITED_THRESHOLD = new BigDecimal("1000");

    @Override
    public SwitchboardGroup<RefundRequest, ?> group() {
        return RefundGroup.REFUNDS;
    }

    @Override
    public String name() {
        return "refund-amount";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest httpRequest, RefundRequest payload) {
        if (payload == null || payload.amount() == null) {
            return Optional.empty();
        }
        return payload.amount().compareTo(EXPEDITED_THRESHOLD) > 0 ? Optional.of("EXPEDITED") : Optional.empty();
    }
}
