# 快速启动指南

## ✅ 已完成的步骤

1. ✅ Node.js 已安装（v25.2.1）
2. ✅ npm 已安装（v11.6.2）
3. ✅ 前端依赖已安装

## 🚀 启动开发服务器

### 方式一：启动前端（推荐先测试前端）

```bash
cd frontend
npm run dev
```

前端将在 http://localhost:3000 启动。

### 方式二：启动后端（需要先启动后端才能完整测试）

在另一个终端窗口：

```bash
cd orchestrator-core
mvn spring-boot:run
```

后端将在 http://localhost:8080 启动。

## 📝 注意事项

1. **依赖警告**：安装过程中有一些 deprecated 警告，这是正常的，不影响使用
2. **安全漏洞**：有 2 个 moderate 级别的安全漏洞，可以稍后处理：
   ```bash
   npm audit fix
   ```
3. **npm 更新**：可以更新到最新版本（可选）：
   ```bash
   npm install -g npm@latest
   ```

## 🎯 下一步

1. 启动前端：`cd frontend && npm run dev`
2. 启动后端：`cd orchestrator-core && mvn spring-boot:run`
3. 访问前端：打开浏览器访问 http://localhost:3000
4. 测试功能：尝试提交任务、查看任务列表等

## 🔧 如果遇到问题

- **端口被占用**：修改 `vite.config.ts` 中的端口号
- **后端连接失败**：确保后端已启动，检查 `vite.config.ts` 中的代理配置
- **CORS 错误**：确保后端的 `CorsConfig.java` 已正确配置

