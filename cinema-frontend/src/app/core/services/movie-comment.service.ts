import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  MovieCommentDto,
  CreateCommentRequest,
  UpdateCommentRequest,
  CommentReactionDto,
  CommentReactionRequest,
  Page
} from '../models/movie-comment.model';

@Injectable({ providedIn: 'root' })
export class MovieCommentService {
  private http = inject(HttpClient);

  getComments(movieId: number, page = 0, size = 20): Observable<Page<MovieCommentDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<MovieCommentDto>>(`/api/movies/${movieId}/comments`, { params });
  }

  createComment(movieId: number, request: CreateCommentRequest): Observable<MovieCommentDto> {
    return this.http.post<MovieCommentDto>(`/api/movies/${movieId}/comments`, request);
  }

  updateComment(commentId: number, request: UpdateCommentRequest): Observable<MovieCommentDto> {
    return this.http.put<MovieCommentDto>(`/api/comments/${commentId}`, request);
  }

  deleteComment(commentId: number): Observable<void> {
    return this.http.delete<void>(`/api/comments/${commentId}`);
  }

  reactToComment(commentId: number, request: CommentReactionRequest): Observable<CommentReactionDto> {
    return this.http.post<CommentReactionDto>(`/api/comments/${commentId}/reactions`, request);
  }

  removeReaction(commentId: number): Observable<CommentReactionDto> {
    return this.http.delete<CommentReactionDto>(`/api/comments/${commentId}/reactions`);
  }
}
