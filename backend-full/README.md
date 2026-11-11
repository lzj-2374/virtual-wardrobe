# 虚拟衣柜 AI 穿搭系统 · 后端（完成版）

Spring Boot 3.5 + MyBatis-Plus + MySQL 8 + Redis + RabbitMQ + 阿里云 OSS。

这份是 `develop/backend` 的**补全版**：原有 5 个模块（用户 / 衣物 / 搭配 / 文件 / 统计 / 管理端）
保持不变，补齐了缺失的 **AI 模块、异步生图链路、管理端数据导出、衣物 AI 识别**，
并修掉了几个影响跑通的问题（详见文末「相对 backend 的改动」）。

---

## 一、能跑起来的最短路径

### 1. 依赖服务

| 服务 | 默认地址 | 说明 |
| --- | --- | --- |
| MySQL 8 | `localhost:3306` | 库名 `virtual_wardrobe`，账号密码见 yml |
| Redis | `192.168.150.130:6379` | 存 token 白名单，**登录依赖它，不启动会全部 401** |
| RabbitMQ | `localhost:5672` | 管理台 `http://localhost:15672`，guest/guest |
| 阿里云 OSS | 公网 | 图片存储，需要能出网 |

Redis 和 RabbitMQ 的地址都可以用环境变量覆盖，不用改文件：

```bash
REDIS_HOST=127.0.0.1 RABBITMQ_HOST=127.0.0.1 mvn spring-boot:run
```

### 2. 建表

```bash
# 基础 7 张表（如果库里还没有）
mysql -u root -p < ../db/init.sql

# AI 模块 5 张表（本仓库自带）
mysql -u root -p < sql/schema-ai.sql
```

建完 `SHOW TABLES LIKE 'ai_%'` 应该看到 5 张：
`ai_conversation`、`ai_message`、`ai_recommendation`、`ai_recommendation_item`、`ai_image_task`。

### 3. 启动

```bash
mvn spring-boot:run
# 接口基址 http://localhost:8080/api
# 文档 http://localhost:8080/api/doc.html
```

> 注意 `server.servlet.context-path` 是 `/api`，所有接口都带这个前缀。

---

## 二、AI 能力怎么配

两组 key **都可以留空**。留空不是"功能不可用"，而是自动降级——答辩现场断网、
额度用完、key 还没申请下来，系统照常跑，只是"聪明程度"下降。

| 能力 | 配了 key | 没配 key（默认） |
| --- | --- | --- |
| 穿搭推荐 | DeepSeek 读懂自然语言 | 内置规则引擎：按季节/厚薄/标签/中性色打分，从上装×下装里组合 |
| 生图 | 通义万相「指令编辑」把平铺图改成模特上身图 | 服务端 Java2D 把真实单品图合成一张搭配卡片 |
| 衣物识别 | qwen-vl 直接读图 | 文件名关键词 + 图片主色提取（真实计算像素） |

### 填 key 的方式（写进 `config/secrets.yml`，别再粘环境变量了）

工程根目录有个 `config/secrets.yml`，`application.yml` 通过
`spring.config.import: optional:file:./config/secrets.yml` 自动读它。
这个文件已经被 `.gitignore` 排除，不会进仓库；没有它也能启动，只是 AI 能力降级。

```yaml
aliyun:
  oss:
    access-key-id: LTAIxxxxxxxx
    access-key-secret: xxxxxxxxxxxxxxxx

ai:
  llm:
    api-key: sk-xxxxxxxx          # DeepSeek
  image:
    api-key: sk-xxxxxxxx          # DashScope，生图 + 衣物识别共用一把
```

为什么不用环境变量了：手往 IDEA 的运行配置里粘密钥，粘错一位报的是
`SignatureDoesNotMatch`，从报错里完全看不出是配置问题，排查成本极高。
写进文件只需要小心一次，而且复制粘贴不容易错。

**如果之前在 IDEA 里配过 `OSS_AK` / `OSS_SK` / `DEEPSEEK_API_KEY` / `DASHSCOPE_API_KEY`，
请把那些变量删掉**，别让两处配置打架。启动日志里会有这一行，可以用它确认实际用的是哪把钥匙：

