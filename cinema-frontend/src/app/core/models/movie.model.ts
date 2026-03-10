export interface Movie {
  id: number;
  title: string;
  description: string;
  durationMinutes: number;
  genre: string;
  releaseDate: string;
  posterUrl: string;
  rating: number;
}

export interface Theater {
  id: number;
  name: string;
  totalSeats: number;
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
