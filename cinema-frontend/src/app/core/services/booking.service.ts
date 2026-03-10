import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Booking, BookingRequest } from '../models/booking.model';

@Injectable({ providedIn: 'root' })
export class BookingService {
  private http = inject(HttpClient);

  getBookedSeatIds(showtimeId: number): Observable<number[]> {
    return this.http.get<number[]>(`/api/bookings/showtimes/${showtimeId}/booked-seats`);
  }

  reserveSeats(request: BookingRequest): Observable<Booking> {
    return this.http.post<Booking>('/api/bookings/reserve', request);
  }

  getBooking(id: number): Observable<Booking> {
    return this.http.get<Booking>(`/api/bookings/${id}`);
  }

  getMyBookings(): Observable<Booking[]> {
    return this.http.get<Booking[]>('/api/bookings/my');
  }

  confirmBooking(id: number): Observable<Booking> {
    return this.http.post<Booking>(`/api/bookings/${id}/confirm`, {});
  }

  cancelBooking(id: number): Observable<void> {
    return this.http.post<void>(`/api/bookings/${id}/cancel`, {});
  }
}
