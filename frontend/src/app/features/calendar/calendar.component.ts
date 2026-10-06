import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

interface CalendarEvent {
  id: string; start: string; end: string; title: string; className: string;
  extendedProps: { car?: string; client?: string; contractId?: string; paymentStatus?: string };
}

interface Day { date: Date; inMonth: boolean; today: boolean; events: CalendarEvent[]; }

/** A month view of every rental: one coloured bar per contract on each day it covers. */
@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  template: `
    <div class="page-head">
      <div><h1>Calendar</h1><p class="muted">Rentals by day</p></div>
      <div class="month-nav">
        <button mat-icon-button (click)="shift(-1)"><mat-icon>chevron_left</mat-icon></button>
        <strong>{{ month() | date: 'MMMM y' }}</strong>
        <button mat-icon-button (click)="shift(1)"><mat-icon>chevron_right</mat-icon></button>
        <button mat-stroked-button (click)="today()">Today</button>
      </div>
    </div>

    <div class="legend">
      <span><i class="dot green"></i> Active</span><span><i class="dot amber"></i> Reserved</span>
      <span><i class="dot grey"></i> Completed</span><span><i class="dot red"></i> Canceled</span>
    </div>

    <div class="card calendar">
      @for (name of weekdays; track name) { <div class="weekday">{{ name }}</div> }
      @for (day of days(); track day.date.getTime()) {
        <div class="day" [class.dim]="!day.inMonth" [class.today]="day.today">
          <span class="num">{{ day.date.getDate() }}</span>
          @for (event of day.events.slice(0, 3); track event.id) {
            <button class="event" [class]="color(event)" [title]="event.title" (click)="selected.set(event)">{{ event.title }}</button>
          }
          @if (day.events.length > 3) { <span class="more">+{{ day.events.length - 3 }} more</span> }
        </div>
      }
    </div>

    @if (selected(); as e) {
      <div class="backdrop" (click)="selected.set(null)">
        <div class="card detail" (click)="$event.stopPropagation()">
          <h3>{{ e.title }}</h3>
          <p><strong>Contract</strong> {{ e.extendedProps.contractId }}</p>
          <p><strong>Car</strong> {{ e.extendedProps.car }}</p>
          <p><strong>Client</strong> {{ e.extendedProps.client }}</p>
          <p><strong>Dates</strong> {{ e.start | date: 'mediumDate' }} to {{ e.end | date: 'mediumDate' }}</p>
          <p><strong>Payment</strong> {{ e.extendedProps.paymentStatus }}</p>
          <button mat-flat-button color="primary" (click)="selected.set(null)">Close</button>
        </div>
      </div>
    }
  `
})
export class CalendarComponent {
  private http = inject(HttpClient);
  readonly weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  events = signal<CalendarEvent[]>([]);
  month = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  selected = signal<CalendarEvent | null>(null);

  days = computed<Day[]>(() => {
    const first = this.month();
    const offset = (first.getDay() + 6) % 7; // week starts on Monday
    const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset);
    const todayKey = new Date().toDateString();
    return Array.from({ length: 42 }, (_, i) => {
      const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
      return {
        date,
        inMonth: date.getMonth() === first.getMonth(),
        today: date.toDateString() === todayKey,
        events: this.events().filter(e => this.covers(e, date))
      };
    });
  });

  constructor() {
    this.http.get<CalendarEvent[]>('/api/calendar/events').subscribe(events => this.events.set(events));
  }

  private covers(event: CalendarEvent, day: Date): boolean {
    const start = new Date(event.start).setHours(0, 0, 0, 0);
    const end = new Date(event.end || event.start).setHours(0, 0, 0, 0);
    const t = new Date(day).setHours(0, 0, 0, 0);
    return t >= start && t <= end;
  }

  color(event: CalendarEvent): string {
    const c = event.className ?? '';
    if (c.includes('success')) return 'event green';
    if (c.includes('warning')) return 'event amber';
    if (c.includes('danger')) return 'event red';
    return 'event grey';
  }

  shift(months: number): void {
    const m = this.month();
    this.month.set(new Date(m.getFullYear(), m.getMonth() + months, 1));
  }

  today(): void {
    const now = new Date();
    this.month.set(new Date(now.getFullYear(), now.getMonth(), 1));
  }
}
