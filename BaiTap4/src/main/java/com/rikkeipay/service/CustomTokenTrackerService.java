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

    /**
     * DTO đóng gói dữ liệu Token Usage thủ công.
     */
    public record ManualUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens,
        String unit,
        double estimatedCostUsd
    ) {}

    /**
     * Uớc tính số lượng Token dựa trên độ dài văn bản (Trung bình 1 Token ~ 3.5 ký tự tiếng Việt/Anh).
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.trim().length() / 3.5);
    }

    /**
     * Giả lập việc tạo Generation Observation và gửi Token Usage thủ công sang Langfuse API.
     *
     * @param traceId ID của Trace hiện tại
     * @param modelName Tên mô hình (Ví dụ: 'ollama-qwen2.5-7b' hoặc 'custom-local-model')
     * @param promptText Văn bản Prompt đầu vào
     * @param responseText Văn bản LLM sinh ra
     * @return Đối tượng ManualUsage chứa thông tin token và chi phí quy đổi
     */
    public ManualUsage trackManualTokenAndCost(String traceId, String modelName, String promptText, String responseText) {
        log.info("[MANUAL TOKEN TRACKER] Bắt đầu tính toán Token cho Trace ID: {}, Model: '{}'", traceId, modelName);

        // 1. Tự đếm Token đầu vào (Prompt Tokens) và đầu ra (Completion Tokens)
        int inputTokens = estimateTokenCount(promptText);
        int outputTokens = estimateTokenCount(responseText);
        int totalTokens = inputTokens + outputTokens;

        // 2. Tính toán chi phí quy đổi thủ công (Ví dụ với giá cố định: Input $0.10/1M, Output $0.30/1M)
        double inputCost = (inputTokens / 1_000_000.0) * 0.10;
        double outputCost = (outputTokens / 1_000_000.0) * 0.30;
        double totalCost = inputCost + outputCost;

        ManualUsage usage = new ManualUsage(inputTokens, outputTokens, totalTokens, "TOKENS", totalCost);

        log.info(" - Input Tokens: {} | Output Tokens: {} | Total Tokens: {}", inputTokens, outputTokens, totalTokens);
        log.info(" - Ước tính Chi phí: ${} USD", String.format("%.6f", totalCost));

        // 3. Đóng gói Payload gửi sang Langfuse API (POST /api/public/generations)
        Map<String, Object> langfuseGenerationPayload = new HashMap<>();
        langfuseGenerationPayload.put("traceId", traceId);
        langfuseGenerationPayload.put("name", "llm-generation-manual-usage");
        langfuseGenerationPayload.put("model", modelName);
        langfuseGenerationPayload.put("input", promptText);
        langfuseGenerationPayload.put("output", responseText);

        // Gửi thông số usage thủ công vào thuộc tính usage của Langfuse Generation API
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
