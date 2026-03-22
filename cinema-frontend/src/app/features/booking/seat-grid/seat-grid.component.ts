import { Component, input, output, computed, signal, ElementRef, viewChildren } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Seat } from '../../../core/models/movie.model';
import {
  SeatSection, SeatRow, LegendType,
  buildSeatRows, buildSeatSections, buildLegendTypes,
  getAislePositions, isAisleBefore
} from './seat-grid-layout.utils';
import {
  FocusPosition, computeNextFocus, getSeatAtPosition, buildSeatAriaLabel
} from './seat-grid-keyboard-navigation.utils';

@Component({
  selector: 'app-seat-grid',
  standalone: true,
  imports: [DecimalPipe, MatTooltipModule],
  template: `
    <div class="screen-container">
      <div class="screen">SCREEN</div>
    </div>

    <!-- Seat map with ARIA grid role -->
    <div class="seat-map" role="grid" aria-label="Theater seat map"
         (keydown)="onGridKeydown($event)">
      @for (section of seatSections(); track section.type) {
        @if (!$first) {
          <div class="section-divider"><span class="section-label">{{ section.type }}</span></div>
        }
        @for (row of section.rows; track row.rowNumber) {
          <div class="seat-row" role="row" [attr.aria-label]="'Row ' + row.label">
            <span class="row-label" role="rowheader">{{ row.label }}</span>
            <div class="seat-row-seats">
              @for (seat of row.seats; track seat.id) {
                @if (hasAisleBefore(seat)) { <div class="aisle-gap"></div> }
                <button class="seat" #seatBtn role="gridcell"
                  [class.standard]="seat.seatType === 'STANDARD'"
                  [class.premium]="seat.seatType === 'PREMIUM'"
                  [class.vip]="seat.seatType === 'VIP'"
                  [class.available]="getSeatState(seat) === 'available' || getSeatState(seat) === 'suggested'"
                  [class.selected]="getSeatState(seat) === 'selected'"
                  [class.occupied]="getSeatState(seat) === 'occupied'"
                  [class.suggested]="getSeatState(seat) === 'suggested'"
                  [disabled]="getSeatState(seat) === 'occupied'"
                  [attr.aria-pressed]="getSeatState(seat) === 'selected'"
                  [attr.aria-label]="getAriaLabel(seat)"
                  [attr.tabindex]="isFocused(seat) ? 0 : -1"
                  [matTooltip]="seat.seatLabel + ' - ' + seat.seatType + ' - $' + seat.price"
                  matTooltipShowDelay="300"
                  (click)="onSeatClick(seat)">
                  {{ seat.seatLabel }}
                </button>
              }
            </div>
          </div>
        }
      }
    </div>

    <!-- Screen reader live region for selection announcements -->
    <div aria-live="polite" class="sr-only">{{ liveAnnouncement() }}</div>

    <!-- Legend -->
    <div class="legend">
      <span class="legend-section">
        @for (lt of legendTypes(); track lt.type) {
          <span class="legend-item">
            <span class="dot" [class]="lt.cssClass"></span>
            {{ lt.type }} - {{ lt.price | number:'1.0-0' }}đ
          </span>
        }
      </span>
      <span class="legend-section">
        <span class="legend-item"><span class="dot selected"></span> Selected</span>
        <span class="legend-item"><span class="dot occupied"></span> Occupied</span>
      </span>
    </div>
  `,
  styles: [`
    .screen-container { text-align: center; margin-bottom: 28px; perspective: 400px; }
    .screen { background: linear-gradient(to bottom, rgba(200,220,255,0.4), rgba(200,220,255,0.05));
      padding: 8px 0; border-radius: 50% / 20%; font-size: 0.8rem; letter-spacing: 4px;
      color: rgba(255,255,255,0.5); max-width: 70%; margin: 0 auto; transform: rotateX(-5deg);
      box-shadow: 0 2px 20px rgba(150,180,255,0.15), 0 0 60px rgba(150,180,255,0.08); }
    .seat-map { perspective: 1000px; padding: 0 16px;
      overflow-x: auto; -webkit-overflow-scrolling: touch; }
    .seat-row { display: flex; justify-content: center; align-items: center; gap: 6px;
      margin-bottom: 6px; min-width: fit-content; }
    .seat-row-seats { display: flex; gap: 6px; align-items: center; }
    .aisle-gap { width: 16px; flex-shrink: 0; }
    @media (max-width: 600px) {
      .seat { width: 36px; height: 36px; font-size: 0.6rem; }
      .seat-row, .seat-row-seats { gap: 4px; }
      .aisle-gap { width: 10px; }
      .row-label { width: 24px; font-size: 0.65rem; }
      .screen { max-width: 90%; }
    }
    @media (min-width: 601px) and (max-width: 960px) {
      .seat { width: 40px; height: 40px; }
      .seat-row, .seat-row-seats { gap: 5px; }
    }
    .section-divider { display: flex; align-items: center; justify-content: center;
      margin: 12px 0 8px; gap: 12px; }
    .section-divider::before, .section-divider::after {
      content: ''; flex: 1; max-width: 120px; height: 1px; background: rgba(255,255,255,0.1); }
    .section-label { font-size: 0.7rem; letter-spacing: 2px;
      color: rgba(255,255,255,0.35); text-transform: uppercase; }
    .row-label { display: flex; align-items: center; justify-content: center;
      font-size: 0.75rem; font-weight: 700; color: rgba(255,255,255,0.5);
      width: 32px; flex-shrink: 0; }
    .seat { width: 44px; height: 44px; border: none; border-radius: 6px; cursor: pointer;
      font-size: 0.7rem; font-weight: 600; transition: all 0.2s; display: flex;
      align-items: center; justify-content: center; flex-shrink: 0; }
    .seat:focus-visible { outline: 2px solid #fff; outline-offset: 2px;
      box-shadow: 0 0 0 4px rgba(255,255,255,0.3); }
    .seat.standard.available { background: #2e7d32; color: white; }
    .seat.standard.available:hover { background: #388e3c; transform: scale(1.1); }
    .seat.premium.available { background: #1565c0; color: white; }
    .seat.premium.available:hover { background: #1976d2; transform: scale(1.1); }
    .seat.vip.available { background: #ff8f00; color: white; }
    .seat.vip.available:hover { background: #ffa000; transform: scale(1.1); }
    .seat.selected { background: #00bcd4; color: white;
      box-shadow: 0 0 8px rgba(0,188,212,0.5); }
    .seat.suggested { animation: suggestPulse 1.5s ease-in-out infinite;
      box-shadow: 0 0 8px rgba(255,193,7,0.6); }
    @keyframes suggestPulse {
      0%, 100% { box-shadow: 0 0 4px rgba(255,193,7,0.3); }
      50% { box-shadow: 0 0 12px rgba(255,193,7,0.7); }
    }
    .seat.occupied { background: #424242; color: rgba(255,255,255,0.3); cursor: not-allowed; }
    .legend { display: flex; flex-wrap: wrap; justify-content: center; gap: 16px; margin-top: 16px; }
    .legend-section { display: flex; gap: 16px; }
    .legend-item { display: flex; align-items: center; gap: 6px; font-size: 0.8rem;
      color: rgba(255,255,255,0.7); }
    .dot { width: 14px; height: 14px; border-radius: 4px; }
    .dot.standard { background: #2e7d32; } .dot.premium { background: #1565c0; }
    .dot.vip { background: #ff8f00; } .dot.selected { background: #00bcd4; }
    .dot.occupied { background: #424242; }
    .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
      overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
  `]
})
export class SeatGridComponent {
  seats = input.required<Seat[]>();
  selectedSeatIds = input.required<Set<number>>();
  suggestedSeatIds = input<Set<number>>(new Set());
  seatToggled = output<Seat>();

