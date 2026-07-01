# CraftMaid

CraftMaid 分成两层能力。

第一层是轻量的服务器 AI 对话插件：只要配置一个兼容 OpenAI 的 LLM 接口，玩家就可以在公屏和女仆对话。它会带上游戏环境上下文、多轮记忆和主人/客人身份，不强制要求安装 NPC 插件。

第二层是可选的实体女仆：安装 Citizens 后可以生成女仆 NPC，右键打开菜单；再安装 Sentinel 后可以让 NPC 跟随、护卫、战斗和守点。后续的工作能力会继续沿着 NPC 行为服务扩展。

当前版本仍然不是完整 Minecraft Agent：LLM 可以通过受限 JSON 选择少量工作 action，但不会直接执行服务器命令或开放式工具调用；锚点、区域和第一版 Job 系统已经可用，钓鱼、农田收割和 ChunkKeeper 仍是 MVP，箱子整理还没有实现。

## 当前能力

* **AI 对话**：玩家在公屏提到女仆名字后触发回复；喊过一次名字后，默认 180 秒内同一玩家可以继续免唤醒对话。
* **环境上下文**：对话时会采集玩家周围的时间、天气、附近实体和怪物等信息写入提示词。
* **多轮记忆**：按玩家 UUID 管理历史；只记录玩家原话和女仆最终回复。超过 `conversation.max_messages` 后调用 LLM 压缩成结构化 Memory，并保留最近 `N/5` 条原始历史。
* **Citizens 女仆实体**：可生成一个 `EntityType.PLAYER` NPC，记录 NPC id，并通过右键打开 CraftMaid 菜单。
* **右键菜单**：支持查看状态、召回、设置 home、回家、看向玩家、打开背包、配置装备、刷新皮肤、默认锚点/区域设置、跟随、停止工作和护卫控制。
* **锚点与区域系统**：命名单点 anchor 和命名长方体 region 会保存到 `plugins/CraftMaid/anchors.yml`；region 使用两个角点 `pos1` / `pos2`。
* **Job 框架骨架**：统一 `MaidJob` / `activeJob` / `JobPhase`，可以查看 `idle` / `fishing` / `chunk_keeper` / `harvest` / `following` / `guarding` 状态，并通过命令或菜单停止当前工作。
* **模拟钓鱼 MVP**：读取 `fishing_spot/<name>` 和 `pond/<name>`，让女仆走到钓鱼点、面向鱼塘、模拟等待和挥手，随机产出鱼/垃圾/宝藏并放入女仆背包；等待时间、loot 权重、宝藏开关可配置。可选开启 Denizen `/npc fish` 作为表演动画，但 CraftMaid 仍负责产出和背包控制。
* **ChunkKeeper MVP**：读取 `redstone_watch/<name>`，使用 Paper `addPluginChunkTicket` 保持机器所在 chunk 加载；停止 job 或插件关闭时释放 ticket。
* **农田收割 MVP**：读取 `farm/<name>`，只处理成熟的白名单 `Ageable` 作物；每 tick 限流，产物以 all-or-nothing 方式进入女仆背包，背包满时停止且不改当前方块。
* **单 LLM JSON Turn Loop**：玩家对话、历史记忆、环境、当前 Job 状态、最近产出和背包摘要会一起进入 LLM；LLM 必须返回 `{chat, actions}` JSON。actions 非空时插件先执行白名单 action，再用执行结果生成最终回复。
* **皮肤配置**：`maid.skin` 支持 `master`、`player`、`none` / `default` 或任意玩家名；底层会尝试调用 Citizens `SkinTrait`。
* **背包和装备**：背包使用 Citizens `Inventory` trait；装备使用 Citizens `Equipment` trait，可配置主手、副手和护甲。
* **跟随**：使用 Citizens Navigator，每 20 tick 更新一次跟随目标；跟随速度可通过 `maid.follow.speed` 调整，默认 `1.35`。
* **Sentinel 护卫原型**：可通过菜单让女仆保护主人、停止护卫或守在当前位置；底层通过反射接 Sentinel trait，目标为怪物并避开 creeper。
* **战斗掉落/经验处理**：护卫初始化时会打开 Sentinel 的敌怪掉落；默认开启 NPC 击杀经验补偿，但它依赖插件识别最后一击来源，不等同于原版玩家击杀。

