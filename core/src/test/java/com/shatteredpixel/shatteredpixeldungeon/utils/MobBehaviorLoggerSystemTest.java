package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MobBehaviorLoggerSystemTest {

    private Files previousFiles;

    @Before
    public void setUp() throws Exception {
        GdxNativesLoader.load();
        previousFiles = Gdx.files;
        Actor.clear();
        MobBehaviorLogger.resetSessionTracking();
        MobBehaviorLogger.setEnabled(true);
        MobBehaviorLoggerTestSupport.clearActualLog();
        SPDSettings.set(new MobBehaviorLoggerTestSupport.MemoryPreferences());
        GamesInProgress.selectedClass = HeroClass.WARRIOR;
        GamesInProgress.curSlot = 1;
        Game.version = "TEST";
        Game.versionCode = ShatteredPixelDungeon.v3_0_0;
    }

    @After
    public void tearDown() throws Exception {
        MobBehaviorLogger.setEnabled(false);
        MobBehaviorLogger.resetSessionTracking();
        Actor.clear();
        Gdx.files = previousFiles;
        MobBehaviorLoggerTestSupport.clearActualLog();
    }

    @Test
    public void bootstrapsLevelAndReloadsWithoutDuplicateSpawnLogs() throws Exception {
        try (MobBehaviorLoggerTestSupport.FileSandbox sandbox = MobBehaviorLoggerTestSupport.createSandbox()) {
            Gdx.files = sandbox.files();
            FileUtils.setDefaultFileProperties(Files.FileType.Local, "");

            Dungeon.seed = 123456789L;
            Dungeon.daily = false;
            Dungeon.dailyReplay = false;
            Dungeon.customSeedText = "";

            Dungeon.init();
            Dungeon.level = Dungeon.newLevel();
            Actor.init();

            assertNotNull(Dungeon.hero);
            assertNotNull(Dungeon.level);
            assertFalse(Dungeon.level.mobs.isEmpty());
            assertEquals(Dungeon.level.mobs.size(), MobBehaviorLoggerTestSupport.trackedMobs().size());

            String initialLog = MobBehaviorLoggerTestSupport.readActualLog();
            int initialSpawnCount = MobBehaviorLoggerTestSupport.countOccurrences(initialLog, "MOB_SPAWN");
            assertEquals(Dungeon.level.mobs.size(), initialSpawnCount);

            Dungeon.saveGame(GamesInProgress.curSlot);
            Dungeon.saveLevel(GamesInProgress.curSlot);

            Actor.clear();
            MobBehaviorLogger.resetSessionTracking();
            // Do NOT clear the log here — we want to verify no duplicate spawns are appended on reload

            Dungeon.loadGame(GamesInProgress.curSlot);
            Dungeon.level = Dungeon.loadLevel(GamesInProgress.curSlot);
            Actor.init();

            assertNotNull(Dungeon.hero);
            assertNotNull(Dungeon.level);
            assertFalse(Dungeon.level.mobs.isEmpty());
            assertEquals(Dungeon.level.mobs.size(), MobBehaviorLoggerTestSupport.trackedMobs().size());

            // After reload, total MOB_SPAWN count in log must still equal the initial count
            // (reload must not produce duplicate spawn entries)
            String reloadedLog = MobBehaviorLoggerTestSupport.readActualLog();
            assertEquals(initialSpawnCount,
                    MobBehaviorLoggerTestSupport.countOccurrences(reloadedLog, "MOB_SPAWN"));
            assertTrue(reloadedLog.contains("depth=1"));
        }
    }
}
