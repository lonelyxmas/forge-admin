# 更新日志

本文档记录项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 新增统一企业协同能力并接入企业微信，支持多连接与多应用配置、安全登录、通讯录与标签同步、协同消息及同步运维管理。
- 新增统一能力开放平台，支持低代码业务动作、流程动作和系统服务的能力注册、版本发布、客户端授权及统一开放调用。
- 新增 OAuth2/HMAC 开放网关、限流、幂等、调用审计、调用指南和在线测试能力。

### Changed
- 重构 APP 模块，优化低代码应用创建、设计与运行体验。
- 优化定时任务管理及前端布局交互。

### Fixed
- 加固验证码、开放接口认证、租户隔离和敏感凭据处理，提升系统安全性与稳定性。

## [1.0.0] - 2026-04-01

### Added
- 初始版本发布
- 微内核 + 插件化架构
- 多租户支持
- RBAC 权限管理
- 代码生成器
- 任务调度
- 流程管理
- 消息中心
- 系统监控

[Unreleased]: https://gitee.com/ForgeLab/forge-admin/compare/v1.0.0...HEAD
[1.0.0]: https://gitee.com/ForgeLab/forge-admin/releases/tag/v1.0.0
