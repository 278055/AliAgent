# AliAgent UI 优化方案

> 参考: ChatGPT 浅色界面  
> 目标: 以纯白主界面为核心，建立简洁、克制、有轻微阴影层次的 AI 对话体验  
> 日期: 2026-06-19

---

## 1. 设计方向

本次 UI 改版不再以深色品牌区或强视觉装饰为主，而是参考 ChatGPT 的浅色工作台风格：

- 页面主色为白色，避免大面积渐变、毛玻璃、彩色发光和装饰性背景。
- 左侧侧边栏使用浅灰背景，主聊天区保持纯白。
- 聊天内容居中，消息区留足空白，输入框固定在底部并带柔和阴影。
- 用户消息使用浅蓝气泡，AI 消息使用无背景或极浅灰文本块。
- 登录页也改为白色为主，使用居中卡片和轻阴影，而不是左右强对比品牌区。
- 阴影只用于浮层、输入框、弹窗、登录卡片等需要明确层级的地方。

整体关键词：**纯白、轻灰、浅蓝、圆角、轻阴影、信息优先**。

---

## 2. 当前问题

| 问题 | 位置 | 调整方向 |
|------|------|----------|
| 登录页视觉过重 | AuthShell | 改为白色背景 + 居中登录卡片 |
| 深色品牌区不符合目标 | AuthShell | 删除固定深色品牌面板 |
| 对话页装饰过多 | ChatShell / ChatArea | 删除渐变、光效、装饰背景 |
| 聊天区不像主流 AI 产品 | MessageBubble / InputArea | 改为居中消息流 + 底部悬浮输入框 |
| 阴影层级不统一 | 全局 | 定义统一 shadow token，仅在关键组件使用 |
| 主题复杂度过高 | main.css | 浅色为默认主视觉，暗色只做可读兼容 |

---

## 3. 页面结构参考

```text
┌──────────────────────────────────────────────────────────────┐
│ Sidebar      │ Header                                        │
│ 浅灰背景      │ Chat title / actions                          │
│              ├───────────────────────────────────────────────┤
│ 新对话        │                                               │
│ 搜索/知识库    │         居中消息流 max-width: 760px            │
│              │                                               │
│ 最近会话列表   │                 user 浅蓝气泡                 │
│              │                 assistant 正文                 │
│              │                                               │
│ 用户信息/退出  │         底部悬浮输入框 max-width: 780px         │
└──────────────────────────────────────────────────────────────┘
```

### 关键尺寸

| 区域 | 建议值 |
|------|--------|
| Sidebar 宽度 | 256px |
| Header 高度 | 56px |
| 消息内容最大宽度 | 760px |
| 输入框最大宽度 | 780px |
| 输入框圆角 | 24px |
| 用户气泡圆角 | 18px |
| 页面基础间距 | 12px / 16px / 24px |

---

## 4. 全局视觉变量

### 4.1 浅色主题为默认

```css
:root,
html[data-theme='light'] {
  --bg-page: #ffffff;
  --bg-sidebar: #f7f7f8;
  --bg-sidebar-hover: #ececf1;
  --bg-surface: #ffffff;
  --bg-surface-muted: #f7f7f8;
  --bg-message-user: #e7f2ff;
  --bg-message-assistant: transparent;

  --text-primary: #111827;
  --text-secondary: #4b5563;
  --text-tertiary: #8a8f98;
  --text-inverse: #ffffff;

  --border-subtle: #e5e7eb;
  --border-strong: #d1d5db;

  --accent: #2563eb;
  --accent-hover: #1d4ed8;
  --accent-soft: #eff6ff;

  --danger: #dc2626;
  --danger-soft: #fef2f2;

  --shadow-xs: 0 1px 2px rgba(0, 0, 0, 0.05);
  --shadow-sm: 0 4px 12px rgba(0, 0, 0, 0.08);
  --shadow-md: 0 12px 32px rgba(0, 0, 0, 0.10);
  --shadow-input: 0 8px 30px rgba(0, 0, 0, 0.10);
}
```

### 4.2 暗色只做兼容

暗色主题保留，但不是本次主视觉。不要再为暗色主题设计大面积蓝紫渐变或霓虹效果。

```css
html[data-theme='dark'] {
  --bg-page: #202123;
  --bg-sidebar: #171717;
  --bg-sidebar-hover: #2f2f2f;
  --bg-surface: #2b2c2f;
  --bg-surface-muted: #343541;
  --bg-message-user: #2f4056;
  --bg-message-assistant: transparent;

  --text-primary: #f3f4f6;
  --text-secondary: #d1d5db;
  --text-tertiary: #9ca3af;
  --text-inverse: #ffffff;

  --border-subtle: rgba(255, 255, 255, 0.10);
  --border-strong: rgba(255, 255, 255, 0.16);

  --accent: #3b82f6;
  --accent-hover: #60a5fa;
  --accent-soft: rgba(59, 130, 246, 0.14);

  --danger: #f87171;
  --danger-soft: rgba(248, 113, 113, 0.14);

  --shadow-xs: 0 1px 2px rgba(0, 0, 0, 0.30);
  --shadow-sm: 0 4px 12px rgba(0, 0, 0, 0.28);
  --shadow-md: 0 12px 32px rgba(0, 0, 0, 0.36);
  --shadow-input: 0 8px 30px rgba(0, 0, 0, 0.35);
}
```

