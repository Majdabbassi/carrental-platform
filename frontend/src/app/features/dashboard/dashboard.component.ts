import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ChartConfiguration } from 'chart.js';
import { Chart, ArcElement, BarController, BarElement, CategoryScale, DoughnutController, Legend, LinearScale, Tooltip } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

Chart.register(ArcElement, BarController, BarElement, CategoryScale, DoughnutController, Legend, LinearScale, Tooltip);

interface AlertItem { id: number; type: string; severity: 'CRITICAL' | 'WARNING' | 'INFO'; title: string; message: string; dueDate: string; }

interface Summary {
  totalCars: number; availableCars: number; rentedCars: number; maintenanceCars: number;
  totalClients: number; activeClients: number; blacklistedClients: number; pendingVerificationClients: number;
  totalEmployees: number; activeEmployees: number; suspendedEmployees: number; onLeaveEmployees: number;
  totalPartners: number; activePartners: number; suspendedPartners: number; pendingPartners: number;
  totalIncome: number;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatTooltipModule, BaseChartDirective],
  template: `
    <div class="page-head">
      <div><h1>Dashboard</h1><p class="muted">Your agency at a glance</p></div>
    </div>

    @if (alerts().length) {
      <div class="card alerts">
        <div class="alerts-head"><h3><mat-icon>notifications_active</mat-icon> Needs your attention ({{ alerts().length }})</h3>
          <span class="muted">Checked every morning</span></div>
        @for (alert of alerts(); track alert.id) {
          <div class="alert" [class]="'alert ' + alert.severity.toLowerCase()">
            <mat-icon>{{ icon(alert) }}</mat-icon>
            <div class="alert-text"><strong>{{ alert.title }}</strong><span>{{ alert.message }}</span></div>
            <button mat-icon-button matTooltip="Dismiss" (click)="dismiss(alert)"><mat-icon>close</mat-icon></button>
          </div>
        }
      </div>
    }

    @if (s(); as s) {
      <div class="stats">
        <div class="stat"><mat-icon class="c-indigo">directions_car</mat-icon>
          <div><strong>{{ s.totalCars }}</strong><span>Cars in the fleet</span></div></div>
        <div class="stat"><mat-icon class="c-blue">key</mat-icon>
          <div><strong>{{ s.rentedCars }}</strong><span>Rented right now</span></div></div>
        <div class="stat"><mat-icon class="c-green">groups</mat-icon>
          <div><strong>{{ s.totalClients }}</strong><span>Clients</span></div></div>
        <div class="stat"><mat-icon class="c-amber">payments</mat-icon>
          <div><strong>{{ s.totalIncome | number: '1.0-0' }}</strong><span>Income from paid contracts</span></div></div>
      </div>

      <div class="chart-grid">
        <div class="card chart"><h3>Fleet status</h3>
          <canvas baseChart [type]="'doughnut'" [data]="fleet()" [options]="doughnut"></canvas></div>
        <div class="card chart"><h3>Clients</h3>
          <canvas baseChart [type]="'doughnut'" [data]="clients()" [options]="doughnut"></canvas></div>
        <div class="card chart wide"><h3>Team and partners</h3>
          <canvas baseChart [type]="'bar'" [data]="people()" [options]="bar"></canvas></div>
      </div>
    }
  `
})
export class DashboardComponent {
  private http = inject(HttpClient);
  s = signal<Summary | null>(null);
  alerts = signal<AlertItem[]>([]);

  readonly doughnut: ChartConfiguration<'doughnut'>['options'] = { responsive: true, plugins: { legend: { position: 'bottom' } }, cutout: '62%' };
  readonly bar: ChartConfiguration<'bar'>['options'] = { responsive: true, plugins: { legend: { position: 'bottom' } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } };

  constructor() {
    this.http.get<Summary>('/api/dashboard/summary').subscribe(summary => this.s.set(summary));
    this.http.get<AlertItem[]>('/api/alerts').subscribe({ next: alerts => this.alerts.set(alerts), error: () => undefined });
  }

  icon(alert: AlertItem): string {
    switch (alert.type) {
      case 'OVERDUE_RETURN': return 'alarm';
      case 'RETURN_DUE': return 'event_available';
      case 'MAINTENANCE': return 'build';
      default: return 'gpp_maybe';
    }
  }

  dismiss(alert: AlertItem): void {
    this.http.post(`/api/alerts/${alert.id}/dismiss`, {}).subscribe(() => this.alerts.update(list => list.filter(a => a.id !== alert.id)));
  }

  fleet(): ChartConfiguration<'doughnut'>['data'] {
    const s = this.s()!;
    return {
      labels: ['Available', 'Rented', 'Maintenance'],
      datasets: [{ data: [s.availableCars, s.rentedCars, s.maintenanceCars], backgroundColor: ['#22c55e', '#3b82f6', '#f59e0b'] }]
    };
  }

  clients(): ChartConfiguration<'doughnut'>['data'] {
    const s = this.s()!;
    return {
      labels: ['Active', 'Pending verification', 'Blacklisted'],
      datasets: [{ data: [s.activeClients, s.pendingVerificationClients, s.blacklistedClients], backgroundColor: ['#22c55e', '#f59e0b', '#ef4444'] }]
    };
  }

  people(): ChartConfiguration<'bar'>['data'] {
    const s = this.s()!;
    return {
      labels: ['Employees', 'Companies'],
      datasets: [
        { label: 'Active', data: [s.activeEmployees, s.activePartners], backgroundColor: '#22c55e' },
        { label: 'On leave / pending', data: [s.onLeaveEmployees, s.pendingPartners], backgroundColor: '#f59e0b' },
        { label: 'Suspended', data: [s.suspendedEmployees, s.suspendedPartners], backgroundColor: '#ef4444' }
      ]
    };
  }
}
