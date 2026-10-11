# MediFood Circle (药食同源养生交流平台) 🌿

## 🌟 项目简介

欢迎来到 **MediFood Circle**！这是一个专为大众设计的“药食同源”主题的健康交流平台。我们的目标是把晦涩难懂的中医养生知识、药膳食谱变得简单有趣，让每个人都能轻松学习如何通过日常饮食来调理身体。

在这个平台上，你可以：
- 📖 **学习知识**：浏览专业的养生文章，了解各种食材的功效。
- 🍲 **发现食谱**：寻找适合自己的健康药膳，或者分享你的独家配方。
- 💬 **社区交流**：发布日常健康打卡，和志同道合的朋友互动。
- ❓ **问答解惑**：遇到养生难题随时提问，有专业认证医师为你解答。
- 🤖 **AI 助手**：随时随地向智能 AI 提问，获取个性化的健康建议！

如果你想了解更硬核的技术细节（架构、中间件、权限等），请查看 [docs/项目详细说明.md](docs/项目详细说明.md)。

---

## 🛠️ 技术栈 (我们用什么做的？)

这个项目采用了目前主流的“前后端分离”和“微服务”架构，保证了系统的高性能和可扩展性。

### 后端技术 (大脑)
- **Spring Boot 3.2+ & Spring Cloud**: 强大的微服务框架，把大系统拆分成小模块，互不干扰。
- **MySQL 8.0+**: 关系型数据库，负责记住所有的用户、文章和评论等数据。
- **Redis**: 缓存神器，把大家最爱看的“热门内容”存起来，秒开不卡顿！
- **RabbitMQ**: 消息队列，负责在后台默默帮你处理点赞、评论的通知，不让你久等。
- **Elasticsearch**: 超级搜索引擎，想搜什么知识和食谱，一搜就出来。
- **SkyWalking**: 系统监控管家 (APM)，负责盯着各个微服务，一旦哪里出bug或者变慢了，立刻报警。
- **AI 大模型 (DeepSeek等)**: 提供聪明的智能问答服务。

### 前端技术 (颜值)
- **Vue 3 & Vite 5**: 最新最快的前端框架，页面切换丝滑流畅。
- **Element Plus**: 漂亮的 UI 组件库，按钮、弹窗都靠它。
- **Pinia**: 状态管理，帮你记住登录状态和个人喜好。
- **Axios**: 负责和后端“大脑”通信的快递员。

---

## 📦 核心功能模块说明书

为了让你清楚每个功能是怎么运作的，这里列出了所有模块的用途和接口。

### 1. 🚪 统一网关模块 (Gateway)
- **用途**: 整个系统的大门。所有的请求都要经过这里，它负责检查你有没有登录（验证 JWT 令牌），把你送到对应的微服务去。
- **端口**: 默认在 `2020` 端口监听。

### 2. 👤 用户模块 (User)
- **用途**: 管家婆，负责大家的注册、登录、个人信息修改。还负责给“专业医师”发认证徽章！
- **主要功能**:
  - 注册/登录 (账号密码)
  - 个人资料与头像修改
  - 医师资格提交与审核
- **核心接口**:
  - `POST /api/user/register` - 注册 (参数: 用户名, 密码)
  - `POST /api/user/login` - 登录 (参数: 用户名, 密码; 返回: Token令牌)
  - `GET /api/user/info` - 获取我的信息

### 3. 📚 养生知识模块 (Knowledge)
- **用途**: 养生图书馆。专业医师和管理员在这里发布权威的药食同源文章。
- **主要功能**:
  - 发布/浏览/搜索知识文章
  - 按分类、食材筛选
  - 点赞、评论、收藏
  - **热门榜单**: 首页展示大家最关心的内容 (Redis 加速)
- **核心接口**:
  - `GET /api/knowledge/list` - 知识列表 (支持分页)
  - `GET /api/knowledge/hot` - 热门知识 (无需登录直接看)
  - `POST /api/knowledge/{id}/like` - 给好文章点个赞

### 4. 🍲 健康食谱模块 (Recipe)
- **用途**: 大家的健康厨房。展示详细的药膳做法、适合什么人吃、有什么功效。
- **主要功能**:
  - 食谱的发布与浏览
  - 功效、季节等标签检索
  - 互动与评价
- **核心接口**:
  - `GET /api/recipe/list` - 食谱列表
  - `GET /api/recipe/{id}` - 查看这道菜怎么做

### 5. 🏘️ 社区互动模块 (Community)
- **用途**: 我们的健康朋友圈。每天打卡、分享心得都在这里。
- **主要功能**:
  - 发布带图动态
  - 关注养生达人，查看专属“关注流”
  - 实时消息通知 (SSE 技术，有人点赞立刻知道)
