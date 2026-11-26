export type Role = 'ADMIN' | 'MEMBER';

export interface AuthUser {
  email: string;
  fullName: string;
  role: Role;
}

export interface AuthResponse {
  token: string;
  email: string;
  fullName: string;
  role: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface BookResponse {
  id: number;
  title: string;
  author: string;
  isbn: string;
  publishedYear: number | null;
  available: boolean;
}

export interface BookRequest {
  title: string;
  author: string;
  isbn: string;
  publishedYear: number | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface LoanResponse {
  id: number;
  bookId: number;
  bookTitle: string;
  bookIsbn: string;
  userId: number;
  userEmail: string;
  userFullName: string;
  borrowedAt: string;
  dueDate: string;
  returnedAt: string | null;
  overdue: boolean;
}

export interface MemberResponse {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  createdAt: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  validationErrors?: Record<string, string>;
}
