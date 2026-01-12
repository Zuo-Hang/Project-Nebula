#!/bin/bash

# Docker Compose 停止脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "停止 Mars Data Java 第三方组件"
echo "=========================================="
echo ""

# 使用 docker compose 或 docker-compose
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# 停止服务
echo "🛑 停止服务..."
$DOCKER_COMPOSE down

echo ""
echo "=========================================="
echo "✅ 服务已停止"
echo "=========================================="
echo ""
echo "💡 提示: 使用 'docker/docker-compose.yml down -v' 可以删除数据卷"
echo ""