### 4.3 删除旧视觉元素

需要删除或停止使用：

- `.orb`
- `.bg-noise`
- `.constellation`
- `.star`
- `.aurora-*`
- 彩色渐变背景
- 彩色发光阴影
- 大面积毛玻璃
- 复杂背景动画

---

## 5. 登录页方案

### 5.1 布局

登录页改成纯白工作台风格：

```text
┌──────────────────────────────────────┐
│                                      │
│              AliAgent                │
│       简洁的 AI 知识库助手            │
│                                      │
│        ┌────────────────────┐        │
│        │ 登录 / 注册 Tab     │        │
│        │ 用户名              │        │
│        │ 密码                │        │
│        │ 登录按钮            │        │
│        └────────────────────┘        │
│                                      │
└──────────────────────────────────────┘
```

### 5.2 具体要求

| 项 | 方案 |
|----|------|
| 页面背景 | `#ffffff`，可加极淡灰底 `#fafafa` |
| 登录卡片 | 白色卡片，宽度 400-440px，圆角 16px |
| 阴影 | `--shadow-md`，透明度克制 |
| Logo 区 | 顶部居中，使用 AliAgent 文本或简单图标 |
| 标题 | `AliAgent`，28px 左右，字重 600 |
| 副标题 | 灰色小字，不超过一行 |
| Tab | 下划线或浅灰 segmented control |
| 输入框 | 高度 44px，圆角 10px，边框浅灰 |
| 主按钮 | 蓝色实心按钮，高度 44px，圆角 10px |
| 主题切换 | 可移到右上角，小图标按钮 |

不再保留左右分栏品牌区，也不再使用固定深色背景。

---

## 6. 聊天页方案

### 6.1 ChatShell

| 区域 | 方案 |
|------|------|
| 页面背景 | 主区域纯白 |
| Sidebar | 浅灰 `--bg-sidebar` |
| Header | 白色，固定顶部，高度 56px |
| 主体 | 消息流居中，底部留出输入框空间 |
| 分割线 | 只在 sidebar 右侧使用极浅边线 |

### 6.2 Sidebar

| 项 | 方案 |
|----|------|
| 背景 | `#f7f7f8` |
| 顶部 | Logo + 折叠按钮 |
| 功能入口 | 新对话、搜索聊天、知识库 |
| 会话列表 | 纯文本列表，hover 使用 `#ececf1` |
| 当前会话 | 浅灰块，圆角 8px |
| 底部 | 用户信息、在线状态、退出 |
| 阴影 | 默认不用阴影，只靠边线区分 |

### 6.3 ChatArea

| 项 | 方案 |
|----|------|
| 内容宽度 | `max-width: 760px` |
| 消息流 | 居中，顶部留 24px |
| 空状态 | 居中显示 `AliAgent` + 2-4 个建议问题 |
| 思考状态 | 灰色小字或三个点，不使用发光动画 |
| 底部提示 | `AliAgent 可能会出错，请核实重要信息` |

### 6.4 MessageBubble

用户消息参考截图中的浅蓝气泡：

```css
.message.user .bubble {
  max-width: 70%;
  margin-left: auto;
  background: var(--bg-message-user);
  border-radius: 18px;
  padding: 12px 16px;
  color: var(--text-primary);
}

.message.assistant .bubble {
  max-width: 100%;
  background: transparent;
  padding: 0;
  color: var(--text-primary);
}
```

| 类型 | 方案 |
|------|------|
| 用户消息 | 右对齐，浅蓝背景，圆角 18px |
| AI 消息 | 左对齐，默认无背景，正文自然排版 |
| 代码块 | 极浅灰卡片或深色代码块，带复制按钮 |
| RAG 来源 | 放在 AI 回复下方，使用浅灰卡片 |
| 操作按钮 | 复制、点赞、重新生成等小图标，灰色 hover |

### 6.5 InputArea

输入框是聊天页视觉重点，参考截图做底部悬浮白色圆角框：

| 项 | 方案 |
|----|------|
| 位置 | 底部固定，居中 |
| 宽度 | `max-width: 780px` |
| 背景 | 白色 |
| 边框 | `1px solid var(--border-subtle)` |
| 阴影 | `--shadow-input` |
| 圆角 | 24px |
| 内边距 | 12px 16px |
| 附件按钮 | 左下角 `+` 或回形针图标 |
| 发送按钮 | 右下角蓝色圆形按钮 |
| 输入高度 | 单行 48px，多行最高 180px |

---

## 7. 知识库与来源卡片

### 7.1 KnowledgeBase

知识库面板仍然从右侧滑出，但视觉改为白色抽屉：

