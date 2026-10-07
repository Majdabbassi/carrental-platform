import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ChartConfiguration } from 'chart.js';
import { Chart, BarController, BarElement, CategoryScale, Legend, LinearScale, Tooltip } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

Chart.register(BarController, BarElement, CategoryScale, Legend, LinearScale, Tooltip);

interface Report {
  from: string; to: string; currency: string;
  totals: { billed: number; collected: number; expenses: number; net: number; outstanding: number; fleetUtilizationPercent: number };
  months: { month: string; billed: number; collected: number; expenses: number; net: number }[];
  fleet: { plate: string; car: string; category: string; nightsRented: number; nightsInPeriod: number; utilizationPercent: number; billed: number }[];
  categories: { category: string; rentals: number; billed: number }[];
  debtors: { contractRef: number; contractId: string; clientName: string; endDate: string; total: number; paid: number; left: number }[];
}

/** The Reports section: money in and out per month, how busy each car was, and who still owes money. */
@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, BaseChartDirective],
  template: `
    <div class="page-head">
      <div><h1>Reports</h1><p class="muted">Billed, collected and spent per month, fleet use and open balances</p></div>
      <form class="range" (ngSubmit)="load()">
        <label>From <input type="month" name="from" [(ngModel)]="from" required></label>
        <label>To <input type="month" name="to" [(ngModel)]="to" required></label>
        <button mat-flat-button color="primary" type="submit"><mat-icon>refresh</mat-icon> Show</button>
      </form>
    </div>
    @if (error()) { <p class="err">{{ error() }}</p> }

    @if (r(); as r) {
      <div class="stats">
        <div class="stat"><mat-icon class="c-indigo">receipt</mat-icon>
          <div><strong>{{ r.totals.billed | number: '1.0-0' }}</strong><span>Billed ({{ r.currency }})</span></div></div>
        <div class="stat"><mat-icon class="c-green">payments</mat-icon>
          <div><strong>{{ r.totals.collected | number: '1.0-0' }}</strong><span>Collected, {{ r.totals.net | number: '1.0-0' }} after expenses</span></div></div>
        <div class="stat"><mat-icon class="c-amber">hourglass_bottom</mat-icon>
          <div><strong>{{ r.totals.outstanding | number: '1.0-0' }}</strong><span>Still owed on open contracts</span></div></div>
        <div class="stat"><mat-icon class="c-blue">directions_car</mat-icon>
          <div><strong>{{ r.totals.fleetUtilizationPercent }} %</strong><span>Fleet utilization</span></div></div>
      </div>

      <div class="card chart"><h3>Per month</h3>
        <canvas baseChart [type]="'bar'" [data]="monthly(r)" [options]="bar"></canvas></div>

      <div class="chart-grid report-grid">
        <div class="card"><h3 class="card-title">Fleet utilization</h3>
          <div class="table-wrap"><table class="plain">
            <thead><tr><th>Car</th><th>Category</th><th>Nights rented</th><th>Use</th><th>Billed</th></tr></thead>
            <tbody>
              @for (c of r.fleet; track c.plate) {
                <tr><td>{{ c.car }} <span class="muted">{{ c.plate }}</span></td><td>{{ c.category }}</td>
                  <td>{{ c.nightsRented }} / {{ c.nightsInPeriod }}</td>
                  <td><div class="meter"><span [style.width.%]="c.utilizationPercent"></span></div>{{ c.utilizationPercent }} %</td>
                  <td>{{ c.billed | number: '1.0-0' }}</td></tr>
              }
            </tbody>
          </table></div>
        </div>

        <div class="card"><h3 class="card-title">By category</h3>
          <div class="table-wrap"><table class="plain">
            <thead><tr><th>Category</th><th>Rentals</th><th>Billed</th></tr></thead>
            <tbody>
              @for (c of r.categories; track c.category) {
                <tr><td>{{ c.category }}</td><td>{{ c.rentals }}</td><td>{{ c.billed | number: '1.0-0' }}</td></tr>
              } @empty { <tr><td colspan="3" class="muted">No rental starts in this period</td></tr> }
            </tbody>
          </table></div>
        </div>

        <div class="card wide"><h3 class="card-title">Who still owes money</h3>
          <div class="table-wrap"><table class="plain">
            <thead><tr><th>Contract</th><th>Client</th><th>Ends</th><th>Total</th><th>Paid</th><th>Left</th></tr></thead>
            <tbody>
              @for (d of r.debtors; track d.contractRef) {
                <tr><td>{{ d.contractId }}</td><td>{{ d.clientName }}</td><td>{{ d.endDate | date: 'mediumDate' }}</td>
                  <td>{{ d.total | number: '1.0-2' }}</td><td>{{ d.paid | number: '1.0-2' }}</td><td><strong>{{ d.left | number: '1.0-2' }}</strong></td></tr>
              } @empty { <tr><td colspan="6" class="muted">Everything is paid</td></tr> }
            </tbody>
          </table></div>
        </div>
      </div>
    }
  `,
  styles: [`
    .range { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .range label { display: flex; flex-direction: column; font-size: 12px; color: var(--muted); }
    .range input { font: inherit; padding: 8px 10px; border: 1px solid var(--border); border-radius: 8px; background: var(--surface); }
    .chart { margin-bottom: 16px; }
    .report-grid .wide { grid-column: 1 / -1; }
    .card-title { padding: 16px 16px 0; }
    table.plain { width: 100%; border-collapse: collapse; font-size: 14px; }
    table.plain th { text-align: left; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; color: var(--muted); padding: 10px 16px; }
    table.plain td { padding: 10px 16px; border-top: 1px solid var(--border); white-space: nowrap; }
    .meter { display: inline-block; width: 70px; height: 8px; background: var(--bg); border-radius: 4px; margin-right: 8px; vertical-align: middle; overflow: hidden; }
    .meter span { display: block; height: 100%; background: var(--primary); }
    @media (max-width: 900px) { .report-grid { grid-template-columns: 1fr; } }
  `]
})
export class ReportsComponent {
  private http = inject(HttpClient);
  r = signal<Report | null>(null);
  error = signal('');
  to = new Date().toISOString().slice(0, 7);
  from = (() => { const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - 5); return d.toISOString().slice(0, 7); })();

  readonly bar: ChartConfiguration<'bar'>['options'] = {
    responsive: true, plugins: { legend: { position: 'bottom' } }, scales: { y: { beginAtZero: true } }
  };

  constructor() {
    this.load();
  }

  load(): void {
    this.error.set('');
    this.http.get<Report>(`/api/reports/summary?from=${this.from}&to=${this.to}`).subscribe({
      next: report => this.r.set(report),
      error: err => this.error.set(err?.error?.message ?? 'Could not load the report')
    });
  }

  monthly(r: Report): ChartConfiguration<'bar'>['data'] {
    return {
      labels: r.months.map(m => m.month),
      datasets: [
        { label: 'Billed', data: r.months.map(m => m.billed), backgroundColor: '#6366f1' },
        { label: 'Collected', data: r.months.map(m => m.collected), backgroundColor: '#22c55e' },
        { label: 'Expenses', data: r.months.map(m => m.expenses), backgroundColor: '#f59e0b' }
      ]
    };
  }
}