- **核心接口**:
  - `POST /api/community/post` - 发一条新动态 (参数: 内容, 图片)
  - `GET /api/community/feed` - 看我关注的人发了啥
  - `POST /api/user/{userId}/follow` - 关注大佬

### 6. 🙋 问答交流模块 (QA)
- **用途**: 养生门诊。不懂就问，大家一起出主意，还有专业医师解答。
- **主要功能**:
  - 提问与回答
  - 题主可以选出“最佳答案”
- **核心接口**:
  - `POST /api/qa/question` - 提出我的疑问
  - `POST /api/qa/answer` - 我来解答
  - `POST /api/qa/answer/{id}/best` - 选为最佳答案

### 7. 🤖 智能助手模块 (AI)
- **用途**: 你的私人 24 小时健康顾问。结合了最新 AI 大模型。
- **主要功能**:
  - **多轮对话**：带记忆的上下文对话，支持滑动窗口+历史摘要压缩，对话历史持久化存储（MySQL + Redis 双存储）
  - **个性化推荐**：根据用户画像（体质、偏好标签）和浏览历史，提供量身定制的养生建议
  - **RAG 增强检索**：多源语义搜索（知识库、食谱库、问答库、社区），动态 topK 自适应，结果重排序
  - **图片识别**：拍照识食材/菜品，AI 自动分析药食同源功效，推荐相关食谱；支持图片粘贴上传
  - **语音输入**：支持语音提问（Web Speech API），说出问题即可获得回答
  - **Agent 智能体**：AI 像一个真正的助手，根据你的问题自动调用最合适的工具（共10个工具），ReAct 循环推理
    - *搜索类*：`semantic_search` 语义检索、`recipe_search` 食谱搜索、`knowledge_search` 知识搜索、`qa_search` 问答搜索
    - *个性化*：`user_profile` 获取用户体质画像、`bmi_calculator` BMI健康计算
    - *详情类*：`recipe_detail` 获取完整食谱做法、`ingredient_lookup` 查询食材药性功效禁忌
    - *辅助类*：`food_compatibility` 检查食物搭配、`weather_query` 实时天气查询（支持经纬度精确定位+多级降级策略）
  - **用户反馈**：每条 AI 回答可 👍/👎 评价，用于持续优化回复质量
  - **Token 用量统计**：实时记录每次调用的 Token 消耗，支持每日/每月/总计统计
  - **质量仪表盘**：提供 RAG 命中率、平均响应时间、反馈满意度等指标
  - **对话管理**：新建/重命名/删除对话，对话列表持久化
  - **响应缓存**：常见问题缓存 1 小时，减少重复 API 调用
  - **Embedding 缓存**：文本向量化结果缓存 24 小时，加速 RAG 检索
- **核心接口**:
  - `POST /api/ai/ragStream` - RAG 增强流式对话（普通模式）
  - `POST /api/ai/agent/reactStream` - Agent 智能体流式对话（智能模式，AI 自动调用工具）
  - `POST /api/ai/agent/react` - Agent 非流式问答（返回思考步骤+工具调用+最终答案）
  - `GET /api/ai/agent/tools` - 查看所有可用工具列表
  - `POST /api/ai/chat-with-image` - 图片 + 文本智能问答
  - `POST /api/ai/analyze-food` - 食物图片识别（食材/菜品模式）
  - `POST /api/ai/analyze-report` - 体检报告智能分析
  - `GET /api/ai/conversations` - 获取对话列表
  - `POST /api/ai/conversations/{id}/messages` - 追加对话消息
  - `POST /api/ai/feedback/{messageId}` - 提交 AI 回答反馈
  - `GET /api/ai/metrics/dashboard` - 获取 AI 质量仪表盘

### 8. 🌤️ 节气饮食推荐系统 (Recipe + Third Party)
- **用途**: 首页核心亮点！结合24节气和实时天气，为你推荐当季最合适的食谱。
- **工作原理**:
  - 浏览器获取你的地理位置（经纬度）→ 发送给后端
  - 后端调用天气服务获取你所在城市的实时天气
  - 系统根据当前节气（如"立秋"）和天气（如"晴热"），智能匹配最合适的食谱
  - **双层匹配策略**：先精确匹配节气标签（suitable_seasons），再关键词兜底
- **核心接口**:
  - `GET /api/recipe/seasonal-diet` - 获取今日节气饮食推荐（支持 lat/lng 参数）
  - `GET /api/weather/current` - 获取实时天气（支持经纬度定位）

### 9. 💬 即时通讯模块 (Message)
- **用途**: 用户之间的私信聊天，支持文字、图片、语音、引用回复。
- **主要功能**:
  - WebSocket 实时通信
  - 语音消息录制与发送
  - 消息撤回（2分钟内）与引用回复
  - 分享卡片（食谱/知识/问答）
  - **在线状态感知**：实时显示好友在线/离线状态（绿色小圆点）
  - **已读回执**：消息被阅读后自动通知发送方
  - **好友申请机制**：发送申请→同意→自动互关→畅聊，拒绝陌生人骚扰
