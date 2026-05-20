-- 创建数据源配置表，id 使用 CHAR(32) 存储 UUID（无连字符）
CREATE TABLE IF NOT EXISTS `datasource_config` (
                                                   `id` VARCHAR(64) NOT NULL COMMENT '主键，UUID',
    `name` VARCHAR(100) NOT NULL COMMENT '数据源名称',
    `db_type` VARCHAR(20) NOT NULL DEFAULT 'mysql' COMMENT '数据库类型：mysql, oracle, postgresql',
    `host` VARCHAR(255) NOT NULL COMMENT '主机地址',
    `port` INT NOT NULL COMMENT '端口号',
    `database_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
    `username` VARCHAR(100) DEFAULT NULL COMMENT '用户名',
    `password` VARCHAR(255) DEFAULT NULL COMMENT '密码',
    `driver_class` VARCHAR(255) DEFAULT NULL COMMENT 'JDBC驱动类名',
    `driver_jar` VARCHAR(500) DEFAULT NULL COMMENT '驱动Jar路径（可选）',
    `connection_properties` VARCHAR(500) DEFAULT NULL COMMENT '额外连接属性，如 useSSL=false',
    `version` VARCHAR(20) DEFAULT NULL COMMENT '数据库版本',
    `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置表';

-- 插入一条示例数据（可选）
INSERT INTO `datasource_config` (`id`, `name`, `db_type`, `host`, `port`, `database_name`, `username`, `password`, `driver_class`, `version`, `status`)
VALUES (REPLACE(UUID(), '-', ''), '本地MySQL', 'mysql', 'localhost', 3306, 'test', 'root', '123456', 'com.mysql.cj.jdbc.Driver', '8.0', 1);