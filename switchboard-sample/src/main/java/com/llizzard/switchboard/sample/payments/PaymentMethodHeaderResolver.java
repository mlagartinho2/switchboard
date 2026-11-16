package com.llizzard.switchboard.sample.payments;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardResolver;

/** Reads the payment use case from the {@code X-Payment-Method} header. */
@Component
public class PaymentMethodHeaderResolver implements SwitchboardResolver<PaymentRequest> {

    static final String HEADER = "X-Payment-Method";

    @Override
    public SwitchboardGroup<PaymentRequest, ?> group() {
        return PaymentGroup.PAYMENTS;
    }

    @Override
    public String name() {
        return "payment-method-header";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest httpRequest, PaymentRequest payload) {
        return Optional.ofNullable(httpRequest.getHeader(HEADER));
    }
}
