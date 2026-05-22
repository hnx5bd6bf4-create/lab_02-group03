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
import java.util.LinkedList;

public class PathFinderBenchmark {

	private static final int WIDTH = 64;
	private static final int HEIGHT = 64;
	private static final int TRIALS = 20_000;
	private static final int FIND_TRIALS = 8_000;
	private static final int CORRECTNESS_TRIALS = 1_000;
	private static final int WARMUP_TRIALS = 4_000;
	private static final int MEASURE_ROUNDS = 5;
	private static final long SEED = 5618_0302L;

	public static void main(String[] args) {
		Trial[] trials = createTrials(TRIALS);

		OriginalPathFinder.setMapSize(WIDTH, HEIGHT);
		PathFinder.setMapSize(WIDTH, HEIGHT);

		verifyCorrectness(trials);
		warmUp(trials);

		Comparison step = measurePair(
				new Measurement() { @Override public Result run() { return measureOriginalStep(trials); } },
				new Measurement() { @Override public Result run() { return measureOptimizedStep(trials); } });
		Comparison find = measurePair(
				new Measurement() { @Override public Result run() { return measureOriginalFind(trials); } },
				new Measurement() { @Override public Result run() { return measureOptimizedFind(trials); } });
		Comparison limited = measurePair(
				new Measurement() { @Override public Result run() { return measureOriginalLimitedMap(trials); } },
				new Measurement() { @Override public Result run() { return measureOptimizedLimitedMap(trials); } });
		Comparison retreat = measurePair(
				new Measurement() { @Override public Result run() { return measureOriginalStepBack(trials); } },
				new Measurement() { @Override public Result run() { return measureOptimizedStepBack(trials); } });

		System.out.println("PathFinder benchmark");
		System.out.println("Map: " + WIDTH + "x" + HEIGHT + ", trials: " + TRIALS + ", seed: " + SEED);
		System.out.println("Measurement: best of " + MEASURE_ROUNDS + " rounds after warmup; correctness checks: " + CORRECTNESS_TRIALS);
		System.out.println();
		printComparison("getStep", step);
		printComparison("find path", find);
		printComparison("limited distance map", limited);
		printComparison("getStepBack", retreat);
	}

	private static Trial[] createTrials(int count) {
		java.util.Random random = new java.util.Random(SEED);
		Trial[] trials = new Trial[count];

		for (int i = 0; i < count; i++) {
			boolean[] passable = new boolean[WIDTH * HEIGHT];
			for (int y = 1; y < HEIGHT - 1; y++) {
				for (int x = 1; x < WIDTH - 1; x++) {
					passable[x + y * WIDTH] = random.nextFloat() < 0.72f;
				}
			}

			int from = randomInteriorCell(random);
			int to = randomInteriorCell(random);
			int source = randomInteriorCell(random);

			passable[from] = true;
			passable[to] = true;
			passable[source] = true;

			trials[i] = new Trial(passable, from, to, source, 2 + random.nextInt(10));
		}

		return trials;
	}

	private static int randomInteriorCell(java.util.Random random) {
		int x = 1 + random.nextInt(WIDTH - 2);
		int y = 1 + random.nextInt(HEIGHT - 2);
		return x + y * WIDTH;
	}

