# BÀI TẬP 2: DÒ LỖI & TỐI ƯU CODE TÍCH HỢP SDK TRACING

---

## 🔍 **1. BẢN PHÂN TÍCH CHI TIẾT CÁC LỖI BẢO MẬT VÀ LỖI LOGIC TRONG CODE CŨ**

### **1.1. Lỗ hổng Bảo mật Nghiêm trọng 1: Hardcode API Key trực tiếp trong Mã nguồn (`LangfuseConfig.java`)**
- **Đoạn code bị lỗi:**
  ```java
  return new LangfuseClient("pk-lf-1234567890abcdef", "sk-lf-0987654321fedcba", "https://cloud.langfuse.com");
  ```
- **Hậu quả:** 
  - Vi phạm nghiêm trọng nguyên tắc thiết kế *12-Factor App*. Khi mã nguồn được commit và push lên các Git Repository (GitHub/GitLab), bất kỳ ai có quyền truy cập mã nguồn đều có thể lấy cắp Secret Key.
  - Kẻ xấu có thể giả mạo dữ liệu Telemetry, đọc toàn bộ thông tin Trace hoặc xoá lịch sử giám sát LLM trên hạ tầng Langfuse.

---

### **1.2. Lỗ hổng Bảo mật Nghiêm trọng 2: Rò rỉ Thông tin Cá nhân / Nhạy cảm PII (`TransferService.java`)**
- **Đoạn code bị lỗi:**
  ```java
  trace.input("User " + user + " chuyển tiền cho " + toAccount + " số tiền " + amount);
  trace.output("Thành công chuyển khoản " + amount + " từ " + user + " sang " + toAccount);
  ```
- **Hậu quả:**
  - Đưa trực tiếp thông tin nhạy cảm của khách hàng dạng Plain-text (Tên/ID người dùng `user`, Số tài khoản nhận `toAccount`, Số tiền chuyển `amount`) lên hệ thống Log/Trace trung gian.
  - Vi phạm các tiêu chuẩn bảo mật dữ liệu ngành ngân hàng (PCI-DSS, GDPR, Luật An ninh mạng). Khi dữ liệu Trace bị lộ, hacker có thể dựng lại hành vi tài chính và số dư của khách hàng.

---

### **1.3. Lỗi Logic Kỹ thuật 3: Thiếu Định danh Session ID và User ID trên Trace**
- **Hậu quả:** 
  - Khi khởi tạo `new Trace().name("bank-transfer")`, đoạn code cũ không gán `.userId(...)` và `.sessionId(...)`.
  - Trên Langfuse Dashboard, tất cả các Trace sẽ đứng độc lập, không thể nhóm (group) theo từng phiên làm việc (Session) của người dùng. Điều này làm vô hiệu hóa khả năng truy vết chuỗi thoại đa lượt (Multi-turn Conversation Tracing).

---

### **1.4. Lỗi Kiến trúc 4: Sử dụng `@Autowired` Field Injection và `System.out.println`**
- Field Injection gây khó khăn khi viết Unit Test.
- `System.out.println` làm nghẽn luồng I/O trên server Production và không hỗ trợ định dạng Log theo cấp độ (INFO/WARN/ERROR).

---

## 💻 **2. MÃ NGUỒN JAVA SAU KHU REFACTOR TỐI ƯU**

### **2.1. Lớp Cấu hình Thuộc tính `LangfuseProperties.java`**
```java
package com.rikkeipay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Lớp cấu hình thuộc tính Langfuse nạp động từ file application.yml.
 * Loại bỏ hoàn toàn lỗi Hardcode API Key trực tiếp trong mã nguồn Java.
 */
@Configuration
@ConfigurationProperties(prefix = "langfuse")
public class LangfuseProperties {

    private String publicKey;
    private String secretKey;
    private String baseUrl = "http://localhost:3000";
    private boolean enabled = true;

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
```

---

### **2.2. Lớp Cấu hình Bean `LangfuseConfig.java`**
```java
package com.rikkeipay.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lớp cấu hình Bean LangfuseClient an toàn, nạp tham số từ LangfuseProperties.
 */
@Configuration
public class LangfuseConfig {

    private static final Logger log = LoggerFactory.getLogger(LangfuseConfig.class);

    private final LangfuseProperties properties;

    public LangfuseConfig(LangfuseProperties properties) {
        this.properties = properties;
    }

    @Bean
    public LangfuseClient langfuseClient() {
        log.info("[LANGFUSE CONFIG] Khởi tạo LangfuseClient an toàn với Base URL: {}", properties.getBaseUrl());
        return new LangfuseClient(
                properties.getPublicKey(),
                properties.getSecretKey(),
                properties.getBaseUrl()
        );
    }
}
```

---

### **2.3. Utility Che mờ PII `PiiMaskingUtils.java`**
```java
package com.rikkeipay.util;

/**
 * Utility class hỗ trợ che mờ thông tin cá nhân/nhạy cảm (PII Masking)
 * trước khi đẩy dữ liệu Telemetry sang hạ tầng giám sát Langfuse.
 */
public class PiiMaskingUtils {

    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) return "***";
        String clean = accountNumber.trim();
        if (clean.length() <= 4) return "****";
        return clean.substring(0, 3) + "****" + clean.substring(clean.length() - 3);
    }

    public static String maskUserId(String userId) {
        if (userId == null || userId.isBlank()) return "***";
        String clean = userId.trim();
        if (clean.length() <= 2) return "*";
        return clean.charAt(0) + "***" + clean.charAt(clean.length() - 1);
    }
}
```

---

### **2.4. Refactored `TransferService.java`**
```java
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
 * Service xử lý chuyển khoản RikkeiPay đã được refactor hoàn chỉnh.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private final LangfuseClient langfuseClient;

    public TransferService(LangfuseClient langfuseClient) {
        this.langfuseClient = langfuseClient;
    }

    public void processTransfer(String userId, String sessionId, String toAccount, double amount) {
        log.info("[TRANSFER SERVICE] Bắt đầu xử lý giao dịch cho User: {}", PiiMaskingUtils.maskUserId(userId));

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

        Trace trace = langfuseClient.trace(new Trace()
                .name("bank-transfer")
                .userId(userId)           // Định danh User trên Langfuse
                .sessionId(sessionId)     // Quản lý Session ID tập trung
                .input(inputData)         // Input đã được che mờ PII
                .metadata(metadata)
        );

        // 3. Giả lập logic chuyển tiền và ghi Output an toàn
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("status", "SUCCESS");
        outputData.put("message", "Thành công chuyển khoản " + amount + " VND từ " + maskedUser + " sang " + maskedToAccount);

        trace.output(outputData);

        log.info("[LANGFUSE TRACE CREATED] Trace ID gắn Session ID: {}", sessionId);
    }
}
```

---

## ⚙️ **3. TỆP CẤU HÌNH `application.yml` TƯƠNG ỨNG**

```yaml
spring:
  application:
    name: rikkeipay-langfuse-tracing

# Cấu hình Langfuse an toàn nạp từ biến môi trường (Environment Variables)
langfuse:
  public-key: ${LANGFUSE_PUBLIC_KEY:pk-lf-prod-default-key}
  secret-key: ${LANGFUSE_SECRET_KEY:sk-lf-prod-default-secret}
  base-url: ${LANGFUSE_BASE_URL:http://localhost:3000}
  enabled: true

server:
  port: 8080
```
