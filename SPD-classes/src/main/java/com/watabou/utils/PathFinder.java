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

public class PathFinder {
	
	private static final int INFINITY = Integer.MAX_VALUE;

	public static int[] distance;
	private static int[] maxVal;
	
	private static boolean[] goals;
	private static int[] queue;
	private static boolean[] queued; //currently only used in getStepBack, other can piggyback on distance
	private static int[] visited;
	private static int visitedCount;
	private static boolean dirtyAll;
	private static int[] pathParent;
	private static int[] heapCells;
	private static int[] heapPriorities;
	private static int heapSize;
	private static int[] closed;
	private static int searchId;
	
	private static int size = 0;
	private static int width = 0;

	private static int[] dir;
	private static int[] dirLR;

	//performance-light shortcuts for some common pathfinder cases
	//they are in array-access order for increased memory performance
	public static int[] NEIGHBOURS4;
	public static int[] NEIGHBOURS8;
	public static int[] NEIGHBOURS9;

	//similar to their equivalent neighbour arrays, but the order is clockwise.
	//Useful for some logic functions, but is slower due to lack of array-access order.
	public static int[] CIRCLE4;
	public static int[] CIRCLE8;
	
	public static void setMapSize( int width, int height ) {
		
		PathFinder.width = width;
		PathFinder.size = width * height;
		
		distance = new int[size];
		goals = new boolean[size];
		queue = new int[size];
		queued = new boolean[size];
		visited = new int[size];
		visitedCount = 0;
		dirtyAll = false;
		pathParent = new int[size];
		heapCells = new int[size * 4];
		heapPriorities = new int[size * 4];
		heapSize = 0;
		closed = new int[size];
		searchId = 1;

		maxVal = new int[size];
		Arrays.fill(maxVal, INFINITY);
		Arrays.fill(distance, INFINITY);

		dir = new int[]{-1, +1, -width, +width, -width-1, -width+1, +width-1, +width+1};
		dirLR = new int[]{-1-width, -1, -1+width, -width, +width, +1-width, +1, +1+width};

		NEIGHBOURS4 = new int[]{-width, -1, +1, +width};
		NEIGHBOURS8 = new int[]{-width-1, -width, -width+1, -1, +1, +width-1, +width, +width+1};
		NEIGHBOURS9 = new int[]{-width-1, -width, -width+1, -1, 0, +1, +width-1, +width, +width+1};

		CIRCLE4 = new int[]{-width, +1, +width, -1};
		CIRCLE8 = new int[]{-width-1, -width, -width+1, +1, +width+1, +width, +width-1, -1};
	}

	private static void resetDistanceMap() {
		if (dirtyAll) {
			System.arraycopy(maxVal, 0, distance, 0, maxVal.length);
			dirtyAll = false;
		} else {
			for (int i = 0; i < visitedCount; i++) {
				distance[visited[i]] = INFINITY;
			}
		}
		visitedCount = 0;
	}

	private static void setDistance( int cell, int value ) {
		if (distance[cell] == INFINITY) {
			visited[visitedCount++] = cell;
		}
		distance[cell] = value;
	}

	public static Path find( int from, int to, boolean[] passable ) {

		if (!buildAStarPath( from, to, passable )) {
			return null;
		}
		
		Path result = new Path();

		for (int s = to; s != from; s = pathParent[s]) {
			result.addFirst( s );
		}
		
		return result;
	}
	
	public static int getStep( int from, int to, boolean[] passable ) {
		
		if (!buildAStarPath( from, to, passable )) {
			return -1;
		}
		
		int step = to;
		while (pathParent[step] != from) {
			step = pathParent[step];
		}

		return step;
	}
	
