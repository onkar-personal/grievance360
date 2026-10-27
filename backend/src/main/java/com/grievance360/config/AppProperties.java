package com.grievance360.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Storage storage, Auth auth, Workflow workflow, Bootstrap bootstrap) {

    public record Jwt(String secret, long expiryMinutes, String issuer) {}

    public record Cors(String allowedOrigins) {
        public List<String> originList() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) return List.of();
            return Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record Storage(String dir, long maxFileBytes, int maxFilesPerComplaint, String allowedTypes) {
        public List<String> allowedTypeList() {
            return Arrays.stream(allowedTypes.split(",")).map(String::trim).toList();
        }
    }

    public record Auth(int loginAttemptsPerWindow, long windowSeconds) {}

    public record Workflow(int reopenWindowDays, int autoCloseDays, String escalationCron) {}

    public record Bootstrap(String adminEmail, String adminPassword, String adminName) {}
}
