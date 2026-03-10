export interface BookingSeat {
  id: number;
  seatId: number;
  seatLabel: string;
  seatType: string;
  price: number;
}

export interface Booking {
  id: number;
  showtimeId: number;
  userId: number;
  status: string; // RESERVED, CONFIRMED, CANCELLED, EXPIRED
  totalAmount: number;
  reservedAt: string;
  confirmedAt: string | null;
  expiresAt: string;
  seats: BookingSeat[];
}

export interface BookingRequest {
  showtimeId: number;
  seatIds: number[];
}