- **核心接口**:
  - WebSocket `/ws/message` - 实时聊天连接
  - `GET /api/message/conversations` - 会话列表（含在线状态）
  - `POST /api/message/friend-request/send` - 发送好友申请
  - `POST /api/message/friend-request/accept/{id}` - 同意好友申请
  - `GET /api/message/online-status/{userId}` - 查询用户在线状态

### 10. 📄 API 文档 (Knife4j)
- **用途**: 所有微服务接口的在线文档，支持在线调试。
- **访问方式**: 启动服务后，访问 `http://localhost:{端口}/doc.html`
- 各服务端口：User(8201), Knowledge(8202), Recipe(8203), Community(8204), QA(8205), AI(8206), Message(8207), ThirdParty(8208)

### 11. 🎯 个性化推荐系统 (Recipe)
- **用途**: 基于用户行为的智能食谱推荐，让每个用户看到最适合自己的内容。
- **工作原理**（三层策略）:
  - **第一层 内容推荐**：分析用户点赞/收藏的食谱标签，找到标签相似度最高的食谱
  - **第二层 协同过滤**：找到口味相似的用户，推荐他们喜欢的其他食谱
  - **第三层 热门兜底**：新用户返回全局热门食谱
- **核心接口**:
  - `GET /api/recipe/recommend` - 获取个性化推荐食谱
  - `GET /api/recipe/hot` - 获取热门食谱（无登录时使用）

---

## 📁 项目结构地图

```text
MediFood_Circle/
├── backend/                    # ⚙️ 后端微服务大本营
│   ├── medi-food-gateway/      # 统一大门 (网关)
│   ├── medi-food-common/       # 大家共用的工具包
│   ├── medi-food-user/         # 用户服务
│   ├── medi-food-knowledge/    # 知识服务
│   ├── medi-food-recipe/       # 食谱服务 + 节气饮食推荐
│   ├── medi-food-community/    # 社区服务
│   ├── medi-food-qa/           # 问答服务
│   ├── medi-food-ai/           # AI 智能服务 (DeepSeek)
│   ├── medi-food-message/      # 即时通讯服务 (WebSocket)
│   └── medi-food-third-party/  # 第三方服务 (天气/OSS)
├── frontend/                   # 🎨 前端 Vue 项目
│   ├── src/
│   │   ├── api/                # 专门和后端打交道的代码
│   │   ├── components/         # 按钮、顶栏等公共零件
│   │   ├── views/              # 各种页面 (首页、社区、食谱等)
│   │   └── store/              # 记忆库 (存登录状态等)
├── database/                   # 💾 数据库设计图
│   ├── init.sql                # 建表脚本
│   └── seed_admin_and_recipes.sql  # 管理员+食谱种子数据
├── docker/                     # 🐳 Docker 部署配置
│   ├── docker-compose.yml
│   ├── backend/Dockerfile
│   └── frontend/Dockerfile
└── docs/                       # 📄 详细文档和参考资料
```

---

## 🚀 如何把项目跑起来？(快速开始)

如果你想在自己的电脑上运行这个项目，请跟着下面的步骤做：

### 准备环境
你需要安装这些软件：
- **Java (JDK 17+)**
- **Node.js (16+)**
- **MySQL 8.0+**
- **Redis 6.0+**
- **RabbitMQ** (为了消息通知能正常工作)

### 1. 启动数据库和中间件
1. 打开 MySQL，创建一个名为 `medifood_circle` 的数据库。
2. 运行 `database/init.sql`，把表结构都建好。
3. 启动你的 Redis 和 RabbitMQ。

### 2. 启动后端 (微服务)
1. 用 IDEA 等开发工具打开 `backend` 文件夹。
2. 依次检查每个模块里 `src/main/resources/application.yml` 的数据库和 Redis 密码是不是你自己的。
3. 按照顺序启动微服务：
   - 先启动 **User**, **Knowledge**, **Recipe**, **Community**, **QA**, **AI**。
   - 最后启动 **Gateway** (因为网关要代理大家)。

### 3. 启动前端
1. 打开终端，进入 `frontend` 文件夹。
2. 输入 `npm install` 下载依赖包。
3. 输入 `npm run dev` 启动前端。
4. 打开浏览器，访问控制台里提示的本地地址 (通常是 `http://localhost:5173`)，就可以看到漂亮的界面啦！

---

## 💡 给开发者的建议

1. **写代码要整洁**: 请遵守阿里巴巴 Java 开发规范，尽量给代码加上通俗易懂的注释。
2. **遇到错误不要怕**: 我们有统一的异常处理 (`GlobalExceptionHandler`)，报错了会返回友好的提示。
3. **保持思考**: 这是一个为了解决真实需求而诞生的项目。每次加新功能前，先想想：“如果我是用户，这个功能好用吗？”

