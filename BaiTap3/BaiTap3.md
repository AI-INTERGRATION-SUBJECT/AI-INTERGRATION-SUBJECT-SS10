# BÀI TẬP 3: TỐI ƯU PROMPT DYNAMIC CỦA PROMPT REGISTRY

---

## 🔍 **1. BẢN PHÂN TÍCH ĐIỂM YẾU CỦA MẪU PROMPT CŨ TRÊN REGISTRY**

Mẫu prompt cũ đang lưu trên Registry:
```text
Hãy giúp tôi thực hiện chuyển khoản từ câu lệnh: {{user_input}}. Trả về JSON chứa: to, amount, bank.
```

Các điểm yếu chết người làm mất ổn định hệ thống ngân hàng:

1. **Thiếu Định danh Vai trò & Ngữ cảnh Chuyên môn (No System Role / Persona):**
   - Prompt cũ không khai báo vai trò cho AI (như *"Bạn là Giao dịch viên Ngân hàng số RikkeiPay cẩn trọng và bảo mật"*). Việc này khiến LLM hoạt động ở chế độ tự do, dễ đưa ra lời chào hay trò chuyện ngoài lề.

2. **Cấu trúc JSON Schema mờ nhạt, thiếu ràng buộc kiểu dữ liệu:**
   - Yêu cầu *"Trả về JSON chứa: to, amount, bank"* quá sơ sài. LLM không hề biết `amount` là kiểu số hay chuỗi, `bank` có danh mục hợp lệ nào, và tên các key có thể bị nhảy loạn (ví dụ: `account_number`, `receiver`, `to_user`).
   - Kết quả: Hàm Jackson ObjectMapper ở backend sẽ báo lỗi `UnrecognizedPropertyException` hoặc `JsonParseException` khi parse dữ liệu.

3. **Hoàn toàn không có khả năng chống Ảo tưởng (Hallucination) & Kiểm soát Lừa đảo (Fraud Prevention):**
   - Không được cung cấp mốc số dư khả dụng (`current_balance`). Nếu khách hàng có 5 triệu nhưng gõ *"Chuyển 100 triệu mua ô tô"*, LLM cũ vẫn ngây thơ trích xuất `amount: 100000000` mà không hề cảnh báo vượt quá số dư.
   - Không có kịch bản xử lý dữ liệu đầu vào lừa đảo, mã độc hoặc prompt injection.

4. **Thiếu Ràng buộc Định dạng JSON Thuần túy (Markdown Leakage):**
   - LLM sẽ tự động bọc chuỗi JSON vào khối code markdown ` ```json ... ``` ` hoặc tự thêm câu chào trước/sau JSON. Điều này làm văng ngoại lệ sập API ở tầng backend.

5. **Thiếu Ví dụ Mẫu (Few-Shot Examples):**
   - Không cung cấp các mẫu JSON đầu ra chuẩn cho các kịch bản Hợp lệ / Thất bại / Thiếu thông tin.

---

## 🛡️ **2. NỘI DUNG MẪU PROMPT TỐI ƯU HÓA (PRODUCTION-READY PROMPT)**

Mẫu Prompt mới được đưa lên **Langfuse Prompt Registry** (Tên: `bank-transfer-intent` | Label: `production`):

```text
[VAI TRÒ - ROLE]
Bạn là Giao dịch viên Ngân hàng số RikkeiPay Assistant cẩn trọng, bảo mật và chính xác.
Nhiệm vụ của bạn là bóc tách ý định chuyển tiền của khách hàng từ câu lệnh tự nhiên sang cấu trúc JSON thuần túy.

[THÔNG TIN TÀI KHOẢN NGUỒN CỦA KHÁCH HÀNG]
- Chủ tài khoản (sender_name): {{sender_name}}
- Số dư khả dụng hiện tại (current_balance): {{current_balance}} VND

[CÂU LỆNH TỰ NHIÊN CỦA KHÁCH HÀNG (USER INPUT)]
"{{user_input}}"

[QUY TẮC PHÂN TÍCH & BÓC TÁCH DỮ LIỆU NGHIÊM NGẶT]
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
```

---

## 💻 **3. MÃ NGUỒN JAVA HOÀN CHỈNH TRUY XUẤT REGISTRY VÀ BINDING THAM SỐ**

### **3.1. DTO Kết quả `TransferIntentDto.java`**
```java
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
```

---

### **3.2. Service Truy xuất & Binding `PromptRegistryService.java`**
```java
package com.rikkeipay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rikkeipay.dto.TransferIntentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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
     * Giả lập hàm truy xuất Prompt từ Langfuse Prompt Registry theo Name và Label.
     */
    public String fetchPromptFromLangfuseRegistry(String promptName, String label) {
        log.info("[LANGFUSE REGISTRY] Fetching Prompt Template Name: '{}' | Label: '{}'", promptName, label);
        // Trong thực tế sử dụng Langfuse SDK: langfuseClient.getPrompt(promptName, label).getTemplate();
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

        // 4. Xử lý làm sạch JSON và parse sang DTO
        String cleanJson = cleanJsonResponse(rawResponse);

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
```
