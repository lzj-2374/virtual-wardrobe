-- =============================================================
-- 虚拟衣柜 AI 穿搭系统 · 建表脚本（第一阶段：6 张业务表）
-- 库：virtual_wardrobe    字符集：utf8mb4    引擎：InnoDB
-- 约定：
--   1. 主键统一 BIGINT AUTO_INCREMENT（不用雪花 ID，避免超出 JS 数字精度）
--   2. 不建物理外键，关联约束由代码保证（批量删除时不会被外键卡住）
--   3. 逻辑删除只用在 user / clothing / outfit 三张"主体表"上
--      clothing_tag / outfit_item 用"全删全插"，物理删除（原因见各表注释）
--   4. 时间字段由数据库默认值兜底，Java 端再配 MetaObjectHandler 自动填充
-- =============================================================

USE virtual_wardrobe;

-- 可选：重跑脚本时先清空，第一次执行请保持注释
-- DROP TABLE IF EXISTS outfit_item, outfit, clothing_tag, clothing, clothing_category, `user`;

-- -------------------------------------------------------------
-- 1. 用户表
-- -------------------------------------------------------------
CREATE TABLE `user` (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL                COMMENT '登录名',
    password    VARCHAR(100) NOT NULL                COMMENT 'BCrypt 加密后的密码（固定 60 位，别用 64）',
    nickname    VARCHAR(32)  DEFAULT NULL            COMMENT '昵称',
    avatar      VARCHAR(255) DEFAULT NULL            COMMENT '头像 URL',
    role        TINYINT      NOT NULL DEFAULT 0      COMMENT '0 普通用户 / 1 管理员',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '1 正常 / 0 停用',
    gender      TINYINT      DEFAULT 0               COMMENT '0 未知 / 1 男 / 2 女',
    height      SMALLINT     DEFAULT NULL            COMMENT '身高 cm',
    weight      DECIMAL(5,1) DEFAULT NULL            COMMENT '体重 kg',
    size        VARCHAR(8)   DEFAULT NULL            COMMENT '常用尺码，如 M / L / XL',
    style_pref  VARCHAR(64)  DEFAULT NULL            COMMENT '风格偏好，逗号分隔，如 休闲,通勤',
    city        VARCHAR(32)  DEFAULT NULL            COMMENT '城市',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';
-- 注：username 唯一索引 + 逻辑删除的组合有个已知副作用——
-- 账号被删后同名无法再注册。毕设阶段不处理；真要做就在注册时判断
-- "存在已删除的同名账号则复活它"。

-- -------------------------------------------------------------
-- 2. 品类字典表
--    parent_id = 0 表示一级品类；level=1 一级 / 2 二级
-- -------------------------------------------------------------
CREATE TABLE clothing_category (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT      NOT NULL DEFAULT 0      COMMENT '父品类 id，0 表示一级',
    name        VARCHAR(32) NOT NULL                COMMENT '品类名称',
    level       TINYINT     NOT NULL DEFAULT 1      COMMENT '1 一级 / 2 二级',
    sort        INT         NOT NULL DEFAULT 0      COMMENT '排序，越小越靠前',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='品类字典表';

-- -------------------------------------------------------------
-- 3. 衣物表
--    season 用位标识存多选：1 春 / 2 夏 / 4 秋 / 8 冬，相加即组合，15 = 四季
--    风格、场合是多值，不在这里存，走 clothing_tag
-- -------------------------------------------------------------
CREATE TABLE clothing (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT       NOT NULL                COMMENT '所属用户 id',
    name         VARCHAR(64)  NOT NULL                COMMENT '衣物名称',
    category     TINYINT      NOT NULL                COMMENT '一级品类：1上衣 2裤子 3外套 4套装 5鞋包配饰',
    sub_category VARCHAR(32)  DEFAULT NULL            COMMENT '子品类，如 T恤 / 牛仔裤',
    image_url    VARCHAR(255) DEFAULT NULL            COMMENT '主图地址（OSS 或本地）',
    color        VARCHAR(16)  DEFAULT NULL            COMMENT '主色，如 白 / 黑 / 蓝',
    season       TINYINT      NOT NULL DEFAULT 15     COMMENT '季节位标识：1春 2夏 4秋 8冬，15 四季',
    thickness    TINYINT      NOT NULL DEFAULT 2      COMMENT '厚薄：1 薄 / 2 适中 / 3 厚',
    status       TINYINT      NOT NULL DEFAULT 1      COMMENT '1 正常 / 0 已归档',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_user_category (user_id, category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='衣物表';

-- -------------------------------------------------------------
-- 4. 衣物标签关联表（多值属性）
--    tag_type：1 风格 / 2 场合 / 3 其他
--    这一张不做逻辑删除：编辑衣物时走"先按 clothing_id 全删、再整批插入"，
--    否则逻辑删除的旧标签会和唯一索引/查询条件打架
-- -------------------------------------------------------------
CREATE TABLE clothing_tag (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    clothing_id BIGINT      NOT NULL                COMMENT '衣物 id',
    tag_type    TINYINT     NOT NULL                COMMENT '1 风格 / 2 场合 / 3 其他',
    tag_value   VARCHAR(32) NOT NULL                COMMENT '标签值，如 休闲 / 通勤',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_clothing (clothing_id),
    KEY idx_type_value (tag_type, tag_value)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='衣物标签关联表';

-- -------------------------------------------------------------
-- 5. 搭配表
--    cover_url 取值优先级：用户上传 > AI 效果图 > 空（列表用占位图）
--    source：1 手动创建 / 2 AI 推荐沉淀
-- -------------------------------------------------------------
CREATE TABLE outfit (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL                COMMENT '所属用户 id',
    name        VARCHAR(64)  NOT NULL                COMMENT '搭配名称',
    cover_url   VARCHAR(255) DEFAULT NULL            COMMENT '封面图',
    source      TINYINT      NOT NULL DEFAULT 1      COMMENT '1 手动 / 2 AI 推荐',
    scene       VARCHAR(32)  DEFAULT NULL            COMMENT '场景：日常/通勤/约会/运动/正式',
    season      TINYINT      NOT NULL DEFAULT 15     COMMENT '季节位标识',
    description VARCHAR(255) DEFAULT NULL            COMMENT '说明或推荐理由',
    like_flag   TINYINT      NOT NULL DEFAULT 0      COMMENT '是否收藏：0 否 / 1 是',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='搭配表';

-- -------------------------------------------------------------
-- 6. 搭配-衣物关联表
--    role：1 上装 / 2 下装 / 3 外套 / 4 鞋 / 5 配饰
--    uk_outfit_role 是"一个搭配里一个角色只能一件单品"的最后一道防线
--    这一张必须物理删除：如果做逻辑删除，删掉旧角色再换一件会撞唯一索引
-- -------------------------------------------------------------
CREATE TABLE outfit_item (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    outfit_id   BIGINT   NOT NULL                COMMENT '搭配 id',
    clothing_id BIGINT   NOT NULL                COMMENT '衣物 id',
    role        TINYINT  NOT NULL                COMMENT '1 上装 2 下装 3 外套 4 鞋 5 配饰',
    sort        INT      NOT NULL DEFAULT 0      COMMENT '排序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outfit_role (outfit_id, role),
    KEY idx_clothing (clothing_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='搭配-衣物关联表';

-- -------------------------------------------------------------
-- 7. 文件记录表
--    biz_type：1 衣物图 / 2 AI 效果图 / 3 头像 / 4 搭配封面
--    存的是 OSS 上的完整 url，业务表（clothing.image_url 等）只引用 url，不重复存文件信息
--    这张表不做逻辑删除：文件删了就是删了，留一条逻辑删除的空壳没有意义
-- -------------------------------------------------------------
CREATE TABLE file (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL                COMMENT '上传人 id',
    file_name   VARCHAR(255) NOT NULL                COMMENT '原始文件名',
    url         VARCHAR(500) NOT NULL                COMMENT 'OSS 访问地址',
    thumb_url   VARCHAR(500) DEFAULT NULL            COMMENT '缩略图地址，暂与 url 相同',
    oss_key     VARCHAR(255) NOT NULL                COMMENT 'OSS 对象 key，删除文件时用',
    biz_type    TINYINT      NOT NULL DEFAULT 1      COMMENT '1 衣物图 2 效果图 3 头像 4 搭配封面',
    file_size   BIGINT       NOT NULL DEFAULT 0      COMMENT '文件字节数',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_biz (biz_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='文件记录表';

-- -------------------------------------------------------------
-- 品类字典初始数据（一级品类，id 与前端写死的 1~5 对齐）
-- -------------------------------------------------------------
INSERT INTO clothing_category (id, parent_id, name, level, sort) VALUES
    (1, 0, '上衣', 1, 1),
    (2, 0, '裤子', 1, 2),
    (3, 0, '外套', 1, 3),
    (4, 0, '套装', 1, 4),
    (5, 0, '鞋包配饰', 1, 5);

-- 二级品类示例（子品类先让用户自己填，这里是常用枚举，可按需补充）
INSERT INTO clothing_category (parent_id, name, level, sort) VALUES
    (1, 'T恤', 2, 1), (1, '衬衫', 2, 2), (1, '卫衣', 2, 3), (1, '针织衫', 2, 4),
    (2, '牛仔裤', 2, 1), (2, '休闲裤', 2, 2), (2, '短裤', 2, 3),
    (3, '夹克', 2, 1), (3, '风衣', 2, 2), (3, '大衣', 2, 3), (3, '羽绒服', 2, 4),
    (4, '连衣裙', 2, 1), (4, '西装套装', 2, 2),
    (5, '运动鞋', 2, 1), (5, '皮鞋', 2, 2), (5, '包', 2, 3);

-- -------------------------------------------------------------
-- 建完自查（在 console 里单独执行）
-- -------------------------------------------------------------
-- SHOW TABLES;                                  -- 应正好 7 张
-- DESC clothing;                                -- season 是否 tinyint、有没有 deleted
-- SHOW INDEX FROM outfit_item;                  -- 必须有 uk_outfit_role
-- SHOW INDEX FROM `user`;                       -- username 必须 UNIQUE
-- SELECT * FROM clothing_category;              -- 应有 5 条一级 + 16 条二级