---

*"用最简单的代码，做最懂你的健康管家。" —— MediFood Circle 团队*

---

## 📝 优化记录 (Changelog)

### 2026-07-22 优化

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| 🔧 **包名统一** | third-party 模块包名从 `com.meidfood` 改为 `com.medifood`，解决组件扫描不到的问题 | third-party, recipe |
| 🎯 **节气食谱双层匹配** | 新增 `suitable_seasons` 字段，先精确匹配节气标签，再关键词兜底，推荐准确率大幅提升 | recipe (Entity/Mapper/Service), database |
| 📄 **API 文档** | 集成 Knife4j (Swagger)，启动后访问 `http://localhost:{port}/doc.html` 即可在线调试 | 所有 8 个微服务模块 |
| 🌱 **种子数据增强** | 10条核心食谱 + 100+条种子食谱均设置节气标签，8个特殊节气各有5条专属推荐 | database |
| 🧹 **Common 模块优化** | 排除 spring-boot-starter-tomcat 依赖，避免与 Gateway WebFlux 冲突 | common |
| 📝 **文档完善** | 更新 README 模块说明、项目结构、API 文档入口 | README.md |

### 2026-07-22 优化（第二轮）

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| 🌤️ **天气服务国产化** | 新增 WeatherApiService，支持和风天气（QWeather）+ 高德地图（Amap）API，四级降级策略：和风→wttr.in→兜底数据 | third-party (service/controller) |
| 🎯 **个性化推荐算法** | 新增 RecommendationService，三层推荐策略：标签相似度内容推荐 → 协同过滤 → 热门兜底，Redis 缓存30分钟 | recipe (service/controller) |
| 📡 **推荐接口** | RecipeController 新增 `GET /api/recipe/recommend` 接口，登录用户可获取个性化推荐 | recipe controller |
| 🔄 **缓存联动** | 用户点赞/收藏/取消时自动清除推荐缓存，确保推荐结果实时更新 | recipe service impl |

### 2026-07-22 优化（第三轮 - 消息通知与私聊体验）

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| 🔔 **统一未读计数** | MessageController 新增 `/api/message/unified-unread` 接口，聚合私聊+通知未读数，顶部铃铛显示一个数字 | message controller, NotificationBell.vue |
| 🟢 **在线状态感知** | 新增 OnlineStatusService，WebSocket 连接/断开自动更新状态，30秒心跳保活，会话列表和个人聊天页显示在线小绿点 | message (service/config/controller), chat store, PrivateChat.vue, MessageCenter.vue |
| ✅ **已读回执** | markRead 时通过 WebSocket 向发送方推送 READ_RECEIPT 事件，消息气泡下方显示"已读/未读"标签 | message service impl, chat store, PrivateChat.vue |
| 👥 **好友申请机制** | 新增 FriendRequest 表+服务+控制器，替代原"只能发1条消息"的硬限制。用户可发送/同意/拒绝好友申请，同意后自动互关即可畅聊 | message (entity/mapper/service/controller), PrivateChat.vue, init.sql |
| ⚡ **通知列表性能优化** | 新增 `POST /api/community/notify/enrich` 批量接口，前端从逐个N+1请求（40+次）改为1次批量请求，大幅减少HTTP开销 | community controller, NotificationBell.vue, notify.js |
| 👀 **聚合通知详情** | 新增 `GET /api/community/notify/aggregate-actors` 接口，用户点击"X 等 N 人赞了你"可查看具体参与者列表，点击头像可跳转用户主页 | community controller, NotificationBell.vue, notify.js |

### 2026-07-22 优化（第四轮 - 前端页面布局统一）

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| 🧩 **PageLayout 统一布局** | 新建 `PageLayout.vue`，统一所有页面：1280px 最大宽度居中、20px 标准间距、300px 侧边栏、响应式适配（≤1024px 隐藏侧边栏） | PageLayout.vue（新增） |
| 🎯 **Header 导航栏重构** | 精简为 56px 高度、粘性定位+毛玻璃效果、图标化导航、用户下拉菜单优化 | Header.vue（重写） |
| 📊 **Sidebar 侧边栏升级** | 用户信息卡片（头像+昵称+统计）、彩色标签云、动态热门推荐、底部版权 | Sidebar.vue（重写） |
| 📄 **全部页面统一** | Home/Knowledge/Recipe/Community/QA/MessageCenter 全部改用 PageLayout，移除冗余嵌套结构 | 6个页面文件 |
| 🎨 **全局样式标准化** | 统一圆角（12px/8px）、统一滚动条、Element Plus 全局覆盖、过渡动画 | App.vue |

