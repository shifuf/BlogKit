package com.blog.system.service;

import com.blog.system.common.exception.BusinessException;
import com.blog.system.common.util.OutboundUrlValidator;
import com.blog.system.domain.system.SiteSetting;
import com.blog.system.repository.SiteSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对话（OpenAI 兼容 /chat/completions）统一封装。读取「AI 服务」后台配置（baseUrl/apiKey/model），
 * 供文章 AI 摘要生成/润色、连通测试等复用；不做权限校验（由调用方所在的 Service/Controller 负责）。
 */
@Service
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final SiteSettingRepository settingRepository;
    private final ObjectMapper objectMapper;
    private final OutboundUrlValidator outboundUrlValidator;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public AiService(SiteSettingRepository settingRepository, ObjectMapper objectMapper,
                     OutboundUrlValidator outboundUrlValidator) {
        this.settingRepository = settingRepository;
        this.objectMapper = objectMapper;
        this.outboundUrlValidator = outboundUrlValidator;
    }

    /** 是否已配置可用的 AI 服务（地址+密钥+模型齐全）。 */
    public boolean isConfigured() {
        Map<String, String> values = readAiSettings();
        return StringUtils.hasText(values.get("baseUrl"))
                && StringUtils.hasText(values.get("apiKey"))
                && StringUtils.hasText(values.get("model"));
    }

    /**
     * 发起一次对话补全，返回助手回复的纯文本。失败抛 BusinessException（含可读原因）。
     * @param systemPrompt 系统提示（可空）
     * @param userPrompt   用户输入
     * @param maxTokens    输出上限
     */
    public String chat(String systemPrompt, String userPrompt, int maxTokens) {
        Map<String, String> values = readAiSettings();
        String baseUrl = values.get("baseUrl");
        String apiKey = values.get("apiKey");
        String model = values.get("model");
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) {
            throw new BusinessException("AI 服务未配置，请先在「系统设置 - AI 服务」填写接口地址、密钥与模型");
        }

        java.util.List<Map<String, String>> messages = new java.util.ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model.trim(),
                    "messages", messages,
                    "max_tokens", Math.max(maxTokens, 16),
                    "temperature", 0.5
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(outboundUrlValidator.validate(resolveChatCompletionsUrl(baseUrl)))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("AI 服务返回 HTTP " + response.statusCode() + "：" + trimMessage(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new BusinessException("AI 服务未返回内容");
            }
            return content.trim();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("AI 对话失败 err={}", ex.getMessage());
            throw new BusinessException("AI 调用失败：" + trimMessage(ex.getMessage()));
        }
    }

    private Map<String, String> readAiSettings() {
        Map<String, String> values = new LinkedHashMap<>();
        for (SiteSetting setting : settingRepository.findAllBySettingGroupOrderBySettingKeyAsc("ai")) {
            values.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return values;
    }

    private String resolveChatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "无响应内容";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }
}
