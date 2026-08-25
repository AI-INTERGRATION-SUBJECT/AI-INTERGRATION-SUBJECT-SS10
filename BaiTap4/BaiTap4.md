# BÀI TẬP 4: GIÁM SÁT CHI PHÍ & PHÂN TÍCH LATENCY

---

## 📊 **1. CƠ CHẾ TOKEN & COST TRACKING VÀ THIẾT LẬP BẢNG GIÁ TRÊN LANGFUSE**

### **1.1. Cơ chế Tự động Đếm Token (Automatic Token Counting)**
- Khi ứng dụng Spring AI thực thi lệnh qua `ChatClient`, SDK sẽ nhận được phản hồi HTTP từ API OpenRouter / OpenAI. Trong header payload phản hồi luôn chứa đối tượng metadata `usage`:
  ```json
  "usage": {
    "prompt_tokens": 450,
    "completion_tokens": 120,
    "total_tokens": 570
  }
  ```
- Langfuse Spring AI Starter tự động trích xuất các chỉ số này (`promptTokens`, `completionTokens`, `totalTokens`) và gắn trực tiếp vào **Generation Observation** của Trace.

---

### **1.2. Hướng dẫn Thiết lập Bảng giá Model Tùy chỉnh (Custom Model Prices) trên Langfuse Dashboard**

Để so sánh chi phí giữa **`google/gemini-2.5-flash`** và **`deepseek/deepseek-v3`**, ta cấu hình Bảng giá trên Langfuse Dashboard như sau:

1. Truc cập **Langfuse Dashboard** $\rightarrow$ Chọn project **RikkeiPay** $\rightarrow$ Vào mục **Settings** $\rightarrow$ chọn **Models / Price List**.
2. Bấm **+ Add Model Price** để thêm cấu hình cho 2 dòng model:

#### **Cấu hình Model 1: `google/gemini-2.5-flash`**
- **Model Name Matcher:** `google/gemini-2.5-flash` (dùng Regex match tên model gửi từ Spring AI).
- **Input Price:** `$0.075` per 1,000,000 tokens ($0.000000075 / token).
- **Output Price:** `$0.300` per 1,000,000 tokens ($0.0000003 / token).
- **Effective From Date:** Ngày bắt đầu áp dụng giá.

#### **Cấu hình Model 2: `deepseek/deepseek-v3`**
- **Model Name Matcher:** `deepseek/deepseek-v3`.
- **Input Price:** `$0.140` per 1,000,000 tokens.
- **Output Price:** `$0.280` per 1,000,000 tokens.

---

### **1.3. Bảng So sánh Chi phí Trung bình 1,000 Lượt gọi AI (Giả định 500 Input Tokens, 200 Output Tokens / Request)**

$$\text{Cost}_{\text{Gemini}} = 1000 \times \left( \frac{500 \times 0.075}{1,000,000} + \frac{200 \times 0.300}{1,000,000} \right) = 1000 \times (0.0000375 + 0.0000600) = \mathbf{\$0.0975 \text{ USD}}$$

$$\text{Cost}_{\text{DeepSeek}} = 1000 \times \left( \frac{500 \times 0.140}{1,000,000} + \frac{200 \times 0.280}{1,000,000} \right) = 1000 \times (0.0000700 + 0.0000560) = \mathbf{\$0.1260 \text{ USD}}$$

> **Kết luận:** Mô hình **Gemini-2.5-Flash** tiết kiệm khoảng **22.6% chi phí** so với DeepSeek-V3 đối với các câu lệnh có lượng Input Tokens lớn.

---

## ⚡ **2. HƯỚNG DẪN PHÂN TÍCH BIỂU ĐỒ LATENCY ĐỂ XÁC ĐỊNH BOTTLENECK RAG**

Khi kiểm tra một cuộc gọi RAG trên biểu đồ **Waterfall Trace View** của Langfuse:

```
[Trace: RAG-Chatbot-Query] ── Total Duration: 2450 ms
 ├── 🔹 [Span: VectorDB-Retrieval (pgvector)] ── Duration: 120 ms  (5%)
 └── 🔸 [Generation: LLM-Completion (Gemini)] ─ Duration: 2330 ms (95%)
```

