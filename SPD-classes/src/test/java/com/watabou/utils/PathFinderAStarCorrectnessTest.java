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
		verifyStraightLinePreference();
		verifyDungeonLikeMaps();
		verifyBlockedMap();
		verifyRandomMaps();

		System.out.println("PathFinder A* correctness test passed: shortest paths and unreachable cases verified.");
	}

	private static void verifyOpenMap() {
		boolean[] passable = new boolean[SIZE];
		Arrays.fill(passable, true);
		verifyCase(passable, cell(2, 2), cell(18, 11), "open map");
	}

	private static void verifyStraightLinePreference() {
		boolean[] passable = new boolean[SIZE];
		Arrays.fill(passable, true);

		PathFinder.Path horizontal = PathFinder.find(cell(3, 10), cell(20, 10), passable);
		assertStraight(horizontal, 3, 10, 20, 10, "horizontal open path");

		PathFinder.Path vertical = PathFinder.find(cell(12, 4), cell(12, 22), passable);
		assertStraight(vertical, 12, 4, 12, 22, "vertical open path");

		PathFinder.Path diagonal = PathFinder.find(cell(4, 4), cell(18, 18), passable);
		assertStraight(diagonal, 4, 4, 18, 18, "diagonal open path");

		PathFinder.Path shallow = PathFinder.find(cell(3, 10), cell(20, 16), passable);
		assertLineHugging(shallow, 3, 10, 20, 16, "shallow diagonal open path");

		PathFinder.Path steep = PathFinder.find(cell(20, 6), cell(13, 24), passable);
		assertLineHugging(steep, 20, 6, 13, 24, "steep diagonal open path");
	}

	private static void verifyDungeonLikeMaps() {
		verifyRoomDoorCorridorRoute();
		verifyLShapedDungeonCorridor();
		verifyOccupiedDestination();
		verifyMappedRouteBeatsUnmappedShortcut();
	}

	private static void verifyRoomDoorCorridorRoute() {
		boolean[] passable = new boolean[SIZE];
		openRect(passable, 2, 5, 8, 11);
		openHorizontal(passable, 9, 22, 8);
		openRect(passable, 23, 5, 29, 11);

		PathFinder.Path path = verifyCase(passable, cell(4, 8), cell(26, 8), "room-door-corridor route");
		assertContains(path, cell(9, 8), "room-door-corridor route should pass through the left doorway");
		assertContains(path, cell(22, 8), "room-door-corridor route should pass through the right doorway");
	}

	private static void verifyLShapedDungeonCorridor() {
		boolean[] passable = new boolean[SIZE];
		openRect(passable, 2, 2, 7, 7);
		openHorizontal(passable, 8, 16, 5);
		openVertical(passable, 16, 5, 20);
		openRect(passable, 14, 20, 24, 26);

		PathFinder.Path path = verifyCase(passable, cell(4, 4), cell(20, 23), "L-shaped dungeon corridor");
		assertContainsAny(path, new int[]{cell(16, 5), cell(16, 6)}, "L-shaped route should enter the vertical corridor");
		assertContainsAny(path, new int[]{cell(16, 20), cell(17, 20)}, "L-shaped route should enter the lower room through its doorway area");
	}

	private static void verifyOccupiedDestination() {
		boolean[] passable = new boolean[SIZE];
		openRect(passable, 4, 4, 16, 12);
		int from = cell(6, 7);
		int to = cell(14, 9);
		passable[to] = false;

		verifyCase(passable, from, to, "occupied destination cell");
	}

	private static void verifyMappedRouteBeatsUnmappedShortcut() {
		boolean[] passable = new boolean[SIZE];
		openRect(passable, 3, 3, 9, 9);
		openHorizontal(passable, 10, 25, 6);
		openVertical(passable, 25, 6, 18);
		openRect(passable, 21, 18, 28, 25);

		PathFinder.Path path = verifyCase(passable, cell(5, 6), cell(24, 21), "mapped route with unmapped shortcut blocked");
		assertContainsAny(path, new int[]{cell(25, 6), cell(25, 7)}, "mapped route should use the known corridor bend");
		assertContainsAny(path, new int[]{cell(25, 18), cell(24, 18)}, "mapped route should use the known lower doorway area");
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

	private static PathFinder.Path verifyCase(boolean[] passable, int from, int to, String label) {
		int shortestLength = shortestLength(from, to, passable);
		PathFinder.Path path = PathFinder.find(from, to, passable);
		int step = PathFinder.getStep(from, to, passable);

		if (shortestLength == -1) {
			if (path != null || step != -1) {
				throw new AssertionError(label + ": unreachable path should return null/-1");
			}
			return null;
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
		if (!isShortestFirstStep(passable, from, to, step, shortestLength)) {
			throw new AssertionError(label + ": getStep did not return a legal shortest first step");
		}
		return path;
	}

	private static boolean isShortestFirstStep(boolean[] passable, int from, int to, int step, int shortestLength) {
		if (!isNeighbour(from, step) || (step != to && !passable[step])) {
			return false;
		}
		if (step == to) {
			return shortestLength == 1;
		}
		return shortestLength(step, to, passable) == shortestLength - 1;
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

	private static void assertStraight(PathFinder.Path path, int fromX, int fromY, int toX, int toY, String label) {
		if (path == null) {
			throw new AssertionError(label + ": path returned null");
		}

		int stepX = Integer.compare(toX, fromX);
		int stepY = Integer.compare(toY, fromY);
		int x = fromX;
		int y = fromY;

		for (int cell : path) {
			x += stepX;
			y += stepY;
			if (cell != cell(x, y)) {
				throw new AssertionError(label + ": expected " + cell(x, y) + " but got " + cell);
			}
		}
	}

	private static void assertLineHugging(PathFinder.Path path, int fromX, int fromY, int toX, int toY, String label) {
		if (path == null) {
			throw new AssertionError(label + ": path returned null");
		}

		int dx = toX - fromX;
		int dy = toY - fromY;
		int stepX = Integer.compare(dx, 0);
		int stepY = Integer.compare(dy, 0);
		int maxDeviation = Math.max(Math.abs(dx), Math.abs(dy));
		int previousX = fromX;
		int previousY = fromY;

		for (int next : path) {
			int x = next % WIDTH;
			int y = next / WIDTH;
			int movedX = x - previousX;
			int movedY = y - previousY;

			if ((movedX != 0 && movedX != stepX) || (movedY != 0 && movedY != stepY)) {
				throw new AssertionError(label + ": path moved away from the target");
			}

			int deviation = Math.abs((x - fromX) * dy - (y - fromY) * dx);
			if (deviation > maxDeviation) {
				throw new AssertionError(label + ": path deviated too far from the clicked line");
			}

			previousX = x;
			previousY = y;
		}
	}

	private static void assertContains(PathFinder.Path path, int expectedCell, String label) {
		if (path == null || !path.contains(expectedCell)) {
			throw new AssertionError(label + ": expected path to contain cell " + expectedCell);
		}
	}

	private static void assertContainsAny(PathFinder.Path path, int[] expectedCells, String label) {
		if (path != null) {
			for (int expectedCell : expectedCells) {
				if (path.contains(expectedCell)) {
					return;
				}
			}
		}
		throw new AssertionError(label + ": expected path to contain one of " + Arrays.toString(expectedCells));
	}

	private static void openRect(boolean[] passable, int left, int top, int right, int bottom) {
		for (int y = top; y <= bottom; y++) {
			for (int x = left; x <= right; x++) {
				passable[cell(x, y)] = true;
			}
		}
	}

	private static void openHorizontal(boolean[] passable, int left, int right, int y) {
		for (int x = left; x <= right; x++) {
			passable[cell(x, y)] = true;
		}
	}

	private static void openVertical(boolean[] passable, int x, int top, int bottom) {
		for (int y = top; y <= bottom; y++) {
			passable[cell(x, y)] = true;
		}
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