```
OSS 客户端初始化：endpoint=oss-cn-xxxxxxxx.aliyuncs.com，bucket=your-bucket，AK=LTAI****（24 位），SK 长度 30
```

AK 长度 24、SK 长度 30 是阿里云标准长度，对不上就是粘错了。

`ai.llm.provider` / `ai.image.provider` 三个取值：
- `auto`（默认）：有 key 走模型，没 key 走降级
- `deepseek` / `wanx`：强制走模型，失败时**仍然降级**，但日志打 ERROR
- `rule` / `composite`：强制走本地实现，一个外部请求都不发（离线演示、省额度）

### 生图流程为什么是"先合成再改"

万相的 image2image 只接受一张底图，没法直接喂"模特图 + 一堆衣服图"。
所以先把这套搭配在服务端拼成一张平铺图，再把它当底图，
指令写"把图中的衣服穿在模特身上，保留款式和颜色"。

这样绕开了一张底图的限制，也保住了项目"推荐基于真实衣物"的卖点——
模型是照着真实单品改的，不是凭空画的。万相失败时直接返回那张平铺图，
用户不会空手而归。

### 对话上下文是怎么算的

多轮对话要做两件不同的事，别混在一起：

1. **意图继承**（`OutfitIntent.parse(message, history)`）——
   用户问完"今天 15 度穿啥"，接着问"那正式点的呢"，第二句自己读不出温度和场合。
   解析时把最近几轮原文一起喂进去，读不出来就去上文找。
   顺带修了个老问题：**以前根本不解析温度数字**，"今天 25 度"里没有季节词，
   只能按月份算（11 月 = 秋），于是顶着 25 度推风衣。现在温度会映射成季节位：
   ≤5℃ 冬 / 6~14℃ 秋冬 / 15~22℃ 春秋 / 23~28℃ 春夏 / ≥29℃ 夏。
2. **上文进提示词**（`DeepSeekOutfitAdvisor`）——让模型别重复推荐上一轮给过的搭配，
   也能听懂"再换一套""不要外套"这类话。

**上下文满了怎么办：滑动窗口，只截断喂给模型的部分，库里一条都不删。**
窗口大小是 `AiServiceImpl.CONTEXT_WINDOW = 6`（一问一答算两条，即最近 3 轮）。
没有做摘要压缩——那要再调一次模型，成本和延迟都翻倍，毕设体量不划算。
聊天记录是另一件事：`ai_message` 全量保留，前端「历史」随时能翻。

### 单次请求的 token 预算

实测口径（system prompt 固定 894 字 ≈ 432 token，每件衣服约 54 字 ≈ 27 token）：

| 场景 | 输入 | 输入+输出上限 |
| --- | --- | --- |
| 衣柜 11 件 + 4 条上文 | 818 | 2018 |
| 衣柜 50 件（送模型的上限） | 1772 | **2972** |

所以对外可以承诺：**单次推荐请求稳定在 3000 token 以内**（输入约 1800 + 输出上限 1200）。

这条承诺靠 `DeepSeekOutfitAdvisor.MAX_PROMPT_ITEMS = 50` 兜住。不设这个上限时，
提示词长度跟衣柜条数线性相关：200 件就是 5441 输入 token，800 件逼近 2 万。
超限时的截断顺序是 **用户指定的 → 当季能穿的 → 其余补位**，
并且送模型的清单和校验 id 用的清单必须是同一份，
否则模型可能返回一个被截掉的衣服 id，校验能过、落库后详情页指向一件它没见过的衣服。

> 注意：`season` 是位掩码，组合值是正常现象（5 = 春秋、12 = 秋冬、6 = 春夏）。
> 判断季节时**必须用位与** `(season & WINTER) != 0`，
> 写成 `season == WINTER` 在组合值上永远不成立。规则引擎里原来有 3 处是 `==`，
> 加温度解析时一并改掉了。

---

