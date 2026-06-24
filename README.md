# 🎀 CraftMaid (环境感知型 AI 女仆)

CraftMaid 是一款为 Minecraft (Paper/Spigot) 设计的轻量级 AI 陪伴插件。
它通过生成一个具有**游戏环境感知能力**的 NPC，结合本地大语言模型（如 Qwen），为玩家提供沉浸式的陪伴体验。

## ✨ 核心特性

* **零冗余依赖**：纯 Java 编写，利用自带的 HttpClient 与服务端底层 Gson。
* **环境态势感知**：不仅能聊天，还能感知游戏内的时间、天气以及周围的实体与怪物。
* **身份级联控制**：基于配置文件设定“主人”身份，AI 将根据对话者的身份切换不同的语气与态度。
* **极简部署**：直接在 `config.yml` 中配置兼容 OpenAI 格式的 LLM API 即可运行。

## 📦 前置要求

* **服务端核心**：Paper 26.1.2+ (Java 25+)
* **可选前置插件**：[Citizens2](https://wiki.citizensnpcs.co/Citizens_Wiki) (用于生成肉身实体；未安装时聊天功能仍可使用)
  * Citizens 官方下载页：[Downloads](https://wiki.citizensnpcs.co/Downloads)
  * Spigot release builds：[Citizens on Spigot](https://www.spigotmc.org/resources/citizens.13811/)
  * Jenkins dev builds：[Citizens2 Jenkins](https://ci.citizensnpcs.co/job/Citizens2/)；新版本 Paper 通常优先看这里的最新 successful build。
* **可选扩展插件**：Sentinel / Denizen 用于护卫、战斗、脚本演出、钓鱼等扩展能力；未安装时不影响基础 AI 对话。
  * [Sentinel](https://www.spigotmc.org/resources/sentinel.22017/)：Citizens 的战斗 NPC 扩展，当前已用于右键菜单里的基础护卫能力。Jenkins dev builds：[Sentinel Jenkins](https://ci.citizensnpcs.co/job/Sentinel/)。
  * [Denizen](https://denizenscript.com/)：Citizens 生态脚本引擎，适合后续做 NPC 演出、脚本任务、钓鱼原型等。稳定构建：[Denizen Jenkins](https://ci.citizensnpcs.co/job/Denizen/)；开发构建：[Denizen Developmental Jenkins](https://ci.citizensnpcs.co/job/Denizen_Developmental/)。
  * Denizen 当前仍未接入；“去钓鱼”等按钮只是为后续扩展预留。
* **AI 算力**：兼容 OpenAI API 格式的 LLM 服务接口 (如本地部署的 vLLM、Ollama 或云端 API)

## 🛠️ 编译与构建

本项目使用 Maven 进行包管理。
当前构建目标为 Paper API `26.1.2.build.72-stable` 与 Java 25。

```bash
git clone https://github.com/quantumxiaol/CraftMaid.git
cd CraftMaid
mvn clean package
```
编译完成后，将 `target/CraftMaid-1.0-SNAPSHOT.jar` 放入服务端的 `plugins` 文件夹下。
提交代码前可以运行 `mvn spotless:apply` 自动整理 Java 格式；CI 会执行 `mvn -B spotless:check` 和 `mvn -B clean package`。

## 🚀 GitHub Actions 自动发布

仓库包含 `.github/workflows/build-release.yml`：

* 每次 push / pull request 会自动执行 `mvn -B spotless:check` 和 `mvn -B clean package`，并把构建出的 jar 作为 workflow artifact 上传。
* 推送 `v*` tag 时，会自动创建或更新 GitHub Release，并上传 `CraftMaid-<tag>.jar`。

发布新版本：
```bash
git tag v1.0.0
git push origin v1.0.0
```


## 📖 使用说明

### 1. 配置模型
首次启动插件后，会在 `plugins/CraftMaid` 目录下生成 `config.yml`，打开并配置你的模型信息：
```yaml
llm:
  base_url: "https://api.openai.com/v1/chat/completions" # 兼容 OpenAI 格式的 API 根地址或 chat/completions 完整地址
  api_key: "your-api-key-here" # 你的 API Key
  model_name: "gpt-3.5-turbo" # 你的模型名称，如 qwen-max
  timeout_seconds: 30
  temperature: 0.7
  max_tokens: 180
maid:
  name: "露西" # 女仆在游戏内的名字
  master: "PlayerName" # 你的游戏 ID，女仆会对该玩家展现主人的态度
  language: "中文"
  skin: "master" # NPC 皮肤：master=使用主人皮肤；player=使用触发者皮肤；none/default=不主动设置；也可直接填玩家名
  combat:
    enemy_drops: true # Sentinel 护卫击杀敌怪时允许掉落物
    enemy_exp: true # NPC 击杀敌怪且死亡经验为 0 时补基础经验
    default_enemy_exp: 5
  system_prompt: |-
    这里可以自定义女仆的人设、称呼习惯、说话风格和行为边界。
    可用占位符：{name}、{master}、{language}。占位符是可选的，完全重写后不包含占位符也可以。
chat:
  cooldown_seconds: 3 # 同一玩家触发 AI 回复的冷却时间
  followup_seconds: 180 # 喊过女仆名字后，同一玩家后续发言免唤醒的滑动窗口；0 表示关闭
  max_context_entities: 8 # 写入提示词的周围实体数量上限
  reply_prefix: "[{name}] "
conversation:
  enabled: true
  max_messages: 100 # 超过 N 后调用同一个 LLM 接口压缩为 Memory，并只保留最近 N/5 条原始历史
  max_message_chars: 1200 # 单条历史消息最长字符数
  max_memory_chars: 4000 # 压缩后的长期 Memory 最长字符数
  summary:
    max_tokens: 900 # Memory 压缩请求的 token 上限
    temperature: 0.2 # Memory 压缩请求的温度
  persist:
    enabled: false # true 时保存到 plugins/CraftMaid/conversations.json
    file: "conversations.json"
```
修改完成后，在游戏中输入 `/craftmaid reload` 即可热重载配置。

DeepSeek 示例：
```yaml
llm:
  base_url: "https://api.deepseek.com"
  api_key: "你的 DeepSeek API Key"
  model_name: "deepseek-v4-flash"
  timeout_seconds: 30
  temperature: 0.7
  max_tokens: 180
conversation:
  enabled: true
  max_messages: 100
  summary:
    max_tokens: 900
    temperature: 0.2
  max_memory_chars: 4000
  persist:
    enabled: false
```

超过 `conversation.max_messages` 后，插件会把较旧的对话连同已有 Memory 发给 DeepSeek/兼容 OpenAI 的接口，要求模型压成结构化摘要：`玩家偏好`、`已承诺事项`、`世界状态`、`重要关系`、`最近目标`。压缩成功后只保留这份 Memory 和最近 `N/5` 条原始历史；压缩失败时会保留原始历史，不会提前删除。

如果你的服务端已经生成过旧版 `plugins/CraftMaid/config.yml`，新字段不会自动写入旧文件。需要手动补上 `maid.skin`、`maid.combat`、`conversation.summary.max_tokens` 和 `conversation.summary.temperature`，或者备份后删除旧配置让插件重新生成。

### 2. 生成女仆
确保已安装 **Citizens** 插件。房主（需拥有 `craftmaid.admin` 权限或 OP）在游戏内输入：
```
/craftmaid spawn
```
会在你当前的位置生成一个拥有配置文件中名字的肉身女仆 NPC。再次执行会把已记录的女仆移动到当前位置，不会重复堆出多个 NPC。

当前插件创建 `EntityType.PLAYER` 类型 NPC，并把显示名设置为 `maid.name`。默认 `maid.skin: "master"` 会尝试使用 `maid.master` 对应玩家的皮肤；也可以把 `maid.skin` 改成任意玩家名，或设为 `none` / `default` 让 Citizens 自己处理外观。

右键已生成的女仆 NPC 会打开 CraftMaid 菜单。当前菜单已支持：

* 查看状态
* 召回到身边
* 设置 home
* 回家
* 看向我
* 打开背包
* 配置装备
* 跟随我 / 别跟了
* 保护我 / 停止护卫 / 守在这里（需要 Sentinel）
* 关闭菜单

“打开背包”使用 Citizens 的 Inventory trait；“配置装备”使用 Citizens 的 Equipment trait，可以设置主手、副手和护甲显示。“去钓鱼”目前仍是占位提示，后续会接 Denizen 原型或 CraftMaid job。菜单里的控制动作只允许 `maid.master` 或拥有 `craftmaid.admin` 权限的玩家执行。

护卫战斗里，`maid.combat.enemy_drops: true` 会在 Sentinel 护卫初始化时打开敌怪掉落；`maid.combat.enemy_exp: true` 会在女仆击杀敌怪但服务端给出 0 经验时补 `default_enemy_exp` 点经验。已经处于护卫状态的旧 NPC 需要重新点击一次“保护我”或“守在这里”，让新设置写入 Sentinel trait。

移除已记录的女仆 NPC：
```
/craftmaid despawn
```

跟随控制：
```
/follow start
/follow stop
/maid follow start
/maid follow stop
```

清空对话历史：
```
/craftmaid forget          # 清空自己的历史
/craftmaid forget Player   # 清空指定玩家历史
/craftmaid forget all      # 清空全部历史
```

### 3. 与女仆对话
在公屏聊天中，只要你的话语包含女仆的名字（如：`露西`），她就会回复你：
* **玩家**：“露西，你觉得这里怎么样？”
* **露西**：“[女仆 露西] 报告主人！这里正在下雨，而且你周围有 2 个僵尸，请小心哦！”

喊过一次女仆名字后，默认 180 秒内同一玩家继续说话不需要再带名字，女仆也会接着回复。这个窗口按玩家最后一次发言滑动续期；如果想恢复成每句都必须喊名字，把 `chat.followup_seconds` 设为 `0`。

多轮对话按玩家 UUID 分开保存。默认只保存在内存里，服务器重启后清空；如果你希望重启后继续沿用上下文，开启 `conversation.persist.enabled`。
