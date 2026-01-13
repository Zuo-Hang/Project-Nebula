-- AI Agent Orchestrator 数据库初始化脚本

CREATE DATABASE IF NOT EXISTS ai_orchestrator DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_orchestrator;

-- 供应商应答概率表（从旧项目迁移）
CREATE TABLE IF NOT EXISTS `supplier_response_rate` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `city_name` VARCHAR(100) NOT NULL COMMENT '城市名称',
    `partner_name` VARCHAR(200) NOT NULL COMMENT '供应商名称',
    `response_rate` DECIMAL(10, 6) NOT NULL DEFAULT 0.000000 COMMENT '应答概率（o_supplier_rate）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_city_partner` (`city_name`, `partner_name`),
    KEY `idx_city_name` (`city_name`),
    KEY `idx_partner_name` (`partner_name`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商应答概率表';

-- 任务表
CREATE TABLE IF NOT EXISTS tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) UNIQUE NOT NULL COMMENT '任务ID',
    task_type VARCHAR(32) NOT NULL COMMENT '任务类型',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    input_data TEXT COMMENT '输入数据',
    output_data TEXT COMMENT '输出数据',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_task_id (task_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- 步骤执行记录表
CREATE TABLE IF NOT EXISTS step_executions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    step_name VARCHAR(64) NOT NULL COMMENT '步骤名称',
    step_type VARCHAR(32) NOT NULL COMMENT '步骤类型',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '执行状态',
    input_data TEXT COMMENT '输入数据',
    output_data TEXT COMMENT '输出数据',
    error_message TEXT COMMENT '错误信息',
    execution_time_ms INT COMMENT '执行耗时(毫秒)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_task_id (task_id),
    INDEX idx_step_name (step_name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='步骤执行记录表';

-- 质量检查记录表
CREATE TABLE IF NOT EXISTS quality_checks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    check_type VARCHAR(32) NOT NULL COMMENT '检查类型',
    check_result VARCHAR(16) NOT NULL COMMENT '检查结果',
    check_details TEXT COMMENT '检查详情',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_check_type (check_type),
    INDEX idx_check_result (check_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量检查记录表';
