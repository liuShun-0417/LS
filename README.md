# LS 整合版（后端 + 前端）

本仓库整合了两个来源，作为 liuShun-0417/LS 的完整升级版：

- 后端：整合自 L_fly_J/stu（Spring Boot 4 + MyBatis-Plus + MySQL + Sa-Token + Redisson/Redis + WebSocket + SSE + AI），位于仓库根目录。
- 前端：整合自 L_fly_J/work-admin（Vue 3 + Element Plus + Vuex + Vue Router），位于 work-admin/。

后端功能与接口说明见下方；前端启动方式见文末「前端 work-admin」。

---
# demo

## 消息对话

登录后访问 `/`，与 GPT-5.6 Luna 进行真实 AI 对话，通过 SSE 实时输出模型回复。支持多轮上下文、五档思考强度、停止生成、失败重试和清空对话。密钥只保存在后端本地配置或环境变量中；配置、接口和验收方式见 [AI 消息对话说明](docs/message-sse.md)。

## MySQL 配置

项目使用 MyBatis-Plus 3.5.17（Spring Boot 4 Starter、分页插件）和 MySQL Connector/J，数据库操作通过 `UserMapper` 完成，无需手写 JDBC 查询。`system/entity/User.java` 对应 `user` 表，`system/config/MybatisPlusConfig.java` 配置每页 20 条所需的分页拦截器。

默认连接 `118.25.25.164:3306/demo`，用户名 `stu`，密码通过环境变量 `DB_PASSWORD` 提供（默认空）。数据库与已有 `user` 表须事先存在；应用启动时通过 `db/chat-schema.sql` 自动创建缺失的三张群聊表，不改写已有用户和消息。已有数据库迁移流程的环境可预先执行该 SQL，再设置 `chat.initialize-schema=false` 关闭自动建表。

当前连接信息配置在 `src/main/resources/application.yaml` 中。项目也会加载根目录的 `application-local.yaml`，该文件已加入 `.gitignore`，可用于设置本地 `spring.datasource` 覆盖配置。IDEA 运行配置的工作目录应设为项目根目录。

其他环境可使用 Spring Boot 标准环境变量 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 覆盖连接信息。

```powershell
mvn spring-boot:run
```

服务默认监听 `8080` 端口。

## 群组聊天

登录后进入 `/chat.html`，可创建、加入群组并使用 WebSocket 实时收发消息。消息先即时推送，再由后台队列异步保存到 MySQL；页面支持连续发送并单独显示保存状态，失败可重试。成员只能读取首次入群前最近 30 条历史，以及入群后的消息。支持消息去重、断线补齐和退出登录后连接失效。异步队列在内存中，已发送确认不等于持久化完成；接口、异常退出边界和验收方法见 [群组聊天文档](docs/group-chat.md)。

## 登录与注册

打开 `http://localhost:8080/login.html` 登录，点击“注册用户”可以创建账号并直接进入群聊；也可直接访问 `/login.html?mode=register`。指定 `next` 时仍返回指定的同源页面。群聊入口为 `/chat.html`，右上角可切换用户管理或退出登录。原 `/` 消息页、群聊页和 `/users.html` 用户页在未登录时会跳转登录页。

| 方法 | 路径 | 请求体 | 返回 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | `{"name":"张三","account":"zhangsan","passwd":"example-password"}` | 201；`tokenName`、`tokenValue`、`user` |
| POST | `/api/auth/login` | `{"account":"zhangsan","passwd":"example-password"}` | 200；`tokenName`、`tokenValue`、`user` |
| GET | `/api/auth/me` | 无 | 200；当前用户 |
| POST | `/api/auth/logout` | 无 | 204；注销当前 Token |

除注册和登录外，`/api/**` 均要求在请求头中携带 `satoken: <tokenValue>`；未登录返回 401。前端将 Token 保存在当前浏览器标签页的 `sessionStorage`，关闭标签页后需重新登录。Sa-Token 通过 Redisson 将 token 与会话持久化到 Redis，服务重启后登录态依然保留，多实例部署可共享同一 Redis。改密或删除用户会撤销该用户的登录态。旧版单次 MD5 密码在成功登录后会升级到当前三次 MD5 格式。

### Redisson + Redis 登录持久化

登录 token 与会话数据通过 Redisson 写入 Redis，不再依赖进程内存。服务重启后，Redis 中未过期的 token 依然有效，前端无需重新登录。

