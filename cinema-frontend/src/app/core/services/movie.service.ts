import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Movie, Showtime, Seat, CreateMovieRequest } from '../models/movie.model';

@Injectable({ providedIn: 'root' })
export class MovieService {
  private http = inject(HttpClient);

  getMovies(): Observable<Movie[]> {
    return this.http.get<Movie[]>('/api/movies');
  }

  getMovie(id: number): Observable<Movie> {
    return this.http.get<Movie>(`/api/movies/${id}`);
  }

  createMovie(request: CreateMovieRequest): Observable<Movie> {
    return this.http.post<Movie>('/api/movies', request);
  }

  updateMovie(id: number, request: CreateMovieRequest): Observable<Movie> {
    return this.http.put<Movie>(`/api/movies/${id}`, request);
  }

  deleteMovie(id: number): Observable<void> {
    return this.http.delete<void>(`/api/movies/${id}`);
  }

  getShowtimes(movieId?: number): Observable<Showtime[]> {
    if (movieId !== undefined) {
      return this.http.get<Showtime[]>('/api/showtimes', { params: { movieId: movieId.toString() } });
    }
    return this.http.get<Showtime[]>('/api/showtimes');
  }

  getShowtime(id: number): Observable<Showtime> {
    return this.http.get<Showtime>(`/api/showtimes/${id}`);
  }

  getShowtimeSeats(showtimeId: number): Observable<Seat[]> {
    return this.http.get<Seat[]>(`/api/showtimes/${showtimeId}/seats`);
  }
}
