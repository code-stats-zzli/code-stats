-- 创建数据库（可选）
-- CREATE DATABASE IF NOT EXISTS `autotest` DEFAULT CHARSET=utf8mb4;
-- USE `autotest`;

-- 1. 项目表
CREATE TABLE `project` (
                           `id` VARCHAR(36) PRIMARY KEY,
                           `project_name` VARCHAR(100) NOT NULL COMMENT '项目名称',
                           `project_code` VARCHAR(50) UNIQUE NOT NULL COMMENT '项目编码',
                           `description` VARCHAR(500) COMMENT '项目描述',
                           `status` TINYINT DEFAULT 1 COMMENT '1-启用 0-禁用',
                           `create_by` VARCHAR(50),
                           `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                           `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 测试用例表
CREATE TABLE `test_case` (
                             `id` VARCHAR(36) PRIMARY KEY,
                             `project_id` VARCHAR(36) NOT NULL,
                             `case_name` VARCHAR(200) NOT NULL,
                             `case_code` VARCHAR(50) UNIQUE,
                             `module_path` VARCHAR(200),
                             `priority` TINYINT DEFAULT 2,
                             `env_type` TINYINT DEFAULT 1,
                             `browser_type` VARCHAR(20) DEFAULT 'chrome',
                             `base_url` VARCHAR(500),
                             `timeout_sec` INT DEFAULT 30,
                             `retry_times` INT DEFAULT 0,
                             `status` TINYINT DEFAULT 1,
                             `tags` VARCHAR(200),
                             `create_by` VARCHAR(50),
                             `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                             `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             INDEX idx_project (`project_id`),
                             INDEX idx_status (`status`),
                             FOREIGN KEY (`project_id`) REFERENCES `project`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 测试步骤表
CREATE TABLE `test_step` (
                             `id` VARCHAR(36) PRIMARY KEY,
                             `case_id` VARCHAR(36) NOT NULL,
                             `step_order` INT NOT NULL,
                             `parent_step_id` VARCHAR(36) DEFAULT NULL,
                             `step_type` TINYINT DEFAULT 1,
                             `action_type` VARCHAR(30) NOT NULL,
                             `locator_type` VARCHAR(20),
                             `locator_value` VARCHAR(1000),
                             `locator_priority` TEXT,
                             `action_value` VARCHAR(4000),
                             `action_value_type` TINYINT DEFAULT 1,
                             `wait_type` VARCHAR(20) DEFAULT 'none',
                             `wait_seconds` INT DEFAULT 0,
                             `wait_until_value` VARCHAR(500),
                             `fail_handle` TINYINT DEFAULT 1,
                             `ignore_exception` TINYINT DEFAULT 0,
                             `step_desc` VARCHAR(200),
                             `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                             `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             INDEX idx_case_order (`case_id`, `step_order`),
                             INDEX idx_parent (`parent_step_id`),
                             FOREIGN KEY (`case_id`) REFERENCES `test_case`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 断言表
CREATE TABLE `test_assertion` (
                                  `id` VARCHAR(36) PRIMARY KEY,
                                  `case_id` VARCHAR(36) NOT NULL,
                                  `step_id` VARCHAR(36) DEFAULT NULL,
                                  `assert_order` INT NOT NULL,
                                  `assert_type` VARCHAR(30) NOT NULL,
                                  `locator_type` VARCHAR(20),
                                  `locator_value` VARCHAR(1000),
                                  `expected_value` VARCHAR(4000),
                                  `expected_value_type` TINYINT DEFAULT 1,
                                  `compare_type` VARCHAR(20) DEFAULT 'equals',
                                  `custom_script` VARCHAR(4000),
                                  `fail_handle` TINYINT DEFAULT 1,
                                  `assert_desc` VARCHAR(200),
                                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  INDEX idx_case (`case_id`),
                                  INDEX idx_step (`step_id`),
                                  FOREIGN KEY (`case_id`) REFERENCES `test_case`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 变量定义表
CREATE TABLE `test_variable` (
                                 `id` VARCHAR(36) PRIMARY KEY,
                                 `case_id` VARCHAR(36) NOT NULL,
                                 `var_name` VARCHAR(50) NOT NULL,
                                 `var_scope` VARCHAR(20) DEFAULT 'case',
                                 `extract_type` VARCHAR(30) NOT NULL,
                                 `locator_type` VARCHAR(20),
                                 `locator_value` VARCHAR(1000),
                                 `extract_expression` VARCHAR(500),
                                 `default_value` VARCHAR(1000),
                                 `is_required` TINYINT DEFAULT 0,
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 INDEX idx_case (`case_id`),
                                 UNIQUE KEY uk_case_var (`case_id`, `var_name`),
                                 FOREIGN KEY (`case_id`) REFERENCES `test_case`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 前后置脚本表
CREATE TABLE `test_hook` (
                             `id` VARCHAR(36) PRIMARY KEY,
                             `case_id` VARCHAR(36) NOT NULL,
                             `hook_type` TINYINT NOT NULL,
                             `hook_order` INT DEFAULT 0,
                             `script_type` VARCHAR(20) DEFAULT 'js',
                             `script_content` TEXT NOT NULL,
                             `script_desc` VARCHAR(200),
                             `enabled` TINYINT DEFAULT 1,
                             `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                             `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             INDEX idx_case_type (`case_id`, `hook_type`),
                             FOREIGN KEY (`case_id`) REFERENCES `test_case`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 测试数据集表
CREATE TABLE `test_data` (
                             `id` VARCHAR(36) PRIMARY KEY,
                             `case_id` VARCHAR(36) DEFAULT NULL,
                             `data_name` VARCHAR(100) NOT NULL,
                             `data_key` VARCHAR(100) NOT NULL,
                             `data_value` VARCHAR(4000) NOT NULL,
                             `data_type` TINYINT DEFAULT 1,
                             `env_type` TINYINT DEFAULT 1,
                             `description` VARCHAR(200),
                             `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                             `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             INDEX idx_case_env (`case_id`, `env_type`),
                             UNIQUE KEY uk_case_key (`case_id`, `data_key`, `env_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 执行记录表
CREATE TABLE `test_execution` (
                                  `id` VARCHAR(36) PRIMARY KEY,
                                  `case_id` VARCHAR(36) NOT NULL,
                                  `execution_code` VARCHAR(50) UNIQUE,
                                  `trigger_type` TINYINT DEFAULT 1,
                                  `trigger_user` VARCHAR(50),
                                  `trigger_source` VARCHAR(100),
                                  `env_type` TINYINT DEFAULT 1,
                                  `browser_type` VARCHAR(20),
                                  `browser_version` VARCHAR(50),
                                  `os_type` VARCHAR(50),
                                  `os_version` VARCHAR(50),
                                  `start_time` DATETIME,
                                  `end_time` DATETIME,
                                  `total_steps` INT DEFAULT 0,
                                  `pass_count` INT DEFAULT 0,
                                  `fail_count` INT DEFAULT 0,
                                  `skip_count` INT DEFAULT 0,
                                  `pass_rate` DECIMAL(5,2) DEFAULT 0.00,
                                  `status` TINYINT DEFAULT 0,
                                  `error_message` TEXT,
                                  `total_time_ms` INT,
                                  `avg_step_time_ms` INT,
                                  `max_step_time_ms` INT,
                                  `report_url` VARCHAR(500),
                                  `report_json_url` VARCHAR(500),
                                  `video_url` VARCHAR(500),
                                  `log_url` VARCHAR(500),
                                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                  INDEX idx_case_time (`case_id`, `create_time`),
                                  INDEX idx_status (`status`),
                                  INDEX idx_code (`execution_code`),
                                  FOREIGN KEY (`case_id`) REFERENCES `test_case`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 步骤快照表
CREATE TABLE `test_snapshot` (
                                 `id` VARCHAR(36) PRIMARY KEY,
                                 `execution_id` VARCHAR(36) NOT NULL,
                                 `step_id` VARCHAR(36) DEFAULT NULL,
                                 `step_order` INT NOT NULL,
                                 `action_type` VARCHAR(30) NOT NULL,
                                 `step_desc` VARCHAR(200),
                                 `html_content` MEDIUMTEXT,
                                 `html_size` INT,
                                 `screenshot_url` VARCHAR(500),
                                 `screenshot_thumbnail_url` VARCHAR(500),
                                 `element_tag` VARCHAR(50),
                                 `element_selector` VARCHAR(500),
                                 `element_rect` VARCHAR(200),
                                 `element_text` VARCHAR(500),
                                 `is_success` TINYINT DEFAULT 1,
                                 `error_msg` VARCHAR(2000),
                                 `error_stack` TEXT,
                                 `exec_time_ms` INT,
                                 `variables_snapshot` TEXT,
                                 `cookies_snapshot` TEXT,
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 INDEX idx_exec_order (`execution_id`, `step_order`),
                                 INDEX idx_exec_time (`execution_id`, `create_time`),
                                 FOREIGN KEY (`execution_id`) REFERENCES `test_execution`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;