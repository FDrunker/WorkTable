# 军师知识库（goutoujunshi）放置说明

本目录是 [shengjidaguai-china/goutoujunshi](https://github.com/shengjidaguai-china/goutoujunshi) 的**运行时副本**位置。

## 为什么需要手动放置

goutoujunshi **没有可执行的 skill 运行时**。它是一个 Markdown 知识库：

- `SKILL.md` —— 行为与路由内核（约 9.5KB），定义情绪落地、事实拆分、利益判断、明确建议、行动收束这五步流程，以及安全边界
- `references/knowledge/*.md` —— 20 篇跨学科知识（依恋理论、MBTI、冲突修复、中国法律安全等）
- `references/practical/*.md` —— 20+ 篇实用指南（话术编排、场景校准、投入失衡等）
- `scripts/memory_store.py` / `scripts/validate_skill.py` —— **不要拷贝**。前者是 Python 记忆存储，本模块的记忆应落在数据库；后者是仓库自检脚本

原本这些文档由会推理的 Agent 自行"按需加载"。在 Java 侧，我们把这件事拆成两步：

1. `SkillRoutingService` 用一次轻量 LLM 调用，读路由表后从目录中挑 1-3 篇（返回路径经白名单过滤）
2. `SkillPromptBuilder` 把选中的文档注入主分析的 system prompt

## 需要的目录结构

```
src/main/resources/skill/
├── SKILL.md                 # 必需
├── routing.md               # 可选。SKILL.md 内路由表的抽取版，缺失时退回 SKILL.md 全文
├── LICENSE                  # 建议保留原始 MIT 许可证与版权声明
└── references/
    ├── knowledge/
    │   ├── 01-证据分级与内容边界.md
    │   ├── 02-亲密关系心理学总论.md
    │   └── ...（共 20 篇）
    └── practical/
        ├── 00-导读与使用分级.md
        ├── 实战话术编排器：从一句回复到后续分支.md
        └── ...（共 20+ 篇）
```

## 获取方式

```bash
# 在临时目录克隆后拷贝，避免把整个仓库（含示例图片）带进来
git clone --depth 1 https://github.com/shengjidaguai-china/goutoujunshi.git
```

然后只拷贝 `SKILL.md`、`LICENSE` 与 `references/` 三项。

## 缺失时的行为

刻意设计为**不阻断**：

| 情况 | 行为 |
|---|---|
| `SKILL.md` 缺失 | 应用正常启动，日志告警；分析时 system prompt 会提示"知识库不可用"，要求模型保守作答 |
| `routing.md` 缺失 | 仅记录 info 日志，`SKILL.md` 全文照常注入 |
| `references/` 为空 | 跳过路由步骤，只加载 `SKILL.md` 行为内核 |

这样数据链路（取数、清洗、映射）可以在知识库尚未就位时先行验证。

## 许可证

goutoujunshi 采用 MIT 协议，允许商用、修改与分发，**分发时须保留版权与许可声明**。
因此拷贝内容时请一并保留 `LICENSE` 文件。
