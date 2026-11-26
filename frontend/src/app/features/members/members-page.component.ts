import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { MemberResponse } from '../../core/models/library.models';
import { extractApiError, LibraryApiService } from '../../core/services/library-api.service';

@Component({
  selector: 'app-members-page',
  imports: [DatePipe],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1>Regisztrált tagok</h1>
          <p class="page-subtitle">Adminisztrátori nézet a könyvtár összes felhasználójáról.</p>
        </div>
      </div>

      @if (errorMessage()) {
        <div class="alert alert-error" role="alert">{{ errorMessage() }}</div>
      }

      <section class="card">
        @if (loading()) {
          <p class="empty-state">Tagok betöltése...</p>
        } @else if (members().length === 0) {
          <p class="empty-state">Nincsenek regisztrált tagok.</p>
        } @else {
          <div class="table-wrapper">
            <table class="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Teljes név</th>
                  <th>Email</th>
                  <th>Szerepkör</th>
                  <th>Regisztráció</th>
                </tr>
              </thead>
              <tbody>
                @for (member of members(); track member.id) {
                  <tr>
                    <td>#{{ member.id }}</td>
                    <td><strong>{{ member.fullName }}</strong></td>
                    <td>{{ member.email }}</td>
                    <td>
                      <span
                        class="badge"
                        [class.badge-warning]="member.role === 'ADMIN'"
                        [class.badge-neutral]="member.role === 'MEMBER'"
                      >
                        {{ member.role }}
                      </span>
                    </td>
                    <td>{{ member.createdAt | date: 'yyyy.MM.dd HH:mm' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    </div>
  `,
})
export class MembersPageComponent implements OnInit {
  private readonly api = inject(LibraryApiService);

  readonly members = signal<MemberResponse[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.api
      .getAllMembers()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (list) => this.members.set(list),
        error: (err) => this.errorMessage.set(extractApiError(err, 'Nem sikerült betölteni a tagokat.')),
      });
  }
}
