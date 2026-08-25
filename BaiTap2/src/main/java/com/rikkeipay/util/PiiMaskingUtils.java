package com.rikkeipay.util;

/**
 * Utility class hỗ trợ che mờ thông tin cá nhân/nhạy cảm (PII Masking)
 * trước khi đẩy dữ liệu Telemetry sang hạ tầng giám sát Langfuse.
 */
public class PiiMaskingUtils {

    /**
     * Che mờ số tài khoản ngân hàng (Ví dụ: '9876543210' -> '987****210').
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "***";
        }
        String clean = accountNumber.trim();
        if (clean.length() <= 4) {
            return "****";
        }
        int len = clean.length();
        return clean.substring(0, 3) + "****" + clean.substring(len - 3);
    }

    /**
     * Che mờ tên/ID người dùng (Ví dụ: 'NguyenVanA' -> 'N***A').
     */
    public static String maskUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "***";
        }
        String clean = userId.trim();
        if (clean.length() <= 2) {
            return "*";
        }
        return clean.charAt(0) + "***" + clean.charAt(clean.length() - 1);
    }
}