	private static void verifyCorrectness(Trial[] trials) {
		for (int i = 0; i < CORRECTNESS_TRIALS; i++) {
			Trial trial = trials[i];
			int shortestLength = shortestLength(trial);

			int originalStep = OriginalPathFinder.getStep(trial.from, trial.to, trial.passable);
			int optimizedStep = PathFinder.getStep(trial.from, trial.to, trial.passable);
			if ((originalStep == -1) != (shortestLength == -1) || (optimizedStep == -1) != (shortestLength == -1)) {
				throw new AssertionError("getStep reachability mismatch at trial " + i
						+ ": original=" + originalStep + ", optimized=" + optimizedStep
						+ ", shortestLength=" + shortestLength);
			}
			if (optimizedStep != -1 && !isShortestFirstStep(trial, optimizedStep, shortestLength)) {
				throw new AssertionError("getStep returned a non-shortest first step at trial " + i
						+ ": optimized=" + optimizedStep
						+ ", shortestLength=" + shortestLength
						+ ", remainingFromStep=" + shortestLength(optimizedStep, trial.to, trial.passable));
			}

			OriginalPathFinder.Path originalPath = OriginalPathFinder.find(trial.from, trial.to, trial.passable);
			PathFinder.Path optimizedPath = PathFinder.find(trial.from, trial.to, trial.passable);
			if (originalPath == null ^ optimizedPath == null) {
				throw new AssertionError("find null mismatch at trial " + i);
			}
			if (optimizedPath != null && (!isValidPath(trial, optimizedPath) || optimizedPath.size() != shortestLength)) {
				throw new AssertionError("find path is invalid or non-shortest at trial " + i);
			}

			OriginalPathFinder.buildDistanceMap(trial.source, trial.passable, trial.limit);
			int[] originalDistance = Arrays.copyOf(OriginalPathFinder.distance, OriginalPathFinder.distance.length);
			PathFinder.buildDistanceMap(trial.source, trial.passable, trial.limit);
			if (!Arrays.equals(originalDistance, PathFinder.distance)) {
				throw new AssertionError("limited distance map mismatch at trial " + i);
			}

			boolean[] originalPassable = Arrays.copyOf(trial.passable, trial.passable.length);
			boolean[] optimizedPassable = Arrays.copyOf(trial.passable, trial.passable.length);
			int originalBack = OriginalPathFinder.getStepBack(trial.from, trial.to, trial.limit, originalPassable, true);
			int optimizedBack = PathFinder.getStepBack(trial.from, trial.to, trial.limit, optimizedPassable, true);
			if (originalBack != optimizedBack) {
				throw new AssertionError("getStepBack mismatch at trial " + i
						+ ": original=" + originalBack + ", optimized=" + optimizedBack);
			}

			originalPassable = Arrays.copyOf(trial.passable, trial.passable.length);
			optimizedPassable = Arrays.copyOf(trial.passable, trial.passable.length);
			originalBack = OriginalPathFinder.getStepBack(trial.from, trial.to, trial.limit, originalPassable, false);
			optimizedBack = PathFinder.getStepBack(trial.from, trial.to, trial.limit, optimizedPassable, false);
			if (originalBack != optimizedBack || !Arrays.equals(originalPassable, optimizedPassable)) {
				throw new AssertionError("getStepBack no-approach mismatch at trial " + i
						+ ": original=" + originalBack + ", optimized=" + optimizedBack);
			}
		}
	}

	private static boolean isShortestFirstStep(Trial trial, int step, int shortestLength) {
		if (!isNeighbour(trial.from, step) || (step != trial.to && !trial.passable[step])) {
			return false;
		}
		if (step == trial.to) {
			return shortestLength == 1;
		}
		return shortestLength(step, trial.to, trial.passable) == shortestLength - 1;
	}

	private static boolean isValidPath(Trial trial, PathFinder.Path path) {
		int previous = trial.from;

		for (int step : path) {
			if (!isNeighbour(previous, step) || (step != trial.to && !trial.passable[step])) {
				return false;
			}
			previous = step;
		}

		return previous == trial.to;
	}

	private static boolean isNeighbour(int first, int second) {
		int firstX = first % WIDTH;
		int firstY = first / WIDTH;
		int secondX = second % WIDTH;
		int secondY = second / WIDTH;
		return Math.max(Math.abs(firstX - secondX), Math.abs(firstY - secondY)) == 1;
	}

	private static int shortestLength(Trial trial) {
		return shortestLength(trial.from, trial.to, trial.passable);
	}

	private static int shortestLength(int from, int to, boolean[] passable) {
		if (from == to) {
			return -1;
		}

		int[] shortest = new int[WIDTH * HEIGHT];
		int[] bfsQueue = new int[WIDTH * HEIGHT];
		Arrays.fill(shortest, Integer.MAX_VALUE);

		int head = 0;
		int tail = 0;
		bfsQueue[tail++] = from;
		shortest[from] = 0;

		while (head < tail) {
			int step = bfsQueue[head++];
			int nextDistance = shortest[step] + 1;

			int start = (step % WIDTH == 0 ? 3 : 0);
			int end = ((step + 1) % WIDTH == 0 ? 3 : 0);
			for (int i = start; i < OriginalPathFinder.dirLR.length - end; i++) {
				int n = step + OriginalPathFinder.dirLR[i];
				if (n == to) {
					return nextDistance;
				}
				if (n >= 0 && n < shortest.length && passable[n] && shortest[n] == Integer.MAX_VALUE) {
					shortest[n] = nextDistance;
					bfsQueue[tail++] = n;
				}
			}
		}

		return -1;
	}

