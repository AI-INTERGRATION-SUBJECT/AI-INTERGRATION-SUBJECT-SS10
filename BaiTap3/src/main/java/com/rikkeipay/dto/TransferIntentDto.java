package com.rikkeipay.dto;

import java.math.BigDecimal;

/**
 * DTO đại diện cho kết quả bóc tách ý định chuyển khoản từ Prompt Registry.
 */
public record TransferIntentDto(
    String status,              // 'VALID', 'REJECTED_INSUFFICIENT_FUNDS', 'FRAUDULENT_INPUT', 'MISSING_SLOTS'
    String senderName,
    String receiverAccountNumber,
    String bankCode,
    BigDecimal amount,
    String description,
    String message
) {}
