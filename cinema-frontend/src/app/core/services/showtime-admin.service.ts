import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Showtime, CreateShowtimeRequest } from '../models/movie.model';

@Injectable({ providedIn: 'root' })
export class ShowtimeAdminService {
  private http = inject(HttpClient);

  createShowtime(request: CreateShowtimeRequest): Observable<Showtime> {
    return this.http.post<Showtime>('/api/showtimes', request);
  }

  updateShowtime(id: number, request: CreateShowtimeRequest): Observable<Showtime> {
    return this.http.put<Showtime>(`/api/showtimes/${id}`, request);
  }
}
