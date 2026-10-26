package com.example.demo.message;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@CrossOrigin
@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageStreamService streams;

    public MessageController(MessageStreamService streams) {
        this.streams = streams;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> receive(@Valid @RequestBody MessageRequest request) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(streams.reply(request));
    }

    public record MessageRequest(
            @NotBlank(message = "请输入消息") @Size(max = 2000, message = "消息不能超过 2000 字符") String message,
            @Size(max = 40, message = "最多携带 20 轮历史对话") List<@NotNull @Valid HistoryMessage> history,
            @Pattern(regexp = "low|medium|high|xhigh|max", message = "不支持的思考强度") String effort) {
    }

    public record HistoryMessage(
            @NotBlank @Pattern(regexp = "user|assistant", message = "历史消息角色只能是 user 或 assistant") String role,
            @NotBlank @Size(max = 32000, message = "单条历史消息过长") String content) {
    }
}