- 依赖：`org.redisson:redisson` + `cn.dev33:sa-token-redisson`（`SaTokenDaoForRedisson`）。
- 配置：`src/main/resources/application.yaml` 中的 `redisson.*`，或环境变量 `REDIS_ADDRESS`、`REDIS_PASSWORD`、`REDIS_DATABASE`。
- 默认连接 `redis://127.0.0.1:6379`（db 0，无密码）。
- Redis 键：`satoken:login:token:<tokenValue>`（值为 userId，TTL 约 24 小时）、`satoken:login:session:<userId>`（用户会话）。
- 要求 Redis 6.0+（Redisson 使用了 `SET ... KEEPTTL`）。
- 代码位置：`src/main/java/com/example/demo/system/config/RedissonConfig.java`。

已实现 RBAC：每位用户绑定一个角色，角色关联菜单和按钮权限，管理接口独立鉴权。新注册及迁移前用户默认绑定普通用户角色，仅能使用消息演示与群聊。首次使用请通过 `RBAC_BOOTSTRAP_ADMIN_ACCOUNT` 或 `rbac.bootstrap-admin-account` 指定一个已有账号作为超级管理员。完整初始化方式、数据模型、接口与权限边界见 [RBAC 文档](docs/rbac.md)。

管理页面默认提供 `/users.html`（用户管理与单角色绑定）、`/roles.html`（角色管理与权限树分配）、`/menus.html`（菜单及按钮权限管理）。导航和操作按钮按当前权限显示。

## 用户接口

浏览器访问 `http://localhost:8080/users.html` 可使用用户管理页面：查看分页列表、新增、编辑和删除用户。页面与 Spring Boot 同源部署，无需安装前端依赖；原消息演示页仍在 `/`，页面间可以互相切换。用户列表没有记录时显示空状态，不会创建示例用户。

代码位于 `src/main/java/com/example/demo/system`，使用现有 `demo.user` 表。

| 方法 | 路径 | 功能 | 成功状态码 |
| --- | --- | --- | --- |
| GET | `/api/users?page=1` | 用户分页列表，按 ID 倒序；默认第 1 页，每页 20 条 | 200 |
| GET | `/api/users/{id}` | 用户详情 | 200 |
| POST | `/api/users` | 新增用户 | 201 |
| PUT | `/api/users/{id}` | 修改用户 | 200 |
| DELETE | `/api/users/{id}` | 删除用户 | 204 |

新增请求示例（`Content-Type: application/json`）：

```json
{"name":"张三","account":"zhangsan","passwd":"example-password"}
```

修改请求示例：

```json
{"name":"李四","account":"lisi"}
```

`name` 可空；`account` 必填且不能全为空白；新增时 `passwd` 必填且不能全为空白。所有字段最长 100 字符。修改时须提供 `account`，省略 `name` 会设为 NULL；`passwd` 省略、为 NULL 或全为空白时保留原密码，提供有效值时更新密码。

密码接收明文，由后端按当前业务代码连续进行三次 UTF-8、小写 MD5 摘要计算；客户端不要提前进行 MD5。用户响应包含 `id`、`name`、`account`、`roleId`、`roleName`，不包含密码摘要。MD5 仅用于兼容本教学表的约定，正式密码存储应改用自适应密码哈希。

列表返回 `{ "items": [], "page": 1, "pageSize": 20, "total": 0, "totalPages": 0 }`；页码从 1 开始，超出范围时 `items` 为空，`page` 保留请求页码，`totalPages` 表示实际总页数。详情、新增和修改返回用户对象；删除成功无响应体。页码小于 1、格式不正确等参数错误返回 400，用户不存在返回 404，账号重复返回 409，错误响应包含 `status` 和 `message`。

注册、创建及修改账号时会检查重复，但若数据库仍有历史重复账号，该账号将无法安全登录。请先人工确认并处理历史重复记录，再执行 `src/main/resources/db/user-account-unique.sql` 建立唯一索引，以阻止并发写入重复账号；部署时不自动删除或改写已有用户。

普通测试：`mvn test`。真实 MySQL 接口测试需显式开启，测试会新建临时用户并在结束时清理：

```powershell
$env:RUN_MYSQL_INTEGRATION_TESTS = 'true'
try { mvn test } finally { Remove-Item Env:\RUN_MYSQL_INTEGRATION_TESTS }
```

---

## 前端 work-admin

进入 work-admin/，准备 Node.js 与 npm（或 pnpm）：

```bash
cd work-admin
npm install
npm run serve      # 开发模式，默认 http://localhost:8080
npm run build      # 生产构建，产物在 dist/
```

前端通过代理访问后端 /api（见 work-admin/vue.config.js），主要页面包括登录、注册、工作台、消息、群聊、用户管理、角色与菜单权限管理。
