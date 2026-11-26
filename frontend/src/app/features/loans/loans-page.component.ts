import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { LoanResponse } from '../../core/models/library.models';
import { AuthService } from '../../core/services/auth.service';
import { extractApiError, LibraryApiService } from '../../core/services/library-api.service';

@Component({
  selector: 'app-loans-page',
  imports: [DatePipe],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1>{{ viewMode() === 'all' ? 'Összes kölcsönzés' : 'Kölcsönzéseim' }}</h1>
          <p class="page-subtitle">Aktív és lezárt kölcsönzések nyomon követése, visszahozás kezelése.</p>
        </div>

        @if (authService.isAdmin()) {
          <div class="view-controls">
            <div class="toggle-group">
              <button
                type="button"
                class="btn btn-sm"
                [class.btn-primary]="viewMode() === 'my'"
                [class.btn-secondary]="viewMode() !== 'my'"
                (click)="setViewMode('my')"
              >
                Saját kölcsönzéseim
              </button>
              <button
                type="button"
                class="btn btn-sm"
                [class.btn-primary]="viewMode() === 'all'"
                [class.btn-secondary]="viewMode() !== 'all'"
                (click)="setViewMode('all')"
              >
                Összes kölcsönzés
              </button>
            </div>

            @if (viewMode() === 'all') {
              <label class="overdue-toggle">
                <input
                  type="checkbox"
                  [checked]="overdueOnly()"
                  (change)="toggleOverdueOnly($any($event.target).checked)"
                />
                <span>Csak késedelmesek</span>
              </label>
            }
          </div>
        }
      </div>

      @if (feedbackMessage()) {
        <div class="alert alert-success" role="status">{{ feedbackMessage() }}</div>
      }
      @if (errorMessage()) {
        <div class="alert alert-error" role="alert">{{ errorMessage() }}</div>
      }

      <section class="card">
        @if (loading()) {
          <p class="empty-state">Kölcsönzések betöltése...</p>
        } @else if (loans().length === 0) {
          <p class="empty-state">Jelenleg nincs megjeleníthető kölcsönzés.</p>
        } @else {
          <div class="table-wrapper">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Könyv</th>
                  <th>ISBN</th>
                  @if (viewMode() === 'all') {
                    <th>Tag</th>
                  }
                  <th>Kölcsönözve</th>
                  <th>Határidő</th>
                  <th>Állapot</th>
                  <th>Művelet</th>
                </tr>
              </thead>
              <tbody>
                @for (loan of loans(); track loan.id) {
                  <tr>
                    <td><strong>{{ loan.bookTitle }}</strong></td>
                    <td><code>{{ loan.bookIsbn }}</code></td>
                    @if (viewMode() === 'all') {
                      <td>
                        <div>{{ loan.userFullName }}</div>
                        <small class="muted-email">{{ loan.userEmail }}</small>
                      </td>
                    }
                    <td>{{ loan.borrowedAt | date: 'yyyy.MM.dd' }}</td>
                    <td>{{ loan.dueDate | date: 'yyyy.MM.dd' }}</td>
                    <td>
                      @if (loan.returnedAt) {
                        <span class="badge badge-neutral">
                          Visszahozva ({{ loan.returnedAt | date: 'yyyy.MM.dd' }})
                        </span>
                      } @else if (loan.overdue) {
                        <span class="badge badge-danger">Késedelmes</span>
                      } @else {
                        <span class="badge badge-success">Aktív</span>
                      }
                    </td>
                    <td>
                      @if (!loan.returnedAt) {
                        <button
                          type="button"
                          class="btn btn-primary btn-sm"
                          [disabled]="returningLoanId() === loan.id"
                          (click)="returnLoan(loan)"
                        >
                          {{ returningLoanId() === loan.id ? 'Mentés...' : 'Visszahozás' }}
                        </button>
                      } @else {
                        <span class="muted-done">Lezárva</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    </div>
  `,
  styles: [
    `
      .view-controls {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px;
      }
      .toggle-group {
        display: flex;
        gap: 6px;
      }
      .overdue-toggle {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 0.9rem;
        font-weight: 500;
        cursor: pointer;
      }
      .muted-email {
        color: var(--text-muted);
      }
      .muted-done {
        color: var(--text-muted);
        font-size: 0.85rem;
      }
    `,
  ],
})
export class LoansPageComponent implements OnInit {
  private readonly api = inject(LibraryApiService);
  protected readonly authService = inject(AuthService);

  readonly loans = signal<LoanResponse[]>([]);
  readonly loading = signal(false);
  readonly returningLoanId = signal<number | null>(null);
  readonly viewMode = signal<'my' | 'all'>('my');
  readonly overdueOnly = signal(false);
  readonly feedbackMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadLoans();
  }

  setViewMode(mode: 'my' | 'all'): void {
    this.viewMode.set(mode);
    if (mode === 'my') {
      this.overdueOnly.set(false);
    }
    this.loadLoans();
  }

  toggleOverdueOnly(checked: boolean): void {
    this.overdueOnly.set(checked);
    this.loadLoans();
  }

  returnLoan(loan: LoanResponse): void {
    this.returningLoanId.set(loan.id);
    this.feedbackMessage.set(null);
    this.errorMessage.set(null);

    this.api
      .returnLoan(loan.id)
      .pipe(finalize(() => this.returningLoanId.set(null)))
      .subscribe({
        next: (returned) => {
          this.feedbackMessage.set(`Könyv visszahozva: „${returned.bookTitle}”.`);
          this.loadLoans();
        },
        error: (err) => {
          this.errorMessage.set(extractApiError(err, 'A visszahozás sikertelen.'));
        },
      });
  }

  private loadLoans(): void {
    this.loading.set(true);
    const request$ =
      this.viewMode() === 'all' && this.authService.isAdmin()
        ? this.api.getAllLoans(this.overdueOnly() ? true : null)
        : this.api.getMyLoans();

    request$.pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (items) => this.loans.set(items),
      error: (err) => this.errorMessage.set(extractApiError(err, 'Nem sikerült betölteni a kölcsönzéseket.')),
    });
  }
}
