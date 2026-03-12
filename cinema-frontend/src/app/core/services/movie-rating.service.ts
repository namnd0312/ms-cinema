import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MovieRatingDto, MovieRatingSummaryDto, CreateRatingRequest } from '../models/movie-rating.model';

@Injectable({ providedIn: 'root' })
export class MovieRatingService {
  private http = inject(HttpClient);

  getRatingSummary(movieId: number): Observable<MovieRatingSummaryDto> {
    return this.http.get<MovieRatingSummaryDto>(`/api/movies/${movieId}/ratings`);
  }

  rateMovie(movieId: number, request: CreateRatingRequest): Observable<MovieRatingDto> {
    return this.http.post<MovieRatingDto>(`/api/movies/${movieId}/ratings`, request);
  }
}
