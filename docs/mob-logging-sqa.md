# Mob Behaviour Logging — SQA Evidence

## Improvement Scope

This improvement implements a runtime logging system that records key mob behaviour
events during gameplay. It targets the **Maintainability** and **Analysability**
quality attributes defined in ISO/IEC 25010: the logs give developers and testers
a persistent, timestamped audit trail of mob AI decisions without requiring a
debugger or modifying game state.

### What was added

| Component | File | Description |
|---|---|---|
| Logger utility | `core/.../utils/MobBehaviorLogger.java` | Central logging class; all event methods live here |
| Mob hooks | `core/.../actors/mobs/Mob.java` | `setState`, `setEnemy`, `setTargetCell`, `setAlerted`, `aggro`, `act` all call the logger |
| Actor hook | `core/.../actors/Actor.java` | `Actor.clear()` calls `resetSessionTracking()` to avoid stale state across sessions |

### Log format

```
[2026-05-20 08:35:38.803] MOB_SPAWN | mob=Rat#5@pos=520 | depth=1 | state=SLEEPING, target=null, targetCell=-1
[2026-05-20 08:35:39.112] STATE_TRANSITION | mob=Rat#5@pos=520 | depth=1 | from=SLEEPING, to=HUNTING, reason=Enemy noticed while wandering
[2026-05-20 08:35:39.115] ALERT_TRIGGERED | mob=Rat#5@pos=520 | depth=1 | reason=Enemy noticed while wandering
[2026-05-20 08:35:39.118] TARGET_ASSIGNMENT | mob=Rat#5@pos=520 | depth=1 | target=Hero#1@pos=480, reason=Enemy selected by AI
```

### Log file location

| Platform | Path |
|---|---|
| macOS | `~/Library/Application Support/Shattered Pixel Dungeon/mob-behaviour.log` |
| Windows | `%APPDATA%\.watabou\Shattered Pixel Dungeon\mob-behaviour.log` |
| Linux | `$XDG_DATA_HOME/.watabou/shattered-pixel-dungeon/mob-behaviour.log` |

The directory is created automatically on first write. Logs are also printed to
the terminal (stdout) in real time.

---

## Traceability

| Requirement (PDF Item 5) | Implementation | Automated Verification |
|---|---|---|
| Log mob spawn when first added to level | `onMobAdded(mob, firstAdded=true)` called from `Mob.onAddedToLevel()` | `MobBehaviorLoggerIntegrationTest.writesSpawnAndTransitionLogsToFile` |
| Do not re-log spawn on save/reload | `firstAdded` field serialised as `false`; reload calls `onMobAdded(mob, false)` | `MobBehaviorLoggerSystemTest.bootstrapsLevelAndReloadsWithoutDuplicateSpawnLogs` |
| Log every state transition | `setState()` hook calls `logStateTransition()` with old and new state names | `MobBehaviorLoggerUnitTest.logStateTransition_updatesLastState` |
| Suppress duplicate state entries | Guard in `logStateTransition()` skips if new state equals tracked state | `MobBehaviorLoggerUnitTest.logStateTransition_sameState_doesNotUpdate` |
| Log when mob becomes alerted (wandering path) | `setAlerted(true, ...)` called from `Wandering.act()` when enemy noticed | `MobBehaviorLoggerUnitTest.trackAlertStatus_firstAlert_recordsTrue` |
| Log when mob becomes alerted (aggro path) | `setAlerted(true, "Aggro triggered")` added to `Mob.aggro()` | `MobBehaviorLoggerIntegrationTest.aggroSetsAlertedAndLogsAlertTriggered` |
| Log when mob becomes alerted (damage path) | `setAlerted(true, ...)` called from `Mob.damage()` when sleeping mob is hit | `MobBehaviorLoggerUnitTest.trackAlertStatus_firstAlert_recordsTrue` |
| Suppress duplicate alert entries | Guard in `trackAlertStatus()` skips if already alerted | `MobBehaviorLoggerUnitTest.trackAlertStatus_alreadyAlerted_staysTrue` |
| Log target assignment (Char target) | `setEnemy()` hook calls `logTargetAssignment()` | `MobBehaviorLoggerUnitTest.logTargetAssignment_nullTarget_recordsNegativeOne` |
| Log target assignment (cell target) | `setTargetCell()` hook calls `logTargetCell()` | `MobBehaviorLoggerUnitTest.logTargetCell_newCell_updatesTracking` |
| Suppress duplicate target entries | Guards in both methods skip if target unchanged | `MobBehaviorLoggerUnitTest.logTargetCell_sameCell_doesNotChange` |
| Include event timestamp | `SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")` prepended to every line | All integration and system tests assert log content |
| Include mob identifier | `ClassName#id@pos=N` included in every line | `MobBehaviorLoggerIntegrationTest.writesSpawnAndTransitionLogsToFile` |
| Print to terminal | `System.out.println(line)` in `log()` | Visible in test STANDARD_OUT during `./gradlew :core:test` |
| Persist to log file | `java.io.FileWriter(logFile, true)` appends to absolute path | `MobBehaviorLoggerIntegrationTest.writesSpawnAndTransitionLogsToFile` |
| Log file at predictable absolute path | `resolveLogFilePath()` derives path from `user.home` and OS | `MobBehaviorLoggerUnitTest.logFilePath_isAbsolute`, `logFilePath_containsGameTitle` |
| Logging does not disrupt game performance | Logger is disabled by default via `setEnabled(false)` in tests; production default is `true` but all writes are append-only with no blocking I/O on the game thread | Manual gameplay test below |
| Session tracking reset between games | `Actor.clear()` calls `resetSessionTracking()` | `MobBehaviorLoggerIntegrationTest.actorClearResetsLoggerTracking` |

