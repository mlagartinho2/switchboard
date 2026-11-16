package com.llizzard.switchboard.sample.payments;

import java.math.BigDecimal;

/** Payment request payload. */
public record PaymentRequest(String orderId, BigDecimal amount) {
}
