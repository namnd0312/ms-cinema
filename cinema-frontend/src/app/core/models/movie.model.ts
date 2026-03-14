export interface Movie {
  id: number;
  title: string;
  description: string;
  durationMinutes: number;
  genre: string;
  releaseDate: string;
  posterUrl: string;
  rating: string;
  averageRating: number;
  totalRatings: number;
  commentCount: number;
}

export interface Theater {
  id: number;
  name: string;
  location: string;
  totalRows: number;
  totalColumns: number;
  totalSeats: number;
  createdAt: string;
}

export interface CreateMovieRequest {
  title: string;
  description: string;
  genre: string;
  durationMin: number;
  rating: string;
  posterUrl: string;
  releaseDate: string;
}

export interface CreateTheaterRequest {
  name: string;
  location: string;
  totalRows: number;
  totalColumns: number;
}

export interface CreateShowtimeRequest {
  movieId: number;
  theaterId: number;
  startTime: string;
  endTime: string;
  basePrice: number;
}

export interface Seat {
  id: number;
  seatLabel: string;
  seatType: string;
  rowNumber: number;
  columnNumber: number;
  price: number;
  status?: string; // AVAILABLE, OCCUPIED, RESERVED
}

export interface Showtime {
  id: number;
  movie: Movie;
  theater: Theater;
  startTime: string;
  endTime: string;
  basePrice: number;
}
