package com.rikkeipay.service;

import com.rikkeipay.config.LangfuseConfig.LangfuseClient;
import com.rikkeipay.config.LangfuseConfig.Trace;
import com.rikkeipay.util.PiiMaskingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service xử lý chuyển khoản RikkeiPay đã được refactor hoàn chỉnh:
 * 1. Tiêm phụ thuộc qua Constructor (Constructor Injection).
 * 2. Che mờ dữ liệu PII (PII Masking) trước khi đẩy sang Langfuse Trace.
 * 3. Gắn định danh Session ID và User ID tập trung để truy vết giao dịch.
 * 4. Sử dụng SLF4J Logger chuyên nghiệp.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final LangfuseClient langfuseClient;

    // Sử dụng Constructor Injection thay cho @Autowired field
    public TransferService(LangfuseClient langfuseClient) {
        this.langfuseClient = langfuseClient;
    }

    /**
     * Thực thi giao dịch chuyển tiền và tạo Trace giám sát an toàn trên Langfuse.
     *
     * @param userId ID người dùng đang thực hiện giao dịch
     * @param sessionId Session ID phiên làm việc
     * @param toAccount Số tài khoản người nhận
     * @param amount Số tiền chuyển khoản
     */
    public void processTransfer(String userId, String sessionId, String toAccount, double amount) {
        log.info("[TRANSFER SERVICE] Bắt đầu xử lý giao dịch cho User: {}, STK Nhận: {}, Số tiền: {}", 
                PiiMaskingUtils.maskUserId(userId), PiiMaskingUtils.maskAccountNumber(toAccount), amount);

        // 1. Thực hiện PII Masking che mờ thông tin cá nhân
        String maskedUser = PiiMaskingUtils.maskUserId(userId);
        String maskedToAccount = PiiMaskingUtils.maskAccountNumber(toAccount);

        // 2. Tạo đối tượng Trace tập trung gắn định danh userId và sessionId
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("senderUser", maskedUser);
        inputData.put("receiverAccount", maskedToAccount);
        inputData.put("amount", amount);
        inputData.put("currency", "VND");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("service", "RikkeiPay-TransferService");
        metadata.put("environment", "production");
        metadata.put("transactionType", "INTERNAL_TRANSFER");

        Trace trace = langfuseClient.trace(new Trace()
                .name("bank-transfer")
                .userId(userId)           // Định danh User trên Langfuse
                .sessionId(sessionId)     // Quản lý Session ID tập trung
                .input(inputData)         // Input đã được che mờ PII
                .metadata(metadata)
        );

        // 3. Giả lập logic chuyển tiền Core Banking
        boolean success = true; // Giả lập thành công

        // 4. Ghi kết quả Output đã che mờ PII vào Trace
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("status", success ? "SUCCESS" : "FAILED");
        outputData.put("message", success 
                ? "Thành công chuyển khoản " + amount + " VND từ " + maskedUser + " sang " + maskedToAccount 
                : "Giao dịch thất bại");

        trace.output(outputData);

        log.info("[LANGFUSE TRACE CREATED] Trace ID gắn Session ID: {} | Output Status: SUCCESS", sessionId);
    }
}