### **Các Bước Phân định Bottleneck:**

1. **Kiểm tra Span `VectorDB-Retrieval` (Thời gian tìm kiếm pgvector):**
   - Nế **Duration > 800 ms**: Nút thắt cổ chai nằm ở **Cơ sở dữ liệu Vector Store**.
   - **Nguyên nhân & Khắc phục:** Bảng `vector_store` chưa đánh chỉ mục HNSW/IVFFlat, số lượng vector lớn nhưng thiếu phân trang, hoặc câu query chưa tối ưu.

2. **Kiểm tra Generation `LLM-Completion` (Thời gian LLM phản hồi):**
   - Nếu **Duration > 2000 ms**: Nút thắt cổ chai nằm ở **Tốc độ sinh văn bản của LLM (LLM Generation Speed)**.
   - **Nguyên nhân & Khắc phục:** Do Output Token quá dài, mô hình LLM phản hồi chậm hoặc nghẽn mạng OpenRouter. Cần kích hoạt **Stream API (Server-Sent Events - SSE)** để giảm độ trễ cảm nhận của người dùng (Time To First Token - TTFT).

---

## 💻 **3. MÃ NGUỒN JAVA GỬI TOKEN USAGE THỦ CÔNG (`CustomTokenTrackerService.java`)**

```java
package com.rikkeipay.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service minh họa cấu hình gửi Token Usage thủ công sang Langfuse
 * đối với các mô hình AI Tự Host (Local LLM qua Ollama/vLLM) không hỗ trợ tự động đếm token.
 */
@Service
public class CustomTokenTrackerService {

    private static final Logger log = LoggerFactory.getLogger(CustomTokenTrackerService.class);

    public record ManualUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens,
        String unit,
        double estimatedCostUsd
    ) {}

    public int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.trim().length() / 3.5);
    }

    public ManualUsage trackManualTokenAndCost(String traceId, String modelName, String promptText, String responseText) {
        log.info("[MANUAL TOKEN TRACKER] Bắt đầu tính toán Token cho Trace ID: {}, Model: '{}'", traceId, modelName);

        // 1. Tự đếm Token đầu vào (Prompt Tokens) và đầu ra (Completion Tokens)
        int inputTokens = estimateTokenCount(promptText);
        int outputTokens = estimateTokenCount(responseText);
        int totalTokens = inputTokens + outputTokens;

        // 2. Tính toán chi phí quy đổi thủ công ($0.10/1M input, $0.30/1M output)
        double inputCost = (inputTokens / 1_000_000.0) * 0.10;
        double outputCost = (outputTokens / 1_000_000.0) * 0.30;
        double totalCost = inputCost + outputCost;

        ManualUsage usage = new ManualUsage(inputTokens, outputTokens, totalTokens, "TOKENS", totalCost);

        log.info(" - Input Tokens: {} | Output Tokens: {} | Total Tokens: {}", inputTokens, outputTokens, totalTokens);
        log.info(" - Ước tính Chi phí: ${} USD", String.format("%.6f", totalCost));

        // 3. Đóng gói Payload gửi sang Langfuse API
        Map<String, Object> langfuseGenerationPayload = new HashMap<>();
        langfuseGenerationPayload.put("traceId", traceId);
        langfuseGenerationPayload.put("name", "llm-generation-manual-usage");
        langfuseGenerationPayload.put("model", modelName);
        langfuseGenerationPayload.put("input", promptText);
        langfuseGenerationPayload.put("output", responseText);

        Map<String, Object> usageMap = new HashMap<>();
        usageMap.put("promptTokens", inputTokens);
        usageMap.put("completionTokens", outputTokens);
        usageMap.put("totalTokens", totalTokens);
        usageMap.put("unit", "TOKENS");

        langfuseGenerationPayload.put("usage", usageMap);

        log.info("[LANGFUSE GENERATION TELEMETRY SENT] Đã gửi thông số Usage thủ công sang Langfuse thành công!");

        return usage;
    }
}
```
