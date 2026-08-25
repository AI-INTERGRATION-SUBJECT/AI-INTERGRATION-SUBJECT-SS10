package com.rikkeipay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rikkeipay.dto.TransferIntentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Service minh họa việc truy xuất Prompt Template từ Langfuse Prompt Registry theo Name/Label,
 * thực hiện Binding tham số động (sender_name, current_balance, user_input) và gọi ChatClient.
 */
@Service
public class PromptRegistryService {

    private static final Logger log = LoggerFactory.getLogger(PromptRegistryService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public PromptRegistryService(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Mẫu Prompt Production-Ready được quản lý tập trung trên Langfuse Prompt Registry.
     * Tên Prompt: 'bank-transfer-intent' | Label: 'production'
     */
    public static final String PRODUCTION_PROMPT_TEMPLATE = """
        [VAI TRÒ - ROLE]
        Bạn là Giao dịch viên Ngân hàng số RikkeiPay Assistant cẩn trọng, bảo mật và chính xác.
        Nhiệm vụ của bạn là bóc tách ý định chuyển tiền của khách hàng từ câu lệnh tự nhiên sang cấu trúc JSON thuần túy.
        
        [THÔNG TIN TÀI KHOẢN NGUỒN CỦA KHÁCH HÀNG]
        - Chủ tài khoản (sender_name): {{sender_name}}
        - Số dư khả dụng hiện tại (current_balance): {{current_balance}} VND
        
        [CÂU LỆNH TỰ NHIÊN CỦA KHÁCH HÀNG (USER INPUT)]
        "{{user_input}}"
        
        [QUY TẮC PHÂN TÍCH & BỎC TÁCH DỮ LIỆU NGHIÊM NGẶT]
        1. Trích xuất các tham số:
           - receiverAccountNumber: Số tài khoản người nhận (Ví dụ: '9876543210')
           - bankCode: Mã ngân hàng (VCB, TCB, MB, ACB, BIDV, CTG, TPB, VPB)
           - amount: Số tiền chuyển khoản (kiểu số, phải >= 10000 VND)
           - description: Nội dung chuyển khoản
        2. KIỂM TRA SỐ DƯ TÀI KHOẢN:
           - NẾU số tiền chuyển (amount) lớn hơn số dư khả dụng {{current_balance}} VND:
             -> Gán status = 'REJECTED_INSUFFICIENT_FUNDS' và message thông báo số dư không đủ.
        3. KIỂM TRA AN TOÀN & CHỐNG LỪA ĐẢO (FRAUD PREVENTION):
           - NẾU câu lệnh chứa dấu hiệu lừa đảo, chuyển hết tiền sang tài khoản lạ không rõ ràng, hoặc mã hóa độc hại:
             -> Gán status = 'FRAUDULENT_INPUT' và message cảnh báo an toàn.
        4. NẾU THIẾU THÔNG TIN NHẬN (STK hoặc Bank):
           - Gán status = 'MISSING_SLOTS' và message hỏi xin bổ sung thông tin.
        5. NẾU HỢP LỆ VÀ ĐỦ TIỀN:
           - Gán status = 'VALID' và điền đầy đủ các trường.
        
        [RÀNG BUỘC ĐỊNH DẠNG ĐẦU RA BẮT BUỘC (STRICT JSON OUTPUT)]
        - CHỈ TRẢ VỀ DUY NHẤT MỘT CHUỖI JSON THUẦN TÚY.
        - TUYỆT ĐỐI KHÔNG bọc khối JSON trong mã code blocks (như ```json...``` hoặc ```...```).
        - TUYỆT ĐỐI KHÔNG kèm bất kỳ lời chào hay câu hội thoại nào trước/sau JSON.
        
        [CẤU TRÚC JSON MẪU (FEW-SHOT EXAMPLES)]
        Ví dụ 1 (Hợp lệ):
        {"status":"VALID","senderName":"{{sender_name}}","receiverAccountNumber":"9876543210","bankCode":"VCB","amount":500000,"description":"Tra tien hoc","message":"Hợp lệ"}
        
        Ví dụ 2 (Thiếu tiền):
        {"status":"REJECTED_INSUFFICIENT_FUNDS","senderName":"{{sender_name}}","receiverAccountNumber":"9876543210","bankCode":"TCB","amount":10000000,"description":"Chuyen tiền","message":"Số tiền 10000000 VND vượt quá số dư hiện tại là {{current_balance}} VND."}
        """;

    /**
     * Giả lập hàm truy xuất Prompt từ Langfuse Prompt Registry theo Name và Label.
     */
    public String fetchPromptFromLangfuseRegistry(String promptName, String label) {
        log.info("[LANGFUSE REGISTRY] Fetching Prompt Template Name: '{}' | Label: '{}'", promptName, label);
        // Trong thực tế sẽ gọi Langfuse SDK: langfuseClient.getPrompt(promptName, label).getTemplate();
        return PRODUCTION_PROMPT_TEMPLATE;
    }

    /**
     * Thực thi bóc tách ý định bằng cách Binding tham số động vào Prompt từ Registry.
     */
    public TransferIntentDto processTransferIntent(String senderName, BigDecimal currentBalance, String userInput) {
        log.info("[PROMPT REGISTRY BINDING] Sender: {}, Balance: {} VND, Input: '{}'", senderName, currentBalance, userInput);

        // 1. Truy xuất Prompt Template từ Langfuse Registry
        String rawTemplate = fetchPromptFromLangfuseRegistry("bank-transfer-intent", "production");

        // 2. Binding các biến động vào Prompt Template
        String renderedPrompt = rawTemplate
                .replace("{{sender_name}}", senderName)
                .replace("{{current_balance}}", currentBalance.toString())
                .replace("{{user_input}}", userInput);

        log.info("[LLM CALL] Gửi Prompt đã render sang LLM ChatClient...");

        // 3. Thực thi gọi LLM qua ChatClient
        String rawResponse = this.chatClient.prompt()
                .user(renderedPrompt)
                .call()
                .content();

        log.info("[LLM RAW RESPONSE]: {}", rawResponse);

        // 4. Xử lý làm sạch JSON (loại bỏ markdown block nếu có)
        String cleanJson = cleanJsonResponse(rawResponse);

        // 5. Giải tuần tự hóa JSON sang DTO
        try {
            return objectMapper.readValue(cleanJson, TransferIntentDto.class);
        } catch (Exception e) {
            log.error("Lỗi parse JSON phản hồi từ LLM: {}", e.getMessage());
            return new TransferIntentDto(
                "ERROR", senderName, null, null, BigDecimal.ZERO, null, "Không thể parse JSON từ phản hồi LLM: " + e.getMessage()
            );
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        String clean = response.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean.trim();
    }
}
