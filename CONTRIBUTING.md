# ForgeAdmin 贡献指南
欢迎参与 ForgeAdmin 开源项目共建🎉
仓库地址：https://gitee.com/ForgeLab/forge-admin

## 一、协作模式（重要）
本项目统一采用 **Fork + Pull Request** 工作流：
1. 请勿直接向本仓库申请成员权限；
2. 所有代码修改必须通过 PR 提交，**main 分支受保护，禁止直接 Push**；
3. 所有 PR 最终由项目创始人审核、决定是否合并，拥有最终取舍权。

> ⚠️ 重要约定
> 1. 开源主干（main）内代码遵循仓库 LICENSE 协议开源；
> 2. ForgeAdmin 品牌、商标、官方商业授权、私有化定制服务相关收益归属项目创始主体；
> 3. 社区代码贡献属于自愿开源共建，**不默认享有商业收益分成**；若需要付费合作开发，由双方单独协商劳务协议。
> 4. 部分高级功能规划为商业增值模块，相关需求 PR 可能不予合入开源主干，请理解。

## 二、开发分支规范
- `main`：稳定发行主干，保持可用状态，仅通过 PR 合并；
- `dev`（规划分支）：新功能开发分支；
> 发起 PR 请优先目标指向 `dev`，版本发布后由维护者合并至 main。

### 开发者标准流程
1. Fork `ForgeLab/forge-admin` 到你自己的 Gitee 账号
2. Clone 你 Fork 后的仓库到本地
3. 添加上游仓库（仅首次执行）
```bash
git remote add upstream https://gitee.com/ForgeLab/forge-admin.git
