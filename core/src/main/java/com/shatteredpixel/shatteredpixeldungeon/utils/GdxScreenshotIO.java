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

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.watabou.utils.FileUtils;

class GdxScreenshotIO implements Screenshot.ScreenshotIO<Pixmap> {

	private static final String LOG_TAG = "Screenshot";

	@Override
	public FileHandle fileFor(String path) {
		return FileUtils.getFileHandle(path);
	}

	@Override
	public Pixmap currentFrame() {
		int width = Gdx.graphics.getBackBufferWidth();
		int height = Gdx.graphics.getBackBufferHeight();
		byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);

		Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
		BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
		return pixmap;
	}

	@Override
	public void writePNG(FileHandle file, Pixmap frame) {
		PixmapIO.writePNG(file, frame);
	}

	@Override
	public void dispose(Pixmap frame) {
		frame.dispose();
	}

	@Override
	public void logSaved(FileHandle file) {
		Gdx.app.log(LOG_TAG, "Saved screenshot to " + file.path());
		GLog.p("Screenshot saved: " + file.path());
	}
}
