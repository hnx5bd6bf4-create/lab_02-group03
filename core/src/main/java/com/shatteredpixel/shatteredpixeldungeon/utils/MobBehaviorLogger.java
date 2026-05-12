
package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MobBehaviorLogger {

    private static final String LOG_FILE = "mob-behaviour.log";
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    public static boolean enabled = true;

    private MobBehaviorLogger() {
        // Utility class
    }

    public static void logMobSpawn(Mob mob) {
        log("MOB_SPAWN", mob, "Mob added to level");
    }

    public static void logStateTransition(Mob mob, String oldState, String newState, String reason) {
        if (oldState == null || newState == null || oldState.equals(newState)) {
            return;
        }
        log("STATE_TRANSITION", mob,
                "from=" + oldState + ", to=" + newState + ", reason=" + reason);
    }

    public static void logAlertStatus(Mob mob, boolean alerted, String reason) {
        log("ALERT_STATUS", mob,
                "alerted=" + alerted + ", reason=" + reason);
    }

    public static void logTargetAssignment(Mob mob, Char target, String reason) {
        String targetInfo = target == null
                ? "null"
                : target.getClass().getSimpleName() + "#" + target.id() + "@pos=" + target.pos;

        log("TARGET_ASSIGNMENT", mob,
                "target=" + targetInfo + ", reason=" + reason);
    }

    public static void logTargetCell(Mob mob, int targetCell, String reason) {
        log("TARGET_ASSIGNMENT", mob,
                "targetCell=" + targetCell + ", reason=" + reason);
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
                + " | " + message;

        System.out.println(line);

        try {
            FileHandle file = Gdx.files.local(LOG_FILE);
            file.writeString(line + System.lineSeparator(), true);
        } catch (Exception e) {
            System.out.println("[MobBehaviorLogger] Failed to write log file: " + e.getMessage());
        }
    }
}