	private static void warmUp(Trial[] trials) {
		for (int i = 0; i < WARMUP_TRIALS; i++) {
			Trial trial = trials[i];
			OriginalPathFinder.getStep(trial.from, trial.to, trial.passable);
			PathFinder.getStep(trial.from, trial.to, trial.passable);
			OriginalPathFinder.find(trial.from, trial.to, trial.passable);
			PathFinder.find(trial.from, trial.to, trial.passable);
			OriginalPathFinder.buildDistanceMap(trial.source, trial.passable, trial.limit);
			PathFinder.buildDistanceMap(trial.source, trial.passable, trial.limit);
			OriginalPathFinder.getStepBack(trial.from, trial.to, trial.limit, trial.passable, true);
			PathFinder.getStepBack(trial.from, trial.to, trial.limit, trial.passable, true);
		}
	}

	private static Result measureOriginalStep(Trial[] trials) {
		long checksum = 0;
		long start = System.nanoTime();
		for (Trial trial : trials) {
			checksum += OriginalPathFinder.getStep(trial.from, trial.to, trial.passable);
		}
		return new Result(System.nanoTime() - start, checksum);
	}

	private static Result measureOptimizedStep(Trial[] trials) {
		long checksum = 0;
		long start = System.nanoTime();
		for (Trial trial : trials) {
			checksum += PathFinder.getStep(trial.from, trial.to, trial.passable);
		}
		return new Result(System.nanoTime() - start, checksum);
	}

	private static Result measureOriginalFind(Trial[] trials) {
		long checksum = 0;
		long start = System.nanoTime();
		for (int i = 0; i < FIND_TRIALS; i++) {
			OriginalPathFinder.Path path = OriginalPathFinder.find(trials[i].from, trials[i].to, trials[i].passable);
			checksum += path == null ? -1 : path.size();
		}
		return new Result(System.nanoTime() - start, checksum);
	}

	private static Result measureOptimizedFind(Trial[] trials) {
		long checksum = 0;
		long start = System.nanoTime();
		for (int i = 0; i < FIND_TRIALS; i++) {
			PathFinder.Path path = PathFinder.find(trials[i].from, trials[i].to, trials[i].passable);
			checksum += path == null ? -1 : path.size();
		}
		return new Result(System.nanoTime() - start, checksum);
	}

	private static Result measureOriginalLimitedMap(Trial[] trials) {
		long checksum = 0;
		long start = System.nanoTime();
		for (Trial trial : trials) {
			OriginalPathFinder.buildDistanceMap(trial.source, trial.passable, trial.limit);
			checksum += OriginalPathFinder.distance[trial.to];
		}
		return new Result(System.nanoTime() - start, checksum);
	}

	private static Result measureOptimizedLimitedMap(Trial[] trials) {
		long checksum = 0;
		long start = System.nanoTime();
		for (Trial trial : trials) {
			PathFinder.buildDistanceMap(trial.source, trial.passable, trial.limit);
			checksum += PathFinder.distance[trial.to];
		}
		return new Result(System.nanoTime() - start, checksum);
	}

	private static Result measureOriginalStepBack(Trial[] trials) {
		long checksum = 0;
		long start = System.nanoTime();
		for (Trial trial : trials) {
			checksum += OriginalPathFinder.getStepBack(trial.from, trial.to, trial.limit, trial.passable, true);
		}
		return new Result(System.nanoTime() - start, checksum);
	}

	private static Result measureOptimizedStepBack(Trial[] trials) {
		long checksum = 0;
		long start = System.nanoTime();
		for (Trial trial : trials) {
			checksum += PathFinder.getStepBack(trial.from, trial.to, trial.limit, trial.passable, true);
		}
		return new Result(System.nanoTime() - start, checksum);
	}

	private static Comparison measurePair(Measurement originalMeasurement, Measurement optimizedMeasurement) {
		Result bestOriginal = null;
		Result bestOptimized = null;

		for (int round = 0; round < MEASURE_ROUNDS; round++) {
			Result original;
			Result optimized;

			if (round % 2 == 0) {
				original = originalMeasurement.run();
				optimized = optimizedMeasurement.run();
			} else {
				optimized = optimizedMeasurement.run();
				original = originalMeasurement.run();
			}

			if (bestOriginal == null || original.nanos < bestOriginal.nanos) {
				bestOriginal = original;
			}
			if (bestOptimized == null || optimized.nanos < bestOptimized.nanos) {
				bestOptimized = optimized;
			}
		}

		return new Comparison(bestOriginal, bestOptimized);
	}

