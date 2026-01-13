# AI Agent Orchestrator Frontend

基于 React + TypeScript + Vite 构建的现代化前端应用。

## 技术栈

- **React 18** - UI框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具（比Webpack快10倍）
- **React Router** - 路由管理
- **React Query** - 数据获取和缓存
- **Ant Design** - UI组件库
- **Zustand** - 轻量级状态管理
- **Axios** - HTTP客户端

## 环境要求

- Node.js >= 18.0.0
- npm >= 9.0.0 或 yarn >= 1.22.0

## 安装依赖

```bash
npm install
# 或
yarn install
```

## 开发

```bash
npm run dev
# 或
yarn dev
```

应用将在 http://localhost:3000 启动。

## 构建

```bash
npm run build
# 或
yarn build
```

构建产物将输出到 `dist/` 目录。

## 预览构建结果

```bash
npm run preview
# 或
yarn preview
```

## 项目结构

```
frontend/
├── src/
│   ├── api/           # API客户端
│   ├── components/    # 组件
│   ├── pages/         # 页面
│   ├── App.tsx        # 根组件
│   └── main.tsx       # 入口文件
├── public/            # 静态资源
├── index.html         # HTML模板
├── vite.config.ts     # Vite配置
├── tsconfig.json      # TypeScript配置
└── package.json       # 依赖配置
```

## 功能特性

- ✅ 任务列表查看
- ✅ 任务详情查看
- ✅ 任务提交
- ✅ 实时状态更新（运行中任务自动刷新）
- ✅ 任务筛选和搜索

## 与后端集成

前端通过代理连接到后端API（`http://localhost:8080`）。

代理配置在 `vite.config.ts` 中：

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

## 环境变量

创建 `.env` 文件（可选）：

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