## 三、新增接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/ai/chat` | 发起对话，返回 1~3 套推荐 |
| GET | `/api/ai/conversation/page` | 会话分页 |
| GET | `/api/ai/conversation/{id}/messages` | 会话消息分页 |
| POST | `/api/ai/image/generate` | 提交生图任务，立即返回 taskId |
| GET | `/api/ai/image/status/{taskId}` | 轮询生图状态（0/1/2/3） |
| GET | `/api/ai/image/history` | 历史效果图，只返回真的出过图的推荐，按最近更新倒序 |
| POST | `/api/ai/recommendation/{id}/to-outfit` | 推荐放入我的搭配（幂等） |
| GET | `/api/admin/export/{type}` | 数据导出，type = `users` / `category-stats` / `scene-stats` |
| POST | `/api/clothing/recognize` | 衣物属性识别，返回建议值 |

完整字段见 `API文档.md`。

---

## 四、异步生图链路

```
POST /ai/image/generate
   │  建 ai_image_task(status=1)  →  改 ai_recommendation.image_status=1
   ▼
wardrobe.ai.exchange ──routingKey=ai.image.generate──▶ wardrobe.ai.image.queue
   │
   ▼  消费者（concurrency=1，prefetch=1，单条 10~30s，刻意限流）
   合成平铺图 → [有 key] 万相改图 → 传 OSS
   │
   ├─ 成功：task.status=2 + 回写 recommendation.image_url / outfit.cover_url
   └─ 失败：重试 3 次 → task.status=3 + recommendation.image_status=3
                      → 消息 reject 不重投 → wardrobe.ai.image.dead
```

**三层防丢**，缺一层都会出问题：

1. **幂等**：消费前查 `status==2` 直接返回；回写用 `UPDATE ... WHERE status<>2`，
   重复消费影响行数为 0，不会覆盖已生成的图
2. **终态**：最后一次重试失败必须主动置 `status=3`，否则前端永远转圈
3. **不循环**：`default-requeue-rejected=false` + `retry.max-attempts=3`，
   重试用尽后进死信队列，而不是无限重放把生图额度刷光

生产者侧额外接了两个回调：`ConfirmCallback`（到没到交换机）和
`ReturnsCallback`（到了交换机有没有队列接）。只接前者的话，路由键打错一个字消息就静默消失了。

---

## 五、相对 `develop/backend` 的改动

### 新增

```
config/AiProperties.java           AI 配置（key / 模型 / 开关）
config/RabbitConfig.java           交换机 + 生图队列 + 死信链路 + JSON 序列化
ai/OutfitIntent.java               从「明天面试穿什么」解析场景与季节
ai/OutfitDraft.java                推荐草稿
ai/OutfitAdvisor.java              推荐引擎接口
ai/DeepSeekOutfitAdvisor.java      DeepSeek 实现
ai/RuleBasedOutfitAdvisor.java     规则引擎实现
ai/OutfitAdvisorRouter.java        运行时二选一 + 失败降级
ai/ClothingRecognizer.java         衣物识别（视觉模型 / 本地启发式）
ai/image/OutfitImageComposer.java  Java2D 合成搭配卡片
ai/image/DashScopeImageEditor.java 万相指令编辑客户端
ai/image/OssImageStorage.java      效果图上传 OSS
ai/image/OutfitImageService.java   生图编排
mq/AiImageMessage.java             队列消息体
mq/AiImageProducer.java            生产者 + Confirm/Return 回调
mq/AiImageConsumer.java            消费者（幂等 + 终态 + 回写）
entity/Ai*.java                    AI 模块实体与 DTO/VO
mapper/Ai*Mapper.java              4 个 Mapper
service/AiService.java + impl      编排层
controller/AiController.java       6 个端点
sql/schema-ai.sql                  AI 模块建表脚本
```

### 修改

| 文件 | 改了什么 | 为什么 |
| --- | --- | --- |
| `application.yml` | MQ 地址由 `192.168.150.130` 改成 `localhost`（可用环境变量覆盖） | 原值是 Redis 那台虚拟机，MQ 实际跑在本机 Docker，连不上 |
| `application.yml` | Redis 地址/host 改为 `${REDIS_HOST:...}`，加 `timeout: 2000ms` | 换机器不用改文件；Redis 挂了不要卡 60 秒 |
| `application.yml` | 新增 `ai:` 段 | 两组 key、模型名、超时、生图尺寸 |
| `ResultCode` | 新增 40xx 段（AI 模块错误码） | 原注释里规划了 4xxx 但没写 |
| `StatisticsService(+Impl)` | 新增 `exportRows(type)` | 补 `GET /admin/export/{type}` |
| `AdminController` | 新增 `/export/{type}` | 同上 |
| `OutfitMapper` | 新增 `countByScene()` | 场景分布导出 |
| `ClothingService(+Impl)` | 新增 `recognize()`；删除衣物时联带失效相关 AI 推荐 | 补 `/clothing/recognize`；避免点到缺件的推荐 |
| `OutfitServiceImpl` | 删除搭配时解除推荐绑定 | 否则 AI 卡片显示"已放入"但点进去找不到 |

