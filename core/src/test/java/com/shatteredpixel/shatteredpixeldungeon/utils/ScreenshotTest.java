/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2024 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.utils;

import com.badlogic.gdx.Input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenshotTest {

	@Test
	public void filenameUsesTimestampWhenAvailable() {
		String filename = Screenshot.filename("2026-05-12_17-30-05", 0);

		assertEquals("screenshot-2026-05-12_17-30-05.png", filename);
	}

	@Test
	public void filenameAddsSequenceWhenSameSecondAlreadyExists() {
		String filename = Screenshot.filename("2026-05-12_17-30-05", 3);

		assertEquals("screenshot-2026-05-12_17-30-05-3.png", filename);
	}

	@Test
	public void filenameAddsFirstSequenceForFirstDuplicate() {
		String filename = Screenshot.filename("2026-05-12_17-30-05", 1);

		assertEquals("screenshot-2026-05-12_17-30-05-1.png", filename);
	}

	@Test
	public void filenameDoesNotAddSequenceForNegativeInput() {
		String filename = Screenshot.filename("2026-05-12_17-30-05", -1);

		assertEquals("screenshot-2026-05-12_17-30-05.png", filename);
	}

	@Test
	public void screenshotPathUsesDedicatedDirectory() {
		String path = Screenshot.pathFor("screenshot-2026-05-12_17-30-05.png");

		assertEquals("screenshots/screenshot-2026-05-12_17-30-05.png", path);
	}

	@Test
	public void altSIsScreenshotShortcut() {
		assertTrue(Screenshot.isShortcut(Input.Keys.S, true));
	}

	@Test
	public void plainSIsNotScreenshotShortcut() {
		assertFalse(Screenshot.isShortcut(Input.Keys.S, false));
	}

	@Test
	public void otherAltKeysAreNotScreenshotShortcut() {
		assertFalse(Screenshot.isShortcut(Input.Keys.A, true));
	}

	@Test
	public void altKeyAloneIsNotScreenshotShortcut() {
		assertFalse(Screenshot.isShortcut(Input.Keys.ALT_LEFT, true));
		assertFalse(Screenshot.isShortcut(Input.Keys.ALT_RIGHT, true));
	}

	@Test
	public void lowercaseAndUppercaseSUseSameKeyCode() {
		assertEquals(Input.Keys.S, Input.Keys.valueOf("S"));
		assertTrue(Screenshot.isShortcut(Input.Keys.valueOf("S"), true));
	}
}
