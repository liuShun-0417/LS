package com.example.demo.chat;

import com.example.demo.chat.ChatModels.*;
import com.example.demo.system.entity.User;
import com.example.demo.system.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.time.LocalDateTime;

@Service
public class ChatService {
    private final ChatMapper mapper;
    private final UserMapper users;

    public ChatService(ChatMapper mapper, UserMapper users) {
        this.mapper = mapper;
        this.users = users;
    }

    public User requireUser(int userId) {
        User user = users.selectById(userId);
        if (user == null) throw error(HttpStatus.UNAUTHORIZED, "登录已失效");
        return user;
    }

    public List<Group> groups(int userId, long afterId) {
        requireUser(userId);
        if (afterId < 0) throw error(HttpStatus.BAD_REQUEST, "游标不能小于 0");
        return mapper.groups(userId, afterId);
    }

    @Transactional
    public GroupRow create(int userId, String name) {
        requireUser(userId);
        var group = new GroupRow();
        group.setName(name.trim());
        group.setOwnerId(userId);
        mapper.insertGroup(group);
        mapper.insertMember(group.getId(), userId, 0, 0);
        return group;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Member join(long groupId, int userId) {
        requireUser(userId);
        lockGroup(groupId);
        Member existing = mapper.member(groupId, userId);
        if (existing != null) return existing;
        // 与发送消息锁住同一个群组行，入群边界不受并发发送影响。
        List<Long> recent = mapper.recentIds(groupId);
        long lastId = recent.isEmpty() ? 0 : recent.getFirst();
        long firstId = recent.isEmpty() ? 0 : recent.getLast();
        mapper.insertMember(groupId, userId, lastId, firstId);
        return mapper.member(groupId, userId);
    }

    public Member requireMember(long groupId, int userId) {
        Member member = mapper.member(groupId, userId);
        if (member == null) throw error(HttpStatus.FORBIDDEN, "请先加入群组");
        return member;
    }

    public History history(long groupId, int userId, long afterId) {
        requireUser(userId);
        if (afterId < 0) throw error(HttpStatus.BAD_REQUEST, "游标不能小于 0");
        Member member = requireMember(groupId, userId);
        List<Message> rows = mapper.history(groupId, member.historyStartId(), afterId);
        boolean hasMore = rows.size() > 100;
        List<Message> items = List.copyOf(rows.subList(0, Math.min(rows.size(), 100)));
        return new History(items, items.isEmpty() ? afterId : items.getLast().getId(), hasMore);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Message send(long groupId, int userId, String content, String clientMessageId) {
        User user = requireUser(userId);
        return persist(prepare(groupId, userId,
                user.getName() == null || user.getName().isBlank() ? user.getAccount() : user.getName(), content, clientMessageId));
    }

    public Message prepare(long groupId, int userId, String senderName, String content, String clientMessageId) {
        if (content == null || content.isBlank() || content.length() > 2000)
            throw error(HttpStatus.BAD_REQUEST, "消息不能为空且不能超过 2000 字符");
        if (clientMessageId == null || !clientMessageId.matches("[a-zA-Z0-9_-]{1,64}"))
            throw error(HttpStatus.BAD_REQUEST, "clientMessageId 必须是 1–64 位字母、数字、下划线或短横线");
        var message = new Message();
        message.setGroupId(groupId);
        message.setSenderId(userId);
        message.setSenderName(senderName);
        message.setContent(content);
        message.setClientMessageId(clientMessageId);
        message.setSentAt(LocalDateTime.now());
        message.setDeliveryStatus("pending");
        return message;
    }

    public Message existing(Message message) {
        return mapper.byClientId(message.getGroupId(), message.getSenderId(), message.getClientMessageId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Message persist(Message draft) {
        lockGroup(draft.getGroupId());
        requireMember(draft.getGroupId(), draft.getSenderId());
        Message existing = existing(draft);
        if (existing != null) {
            if (!existing.getContent().equals(draft.getContent())) throw error(HttpStatus.CONFLICT, "消息编号已被其他内容使用");
            return existing;
        }
        // 不修改已经广播的对象，也不在保存时重新读取可能已经注销的用户。
        var message = prepare(draft.getGroupId(), draft.getSenderId(), draft.getSenderName(),
                draft.getContent(), draft.getClientMessageId());
        message.setSentAt(draft.getSentAt());
        mapper.insertMessage(message);
        return mapper.message(message.getId());
    }

    private void lockGroup(long groupId) {
        if (mapper.lockGroup(groupId) == null) throw error(HttpStatus.NOT_FOUND, "群组不存在");
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
