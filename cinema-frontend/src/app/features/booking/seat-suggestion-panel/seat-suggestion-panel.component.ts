import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { SeatGroup } from '../../../core/services/seat-suggestion.service';

/**
 * UI panel for group seat suggestions: enter group size, see top suggestions,
 * click to auto-select a group.
 */
@Component({
  selector: 'app-seat-suggestion-panel',
  standalone: true,
  imports: [FormsModule, CurrencyPipe, MatButtonModule, MatChipsModule, MatIconModule],
  template: `
    <div class="suggestion-panel">
      <div class="group-input">
        <label>Find seats together:</label>
        <div class="size-controls">
          <button mat-icon-button (click)="changeSize(-1)" [disabled]="groupSize <= 2">
            <mat-icon>remove</mat-icon>
          </button>
          <span class="size-display">{{ groupSize }}</span>
          <button mat-icon-button (click)="changeSize(1)" [disabled]="groupSize >= 10">
            <mat-icon>add</mat-icon>
          </button>
          <button mat-raised-button color="primary" (click)="onFind()">Find</button>
        </div>
      </div>

      @if (suggestions().length > 0) {
        <div class="suggestions">
          <mat-chip-set>
            @for (group of suggestions(); track $index) {
              <mat-chip (click)="onSelect(group)" class="suggestion-chip">
                <mat-icon matChipAvatar>event_seat</mat-icon>
                Row {{ group.rowLabel }}:
                {{ group.seats[0].seatLabel }}-{{ group.seats[group.seats.length - 1].seatLabel }}
                ({{ group.totalPrice | currency }})
              </mat-chip>
            }
          </mat-chip-set>
          <button mat-button (click)="onClear()">Clear</button>
        </div>
      }

      @if (noResults()) {
        <p class="no-results">No adjacent group of {{ groupSize }} seats available.</p>
      }
    </div>
  `,
  styles: [`
    .suggestion-panel { padding: 12px 0; border-bottom: 1px solid rgba(255,255,255,0.1);
      margin-bottom: 12px; }
    .group-input { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .group-input label { font-size: 0.85rem; color: rgba(255,255,255,0.7); }
    .size-controls { display: flex; align-items: center; gap: 4px; }
    .size-display { font-size: 1.1rem; font-weight: 700; min-width: 28px; text-align: center; }
    .suggestions { margin-top: 8px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .suggestion-chip { cursor: pointer; }
    .no-results { color: rgba(255,255,255,0.5); font-size: 0.8rem; margin: 8px 0 0; }
  `]
})
export class SeatSuggestionPanelComponent {
  suggestions = input.required<SeatGroup[]>();
  noResults = input(false);

  groupSizeChanged = output<number>();
  suggestionSelected = output<SeatGroup>();
  suggestionsCleared = output<void>();

  groupSize = 2;

  changeSize(delta: number): void {
    this.groupSize = Math.max(2, Math.min(10, this.groupSize + delta));
  }

  onFind(): void {
    this.groupSizeChanged.emit(this.groupSize);
  }

  onSelect(group: SeatGroup): void {
    this.suggestionSelected.emit(group);
  }

  onClear(): void {
    this.suggestionsCleared.emit();
  }
}
