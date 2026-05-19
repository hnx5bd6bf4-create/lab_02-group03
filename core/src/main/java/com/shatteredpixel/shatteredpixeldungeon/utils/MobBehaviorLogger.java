
package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MobBehaviorLogger {

    // Fixed absolute log path: ~/Library/Application Support/Shattered Pixel Dungeon/mob-behaviour.log (macOS)
    // Falls back to user.home/mob-behaviour.log on other platforms
    private static final String LOG_FILE = resolveLogFilePath();
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static String resolveLogFilePath() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
        String home = System.getProperty("user.home", ".");
        if (os.contains("mac")) {
            return home + "/Library/Application Support/Shattered Pixel Dungeon/mob-behaviour.log";
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null) appData = home + "/AppData/Roaming";
            return appData + "/.watabou/Shattered Pixel Dungeon/mob-behaviour.log";
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            if (xdg == null) xdg = home + "/.local/share";
            return xdg + "/.watabou/shattered-pixel-dungeon/mob-behaviour.log";
        }
    }
    private static final String UNKNOWN_REASON = "unspecified";

    private static final Set<Integer> trackedMobs = new HashSet<>();
    private static final HashMap<Integer, String> lastStates = new HashMap<>();
    private static final HashMap<Integer, Integer> lastTargetIds = new HashMap<>();
    private static final HashMap<Integer, Integer> lastTargetCells = new HashMap<>();
    private static final HashMap<Integer, Boolean> lastAlertStates = new HashMap<>();

    private static boolean enabled = true;

    private MobBehaviorLogger() {
        // Utility class
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        MobBehaviorLogger.enabled = enabled;
    }

    public static void resetSessionTracking() {
        trackedMobs.clear();
        lastStates.clear();
        lastTargetIds.clear();
        lastTargetCells.clear();
        lastAlertStates.clear();
    }

    public static void onMobAdded(Mob mob, boolean firstAdded) {
        if (mob == null) {
            return;
        }

        int mobId = mob.id();
        trackedMobs.add(mobId);
        updateSnapshots(mob);

        if (firstAdded) {
            log("MOB_SPAWN", mob,
                    "state=" + mob.behaviorStateName()
                            + ", target=" + targetInfo(mob.behaviorEnemy())
                            + ", targetCell=" + mob.behaviorTargetCell());
        }
    }

    public static void logMobSpawn(Mob mob) {
        onMobAdded(mob, true);
    }

    public static void logStateTransition(Mob mob, String oldState, String newState, String reason) {
        if (!isTrackable(mob) || oldState == null || newState == null || oldState.equals(newState)) {
            return;
        }

        int mobId = mob.id();
        String trackedState = lastStates.get(mobId);
        if (newState.equals(trackedState)) {
            return;
        }

        log("STATE_TRANSITION", mob,
                "from=" + oldState + ", to=" + newState + ", reason=" + normalizedReason(reason));
        lastStates.put(mobId, newState);
    }

    public static void trackAlertStatus(Mob mob, boolean alerted, String reason) {
        if (!isTrackable(mob)) {
            return;
        }

        int mobId = mob.id();
        boolean lastAlerted = lastAlertStates.containsKey(mobId) && Boolean.TRUE.equals(lastAlertStates.get(mobId));

        if (alerted && !lastAlerted) {
            log("ALERT_TRIGGERED", mob,
                    "reason=" + normalizedReason(reason));
        }

        lastAlertStates.put(mobId, alerted);
    }

    public static void logTargetAssignment(Mob mob, Char target, String reason) {
        if (!isTrackable(mob)) {
            return;
        }

        int mobId = mob.id();
        int targetId = targetId(target);
        Integer trackedTargetId = lastTargetIds.get(mobId);
        if (trackedTargetId != null && trackedTargetId == targetId) {
            return;
        }

        log("TARGET_ASSIGNMENT", mob,
                "target=" + targetInfo(target) + ", reason=" + normalizedReason(reason));
        lastTargetIds.put(mobId, targetId);
    }

    public static void logTargetCell(Mob mob, int targetCell, String reason) {
        if (!isTrackable(mob)) {
            return;
        }

        int mobId = mob.id();
        Integer trackedTargetCell = lastTargetCells.get(mobId);
        if (trackedTargetCell != null && trackedTargetCell == targetCell) {
            return;
        }

        log("TARGET_ASSIGNMENT", mob,
                "targetCell=" + targetCell + ", reason=" + normalizedReason(reason));
        lastTargetCells.put(mobId, targetCell);
    }

    public static void sync(Mob mob, String reason) {
        if (!isTrackable(mob)) {
            return;
        }

        int mobId = mob.id();
        String currentState = mob.behaviorStateName();
        String previousState = lastStates.get(mobId);
        if (previousState != null && !previousState.equals(currentState)) {
            logStateTransition(mob, previousState, currentState, reason);
        } else if (previousState == null) {
            lastStates.put(mobId, currentState);
        }

        Char currentEnemy = mob.behaviorEnemy();
        int currentTargetId = targetId(currentEnemy);
        Integer previousTargetId = lastTargetIds.get(mobId);
        if (previousTargetId == null) {
            lastTargetIds.put(mobId, currentTargetId);
        } else if (previousTargetId != currentTargetId) {
            logTargetAssignment(mob, currentEnemy, reason);
        }

        int currentTargetCell = mob.behaviorTargetCell();
        Integer previousTargetCell = lastTargetCells.get(mobId);
        if (previousTargetCell == null) {
            lastTargetCells.put(mobId, currentTargetCell);
        } else if (previousTargetCell != currentTargetCell) {
            logTargetCell(mob, currentTargetCell, reason);
        }

        trackAlertStatus(mob, mob.behaviorAlerted(), reason);
    }

    private static void log(String eventType, Mob mob, String message) {
        if (!enabled || mob == null) {
            return;
        }

        String mobInfo = mob.getClass().getSimpleName()
                + "#" + mob.id()
                + "@pos=" + mob.pos;

        String line = "[" + DATE_FORMAT.format(new Date()) + "] "
                + eventType
                + " | mob=" + mobInfo
                + " | depth=" + Dungeon.depth
                + " | " + message;

        System.out.println(line);

        try {
            File logFile = new File(LOG_FILE);
            File parent = logFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
            fw.write(line + System.lineSeparator());
            fw.close();
        } catch (Exception e) {
            System.out.println("[MobBehaviorLogger] Failed to write log file: " + e.getMessage());
        }
    }

    private static boolean isTrackable(Mob mob) {
        return enabled && mob != null && trackedMobs.contains(mob.id());
    }

    private static void updateSnapshots(Mob mob) {
        int mobId = mob.id();
        lastStates.put(mobId, mob.behaviorStateName());
        lastTargetIds.put(mobId, targetId(mob.behaviorEnemy()));
        lastTargetCells.put(mobId, mob.behaviorTargetCell());
        lastAlertStates.put(mobId, mob.behaviorAlerted());
    }

    private static int targetId(Char target) {
        return target == null ? -1 : target.id();
    }

    private static String targetInfo(Char target) {
        return target == null
                ? "null"
                : target.getClass().getSimpleName() + "#" + target.id() + "@pos=" + target.pos;
    }

    private static String normalizedReason(String reason) {
        return reason == null || reason.trim().isEmpty() ? UNKNOWN_REASON : reason;
    }
}