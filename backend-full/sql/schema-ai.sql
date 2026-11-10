-- =============================================================
-- 虚拟衣柜 AI 穿搭系统 · AI 模块建表脚本
-- 库：virtual_wardrobe    字符集：utf8mb4    引擎：InnoDB
--
-- 前置：先执行过 develop/db/init.sql（7 张基础表 + 品类字典）
-- 本脚本只新增 AI 相关的 5 张表，不动已有表结构，可重复执行前先看第 0 步。
--
-- 与《开发文档》5.1 的差异（有意为之，答辩时需要能解释）：
--   文档里只列了 ai_recommendation，把推荐单品记在「推荐时立刻创建的 outfit 草稿」上。
--   那样做的话，用户还没点「放入我的搭配」，草稿就会出现在 GET /outfit/page 列表里，
--   只能再加一个 draft 标志位去过滤，等于把 AI 的中间态污染进主体表。
--   所以这里单独建 ai_recommendation_item 存推荐单品，
--   真正的 outfit / outfit_item 只在用户点「放入我的搭配」那一刻才创建。
-- =============================================================

USE virtual_wardrobe;

-- -------------------------------------------------------------
-- 0. 重跑开关（第一次执行保持注释）
-- -------------------------------------------------------------
-- DROP TABLE IF EXISTS ai_image_task, ai_recommendation_item, ai_recommendation, ai_message, ai_conversation;

-- -------------------------------------------------------------
-- 1. AI 会话表
--    一次「打开 AI 助手 → 连续问几句」对应一条记录。
--    title 取首句用户发言的前 32 个字，用于会话列表展示。
--    这张表不做逻辑删除：会话删了就是删了，留空壳没有意义
-- -------------------------------------------------------------
CREATE TABLE ai_conversation (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT      NOT NULL                COMMENT '所属用户 id',
    title       VARCHAR(64) DEFAULT NULL            COMMENT '会话标题（首句摘要）',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_user_time (user_id, update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 会话表';

-- -------------------------------------------------------------
-- 2. AI 消息表
--    role：1 用户 / 2 AI
--    recommendation_id 只在 AI 消息上有值，指向这一轮生成的推荐结果
--    「一轮对话产多套推荐」时，这里存第一条推荐的 id 做锚点，其余靠 conversation_id 反查
-- -------------------------------------------------------------
CREATE TABLE ai_message (
    id                BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id   BIGINT   NOT NULL                COMMENT '所属会话 id',
    role              TINYINT  NOT NULL                COMMENT '1 用户 / 2 AI',
    content           TEXT                             COMMENT '消息正文',
    recommendation_id BIGINT   DEFAULT NULL            COMMENT '关联的推荐结果 id',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_conversation (conversation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 消息表';

-- -------------------------------------------------------------
-- 3. AI 推荐结果表
--    saved：0 未放入 / 1 已放入我的搭配
--    image_status：0 未生成 / 1 生成中 / 2 成功 / 3 失败（与 ai_image_task.status 同一套取值）
--    outfit_id：用户点「放入我的搭配」后创建的那条 outfit 的 id，同时也是幂等的依据
--    deleted：搭配被删时把这条推荐标脏，让 saved 回到 false，用户可以重新放入
-- -------------------------------------------------------------
CREATE TABLE ai_recommendation (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT       NOT NULL                COMMENT '所属用户 id',
    conversation_id BIGINT       DEFAULT NULL            COMMENT '所属会话 id',
    outfit_id       BIGINT       DEFAULT NULL            COMMENT '已转正的搭配 id，空表示还没放入',
    name            VARCHAR(64)  DEFAULT NULL            COMMENT '推荐名称',
    reason          VARCHAR(500) DEFAULT NULL            COMMENT '推荐理由',
    color_note      VARCHAR(64)  DEFAULT NULL            COMMENT '配色说明，如 主色：白 / 点缀：米',
    scene           VARCHAR(32)  DEFAULT NULL            COMMENT '场景：日常/通勤/约会/运动/正式',
    season          TINYINT      NOT NULL DEFAULT 15     COMMENT '季节位标识',
    image_url       VARCHAR(500) DEFAULT NULL            COMMENT '效果图地址',
    image_status    TINYINT      NOT NULL DEFAULT 0      COMMENT '0未生成 1生成中 2成功 3失败',
    saved           TINYINT      NOT NULL DEFAULT 0      COMMENT '0未放入 1已放入我的搭配',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '0 正常 / 1 已失效',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_conversation (conversation_id),
    KEY idx_outfit (outfit_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 推荐结果表';

-- -------------------------------------------------------------
-- 4. AI 推荐单品表
--    role：1 上装 / 2 下装 / 3 外套 / 4 鞋 / 5 配饰（和 outfit_item 一致）
--    这张表只在「推荐刚生成」时有意义；用户点「放入我的搭配」后，
--    数据会被搬进 outfit_item，这里只作为历史留档保留
-- -------------------------------------------------------------
CREATE TABLE ai_recommendation_item (
    id              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    recommendation_id BIGINT NOT NULL                COMMENT '推荐结果 id',
    clothing_id     BIGINT   NOT NULL                COMMENT '衣物 id，必须是该用户真实存在的衣物',
    role            TINYINT  NOT NULL                COMMENT '1上装 2下装 3外套 4鞋 5配饰',
    sort            INT      NOT NULL DEFAULT 0      COMMENT '排序',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rec_role (recommendation_id, role),
    KEY idx_rec (recommendation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 推荐单品表';

-- -------------------------------------------------------------
-- 5. 生图任务表
--    task_id 是对外暴露的任务号（前端拿去轮询），不直接暴露自增主键，
--    否则别人改个数字就能查到别人的任务
--    retry_count 记录消费者重试次数，超过阈值直接置失败，不再无限重投
-- -------------------------------------------------------------
CREATE TABLE ai_image_task (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id           VARCHAR(64)   NOT NULL                COMMENT '对外任务号',
    user_id           BIGINT        NOT NULL                COMMENT '所属用户 id',
    recommendation_id BIGINT        NOT NULL                COMMENT '关联推荐 id',
    prompt            VARCHAR(1000) DEFAULT NULL            COMMENT '生图提示词',
    image_url         VARCHAR(500)  DEFAULT NULL            COMMENT '结果图地址',
    status            TINYINT       NOT NULL DEFAULT 0      COMMENT '0未生成 1生成中 2成功 3失败',
    retry_count       INT           NOT NULL DEFAULT 0      COMMENT '已重试次数',
    error_msg         VARCHAR(255)  DEFAULT NULL            COMMENT '失败原因，便于前端和日志排查',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_user (user_id),
    KEY idx_recommendation (recommendation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI 生图任务表';

-- -------------------------------------------------------------
-- 建完自查
-- -------------------------------------------------------------
-- SHOW TABLES LIKE 'ai_%';              -- 应正好 5 张
-- DESC ai_recommendation;               -- 确认 image_status / saved / deleted 都在
-- SHOW INDEX FROM ai_image_task;        -- 必须有 uk_task_id
-- 共 12 张表：7 张业务表 + 5 张 AI 表
