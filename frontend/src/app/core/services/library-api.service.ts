import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BookRequest,
  BookResponse,
  ErrorResponse,
  LoanResponse,
  MemberResponse,
  PageResponse,
} from '../models/library.models';

export interface BookFilterParams {
  title?: string;
  author?: string;
  available?: boolean | null;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class LibraryApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  getBooks(filters: BookFilterParams): Observable<PageResponse<BookResponse>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 10));

    if (filters.title?.trim()) {
      params = params.set('title', filters.title.trim());
    }
    if (filters.author?.trim()) {
      params = params.set('author', filters.author.trim());
    }
    if (filters.available !== null && filters.available !== undefined) {
      params = params.set('available', String(filters.available));
    }

    return this.http.get<PageResponse<BookResponse>>(`${this.apiUrl}/books`, { params });
  }

  createBook(request: BookRequest): Observable<BookResponse> {
    return this.http.post<BookResponse>(`${this.apiUrl}/books`, request);
  }

  updateBook(id: number, request: BookRequest): Observable<BookResponse> {
    return this.http.put<BookResponse>(`${this.apiUrl}/books/${id}`, request);
  }

  deleteBook(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/books/${id}`);
  }

  borrowBook(bookId: number): Observable<LoanResponse> {
    return this.http.post<LoanResponse>(`${this.apiUrl}/loans`, { bookId });
  }

  returnLoan(loanId: number): Observable<LoanResponse> {
    return this.http.patch<LoanResponse>(`${this.apiUrl}/loans/${loanId}/return`, {});
  }

  getMyLoans(): Observable<LoanResponse[]> {
    return this.http.get<LoanResponse[]>(`${this.apiUrl}/loans/my`);
  }

  getAllLoans(overdue?: boolean | null): Observable<LoanResponse[]> {
    let params = new HttpParams();
    if (overdue !== null && overdue !== undefined) {
      params = params.set('overdue', String(overdue));
    }
    return this.http.get<LoanResponse[]>(`${this.apiUrl}/loans`, { params });
  }

  getAllMembers(): Observable<MemberResponse[]> {
    return this.http.get<MemberResponse[]>(`${this.apiUrl}/members`);
  }
}

export function extractApiError(error: unknown, fallbackMessage: string): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallbackMessage;
  }

  const payload = error.error as Partial<ErrorResponse> | null;
  if (payload?.validationErrors && Object.keys(payload.validationErrors).length > 0) {
    return Object.entries(payload.validationErrors)
      .map(([field, msg]) => `${field}: ${msg}`)
      .join(' | ');
  }
  if (payload?.message) {
    return payload.message;
  }
  if (error.status === 0) {
    return 'Nem érhető el a szerver (ellenőrizd, hogy fut-e a backend a 8080-as porton).';
  }
  return fallbackMessage;
}
