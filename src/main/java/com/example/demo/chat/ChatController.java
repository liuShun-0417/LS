package com.example.demo.chat;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.chat.ChatModels.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class ChatController {
    private final ChatService service;
    private final ChatDeliveryService delivery;
    public ChatController(ChatService service, ChatDeliveryService delivery) {
        this.service = service;
        this.delivery = delivery;
    }

    @GetMapping
    public List<Group> list(@RequestParam(defaultValue = "0") long afterId) {
        return service.groups(StpUtil.getLoginIdAsInt(), afterId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupRow create(@Valid @RequestBody CreateGroup request) {
        return service.create(StpUtil.getLoginIdAsInt(), request.name());
    }

    @PostMapping("/{groupId}/join")
    public Member join(@PathVariable long groupId) {
        return delivery.join(groupId, StpUtil.getLoginIdAsInt());
    }

    @GetMapping("/{groupId}/messages")
    public History history(@PathVariable long groupId, @RequestParam(defaultValue = "0") long afterId) {
        return service.history(groupId, StpUtil.getLoginIdAsInt(), afterId);
    }
}
