# TaskFlow AI TODO

> 更新日期: 2026-05-23 | 测试: 108 个，全部通过

---

## 高优先级

- [x] **数据库迁移** — 引入 Flyway，替代 `ddl-auto: update`。创建 `V1__init.sql` 基线脚本 ✅ 2026-05-23
  - 添加 `flyway-core` 依赖，`ddl-auto` 改为 `validate`，`baseline-on-migrate: true`
  - 实际启动验证：Flyway 成功连接 PostgreSQL 17，基线版本 1 已创建
- [x] **API 限流** — 接入 Resilience4j RateLimiter，防止 LLM API 费用失控。`RATE_LIMITED` 错误码已激活 ✅ 2026-05-23
  - `@RateLimiter(name = "agent-execute")` 加在 `executeTask/executeTaskAsync`，默认 10 req/s
  - curl 20 并发验证：10×202 + 10×429，429 响应体含 `errorCode: "RATE_LIMITED"`
- [x] **压力测试** — 按 `docs/压测方案.md` 执行场景 3（Mock 高并发）和场景 4（幂等性） ✅ 2026-05-23
  - 场景 3：20 线程 × 5 任务 = 100 任务全部完成，耗时 4.7s
  - 场景 4：10 并发同 key → 1×202 + 9×409（通过），发现并修复幂等性竞态条件 bug
  - 场景 1/2/5/6/7 需真实基础设施，详见 `docs/压测方案.md` 第八章

## 中优先级

- [ ] **结构化日志** — 创建 `logback-spring.xml`，加入滚动文件 + JSON 格式输出，对接 ELK/Datadog
- [ ] **docker-compose 完善** — 取消注释 PostgreSQL 和 Redis 服务，添加共享网络，实现 `docker compose up` 一键启动
- [ ] **部署文档** — 创建 `docs/部署指南.md`，包含 K8s 部署清单、环境变量参考表、容量规划建议
- [ ] **Swagger 注解** — 给 Controller 加 `@Operation`，给 DTO 加 `@Schema`，补 `@OpenAPIDefinition`
- [ ] **CORS 配置** — `WebConfig` 增加 `addCorsMappings()`，当前仅同源 SPA 不受影响

## 低优先级

- [ ] **代码质量工具** — 添加 `.editorconfig`、Checkstyle/SpotBugs、Spotless Maven 插件
- [ ] **自定义 Actuator 指标** — 任务执行数、工具调用次数、缓存命中率的 Micrometer 指标
- [ ] **环境 Profile 拆分** — 从 default/prod 两个 profile 扩展出 dev/test/staging
- [ ] **`.gitignore` 完善** — 补充 `*.log.*`、`*.swp`、`application-*.yml`
- [ ] **`ExecutorService` 生命周期** — 当前 `asyncExecutor` 未在 JVM 关闭时 shutdown
- [ ] **`LLMClient` apiKey 空值校验** — 缺少 apiKey 时提前报错，避免 `Authorization: Bearer ` 空 header