	public static int getStepBack( int cur, int from, int lookahead, boolean[] passable, boolean canApproachFromPos ) {

		int d = buildEscapeDistanceMap( cur, from, lookahead, passable );
		if (d == 0) return -1;

		if (!canApproachFromPos) {
			//We can't approach the position we are retreating from
			//re-calculate based on this, and reduce the target distance if need-be
			int head = 0;
			int tail = 0;

			int newD = distance[cur];
			BArray.setFalse(queued);

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
							// Add to queue
							queue[tail++] = n;
							queued[n] = true;
						}
					}
				}

			}

			d = Math.min(newD, d);
		}

		for (int i=0; i < size; i++) {
			goals[i] = distance[i] == d;
		}
		if (!buildDistanceMap( cur, goals, passable )) {
			return -1;
		}

		int s = cur;
		
		// From the starting position we are making one step downwards
		int minD = distance[s];
		int mins = s;
		
		for (int i=0; i < dir.length; i++) {

			int n = s + dir[i];
			int thisD = distance[n];
			
			if (thisD < minD) {
				minD = thisD;
				mins = n;
			}
		}

		return mins;
	}
	
	private static boolean buildDistanceMap( int from, int to, boolean[] passable ) {
		
		if (from == to) {
			return false;
		}

		System.arraycopy(maxVal, 0, distance, 0, maxVal.length);
		visitedCount = 0;
		dirtyAll = true;
		
		boolean pathFound = false;
		
		int head = 0;
		int tail = 0;
		
		// Add to queue
		queue[tail++] = to;
		distance[to] = 0;
		
		while (head < tail) {
			
			// Remove from queue
			int step = queue[head++];
			if (step == from) {
				pathFound = true;
				break;
			}
			int nextDistance = distance[step] + 1;
			
			int start = (step % width == 0 ? 3 : 0);
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n == from || (n >= 0 && n < size && passable[n] && (distance[n] > nextDistance))) {
					// Add to queue
					queue[tail++] = n;
					distance[n] = nextDistance;
				}
					
			}
		}
		
		return pathFound;
	}

	private static boolean buildAStarPath( int from, int to, boolean[] passable ) {

		if (from == to) {
			return false;
		}

		System.arraycopy(maxVal, 0, distance, 0, maxVal.length);
		visitedCount = 0;
		dirtyAll = false;
		nextSearchId();
		heapSize = 0;

		setDistance( from, 0 );
		pathParent[from] = -1;
		heapAdd( from, heuristic( from, to ) );

		while (heapSize > 0) {

			int step = heapRemove();
			if (closed[step] == searchId) {
				continue;
			}
			closed[step] = searchId;

			if (step == to) {
				return true;
			}

			int nextDistance = distance[step] + 1;

			int start = (step % width == 0 ? 3 : 0);
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n >= 0 && n < size && (n == to || passable[n])
						&& closed[n] != searchId && distance[n] > nextDistance) {
					pathParent[n] = step;
					setDistance( n, nextDistance );
					heapAdd( n, nextDistance + heuristic( n, to ) );
				}

			}
		}

		return false;
	}

	private static void nextSearchId() {
		if (searchId == Integer.MAX_VALUE) {
			Arrays.fill( closed, 0 );
			searchId = 1;
		} else {
			searchId++;
		}
	}

	private static int heuristic( int from, int to ) {
		int dx = Math.abs( (from % width) - (to % width) );
		int dy = Math.abs( (from / width) - (to / width) );
		return Math.max( dx, dy );
	}

	private static void heapAdd( int cell, int priority ) {
		int index = heapSize++;

		while (index > 0) {
			int parent = (index - 1) >>> 1;
			if (heapPriorities[parent] <= priority) {
				break;
			}
			heapCells[index] = heapCells[parent];
			heapPriorities[index] = heapPriorities[parent];
			index = parent;
		}

		heapCells[index] = cell;
		heapPriorities[index] = priority;
	}

	private static int heapRemove() {
		int result = heapCells[0];
		int cell = heapCells[--heapSize];
		int priority = heapPriorities[heapSize];

		int index = 0;
		while (true) {
			int child = index * 2 + 1;
			if (child >= heapSize) {
				break;
			}
			if (child + 1 < heapSize && heapPriorities[child + 1] < heapPriorities[child]) {
				child++;
			}
			if (heapPriorities[child] >= priority) {
				break;
			}
			heapCells[index] = heapCells[child];
			heapPriorities[index] = heapPriorities[child];
			index = child;
		}

		heapCells[index] = cell;
		heapPriorities[index] = priority;
		return result;
	}
	
	public static void buildDistanceMap( int to, boolean[] passable, int limit ) {
		
		resetDistanceMap();
		
		int head = 0;
		int tail = 0;
		
		// Add to queue
		queue[tail++] = to;
		setDistance( to, 0 );
		
		while (head < tail) {
			
			// Remove from queue
			int step = queue[head++];
			
			int nextDistance = distance[step] + 1;
			if (nextDistance > limit) {
				return;
			}
			
			int start = (step % width == 0 ? 3 : 0);
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n >= 0 && n < size && passable[n] && (distance[n] > nextDistance)) {
					// Add to queue
					queue[tail++] = n;
					setDistance( n, nextDistance );
				}
					
			}
		}
	}
	
	private static boolean buildDistanceMap( int from, boolean[] to, boolean[] passable ) {
		
		if (to[from]) {
			return false;
		}
		
		System.arraycopy(maxVal, 0, distance, 0, maxVal.length);
		visitedCount = 0;
		dirtyAll = true;
		
		boolean pathFound = false;
		
		int head = 0;
		int tail = 0;
		
		// Add to queue
		for (int i=0; i < size; i++) {
			if (to[i]) {
				queue[tail++] = i;
				distance[i] = 0;
			}
		}
		
		while (head < tail) {
			
			// Remove from queue
			int step = queue[head++];
			if (step == from) {
				pathFound = true;
				break;
			}
			int nextDistance = distance[step] + 1;
			
			int start = (step % width == 0 ? 3 : 0);
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n == from || (n >= 0 && n < size && passable[n] && (distance[n] > nextDistance))) {
					// Add to queue
					queue[tail++] = n;
					distance[n] = nextDistance;
				}
					
			}
		}
		
		return pathFound;
	}

	//the lookahead is the target number of cells to retreat toward from our current position's
	// distance from the position we are escaping from. Returns the highest found distance, up to the lookahead
	private static int buildEscapeDistanceMap( int cur, int from, int lookAhead, boolean[] passable ) {
		
		resetDistanceMap();
		
		int destDist = INFINITY;
		
		int head = 0;
		int tail = 0;
		
		// Add to queue
		queue[tail++] = from;
		setDistance( from, 0 );
		
		int dist = 0;
		
		while (head < tail) {
			
			// Remove from queue
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
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n >= 0 && n < size && passable[n] && distance[n] > nextDistance) {
					// Add to queue
					queue[tail++] = n;
					setDistance( n, nextDistance );
				}
					
			}
		}
		
		return dist;
	}
	
	public static void buildDistanceMap( int to, boolean[] passable ) {
		
		System.arraycopy(maxVal, 0, distance, 0, maxVal.length);
		visitedCount = 0;
		dirtyAll = true;
		
		int head = 0;
		int tail = 0;
		
		// Add to queue
		queue[tail++] = to;
		distance[to] = 0;
		
		while (head < tail) {
			
			// Remove from queue
			int step = queue[head++];
			int nextDistance = distance[step] + 1;
			
			int start = (step % width == 0 ? 3 : 0);
			int end   = ((step+1) % width == 0 ? 3 : 0);
			for (int i = start; i < dirLR.length - end; i++) {

				int n = step + dirLR[i];
				if (n >= 0 && n < size && passable[n] && (distance[n] > nextDistance)) {
					// Add to queue
					queue[tail++] = n;
					distance[n] = nextDistance;
				}
					
			}
		}
	}
	
	@SuppressWarnings("serial")
	public static class Path extends LinkedList<Integer> {
	}
}
