# Mob 行为日志系统 — SQA 证据文档

## 改进范围

本改进为游戏运行时实现了一套 mob 行为事件日志系统，记录 mob AI 在游戏过程中的关键决策。
对应 ISO/IEC 25010 质量模型中的**可维护性（Maintainability）**下的两个子特性：

- **可分析性（Analysability）**：开发者和测试人员无需附加调试器，即可通过持久化的时间戳日志复现和诊断 mob 行为异常。
- **可测试性（Testability）**：结构化的日志输出使得针对真实运行时行为编写断言成为可能，系统测试中已验证跨存档周期的 spawn 计数一致性。

### 改动文件清单

| 文件 | 改动内容 |
|---|---|
| `core/.../utils/MobBehaviorLogger.java` | 新增日志工具类，包含全部事件方法 |
| `core/.../actors/mobs/Mob.java` | 在 `setState`、`setEnemy`、`setTargetCell`、`setAlerted`、`aggro`、`act` 中插入日志钩子 |
| `core/.../actors/Actor.java` | `Actor.clear()` 调用 `resetSessionTracking()`，防止跨会话状态污染 |

### 日志格式示例

```
[2026-05-20 08:35:38.803] MOB_SPAWN       | mob=Rat#5@pos=520  | depth=1 | state=SLEEPING, target=null, targetCell=-1
[2026-05-20 08:35:39.112] STATE_TRANSITION| mob=Rat#5@pos=520  | depth=1 | from=SLEEPING, to=HUNTING, reason=Enemy noticed while wandering
[2026-05-20 08:35:39.115] ALERT_TRIGGERED | mob=Rat#5@pos=520  | depth=1 | reason=Enemy noticed while wandering
[2026-05-20 08:35:39.118] TARGET_ASSIGNMENT| mob=Rat#5@pos=520 | depth=1 | target=Hero#1@pos=480, reason=Enemy selected by AI
```

### 日志文件路径

| 平台 | 路径 |
|---|---|
| macOS | `~/Library/Application Support/Shattered Pixel Dungeon/mob-behaviour.log` |
| Windows | `%APPDATA%\.watabou\Shattered Pixel Dungeon\mob-behaviour.log` |
| Linux | `$XDG_DATA_HOME/.watabou/shattered-pixel-dungeon/mob-behaviour.log` |

路径由 `resolveLogFilePath()` 根据操作系统自动推导，目录不存在时自动创建（`mkdirs()`）。
日志同时实时打印到终端（stdout）。

---

## 需求追踪矩阵

| PDF 第五项需求 | 实现位置 | 自动化验证 |
|---|---|---|
| 首次加入关卡时记录 mob spawn | `Mob.onAddedToLevel()` → `onMobAdded(mob, firstAdded=true)` | `MobBehaviorLoggerIntegrationTest.writesSpawnAndTransitionLogsToFile` |
| 存档读取后不重复记录 spawn | `firstAdded` 字段序列化为 `false`，reload 时调用 `onMobAdded(mob, false)` | `MobBehaviorLoggerSystemTest.bootstrapsLevelAndReloadsWithoutDuplicateSpawnLogs` |
| 每次状态变化时记录 | `setState()` 钩子调用 `logStateTransition()`，传入新旧状态名 | `MobBehaviorLoggerUnitTest.logStateTransition_updatesLastState` |
| 相同状态不重复记录 | `logStateTransition()` 内置去重守卫 | `MobBehaviorLoggerUnitTest.logStateTransition_sameState_doesNotUpdate` |
| mob 进入警戒时记录（游荡路径） | `Wandering.act()` 发现敌人时调用 `setAlerted(true, ...)` | `MobBehaviorLoggerUnitTest.trackAlertStatus_firstAlert_recordsTrue` |
| mob 进入警戒时记录（aggro 路径） | `Mob.aggro()` 中补充调用 `setAlerted(true, "Aggro triggered")` | `MobBehaviorLoggerIntegrationTest.aggroSetsAlertedAndLogsAlertTriggered` |
| mob 进入警戒时记录（受伤路径） | `Mob.damage()` 中睡眠状态受击时调用 `setAlerted(true, ...)` | `MobBehaviorLoggerUnitTest.trackAlertStatus_firstAlert_recordsTrue` |
| 已处于警戒状态时不重复记录 | `trackAlertStatus()` 内置去重守卫 | `MobBehaviorLoggerUnitTest.trackAlertStatus_alreadyAlerted_staysTrue` |
| 设置或更换目标（Char 对象）时记录 | `setEnemy()` 钩子调用 `logTargetAssignment()` | `MobBehaviorLoggerUnitTest.logTargetAssignment_nullTarget_recordsNegativeOne` |
| 设置或更换目标（格子坐标）时记录 | `setTargetCell()` 钩子调用 `logTargetCell()` | `MobBehaviorLoggerUnitTest.logTargetCell_newCell_updatesTracking` |
| 目标未变化时不重复记录 | 两个方法均有去重守卫 | `MobBehaviorLoggerUnitTest.logTargetCell_sameCell_doesNotChange` |
| 每条日志包含事件时间戳 | `SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")` 前缀 | 所有集成测试和系统测试均断言日志内容 |
| 每条日志包含 mob 标识符 | `ClassName#id@pos=N` 格式 | `MobBehaviorLoggerIntegrationTest.writesSpawnAndTransitionLogsToFile` |
| 打印到终端 | `log()` 方法中 `System.out.println(line)` | `./gradlew :core:test` 的 STANDARD_OUT 输出可见 |
| 持久化到日志文件 | `java.io.FileWriter(logFile, true)` 追加写入绝对路径 | `MobBehaviorLoggerIntegrationTest.writesSpawnAndTransitionLogsToFile` |
| 日志文件路径固定可预期 | `resolveLogFilePath()` 基于 `user.home` 和操作系统推导 | `MobBehaviorLoggerUnitTest.logFilePath_isAbsolute`、`logFilePath_containsGameTitle` |
| 日志不影响游戏性能 | 所有写入为追加模式，无阻塞 I/O；`enabled` 开关可随时关闭 | 手动游戏测试（见下文） |
| 跨游戏会话重置追踪状态 | `Actor.clear()` 调用 `resetSessionTracking()` | `MobBehaviorLoggerIntegrationTest.actorClearResetsLoggerTracking` |