### 2026-07-22 优化（第五轮 - 前端页面布局全面统一）

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| 📄 **详情页布局统一** | KnowledgeDetail、RecipeDetail、QADetail、PostDetail 全部改用 PageLayout 替代旧版 el-container 嵌套结构，统一卡片圆角和间距 | 4个详情页 |
| 👤 **用户页布局统一** | UserProfile、OtherUserProfile、UserSettings、FollowList、MutualFollowList 全部改用 PageLayout，统一布局体验 | 5个用户页 |
| 🔐 **登录/注册页视觉升级** | 新增品牌 Logo（药食同源+图标）、柔和的四色渐变背景、浮动装饰圆动画、渐变色提交按钮、卡片圆角 16px | Login.vue、Register.vue |
| ✏️ **编辑/管理页布局统一** | KnowledgeEdit、RecipeEdit、BannerManage、DoctorCertReview 全部改用 PageLayout（无侧边栏模式），统一卡片样式 | 4个编辑/管理页 |
| 🎯 **全站一致性** | 所有 23 个 Vue 页面全部使用统一的 PageLayout 布局，移除所有冗余的 el-container/el-main 嵌套结构 | 23个页面 |

### 2026-07-22 优化（第六轮 - 编译修复与最终统一）

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| 🔧 **编译错误修复** | 修复 MessageCenter.vue、BannerManage.vue、DoctorCertReview.vue 中根 `<template>` 标签未闭合导致的编译失败 | 3个页面 |
| 🤖 **SmartQA 布局统一** | 智能问答页面改用 PageLayout（无侧边栏模式），调整高度计算适配新 56px 页头，保留内部对话侧边栏 | SmartQA.vue |
| ✅ **编译验证通过** | 全部 1952 个模块编译成功，无错误，构建产物大小正常 | 全部前端 |

### 2026-07-23 优化（第七轮 - 数据库修复与同步）

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| 🗄️ **suitable_seasons 字段修复** | 添加 `recipe.suitable_seasons` 列（安全迁移脚本，可重复执行），支持节气食谱精准推荐 | recipe 表 |
| 📨 **private_message 表创建** | 新增私信表，支持用户私聊消息存储（文本/图片/语音/引用消息） | 新增表 |
| 🖼️ **banner 表创建** | 新增首页轮播图表，支持后台管理轮播图（排序、启用/禁用） | 新增表 |
| 🔧 **common 模块依赖修复** | 添加 `jakarta.servlet-api` 和 `knife4j` 依赖，修复 `AuthInterceptor` 编译错误 | pom.xml |
| 🔧 **Recipe 模块编译修复** | 修复 `RecommendationService` 和 `RecipeServiceImpl` 之间的 import 缺失 | 2个文件 |
| 📝 **init.sql 同步** | 将 `private_message`、`banner` 表及初始化数据同步到 init.sql，新安装直接可用 | init.sql |

### 2026-07-23 优化（第八轮 - 医师功能全面升级）

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| 🏥 **医师服务模块 (medi-food-doctor)** | 新建独立微服务模块（端口8209），包含工作台、膳食计划、咨询管理、患者管理四大核心功能 | 新建模块（19个Java文件） |
| 📊 **医师工作台仪表盘** | 今日计划数、累计计划数、待回复咨询数、患者总数统计卡片 + 最近计划/待回复咨询快捷列表 | DoctorController, DoctorWorkspace.vue |
| 📋 **膳食计划系统** | 医师可为患者创建个性化膳食计划，支持标题/描述/详细内容/针对症状/建议天数/注意事项，查看次数统计 | DietPlan 实体/Mapper/Service, DietPlanList/Edit/Detail.vue |
| 💬 **膳食咨询系统** | 患者向医师发起咨询，医师回复，支持关闭咨询。回复列表显示医师标识，聊天式界面 | DietConsultation/ConsultationReply 实体/Mapper/Service, ConsultationList/Detail/MyConsultationList.vue |
| 👥 **患者管理** | 医师查看所有交互过的患者列表，显示膳食计划数和咨询数，支持快速创建计划 | PatientList.vue, PatientDTO |
| 🎯 **医师徽章展示** | 知识列表和食谱列表显示"医师推荐"绿色徽章，DTO 新增 isDoctor 字段，批量获取用户医师状态 | KnowledgeDTO/RecipeDTO, KnowledgeList/RecipeList.vue |
| 🔐 **医师路由守卫** | 新增 `requiresDoctor` 路由守卫，非认证医师访问医师功能页自动拦截并提示 | router/index.js |
| 🚪 **网关路由** | Gateway 新增 `/api/doctor/**` 路由到 `medi-food-doctor` 服务 | Gateway application.yml |
| 🗄️ **数据库完整同步** | user 表新增5个医师专业字段（hospital/department/title/specialties/practice_years），diet_plan/diet_consultation/consultation_reply 三张新表，安全迁移脚本 | init.sql, migration_add_doctor_full.sql |

