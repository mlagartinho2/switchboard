package com.llizzard.switchboard.sample.refunds;

import com.llizzard.switchboard.SwitchboardGroup;

/** The refunds dispatch group: one contract, several refund use cases. */
public final class RefundGroup {

    public static final SwitchboardGroup<RefundRequest, RefundResponse> REFUNDS =
            SwitchboardGroup.of("refunds", RefundRequest.class, RefundResponse.class);

    private RefundGroup() {
    }
}
