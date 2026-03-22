import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';
import SockJS from 'sockjs-client';

/** Message received from the seat status WebSocket topic */
export interface SeatStatusMessage {
  seatIds: number[];
  status: string; // RESERVED, CONFIRMED, RELEASED
  timestamp: string;
}

/**
 * WebSocket service for real-time seat availability updates.
 * Connects via STOMP/SockJS to booking-service and subscribes per showtime.
 * Handles reconnection and clean topic switching between showtimes.
 */
@Injectable({ providedIn: 'root' })
export class SeatWebSocketService implements OnDestroy {
  private client: Client | null = null;
  private messageSubject = new Subject<SeatStatusMessage>();
  private currentShowtimeId: number | null = null;

  /** Subscribe to seat updates for a given showtime. Disconnects previous if different. */
  subscribe(showtimeId: number): Observable<SeatStatusMessage> {
    // If switching to a different showtime, disconnect old connection first
    if (this.currentShowtimeId !== null && this.currentShowtimeId !== showtimeId) {
      this.disconnect();
    }
    this.currentShowtimeId = showtimeId;
    this.connect(showtimeId);
    return this.messageSubject.asObservable();
  }

  /** Disconnect and clean up */
  disconnect(): void {
    if (this.client?.active) {
      this.client.deactivate();
    }
    this.client = null;
    this.currentShowtimeId = null;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private connect(showtimeId: number): void {
    if (this.client?.active) return;

    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        this.client!.subscribe(
          `/topic/showtime/${showtimeId}/seats`,
          (message: IMessage) => {
            const body: SeatStatusMessage = JSON.parse(message.body);
            this.messageSubject.next(body);
          }
        );
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message']);
      }
    });

    this.client.activate();
  }
}
