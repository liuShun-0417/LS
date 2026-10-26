package com.example.demo.chat;

import com.example.demo.chat.ChatModels.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** 单实例有界 FIFO 保存队列。发送确认和数据库保存确认分开。 */
@Service
public class ChatDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(ChatDeliveryService.class);
    private final ChatService service;
    private final ThreadPoolTaskExecutor executor;
    private final Map<Key, Pending> pending = new HashMap<>();
    private final Map<Key, Message> saved = new LinkedHashMap<>();
    private long sequence;

    public ChatDeliveryService(ChatService service,
                               @Qualifier("chatPersistenceExecutor") ThreadPoolTaskExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    public synchronized void send(Message draft, Consumer<Message> accepted, Consumer<Message> finished) {
        Key key = key(draft);
        Pending entry = pending.get(key);
        if (entry != null) {
            checkContent(entry.message, draft);
            if ("failed".equals(entry.message.getDeliveryStatus())) {
                Message retry = service.prepare(draft.getGroupId(), draft.getSenderId(),
                        entry.message.getSenderName(), draft.getContent(), draft.getClientMessageId());
                retry.setSentAt(entry.message.getSentAt());
                schedule(key, new Pending(entry.sequence, retry), accepted, finished);
            } else accepted.accept(entry.message);
            return;
        }
        Message existing = saved.get(key);
        if (existing == null) existing = service.existing(draft);
        if (existing != null) {
            checkContent(existing, draft);
            accepted.accept(existing);
            finished.accept(existing);
            return;
        }
        if (pending.size() >= 1000) throw busy("待保存消息已满，请稍后重试");
        schedule(key, new Pending(++sequence, draft), accepted, finished);
    }

    private void schedule(Key key, Pending entry, Consumer<Message> accepted, Consumer<Message> finished) {
        var broadcastDone = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                try { broadcastDone.await(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                save(key, entry, finished);
            });
        } catch (TaskRejectedException exception) {
            throw busy("保存队列繁忙，请稍后重试");
        }
        pending.put(key, entry);
        try { accepted.accept(entry.message); }
        finally { broadcastDone.countDown(); }
    }

    private void save(Key key, Pending entry, Consumer<Message> finished) {
        Message result = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try { result = service.persist(entry.message); break; }
            catch (RuntimeException exception) {
                // 不打印消息正文、凭证或 SQL 参数。
                log.warn("Async chat save failed, attempt {}: {}", attempt + 1, exception.getClass().getSimpleName());
                if (attempt < 2) {
                    try { Thread.sleep(200L * (attempt + 1)); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        synchronized (this) {
            if (result != null) {
                pending.remove(key);
                saved.put(key, result);
                if (saved.size() > 1000) saved.remove(saved.keySet().iterator().next());
            } else {
                result = service.prepare(entry.message.getGroupId(), entry.message.getSenderId(),
                        entry.message.getSenderName(), entry.message.getContent(), entry.message.getClientMessageId());
                result.setSentAt(entry.message.getSentAt());
                result.setDeliveryStatus("failed");
                pending.put(key, new Pending(entry.sequence, result));
            }
        }
        finished.accept(result);
    }

    public Member join(long groupId, int userId) {
        Future<Member> future;
        synchronized (this) {
            long boundary = sequence;
            try {
                future = executor.submit(() -> {
                    synchronized (this) {
                        // 此前接收但尚未成功保存的消息会使首次加入失败，不能悄悄错算 30 条边界。
                        if (pending.values().stream().anyMatch(p -> p.message.getGroupId() == groupId && p.sequence <= boundary))
                            throw busy("群组此前的消息尚未保存成功，请稍后加入");
                    }
                    return service.join(groupId, userId);
                });
            } catch (TaskRejectedException exception) { throw busy("保存队列繁忙，请稍后加入"); }
        }
        try { return future.get(30, TimeUnit.SECONDS); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(false);
            throw busy("加入请求已中断，请重试");
        } catch (TimeoutException exception) {
            future.cancel(false);
            throw busy("正在保存此前的消息，请稍后加入");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            throw busy("暂时无法加入群组");
        }
    }

    public synchronized List<Message> pendingMessages(long groupId) {
        return pending.values().stream().filter(p -> p.message.getGroupId() == groupId)
                .sorted(Comparator.comparingLong(Pending::sequence)).map(Pending::message).toList();
    }

    private static void checkContent(Message existing, Message draft) {
        if (!existing.getContent().equals(draft.getContent()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "消息编号已被其他内容使用");
    }
    private static ResponseStatusException busy(String text) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, text);
    }
    private static Key key(Message message) {
        return new Key(message.getGroupId(), message.getSenderId(), message.getClientMessageId());
    }
    private record Key(long groupId, int senderId, String clientMessageId) {}
    private record Pending(long sequence, Message message) {}
}