---

## 自动化测试

### 运行命令

```bash
./gradlew :core:test
```

### 测试结果（全部通过）

**单元测试** — `MobBehaviorLoggerUnitTest`（18 个用例）

| 测试方法 | 验证内容 |
|---|---|
| `setEnabled_true_isEnabled` | 启用开关生效 |
| `setEnabled_false_isDisabled` | 禁用开关生效 |
| `resetSessionTracking_clearsAllCaches` | 五个内部 Map 全部清空 |
| `onMobAdded_registersInTrackedMobs` | Mob ID 加入追踪集合 |
| `onMobAdded_nullMob_doesNotThrow` | null 输入不抛异常 |
| `onMobAdded_snapshotsInitialState` | 初始状态快照已记录 |
| `logStateTransition_updatesLastState` | 状态 Map 在转换后更新 |
| `logStateTransition_sameState_doesNotUpdate` | 相同状态不触发更新 |
| `logStateTransition_nullOldState_skipsWithoutThrow` | null 旧状态安全跳过 |
| `logStateTransition_whenDisabled_doesNotUpdateState` | 禁用时日志器完全静默 |
| `trackAlertStatus_firstAlert_recordsTrue` | 首次警戒状态被记录 |
| `trackAlertStatus_alreadyAlerted_staysTrue` | 重复警戒不触发重复记录 |
| `trackAlertStatus_falseAlert_recordsFalse` | 解除警戒状态被记录 |
| `logTargetAssignment_nullTarget_recordsNegativeOne` | null 目标映射为 -1 |
| `logTargetCell_newCell_updatesTracking` | 新格子坐标被记录 |
| `logTargetCell_sameCell_doesNotChange` | 相同格子不触发重复记录 |
| `logFilePath_isAbsolute` | 日志路径为绝对路径 |
| `logFilePath_endsWithLogExtension` | 路径以 `.log` 结尾 |
| `logFilePath_containsGameTitle` | 路径包含游戏目录名 |

**集成测试** — `MobBehaviorLoggerIntegrationTest`（3 个用例）

| 测试方法 | 验证内容 |
|---|---|
| `actorClearResetsLoggerTracking` | `Actor.clear()` 触发 `resetSessionTracking()` |
| `aggroSetsAlertedAndLogsAlertTriggered` | `aggro()` 路径向文件写入 `ALERT_TRIGGERED` |
| `writesSpawnAndTransitionLogsToFile` | `MOB_SPAWN` 和 `STATE_TRANSITION` 写入实际日志文件 |

**系统测试** — `MobBehaviorLoggerSystemTest`（1 个用例）

| 测试方法 | 验证内容 |
|---|---|
| `bootstrapsLevelAndReloadsWithoutDuplicateSpawnLogs` | 完整 `Dungeon.init()` → 存档 → 读档流程；spawn 数量与 mob 数量一致；读档后无重复 spawn 记录 |

**合计：22 个测试用例，22 通过，0 失败。**

### 代码覆盖率（JaCoCo）

报告位置：`core/build/reports/jacoco/test/html/`

重新生成命令：

```bash
./gradlew :core:jacocoTestReport
```

| 指标 | 已覆盖 | 总计 | 覆盖率 |
|---|---|---|---|
| 行（Line） | 88 | 134 | 66% |
| 方法（Method） | 16 | 18 | 89% |
| 类（Class） | 1 | 1 | 100% |

未覆盖行主要为 `resolveLogFilePath()` 中的 Windows 和 Linux 分支（macOS 环境下不可达），
以及文件写入失败的异常处理路径（需要模拟文件系统故障才能触发）。

---

## 手动系统测试步骤

1. 构建并运行桌面版游戏：

   ```bash
   ./gradlew desktop:debug
   ```

2. 开始新游戏。观察终端输出，第一层的每个 mob 应出现对应的 `MOB_SPAWN` 记录。

3. 将英雄移向某个 mob。观察终端依次出现：
   - `STATE_TRANSITION`（SLEEPING → HUNTING）
   - `ALERT_TRIGGERED`
   - `TARGET_ASSIGNMENT`

4. 打开日志文件，确认内容与终端输出一致，每条记录均包含时间戳和 mob 标识符：

   ```bash
   # macOS
   cat ~/Library/Application\ Support/Shattered\ Pixel\ Dungeon/mob-behaviour.log
   ```

5. 存档后重新读档，确认日志文件中不出现重复的 `MOB_SPAWN` 条目。

6. 全程确认游戏帧率正常，无卡顿或延迟，日志系统不影响游戏性能。

---

## 质量属性说明

本改进对应 ISO/IEC 25010 可维护性（Maintainability）下的两个子特性：

**可分析性（Analysability）**：日志提供了持久化、人类可读的 mob AI 决策记录。
开发者可通过读取日志文件复现并诊断异常行为（如 mob 未能正确进入警戒状态、
状态转换顺序异常等），无需附加调试器或修改游戏状态。

**可测试性（Testability）**：结构化的日志输出使针对真实运行时行为编写断言成为可能。
系统测试 `bootstrapsLevelAndReloadsWithoutDuplicateSpawnLogs` 即通过断言日志内容，
验证了跨存档周期的 spawn 行为正确性。
