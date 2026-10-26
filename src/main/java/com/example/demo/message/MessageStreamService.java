package com.example.demo.message;

import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Service
public class MessageStreamService {
    private final AiProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService workers = new ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().name("message-ai-", 0).factory());
    private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2,
            Thread.ofPlatform().name("message-ai-timer-", 0).factory());
    private final Set<StreamSession> sessions = ConcurrentHashMap.newKeySet();

    public MessageStreamService(AiProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public SseEmitter reply(MessageController.MessageRequest request) {
        if (properties.getApiKey().isBlank() || properties.getBaseUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务尚未配置，请联系管理员");
        }
        List<Map<String, String>> input = buildInput(request);
        Map<String, Object> body = Map.of(
                "model", properties.getModel(), "store", false, "stream", true,
                "instructions", "你是一个友好、准确的 AI 助手。请根据用户问题直接回答，默认使用简体中文。对不确定的内容如实说明。",
                "reasoning", Map.of("effort", request.effort() == null ? "medium" : request.effort()),
                "max_output_tokens", properties.getMaxOutputTokens(), "input", input);
        HttpRequest upstream = HttpRequest.newBuilder(URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/responses"))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json").header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        StreamSession session = new StreamSession();
        FutureTask<Void> task = new FutureTask<>(() -> { run(session, upstream); return null; });
        session.task = task;
        session.emitter.onCompletion(session::close);
        session.emitter.onError(error -> session.close());
        session.emitter.onTimeout(() -> session.fail("AI 回复超时，请重试或降低思考强度"));
        sessions.add(session);
        try {
            workers.execute(task);
        } catch (RejectedExecutionException exception) {
            session.close();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前应答较多，请稍后重试");
        }
        return session.emitter;
    }

    private List<Map<String, String>> buildInput(MessageController.MessageRequest request) {
        var input = new ArrayList<Map<String, String>>();
        int characters = request.message().length();
        var history = request.history() == null ? List.<MessageController.HistoryMessage>of() : request.history();
        if (history.size() % 2 != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "历史对话必须包含完整的提问和回复");
        }
        for (int index = 0; index < history.size(); index++) {
            var message = history.get(index);
            if (!message.role().equals(index % 2 == 0 ? "user" : "assistant")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "历史消息顺序错误");
            }
            characters += message.content().length();
            input.add(Map.of("role", message.role(), "content", message.content()));
        }
        if (characters > 64000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对话内容过长，请清空对话后重试");
        }
        input.add(Map.of("role", "user", "content", request.message().strip()));
        return input;
    }

    private void run(StreamSession session, HttpRequest request) {
        try {
            session.send("start", Map.of("model", properties.getModel(), "status", "generating"));
            session.heartbeat = timers.scheduleAtFixedRate(() -> {
                try { session.emitter.send(SseEmitter.event().comment("keepalive")); }
                catch (Exception exception) { session.close(); }
            }, 5, 5, TimeUnit.SECONDS);
            session.deadline = timers.schedule(() -> session.fail("AI 回复超时，请重试或降低思考强度"),
                    properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            var pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            session.pending.set(pending);
            if (session.closed.get()) { pending.cancel(true); return; }
            var response = pending.get();
            session.body.set(response.body());
            if (session.closed.get()) { response.body().close(); return; }
            if (response.statusCode() != 200) {
                throw new UpstreamFailure(switch (response.statusCode()) {
                    case 401, 403 -> "AI 服务认证失败，请联系管理员检查配置";
                    case 429 -> "AI 服务繁忙或额度不足，请稍后重试";
                    default -> "AI 服务暂时不可用，请稍后重试";
                });
            }
            if (!response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream")) {
                throw new UpstreamFailure("AI 服务未返回有效的流式回复");
            }
            readStream(session, response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (UpstreamFailure exception) {
            session.fail(exception.getMessage());
        } catch (Exception exception) {
            // 不向浏览器或日志输出上游响应体、请求头和异常详情，避免回显凭据。
            session.fail("AI 连接中断或响应异常，请重新生成");
        } finally {
            session.close();
            session.emitter.complete();
        }
    }

    private void readStream(StreamSession session, InputStream body) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder data = new StringBuilder();
            boolean hasText = false;
            while (!session.closed.get() && (line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    if (line.startsWith("data:")) {
                        if (!data.isEmpty()) data.append('\n');
                        data.append(line.substring(5).stripLeading());
                        if (data.length() > 2_000_000) throw new UpstreamFailure("AI 响应数据过大，请重试");
                    }
                    continue;
                }
                if (data.isEmpty()) continue;
                String payload = data.toString();
                data.setLength(0);
                if (payload.equals("[DONE]")) break;
                JsonNode event = json.readTree(payload);
                switch (event.path("type").asText()) {
                    case "response.output_text.delta", "response.refusal.delta" -> {
                        String delta = event.path("delta").asText("");
                        if (!delta.isEmpty()) {
                            session.send("delta", Map.of("text", delta));
                            hasText = true;
                        }
                    }
                    case "response.completed" -> {
                        if (!hasText) throw new UpstreamFailure("AI 未返回文本，请重新生成");
                        session.send("done", Map.of("status", "completed"));
                        return;
                    }
                    case "response.incomplete" -> throw new UpstreamFailure("回复未完成，可能达到输出限制；请缩小问题范围后重试");
                    case "response.failed", "error" -> throw new UpstreamFailure("AI 生成失败，请稍后重试");
                    default -> { /* 忽略推理及内部元数据，仅转发用户可见文本。 */ }
                }
            }
            if (!session.closed.get()) throw new UpstreamFailure("AI 连接中断，回复未完成，请重新生成");
        }
    }

    private static class UpstreamFailure extends IOException {
        UpstreamFailure(String message) { super(message); }
    }

    private class StreamSession {
        final SseEmitter emitter = new SseEmitter((properties.getTimeoutSeconds() + 10L) * 1000);
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicReference<InputStream> body = new AtomicReference<>();
        final AtomicReference<CompletableFuture<?>> pending = new AtomicReference<>();
        volatile Future<?> task;
        volatile ScheduledFuture<?> heartbeat;
        volatile ScheduledFuture<?> deadline;

        void send(String event, Object data) throws IOException {
            if (closed.get()) throw new IOException("Stream closed");
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        }

        void fail(String message) {
            if (closed.get()) return;
            try { send("error", Map.of("message", message)); } catch (Exception ignored) { }
            close();
            emitter.complete();
        }

        void close() {
            closed.set(true);
            if (heartbeat != null) heartbeat.cancel(false);
            if (deadline != null) deadline.cancel(false);
            if (task != null) task.cancel(true);
            var pendingRequest = pending.getAndSet(null);
            if (pendingRequest != null) pendingRequest.cancel(true);
            var stream = body.getAndSet(null);
            if (stream != null) try { stream.close(); } catch (IOException ignored) { }
            sessions.remove(this);
        }
    }

    @PreDestroy
    public void shutdown() {
        sessions.forEach(StreamSession::close);
        timers.shutdownNow();
        workers.shutdownNow();
        http.shutdownNow();
    }
}
