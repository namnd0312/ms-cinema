import { Component, input, output, computed } from '@angular/core';
import { Seat } from '../../../core/models/movie.model';

@Component({
  selector: 'app-seat-grid',
  standalone: true,
  template: `
    <div class="screen-container">
      <div class="screen">SCREEN</div>
    </div>
    <div class="seat-grid" [style.grid-template-columns]="gridColumns()">
      @for (seat of seats(); track seat.id) {
        <button class="seat"
          [class.available]="getSeatState(seat) === 'available'"
          [class.selected]="getSeatState(seat) === 'selected'"
          [class.occupied]="getSeatState(seat) === 'occupied'"
          [disabled]="getSeatState(seat) === 'occupied'"
          [title]="seat.seatLabel + ' - $' + seat.price"
          (click)="onSeatClick(seat)">
          {{ seat.seatLabel }}
        </button>
      }
    </div>
    <div class="legend">
      <span class="legend-item"><span class="dot available"></span> Available</span>
      <span class="legend-item"><span class="dot selected"></span> Selected</span>
      <span class="legend-item"><span class="dot occupied"></span> Occupied</span>
    </div>
  `,
  styles: [`
    .screen-container { text-align: center; margin-bottom: 24px; }
    .screen { background: linear-gradient(to bottom, rgba(255,255,255,0.3), rgba(255,255,255,0.05));
      padding: 6px 0; border-radius: 4px; font-size: 0.8rem; letter-spacing: 4px;
      color: rgba(255,255,255,0.5); max-width: 80%; margin: 0 auto; }
    .seat-grid { display: grid; gap: 6px; justify-content: center; padding: 16px; }
    .seat { width: 44px; height: 44px; border: none; border-radius: 6px; cursor: pointer;
      font-size: 0.7rem; font-weight: 600; transition: all 0.2s; display: flex;
      align-items: center; justify-content: center; }
    .seat.available { background: #2e7d32; color: white; }
    .seat.available:hover { background: #388e3c; transform: scale(1.1); }
    .seat.selected { background: #00bcd4; color: white; box-shadow: 0 0 8px rgba(0,188,212,0.5); }
    .seat.occupied { background: #424242; color: rgba(255,255,255,0.3); cursor: not-allowed; }
    .legend { display: flex; justify-content: center; gap: 24px; margin-top: 16px; }
    .legend-item { display: flex; align-items: center; gap: 6px; font-size: 0.85rem;
      color: rgba(255,255,255,0.7); }
    .dot { width: 14px; height: 14px; border-radius: 4px; }
    .dot.available { background: #2e7d32; }
    .dot.selected { background: #00bcd4; }
    .dot.occupied { background: #424242; }
  `]
})
export class SeatGridComponent {
  seats = input.required<Seat[]>();
  selectedSeatIds = input.required<Set<number>>();
  seatToggled = output<Seat>();

  gridColumns = computed(() => {
    const maxCol = Math.max(...this.seats().map(s => s.columnNumber), 0);
    return `repeat(${maxCol || 10}, 44px)`;
  });

  getSeatState(seat: Seat): string {
    if (this.selectedSeatIds().has(seat.id)) return 'selected';
    if (seat.status === 'OCCUPIED' || seat.status === 'RESERVED') return 'occupied';
    return 'available';
  }

  onSeatClick(seat: Seat): void {
    if (this.getSeatState(seat) !== 'occupied') {
      this.seatToggled.emit(seat);
    }
  }
}
