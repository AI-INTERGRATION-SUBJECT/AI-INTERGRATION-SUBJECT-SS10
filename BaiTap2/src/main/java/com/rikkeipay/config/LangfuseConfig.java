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

    /**
     * Giả lập khởi tạo Client Langfuse an toàn từ file cấu hình application.yml / biến môi trường.
     */
    @Bean
    public LangfuseClient langfuseClient() {
        log.info("[LANGFUSE CONFIG] Khởi tạo LangfuseClient an toàn với Base URL: {}", properties.getBaseUrl());
        log.info(" - Public Key: {}", maskKey(properties.getPublicKey()));
        log.info(" - Trạng thái Tracing: {}", properties.isEnabled() ? "ENABLED" : "DISABLED");

        return new LangfuseClient(
                properties.getPublicKey(),
                properties.getSecretKey(),
                properties.getBaseUrl()
        );
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "********";
        return key.substring(0, 5) + "..." + key.substring(key.length() - 3);
    }

    /**
     * Stub class giả lập LangfuseClient SDK phục vụ minh chứng mã nguồn.
     */
    public static class LangfuseClient {
        private final String publicKey;
        private final String secretKey;
        private final String baseUrl;

        public LangfuseClient(String publicKey, String secretKey, String baseUrl) {
            this.publicKey = publicKey;
            this.secretKey = secretKey;
            this.baseUrl = baseUrl;
        }

        public Trace trace(Trace trace) {
            return trace;
        }
    }

    /**
     * Stub class giả lập Trace model của Langfuse.
     */
    public static class Trace {
        private String name;
        private String userId;
        private String sessionId;
        private Object input;
        private Object output;
        private Object metadata;

        public Trace name(String name) {
            this.name = name;
            return this;
        }

        public Trace userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Trace sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Trace input(Object input) {
            this.input = input;
            return this;
        }

        public Trace output(Object output) {
            this.output = output;
            return this;
        }

        public Trace metadata(Object metadata) {
            this.metadata = metadata;
            return this;
        }

        public String getName() { return name; }
        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public Object getInput() { return input; }
        public Object getOutput() { return output; }
        public Object getMetadata() { return metadata; }
    }
}