	private static void printComparison(String name, Comparison comparison) {
		double originalMs = comparison.original.nanos / 1_000_000.0;
		double optimizedMs = comparison.optimized.nanos / 1_000_000.0;
		double change = (originalMs - optimizedMs) * 100.0 / originalMs;

		System.out.printf("%-22s original=%8.3f ms  optimized=%8.3f ms  improvement=%6.2f%%  checksum=%d/%d%n",
				name, originalMs, optimizedMs, change, comparison.original.checksum, comparison.optimized.checksum);
	}

	private static class Trial {
		final boolean[] passable;
		final int from;
		final int to;
		final int source;
		final int limit;

		Trial(boolean[] passable, int from, int to, int source, int limit) {
			this.passable = passable;
			this.from = from;
			this.to = to;
			this.source = source;
			this.limit = limit;
		}
	}

	private static class Result {
		final long nanos;
		final long checksum;

		Result(long nanos, long checksum) {
			this.nanos = nanos;
			this.checksum = checksum;
		}
	}

	private interface Measurement {
		Result run();
	}

	private static class Comparison {
		final Result original;
		final Result optimized;

		Comparison(Result original, Result optimized) {
			this.original = original;
			this.optimized = optimized;
		}
	}

	private static class OriginalPathFinder {

		static int[] distance;
		private static int[] maxVal;
		private static boolean[] goals;
		private static int[] queue;
		private static boolean[] queued;
		private static int size = 0;
		private static int width = 0;
		private static int[] dir;
		private static int[] dirLR;

		static void setMapSize(int width, int height) {
			OriginalPathFinder.width = width;
			OriginalPathFinder.size = width * height;

			distance = new int[size];
			goals = new boolean[size];
			queue = new int[size];
			queued = new boolean[size];
			maxVal = new int[size];
			Arrays.fill(maxVal, Integer.MAX_VALUE);

			dir = new int[]{-1, +1, -width, +width, -width-1, -width+1, +width-1, +width+1};
			dirLR = new int[]{-1-width, -1, -1+width, -width, +width, +1-width, +1, +1+width};
		}

		static Path find(int from, int to, boolean[] passable) {
			if (!buildDistanceMap(from, to, passable)) {
				return null;
			}

			Path result = new Path();
			int s = from;

			do {
				int minD = distance[s];
				int mins = s;

				for (int i = 0; i < dir.length; i++) {
					int n = s + dir[i];
					int thisD = distance[n];
					if (thisD < minD) {
						minD = thisD;
						mins = n;
					}
				}
				s = mins;
				result.add(s);
			} while (s != to);

			return result;
		}

		static int getStep(int from, int to, boolean[] passable) {
			if (!buildDistanceMap(from, to, passable)) {
				return -1;
			}

			int minD = distance[from];
			int best = from;
			int step, stepD;

			for (int i = 0; i < dir.length; i++) {
				if ((stepD = distance[step = from + dir[i]]) < minD) {
					minD = stepD;
					best = step;
				}
			}

			return best;
		}

		static int getStepBack(int cur, int from, int lookahead, boolean[] passable, boolean canApproachFromPos) {
			int d = buildEscapeDistanceMap(cur, from, lookahead, passable);
			if (d == 0) return -1;

			if (!canApproachFromPos) {
				int head = 0;
				int tail = 0;

				int newD = distance[cur];
				Arrays.fill(queued, false);

				queue[tail++] = cur;
				queued[cur] = true;

				while (head < tail) {
					int step = queue[head++];

					if (distance[step] > newD) {
						newD = distance[step];
					}

					int start = (step % width == 0 ? 3 : 0);
					int end = ((step + 1) % width == 0 ? 3 : 0);
					for (int i = start; i < dirLR.length - end; i++) {
						int n = step + dirLR[i];
						if (n >= 0 && n < size && passable[n]) {
							if (distance[n] < distance[cur]) {
								passable[n] = false;
							} else if (distance[n] >= distance[step] && !queued[n]) {
								queue[tail++] = n;
								queued[n] = true;
							}
						}
					}
				}

				d = Math.min(newD, d);
			}

			for (int i = 0; i < size; i++) {
				goals[i] = distance[i] == d;
			}
			if (!buildDistanceMap(cur, goals, passable)) {
				return -1;
			}

			int minD = distance[cur];
			int best = cur;

			for (int i = 0; i < dir.length; i++) {
				int n = cur + dir[i];
				int thisD = distance[n];
				if (thisD < minD) {
					minD = thisD;
					best = n;
				}
			}

			return best;
		}

