package com.llizzard.switchboard.sample.refunds;

import org.springframework.stereotype.Component;

import com.llizzard.switchboard.SwitchboardContext;
import com.llizzard.switchboard.SwitchboardGroup;
import com.llizzard.switchboard.SwitchboardStrategy;

/** Expedited refund flow: settles faster and says when. */
@Component
public class ExpeditedRefundStrategy implements SwitchboardStrategy<RefundRequest, RefundResponse> {

    private static final int EXPECTED_SETTLEMENT_HOURS = 4;

    @Override
    public SwitchboardGroup<RefundRequest, RefundResponse> group() {
        return RefundGroup.REFUNDS;
    }

    @Override
    public String caseKey() {
        return "EXPEDITED";
    }

    @Override
    public RefundResponse execute(RefundRequest request, SwitchboardContext context) {
        return new RefundResponse("ExpeditedRefundStrategy", "REFUNDED", EXPECTED_SETTLEMENT_HOURS);
    }
}
