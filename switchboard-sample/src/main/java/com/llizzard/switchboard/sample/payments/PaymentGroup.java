package com.llizzard.switchboard.sample.payments;

import com.llizzard.switchboard.SwitchboardGroup;

/** The payments dispatch group, fully isolated from refunds. */
public final class PaymentGroup {

    public static final SwitchboardGroup<PaymentRequest, PaymentResponse> PAYMENTS =
            SwitchboardGroup.of("payments", PaymentRequest.class, PaymentResponse.class);

    private PaymentGroup() {
    }
}
