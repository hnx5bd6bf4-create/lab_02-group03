package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Statue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MobBehaviorLoggerIntegrationTest {

    @Before
    public void setUp() throws Exception {
        Actor.clear();
        MobBehaviorLogger.resetSessionTracking();
        MobBehaviorLogger.setEnabled(true);
        MobBehaviorLoggerTestSupport.clearActualLog();
        Dungeon.depth = 7;
    }

    @After
    public void tearDown() throws Exception {
        MobBehaviorLogger.setEnabled(false);
        MobBehaviorLogger.resetSessionTracking();
        Actor.clear();
        MobBehaviorLoggerTestSupport.clearActualLog();
    }

    @Test
    public void actorClearResetsLoggerTracking() {
        Statue statue = new Statue();
        statue.pos = 11;

        Actor.add(statue);

        assertTrue(MobBehaviorLoggerTestSupport.trackedMobs().contains(statue.id()));

        Actor.clear();

        assertTrue(MobBehaviorLoggerTestSupport.trackedMobs().isEmpty());
        assertTrue(MobBehaviorLoggerTestSupport.lastStates().isEmpty());
    }

    @Test
    public void aggroSetsAlertedAndLogsAlertTriggered() throws Exception {
        // Use Rat — default state is SLEEPING (not PASSIVE), so aggro() will trigger setAlerted
        com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat rat =
                new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat();
        rat.pos = 15;
        MobBehaviorLogger.onMobAdded(rat, true);

        // Before aggro, alerted should not be true
        assertFalse(Boolean.TRUE.equals(MobBehaviorLoggerTestSupport.lastAlertStates().get(rat.id())));

        rat.aggro(null);

        // After aggro, alerted must be recorded as true
        assertTrue(Boolean.TRUE.equals(MobBehaviorLoggerTestSupport.lastAlertStates().get(rat.id())));

        String log = MobBehaviorLoggerTestSupport.readActualLog();
        assertTrue("ALERT_TRIGGERED must appear in log after aggro", log.contains("ALERT_TRIGGERED"));
    }

    @Test
    public void writesSpawnAndTransitionLogsToFile() throws Exception {
        Statue statue = new Statue();
        statue.pos = 9;

        MobBehaviorLogger.onMobAdded(statue, true);
        statue.setState(statue.HUNTING, " ");

        String logContents = MobBehaviorLoggerTestSupport.readActualLog();

        assertTrue(logContents.contains("MOB_SPAWN"));
        assertTrue(logContents.contains("STATE_TRANSITION"));
        assertTrue(logContents.contains("depth=7"));
        assertTrue(logContents.contains("reason=unspecified"));
        assertEquals("HUNTING", MobBehaviorLoggerTestSupport.lastStates().get(statue.id()));
    }
}
