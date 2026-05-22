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
import com.badlogic.gdx.files.FileHandle;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ScreenshotTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
	public void timestampUsesStableScreenshotFormat() {
		String timestamp = Screenshot.timestamp(0L);

		assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}"));
	}

	@Test
	public void currentTimestampUsesStableScreenshotFormat() {
		String timestamp = Screenshot.timestamp();

		assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}"));
	}

	@Test
	public void captureCreatesScreenshotDirectoryAndWritesPng() {
		FakeScreenshotIO io = new FakeScreenshotIO();

		FileHandle file = Screenshot.capture("2026-05-12_17-30-05", io);

		assertEquals("screenshots/screenshot-2026-05-12_17-30-05.png", relativePath(file));
		assertTrue(new File(temporaryFolder.getRoot(), "screenshots").isDirectory());
		assertEquals("screenshots/screenshot-2026-05-12_17-30-05.png", io.writtenPath);
		assertEquals("screenshots/screenshot-2026-05-12_17-30-05.png", io.loggedPath);
		assertTrue(io.disposed);
		assertSame(io.frame, io.writtenFrame);
	}

	@Test
	public void captureUsesNextSequenceWhenFilesAlreadyExist() throws IOException {
		File screenshotDir = temporaryFolder.newFolder("screenshots");
		assertTrue(new File(screenshotDir, "screenshot-2026-05-12_17-30-05.png").createNewFile());
		assertTrue(new File(screenshotDir, "screenshot-2026-05-12_17-30-05-1.png").createNewFile());
		FakeScreenshotIO io = new FakeScreenshotIO();

		FileHandle file = Screenshot.capture("2026-05-12_17-30-05", io);

		assertEquals("screenshots/screenshot-2026-05-12_17-30-05-2.png", relativePath(file));
		assertEquals("screenshots/screenshot-2026-05-12_17-30-05-2.png", io.writtenPath);
	}

	@Test
	public void captureDisposesFrameWhenWriteFails() {
		FakeScreenshotIO io = new FakeScreenshotIO();
		io.failWrite = true;

		assertThrows(RuntimeException.class, () -> Screenshot.capture("2026-05-12_17-30-05", io));
		assertTrue(io.disposed);
		assertEquals("screenshots/screenshot-2026-05-12_17-30-05.png", io.writtenPath);
		assertEquals(null, io.loggedPath);
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

	private String relativePath(FileHandle file) {
		return temporaryFolder.getRoot().toPath().relativize(file.file().toPath()).toString().replace('\\', '/');
	}

	private class FakeScreenshotIO implements Screenshot.ScreenshotIO<Object> {

		private final Object frame = new Object();
		private boolean failWrite;
		private boolean disposed;
		private Object writtenFrame;
		private String writtenPath;
		private String loggedPath;

		@Override
		public FileHandle fileFor(String path) {
			return new FileHandle(new File(temporaryFolder.getRoot(), path));
		}

		@Override
		public Object currentFrame() {
			return frame;
		}

		@Override
		public void writePNG(FileHandle file, Object frame) {
			writtenPath = relativePath(file);
			writtenFrame = frame;
			if (failWrite) {
				throw new RuntimeException("write failed");
			}
		}

		@Override
		public void dispose(Object frame) {
			disposed = true;
		}

		@Override
		public void logSaved(FileHandle file) {
			loggedPath = relativePath(file);
		}
	}
}
