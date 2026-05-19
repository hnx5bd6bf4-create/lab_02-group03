package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Statue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class MobBehaviorLoggerUnitTest {

    @Before
    public void setUp() {
        Actor.clear();
        MobBehaviorLogger.resetSessionTracking();
        MobBehaviorLogger.setEnabled(false);
        Dungeon.depth = 1;
    }

    @After
    public void tearDown() {
        MobBehaviorLogger.resetSessionTracking();
        MobBehaviorLogger.setEnabled(false);
        Actor.clear();
    }

    // ── enable / disable ──────────────────────────────────────────────────────

    @Test
    public void setEnabled_true_isEnabled() {
        MobBehaviorLogger.setEnabled(true);
        assertTrue(MobBehaviorLogger.isEnabled());
    }

    @Test
    public void setEnabled_false_isDisabled() {
        MobBehaviorLogger.setEnabled(true);
        MobBehaviorLogger.setEnabled(false);
        assertFalse(MobBehaviorLogger.isEnabled());
    }

    // ── resetSessionTracking ──────────────────────────────────────────────────

    @Test
    public void resetSessionTracking_clearsAllCaches() {
        MobBehaviorLoggerTestSupport.trackedMobs().add(42);
        MobBehaviorLoggerTestSupport.lastStates().put(42, "HUNTING");
        MobBehaviorLoggerTestSupport.lastTargetIds().put(42, 7);
        MobBehaviorLoggerTestSupport.lastTargetCells().put(42, 99);
        MobBehaviorLoggerTestSupport.lastAlertStates().put(42, Boolean.TRUE);

        MobBehaviorLogger.resetSessionTracking();

        assertTrue(MobBehaviorLoggerTestSupport.trackedMobs().isEmpty());
        assertTrue(MobBehaviorLoggerTestSupport.lastStates().isEmpty());
        assertTrue(MobBehaviorLoggerTestSupport.lastTargetIds().isEmpty());
        assertTrue(MobBehaviorLoggerTestSupport.lastTargetCells().isEmpty());
        assertTrue(MobBehaviorLoggerTestSupport.lastAlertStates().isEmpty());
    }

    // ── onMobAdded ────────────────────────────────────────────────────────────

    @Test
    public void onMobAdded_registersInTrackedMobs() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 5;
        MobBehaviorLogger.onMobAdded(mob, false);
        assertTrue(MobBehaviorLoggerTestSupport.trackedMobs().contains(mob.id()));
    }

    @Test
    public void onMobAdded_nullMob_doesNotThrow() {
        MobBehaviorLogger.setEnabled(true);
        MobBehaviorLogger.onMobAdded(null, true); // must not throw
    }

    @Test
    public void onMobAdded_snapshotsInitialState() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 5;
        MobBehaviorLogger.onMobAdded(mob, false);
        assertNotNull(MobBehaviorLoggerTestSupport.lastStates().get(mob.id()));
    }

    // ── logStateTransition ────────────────────────────────────────────────────

    @Test
    public void logStateTransition_updatesLastState() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 3;
        MobBehaviorLogger.onMobAdded(mob, false);
        MobBehaviorLogger.logStateTransition(mob, "SLEEPING", "HUNTING", "spotted hero");
        assertEquals("HUNTING", MobBehaviorLoggerTestSupport.lastStates().get(mob.id()));
    }

    @Test
    public void logStateTransition_sameState_doesNotUpdate() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 3;
        MobBehaviorLogger.onMobAdded(mob, false);
        MobBehaviorLoggerTestSupport.lastStates().put(mob.id(), "HUNTING");
        MobBehaviorLogger.logStateTransition(mob, "HUNTING", "HUNTING", "no change");
        assertEquals("HUNTING", MobBehaviorLoggerTestSupport.lastStates().get(mob.id()));
    }

    @Test
    public void logStateTransition_nullOldState_skipsWithoutThrow() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 3;
        MobBehaviorLogger.onMobAdded(mob, false);
        MobBehaviorLogger.logStateTransition(mob, null, "HUNTING", "reason");
        // null oldState must be silently ignored
    }

    @Test
    public void logStateTransition_whenDisabled_doesNotUpdateState() {
        MobBehaviorLogger.setEnabled(false);
        Statue mob = new Statue();
        mob.pos = 3;
        MobBehaviorLogger.onMobAdded(mob, false);
        String stateBefore = MobBehaviorLoggerTestSupport.lastStates().get(mob.id());
        MobBehaviorLogger.logStateTransition(mob, stateBefore, "HUNTING", "reason");
        // disabled: state should not be updated by logStateTransition
        // (isTrackable returns false when disabled)
        assertEquals(stateBefore, MobBehaviorLoggerTestSupport.lastStates().get(mob.id()));
    }

    // ── trackAlertStatus ──────────────────────────────────────────────────────

    @Test
    public void trackAlertStatus_firstAlert_recordsTrue() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 7;
        MobBehaviorLogger.onMobAdded(mob, false);
        MobBehaviorLogger.trackAlertStatus(mob, true, "hero visible");
        assertTrue(MobBehaviorLoggerTestSupport.lastAlertStates().get(mob.id()));
    }

    @Test
    public void trackAlertStatus_alreadyAlerted_staysTrue() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 7;
        MobBehaviorLogger.onMobAdded(mob, false);
        MobBehaviorLoggerTestSupport.lastAlertStates().put(mob.id(), Boolean.TRUE);
        MobBehaviorLogger.trackAlertStatus(mob, true, "still alerted");
        assertTrue(MobBehaviorLoggerTestSupport.lastAlertStates().get(mob.id()));
    }

    @Test
    public void trackAlertStatus_falseAlert_recordsFalse() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 7;
        MobBehaviorLogger.onMobAdded(mob, false);
        MobBehaviorLogger.trackAlertStatus(mob, false, "calm");
        assertFalse(MobBehaviorLoggerTestSupport.lastAlertStates().get(mob.id()));
    }

    // ── logTargetAssignment ───────────────────────────────────────────────────

    @Test
    public void logTargetAssignment_nullTarget_recordsNegativeOne() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 2;
        MobBehaviorLogger.onMobAdded(mob, false);
        MobBehaviorLogger.logTargetAssignment(mob, null, "lost target");
        assertEquals(Integer.valueOf(-1), MobBehaviorLoggerTestSupport.lastTargetIds().get(mob.id()));
    }

    // ── logTargetCell ─────────────────────────────────────────────────────────

    @Test
    public void logTargetCell_newCell_updatesTracking() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 4;
        MobBehaviorLogger.onMobAdded(mob, false);
        MobBehaviorLogger.logTargetCell(mob, 88, "moving");
        assertEquals(Integer.valueOf(88), MobBehaviorLoggerTestSupport.lastTargetCells().get(mob.id()));
    }

    @Test
    public void logTargetCell_sameCell_doesNotChange() {
        MobBehaviorLogger.setEnabled(true);
        Statue mob = new Statue();
        mob.pos = 4;
        MobBehaviorLogger.onMobAdded(mob, false);
        MobBehaviorLoggerTestSupport.lastTargetCells().put(mob.id(), 88);
        MobBehaviorLogger.logTargetCell(mob, 88, "same cell");
        assertEquals(Integer.valueOf(88), MobBehaviorLoggerTestSupport.lastTargetCells().get(mob.id()));
    }

    // ── log file path ─────────────────────────────────────────────────────────

    @Test
    public void logFilePath_isAbsolute() {
        String path = MobBehaviorLoggerTestSupport.logFilePath();
        assertNotNull(path);
        assertTrue("Log path must be absolute, got: " + path, new File(path).isAbsolute());
    }

    @Test
    public void logFilePath_endsWithLogExtension() {
        String path = MobBehaviorLoggerTestSupport.logFilePath();
        assertTrue("Log path must end with .log, got: " + path, path.endsWith(".log"));
    }

    @Test
    public void logFilePath_containsGameTitle() {
        String path = MobBehaviorLoggerTestSupport.logFilePath();
        assertTrue("Log path should reference game directory, got: " + path,
                path.contains("Shattered Pixel Dungeon") || path.contains("shattered-pixel-dungeon"));
    }
}
