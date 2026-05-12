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

package com.watabou.utils;

import java.util.Arrays;

public class PathFinderAStarCorrectnessTest {

	private static final int WIDTH = 32;
	private static final int HEIGHT = 32;
	private static final int SIZE = WIDTH * HEIGHT;
	private static final int RANDOM_TRIALS = 5_000;
	private static final long SEED = 5618_0302L;
	private static final int INF = Integer.MAX_VALUE;

	private static final int[] DIR_LR = new int[]{
			-1-WIDTH, -1, -1+WIDTH, -WIDTH, +WIDTH, +1-WIDTH, +1, +1+WIDTH
	};

	public static void main(String[] args) {
		PathFinder.setMapSize(WIDTH, HEIGHT);

		verifyOpenMap();
		verifyBlockedMap();
		verifyRandomMaps();

		System.out.println("PathFinder A* correctness test passed: shortest paths and unreachable cases verified.");
	}

	private static void verifyOpenMap() {
		boolean[] passable = new boolean[SIZE];
		Arrays.fill(passable, true);
		verifyCase(passable, cell(2, 2), cell(18, 11), "open map");
	}

	private static void verifyBlockedMap() {
		boolean[] passable = new boolean[SIZE];
		int from = cell(5, 5);
		int to = cell(12, 5);
		passable[from] = true;
		passable[to] = true;
		verifyCase(passable, from, to, "blocked map");
	}

	private static void verifyRandomMaps() {
		java.util.Random random = new java.util.Random(SEED);

		for (int trial = 0; trial < RANDOM_TRIALS; trial++) {
			boolean[] passable = new boolean[SIZE];
			float openChance = 0.45f + random.nextFloat() * 0.45f;

			for (int y = 1; y < HEIGHT - 1; y++) {
				for (int x = 1; x < WIDTH - 1; x++) {
					passable[cell(x, y)] = random.nextFloat() < openChance;
				}
			}

			int from = randomInteriorCell(random);
			int to = randomInteriorCell(random);
			passable[from] = true;
			passable[to] = true;

			verifyCase(passable, from, to, "random trial " + trial);
		}
	}

	private static void verifyCase(boolean[] passable, int from, int to, String label) {
		int shortestLength = shortestLength(from, to, passable);
		PathFinder.Path path = PathFinder.find(from, to, passable);
		int step = PathFinder.getStep(from, to, passable);

		if (shortestLength == -1) {
			if (path != null || step != -1) {
				throw new AssertionError(label + ": unreachable path should return null/-1");
			}
			return;
		}

		if (path == null) {
			throw new AssertionError(label + ": reachable path returned null");
		}
		if (path.size() != shortestLength) {
			throw new AssertionError(label + ": path length " + path.size() + " != shortest length " + shortestLength);
		}
		if (!isValidPath(passable, from, to, path)) {
			throw new AssertionError(label + ": path contains an illegal move or blocked cell");
		}
		if (step != path.getFirst()) {
			throw new AssertionError(label + ": getStep did not match the first path cell");
		}
	}

	private static int shortestLength(int from, int to, boolean[] passable) {
		if (from == to) {
			return -1;
		}

		int[] distance = new int[SIZE];
		int[] queue = new int[SIZE];
		Arrays.fill(distance, INF);

		int head = 0;
		int tail = 0;
		queue[tail++] = from;
		distance[from] = 0;

		while (head < tail) {
			int step = queue[head++];
			int nextDistance = distance[step] + 1;

			for (int i = startIndex(step); i < endIndex(step); i++) {
				int n = step + DIR_LR[i];
				if (n == to) {
					return nextDistance;
				}
				if (n >= 0 && n < SIZE && passable[n] && distance[n] == INF) {
					distance[n] = nextDistance;
					queue[tail++] = n;
				}
			}
		}

		return -1;
	}

	private static boolean isValidPath(boolean[] passable, int from, int to, PathFinder.Path path) {
		int previous = from;

		for (int step : path) {
			if (!isNeighbour(previous, step) || (step != to && !passable[step])) {
				return false;
			}
			previous = step;
		}

		return previous == to;
	}

	private static boolean isNeighbour(int first, int second) {
		int firstX = first % WIDTH;
		int firstY = first / WIDTH;
		int secondX = second % WIDTH;
		int secondY = second / WIDTH;
		return Math.max(Math.abs(firstX - secondX), Math.abs(firstY - secondY)) == 1;
	}

	private static int randomInteriorCell(java.util.Random random) {
		int x = 1 + random.nextInt(WIDTH - 2);
		int y = 1 + random.nextInt(HEIGHT - 2);
		return cell(x, y);
	}

	private static int cell(int x, int y) {
		return x + y * WIDTH;
	}

	private static int startIndex(int cell) {
		return cell % WIDTH == 0 ? 3 : 0;
	}

	private static int endIndex(int cell) {
		return DIR_LR.length - ((cell + 1) % WIDTH == 0 ? 3 : 0);
	}
}
