#!/bin/bash

# Docker Compose 启动脚本
# 用于启动所有第三方组件（Redis、RocketMQ、Nacos）

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "启动 Mars Data Java 第三方组件"
echo "=========================================="
echo ""

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ 错误: Docker 未运行，请先启动 Docker"
    exit 1
fi

# 检查 Docker Compose 是否安装
if ! command -v docker-compose > /dev/null 2>&1 && ! docker compose version > /dev/null 2>&1; then
    echo "❌ 错误: Docker Compose 未安装"
    exit 1
fi

# 使用 docker compose 或 docker-compose
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# 创建必要的目录
echo "📁 创建必要的目录..."
mkdir -p mysql redis rocketmq prometheus grafana/provisioning/datasources grafana/provisioning/dashboards
echo "✅ 目录创建完成"
echo ""

# 启动服务
echo "🚀 启动服务..."
$DOCKER_COMPOSE up -d

echo ""
echo "⏳ 等待服务启动..."
sleep 10

# 检查服务状态
echo ""
echo "📊 服务状态:"
$DOCKER_COMPOSE ps

echo ""
echo "=========================================="
echo "✅ 服务启动完成！"
echo "=========================================="
echo ""
echo "📋 服务访问地址:"
echo "  - Nacos 控制台:     http://localhost:8848/nacos"
echo "  - Nacos 用户名:     nacos"
echo "  - Nacos 密码:       nacos"
echo "  - RocketMQ 控制台:  http://localhost:8081"
echo "  - Redis:            localhost:6379"
echo "  - MySQL (Nacos):    localhost:3307"
echo "  - Prometheus:       http://localhost:9090"
echo "  - Grafana:          http://localhost:3000"
echo "  - Grafana 用户名:   admin"
echo "  - Grafana 密码:     admin"
echo ""
echo "🔧 常用命令:"
echo "  - 查看日志:         $DOCKER_COMPOSE logs -f [服务名]"
echo "  - 停止服务:         $DOCKER_COMPOSE down"
echo "  - 重启服务:         $DOCKER_COMPOSE restart [服务名]"
echo "  - 查看状态:         $DOCKER_COMPOSE ps"
echo ""

