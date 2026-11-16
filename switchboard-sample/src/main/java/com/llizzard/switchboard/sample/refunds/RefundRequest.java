package com.llizzard.switchboard.sample.refunds;

import java.math.BigDecimal;

/** Refund request payload. */
public record RefundRequest(String orderId, BigDecimal amount) {
}