| 项 | 方案 |
|----|------|
| 面板背景 | 白色 |
| 宽度 | 420px |
| 阴影 | `--shadow-md` |
| 标题区 | 白底 + 底部分割线 |
| 上传区 | 浅灰虚线框 |
| 文档列表 | 行列表，不用大卡片堆叠 |
| 删除按钮 | 图标按钮，hover 时显示危险色 |

### 7.2 RAGSourceCard

| 项 | 方案 |
|----|------|
| 背景 | `#f7f7f8` |
| 边框 | `1px solid var(--border-subtle)` |
| 圆角 | 10px |
| 强调 | 左侧 3px 蓝色细线 |
| 分数 | 右上角小号灰字或蓝色 badge |
| 展开 | 点击后显示片段全文 |

不再使用环形相似度、彩色等级、发光 hover。

---

## 8. 组件级改动清单

| 文件 | 改动 |
|------|------|
| `frontend/src/assets/main.css` | 重建浅色变量，删除 aurora / orbs / noise / 复杂动画 |
| `frontend/src/components/AuthShell.vue` | 改为白色居中登录卡片 |
| `frontend/src/components/ChatShell.vue` | 主布局改为白色工作台 |
| `frontend/src/components/Sidebar.vue` | 浅灰侧栏，简化菜单和会话列表 |
| `frontend/src/components/ChatArea.vue` | 消息流居中，保留底部输入空间 |
| `frontend/src/components/WelcomeScreen.vue` | 简洁居中欢迎态 |
| `frontend/src/components/MessageBubble.vue` | 用户浅蓝气泡，AI 透明正文 |
| `frontend/src/components/InputArea.vue` | 底部悬浮输入框，白底轻阴影 |
| `frontend/src/components/KnowledgeBase.vue` | 白色右侧抽屉 |
| `frontend/src/components/FileUploader.vue` | 简洁虚线上传框 |
| `frontend/src/components/RAGSourceCard.vue` | 浅灰来源卡片 |
| `frontend/src/components/ToastContainer.vue` | 白底 toast，轻阴影 |

---

## 9. 阴影使用规则

阴影要适当，不能让页面显得油腻。

| 层级 | 使用位置 | token |
|------|----------|-------|
| 极轻 | 按钮 hover、小菜单 | `--shadow-xs` |
| 轻 | Toast、ContextMenu、小浮层 | `--shadow-sm` |
| 中 | 登录卡片、知识库抽屉、弹窗 | `--shadow-md` |
| 输入框 | 聊天底部输入框 | `--shadow-input` |

禁止给普通消息列表、sidebar 会话项、页面背景加大阴影。

---

## 10. 响应式方案

| 断点 | 方案 |
|------|------|
| `>= 1024px` | Sidebar 固定 256px，聊天区居中 |
| `768px - 1023px` | Sidebar 可折叠，聊天区宽度自适应 |
| `< 768px` | Sidebar 变抽屉，输入框左右间距 12px |
| `< 480px` | 用户气泡最大宽度 86%，登录卡片贴近全宽 |

移动端输入框不要遮挡最后一条消息，ChatArea 底部 padding 至少 120px。

---

## 11. 执行计划

### Phase A: 全局样式

1. 重写 `main.css` 变量，浅色为默认主视觉。
2. 删除装饰性背景、光效、复杂动画。
3. 统一按钮、输入框、滚动条、Markdown 样式。
4. 定义阴影 token 和基础圆角规则。

### Phase B: 登录页

1. 删除左右品牌分栏。
2. 新增白色居中登录卡片。
3. 登录/注册切换改为简洁 tab。
4. 输入框、按钮、错误提示统一浅色样式。

### Phase C: 聊天主界面

1. ChatShell 改为浅灰 sidebar + 白色主区域。
2. Sidebar 简化为 ChatGPT 风格列表。
3. ChatArea 消息流居中。
4. MessageBubble 改为用户浅蓝气泡、AI 透明正文。
5. InputArea 改为底部悬浮白色输入框。

### Phase D: RAG 相关组件

1. KnowledgeBase 改为白色右侧抽屉。
2. FileUploader 改为浅灰虚线上传区域。
3. RAGSourceCard 改为浅灰来源卡片。

### Phase E: 验证

1. 登录页浅色下无白屏、无低对比文字。
2. 聊天页截图与参考图整体一致：白色主区、浅灰侧栏、底部悬浮输入框。
3. 用户长消息、代码块、RAG 来源不会撑破布局。
4. 移动端 sidebar、输入框、消息气泡不重叠。
5. 执行 `npm run build` 和必要的前端类型检查。

---

## 12. 验收标准

- 打开应用第一眼是白色、干净、类似 ChatGPT 的聊天工作台。
- 登录页以白色为主，不再出现深色品牌大面板。
- 聊天输入框有柔和阴影，悬浮感明显但不夸张。
- 用户消息是浅蓝气泡，AI 回复是自然正文。
- Sidebar 是浅灰背景，列表简洁，不再有彩色装饰条。
- 所有组件在浅色主题下优先保证可读性。
- 深色主题可用，但不是主设计目标。
