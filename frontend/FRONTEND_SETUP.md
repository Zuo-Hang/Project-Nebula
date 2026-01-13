# 前端项目设置指南

## 📦 项目结构

前端项目已创建在 `frontend/` 目录下，采用 **Monorepo** 方式与后端放在同一仓库。

```
Project-Nebula/
├── frontend/              # 前端项目（React + TypeScript + Vite）
│   ├── src/
│   │   ├── api/          # API客户端
│   │   ├── components/   # 组件
│   │   ├── pages/        # 页面
│   │   └── ...
│   ├── package.json
│   └── vite.config.ts
├── orchestrator-core/    # 后端核心
└── ...
```

## 🚀 快速开始

### 1. 安装 Node.js

确保已安装 Node.js >= 18.0.0：

```bash
node --version
npm --version
```

如果未安装，请访问 https://nodejs.org/ 下载安装。

### 2. 安装前端依赖

```bash
cd frontend
npm install
```

### 3. 启动开发服务器

```bash
npm run dev
```

前端将在 http://localhost:3000 启动。

### 4. 启动后端服务

在另一个终端中启动后端：

```bash
cd orchestrator-core
mvn spring-boot:run
```

后端将在 http://localhost:8080 启动。

## 🔧 配置说明

### 代理配置

前端通过 Vite 代理连接到后端 API，配置在 `vite.config.ts` 中：

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
}
```

### CORS 配置

后端已配置 CORS，允许前端跨域访问。配置在 `CorsConfig.java` 中。

## 📝 API 接口

后端提供了以下 REST API：

- `POST /api/tasks` - 提交任务
- `GET /api/tasks` - 查询任务列表
- `GET /api/tasks/{taskId}` - 查询任务详情
- `POST /api/tasks/{taskId}/cancel` - 取消任务

## 🎯 功能特性

- ✅ 任务列表查看
- ✅ 任务详情查看
- ✅ 任务提交
- ✅ 实时状态更新（运行中任务自动刷新）
- ✅ 任务筛选和搜索

## 🛠️ 技术栈

- **React 18** - UI框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **React Router** - 路由管理
- **React Query** - 数据获取和缓存
- **Ant Design** - UI组件库
- **Axios** - HTTP客户端

## 📚 学习资源

- [React 官方文档](https://react.dev/)
- [TypeScript 官方文档](https://www.typescriptlang.org/)
- [Vite 官方文档](https://vitejs.dev/)
- [Ant Design 官方文档](https://ant.design/)
- [React Query 官方文档](https://tanstack.com/query/latest)

## ⚠️ 注意事项

1. **任务列表查询**：当前实现是简化版本，实际应该从数据库查询。需要在 `TaskService.getTaskList()` 中实现数据库查询逻辑。

2. **任务取消**：当前任务取消功能还未完全实现，需要在 `TaskService.cancelTask()` 中添加取消逻辑。

3. **生产环境**：部署到生产环境时，需要：
   - 配置具体的前端域名（CORS配置）
   - 配置环境变量（API地址）
   - 构建前端项目（`npm run build`）

## 🔄 下一步

1. 实现任务列表的数据库查询
2. 实现任务取消功能
3. 添加更多任务管理功能（批量操作、导出等）
4. 优化UI/UX
5. 添加单元测试

