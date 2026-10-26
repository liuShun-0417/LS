# AI 消息对话（SSE）

登录后访问 `/`，使用 **GPT-5.6 Luna** 进行真实 AI 对话。浏览器只调用本站 `/api/messages`，后端通过 `https://gpt.wenmeng.online/v1/responses` 请求模型，将上游文本增量立即转成 SSE；没有模拟逐字延时，也不回传内部推理内容。

## 配置

真实密钥放在 Git 忽略的根目录 `application-local.yaml`，或通过 `AI_API_KEY` 环境变量提供。不要将密钥写入前端、文档或 Git。现有本地数据库配置保持原样。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_API_KEY` | 空 | 必填；缺少时返回 503 |
| `AI_BASE_URL` | `https://gpt.wenmeng.online/v1` | 后端固定配置，不接受客户端指定地址 |
| `AI_MODEL` | `gpt-5.6-luna` | 模型标识 |
| `AI_MAX_OUTPUT_TOKENS` | `8192` | 单次生成预算，含模型推理；可配置 256～128000 |
| `AI_TIMEOUT_SECONDS` | `180` | 单次请求总时限；可配置 10～600 秒 |

所有上游请求均发送 `store: false`、`stream: true`。这是 API 请求设置，不代表对网关内部数据保留政策的保证。页面可选择 `low / medium / high / xhigh / max` 思考强度，默认 `medium`；较高强度可能等待更久。

## 多轮上下文

浏览器携带最近最多 20 轮**已完成**的问答，总计不超过 64000 个 UTF-16 代码单元（包含本次问题）。超过预算时从最旧一轮开始排除；单条历史回复超过 32000 字符时，不再携带该轮及更旧上下文。当前提问最多 2000 字符。

停止、失败、超时或输出达到限制的半条回复仍展示，但不加入下一次请求的上下文。重新生成使用当时的上下文和思考强度；开始新的提问后，旧失败消息不再提供重试，避免改写后续对话。清空和刷新均清除当前页面的上下文，不将会话持久化到数据库。

## 接口契约

`POST /api/messages`，请求头 `Content-Type: application/json`、`Accept: text/event-stream, application/json`、`satoken: <登录 token>`。需要 `message:access` 权限。

```json
{
  "message": "那应该怎么做？",
  "history": [
    {"role": "user", "content": "我想学习 Spring Boot"},
    {"role": "assistant", "content": "可以先创建一个 REST 接口。"}
  ],
  "effort": "medium"
}
```

`history` 和 `effort` 可省略。历史消息只接受 `user`、`assistant` 两种角色，必须成对交替；不接受客户端注入系统角色。

成功响应为 `text/event-stream`，每个事件以空行分隔：

```text
event:start
data:{"model":"gpt-5.6-luna","status":"generating"}

event:delta
data:{"text":"可以先从"}

event:delta
data:{"text":"创建项目开始。"}

event:done
data:{"status":"completed"}

```

只在收到上游 `response.completed` 且存在文本时发送 `done`；上游失败、截断和不完整输出发送 `error` 事件（`data:{"message":"安全的错误提示"}`），保留已输出文本供查看。只转发回答和拒绝回答的文本，不转发上游内部错误详情、请求头、密钥或推理数据。

打开流之前：未登录 401，无权限 403，无效请求 400，缺少配置或同时生成超过 8 路 503。打开流之后：通过 `error` 事件报告上游错误。默认每 5 秒发送 SSE 注释心跳；前端 45 秒无网络数据则终止请求。停止生成、离开页面或退出登录会取消本站请求，后端检测断开后取消上游请求并关闭响应流。停止传输不能保证网关退还已消耗的额度。

页面支持 Enter 发送、Shift + Enter 换行、输入法组合输入、停止生成、失败重试、清空对话、手机布局；内容用 `textContent` 渲染。当前呈现纯文本，不执行模型返回的 HTML。

## 验证

`mvn "-DforkCount=0" "-Dtest=MessageIntegrationTests" test`

自动测试使用隔离的 H2 数据库与本地上游 HTTP 测试服务，不调用付费模型，覆盖增量转发、Unicode、上下文、参数、权限、上游错误脱敏、异常断流和停止后续发。真实网关联调单独进行。

参考：[OpenAI 流式响应文档](https://developers.openai.com/api/docs/guides/streaming-responses)、[GPT-5.6 Luna](https://developers.openai.com/api/docs/models/gpt-5.6-luna)。
