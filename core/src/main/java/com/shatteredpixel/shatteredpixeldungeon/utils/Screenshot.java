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

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.Input;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Screenshot {

	private static final String DIR = "screenshots";
	private static final String PREFIX = "screenshot-";
	private static final String EXTENSION = ".png";

	public static FileHandle capture() {
		return capture(timestamp(), new GdxScreenshotIO());
	}

	static <T> FileHandle capture(String timestamp, ScreenshotIO<T> io) {
		FileHandle file = nextFile(timestamp, io);
		FileHandle parent = file.parent();
		if (parent != null) parent.mkdirs();

		T frame = io.currentFrame();
		try {
			io.writePNG(file, frame);
		} finally {
			io.dispose(frame);
		}

		io.logSaved(file);
		return file;
	}

	public static boolean isShortcut(int keyCode, boolean altPressed) {
		return altPressed && keyCode == Input.Keys.S;
	}

	static String pathFor(String filename) {
		return DIR + "/" + filename;
	}

	static String filename(String timestamp, int sequence) {
		if (sequence <= 0) {
			return PREFIX + timestamp + EXTENSION;
		} else {
			return PREFIX + timestamp + "-" + sequence + EXTENSION;
		}
	}

	private static <T> FileHandle nextFile(String timestamp, ScreenshotIO<T> io) {
		int sequence = 0;
		FileHandle file;
		do {
			file = io.fileFor(pathFor(filename(timestamp, sequence++)));
		} while (file.exists());
		return file;
	}

	static String timestamp() {
		return timestamp(new Date());
	}

	static String timestamp(long time) {
		return timestamp(new Date(time));
	}

	private static String timestamp(Date date) {
		return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(date);
	}

	interface ScreenshotIO<T> {

		FileHandle fileFor(String path);

		T currentFrame();

		void writePNG(FileHandle file, T frame);

		void dispose(T frame);

		void logSaved(FileHandle file);
	}
}
