import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { BookRequest, BookResponse } from '../../core/models/library.models';
import { AuthService } from '../../core/services/auth.service';
import { extractApiError, LibraryApiService } from '../../core/services/library-api.service';

@Component({
  selector: 'app-books-page',
  imports: [ReactiveFormsModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1>Könyvkatalógus</h1>
          <p class="page-subtitle">Böngéssz a könyvek között, vagy kölcsönözz egy elérhető példányt (max. 3 aktív).</p>
        </div>
        @if (authService.isAdmin()) {
          <button type="button" class="btn btn-primary" (click)="openCreateForm()">
            + Új könyv
          </button>
        }
      </div>

      @if (feedbackMessage()) {
        <div class="alert alert-success" role="status">{{ feedbackMessage() }}</div>
      }
      @if (errorMessage()) {
        <div class="alert alert-error" role="alert">{{ errorMessage() }}</div>
      }

      @if (authService.isAdmin() && showBookForm()) {
        <section class="card book-form-card">
          <h2>{{ editingBookId() === null ? 'Új könyv felvitele' : 'Könyv szerkesztése' }}</h2>
          <form [formGroup]="bookForm" (ngSubmit)="submitBookForm()" class="book-form-grid">
            <div class="form-field">
              <label for="book-title">Cím</label>
              <input id="book-title" type="text" class="input" formControlName="title" />
              @if (bookForm.controls.title.touched && bookForm.controls.title.invalid) {
                <span class="field-error">A cím megadása kötelező.</span>
              }
            </div>

            <div class="form-field">
              <label for="book-author">Szerző</label>
              <input id="book-author" type="text" class="input" formControlName="author" />
              @if (bookForm.controls.author.touched && bookForm.controls.author.invalid) {
                <span class="field-error">A szerző megadása kötelező.</span>
              }
            </div>

            <div class="form-field">
              <label for="book-isbn">ISBN</label>
              <input id="book-isbn" type="text" class="input" formControlName="isbn" placeholder="978-0132350884" />
              @if (bookForm.controls.isbn.touched && bookForm.controls.isbn.invalid) {
                <span class="field-error">Érvényes ISBN kötelező (max. 20 karakter).</span>
              }
            </div>

            <div class="form-field">
              <label for="book-year">Kiadás éve</label>
              <input id="book-year" type="number" class="input" formControlName="publishedYear" placeholder="2024" />
              @if (bookForm.controls.publishedYear.touched && bookForm.controls.publishedYear.invalid) {
                <span class="field-error">Az évszám 1000 és 2100 között lehet.</span>
              }
            </div>

            <div class="book-form-actions">
              <button type="submit" class="btn btn-primary" [disabled]="savingBook()">
                {{ savingBook() ? 'Mentés...' : 'Mentés' }}
              </button>
              <button type="button" class="btn btn-secondary" (click)="closeBookForm()">Mégse</button>
            </div>
          </form>
        </section>
      }

      <section class="card">
        <form [formGroup]="filterForm" (ngSubmit)="applyFilters()" class="filters-grid">
          <div class="form-field">
            <label for="filter-title">Cím</label>
            <input id="filter-title" type="text" class="input" formControlName="title" placeholder="Keresés címre..." />
          </div>

          <div class="form-field">
            <label for="filter-author">Szerző</label>
            <input id="filter-author" type="text" class="input" formControlName="author" placeholder="Keresés szerzőre..." />
          </div>

          <div class="form-field">
            <label for="filter-available">Elérhetőség</label>
            <select id="filter-available" class="select" formControlName="available">
              <option value="ALL">Mind</option>
              <option value="TRUE">Csak elérhető</option>
              <option value="FALSE">Kikölcsönzött</option>
            </select>
          </div>

          <div class="filter-actions">
            <button type="submit" class="btn btn-primary">Keresés</button>
            <button type="button" class="btn btn-secondary" (click)="resetFilters()">Alaphelyzet</button>
          </div>
        </form>
      </section>

      <section class="card">
        @if (loading()) {
          <p class="empty-state">Könyvek betöltése...</p>
        } @else if (books().length === 0) {
          <p class="empty-state">Nincs a keresési feltételeknek megfelelő könyv.</p>
        } @else {
          <div class="table-wrapper">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Cím</th>
                  <th>Szerző</th>
                  <th>ISBN</th>
                  <th>Év</th>
                  <th>Állapot</th>
                  <th>Műveletek</th>
                </tr>
              </thead>
              <tbody>
                @for (book of books(); track book.id) {
                  <tr>
                    <td><span class="book-title">{{ book.title }}</span></td>
                    <td class="book-author">{{ book.author }}</td>
                    <td><code>{{ book.isbn }}</code></td>
                    <td>{{ book.publishedYear ?? '—' }}</td>
                    <td>
                      @if (book.available) {
                        <span class="badge badge-success">Elérhető</span>
                      } @else {
                        <span class="badge badge-warning">Kikölcsönzött</span>
                      }
                    </td>
                    <td>
                      <div class="actions-cell">
                        <button
                          type="button"
                          class="btn btn-primary btn-sm"
                          [disabled]="!book.available || busyBookId() === book.id"
                          (click)="borrowBook(book)"
                        >
                          {{ busyBookId() === book.id ? 'Folyamatban...' : 'Kölcsönzés' }}
                        </button>

                        @if (authService.isAdmin()) {
                          <button type="button" class="btn btn-secondary btn-sm" (click)="openEditForm(book)">
                            Szerkesztés
                          </button>
                          <button
                            type="button"
                            class="btn btn-danger btn-sm"
                            [disabled]="busyBookId() === book.id"
                            (click)="deleteBook(book)"
                          >
                            Törlés
                          </button>
                        }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="pagination-bar">
            <span class="pagination-info">
              Összesen {{ totalElements() }} találat · {{ currentPage() + 1 }} / {{ Math.max(totalPages(), 1) }}. oldal
            </span>
            <div class="pagination-buttons">
              <button
                type="button"
                class="btn btn-secondary btn-sm"
                [disabled]="currentPage() === 0 || loading()"
                (click)="goToPage(currentPage() - 1)"
              >
                Előző
              </button>
              <button
                type="button"
                class="btn btn-secondary btn-sm"
                [disabled]="currentPage() + 1 >= totalPages() || loading()"
                (click)="goToPage(currentPage() + 1)"
              >
                Következő
              </button>
            </div>
          </div>
        }
      </section>
    </div>
  `,
  styles: [
    `
      .filters-grid {
        display: grid;
        grid-template-columns: 1fr 1fr 180px auto;
        gap: 12px;
        align-items: end;
      }
      @media (max-width: 760px) {
        .filters-grid {
          grid-template-columns: 1fr;
        }
      }
      .filter-actions {
        display: flex;
        gap: 8px;
      }
      .book-title {
        font-weight: 500;
        color: var(--text);
      }
      .book-author {
        color: var(--text-muted);
      }
      .book-form-card h2 {
        margin: 0 0 14px;
        font-family: var(--font-serif);
        font-size: 1.25rem;
        font-weight: 500;
      }
      .book-form-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
        gap: 12px;
      }
      .book-form-actions {
        grid-column: 1 / -1;
        display: flex;
        gap: 8px;
        margin-top: 4px;
      }
      .pagination-bar {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-between;
        align-items: center;
        gap: 12px;
        margin-top: 16px;
        padding-top: 12px;
        border-top: 1px solid var(--border);
      }
      .pagination-info {
        font-size: 0.88rem;
        color: var(--text-muted);
      }
      .pagination-buttons {
        display: flex;
        gap: 8px;
      }
    `,
  ],
})
export class BooksPageComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(LibraryApiService);
  protected readonly authService = inject(AuthService);
  protected readonly Math = Math;

  readonly books = signal<BookResponse[]>([]);
  readonly loading = signal(false);
  readonly savingBook = signal(false);
  readonly busyBookId = signal<number | null>(null);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly pageSize = 10;

  readonly showBookForm = signal(false);
  readonly editingBookId = signal<number | null>(null);

  readonly feedbackMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly filterForm = this.fb.nonNullable.group({
    title: [''],
    author: [''],
    available: ['ALL' as 'ALL' | 'TRUE' | 'FALSE'],
  });

  readonly bookForm = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    author: ['', [Validators.required, Validators.maxLength(255)]],
    isbn: ['', [Validators.required, Validators.maxLength(20)]],
    publishedYear: [null as number | null, [Validators.min(1000), Validators.max(2100)]],
  });

  ngOnInit(): void {
    this.loadBooks();
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.loadBooks();
  }

  resetFilters(): void {
    this.filterForm.setValue({ title: '', author: '', available: 'ALL' });
    this.currentPage.set(0);
    this.loadBooks();
  }

  goToPage(page: number): void {
    this.currentPage.set(page);
    this.loadBooks();
  }

  borrowBook(book: BookResponse): void {
    this.busyBookId.set(book.id);
    this.clearMessages();

    this.api
      .borrowBook(book.id)
      .pipe(finalize(() => this.busyBookId.set(null)))
      .subscribe({
        next: (loan) => {
          this.feedbackMessage.set(`Sikeres kölcsönzés: „${loan.bookTitle}” (határidő: ${loan.dueDate.slice(0, 10)}).`);
          this.loadBooks();
        },
        error: (err) => {
          this.errorMessage.set(extractApiError(err, 'A kölcsönzés sikertelen.'));
        },
      });
  }

  openCreateForm(): void {
    this.editingBookId.set(null);
    this.bookForm.reset({ title: '', author: '', isbn: '', publishedYear: null });
    this.showBookForm.set(true);
    this.clearMessages();
  }

  openEditForm(book: BookResponse): void {
    this.editingBookId.set(book.id);
    this.bookForm.setValue({
      title: book.title,
      author: book.author,
      isbn: book.isbn,
      publishedYear: book.publishedYear,
    });
    this.showBookForm.set(true);
    this.clearMessages();
  }

  closeBookForm(): void {
    this.showBookForm.set(false);
    this.editingBookId.set(null);
  }

  submitBookForm(): void {
    if (this.bookForm.invalid) {
      this.bookForm.markAllAsTouched();
      return;
    }

    const raw = this.bookForm.getRawValue();
    const payload: BookRequest = {
      title: raw.title ?? '',
      author: raw.author ?? '',
      isbn: raw.isbn ?? '',
      publishedYear: raw.publishedYear !== null && raw.publishedYear !== undefined ? Number(raw.publishedYear) : null,
    };

    const editId = this.editingBookId();
    this.savingBook.set(true);
    this.clearMessages();

    const request$ = editId === null ? this.api.createBook(payload) : this.api.updateBook(editId, payload);

    request$.pipe(finalize(() => this.savingBook.set(false))).subscribe({
      next: (saved) => {
        this.feedbackMessage.set(
          editId === null ? `Új könyv létrehozva: „${saved.title}”.` : `Könyv frissítve: „${saved.title}”.`
        );
        this.closeBookForm();
        this.loadBooks();
      },
      error: (err) => {
        this.errorMessage.set(extractApiError(err, 'A könyv mentése sikertelen.'));
      },
    });
  }

  deleteBook(book: BookResponse): void {
    if (!confirm(`Biztosan törlöd a(z) „${book.title}” című könyvet?`)) {
      return;
    }

    this.busyBookId.set(book.id);
    this.clearMessages();

    this.api
      .deleteBook(book.id)
      .pipe(finalize(() => this.busyBookId.set(null)))
      .subscribe({
        next: () => {
          this.feedbackMessage.set(`Könyv törölve: „${book.title}”.`);
          this.loadBooks();
        },
        error: (err) => {
          this.errorMessage.set(extractApiError(err, 'A könyv törlése sikertelen.'));
        },
      });
  }

  private loadBooks(): void {
    const raw = this.filterForm.getRawValue();
    const available =
      raw.available === 'TRUE' ? true : raw.available === 'FALSE' ? false : null;

    this.loading.set(true);
    this.api
      .getBooks({
        title: raw.title,
        author: raw.author,
        available,
        page: this.currentPage(),
        size: this.pageSize,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => {
          this.books.set(page.content);
          this.totalPages.set(page.totalPages);
          this.totalElements.set(page.totalElements);
        },
        error: (err) => {
          this.errorMessage.set(extractApiError(err, 'Nem sikerült betölteni a könyveket.'));
        },
      });
  }

  private clearMessages(): void {
    this.feedbackMessage.set(null);
    this.errorMessage.set(null);
  }
}
