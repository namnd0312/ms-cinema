import { Component, inject, signal, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { User } from '../../../core/models/user.model';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [MatCardModule, MatChipsModule, MatProgressSpinnerModule, MatIconModule],
  template: `
    @if (loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    } @else if (user()) {
      <div class="profile-container">
        <mat-card>
          <mat-card-header>
            <mat-icon mat-card-avatar class="avatar-icon">account_circle</mat-icon>
            <mat-card-title>{{ user()!.fullName }}</mat-card-title>
            <mat-card-subtitle>{{ user()!.email }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div class="info-row"><strong>Username:</strong> {{ user()!.username }}</div>
            <div class="info-row"><strong>Email:</strong> {{ user()!.email }}</div>
            <div class="info-row"><strong>Full Name:</strong> {{ user()!.fullName }}</div>
            <div class="info-row">
              <strong>Roles:</strong>
              <mat-chip-set>
                @for (role of user()!.roles; track role) {
                  <mat-chip>{{ role }}</mat-chip>
                }
              </mat-chip-set>
            </div>
          </mat-card-content>
        </mat-card>
      </div>
    }
  `,
  styles: [`
    .loading { display: flex; justify-content: center; padding: 64px; }
    .profile-container { padding: 24px; max-width: 500px; margin: 0 auto; }
    .avatar-icon { font-size: 40px; height: 40px; width: 40px; color: rgba(255,255,255,0.5); }
    .info-row { margin: 12px 0; display: flex; align-items: center; gap: 8px; }
  `]
})
export class ProfilePageComponent implements OnInit {
  private http = inject(HttpClient);
  user = signal<User | null>(null);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<User>('/api/users/me').subscribe({
      next: (u) => { this.user.set(u); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}
