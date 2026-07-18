# 低代码不该止步于"能跑"：Forge Admin 三大核心能力硬核升级解析

> 当低代码平台的天花板被吐槽了 5 年，是时候看看真正能"深度扩展"的方案长什么样了。

---

## 写在前面

低代码这个赛道，这两年有个很微妙的变化——大家不再争论"低代码会不会取代程序员"了，而是开始吐槽**"低代码做出来的东西，改又改不动，扔又舍不得"**。

核心痛点就一句话：**低代码平台能做的事情天花板太低，一旦需求超出平台能力，就得全部推倒重来。**

[Forge Admin](https://gitee.com/ForgeLab/forge-admin) 最近一轮更新，直接对着这个痛点开刀。7 月以来连续 40+ 次提交，把三个方向拉了一个大版本：

1. **低代码扩展能力**——新增「扩展代码工作台」，JS/CSS 沙箱隔离运行，低代码页面也能写自定义逻辑
2. **编码规则**——从一行模板字符串，重构为分段式可视化配置引擎
3. **多标签页交互**——拖拽排序、右键全功能、未保存红点、恢复关闭，全面对齐主流 IDE

这篇文章拆解这三个方向的技术实现，不讲营销话术，直接看代码和设计思路。

---

## 一、低代码扩展能力：让"能跑"变成"能扩展"

### 1.1 先说问题在哪

大部分低代码平台的表格组件，能力边界是固定的——分页、排序、搜索，到此为止。你想要一个"金额超过 10 万的行自动标红"的需求，平台不支持，你就只能：

- 方案 A：在低代码平台外手写一个完整页面（低代码白用了）
- 方案 B： fork 平台源码，改表格组件（维护噩梦）

Forge 的答案是 **方案 C：在低代码页面上直接写扩展代码，沙箱隔离运行**。

### 1.2 扩展代码工作台：JS + CSS 双通道

新增的 `lowcode-extension` 组件目录下有三个核心文件：

```
lowcode-extension/
├── ExtensionCodeWorkbench.vue      # 工作台主组件
├── js/ExtensionSandboxHost.vue     # JS 沙箱宿主
└── css/ScopedCssPreview.vue        # CSS 隔离预览
```

**JS 扩展的隔离方案**——用 Web Worker：

```javascript
// ExtensionSandboxHost.vue 核心逻辑
function execute(script, context = {}, allowedFields = []) {
  validateClientScript(script)                    // 1. 静态校验脚本
  const safeContext = sanitizeExtensionContext(    // 2. 白名单过滤上下文
    context, allowedFields
  )
  const nonce = createNonce()                      // 3. 生成唯一 nonce

  worker = new Worker(
    new URL('./extension-sandbox.worker.js', import.meta.url),
    { type: 'module' }
  )
  // 4. 超时控制 + 输出大小限制
  // - 执行超时默认 800ms
  // - 输出上限 64KB
  // - Worker 初始化超时 5000ms
}
```

为什么用 Worker 而不是 `eval` 或 `new Function`？三个原因：

| 维度 | eval/new Function | Web Worker 沙箱 |
|------|-------------------|-----------------|
| **DOM 访问** | 能访问主线程 DOM，有 XSS 风险 | 不能访问主线程 DOM |
| **崩溃隔离** | 脚本崩溃直接卡死主线程 | Worker 崩溃不影响主应用 |
| **超时控制** | 无法强制中断 | 可以 terminate Worker |

也就是说，用户在低代码页面上写的扩展 JS，**就算写了个死循环，也不会拖垮整个后台管理系统**。这在多租户场景下尤其重要——一个租户的扩展脚本出问题，不能影响其他租户。

**CSS 扩展的隔离方案**——用 iframe + 作用域选择器：

```javascript
// ScopedCssPreview.vue
const previewDocument = computed(() => {
  return `<!doctype html>
    <html><head><style>
      body { margin: 0; padding: 16px; ... }
      ${css}                              // 用户写的 CSS
    </style></head><body>
      <div data-forge-app="${applicationCode}"
           data-forge-page="${pageCode}">  <!-- 作用域锚点 -->
        <div class="preview-card">...</div>
      </div>
    </body></html>`
})
```

`<iframe sandbox="">` 加上 `data-forge-app` / `data-forge-page` 属性锚定，CSS 样式只在自己的作用域内生效，不会污染其他低代码页面。保存时框架会自动给用户的 CSS 规则加上作用域前缀，再注入到真实页面。

### 1.3 工作台 UX：不止能写，还要好写

`ExtensionCodeWorkbench.vue` 这个组件本身的设计也有几个值得说的点：

```vue
<header class="workbench-context">
  <div class="context-main">
    <span class="language-badge">{{ modeTitle }}</span>   <!-- JS / CSS 标识 -->
    <div>
      <strong>{{ hookGuide.label }}</strong>              <!-- 钩子说明 -->
      <p>{{ hookGuide.description }}</p>                   <!-- 使用场景 -->
    </div>
  </div>
  <div class="scope-summary">
    <span>增强范围</span>
    <strong>{{ scopeDescription }}</strong>                <!-- 影响哪些组件 -->
  </div>
</header>
```

- **语言标识**：当前写的是 JS 还是 CSS，一目了然
- **钩子说明**：扩展代码注入的时机和能拿到什么上下文
- **增强范围**：这段代码影响的是整个页面还是某个组件
- **字符计数**：实时显示代码量，防止过大脚本
- **全屏编辑**：复杂扩展可以全屏写
- **示例模板**：右侧预设可用示例，一键填入

这不是一个简单的"塞个 Monaco Editor 进去"，而是把**"扩展代码的生命周期管理"**做成了产品级的体验。

### 1.4 AiCrudPage：Excel 导入零代码

低代码 CRUD 页面的数据导入一直是痛点——你要么手写后端导入接口，要么用第三方工具。Forge 这次直接把导入做成了组件：

`AiCrudImportModal.vue` 的流程是：

```
选择 Excel 文件
    ↓
浏览器本地解析预览（不上传，保护数据）
    ↓
展示前 N 行数据预览
    ↓
确认无误后点击"开始导入"
    ↓
后端校验 + 写入 + 返回错误行
```

关键设计：**预览阶段在浏览器本地完成**，用的是 SheetJS 解析 `.xlsx` / `.xls`，不把文件传到后端。只有用户确认后才上传，减少了无效的网络传输和后端解析压力。

错误行会在预览表格里高亮，用户可以直接看到哪一行哪个字段出了问题。

### 1.5 AiTable：补齐"该有但之前没有"的能力

表格组件 `AiTable.vue` 这次的改动可以用四个词概括：**拖、排、筛、应**。

```vue
<n-data-table
  remote                          <!-- 1. 远程模式 -->
  flex-height                     <!-- 2. 自适应高度 -->
  :columns="tableColumns"
  :max-height="maxHeight"
  @update:sorter="handleUpdateSorter"      <!-- 3. 远程排序 -->
  @update:filters="handleUpdateFilters"   <!-- 4. 远程筛选 -->
/>
```

```javascript
// 列定义新增
{
  resizable: col.resizable ?? props.resizable,  // 列宽拖拽，默认开启
}
```

| 能力 | 之前 | 现在 |
|------|------|------|
| 列宽调整 | 不支持 | 默认开启拖拽 |
| 排序 | 前端排序 | 远程排序，支持百万级数据 |
| 筛选 | 前端筛选 | 远程筛选 |
| 高度 | 固定高度 | `flex-height` 自适应容器 |
| 密度 | 固定 | 紧凑/默认/宽松三档切换 |
| 全屏 | 不支持 | 一键全屏查看 |
| 渲染模式 | 单一 | 表格/卡片模式切换 |

还有一个 ER 图设计器 `BusinessRelationDesigner.vue`，低代码建模时可以可视化拖线建立对象关系，字段级联一目了然，不用再对着 JSON 想象数据结构。

---

## 二、编码规则：从模板字符串到分段式引擎

### 2.1 旧方案的天花板

之前的编码规则就是一行模板字符串：

```
WL${yyyyMMdd}${seq:3}
```

简单场景够用，但遇到 MES、WMS 这种企业级场景就撑不住了：

- ❌ 流水号只支持十进制，不支持字母编号
- ❌ 没有分组计数（不同仓库各自的物料编号从 001 开始？做不到）
- ❌ 没有可视化配置，管理员得理解模板语法
- ❌ 没有实时预览，配完得保存生成才知道长啥样
- ❌ 没有容量溢出校验，3 位流水号到 999 就崩

### 2.2 新方案：分段式可视化配置引擎

重构后的编码规则变成了**主表 + 分段明细子表**的结构。一条规则由多个段组成，每段可选五种类型：

| 段类型 | 说明 | 示例 |
|--------|------|------|
| **DATE** | 日期格式 | `yyyyMMdd` → `20260718` |
| **FIXED** | 固定字元 | `WL` → `WL` |
| **SEQ** | 流水号 | 3 位十进制 → `001` |
| **VARIABLE** | 变量值（业务字段） | 仓库编码 → `WH01` |
| **SYS_VAR** | 系统变量 | 租户/用户/组织 → `T001` |

流水号（SEQ）支持**五种进制**：

| 进制 | 字符集 | 3 位容量 | 首个值 |
|------|--------|---------|--------|
| DECIMAL | 0-9 | 1,000 | 001 |
| HEX | 0-9A-F | 4,096 | 001 |
| ALPHA_UPPER | A-Z | 17,576 | AAA |
| ALPHA_LOWER | a-z | 17,576 | aaa |
| ALPHANUMERIC | 0-9A-Z | 46,656 | 001 |

还有一个细节：**去除易混淆字符**。字母 `I` 容易和数字 `1` 混，`O` 容易和 `0` 混，`Z` 和 `2` 混。开了 `excludeAmbiguous` 之后，这些字符自动跳过。这个功能在制造业场景里非常重要——产线扫码枪识别错误一个字符，整批料就错了。

### 2.3 分组计数：让不同业务各自独立编号

这是企业级场景最需要的能力。看一个实际例子：

```
规则：仓库物料编码
分段配置：
  段1: VARIABLE(仓库编码)  → 标记为"前置码"（分组依据）
  段2: FIXED("-")           → 固定连接符
  段3: DATE(yyyy)           → 年份
  段4: SEQ(3位十进制)       → 流水号，按前置码分组
```

生成结果：

```
WH01-2026-001    ← WH01 仓库的第 1 个
WH01-2026-002    ← WH01 仓库的第 2 个
WH02-2026-001    ← WH02 仓库独立从 001 开始
WH02-2026-002
WH01-2026-003    ← WH01 继续
```

**不同仓库各自的流水号独立计数，互不干扰。** 这个在旧模板字符串方案里根本做不到。

分组 key 的构建也很有讲究——不是简单拼字符串，而是按段顺序解析后做 **SHA-256 摘要**，避免分隔碰撞和数据库 key 超长。

### 2.4 周期重置：按年/月/日/时自动归零

流水号可以按时间周期自动重置：

```yaml
resetEnabled: true
resetPolicy: MONTH    # YEAR / MONTH / DAY / HOUR
```

- `YEAR`：每年 1 月 1 日归零
- `MONTH`：每月 1 日归零
- `DAY`：每天 0 点归零
- `HOUR`：每小时整点归零

周期重置使用应用配置时区，以注入 `Clock` 的服务端时区为基线，保证多实例部署时行为一致。

### 2.5 实时预览：所见即所得

这个功能是管理员最喜欢的——配完分段，**立刻看到生成的编码长什么样**，不需要保存、不需要生成、不消耗真实流水号。

```
你配置：
  段1: FIXED("WL")
  段2: DATE(yyyyMMdd)
  段3: SEQ(3位，ALPHA_UPPER)

预览结果：
  WL20260718AAA    ✓ 长度: 13
  WL20260718AAB    ✓ 下一个
```

预览接口和真实生成接口是分开的，**预览不访问真实计数器**，用 `startValue` 作为示例序号。而且预览请求会取消上一条（防抖），不会因为频繁调整配置而打爆后端。

### 2.6 安全设计：业务字段不能伪造系统变量

编码规则的安全边界设计得很克制：

| 变量来源 | 谁能提供 | 风险 |
|---------|---------|------|
| **SYS_VAR** | 只从可信 Session 读取 | 业务代码不能覆盖 |
| **VARIABLE** | 从业务 `fields` 取值 | 调用方显式传值 |
| **SEQ** | 服务端号段分配 | 防伪防重 |

旧实现里 `resolveTenantId` 在上下文缺失时回退租户 1，新规则改成**缺失即失败**，不允许业务代码伪造租户、用户或组织系统变量。这在多租户场景下是必须的——否则一个租户的业务代码可以伪造系统变量，给另一个租户生成编号。

### 2.7 平滑迁移：老规则无缝升级

这次重构最让人头大的问题是——**已有的低代码业务对象的自动编号字段，怎么保证不断号？**

Forge 的迁移方案分三层：

1. **旧 `ai_code_rule` 全量迁移**：已知模板由 SQL 回填，未知模板由 legacy parser 兼容物化
2. **旧号段水位兼容**：旧水位高于新配置宽度时，按旧安全起点最小扩宽，不回退水位
3. **legacy 兼容开关**：存量规则默认 `legacy_compat_enabled=1`，新建规则关闭，逐步收敛

迁移后，低代码字段自动编号继续读取 `generation.ruleCode`，老项目无感知。

---

## 三、多标签页交互：全面对齐主流 IDE

### 3.1 拖拽排序 + 分组分离

`tab/index.vue` 用 `VueDraggable` 实现了标签拖拽排序：

```vue
<VueDraggable
  class="top-tab-list"
  :model-value="tabStore.tabs"
  :animation="180"                    <!-- 拖拽动画 180ms -->
  :move="canMoveTab"                  <!-- 拖拽可行性判断 -->
  ghost-class="top-tab-drag-ghost"
  chosen-class="top-tab-drag-chosen"
  drag-class="top-tab-dragging-item"
  @update:model-value="handleTabReorder"
>
```

标签分两个组：**固定标签** 和 **普通标签**。拖拽时 `canMoveTab` 会判断目标位置是否允许——固定标签始终在左侧，普通标签不能拖到固定标签前面。视觉上两组之间有分隔线，一眼能分清。

### 3.2 右键菜单：11 个操作，分 4 组

`ContextMenu.vue` 的菜单结构是：

```
├── 重新加载
├── 固定 / 取消固定
├── 恢复刚关闭的标签
├── ──────────────
├── 页面操作
│   ├── 复制页面地址
│   ├── 复制页面名称和地址
│   └── 在新窗口打开
└── 标签整理
    ├── 移动到最左侧
    ├── 移动到最右侧
    ├── 固定全部
    ├── 取消全部固定
    ├── 刷新其他标签
    ├── 关闭其他
    ├── 关闭左侧
    └── 关闭右侧
```

每个菜单项都有**可用性判断**：

```javascript
const canCloseCurrentTab = computed(() => {
  if (!currentTab.value || isCurrentTabPinned.value || currentTab.value.closable === false)
    return false
  return Boolean(currentTab.value.forceClosable || tabStore.tabs.length > 1)
})
const hasClosableLeftTabs = computed(() =>
  tabStore.tabs.some((item, index) => index < currentTabIndex.value && !item.pinned)
)
```

比如"关闭左侧"在左侧没有可关闭标签时自动禁用，"固定全部"在所有标签都已固定时禁用。不是简单的一堆按钮，而是**根据上下文智能启用/禁用**。

### 3.3 未保存提示：防止误关丢数据

标签有 `dirty` 状态（未保存）时显示红点：

```vue
<div
  class="top-tab-item"
  :class="{
    'is-active': item.path === tabStore.activeTab,
    'is-pinned': item.pinned,
    'is-dirty': item.dirty,              <!-- 未保存红点 -->
  }"
>
```

关闭有 `dirty` 状态的标签时，会弹确认框：

```javascript
// tab-interactions.js
export function confirmDirtyTabs(tabs, actionLabel = '继续操作') {
  const dirtyTabs = tabs.filter(tab => tab.dirty)
  if (dirtyTabs.length === 0) return true
  // ... 弹窗确认
}
```

**恢复刚关闭的标签**也是个高频功能——手滑关错了，右键一键恢复，不用重新从菜单找。

### 3.4 滚动体验：滚轮横向 + 溢出阴影

标签多了之后，支持滚轮横向滚动，而且边界有渐变阴影提示：

```vue
<div
  :class="{
    'has-tab-overflow-start': canScrollLeft,     <!-- 左侧有内容 -->
    'has-tab-overflow-end': canScrollRight,       <!-- 右侧有内容 -->
  }"
  @wheel.capture="handleTabWheel"
  @scroll="syncTabOverflowState"
>
```

这些细节看起来小，但对每天在后台开十几个标签页的人来说，体验提升是实打实的——不需要精确点到那个小小的关闭按钮，不需要因为标签太多而找不到页面，不需要因为手滑关闭而重新填一遍表单。

---

## 四、技术栈一览

| 层 | 技术 | 版本 |
|----|------|------|
| 前端框架 | Vue 3 | 3.5 |
| UI 组件库 | Naive UI | 2.42 |
| 构建工具 | Vite | 7 |
| 状态管理 | Pinia | 3 |
| 原子化 CSS | UnoCSS | 66 |
| 后端框架 | Spring Boot | 3.2 |
| ORM | MyBatis-Plus | 3.5 |
| 认证授权 | Sa-Token | 1.38 |
| 流程引擎 | Flowable | 7.0 |
| 数据库 | MySQL | 8.0+ |
| 缓存 | Redis | 6.0+ |

---

## 五、快速体验

```bash
# 在线演示（直接打开即用）
http://www.dlforgelab.com:8084/forge/login
# 账号：admin  密码：123456

# 本地启动
git clone https://gitee.com/ForgeLab/forge-admin.git
cd forge-admin-ui && pnpm install && pnpm dev
```

进去后重点体验：

1. **扩展代码工作台**：打开任意低代码页面 → 编辑 → 扩展代码 → 写一段 JS/CSS
2. **编码规则配置**：系统管理 → 编码管理 → 新增规则 → 分段配置 → 实时预览
3. **多标签页**：打开几个页面 → 右键标签 → 试试拖拽、固定、恢复关闭

---

## 写在最后

回到开头的问题——低代码的天花板到底在哪里？

Forge 这轮更新给出的答案是：**天花板不在平台能做什么，而在于平台做不到的时候，你有没有出路。**

- 低代码页面不够用？→ 写扩展代码，沙箱隔离，不影响主应用
- 编码规则太简单？→ 分段式引擎，5 种段类型 + 5 种进制 + 分组计数
- 交互体验不够好？→ 标签页全面对齐 IDE 级别

**低代码的终局不是消灭代码，而是消灭重复代码。把选择权还给开发者——简单场景配置搞定，复杂场景代码扩展，两者无缝共存。**

如果你也在做企业级中后台开发，被重复的 CRUD 和编码规则配置折磨过，不妨试试 [Forge Admin](https://gitee.com/ForgeLab/forge-admin)。

> **项目地址**：[https://gitee.com/ForgeLab/forge-admin](https://gitee.com/ForgeLab/forge-admin)
>
> **在线演示**：[https://www.dlforgelab.com:8084/forge/login](https://www.dlforgelab.com:8084/forge/login)
>
> **GitHub 镜像**：[https://github.com/yaomindong1996/forge-admin](https://github.com/yaomindong1996/forge-admin)

---

*觉得有帮助的话，点个赞收藏起来，也欢迎去项目仓库点个 Star ⭐️ 支持一下~*

*你对低代码的扩展能力有什么看法？是"平台内置能力够用就行"还是"必须支持自定义代码扩展"？评论区聊聊。*
