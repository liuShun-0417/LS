package com.example.demo.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

public final class ChatModels {
    private ChatModels() {}

    public record CreateGroup(@NotBlank @Size(max = 100) String name) {}
    public record Group(long id, String name, int ownerId, LocalDateTime createdAt,
                        long memberCount, boolean joined) {}
    public record Member(long groupId, int userId, LocalDateTime joinedAt,
                         long joinedAfterId, long historyStartId) {}
    public record History(List<Message> items, long nextAfterId, boolean hasMore) {}

    @Getter @Setter
    public static class GroupRow {
        private Long id;
        private String name;
        private int ownerId;
    }

    @Getter @Setter
    public static class Message {
        private Long id;
        private long groupId;
        private int senderId;
        private String senderName;
        private String content;
        private String clientMessageId;
        private LocalDateTime sentAt;
        // 传输状态，不对应数据库列；历史记录默认已保存。
        private String deliveryStatus = "saved";
    }
}