### 2026-07-23 优化（第九轮 - AI 模块多轮对话与个性化推荐）

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| 💬 **多轮对话持久化** | 新建 `ai_conversation` 和 `ai_message` 表，启用 MySQL 数据源，实现对话历史服务重启不丢失 | 新增 3 张表、6 个 Java 文件 |
| 🔄 **双存储策略** | ConversationService 采用 MySQL 主存储 + Redis 缓存层（30 分钟 TTL），读写性能与持久化兼顾 | ConversationService/Impl |
| 📝 **滑动窗口+摘要** | DialogueContextService 保留最近 3 轮完整对话，更早消息压缩为摘要，每 5 轮触发 LLM 摘要更新 | DialogueContextService |
| 🎯 **用户画像注入** | 通过 Feign 调用 User 服务获取用户昵称/性别/体质，构建画像摘要注入 AI System Prompt | UserServiceClient, UserProfileService |
| 🏷️ **行为偏好分析** | 分析用户点赞/收藏的食谱和知识，提取高频标签作为偏好摘要，实现个性化推荐 | UserBehaviorService |
| 🔗 **前端联动** | SmartQA.vue 发送 `conversationId` 和 `userId`，支持多轮对话上下文和个性化推荐 | SmartQA.vue |
| 🔧 **Common 模块扩展** | EsSyncEvent.EntityType 新增 QA 和 COMMUNITY 枚举值，支持问答和社区内容的 ES 同步 | EsSyncEvent.java |

### 2026-07-23 优化（第十轮 - AI 模块性能优化与 Agent 升级）

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| ⚡ **AI 回答缓存** | ResponseCacheService 对非流式 AI 回答进行缓存（Redis，1 小时 TTL），相同问题二次提问直接返回 | ResponseCacheService |
| 🧠 **Embedding 缓存** | SiliconFlowEmbeddingService 增加 Redis 缓存层（24 小时 TTL），避免重复调用 Embedding API | SiliconFlowEmbeddingService |
| 🔥 **热门内容预计算** | EmbeddingPrecomputeService 每日凌晨 3 点自动预计算 Top 100 热门知识/食谱的 Embedding | EmbeddingPrecomputeService |
| 🔍 **食谱搜索工具** | RecipeSearchTool：基于 ES 全文检索的关键词食谱搜索，Agent 可自主调用 | RecipeSearchTool |
| 📚 **知识搜索工具** | KnowledgeSearchTool：基于 ES 全文检索的关键词知识搜索，Agent 可自主调用 | KnowledgeSearchTool |
| 🌤️ **天气节气工具** | WeatherQueryTool：查询当前节气/季节/天气 + 饮食建议，辅助节气养生回答 | WeatherQueryTool |
| 🤖 **ReAct Agent 服务** | ReActAgentService：Thought→Action→Observation 循环，最多 3 轮工具调用，支持 4 个工具 | ReActAgentService |
| 🔄 **Agent 流式回答** | AgentController 新增 `/react`（非流式）和 `/reactStream`（流式 SSE）两个端点 | AgentController |

### 2026-07-24 优化（第十一轮 - 熔断降级 & 限流保护）

| 优化项 | 说明 | 影响范围 |
|--------|------|----------|
| 🛡️ **Sentinel 全链路限流熔断** | 集成 Alibaba Sentinel，Gateway + 微服务双层防护，防止服务雪崩 | 全部 11 个模块 |
| 🚪 **Gateway 层 API 分组限流** | 认证 5/s、AI 10/s、上传 3/s、通用 100/s，自定义中文友好提示 | GatewaySentinelConfig |
| 🔥 **Gateway 层熔断规则** | AI 慢调用 50% 熔断 30s，通用 API 异常 30% 熔断 20s，自动恢复 | GatewaySentinelConfig |
| 🤖 **AI 模块精细化限流** | RAG 对话 10/s、图片识别 5/s、报告分析 3/s、Agent 5/s，防 API 费用失控 | AiSentinelConfig |
| 🔥 **AI 模块熔断降级** | 慢调用 > 50% 熔断 60s，异常 > 40% 熔断 30s，blockHandler 降级返回友好提示 | AiSentinelConfig, DeepSeekController |
| 🔐 **登录安全限流** | 登录 3/s、注册 2/s、Token 刷新 5/s，防暴力破解和批量注册 | UserSentinelConfig, UserController |
| 🔗 **Feign 调用熔断** | 6 个服务启用 `feign.sentinel.enabled=true`，配合 fallback 类自动降级 | 6 个服务 application.yml |
| 📊 **Sentinel Dashboard** | Docker Compose 新增可视化控制台（端口 8858），实时监控流量和熔断状态 | docker-compose.yml |
| 🗄️ **Nacos 规则持久化** | Sentinel 规则通过 Nacos DataSource 持久化，服务重启后规则不丢失 | 所有 application.yml |
| 📝 **@SentinelResource 注解** | AI 和 User 关键接口添加注解，支持 blockHandler 自定义降级处理 | DeepSeekController, UserController |

