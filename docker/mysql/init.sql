-- Nacos MySQL 初始化脚本
-- 用于创建 Nacos 数据库和表结构

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS nacos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE nacos;

-- 注意：Nacos 2.3.0 的表结构会自动创建
-- 这里只创建数据库，表结构会在 Nacos 首次启动时自动创建

