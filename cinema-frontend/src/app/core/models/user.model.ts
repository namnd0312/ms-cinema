export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string;
  roles: string[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  fullName: string;
}

export interface JwtResponse {
  id: number;
  token: string;
  refreshToken: string;
  email: string;
  username: string;
  name: string;
  roles: string[];
}

export interface TokenRefreshResponse {
  accessToken: string;
  refreshToken: string;
}
