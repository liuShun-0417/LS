# 群组聊天接口与验收

登录后访问 `/chat.html`。创建群组时创建者自动加入；其他已登录用户可以查看群组列表并点击加入。支持多个用户、同一用户的多个连接、群组切换、实时消息、断线重连、历史补齐及失败重试。

## 数据与历史规则

- `chat_group` 保存群组名称、创建者和创建时间。
- `chat_member` 保存成员和首次入群时间，`joined_after_id` 记录入群前最后一条消息，`history_start_id` 记录入群前最近 30 条中的第一条消息。
- `chat_message` 保存每条消息的群组、发送者 ID、发送者名称快照、正文、客户端消息编号和服务端时间。删除或改名用户不会删除或改写已有发言。
- **首次入群只能读取此前最近 30 条消息**（不足 30 条则全部返回），按从旧到新展示。更早的消息仍完整保存在数据库，但该成员无权读取。入群后的消息全部可见。
- 重复加入不改变首次入群边界。历史接口每页最多 100 条，使用 `nextAfterId` 继续加载；页面自动补齐，因此加入时间较早的成员可能看到超过 30 条，其中入群前的消息仍不超过 30 条。
- 消息先推送，再进入单线程 FIFO 队列异步保存。加入请求排在此前已接收的保存任务之后，以这个队列顺序确定入群边界；后续消息继续即时推送，不等待保存或加入完成。此前消息保存失败时加入返回 503，避免历史范围错误。保存与入群事务仍使用群组行锁和 READ COMMITTED。空群边界为 0。
- `(group_id, sender_id, client_message_id)` 唯一约束保证重试不重复保存。同一编号不同正文返回 409。
- 所有查询均按群组和成员权限过滤，无法通过修改游标读取更早的受限消息。客户端传入的发送者身份不被采用。

启动时由 Flyway 执行 `db/migration/V2__chat_schema.sql`，只创建不存在的表。数据库需要 CREATE TABLE 权限。消息时间由服务端接收时生成，保存时保留该时间，响应为服务端本地日期时间字符串。

## HTTP 接口

请求头沿用登录返回的 `satoken: <tokenValue>`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/groups?afterId=0` | 按群组 ID 升序，每页最多 50 条；用本页最后一个 ID 继续。返回 `id/name/ownerId/createdAt/memberCount/joined` 数组 |
| POST | `/api/groups` | 请求 `{"name":"学习小组"}`，返回 201 及 `id/name/ownerId`，创建者自动入群；名称 1–100 字符 |
| POST | `/api/groups/{groupId}/join` | 幂等加入，返回 `groupId/userId/joinedAt/joinedAfterId/historyStartId` |
| GET | `/api/groups/{groupId}/messages?afterId=0` | 仅成员可用，返回 `{items:[],nextAfterId:0,hasMore:false}` |

消息字段：`id/groupId/senderId/senderName/content/clientMessageId/sentAt/deliveryStatus`。正文必须有非空白字符，最长 2000 个 Java/JavaScript 字符单位。数据库保存前 `id=null`、`deliveryStatus=pending`；保存后 `id` 为数据库编号、状态为 `saved`；保存重试耗尽后为 `failed`。历史接口只返回已保存记录。

常见错误：400 参数错误；401 未登录或登录失效；403 未加入群组；404 加入的群组不存在；409 同一消息编号对应不同内容。HTTP 错误体为 `{"status":403,"message":"请先加入群组"}`。

## WebSocket 协议

连接同源 `/ws/chat`，HTTPS 页面使用 `wss://`。使用 Spring 原生 WebSocket JSON 消息，不使用 STOMP 或 SockJS。默认拒绝其他来源的浏览器连接。

连接后的 10 秒内发送首帧进行身份验证，凭证不写入 URL。验证完成前不会订阅或推送群聊消息：

```json
{"type":"auth","token":"登录接口返回的 tokenValue"}
```

成功响应 `{"type":"ready","userId":1}`。随后订阅一个已加入的群组，每条连接同时订阅一个群；重新订阅会切换当前群，不会退出原群成员关系：

```json
{"type":"subscribe","groupId":1,"requestId":"select-1"}
```

响应 `{"type":"subscribed","groupId":1,"requestId":"select-1"}`，随后推送当前群组尚未保存的消息及状态。**先等订阅成功，再读取 HTTP 分页历史**。使用 `groupId + senderId + clientMessageId` 合并即时消息和数据库记录，不能使用保存前为空的 `id` 作为唯一键。合并时已保存状态不能被延迟到达的 pending 状态覆盖。刷新或重连按同样流程执行。

发送消息（无需依赖客户端声明的用户身份）：

```json
{"type":"send","groupId":1,"content":"大家好！","clientMessageId":"client-uuid-1","requestId":"client-uuid-1"}
```

`clientMessageId` 为 1–64 位字母、数字、下划线或短横线。`requestId` 可选，最多 64 字符，用于关联确认和错误；不承担幂等职责。

