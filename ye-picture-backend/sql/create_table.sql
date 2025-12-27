CREATE DATABASE IF NOT EXISTS ye_picture;

-- 用户表
CREATE TABLE IF NOT EXISTS user
(
    id           bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    userAccount  varchar(256)                           NOT NULL COMMENT '账号',
    userPassword varchar(512)                           NOT NULL COMMENT '密码',
    userName     varchar(256)                           NULL COMMENT '用户昵称',
    userAvatar   varchar(1024)                          NULL COMMENT '用户头像',
    userProfile  varchar(512)                           NULL COMMENT '用户简介',
    userRole     varchar(256) DEFAULT 'user'            NOT NULL COMMENT '用户角色：user/admin',
    editTime     datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
    createTime   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete     tinyint      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    userEmail    varchar(128)                           NULL COMMENT '用户接收通知的邮箱',

    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName),
    INDEX idx_userEmail (userEmail)
) COMMENT '用户' COLLATE = utf8mb4_unicode_ci;

-- 图片表
CREATE TABLE IF NOT EXISTS picture
(
    -- 基础主键与核心信息
    id            bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    url           varchar(512)                       NOT NULL COMMENT '原图片 url',
    compressUrl   varchar(512)                       NULL COMMENT '压缩图 url',
    thumbnailUrl  varchar(512)                       NULL COMMENT '缩略图 url',
    name          varchar(128)                       NOT NULL COMMENT '图片名称',
    introduction  varchar(512)                       NULL COMMENT '简介',
    category      varchar(64)                        NULL COMMENT '分类',
    tags          varchar(512)                       NULL COMMENT '标签（JSON 数组）',
    -- 图片属性字段
    picSize       bigint                             NULL COMMENT '图片体积',
    picWidth      int                                NULL COMMENT '图片宽度',
    picHeight     int                                NULL COMMENT '图片高度',
    picScale      double                             NULL COMMENT '图片宽高比例',
    picFormat     varchar(32)                        NULL COMMENT '图片格式',
    picColor      varchar(16)                        NULL COMMENT '图片主色调',
    -- 关联与审核字段
    userId        bigint                             NOT NULL COMMENT '创建用户 id',
    spaceId       bigint                             NULL COMMENT '空间 id（为空表示公共空间）',
    reviewStatus  INT                                NOT NULL DEFAULT 0 COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    reviewMessage VARCHAR(512)                       NULL COMMENT '审核信息',
    reviewerId    BIGINT                             NULL COMMENT '审核人 ID',
    reviewTime    DATETIME                           NULL COMMENT '审核时间',
    -- 通用时间与逻辑删除字段
    createTime    datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    editTime      datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
    updateTime    datetime DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete      tinyint  DEFAULT 0                 NOT NULL COMMENT '是否删除',

    INDEX idx_name (name),
    INDEX idx_introduction (introduction),
    INDEX idx_category (category),
    INDEX idx_tags (tags),
    INDEX idx_userId (userId),
    INDEX idx_reviewStatus (reviewStatus),
    INDEX idx_spaceId (spaceId)
) COMMENT '图片' COLLATE = utf8mb4_unicode_ci;

-- 空间表
CREATE TABLE IF NOT EXISTS space
(
    id         bigint auto_increment comment 'id' primary key,
    spaceName  varchar(128)                       null comment '空间名称',
    spaceLevel int      default 0                 null comment '空间级别：0-普通版 1-专业版 2-旗舰版',
    maxSize    bigint   default 0                 null comment '空间图片的最大总大小',
    maxCount   bigint   default 0                 null comment '空间图片的最大数量',
    totalSize  bigint   default 0                 null comment '当前空间下图片的总大小',
    totalCount bigint   default 0                 null comment '当前空间下的图片数量',
    userId     bigint                             not null comment '创建用户 id',
    spaceType  int      default 0                 not null comment '空间类型：0-私有 1-团队',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',

    INDEX idx_userId (userId),
    INDEX idx_spaceName (spaceName),
    INDEX idx_spaceLevel (spaceLevel),
    INDEX idx_spaceType (spaceType)
) COMMENT '空间' COLLATE = utf8mb4_unicode_ci;

-- 空间成员表
CREATE TABLE IF NOT EXISTS space_user
(
    id         bigint auto_increment comment 'id' primary key,
    spaceId    bigint                                 not null comment '空间 id',
    userId     bigint                                 not null comment '用户 id',
    spaceRole  varchar(128) default 'viewer'          null comment '空间角色：viewer/editor/admin',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',

    UNIQUE KEY uk_spaceId_userId (spaceId, userId),
    INDEX idx_spaceId (spaceId),
    INDEX idx_userId (userId)
) COMMENT '空间用户关联' COLLATE = utf8mb4_unicode_ci;

-- 图片标签表
CREATE TABLE IF NOT EXISTS picture_tag
(
    id         bigint auto_increment comment 'id' primary key,
    tagName    varchar(64)                        not null comment '标签名称',
    sortOrder  int      default 0                 null comment '排序顺序，数字越小越靠前',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',

    UNIQUE KEY uk_tagName (tagName),
    INDEX idx_sortOrder (sortOrder)
) COMMENT '图片标签' COLLATE = utf8mb4_unicode_ci;

-- 图片分类表
CREATE TABLE IF NOT EXISTS picture_category
(
    id           bigint auto_increment comment 'id' primary key,
    categoryName varchar(64)                        not null comment '分类名称',
    sortOrder    int      default 0                 null comment '排序顺序，数字越小越靠前',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',

    UNIQUE KEY uk_categoryName (categoryName),
    INDEX idx_sortOrder (sortOrder)
) COMMENT '图片分类' COLLATE = utf8mb4_unicode_ci;

-- 系统通知表（存储审核结果通知、消息状态等）
CREATE TABLE IF NOT EXISTS sys_notice
(
    id             bigint AUTO_INCREMENT COMMENT '通知ID' PRIMARY KEY,
    userId         bigint                             NOT NULL COMMENT '接收通知的用户ID（图片上传者）',
    pictureId      bigint                             NOT NULL COMMENT '关联图片ID',
    noticeType     tinyint                            NOT NULL COMMENT '通知类型：1-图片审核结果',
    noticeContent  varchar(500)                       NOT NULL COMMENT '通知内容（如审核通过/驳回理由）',
    noticeStatus   tinyint      DEFAULT 0             NOT NULL COMMENT '发送状态：0-待发送；1-已发送；2-发送失败',
    readStatus     tinyint      DEFAULT 0             NOT NULL COMMENT '阅读状态：0-未读；1-已读',
    sendTime       datetime                           NULL COMMENT '通知发送时间',
    createTime     datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime     datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete       tinyint      DEFAULT 0             NOT NULL COMMENT '是否删除',

    INDEX idx_userId (userId),
    INDEX idx_pictureId (pictureId),
    INDEX idx_noticeStatus (noticeStatus),
    INDEX idx_readStatus (readStatus)
) COMMENT '系统通知表' COLLATE = utf8mb4_unicode_ci;

ALTER TABLE sys_notice
    ADD COLUMN reviewStatus TINYINT NULL DEFAULT NULL COMMENT '审核状态：0-待审,1-通过,2-拒绝';