## 尚未实现

* **完整 Denizen 行为**：当前只可选调用 Denizen `/npc fish` 作为钓鱼表演层，还没有接 Denizen 脚本任务或 Denizen 驱动的工作流。
* **真实鱼钩/原版钓鱼**：当前钓鱼产出由 CraftMaid 内置模拟控制，不生成原版 `FishHook` 实体，也不把 loot 权交给 Denizen。
* **自动找水域**：钓鱼需要先设置 `region pond/<name>`，暂时不会自动扫描附近水域。
* **家务系统**：还没有箱子整理、自动补种消耗种子、鱼塘自动发现、红石机器巡检逻辑或重载后恢复工作。
* **完整 Job 调度**：当前只有单任务骨架，还没有任务队列、优先级、重载后恢复或复杂中断策略。
* **完整自然语言动作执行**：当前只开放钓鱼、收田、看机器、停止工作和状态查询这些有限 action；“露西，跟着我”还不会自动转换成 `FOLLOW_START`。
* **跟随细节**：当前是第一版，还没有 `follow_distance`、`start_distance`、`teleport_distance`、跨世界处理、卡住恢复或重载后继续跟随。

## 📦 前置要求

* **服务端核心**：Paper 26.1.2+ (Java 25+)
* **可选前置插件**：[Citizens2](https://wiki.citizensnpcs.co/Citizens_Wiki) (用于生成肉身实体；未安装时聊天功能仍可使用)
  * Citizens 官方下载页：[Downloads](https://wiki.citizensnpcs.co/Downloads)
  * Spigot release builds：[Citizens on Spigot](https://www.spigotmc.org/resources/citizens.13811/)
  * Jenkins dev builds：[Citizens2 Jenkins](https://ci.citizensnpcs.co/job/Citizens2/)；新版本 Paper 通常优先看这里的最新 successful build。
* **可选扩展插件**：Sentinel / Denizen 用于护卫、战斗、脚本演出、钓鱼等扩展能力；未安装时不影响基础 AI 对话。
  * [Sentinel](https://www.spigotmc.org/resources/sentinel.22017/)：Citizens 的战斗 NPC 扩展，当前已用于右键菜单里的基础护卫能力。Jenkins dev builds：[Sentinel Jenkins](https://ci.citizensnpcs.co/job/Sentinel/)。
  * [Denizen](https://denizenscript.com/)：Citizens 生态脚本引擎，适合后续做 NPC 演出、脚本任务、钓鱼原型等。稳定构建：[Denizen Jenkins](https://ci.citizensnpcs.co/job/Denizen/)；开发构建：[Denizen Developmental Jenkins](https://ci.citizensnpcs.co/job/Denizen_Developmental/)。
  * Denizen 当前只作为可选钓鱼表演层；“去钓鱼”由 CraftMaid 内置 MVP 控制产出，不依赖 Denizen。
* **AI 算力**：兼容 OpenAI API 格式的 LLM 服务接口 (如本地部署的 vLLM、Ollama 或云端 API)

## 🛠️ 安装、编译与构建

### 直接安装 Release

推荐从 GitHub Release 下载最新 jar：

<https://github.com/quantumxiaol/CraftMaid/releases/latest>

下载后把 jar 放入服务端的 `plugins` 文件夹，然后重启服务器。下面的命令应在服务端目录执行。

当前版本也可以直接下载：

```bash
mkdir -p plugins
curl -fL -o plugins/CraftMaid-v1.1.0.jar \
  https://github.com/quantumxiaol/CraftMaid/releases/download/v1.1.0/CraftMaid-v1.1.0.jar
```

以后换版本时只改 `VERSION`：

```bash
VERSION=v1.1.0
mkdir -p plugins
curl -fL -o "plugins/CraftMaid-${VERSION}.jar" \
  "https://github.com/quantumxiaol/CraftMaid/releases/download/${VERSION}/CraftMaid-${VERSION}.jar"
```

也可以自动获取最新版本号：

```bash
mkdir -p plugins
VERSION="$(curl -fsSL https://api.github.com/repos/quantumxiaol/CraftMaid/releases/latest \
  | sed -n 's/.*"tag_name": "\(v[^"]*\)".*/\1/p')"
curl -fL -o "plugins/CraftMaid-${VERSION}.jar" \
  "https://github.com/quantumxiaol/CraftMaid/releases/download/${VERSION}/CraftMaid-${VERSION}.jar"
```

如果你希望每次覆盖同一个插件文件名，也可以把 `-o` 改成 `plugins/CraftMaid.jar`；Minecraft/Paper 加载插件不要求 jar 文件名带版本号。

Release 页面里的 `Source code (zip)` 和 `Source code (tar.gz)` 是源码包，不是服务端要安装的插件 jar。

### 从源码编译

本项目使用 Maven 进行包管理。当前构建目标为 Paper API `26.1.2.build.72-stable` 与 Java 25。

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
git tag v1.1.0
git push origin v1.1.0
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
  follow:
    speed: 1.35 # Citizens Navigator 默认速度是 1.0；这里只影响跟随赶路
  combat:
    enemy_drops: true # Sentinel 护卫击杀敌怪时允许掉落物
    enemy_exp: true # NPC 击杀敌怪且死亡经验为 0 时尝试补基础经验
    default_enemy_exp: 5
  system_prompt: |-
    这里可以自定义女仆的人设、称呼习惯、说话风格和行为边界。
    可用占位符：{name}、{master}、{language}。占位符是可选的，完全重写后不包含占位符也可以。
chat:
  cooldown_seconds: 3 # 同一玩家触发 AI 回复的冷却时间
  followup_seconds: 180 # 喊过女仆名字后，同一玩家后续发言免唤醒的滑动窗口；0 表示关闭
  max_context_entities: 8 # 写入提示词的周围实体数量上限
  reply_prefix: "[{name}] "
intent:
  enabled: true # 有限自然语言意图
  master_only: true # 只有 maid.master 或 craftmaid.admin 可以用聊天触发工作
  consume_on_match: true # 规则 fallback 使用；LLM JSON 模式下 action 会先执行再回复
  allow_followup_window: true # 允许 followup_seconds 窗口内免唤醒触发工作意图
  llm_json: true # 普通聊天和工作控制统一走 JSON：{chat, actions}
  response_format_json_object: true # DeepSeek 和 OpenAI GPT 可用；不兼容接口会自动重试并降级
  plan_max_tokens: 1024 # 输出 token 上限，不限制输入里的 system prompt / 历史 / 环境上下文
  plan_temperature: 0.2
  final_max_tokens: 480
  final_temperature: 0.6
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
jobs:
  fishing:
    min_wait_ticks: 100
    max_wait_ticks: 240
    fish_weight: 72.0
    junk_weight: 23.0
    treasure_weight: 5.0
    treasure_enabled: true
    denizen_animation: false # 可选 Denizen 表演层，不改变 CraftMaid 产出控制
  chunk_keeper:
    radius_chunks: 0
    guard_with_sentinel: false
  harvest:
    max_region_volume: 4096
    max_blocks_per_tick: 4
    max_blocks_per_run: 128
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

如果服务端已经生成过旧版 `plugins/CraftMaid/config.yml`，新字段不会自动写入旧文件。需要手动补上 `maid.skin`、`maid.follow.speed`、`maid.combat`、`conversation.summary.max_tokens`、`conversation.summary.temperature` 和 `jobs`，或者备份后删除旧配置让插件重新生成。

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
* 刷新皮肤
* 查看 anchors / regions
* 设置 home/default
* 设置 fishing_spot/default
* 设置 farm/default、pond/default、redstone/default 的两个角点
* 设置 redstone_watch/default
* 跟随我 / 别跟了
* 停止工作（停止当前 job、跟随或护卫）
* 保护我 / 停止护卫 / 守在这里（需要 Sentinel）
* 去钓鱼（优先使用 `fishing_spot/default` 和 `pond/default`；没有 default 且只有一个可用配置时自动使用它）
* 看住机器（优先使用 `redstone_watch/default`；没有 default 且只有一个可用配置时自动使用它）
* 收农田（优先使用 `farm/default`；没有 default 且只有一个可用配置时自动使用它）
* 关闭菜单

“打开背包”使用 Citizens 的 Inventory trait；“配置装备”使用 Citizens 的 Equipment trait，可以设置主手、副手和护甲显示。“去钓鱼 / 看住机器 / 收农田”会启动 CraftMaid 内置 Job，产物会写入女仆背包。菜单里的控制动作只允许 `maid.master` 或拥有 `craftmaid.admin` 权限的玩家执行。

anchors / regions 会保存到 `plugins/CraftMaid/anchors.yml`，用于钓鱼、农田、红石机器监控和回家。当前钓鱼已经使用 `fishing_spot` 和 `pond`，农田收割已经使用 `farm`，ChunkKeeper 已经使用 `redstone_watch`；箱子整理还只是数据准备。

单点 anchor 用于“女仆站在哪里 / 去哪里 / 从哪里交互”：

```
/craftmaid anchor list
/craftmaid anchor set home default
/craftmaid anchor set fishing_spot main
/craftmaid anchor set chest drops
/craftmaid anchor set guard_post gate
/craftmaid anchor set redstone_watch iron_farm
/craftmaid anchor remove chest drops
```

长方体 region 用于扫描/操作一片方块；两个角点必须在同一个世界：

```
/craftmaid region list
/craftmaid region set farm wheat_field pos1
/craftmaid region set farm wheat_field pos2
/craftmaid region set pond main pos1
/craftmaid region set pond main pos2
/craftmaid region set redstone iron_farm pos1
/craftmaid region set redstone iron_farm pos2
/craftmaid region show farm wheat_field
/craftmaid region show pond main
/craftmaid region remove farm wheat_field
```

`region show` 会在长方体 12 条边上短暂显示粒子边框，只对执行命令的玩家可见。为了避免误刷大量粒子，过大的区域会拒绝显示。

当前推荐的 anchor 类型是 `home`、`fishing_spot`、`chest`、`guard_post`、`redstone_watch`；region 类型是 `farm`、`pond`、`redstone`。名称只能使用小写字母、数字、下划线或连字符。右键菜单和自然语言 intent 会优先使用 `default`；如果没有 `default` 但只有一个可用配置，会自动使用它；如果有多个配置，会拒绝自动选择并提示用命令指定名称。

Job 状态和钓鱼控制：

```
/craftmaid job status
/craftmaid job stop
/craftmaid fishing start main
/craftmaid fishing stop
/craftmaid chunk start iron_farm
/craftmaid chunk stop
/craftmaid harvest start wheat_field
/craftmaid harvest stop
```

`/craftmaid fishing start main` 会读取 `anchor fishing_spot/main` 和 `region pond/main`。`/craftmaid chunk start iron_farm` 会读取 `anchor redstone_watch/iron_farm` 并加载附近 chunk。`/craftmaid harvest start wheat_field` 会读取 `region farm/wheat_field` 并收割成熟作物。如果省略名称，命令默认使用 `main`；右键菜单和自然语言 intent 优先使用 `default`，否则在只有一个可用配置时自动选择。开始钓鱼或收割会自动停止跟随；如果女仆正在护卫，会拒绝启动钓鱼或收割。ChunkKeeper 可以和 Sentinel 守点共存。当前钓鱼不会生成真实鱼钩，而是模拟等待、挥手和产出，产物会进入女仆背包；背包满时任务会自动停止。ChunkKeeper 使用 Paper plugin chunk ticket，job 运行期间会保持目标 chunk 加载，停止 job、插件 disable 或服务器关闭时会释放 ticket。

自然语言 action 只在玩家喊了女仆名字，或处于 `chat.followup_seconds` 对话窗口内时检测。默认 `intent.llm_json: true` 时，普通聊天和工作控制都走同一个 JSON 协议：

```json
{"chat":"普通聊天回复","actions":[]}
```

或者：

```json
{"chat":"","actions":[{"type":"JOB_STOP","target":"current"},{"type":"HARVEST_START","name":"main"}]}
```

有 action 时，第一轮 `chat` 不会显示；插件会先执行 action，再把执行结果、更新后的 job 状态、最近工作事件和背包摘要交给同一个 LLM 生成最终回复。长期历史只记录玩家原话和最终回复，不记录内部 JSON、action result 或每条产出日志。

当前 action 白名单只有：`FISHING_START`、`FISHING_STOP`、`HARVEST_START`、`HARVEST_STOP`、`CHUNK_KEEPER_START`、`CHUNK_KEEPER_STOP`、`RECALL`、`JOB_STOP`、`JOB_STATUS`。插件最多接受 2 个 action，且只允许单动作或 `STOP + START/RECALL` 的切换组合，不允许 LLM 执行 Bukkit/控制台命令。

每次 JSON 对话请求会发送：稳定的 system prompt（女仆人设、JSON 协议、action 白名单和规则）、可选长期 Memory、最近聊天历史，以及本轮最后一条 user message。本轮 user message 里包含玩家名和身份、玩家原话、当前环境、Job 状态、可用工作配置、最近工作事件和女仆背包摘要。`plan_max_tokens` 和 `final_max_tokens` 只限制模型输出长度，不限制这些输入上下文。

`intent.response_format_json_object: true` 时，CraftMaid 会先在请求体里带上 `response_format: {"type":"json_object"}`。DeepSeek 和 OpenAI GPT 支持这类 JSON 输出约束；如果某个 OpenAI-compatible 接口返回“不支持 / unknown / invalid response_format”之类错误，插件会自动重试一次不带该参数，并在本次插件运行期间降级为只靠 system prompt 约束 JSON。JSON 解析失败不会执行 action。

如果使用会输出 `reasoning_content` 的推理模型，建议给 `plan_max_tokens` 和 `final_max_tokens` 留足空间，或者换用非推理聊天模型。CraftMaid 只会解析普通 `message.content`，不会把 `reasoning_content` 当作可执行 JSON。

为提高 DeepSeek 前缀缓存命中率，CraftMaid 会把稳定内容放在消息前缀：女仆人设、JSON 协议、action 白名单、安全规则、长期 Memory、历史对话；当前环境、job 状态、最近产出、背包摘要、玩家本轮原话和 action result 放在最后一个 user message。服务端日志会输出 DeepSeek 兼容 usage 中的 `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens`，例如 `LLM mode=plan cache_hit=... cache_miss=...`。

`anchors.yml` 大致结构如下：

```yaml
version: 1
anchors:
  home:
    default: ...
  fishing_spot:
    main: ...
  chest:
    drops: ...
regions:
  pond:
    main:
      pos1: ...
      pos2: ...
  farm:
    wheat_field:
      pos1: ...
      pos2: ...
  redstone:
    iron_farm:
      pos1: ...
      pos2: ...
```

护卫战斗里，`maid.combat.enemy_drops: true` 会在 Sentinel 护卫初始化时打开敌怪掉落。`maid.combat.enemy_exp: true` 会在插件能识别到最后一击来自女仆、且服务端给出 0 经验时尝试补 `default_enemy_exp` 点经验。默认配置是开启的；如果实服没有经验，先确认已经替换到最新 jar，并且服务器实际加载的 `plugins/CraftMaid/config.yml` 里有 `maid.combat.enemy_exp: true`。已经处于护卫状态的旧 NPC 需要重新点击一次“保护我”或“守在这里”，让新设置写入 Sentinel trait。

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
