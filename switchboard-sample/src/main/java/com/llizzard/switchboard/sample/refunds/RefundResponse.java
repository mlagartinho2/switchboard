package com.llizzard.switchboard.sample.refunds;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Refund response.
 *
 * @param handledBy the strategy that handled the refund
 * @param status the refund status
 * @param expectedSettlementHours set only by the {@code EXPEDITED} use case; omitted otherwise
 */
public record RefundResponse(String handledBy, String status,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer expectedSettlementHours) {
}