- 服务端完成身份、群成员、输入和幂等校验后，立即向在线订阅者推送 `{"type":"message","message":{...,"id":null,"deliveryStatus":"pending"}}`，此时不等待数据库写入。
- 同时向发送连接确认 `{"type":"ack","requestId":"client-uuid-1","message":{...}}`，代表已接收发送，不代表数据库提交成功。
- 后台保存提交成功后，通知 `{"type":"saved","message":{...,"id":123,"deliveryStatus":"saved"}}`；保存失败自动尝试最多 3 次，仍失败通知 `{"type":"save_failed","message":{...,"deliveryStatus":"failed"}}`。接收者按稳定消息键更新原消息，不能新增一个重复气泡。
- 相同 `clientMessageId` 重试返回原消息或重新尝试失败的保存任务，保留最初接收时间。广播可能重复，所有客户端按 `groupId + senderId + clientMessageId` 去重。
- 失败响应 `{"type":"error","status":403,"message":"请先加入群组","requestId":"client-uuid-1"}`，失败不显示为已发送。
- 心跳 `{"type":"ping"}`，响应 `{"type":"pong"}`。页面每 25 秒心跳，无响应会重连。
- 接收和广播前验证 Token；后台每 5 秒检查失效连接。退出登录、改密、删用户后旧连接不能继续收发。没有认证的连接会在 10–15 秒内关闭。

页面点击发送后立即展示消息并清空输入框，可以连续发送和切换群组。每条消息显示“发送中”“已发送 · 正在保存”“已保存”或“未保存成功”。失败和确认超时可点击消息旁的重试，沿用同一编号。待确认的本人消息保存在当前标签页 sessionStorage，刷新和重连时先核对历史，再补发尚未确认的记录；明确失败的消息由用户手动重试。

保存任务使用独立的 `chat-persistence-*` 线程，FIFO 队列容量为 1000；待保存/失败记录也限制在 1000 条。队列满时返回 503，不先广播，更不会回退到发送线程同步写库。仅最近 1000 条成功结果缓存于内存，较早消息重试仍由数据库唯一键去重。正常关闭最多等待 30 秒排空队列。

实现依据：[Spring WebSocket API](https://docs.spring.io/spring-framework/reference/web/websocket/server.html)。并发写入使用 `ConcurrentWebSocketSessionDecorator`，限制发送缓冲和发送时间。

## 部署边界

当前与原 Sa-Token 登录配置一致，支持单个应用实例中的多个用户和多个连接。已保存消息、群组、成员和历史边界保存在 MySQL，应用重启不丢失；现有登录态在内存中，重启后需要重新登录。异步队列在内存中：进程崩溃、强制终止或关闭等待超时可能丢失尚未保存的消息，不能将 ack 当作持久化保证。若要求异常断电也不丢消息，需要进一步使用持久化消息队列或本地持久化日志。跨实例还需要共享消息通道及登录态共享存储。当前群组为公开可加入群组，未实现邀请审批或私密群。

## 验证

```powershell
# H2 MySQL 模式下运行真实 HTTP + WebSocket 测试，不连接业务 MySQL
mvn '-DforkCount=0' '-Dtest=ChatIntegrationTests,ChatDeliveryServiceTests' test

# 同时运行真实 MySQL 群聊测试和原有登录/用户回归测试
$env:RUN_MYSQL_INTEGRATION_TESTS = 'true'
try { mvn '-DforkCount=0' test } finally { Remove-Item Env:\RUN_MYSQL_INTEGRATION_TESTS }
```

真实数据库测试只清理本次创建的账号、群组、成员和消息。覆盖多用户互发、跨群隔离、身份伪造、历史 30 条边界、分页、重复加入、并发加入/发送、幂等重试、断线补消息、退出后的连接拒绝、输入校验、跨源握手限制和发送者删除后的消息保留。

手工验收可在两个独立浏览器会话分别注册/登录不同账号：账号 A 创建群组，账号 B 刷新列表并加入；双方发送消息确认实时显示；刷新页面重新进入群组确认消息保留。页面使用每个标签页独立的 `sessionStorage` 保存登录凭证。

异步保存验收还会显式锁住数据库写入，确认两条消息在数据库仍为 0 条时已收到推送和 ack；并覆盖保存失败的自动/手动重试、有界队列拒绝和入群等待先前保存的顺序。

2026-10-19 异步版本验收：19 项自动化测试全部通过（队列与重试 3 项、H2 群聊 5 项、真实 MySQL 群聊 5 项、原有登录/用户 5 项、启动上下文 1 项）。双浏览器验证了连续发送、即时清空输入、保存状态更新、失败通知后的重试去重，以及原有 30 条历史边界、刷新补齐、自动重连和移动布局。浏览器保存失败通知使用模拟帧验证交互，后端实际异常重试由单元测试覆盖。
