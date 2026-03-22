import { WritableSignal } from '@angular/core';

/** Manages countdown timer for booking expiry */
export class BookingCountdownTimer {
  private intervalId: ReturnType<typeof setInterval> | null = null;

  start(expiresAt: string, countdown: WritableSignal<string>): void {
    this.stop();
    const update = () => {
      const diff = new Date(expiresAt).getTime() - Date.now();
      if (diff <= 0) {
        countdown.set('EXPIRED');
        this.stop();
        return;
      }
      const min = Math.floor(diff / 60000);
      const sec = Math.floor((diff % 60000) / 1000);
      countdown.set(`${min}:${sec.toString().padStart(2, '0')}`);
    };
    update();
    this.intervalId = setInterval(update, 1000);
  }

  stop(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }
}
