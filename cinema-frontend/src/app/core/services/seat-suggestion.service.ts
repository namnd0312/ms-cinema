import { Injectable } from '@angular/core';
import { Seat } from '../models/movie.model';

/** A group of adjacent available seats with a quality score */
export interface SeatGroup {
  seats: Seat[];
  score: number;
  rowLabel: string;
  totalPrice: number;
}

/**
 * Client-side service to find adjacent available seat groups for group bookings.
 * Uses sliding window per row, scores by center proximity + row position + type uniformity.
 */
@Injectable({ providedIn: 'root' })
export class SeatSuggestionService {

  /**
   * Find top N adjacent seat groups of given size.
   * @param seats All seats for the showtime
   * @param occupiedIds Set of occupied/reserved seat IDs
   * @param groupSize Number of adjacent seats needed
   * @param aisleColumns Column numbers where aisles exist (breaks adjacency)
   * @param topN Max suggestions to return (default 3)
   */
  findAdjacentGroups(
    seats: Seat[],
    occupiedIds: Set<number>,
    groupSize: number,
    aisleColumns: Set<number> = new Set(),
    topN = 3
  ): SeatGroup[] {
    if (groupSize < 1 || seats.length === 0) return [];

    // Group seats by row
    const rowMap = new Map<number, Seat[]>();
    for (const seat of seats) {
      if (occupiedIds.has(seat.id)) continue; // skip occupied
      const arr = rowMap.get(seat.rowNumber) || [];
      arr.push(seat);
      rowMap.set(seat.rowNumber, arr);
    }

    const totalRows = seats.reduce((max, s) => Math.max(max, s.rowNumber), 0);
    const maxCol = seats.reduce((max, s) => Math.max(max, s.columnNumber), 0);
    const centerCol = (maxCol + 1) / 2;
    const allGroups: SeatGroup[] = [];

    for (const [, rowSeats] of rowMap) {
      const sorted = rowSeats.sort((a, b) => a.columnNumber - b.columnNumber);
      // Split into consecutive runs (broken by gaps or aisles)
      const runs = this.findConsecutiveRuns(sorted, aisleColumns);

      for (const run of runs) {
        if (run.length < groupSize) continue;
        // Sliding window of groupSize across each run
        for (let i = 0; i <= run.length - groupSize; i++) {
          const group = run.slice(i, i + groupSize);
          const score = this.scoreGroup(group, centerCol, totalRows);
          allGroups.push({
            seats: group,
            score,
            rowLabel: group[0].seatLabel.charAt(0),
            totalPrice: group.reduce((sum, s) => sum + s.price, 0)
          });
        }
      }
    }

    return allGroups
      .sort((a, b) => b.score - a.score)
      .slice(0, topN);
  }

  /** Split sorted seats into runs of consecutive columns, broken by gaps or aisles */
  private findConsecutiveRuns(sorted: Seat[], aisleColumns: Set<number>): Seat[][] {
    if (sorted.length === 0) return [];
    const runs: Seat[][] = [[sorted[0]]];
    for (let i = 1; i < sorted.length; i++) {
      const prev = sorted[i - 1];
      const curr = sorted[i];
      // Break if not consecutive or aisle between them
      const isConsecutive = curr.columnNumber === prev.columnNumber + 1;
      const crossesAisle = aisleColumns.has(curr.columnNumber - 1);
      if (isConsecutive && !crossesAisle) {
        runs[runs.length - 1].push(curr);
      } else {
        runs.push([curr]);
      }
    }
    return runs;
  }

  /** Score a group: center proximity (40%), row sweet spot (30%), type uniformity (30%) */
  private scoreGroup(group: Seat[], centerCol: number, totalRows: number): number {
    const avgCol = group.reduce((s, g) => s + g.columnNumber, 0) / group.length;
    const centerDist = Math.abs(avgCol - centerCol);
    const maxDist = centerCol;
    const centerScore = maxDist > 0 ? (1 - centerDist / maxDist) * 40 : 40;

    // Prefer rows 3-7 (1-indexed) as sweet spot
    const row = group[0].rowNumber;
    const sweetSpotCenter = Math.min(5, Math.ceil(totalRows / 2));
    const rowDist = Math.abs(row - sweetSpotCenter);
    const maxRowDist = Math.max(sweetSpotCenter, totalRows - sweetSpotCenter);
    const rowScore = maxRowDist > 0 ? (1 - rowDist / maxRowDist) * 30 : 30;

    // Prefer same seat type throughout group
    const types = new Set(group.map(s => s.seatType));
    const typeScore = types.size === 1 ? 30 : 30 / types.size;

    return centerScore + rowScore + typeScore;
  }
}