  private seatBtns = viewChildren<ElementRef<HTMLButtonElement>>('seatBtn');
  private focusPos = signal<FocusPosition>({ rowIndex: 0, colIndex: 0 });
  liveAnnouncement = signal('');

  private aislePositions = computed(() => {
    const maxCol = this.seats().reduce((max, s) => Math.max(max, s.columnNumber), 0);
    return getAislePositions(maxCol);
  });

  private allRows = computed<SeatRow[]>(() => buildSeatRows(this.seats()));
  seatSections = computed<SeatSection[]>(() => buildSeatSections(this.allRows()));
  legendTypes = computed<LegendType[]>(() => buildLegendTypes(this.seats()));

  hasAisleBefore(seat: Seat): boolean {
    return isAisleBefore(seat.columnNumber, this.aislePositions());
  }

  getSeatState(seat: Seat): string {
    if (this.selectedSeatIds().has(seat.id)) return 'selected';
    if (seat.status === 'OCCUPIED' || seat.status === 'RESERVED') return 'occupied';
    if (this.suggestedSeatIds().has(seat.id)) return 'suggested';
    return 'available';
  }

  getAriaLabel(seat: Seat): string {
    return buildSeatAriaLabel(seat, this.getSeatState(seat));
  }

  /** Check if this seat has keyboard focus (roving tabindex) */
  isFocused(seat: Seat): boolean {
    const pos = this.focusPos();
    const row = this.allRows()[pos.rowIndex];
    return row?.seats[pos.colIndex]?.id === seat.id;
  }

  onSeatClick(seat: Seat): void {
    if (this.getSeatState(seat) !== 'occupied') {
      // Read state before emit (parent mutates selectedSeatIds synchronously)
      const wasSelected = this.selectedSeatIds().has(seat.id);
      this.seatToggled.emit(seat);
      this.liveAnnouncement.set(`Seat ${seat.seatLabel} ${wasSelected ? 'deselected' : 'selected'}`);
    }
  }

  onGridKeydown(event: KeyboardEvent): void {
    // Enter/Space toggles selection
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      const seat = getSeatAtPosition(this.allRows(), this.focusPos());
      if (seat) this.onSeatClick(seat);
      return;
    }
    const next = computeNextFocus(event, this.focusPos(), this.allRows());
    if (!next) return;
    event.preventDefault();
    this.focusPos.set(next);
    // Find and focus the button at the new position
    const seat = getSeatAtPosition(this.allRows(), next);
    if (!seat) return;
    const btns = this.seatBtns();
    const flatSeats = this.allRows().flatMap(r => r.seats);
    const idx = flatSeats.findIndex(s => s.id === seat.id);
    if (idx >= 0 && btns[idx]) btns[idx].nativeElement.focus();
  }
}
