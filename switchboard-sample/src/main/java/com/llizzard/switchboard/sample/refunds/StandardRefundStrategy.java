package com.llizzard.switchboard.sample.refunds;

import org.springframework.stereotype.Component;

import com.llizzard.switchboard.SwitchboardContext;
import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardStrategy;

/** Standard refund flow. */
@Component
public class StandardRefundStrategy implements SwitchboardStrategy<RefundRequest, RefundResponse> {

    @Override
    public SwitchboardGroup<RefundRequest, RefundResponse> group() {
        return RefundGroup.REFUNDS;
    }

    @Override
    public String caseKey() {
        return "STANDARD";
    }

    @Override
    public RefundResponse execute(RefundRequest request, SwitchboardContext context) {
        return new RefundResponse("StandardRefundStrategy", "REFUNDED", null);
    }
}
