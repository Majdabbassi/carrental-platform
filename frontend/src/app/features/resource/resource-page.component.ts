import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ConfirmDialogComponent, ResourceFormDialogComponent, AccountDialogComponent } from './resource-dialogs.component';
import { Column, RESOURCES, ResourceConfig, getPath } from './resources';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-resource-page',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule, MatDialogModule, MatSnackBarModule, MatTooltipModule],
  template: `
    <div class="page-head">
      <div>
        <h1>{{ config.title }}</h1>
        <p class="muted">{{ filtered().length }} of {{ rows().length }} {{ config.title.toLowerCase() }}</p>
      </div>
      <button mat-flat-button color="primary" (click)="edit()"><mat-icon>add</mat-icon> Add {{ config.singular }}</button>
    </div>

    <div class="card">
      <div class="toolbar">
        <div class="search">
          <mat-icon>search</mat-icon>
          <input type="search" placeholder="Search..." [value]="query()" (input)="setQuery($any($event.target).value)">
        </div>
      </div>

      <div class="table-wrap">
        <table mat-table [dataSource]="pageRows()">
          @for (col of config.columns; track col.key) {
            <ng-container [matColumnDef]="col.key">
              <th mat-header-cell *matHeaderCellDef>{{ col.label }}</th>
              <td mat-cell *matCellDef="let row">
                @switch (col.format) {
                  @case ('status') { <span class="chip" [class]="chipClass(value(row, col))">{{ value(row, col) }}</span> }
                  @case ('money') { {{ value(row, col) | number: '1.0-2' }} }
                  @case ('image') { <img class="thumb" [src]="value(row, col) || fallback" alt=""> }
                  @case ('date') { {{ value(row, col) | date: 'mediumDate' }} }
                  @default { {{ value(row, col) }} }
                }
              </td>
            </ng-container>
          }
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row" class="actions">
              @if (config.pdf) {
                <button mat-icon-button matTooltip="Rental agreement (PDF)" (click)="pdf(row, 'contract')"><mat-icon>picture_as_pdf</mat-icon></button>
                <button mat-icon-button matTooltip="Invoice (PDF)" (click)="pdf(row, 'invoice')"><mat-icon>receipt</mat-icon></button>
              }
              @if (config.rights) {
                <button mat-icon-button matTooltip="Create or reset the login" (click)="account(row)"><mat-icon>vpn_key</mat-icon></button>
              }
              <button mat-icon-button matTooltip="Edit" (click)="edit(row)"><mat-icon>edit</mat-icon></button>
              <button mat-icon-button matTooltip="Delete" (click)="remove(row)"><mat-icon>delete_outline</mat-icon></button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="displayed"></tr>
          <tr mat-row *matRowDef="let row; columns: displayed"></tr>
        </table>
        @if (!loading() && filtered().length === 0) {
          <div class="empty">Nothing here yet.</div>
        }
      </div>

      <div class="pager">
        <span class="muted">Page {{ page() + 1 }} of {{ pages() }}</span>
        <button mat-icon-button [disabled]="page() === 0" (click)="page.set(page() - 1)"><mat-icon>chevron_left</mat-icon></button>
        <button mat-icon-button [disabled]="page() + 1 >= pages()" (click)="page.set(page() + 1)"><mat-icon>chevron_right</mat-icon></button>
      </div>
    </div>
  `
})
export class ResourcePageComponent {
  private http = inject(HttpClient);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);

  readonly fallback = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='56' height='40'><rect width='56' height='40' rx='6' fill='%23e2e8f0'/></svg>";

  config: ResourceConfig = RESOURCES[this.route.snapshot.data['resource']];
  displayed = [...this.config.columns.map(c => c.key), 'actions'];

  rows = signal<any[]>([]);
  loading = signal(true);
  query = signal('');
  page = signal(0);

  filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    if (!q) {
      return this.rows();
    }
    return this.rows().filter(row => this.config.searchKeys.some(key => String(getPath(row, key) ?? '').toLowerCase().includes(q)));
  });
  pages = computed(() => Math.max(1, Math.ceil(this.filtered().length / PAGE_SIZE)));
  pageRows = computed(() => this.filtered().slice(this.page() * PAGE_SIZE, (this.page() + 1) * PAGE_SIZE));

  constructor() {
    this.load();
  }

  value(row: any, col: Column): any {
    return getPath(row, col.key);
  }

  chipClass(status: string | null | undefined): string {
    const s = (status ?? '').toLowerCase();
    if (['active', 'available', 'paid', 'completed'].includes(s)) return 'chip green';
    if (['pending', 'reserved', 'partial', 'on leave', 'pending verification', 'pending approval'].includes(s)) return 'chip amber';
    if (['rented'].includes(s)) return 'chip blue';
    if (['maintenance', 'overdue', 'suspended', 'blacklisted', 'canceled'].includes(s)) return 'chip red';
    return 'chip';
  }

  setQuery(value: string): void {
    this.query.set(value);
    this.page.set(0);
  }

  load(): void {
    this.loading.set(true);
    this.http.get<any[]>(`/api/${this.config.path}`).subscribe({
      next: rows => { this.rows.set(rows); this.loading.set(false); },
      error: () => { this.loading.set(false); this.notify('Could not load the list'); }
    });
  }

  edit(row?: any): void {
    this.dialog.open(ResourceFormDialogComponent, { width: '720px', maxWidth: '96vw', data: { config: this.config, row } })
      .afterClosed().subscribe(saved => {
        if (saved) {
          this.notify(row ? 'Saved' : `${this.config.singular[0].toUpperCase()}${this.config.singular.slice(1)} added`);
          this.load();
        }
      });
  }

  remove(row: any): void {
    this.dialog.open(ConfirmDialogComponent, { data: { message: `Delete this ${this.config.singular}? This cannot be undone.` } })
      .afterClosed().subscribe(confirmed => {
        if (!confirmed) {
          return;
        }
        this.http.delete(`/api/${this.config.path}/${row.id}`).subscribe({
          next: () => { this.notify('Deleted'); this.load(); },
          error: () => this.notify('Could not delete')
        });
      });
  }

  /** Fetches the PDF with the login token and opens it in a new tab. */
  pdf(row: any, type: 'contract' | 'invoice'): void {
    this.http.get(`/api/${this.config.path}/${row.id}/pdf?type=${type}`, { responseType: 'blob' }).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => this.notify('Could not create the PDF')
    });
  }

  account(row: any): void {
    this.dialog.open(AccountDialogComponent, { width: '420px', data: { employee: row } });
  }

  private notify(message: string): void {
    this.snack.open(message, 'OK', { duration: 3000 });
  }
}