### 2026-08-24 优化（第十二轮 - 全链路安全加固）

> 本轮针对全部后端接口的安全审计结果进行修复，堵住认证绕过、隐私泄露、云存储滥用与 XSS 四类高危风险。

| 修复项 | 说明 | 影响范围 |
|--------|------|----------|
| 🚪 **网关白名单精确匹配** | 登录/注册/刷新白名单由 `contains` 子串匹配改为 `Set` 精确匹配，杜绝 `/login/../info` 路径穿越绕过鉴权 | GatewayOfJwtConfig |
| 🛡️ **剥离伪造身份头** | 所有路由入口强制删除客户端自带的 `X-User-Id`/`X-Username` 头，身份头只能由网关在 JWT 校验后写入（`set` 替换语义双保险） | GatewayOfJwtConfig, JwtGatewayFilter |
| 🔒 **第三方服务纳入鉴权** | `/api/third/**`（OSS 签名/上传）挂 JWT，防匿名滥用云存储 | GatewayOfJwtConfig |
| 🤖 **AI 服务纳入鉴权** | `/api/ai/**` 挂 JWT，防匿名刷 DeepSeek/SiliconFlow 付费额度 | GatewayOfJwtConfig |
| 🗑️ **移除内部接口公网放行** | `/api/user/internal/**` 不再从网关放行（微服务间 Feign 走 Nacos 直连，不受影响） | GatewayOfJwtConfig |
| 🚫 **删除 reset-admin 后门** | 移除可被任意用户调用的管理员密码重置接口 | UserController |
| 🔑 **JWT 密钥更换** | 公开的示例密钥替换为随机强密钥（网关与 common 模块默认值保持一致，本地无需配置） | JwtUtils, gateway application.properties |
| 🔒 **默认管理员密码可配置** | `medifood.admin.default-password` 可覆盖 admin123，使用默认值时启动强警告 | AdminBootstrap |
| 📁 **OSS 上传白名单校验** | 服务端 `/upload` 接口增加扩展名白名单（图片+PDF）与 10MB 大小上限 | OssController |
| 🔒 **用户隐私脱敏** | 查看他人主页时手机号/邮箱打码（`138****5678`），生日与审核信息不返回，防遍历爬取 | UserController |
| 🧹 **清理敏感日志** | 删除打印 RefreshToken 的调试代码 | UserController, JwtGatewayFilter |
| 🧼 **前端 XSS 消毒** | 知识详情/饮食计划/AI 回复的 `v-html` 渲染统一经 DOMPurify 消毒 | KnowledgeDetail, DietPlanDetail, SmartQA, package.json |
| 🔑 **上传签名带登录态** | policy.js 请求 OSS 签名接口手动携带 Authorization（配合网关新鉴权） | frontend policy.js |

**遗留事项（部署到公网前必须处理，本轮按需求保留本地调试体验）：**
- 阿里云 OSS AccessKey 仍为明文配置，上线前需在阿里云控制台禁用旧 Key 并改用环境变量注入
- JWT 密钥仍写在配置文件中，上线前建议改为环境变量 `jwt.secret` 注入
- Redis/RabbitMQ/MySQL 密码沿用弱密码，仅限内网/本地使用

### 2026-08-24 优化（第十三轮 - 三级漏斗内容审核体系）

> 建立「敏感词实时拦截 → AI 异步审核（三态判定）→ 管理员人工复核」三级审核漏斗，
> 权威内容（知识/食谱）先审后发，高频内容（动态/评论/问答）先发后审，兼顾内容安全与社区活跃度。

**三级防线架构：**

| 层级 | 机制 | 时效 | 说明 |
|------|------|------|------|
| 第一层 | 敏感词过滤（DFA 算法） | 同步（毫秒级） | 发布瞬间本地内存匹配，命中直接拒绝并提示命中的词 |
| 第二层 | AI 异步审核（RabbitMQ） | 异步（秒级） | SiliconFlow 多模态模型三态判定：safe（放行）/ suspicious（进台账）/ violation（自动删除） |
| 第三层 | 管理员人工复核 | 人工 | 审核台处理台账记录：通过 / 删除 / 删除并警告用户 |

**双审核模式（按内容类型区分）：**

| 模式 | 适用内容 | 发布后状态 | 违规处理 |
|------|----------|------------|----------|
| 方案 A：先发后审 | 社区动态、各类评论、提问、回答 | 直接在线可见 | AI 判定违规自动删除；判定疑似进台账待人工复核 |
| 方案 B：先审后发 | 知识文章、食谱（发布者为认证医师，量少） | status=3「审核中」，对外不可见 | AI 审核通过自动转「已发布」并同步 ES；判定疑似/违规进台账，由管理员复核上架或删除 |