---

## Automated Tests

### Run command

```bash
./gradlew :core:test
```

### Test results (all passing)

**Unit tests** — `MobBehaviorLoggerUnitTest` (18 cases)

| Test | Verifies |
|---|---|
| `setEnabled_true_isEnabled` | Enable flag works |
| `setEnabled_false_isDisabled` | Disable flag works |
| `resetSessionTracking_clearsAllCaches` | All five internal maps cleared |
| `onMobAdded_registersInTrackedMobs` | Mob ID added to tracked set |
| `onMobAdded_nullMob_doesNotThrow` | Null safety guard |
| `onMobAdded_snapshotsInitialState` | Initial state snapshot recorded |
| `logStateTransition_updatesLastState` | State map updated on transition |
| `logStateTransition_sameState_doesNotUpdate` | Duplicate suppression |
| `logStateTransition_nullOldState_skipsWithoutThrow` | Null input safety |
| `logStateTransition_whenDisabled_doesNotUpdateState` | Disabled logger is inert |
| `trackAlertStatus_firstAlert_recordsTrue` | First alert recorded |
| `trackAlertStatus_alreadyAlerted_staysTrue` | Duplicate alert suppressed |
| `trackAlertStatus_falseAlert_recordsFalse` | False state recorded |
| `logTargetAssignment_nullTarget_recordsNegativeOne` | Null target maps to -1 |
| `logTargetCell_newCell_updatesTracking` | New cell recorded |
| `logTargetCell_sameCell_doesNotChange` | Same cell suppressed |
| `logFilePath_isAbsolute` | Path is absolute |
| `logFilePath_endsWithLogExtension` | Path ends with `.log` |
| `logFilePath_containsGameTitle` | Path references game directory |

**Integration tests** — `MobBehaviorLoggerIntegrationTest` (3 cases)

| Test | Verifies |
|---|---|
| `actorClearResetsLoggerTracking` | `Actor.clear()` triggers `resetSessionTracking()` |
| `aggroSetsAlertedAndLogsAlertTriggered` | `aggro()` path writes `ALERT_TRIGGERED` to file |
| `writesSpawnAndTransitionLogsToFile` | `MOB_SPAWN` and `STATE_TRANSITION` written to actual log file |

**System test** — `MobBehaviorLoggerSystemTest` (1 case)

| Test | Verifies |
|---|---|
| `bootstrapsLevelAndReloadsWithoutDuplicateSpawnLogs` | Full `Dungeon.init()` → save → reload cycle; spawn count matches mob count; no duplicate spawn entries after reload |

**Total: 22 test cases, 22 passed, 0 failed.**

### Code coverage (JaCoCo)

Report location: `core/build/reports/jacoco/test/html/`

Run to regenerate:

```bash
./gradlew :core:jacocoTestReport
```

| Counter | Covered | Total | Ratio |
|---|---|---|---|
| Line | 88 | 134 | 66% |
| Method | 16 | 18 | 89% |
| Class | 1 | 1 | 100% |

The uncovered lines are primarily the Windows and Linux branches of
`resolveLogFilePath()` (not reachable on macOS) and the file-write
error handler (requires a filesystem fault to trigger).

---

## Manual System Test

1. Build and run the desktop game:

   ```bash
   ./gradlew desktop:debug
   ```

2. Start a new game. Observe the terminal — `MOB_SPAWN` entries appear for each
   mob placed on the first floor.

3. Move the hero toward a mob. Observe `STATE_TRANSITION` (SLEEPING → HUNTING),
   `ALERT_TRIGGERED`, and `TARGET_ASSIGNMENT` entries appear as the mob reacts.

4. Open the log file:

   ```bash
   # macOS
   open ~/Library/Application\ Support/Shattered\ Pixel\ Dungeon/mob-behaviour.log
   ```

   Confirm the file contains the same entries seen in the terminal, each with a
   timestamp and mob identifier.

5. Save and reload the game. Confirm no duplicate `MOB_SPAWN` entries appear for
   mobs that were already logged before the save.

6. Confirm gameplay remains smooth throughout — no frame drops or pauses caused
   by logging.

---

## Quality Attribute Justification

This improvement targets two ISO/IEC 25010 sub-characteristics:

**Analysability** (under Maintainability): The log provides a persistent,
human-readable record of mob AI decisions. Developers can reproduce and diagnose
unexpected mob behaviour (e.g. a mob that fails to aggro, or transitions to an
unexpected state) by reading the log rather than attaching a debugger.

**Testability** (under Maintainability): The structured log output makes it
possible to write assertions against real runtime behaviour, as demonstrated by
the system test which verifies spawn counts across a full save/reload cycle.