		static void buildDistanceMap(int to, boolean[] passable, int limit) {
			System.arraycopy(maxVal, 0, distance, 0, maxVal.length);

			int head = 0;
			int tail = 0;

			queue[tail++] = to;
			distance[to] = 0;

			while (head < tail) {
				int step = queue[head++];

				int nextDistance = distance[step] + 1;
				if (nextDistance > limit) {
					return;
				}

				int start = (step % width == 0 ? 3 : 0);
				int end = ((step + 1) % width == 0 ? 3 : 0);
				for (int i = start; i < dirLR.length - end; i++) {
					int n = step + dirLR[i];
					if (n >= 0 && n < size && passable[n] && distance[n] > nextDistance) {
						queue[tail++] = n;
						distance[n] = nextDistance;
					}
				}
			}
		}

		private static boolean buildDistanceMap(int from, int to, boolean[] passable) {
			if (from == to) {
				return false;
			}

			System.arraycopy(maxVal, 0, distance, 0, maxVal.length);

			boolean pathFound = false;
			int head = 0;
			int tail = 0;

			queue[tail++] = to;
			distance[to] = 0;

			while (head < tail) {
				int step = queue[head++];
				if (step == from) {
					pathFound = true;
					break;
				}
				int nextDistance = distance[step] + 1;

				int start = (step % width == 0 ? 3 : 0);
				int end = ((step + 1) % width == 0 ? 3 : 0);
				for (int i = start; i < dirLR.length - end; i++) {
					int n = step + dirLR[i];
					if (n == from || (n >= 0 && n < size && passable[n] && distance[n] > nextDistance)) {
						queue[tail++] = n;
						distance[n] = nextDistance;
					}
				}
			}

			return pathFound;
		}

		private static boolean buildDistanceMap(int from, boolean[] to, boolean[] passable) {
			if (to[from]) {
				return false;
			}

			System.arraycopy(maxVal, 0, distance, 0, maxVal.length);

			boolean pathFound = false;
			int head = 0;
			int tail = 0;

			for (int i = 0; i < size; i++) {
				if (to[i]) {
					queue[tail++] = i;
					distance[i] = 0;
				}
			}

			while (head < tail) {
				int step = queue[head++];
				if (step == from) {
					pathFound = true;
					break;
				}
				int nextDistance = distance[step] + 1;

				int start = (step % width == 0 ? 3 : 0);
				int end = ((step + 1) % width == 0 ? 3 : 0);
				for (int i = start; i < dirLR.length - end; i++) {
					int n = step + dirLR[i];
					if (n == from || (n >= 0 && n < size && passable[n] && distance[n] > nextDistance)) {
						queue[tail++] = n;
						distance[n] = nextDistance;
					}
				}
			}

			return pathFound;
		}

		private static int buildEscapeDistanceMap(int cur, int from, int lookAhead, boolean[] passable) {
			System.arraycopy(maxVal, 0, distance, 0, maxVal.length);

			int destDist = Integer.MAX_VALUE;
			int head = 0;
			int tail = 0;

			queue[tail++] = from;
			distance[from] = 0;

			int dist = 0;

			while (head < tail) {
				int step = queue[head++];
				dist = distance[step];

				if (dist > destDist) {
					return destDist;
				}

				if (step == cur) {
					destDist = dist + lookAhead;
				}

				int nextDistance = dist + 1;

				int start = (step % width == 0 ? 3 : 0);
				int end = ((step + 1) % width == 0 ? 3 : 0);
				for (int i = start; i < dirLR.length - end; i++) {
					int n = step + dirLR[i];
					if (n >= 0 && n < size && passable[n] && distance[n] > nextDistance) {
						queue[tail++] = n;
						distance[n] = nextDistance;
					}
				}
			}

			return dist;
		}

		@SuppressWarnings("serial")
		static class Path extends LinkedList<Integer> {
		}
	}
}