### 表结构

原有 7 张表一张没动。新增 5 张 AI 表，其中一点和《开发文档》不同，**答辩要能解释**：

> 文档里说"推荐时先建一条 outfit 草稿（source=2），把单品挂在 outfit_item 上"。
> 那样做的话，用户还没点「放入我的搭配」，搭配列表里就先多了几套草稿。
> 所以这里单独建 `ai_recommendation_item` 存推荐单品，
> 真正的 `outfit` / `outfit_item` 只在用户点「放入我的搭配」那一刻才创建。

---

## 六、已知限制

1. **`application.yml` 里没有明文密钥**。密钥统一放工程根目录的 `config/secrets.yml`
   （已在 `.gitignore` 里），由 `spring.config.import` 自动加载。
   文件不存在也能启动，只是传图/生图/对话推荐会降级到本地实现。
2. **万相图像编辑用 `wan2.5-i2i-preview`，已用真实 key 实测通过**，能稳定出"模特上身图"。
   下面这些是踩过的坑，改之前先看完：
   - 别用 `wanx2.1-imageedit`。它虽在（Legacy 分类、仅北京地域），但实测只会把衣服重画成
     电商主图，加不进人，还会自己编品牌文字。
   - 换模型不是改个名字：wan2.5+ 的 `input` 是 `{prompt, images[]}`，
     wanx2.1 是 `{function, prompt, base_image_url}`，结构不通用。
   - **提示词里必须写"不要出现文字"**。拼图上有中文标签，不清掉会被画进成图。
   - 视觉识别用的是 `qwen3-vl-plus`。老名字 `qwen-vl-max` 已下线，传过去返回
     `model_not_found`，然后被上层吞掉静默降级到本地启发式，表现是"配了 key 跟没配一样"。
3. **同一个库上不要同时跑两个后端实例**。`wardrobe.ai.image.queue` 是共享队列，
   两个实例会抢同一条消息，表现为生图"时好时坏"。而且 JWT 密钥改过之后，
   两边 token 互不认，看着更像玄学问题。调试时先把不用的那个停掉。
4. **单次导出上限 5000 行**。分页导出更正规，毕设体量下加个上限更划算。
5. **衣物识别现在会传图了**：前端 `ClothingAdd.vue` 在识别前先上传拿 OSS 地址，
   再调 `{ fileName, imageUrl }`，所以颜色是从真实像素算出来的。
   但子品类、季节在没配 DashScope key 时仍然依赖文件名关键词，名字是一串哈希就只能填"待补充"。
6. **生图按次收费**，`concurrency=1` / `prefetch=1` 是有意限流，不是配置遗漏。
7. **模特性别按「这轮对话里说过的 &gt; 个人资料里的」取**，两个都没有才交给模型自己挑。
   注意 `user.gender` 默认是 0（未设置），这种账号成图的性别依然是随机的——
   先在「个人中心」把性别设上，提示词里才会写死"男性模特"或"女性模特"。
8. **前端轮询的上限必须比后端宽**。实测一次生图要 **60 秒**（任务表 `create_time` →
   `update_time` 正好差 60s），后端等万相的上限是 120s，加下载和传 OSS 最坏 150s。
   `AiAssistant.vue` 原来把轮询上限写成 30 秒，时间一到就 `clearInterval`，
   于是图早就落进 OSS、库里 `image_status=2`，页面却一直停在骨架屏——
   表现就是"生成成功了但页面显示不出"。现在给到 240 秒，超时不静默收工，
   会保留 `taskId` 把按钮变成「再查一次」，避免重复下单花两次钱。
