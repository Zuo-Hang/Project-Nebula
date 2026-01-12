#!/bin/bash

# Nacos 配置初始化脚本
# 用于批量将配置导入到 Nacos 配置中心

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

# 默认配置
NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR:-127.0.0.1:8848}
NACOS_NAMESPACE=${NACOS_NAMESPACE:-}
NACOS_USERNAME=${NACOS_USERNAME:-nacos}
NACOS_PASSWORD=${NACOS_PASSWORD:-nacos}
NACOS_GROUP=${NACOS_GROUP:-DEFAULT_GROUP}
CONFIG_DIR=${CONFIG_DIR:-./conf/nacos}

echo "=========================================="
echo "Nacos 配置初始化"
echo "=========================================="
echo ""
echo "配置参数:"
echo "  服务器地址: $NACOS_SERVER_ADDR"
echo "  命名空间: ${NACOS_NAMESPACE:-（默认）}"
echo "  用户名: $NACOS_USERNAME"
echo "  配置目录: $CONFIG_DIR"
echo ""

# 检查配置目录是否存在
if [ ! -d "$CONFIG_DIR" ]; then
    echo "❌ 错误: 配置目录不存在: $CONFIG_DIR"
    echo ""
    echo "请创建配置目录并添加配置文件，例如:"
    echo "  mkdir -p $CONFIG_DIR"
    echo "  echo 'key1=value1' > $CONFIG_DIR/OCR_LLM_CONF.properties"
    exit 1
fi

# 检查是否有配置文件
if [ -z "$(ls -A $CONFIG_DIR/*.properties $CONFIG_DIR/*.yaml $CONFIG_DIR/*.yml 2>/dev/null)" ]; then
    echo "❌ 错误: 配置目录中没有找到配置文件"
    echo ""
    echo "支持的格式: .properties, .yaml, .yml"
    exit 1
fi

# 编译项目（如果需要）
if [ ! -f "$PROJECT_DIR/target/classes/com/wuxiansheng/shieldarch/marsdata/config/NacosConfigInitializer.class" ]; then
    echo "📦 编译项目..."
    mvn compile -q
    echo "✅ 编译完成"
    echo ""
fi

# 运行配置初始化
echo "🚀 开始导入配置..."
echo ""

java -cp "$PROJECT_DIR/target/classes:$PROJECT_DIR/target/dependency/*" \
    com.wuxiansheng.shieldarch.marsdata.config.NacosConfigInitializer \
    --server-addr="$NACOS_SERVER_ADDR" \
    --namespace="$NACOS_NAMESPACE" \
    --username="$NACOS_USERNAME" \
    --password="$NACOS_PASSWORD" \
    --group="$NACOS_GROUP" \
    --config-dir="$CONFIG_DIR"

echo ""
echo "=========================================="
echo "✅ 配置初始化完成"
echo "=========================================="

