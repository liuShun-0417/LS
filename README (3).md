# work-admin 工作管理中台

基于 **Vue 3（选项式 API）+ Element Plus + Vuex + Vue Router + Axios + MockJS** 的工作管理中台教学项目，按「第 0～10 课」的最终状态实现。

## 功能

- 登录（`admin` / `manager` / `viewer` 三个演示账号）
- 中台布局（flex 骨架 + 侧边动态菜单 + 顶部栏）
- 仪表盘（ECharts 柱状图 + 环形图，含 resize / dispose）
- 用户管理（搜索、新增、编辑、删除、批量删除，走 Mock 接口）
- 组件封装（`UserFormDialog` 自定义 v-model、`SearchBar`、`AvatarUpload` 本地头像预览）
- 路由权限守卫（`meta.roles` 控制页面访问）+ 按钮级权限（`permissions` + `v-if`）
- 登录状态持久化（Vuex + localStorage）
- 环境变量与打包配置（`vue.config.js`、`.env.*`、路由懒加载）

## 快速开始

```bash
# 进入项目目录
cd work-admin

# 安装依赖
npm install

# 启动开发服务（默认 http://localhost:8080）
npm run serve

# 生产构建（产物在 dist/）
npm run build
```

## 演示账号（密码均为 `123456`）

| 账号 | 角色 | 可访问页面 | 可执行操作 |
|---|---|---|---|
| `admin` | 管理员 | 仪表盘、用户管理 | 新增、编辑、删除 |
| `manager` | 负责人 | 仪表盘、用户管理 | 编辑 |
| `viewer` | 访客 | 仪表盘 | 无 |

> 演示用的登录凭证与权限均由前端模拟，仅用于课堂观察 UI；真实项目必须由后端鉴权。

## 目录结构

```
work-admin/
├── public/index.html
├── src/
│   ├── api/
│   │   ├── request.js          # axios 实例 + 请求/响应拦截器
│   │   └── user.js             # 用户相关接口
│   ├── components/
│   │   ├── UserFormDialog.vue  # 用户弹窗表单（自定义 v-model）
│   │   ├── SearchBar.vue       # 搜索栏
│   │   └── AvatarUpload.vue    # 本地头像预览
│   ├── mock/index.js           # MockJS 模拟后端
│   ├── router/index.js         # 路由 + 全局守卫
│   ├── store/index.js          # Vuex + localStorage
│   ├── views/
│   │   ├── Layout.vue
│   │   ├── Login.vue
│   │   ├── Dashboard.vue
│   │   └── User.vue
│   ├── App.vue
│   └── main.js
├── .env.development
├── .env.production
├── vue.config.js
├── babel.config.js
└── package.json
```

## 说明

- 数据由 MockJS 拦截请求返回，保存在浏览器内存中，**刷新页面会重置**。
- 对接真实后端时：删除 `src/main.js` 中的 `import './mock'`，确认后端接口路径与返回结构与 `src/api/user.js`、`src/mock/index.js` 约定一致，再重启/重新构建。
- 本项目使用哈希路由（URL 带 `#`），部署时无需服务端回退配置。
