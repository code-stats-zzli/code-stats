-- ============================================================
-- 表: datasource_config (移除 driver_code 字段)
-- ============================================================
CREATE TABLE IF NOT EXISTS `datasource_config` (
                                                   `id` VARCHAR(32) PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,
    `db_type` VARCHAR(20) NOT NULL,
    `host` VARCHAR(100) NOT NULL,
    `port` INT NOT NULL,
    `database_name` VARCHAR(100) NOT NULL,
    `username` VARCHAR(100),
    `password` VARCHAR(100),
    `driver_class` VARCHAR(200),
    `driver_jar` VARCHAR(200),
    `connection_properties` TEXT,
    `version` VARCHAR(20),
    `status` TINYINT DEFAULT 1,
    `create_time` DATETIME,
    `update_time` DATETIME
    );

-- ============================================================
-- 表: driver_jar (移除 code 字段)
-- ============================================================
CREATE TABLE IF NOT EXISTS `driver_jar` (
                                            `id` VARCHAR(32) PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,
    `jar_data` LONGBLOB NOT NULL,
    `file_name` VARCHAR(200),
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
    );