**新增/改造的关键组件：**

| 组件 | 位置 | 职责 |
|------|------|------|
| SensitiveWordFilter | common 模块 | DFA（Trie 树）敏感词过滤器，启动加载内置词库，静态方法 `check(text, fieldName)` 命中即抛业务异常 |
| ContentAuditEvent / Publisher | common 模块 | 审核事件模型（8 种内容类型常量）与 RabbitMQ 事件发布器，各业务模块统一接入 |
| ContentAuditListener | community 模块 | 审核事件消费者（2-5 并发线程），调用 AI 审核服务三态结果分发：安全→放行（先审后发内容自动上架），疑似→写台账，违规→自动删除并通知 |
| ContentAuditAdminService/Controller | community 模块 | 管理员审核接口：台账列表（类型/状态筛选+分页）、统计、通过（先审后发内容上架+ES 重同步）、拒绝（删除内容）、拒绝并警告（额外发送警告通知） |
| content_audit_record 表 | database/init.sql | 审核台账：内容快照（标题/正文/图片）、发布者、AI 判定类别与原因、审核模式、状态流转 |
| ContentAuditReview.vue | frontend /admin/content-audit | 管理员审核台界面：待审徽标、类型/状态筛选、内容快照与图片预览、通过/删除/删+警告操作、备注记录 |

**业务模块接入点：**

| 模块 | 接入方式 |
|------|----------|
| 知识（Knowledge） | 发布时 status=3 + 敏感词 check + 发审核事件；编辑同样触发重审 |
| 食谱（Recipe） | 同知识模块（方案 B）；编辑内容触发 AI 重新审核（方案 A 语义，内容保持在线） |
| 社区（Community） | 动态/评论发布：敏感词同步拦截 + 事件异步审核，内容直接可见（方案 A） |
| 问答（QA） | 提问与回答：敏感词同步拦截 + 事件异步审核（方案 A） |

**管理员审核台使用方法：**
1. 使用管理员账号登录（Header 用户菜单 → 「内容审核台」，路由 `/admin/content-audit`）
2. 列表默认展示「待人工审核」记录，可按内容类型（动态/食谱/知识/评论/提问/回答）与状态筛选
3. 每条记录展示内容快照（含图片预览）、AI 判定类别与原因、审核模式
4. 操作三选一：
   - **通过**：先审后发内容自动上架并同步搜索索引；先发后审内容仅结案
   - **删除**：删除内容并向发布者发送违规删除通知
   - **删+警告**：删除内容 + 违规删除通知 + 警告通知
5. 全部操作可填备注（仅记录在台账，不展示给用户）

**性能设计（不阻塞业务主流程）：**
- 敏感词过滤为纯本地内存计算（Trie 树），单次检查微秒级，不增加可感知延迟
- AI 审核走 RabbitMQ 异步事件，消费者并发 2-5 线程，审核失败自动降级为 suspicious 进台账，不影响发布接口响应时间
- AI 审核服务异常/超时不阻塞内容发布，最坏情况为内容进入人工复核队列
- 先审后发内容的 ES 同步事件自动跳过非「已发布」状态，审核通过后再触发重同步，避免未审内容进搜索

### 2026-08-24 修复（第十四轮 - ReAct Agent 线程安全）

> 修复 ReActAgentService 的并发串号缺陷：请求级状态（用户ID/经纬度）误存为单例 Bean 的实例字段，
> 并发请求互相覆盖，可能导致 A 用户的回答中混入 B 用户的体质/位置信息（医疗场景不可接受）。

**问题根因**：`ReActAgentService` 是 Spring 单例，`userId/userLat/userLng` 作为实例字段在 `react()` 入口写入、
在 `executeTool()`（间隔多次 LLM 调用，窗口可达数秒）读取，并发下后写入者覆盖先写入者。

**修复方案**：新增线程私有的 `AgentContext` 局部对象，随 `react() → executeTool()` 调用链传递：
- 删除 3 个实例字段，`react()` 入口创建局部 `AgentContext`（存于线程栈，天然隔离）
- `executeTool()` 增加 `ctx` 参数，`user_profile`/`weather_query` 工具改从 ctx 读取注入值
- 外部接口签名不变（`react`/`reactStream` 参数列表未动），调用方零改动

**经验教训**：Spring 单例 Bean 中，凡是"每次请求一个值"的状态，必须走方法参数/局部变量传递，
禁止存实例字段。此类 bug 测试环境（单用户）几乎无法复现，仅在高并发生产环境偶发，排查成本极高。

*"用最简单的代码，做最懂你的健康管家。" —— MediFood Circle 团队
