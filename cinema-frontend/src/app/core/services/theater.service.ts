import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Theater, CreateTheaterRequest } from '../models/movie.model';

@Injectable({ providedIn: 'root' })
export class TheaterService {
  private http = inject(HttpClient);

  getTheaters(): Observable<Theater[]> {
    return this.http.get<Theater[]>('/api/theaters');
  }

  getTheater(id: number): Observable<Theater> {
    return this.http.get<Theater>(`/api/theaters/${id}`);
  }

  createTheater(request: CreateTheaterRequest): Observable<Theater> {
    return this.http.post<Theater>('/api/theaters', request);
  }

  updateTheater(id: number, request: CreateTheaterRequest): Observable<Theater> {
    return this.http.put<Theater>(`/api/theaters/${id}`, request);
  }
}
