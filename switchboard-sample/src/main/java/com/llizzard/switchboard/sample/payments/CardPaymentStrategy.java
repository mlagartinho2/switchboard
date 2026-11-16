package com.llizzard.switchboard.sample.payments;

import org.springframework.stereotype.Component;

import com.llizzard.switchboard.SwitchboardContext;
import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardStrategy;

/** Card payment flow. */
@Component
public class CardPaymentStrategy implements SwitchboardStrategy<PaymentRequest, PaymentResponse> {

    @Override
    public SwitchboardGroup<PaymentRequest, PaymentResponse> group() {
        return PaymentGroup.PAYMENTS;
    }

    @Override
    public String caseKey() {
        return "CARD";
    }

    @Override
    public PaymentResponse execute(PaymentRequest request, SwitchboardContext context) {
        return new PaymentResponse("CardPaymentStrategy", "CAPTURED");
    }
}
