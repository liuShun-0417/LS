package com.example.demo.chat;

import org.apache.ibatis.annotations.*;
import java.util.List;
import com.example.demo.chat.ChatModels.*;

@Mapper
public interface ChatMapper {
    @Insert("INSERT INTO chat_group(name, owner_id) VALUES(#{name}, #{ownerId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertGroup(GroupRow group);

    @Select("SELECT id FROM chat_group WHERE id = #{id} FOR UPDATE")
    Long lockGroup(long id);

    @Select("""
        SELECT g.id, g.name, g.owner_id, g.created_at,
          (SELECT COUNT(*) FROM chat_member m WHERE m.group_id=g.id) AS member_count,
          EXISTS(SELECT 1 FROM chat_member m WHERE m.group_id=g.id AND m.user_id=#{userId}) AS joined
        FROM chat_group g WHERE g.id > #{afterId} ORDER BY g.id LIMIT 50
        """)
    List<Group> groups(@Param("userId") int userId, @Param("afterId") long afterId);

    @Select("SELECT * FROM chat_member WHERE group_id=#{groupId} AND user_id=#{userId}")
    Member member(@Param("groupId") long groupId, @Param("userId") int userId);

    @Insert("""
        INSERT INTO chat_member(group_id, user_id, joined_after_id, history_start_id)
        VALUES(#{groupId}, #{userId}, #{joinedAfterId}, #{historyStartId})
        """)
    void insertMember(@Param("groupId") long groupId, @Param("userId") int userId,
                      @Param("joinedAfterId") long joinedAfterId, @Param("historyStartId") long historyStartId);

    @Select("SELECT id FROM chat_message WHERE group_id=#{groupId} ORDER BY id DESC LIMIT 30")
    List<Long> recentIds(long groupId);

    @Select("""
        SELECT * FROM chat_message WHERE group_id=#{groupId} AND id >= #{startId} AND id > #{afterId}
        ORDER BY id LIMIT 101
        """)
    List<Message> history(@Param("groupId") long groupId, @Param("startId") long startId,
                          @Param("afterId") long afterId);

    @Insert("""
        INSERT INTO chat_message(group_id, sender_id, sender_name, content, client_message_id, sent_at)
        VALUES(#{groupId}, #{senderId}, #{senderName}, #{content}, #{clientMessageId}, #{sentAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertMessage(Message message);

    @Select("SELECT * FROM chat_message WHERE id=#{id}")
    Message message(long id);

    @Select("""
        SELECT * FROM chat_message WHERE group_id=#{groupId} AND sender_id=#{senderId}
        AND client_message_id=#{clientMessageId}
        """)
    Message byClientId(@Param("groupId") long groupId, @Param("senderId") int senderId,
                       @Param("clientMessageId") String clientMessageId);
}
