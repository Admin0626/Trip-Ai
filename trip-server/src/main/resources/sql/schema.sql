-- =====================================================================
-- 数据库：基于 LLM 的旅行行程推荐系统
-- 版本：v1.2 ｜ 共 29 张表（26 张设计文档 + 评论点赞 + 本地知识分片/倒排词表；私人检索会话字段）
-- 依据：docs/05-数据库设计.md + docs/dev/建表对照清单.md（B1~B9 已裁定）
-- 建表约定：utf8mb4 / InnoDB / bigint unsigned 自增主键 / 业务侧维护计数字段
-- 执行：mysql -u root -p < schema.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS trip_llm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE trip_llm;

-- ---------------------------------------------------------------------
-- 一、用户组（3 张）
-- ---------------------------------------------------------------------

-- 1. sys_user 用户（8 张逻辑删除表之一）
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username         varchar(50)  NOT NULL COMMENT '登录名（唯一，忽略大小写）',
    password         varchar(100) NOT NULL COMMENT 'BCrypt 加密密码',
    nickname         varchar(50)  NOT NULL DEFAULT '' COMMENT '昵称',
    avatar           varchar(255) NOT NULL DEFAULT '' COMMENT '头像URL',
    phone            varchar(20)  DEFAULT NULL COMMENT '手机号（可空，唯一）',
    email            varchar(100) DEFAULT NULL COMMENT '邮箱（可空，唯一）',
    city             varchar(50)  NOT NULL DEFAULT '' COMMENT '所在城市',
    role             varchar(20)  NOT NULL DEFAULT 'USER' COMMENT 'ADMIN / USER',
    status           tinyint      NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 禁用',
    last_login_time  datetime     DEFAULT NULL COMMENT '最后登录时间',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone),
    UNIQUE KEY uk_email (email),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户';

