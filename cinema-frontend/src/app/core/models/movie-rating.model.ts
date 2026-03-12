export interface MovieRatingDto {
  id: number;
  movieId: number;
  userId: number;
  rating: number;
  createdAt: string;
}

export interface MovieRatingSummaryDto {
  averageRating: number;
  totalRatings: number;
  userRating: number | null;
}

export interface CreateRatingRequest {
  rating: number;
}
