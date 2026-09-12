package org.example.lightaisoilmoistirrigationbackend.image.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.lightaisoilmoistirrigationbackend.image.entity.ImageAnalysis;
import org.example.lightaisoilmoistirrigationbackend.image.entity.ImageRecord;
import org.example.lightaisoilmoistirrigationbackend.image.repository.ImageAnalysisRepository;
import org.example.lightaisoilmoistirrigationbackend.image.repository.ImageRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 阶段四：调用本地部署的图片分析智能体（FastAPI /api/chat/stream）对图片进行分析。
 * 注意：该智能体独立部署在 :8008，与「数据智能分析」的 :8001（小沂 AI 助理）互不影响。
 *
 * 对接的接口协议（见 VLMAgent/api_server.py）：
 *   POST {base-url}/api/chat/stream  （multipart/form-data: message + session_name + image）
 *   返回 SSE 流，事件格式：
 *     data: {"chunk": "..."}                       // 流式文本片段
 *     data: {"done": true, "full_response": "..."} // 结束 + 完整结论
 *     data: {"error": "..."}                       // 异常
 *
 * 本服务两个出口：
 *   1) analyzeStream —— 以 SseEmitter 把智能体的 SSE 原样转发给前端（实时流式）。
 *   2) analyzeSync   —— 阻塞等待完整结论，落库 image_analysis 并返回。
 */