-- 2. user_preference 用户旅行偏好（推荐冷启动唯一数据源）
DROP TABLE IF EXISTS user_preference;
CREATE TABLE user_preference (
    id               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          bigint unsigned NOT NULL COMMENT '用户ID',
    preference_tags  varchar(255) NOT NULL DEFAULT '' COMMENT '偏好标签，逗号分隔，≤8 个',
    avoid_tags       varchar(255) NOT NULL DEFAULT '' COMMENT '避雷标签，逗号分隔，≤5 个',
    budget_min       decimal(10,2) NOT NULL DEFAULT 0 COMMENT '预算下限',
    budget_max       decimal(10,2) NOT NULL DEFAULT 0 COMMENT '预算上限（≥下限）',
    preferred_days   int          DEFAULT NULL COMMENT '常用出行天数',
    companions       varchar(20)  NOT NULL DEFAULT '' COMMENT '同行人 single/couple/family/group',
    pace             varchar(20)  NOT NULL DEFAULT '' COMMENT '节奏 relaxed/normal/intense',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户旅行偏好';

-- 3. user_behavior 行为埋点（混合推荐第四路召回数据源，高写入，无 update_time）
DROP TABLE IF EXISTS user_behavior;
CREATE TABLE user_behavior (
    id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       bigint unsigned NOT NULL COMMENT '用户ID',
    behavior_type varchar(20)  NOT NULL COMMENT 'VIEW/LIKE/FAVORITE/BOOK/SEARCH',
    target_type   varchar(20)  NOT NULL COMMENT 'ROUTE / DESTINATION',
    target_id     bigint unsigned NOT NULL DEFAULT 0 COMMENT '目标ID',
    keyword       varchar(100) NOT NULL DEFAULT '' COMMENT '搜索关键词（SEARCH 时用）',
    duration      int unsigned NOT NULL DEFAULT 0 COMMENT '停留时长（秒）',
    create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户行为埋点';

-- ---------------------------------------------------------------------
-- 二、内容组（5 张）
-- ---------------------------------------------------------------------

-- 4. destination 目的地（8 张逻辑删除表之一）
DROP TABLE IF EXISTS destination;
CREATE TABLE destination (
    id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '目的地ID',
    name        varchar(100) NOT NULL COMMENT '目的地名称（同省内唯一）',
    province    varchar(50)  NOT NULL DEFAULT '' COMMENT '省份',
    city        varchar(50)  NOT NULL DEFAULT '' COMMENT '城市',
    longitude   decimal(10,6) NOT NULL COMMENT '经度（73~136）',
    latitude    decimal(10,6) NOT NULL COMMENT '纬度（3~54）',
    cover_img   varchar(255) NOT NULL COMMENT '封面图（必填）',
    intro       text                  COMMENT '简介',
    tags        varchar(255) NOT NULL DEFAULT '' COMMENT '标签，逗号分隔，≤5 个',
    best_season varchar(50)  NOT NULL DEFAULT '' COMMENT '最佳季节',
    avg_cost    decimal(10,2) NOT NULL DEFAULT 0 COMMENT '人均花费',
    heat        int unsigned NOT NULL DEFAULT 0 COMMENT '热度值',
    route_count int unsigned NOT NULL DEFAULT 0 COMMENT '关联在架路线数（冗余）',
    status      tinyint      NOT NULL DEFAULT 1 COMMENT '1 上架 / 0 下架',
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_province_name (province, name),
    KEY idx_province (province),
    KEY idx_heat (heat),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='目的地';

-- 5. attraction 景点（行程项素材来源）
DROP TABLE IF EXISTS attraction;
CREATE TABLE attraction (
    id             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '景点ID',
    destination_id bigint unsigned NOT NULL COMMENT '所属目的地',
    name           varchar(100) NOT NULL COMMENT '景点名称',
    cover_img      varchar(255) NOT NULL DEFAULT '' COMMENT '封面图',
    intro          text                  COMMENT '简介',
    address        varchar(255) NOT NULL DEFAULT '' COMMENT '地址',
    longitude      decimal(10,6) NOT NULL DEFAULT 0 COMMENT '经度',
    latitude       decimal(10,6) NOT NULL DEFAULT 0 COMMENT '纬度',
    ticket_price   decimal(10,2) NOT NULL DEFAULT 0 COMMENT '门票参考价',
    open_time      varchar(100) NOT NULL DEFAULT '' COMMENT '开放时间',
    duration_min   int unsigned NOT NULL DEFAULT 0 COMMENT '建议游玩时长（分钟）',
    tags           varchar(255) NOT NULL DEFAULT '' COMMENT '标签，逗号分隔',
    status         tinyint      NOT NULL DEFAULT 1 COMMENT '1 上架 / 0 下架',
    create_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_destination_id (destination_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='景点';

-- 6. route 路线（8 张逻辑删除表之一）
DROP TABLE IF EXISTS route;
CREATE TABLE route (
    id               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '路线ID',
    title            varchar(200) NOT NULL COMMENT '路线标题',
    subtitle         varchar(255) NOT NULL DEFAULT '' COMMENT '副标题',
    cover_img        varchar(255) NOT NULL DEFAULT '' COMMENT '封面图',
    destination_id   bigint unsigned NOT NULL COMMENT '目的地ID',
    days             int unsigned NOT NULL DEFAULT 1 COMMENT '天数（= route_day 条数）',
    price            decimal(10,2) NOT NULL DEFAULT 0 COMMENT '参考价格（≥0）',
    difficulty       tinyint      NOT NULL DEFAULT 1 COMMENT '难度 1-5',
    tags             varchar(255) NOT NULL DEFAULT '' COMMENT '标签，逗号分隔',
    highlights       text                  COMMENT '行程亮点',
    notice           text                  COMMENT '注意事项',
    like_count       int unsigned NOT NULL DEFAULT 0 COMMENT '点赞数（冗余）',
    favorite_count   int unsigned NOT NULL DEFAULT 0 COMMENT '收藏数（冗余）',
    booking_count    int unsigned NOT NULL DEFAULT 0 COMMENT '预约数（冗余）',
    comment_count    int unsigned NOT NULL DEFAULT 0 COMMENT '评论数（冗余）',
    view_count       int unsigned NOT NULL DEFAULT 0 COMMENT '浏览量（冗余）',
    avg_score        decimal(3,2) NOT NULL DEFAULT 0 COMMENT '平均评分 0.00-5.00',
    recommend_weight decimal(5,4) NOT NULL DEFAULT 0.5000 COMMENT '人工推荐权重 0-1（运营干预）',
    is_top           tinyint      NOT NULL DEFAULT 0 COMMENT '是否置顶 1是 0否',
    quota_per_day    int unsigned NOT NULL DEFAULT 20 COMMENT '每日预约名额上限',
    status           tinyint      NOT NULL DEFAULT 0 COMMENT '1 上架 / 0 下架',
    create_by        bigint unsigned NOT NULL DEFAULT 0 COMMENT '创建管理员',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_destination_id (destination_id),
    KEY idx_status_destination (status, destination_id),
    KEY idx_is_top (is_top),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='旅游路线';

-- 7. route_day 路线行程天
DROP TABLE IF EXISTS route_day;
CREATE TABLE route_day (
    id         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    route_id   bigint unsigned NOT NULL COMMENT '路线ID',
    day_index  int unsigned NOT NULL COMMENT '第几天（从 1 起）',
    title      varchar(100) NOT NULL DEFAULT '' COMMENT '当天标题',
    summary    varchar(255) NOT NULL DEFAULT '' COMMENT '当天摘要',
    create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_day (route_id, day_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路线行程天';

-- 8. route_item 路线行程项
DROP TABLE IF EXISTS route_item;
CREATE TABLE route_item (
    id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    route_day_id  bigint unsigned NOT NULL COMMENT '行程天ID',
    sort_no       int unsigned NOT NULL COMMENT '天内排序号（唯一且连续）',
    time_point    varchar(10)  NOT NULL DEFAULT '' COMMENT '时间点，如 09:00（可选）',
    attraction_id bigint unsigned NOT NULL DEFAULT 0 COMMENT '关联景点ID（0=非景点项）',
    title         varchar(100) NOT NULL COMMENT '行程项标题',
    activity      varchar(255) NOT NULL DEFAULT '' COMMENT '活动内容',
    transport     varchar(50)  NOT NULL DEFAULT '' COMMENT '交通方式',
    hotel         varchar(100) NOT NULL DEFAULT '' COMMENT '住宿',
    meal          varchar(100) NOT NULL DEFAULT '' COMMENT '用餐',
    duration_min  int unsigned NOT NULL DEFAULT 0 COMMENT '时长（分钟）',
    cost          decimal(10,2) NOT NULL DEFAULT 0 COMMENT '费用',
    tips          varchar(255) NOT NULL DEFAULT '' COMMENT '小贴士',
    create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_day_sort (route_day_id, sort_no),
    KEY idx_route_day_id (route_day_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路线行程项';

-- ---------------------------------------------------------------------
-- 三、互动组（5 张：点赞/收藏/预约/评论/评论点赞）
-- ---------------------------------------------------------------------

-- 9. route_like 路线点赞（幂等，取消即物理删除，无 update_time）
DROP TABLE IF EXISTS route_like;
CREATE TABLE route_like (
    id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     bigint unsigned NOT NULL COMMENT '用户ID',
    route_id    bigint unsigned NOT NULL COMMENT '路线ID',
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_route (user_id, route_id),
    KEY idx_route_id (route_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路线点赞';

-- 10. route_favorite 路线收藏（幂等，取消即物理删除）
DROP TABLE IF EXISTS route_favorite;
CREATE TABLE route_favorite (
    id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     bigint unsigned NOT NULL COMMENT '用户ID',
    route_id    bigint unsigned NOT NULL COMMENT '路线ID',
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_route (user_id, route_id),
    KEY idx_user_create (user_id, create_time),
    KEY idx_route_id (route_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路线收藏';

-- 11. route_booking 预约
DROP TABLE IF EXISTS route_booking;
CREATE TABLE route_booking (
    id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    booking_no    varchar(32)  NOT NULL COMMENT '单号 BK+yyyyMMdd+3位流水',
    route_id      bigint unsigned NOT NULL COMMENT '路线ID',
    user_id       bigint unsigned NOT NULL COMMENT '用户ID',
    travel_date   date         NOT NULL COMMENT '出行日期（≥明天）',
    people_num    tinyint unsigned NOT NULL DEFAULT 1 COMMENT '人数 1-10',
    contact_name  varchar(50)  NOT NULL COMMENT '联系人',
    contact_phone varchar(20)  NOT NULL COMMENT '联系电话',
    remark        varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
    status        tinyint      NOT NULL DEFAULT 0 COMMENT '0待确认 1已确认 2已取消 3已完成',
    audit_by      bigint unsigned NOT NULL DEFAULT 0 COMMENT '审核管理员',
    audit_time    datetime     DEFAULT NULL COMMENT '审核时间',
    cancel_time   datetime     DEFAULT NULL COMMENT '取消时间',
    create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_no (booking_no),
    KEY idx_user_route_date (user_id, route_id, travel_date),
    KEY idx_route_date_status (route_id, travel_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路线预约';

-- 12. route_comment 评论（8 张逻辑删除表之一）
DROP TABLE IF EXISTS route_comment;
CREATE TABLE route_comment (
    id               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    route_id         bigint unsigned NOT NULL COMMENT '路线ID',
    user_id          bigint unsigned NOT NULL COMMENT '用户ID',
    parent_id        bigint unsigned NOT NULL DEFAULT 0 COMMENT '父评论ID（0=顶级，回复最多 2 层）',
    score            tinyint      NOT NULL DEFAULT 5 COMMENT '评分 1-5 整数',
    content          text         NOT NULL COMMENT '评论内容 5-500 字',
    images           varchar(1000) NOT NULL DEFAULT '' COMMENT '图片，逗号分隔，≤3 张',
    like_count       int unsigned NOT NULL DEFAULT 0 COMMENT '评论点赞数',
    sentiment        varchar(20)  NOT NULL DEFAULT 'unknown' COMMENT 'positive/neutral/negative/unknown',
    sentiment_score  decimal(4,3) NOT NULL DEFAULT 0 COMMENT '情感置信度',
    sentiment_retry  tinyint unsigned NOT NULL DEFAULT 0 COMMENT '情感分析失败重试次数',
    keywords         varchar(255) NOT NULL DEFAULT '' COMMENT 'AI 抽取关键词',
    status           tinyint      NOT NULL DEFAULT 1 COMMENT '1 显示 / 0 隐藏',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是（用户自删），与隐藏区分',
    PRIMARY KEY (id),
    KEY idx_route_status (route_id, status, create_time),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路线评论（含 AI 情感分析字段）';

-- 13. route_comment_like 评论点赞（阻塞项 B2 裁定：加第 27 张表，保证幂等）
DROP TABLE IF EXISTS route_comment_like;
CREATE TABLE route_comment_like (
    id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     bigint unsigned NOT NULL COMMENT '用户ID',
    comment_id  bigint unsigned NOT NULL COMMENT '评论ID',
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_comment (user_id, comment_id),
    KEY idx_comment_id (comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评论点赞';

-- ---------------------------------------------------------------------
-- 四、规划组（3 张）
-- ---------------------------------------------------------------------

-- 14. user_plan 用户自主规划（8 张逻辑删除表之一）
DROP TABLE IF EXISTS user_plan;
CREATE TABLE user_plan (
    id               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          bigint unsigned NOT NULL COMMENT '用户ID',
    title            varchar(200) NOT NULL COMMENT '规划标题',
    destination_ids  varchar(255) NOT NULL DEFAULT '' COMMENT '目的地ID，逗号分隔',
    start_date       date         NOT NULL COMMENT '出发日期（≥今天）',
    days             int unsigned NOT NULL DEFAULT 1 COMMENT '天数 1-30',
    budget           decimal(10,2) NOT NULL DEFAULT 0 COMMENT '预算（>0）',
    people_num       tinyint unsigned NOT NULL DEFAULT 1 COMMENT '人数',
    status           tinyint      NOT NULL DEFAULT 0 COMMENT '0 草稿 / 1 已保存',
    source_route_id  bigint unsigned NOT NULL DEFAULT 0 COMMENT '来源路线ID（模板创建时记录）',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户自主规划';

-- 15. user_plan_day 规划行程天（与 route_day 对称，便于模板复制）
DROP TABLE IF EXISTS user_plan_day;
CREATE TABLE user_plan_day (
    id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_plan_id bigint unsigned NOT NULL COMMENT '规划ID',
    day_index   int unsigned NOT NULL COMMENT '第几天',
    title       varchar(100) NOT NULL DEFAULT '' COMMENT '当天标题',
    summary     varchar(255) NOT NULL DEFAULT '' COMMENT '当天摘要',
    create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_day (user_plan_id, day_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='规划行程天';

-- 16. user_plan_item 规划行程项（与 route_item 对称）
DROP TABLE IF EXISTS user_plan_item;
CREATE TABLE user_plan_item (
    id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_day_id   bigint unsigned NOT NULL COMMENT '规划天ID',
    sort_no       int unsigned NOT NULL COMMENT '天内排序号（唯一且连续）',
    time_point    varchar(10)  NOT NULL DEFAULT '' COMMENT '时间点',
    attraction_id bigint unsigned NOT NULL DEFAULT 0 COMMENT '关联景点ID（0=非景点项）',
    title         varchar(100) NOT NULL COMMENT '行程项标题',
    activity      varchar(255) NOT NULL DEFAULT '' COMMENT '活动内容',
    transport     varchar(50)  NOT NULL DEFAULT '' COMMENT '交通方式',
    hotel         varchar(100) NOT NULL DEFAULT '' COMMENT '住宿',
    meal          varchar(100) NOT NULL DEFAULT '' COMMENT '用餐',
    duration_min  int unsigned NOT NULL DEFAULT 0 COMMENT '时长（分钟）',
    cost          decimal(10,2) NOT NULL DEFAULT 0 COMMENT '费用',
    tips          varchar(255) NOT NULL DEFAULT '' COMMENT '小贴士',
    create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_day_sort (plan_day_id, sort_no),
    KEY idx_plan_day_id (plan_day_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='规划行程项';

-- ---------------------------------------------------------------------
-- 五、AI 组（5 张）
-- ---------------------------------------------------------------------

-- 17. llm_chat_session 对话会话（8 张逻辑删除表之一）
DROP TABLE IF EXISTS llm_chat_session;
CREATE TABLE llm_chat_session (
    id               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    user_id          bigint unsigned NOT NULL COMMENT '用户ID',
    title            varchar(100) NOT NULL DEFAULT '' COMMENT '会话标题（取首问前 N 字）',
    mode             varchar(20) NOT NULL DEFAULT 'LEGACY' COMMENT 'LEGACY / LOCAL_SEARCH',
    revision         bigint unsigned NOT NULL DEFAULT 1 COMMENT '会话内容及标题版本',
    message_count    int unsigned NOT NULL DEFAULT 0 COMMENT '消息条数',
    last_message_time datetime    DEFAULT NULL COMMENT '最后活跃时间',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_local_session_page (user_id,mode,deleted,update_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 对话会话';

-- 18. llm_chat_message 对话消息
DROP TABLE IF EXISTS llm_chat_message;
CREATE TABLE llm_chat_message (
    id              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id      bigint unsigned NOT NULL COMMENT '会话ID',
    role            varchar(20)  NOT NULL COMMENT 'user / assistant',
    content         text         NOT NULL COMMENT '消息内容',
    message_type    varchar(20) NOT NULL DEFAULT 'LEGACY' COMMENT 'LEGACY / LOCAL_QUERY / LOCAL_RESULT',
    request_id      char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '本次检索UUID',
    request_hash    char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '规范化问题和topK摘要',
    references_json text                  COMMENT '引用溯源 JSON',
    tokens_used     int unsigned NOT NULL DEFAULT 0 COMMENT 'token 消耗',
    cost_ms         int unsigned NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_session_id (session_id),
    UNIQUE KEY uk_chat_request_role (session_id,request_id,role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 对话消息';

-- 19. llm_call_log LLM 调用日志（成本与稳定性观测唯一依据）
DROP TABLE IF EXISTS llm_call_log;
CREATE TABLE llm_call_log (
    id                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           bigint unsigned NOT NULL DEFAULT 0 COMMENT '用户ID（0=系统）',
    scene             varchar(50)  NOT NULL COMMENT '场景（提示词 code）',
    model             varchar(50)  NOT NULL DEFAULT '' COMMENT '模型名',
    prompt_tokens     int unsigned NOT NULL DEFAULT 0 COMMENT '输入 token',
    completion_tokens int unsigned NOT NULL DEFAULT 0 COMMENT '输出 token',
    total_tokens      int unsigned NOT NULL DEFAULT 0 COMMENT '总 token',
    cost_ms           int unsigned NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    success           tinyint      NOT NULL DEFAULT 1 COMMENT '1 成功 / 0 失败',
    is_fallback       tinyint      NOT NULL DEFAULT 0 COMMENT '是否降级 1是 0否',
    error_msg         varchar(500) NOT NULL DEFAULT '' COMMENT '错误信息',
    biz_id            bigint unsigned NOT NULL DEFAULT 0 COMMENT '关联业务ID（路线/会话/消息）',
    create_time       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_scene_create (scene, create_time),
    KEY idx_create_success (create_time, success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='LLM 调用日志';

-- 20. ai_insight AI 数据洞察记录
DROP TABLE IF EXISTS ai_insight;
CREATE TABLE ai_insight (
    id              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    insight_date    date         NOT NULL COMMENT '洞察日期（按日唯一）',
    content         text         NOT NULL COMMENT '洞察文本',
    highlights_json text                  COMMENT '高亮标签 JSON [{type,text}]',
    model           varchar(50)  NOT NULL DEFAULT '' COMMENT '生成模型',
    generated_at    datetime     NOT NULL COMMENT '生成时间',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_insight_date (insight_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 数据洞察';

-- 21. knowledge_doc RAG 知识库文档（8 张逻辑删除表之一）
-- 以下为新库完整建表；已有库仅执行 upgrade_local_knowledge.sql。
DROP TABLE IF EXISTS knowledge_chunk_token;
DROP TABLE IF EXISTS knowledge_chunk;
DROP TABLE IF EXISTS knowledge_doc;
CREATE TABLE knowledge_doc (
    id             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    title          varchar(200) NOT NULL COMMENT '文档标题',
    doc_type       varchar(20)  NOT NULL DEFAULT 'GUIDE' COMMENT 'DESTINATION / ROUTE / GUIDE',
    source_type    varchar(20)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / FILE / LINK',
    source_id      bigint unsigned DEFAULT NULL COMMENT '关联目的地或路线ID，空表示独立资料',
    revision       bigint unsigned NOT NULL DEFAULT 1 COMMENT '内容与状态版本',
    indexed_revision bigint unsigned NOT NULL DEFAULT 0 COMMENT '本地索引版本',
    index_method   varchar(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE / LOCAL_NGRAM，非向量索引',
    content        longtext     NOT NULL COMMENT 'RAG 正文',
    file_path      varchar(255) NOT NULL DEFAULT '' COMMENT '文件路径',
    chunk_count    int unsigned NOT NULL DEFAULT 0 COMMENT '切片数',
    vector_status  tinyint      NOT NULL DEFAULT 0 COMMENT '向量索引：0 未索引 / 1 已索引 / 2 失败，本地看index_method',
    status         tinyint      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    create_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_doc_type (doc_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='RAG 知识库文档';

CREATE TABLE knowledge_chunk (
    id bigint unsigned NOT NULL AUTO_INCREMENT,
    doc_id bigint unsigned NOT NULL,
    doc_revision bigint unsigned NOT NULL,
    chunk_index int unsigned NOT NULL,
    start_offset int unsigned NOT NULL,
    end_offset int unsigned NOT NULL,
    content text NOT NULL,
    PRIMARY KEY(id),
    UNIQUE KEY uk_knowledge_chunk(doc_id,doc_revision,chunk_index),
    CONSTRAINT fk_knowledge_chunk_doc FOREIGN KEY(doc_id) REFERENCES knowledge_doc(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本地知识分片，偏移量以Unicode字符计';

CREATE TABLE knowledge_chunk_token (
    chunk_id bigint unsigned NOT NULL,
    token varchar(64) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY(chunk_id,token),
    KEY idx_knowledge_token(token,chunk_id),
    CONSTRAINT fk_knowledge_token_chunk FOREIGN KEY(chunk_id) REFERENCES knowledge_chunk(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本地字符二元组与英文词倒排索引，非语义向量';

-- ---------------------------------------------------------------------
-- 六、运营组（6 张）
-- ---------------------------------------------------------------------

-- 22. banner 首页轮播图
DROP TABLE IF EXISTS banner;
CREATE TABLE banner (
    id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       varchar(100) NOT NULL COMMENT '标题',
    image_url   varchar(255) NOT NULL COMMENT '图片URL',
    link_type   varchar(20)  NOT NULL DEFAULT 'NONE' COMMENT 'ROUTE / DESTINATION / URL / NONE',
    link_value  varchar(255) NOT NULL DEFAULT '' COMMENT '跳转值',
    sort_no     int          NOT NULL DEFAULT 0 COMMENT '排序号（同值按创建时间倒序）',
    status      tinyint      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用（启用中 ≤8 张）',
    start_time  datetime     DEFAULT NULL COMMENT '生效开始时间',
    end_time    datetime     DEFAULT NULL COMMENT '生效结束时间',
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status_sort (status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='首页轮播图';

-- 23. feedback 用户反馈（8 张逻辑删除表之一）
DROP TABLE IF EXISTS feedback;
CREATE TABLE feedback (
    id            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       bigint unsigned NOT NULL DEFAULT 0 COMMENT '用户ID',
    type          varchar(20)  NOT NULL COMMENT 'FUNCTION / BUG / CONTENT / OTHER',
    title         varchar(100) NOT NULL COMMENT '标题',
    content       text         NOT NULL COMMENT '内容',
    images        varchar(1000) NOT NULL DEFAULT '' COMMENT '图片，逗号分隔',
    contact       varchar(100) NOT NULL DEFAULT '' COMMENT '联系方式',
    status        tinyint      NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2已解决 3已关闭',
    reply_content text                  COMMENT '回复内容',
    reply_by      bigint unsigned NOT NULL DEFAULT 0 COMMENT '回复管理员',
    reply_time    datetime     DEFAULT NULL COMMENT '回复时间',
    create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户反馈';

-- 24. recommend_config 推荐与 LLM 配置（KV 形式，后台按组渲染）
DROP TABLE IF EXISTS recommend_config;
CREATE TABLE recommend_config (
    id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_group varchar(50)  NOT NULL DEFAULT '' COMMENT '分组 recall/llm/rag/sentiment/quota/insight',
    config_key   varchar(100) NOT NULL COMMENT '配置键（唯一）',
    config_value varchar(500) NOT NULL DEFAULT '' COMMENT '配置值（字符串存储）',
    value_type   varchar(20)  NOT NULL DEFAULT 'STRING' COMMENT 'NUMBER / BOOLEAN / STRING',
    remark       varchar(255) NOT NULL DEFAULT '' COMMENT '中文说明',
    sort_no      int          NOT NULL DEFAULT 0 COMMENT '组内排序',
    create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='推荐与LLM配置';

-- 25. prompt_template 提示词模板（含版本与 JSON Schema）
DROP TABLE IF EXISTS prompt_template;
CREATE TABLE prompt_template (
    id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        varchar(50)  NOT NULL COMMENT '模板编码（唯一于业务）',
    name        varchar(100) NOT NULL COMMENT '模板名称',
    scene       varchar(50)  NOT NULL DEFAULT '' COMMENT '场景说明',
    content     longtext     NOT NULL COMMENT '模板正文（占位符 {{var}}）',
    variables   varchar(500) NOT NULL DEFAULT '' COMMENT '变量名 JSON 数组',
    version     int unsigned NOT NULL DEFAULT 1 COMMENT '版本号（从 1 递增）',
    status      tinyint      NOT NULL DEFAULT 0 COMMENT '1 启用 / 0 停用（同 code 唯一启用）',
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code_version (code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='提示词模板';

-- 26. stat_daily 日粒度预聚合指标（大屏与 AI 洞察数据源，长表）
DROP TABLE IF EXISTS stat_daily;
CREATE TABLE stat_daily (
    id           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    stat_date    date         NOT NULL COMMENT '统计日期（自然日）',
    metric_key   varchar(50)  NOT NULL COMMENT '指标键',
    metric_value decimal(18,4) NOT NULL DEFAULT 0 COMMENT '指标值',
    extra_json   text                  COMMENT '维度数据 JSON（地域TOP5/路线热度TOP3等）',
    create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_date_metric (stat_date, metric_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日粒度预聚合指标';

-- 27. sys_log 操作日志
DROP TABLE IF EXISTS sys_log;
CREATE TABLE sys_log (
    id          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     bigint unsigned NOT NULL DEFAULT 0 COMMENT '用户ID',
    username    varchar(50)  NOT NULL DEFAULT '' COMMENT '用户名（冗余，防删）',
    module      varchar(50)  NOT NULL DEFAULT '' COMMENT '模块',
    operation   varchar(100) NOT NULL DEFAULT '' COMMENT '操作说明',
    method      varchar(10)  NOT NULL DEFAULT '' COMMENT 'HTTP 方法',
    request_uri varchar(255) NOT NULL DEFAULT '' COMMENT '请求路径',
    params      text                  COMMENT '请求参数',
    ip          varchar(50)  NOT NULL DEFAULT '' COMMENT '客户端 IP',
    cost_ms     int unsigned NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    success     tinyint      NOT NULL DEFAULT 1 COMMENT '1 成功 / 0 失败',
    error_msg   varchar(500) NOT NULL DEFAULT '' COMMENT '错误信息',
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_create_time (create_time),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志';