@Service
@Slf4j
public class AgentAnalysisService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Executor EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-analysis");
        t.setDaemon(true);
        return t;
    });

    private final ImageService imageService;
    private final ImageRecordRepository recordRepository;
    private final ImageAnalysisRepository analysisRepository;

    private final String baseUrl;
    private final String chatStreamPath;
    private final String defaultSessionName;
    private final String defaultPrompt;
    private final long connectTimeoutMs;
    private final long readTimeoutMs;
    private final long sseTimeoutMs;

    public AgentAnalysisService(ImageService imageService,
                                ImageRecordRepository recordRepository,
                                ImageAnalysisRepository analysisRepository,
                                @Value("${app.agent.base-url:http://localhost:8008}") String baseUrl,
                                @Value("${app.agent.chat-stream-path:/api/chat/stream}") String chatStreamPath,
                                @Value("${app.agent.session-name:默认对话}") String defaultSessionName,
                                @Value("${app.agent.default-prompt:请分析这张农作物/土壤图片，结合拍摄上下文判断其墒情、湿度、灌溉需求与可能的病虫害风险，并给出结论与建议。}") String defaultPrompt,
                                @Value("${app.agent.connect-timeout-ms:5000}") long connectTimeoutMs,
                                @Value("${app.agent.read-timeout-ms:300000}") long readTimeoutMs,
                                @Value("${app.agent.sse-timeout-ms:300000}") long sseTimeoutMs) {
        this.imageService = imageService;
        this.recordRepository = recordRepository;
        this.analysisRepository = analysisRepository;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.chatStreamPath = chatStreamPath.startsWith("/") ? chatStreamPath : "/" + chatStreamPath;
        this.defaultSessionName = defaultSessionName;
        this.defaultPrompt = defaultPrompt;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.sseTimeoutMs = sseTimeoutMs;
    }

    // ===================== 流式分析（SSE 代理） =====================

    /**
     * 选择某张图片调用智能体，以 SSE 把分析过程实时转发给前端。
     * 结束时把完整结论落库 image_analysis，并置 image_record.analyzed=true。
     */
    public SseEmitter analyzeStream(Long imageId, String prompt, String sessionName) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        CompletableFuture.runAsync(() -> {
            try {
                ImageRecord record = imageService.getById(imageId);
                byte[] bytes = imageService.readBytes(imageId);
                String resolvedPrompt = resolvePrompt(record, prompt);
                String resolvedSession = (sessionName == null || sessionName.isBlank()) ? defaultSessionName : sessionName;
                String mime = record.getContentType() != null ? record.getContentType() : "application/octet-stream";
                String filename = record.getStoredName() != null ? record.getStoredName() : "image";

                callAgentStream(bytes, filename, mime, resolvedPrompt, resolvedSession,
                        // 每个智能体事件：原样转发（保持与 Python 服务一致的 SSE 协议）
                        json -> sendSse(emitter, json),
                        // 结束：落库 + 置已分析
                        fullResponse -> {
                            sendSse(emitter, toJson(Map.of("done", true, "full_response", fullResponse)));
                            saveAnalysis(imageId, resolvedPrompt, resolvedSession, fullResponse, "DONE");
                            markAnalyzed(imageId);
                            emitter.complete();
                        },
                        // 异常：转发错误事件
                        err -> {
                            sendSse(emitter, toJson(Map.of("error", err)));
                            emitter.completeWithError(new RuntimeException(err));
                        });
            } catch (Exception e) {
                log.error("图片[{}]智能体分析失败", imageId, e);
                sendSse(emitter, toJson(Map.of("error", e.getMessage())));
                emitter.completeWithError(e);
            }
        }, EXECUTOR);

        emitter.onTimeout(() -> {
            log.warn("图片[{}]智能体分析 SSE 超时", imageId);
            emitter.completeWithError(new RuntimeException("分析请求超时"));
        });
        emitter.onError(e -> log.error("图片[{}]SSE 连接异常", imageId, e));
        return emitter;
    }

    // ===================== 同步分析（阻塞取完整结论） =====================

    /**
     * 选择某张图片调用智能体，等待分析完成，返回结论并落库。
     */
    public ImageAnalysis analyzeSync(Long imageId, String prompt, String sessionName) {
        ImageRecord record = imageService.getById(imageId);
        byte[] bytes = imageService.readBytes(imageId);
        String resolvedPrompt = resolvePrompt(record, prompt);
        String resolvedSession = (sessionName == null || sessionName.isBlank()) ? defaultSessionName : sessionName;
        String mime = record.getContentType() != null ? record.getContentType() : "application/octet-stream";
        String filename = record.getStoredName() != null ? record.getStoredName() : "image";

        AtomicReference<String> conclusionRef = new AtomicReference<>("");
        callAgentStream(bytes, filename, mime, resolvedPrompt, resolvedSession,
                json -> { /* 流式片段在同步模式忽略，仅取最终结果 */ },
                conclusionRef::set,
                err -> { throw new IllegalStateException("智能体分析失败: " + err); });

        ImageAnalysis analysis = saveAnalysis(imageId, resolvedPrompt, resolvedSession, conclusionRef.get(), "DONE");
        markAnalyzed(imageId);
        return analysis;
    }

    // ===================== 查询分析历史 =====================

    public List<ImageAnalysis> listAnalyses(Long imageId) {
        if (!recordRepository.existsById(imageId)) {
            throw new IllegalArgumentException("图片不存在: " + imageId);
        }
        return analysisRepository.findByImageIdOrderByCreatedAtDesc(imageId);
    }

    // ===================== 核心 HTTP 调用 =====================

    /**
     * 调用智能体 /api/chat/stream（multipart/form-data），按 SSE 事件回调。
     */
    private void callAgentStream(byte[] imageBytes, String filename, String mime,
                                 String prompt, String sessionName,
                                 Consumer<String> onEvent,
                                 Consumer<String> onDone,
                                 Consumer<String> onError) {
        String boundary = "----agentBoundary" + java.util.UUID.randomUUID().toString().replace("-", "");
        HttpRequest.BodyPublisher body = buildMultipart(boundary, prompt, sessionName, imageBytes, filename, mime);

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + chatStreamPath))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofMillis(readTimeoutMs))
                .POST(body)
                .build();

        try {
            HttpResponse<java.util.stream.Stream<String>> response =
                    client.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                onError.accept("智能体接口返回 HTTP " + response.statusCode());
                return;
            }
            StringBuilder full = new StringBuilder();
            java.util.Iterator<String> lineIt = response.body().iterator();
            while (lineIt.hasNext()) {
                String line = lineIt.next();
                if (line == null || line.isBlank()) {
                    continue;
                }
                String trimmed = line.trim();
                if (!trimmed.startsWith("data:")) {
                    continue;
                }
                String json = trimmed.substring(5).trim();
                if (json.isEmpty()) {
                    continue;
                }
                onEvent.accept(json);
                try {
                    Map<String, Object> map = MAPPER.readValue(json, Map.class);
                    if (map.containsKey("chunk")) {
                        full.append(String.valueOf(map.get("chunk")));
                    } else if (map.get("done") != null
                            && Boolean.parseBoolean(String.valueOf(map.get("done")))) {
                        String fr = map.get("full_response") != null
                                ? String.valueOf(map.get("full_response")) : full.toString();
                        onDone.accept(fr);
                        return;
                    } else if (map.containsKey("error")) {
                        onError.accept(String.valueOf(map.get("error")));
                        return;
                    }
                } catch (Exception e) {
                    log.warn("解析智能体事件失败: {}", json, e);
                }
            }
            // 流结束但未收到显式 done：以已累积内容作为结论
            onDone.accept(full.toString());
        } catch (Exception e) {
            log.error("调用智能体接口异常", e);
            onError.accept(e.getMessage());
        }
    }

    private HttpRequest.BodyPublisher buildMultipart(String boundary, String message, String sessionName,
                                                    byte[] imageBytes, String filename, String mime) {
        String CRLF = "\r\n";
        String delimiter = "--" + boundary;
        List<HttpRequest.BodyPublisher> parts = new ArrayList<>();

        parts.add(HttpRequest.BodyPublishers.ofByteArray(
                (delimiter + CRLF + "Content-Disposition: form-data; name=\"message\"" + CRLF + CRLF + message + CRLF)
                        .getBytes(StandardCharsets.UTF_8)));
        parts.add(HttpRequest.BodyPublishers.ofByteArray(
                (delimiter + CRLF + "Content-Disposition: form-data; name=\"session_name\"" + CRLF + CRLF + sessionName + CRLF)
                        .getBytes(StandardCharsets.UTF_8)));
        parts.add(HttpRequest.BodyPublishers.ofByteArray(
                (delimiter + CRLF + "Content-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"" + CRLF
                        + "Content-Type: " + mime + CRLF + CRLF).getBytes(StandardCharsets.UTF_8)));
        parts.add(HttpRequest.BodyPublishers.ofByteArray(imageBytes));
        parts.add(HttpRequest.BodyPublishers.ofByteArray(
                (CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8)));

        return HttpRequest.BodyPublishers.concat(parts.toArray(new HttpRequest.BodyPublisher[0]));
    }

    // ===================== 辅助 =====================

    private String resolvePrompt(ImageRecord record, String prompt) {
        if (prompt != null && !prompt.isBlank()) {
            return prompt;
        }
        StringBuilder sb = new StringBuilder(defaultPrompt);
        List<String> ctx = new ArrayList<>();
        if (record.getPlotCode() != null) ctx.add("地块：" + record.getPlotCode());
        if (record.getCropType() != null) ctx.add("作物：" + record.getCropType());
        if (record.getSoilType() != null) ctx.add("土壤类型：" + record.getSoilType());
        if (record.getGrowthStage() != null) ctx.add("生育期：" + record.getGrowthStage());
        if (record.getIrrigationStatus() != null) ctx.add("灌溉状态：" + record.getIrrigationStatus());
        if (!ctx.isEmpty()) {
            sb.append("\n【拍摄上下文】").append(String.join("；", ctx));
        }
        return sb.toString();
    }

    private ImageAnalysis saveAnalysis(Long imageId, String prompt, String sessionName, String conclusion, String status) {
        ImageAnalysis a = new ImageAnalysis();
        a.setImageId(imageId);
        a.setSessionName(sessionName);
        a.setPrompt(prompt);
        a.setConclusion(conclusion);
        a.setStatus(status);
        a.setCreatedAt(LocalDateTime.now());
        return analysisRepository.save(a);
    }

    private void markAnalyzed(Long imageId) {
        ImageRecord r = imageService.getById(imageId);
        r.setAnalyzed(true);
        r.setAnalysisStatus("DONE");
        recordRepository.save(r);
    }

    private void sendSse(SseEmitter emitter, String payload) {
        try {
            emitter.send(SseEmitter.event().data(payload));
        } catch (IllegalStateException e) {
            // 客户端已断开，忽略
            log.debug("SSE 发送时客户端已断开: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("SSE 发送异常", e);
        }
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}
