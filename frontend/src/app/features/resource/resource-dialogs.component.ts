import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { EMPTY, Subject, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs';
import { Field, RIGHTS, ResourceConfig, getPath, setPath } from './resources';

interface Quote {
  days: number; baseDailyRate: number; subtotal: number; total: number; averageDailyRate: number;
  discountLabel: string | null; discountAmount: number;
  lines: { label: string; from: string; to: string; days: number; rate: number; amount: number }[];
}

const control = (key: string) => key.replace(/\./g, '__');

/** Shrinks an uploaded picture so it stays small in the database (max 800 px wide, JPEG). */
function toSmallDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => {
      const image = new Image();
      image.onerror = () => reject(new Error('Not an image'));
      image.onload = () => {
        const scale = Math.min(1, 800 / image.width);
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(image.width * scale);
        canvas.height = Math.round(image.height * scale);
        canvas.getContext('2d')!.drawImage(image, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL('image/jpeg', 0.8));
      };
      image.src = reader.result as string;
    };
    reader.readAsDataURL(file);
  });
}

// ---------------------------------------------------------------------------------------------------------------------

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Are you sure?</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">Delete</button>
    </mat-dialog-actions>
  `
})
export class ConfirmDialogComponent {
  data = inject<{ message: string }>(MAT_DIALOG_DATA);
}

// ---------------------------------------------------------------------------------------------------------------------

@Component({
  selector: 'app-resource-form-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatCheckboxModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ data.row ? 'Edit' : 'Add' }} {{ config.singular }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-grid" (ngSubmit)="save()" id="resource-form">
        @for (field of config.fields; track field.key) {
          @if (!visible(field)) {
          } @else if (field.type === 'image') {
            <div class="image-field full">
              <img [src]="form.get(ctl(field.key))?.value || ''" alt="" class="preview" [class.hidden]="!form.get(ctl(field.key))?.value">
              <div>
                <label class="muted">{{ field.label }}</label><br>
                <input type="file" accept="image/*" (change)="pick($event, field)">
              </div>
            </div>
          } @else {
            <mat-form-field appearance="outline" [class.full]="field.full">
              <mat-label>{{ field.label }}</mat-label>
              @switch (field.type) {
                @case ('select') {
                  <mat-select [formControlName]="ctl(field.key)">
                    @if (!field.required) { <mat-option value="">-</mat-option> }
                    @for (option of options(field); track option.value) {
                      <mat-option [value]="option.value">{{ option.label }}</mat-option>
                    }
                  </mat-select>
                }
                @case ('textarea') { <textarea matInput rows="3" [formControlName]="ctl(field.key)"></textarea> }
                @case ('number') { <input matInput type="number" step="any" [formControlName]="ctl(field.key)"> }
                @case ('date') { <input matInput type="date" [formControlName]="ctl(field.key)"> }
                @case ('email') { <input matInput type="email" [formControlName]="ctl(field.key)"> }
                @default { <input matInput [formControlName]="ctl(field.key)"> }
              }
              @if (form.get(ctl(field.key))?.hasError('required') && submitted()) {
                <mat-error>Required</mat-error>
              }
            </mat-form-field>
          }
        }

        @if (config.rights) {
          <div class="full rights" formGroupName="rights">
            <h3>Access rights</h3>
            <p class="muted">The sections this employee may open. Administrators always see everything.</p>
            <div class="rights-grid">
              @for (right of rights; track right.key) {
                <mat-checkbox [formControlName]="right.key">{{ right.label }}</mat-checkbox>
              }
            </div>
          </div>
        }
      </form>
    </mat-dialog-content>
    <div class="dialog-foot">
    @if (quote(); as q) {
      <div class="quote">
        <h3>Price</h3>
        @for (line of q.lines; track line.from) {
          <div class="quote-line"><span>{{ line.label }}: {{ line.days }} {{ line.days === 1 ? 'day' : 'days' }} x {{ line.rate | number: '1.0-2' }}</span>
            <span>{{ line.amount | number: '1.2-2' }}</span></div>
        }
        @if (q.discountLabel) {
          <div class="quote-line"><span>{{ q.discountLabel }}</span><span>-{{ q.discountAmount | number: '1.2-2' }}</span></div>
        }
        <div class="quote-line total"><span>Total, {{ q.days }} {{ q.days === 1 ? 'day' : 'days' }}</span><span>{{ q.total | number: '1.2-2' }}</span></div>
      </div>
    }
    @if (availabilityNote()) { <p class="muted">{{ availabilityNote() }}</p> }
    @if (error()) { <p class="err">{{ error() }}</p> }
    </div>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" type="submit" form="resource-form" [disabled]="saving()">Save</button>
    </mat-dialog-actions>
  `
})
export class ResourceFormDialogComponent {
  private http = inject(HttpClient);
  private ref = inject(MatDialogRef<ResourceFormDialogComponent, boolean>);
  data = inject<{ config: ResourceConfig; row?: any }>(MAT_DIALOG_DATA);

  config = this.data.config;
  rights = RIGHTS;
  saving = signal(false);
  submitted = signal(false);
  error = signal('');
  quote = signal<Quote | null>(null);
  availabilityNote = signal('');
  lookups: Record<string, any[]> = {};
  private carried: Record<string, unknown> = {};
  form: FormGroup;

  constructor() {
    const controls: Record<string, FormControl> = {};
    for (const field of this.config.fields) {
      const initial = getPath(this.data.row ?? {}, field.key);
      controls[control(field.key)] = new FormControl(initial ?? field.default ?? '', field.required ? [Validators.required] : []);
    }
    const group: Record<string, any> = { ...controls };
    if (this.config.rights) {
      const current = this.data.row?.accessRights ?? {};
      group['rights'] = new FormGroup(Object.fromEntries(RIGHTS.map(r => [r.key, new FormControl(!!current[r.key])])));
    }
    this.form = new FormGroup(group);

    // lookups: load the lists once; picking a record can fill other fields
    for (const field of this.config.fields.filter(f => f.lookup)) {
      const source = field.lookup!.source;
      if (!field.lookup!.availableBetween) {
        this.http.get<any[]>(`/api/${source}`).subscribe(rows => (this.lookups[source] = rows));
      }
      this.form.get(control(field.key))!.valueChanges.subscribe(selected => this.fillFrom(field, selected));
    }

    // a contract offers only the cars that are free on its dates and is priced by the pricing rules
    const dated = this.config.fields.find(f => f.lookup?.availableBetween);
    if (dated) {
      this.watchAvailability(dated);
    }
    if (this.config.quote) {
      this.watchQuote();
    }
    this.syncVisibility();
    this.form.valueChanges.subscribe(() => this.syncVisibility());
  }

  private value(key: string): any {
    return this.form.get(control(key))?.value;
  }

  /** Cars free between the start and end dates (the contract being edited does not block its own car). */
  private watchAvailability(field: Field): void {
    const [fromKey, toKey] = field.lookup!.availableBetween!;
    const source = field.lookup!.source;
    const window$ = new Subject<void>();
    this.form.valueChanges.subscribe(() => window$.next());
    window$.pipe(
      map(() => `${this.value(fromKey)}|${this.value(toKey)}`),
      distinctUntilChanged(),
      debounceTime(150),
      switchMap(() => {
        const from = this.value(fromKey);
        const to = this.value(toKey);
        if (!from || !to || to <= from) {
          return this.http.get<any[]>(`/api/${source}`);
        }
        const exclude = this.data.row?.id ? `&excludeContract=${this.data.row.id}` : '';
        return this.http.get<any[]>(`/api/availability/${source}?from=${from}&to=${to}${exclude}`);
      })
    ).subscribe(rows => {
      this.lookups[source] = rows;
      const dates = this.value(fromKey) && this.value(toKey) && this.value(toKey) > this.value(fromKey);
      const chosen = this.value(field.key);
      this.availabilityNote.set(dates
        ? (chosen && !rows.some(c => c.licensePlate === chosen) ? 'The chosen car is not free on these dates.' : `${rows.length} cars are free on these dates.`)
        : '');
    });
    window$.next(); // first load: every car, or the free ones when a saved contract is opened
  }

  /** Price from the pricing rules whenever the car or the dates change (not when merely opening a saved contract). */
  private watchQuote(): void {
    let last = `${this.value('licensePlate')}|${this.value('startDate')}|${this.value('endDate')}`;
    const changes$ = new Subject<void>();
    this.form.valueChanges.subscribe(() => changes$.next());
    changes$.pipe(
      debounceTime(250),
      map(() => ({ plate: this.value('licensePlate'), from: this.value('startDate'), to: this.value('endDate') })),
      distinctUntilChanged((a, b) => a.plate === b.plate && a.from === b.from && a.to === b.to),
      switchMap(({ plate, from, to }) => {
        const key = `${plate}|${from}|${to}`;
        if (key === last || !plate || !from || !to || to <= from) {
          last = key;
          this.quote.set(null);
          return EMPTY;
        }
        last = key;
        return this.http.get<Quote>(`/api/pricing/quote?plate=${encodeURIComponent(plate)}&from=${from}&to=${to}`);
      })
    ).subscribe({
      next: quote => {
        this.quote.set(quote);
        this.form.get('dailyRate')?.setValue(quote.averageDailyRate, { emitEvent: false });
        this.form.get('totalValue')?.setValue(quote.total, { emitEvent: false });
      },
      error: () => this.quote.set(null)
    });
  }

  visible(field: Field): boolean {
    return !field.showWhen || this.value(field.showWhen.key) === field.showWhen.value;
  }

  /** Hidden fields are neither asked for nor validated. */
  private syncVisibility(): void {
    for (const field of this.config.fields.filter(f => f.showWhen)) {
      const target = this.form.get(control(field.key))!;
      if (this.visible(field) && target.disabled) {
        target.enable({ emitEvent: false });
      } else if (!this.visible(field) && target.enabled) {
        target.disable({ emitEvent: false });
      }
    }
  }

  ctl = control;

  options(field: Field): { value: string; label: string }[] {
    if (field.lookup) {
      const rows = this.lookups[field.lookup.source] ?? [];
      const options = rows.map(row => ({ value: (field.lookup!.value ?? field.lookup!.label)(row), label: field.lookup!.label(row) }));
      const current = this.form.get(control(field.key))?.value;
      if (current && !options.some(o => o.value === current)) {
        options.unshift({ value: current, label: String(current) });
      }
      return options;
    }
    return (field.options ?? []).map(option => ({ value: option, label: option }));
  }

  private fillFrom(field: Field, selected: unknown): void {
    const lookup = field.lookup!;
    if (!lookup.fill || !selected) {
      return;
    }
    const row = (this.lookups[lookup.source] ?? []).find(r => (lookup.value ?? lookup.label)(r) === selected);
    if (!row) {
      return;
    }
    for (const [key, pick] of Object.entries(lookup.fill)) {
      const target = this.form.get(control(key));
      if (target) {
        target.setValue(pick(row) as never, { emitEvent: true });
      } else {
        this.carried[key] = pick(row); // not an editable field, but stored with the record (client phone, car make...)
      }
    }
  }

  pick(event: Event, field: Field): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      toSmallDataUrl(file).then(url => this.form.get(control(field.key))!.setValue(url)).catch(() => undefined);
    }
  }

  save(): void {
    this.submitted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const payload: any = structuredClone(this.data.row ?? {});
    for (const field of this.config.fields) {
      let value: any = this.visible(field) ? this.form.get(control(field.key))!.value : null;
      if (field.type === 'number') {
        value = value === '' || value === null ? null : Number(value);
      } else if (value === '') {
        value = null;
      }
      setPath(payload, field.key, value);
    }
    for (const [key, value] of Object.entries(this.carried)) {
      setPath(payload, key, value);
    }
    if (this.config.rights) {
      payload.accessRights = { ...this.form.get('rights')!.value };
    }
    if (!this.data.row && this.config.idKey && !payload[this.config.idKey]) {
      payload[this.config.idKey] = `${this.config.idPrefix}-${Date.now().toString().slice(-6)}`;
    }

    this.saving.set(true);
    this.error.set('');
    const request = this.data.row
      ? this.http.put(`/api/${this.config.path}/${this.data.row.id}`, payload)
      : this.http.post(`/api/${this.config.path}`, payload);
    request.subscribe({
      next: () => this.ref.close(true),
      error: err => {
        this.saving.set(false);
        this.error.set(err?.error?.message ?? 'Could not save, please check the form');
      }
    });
  }
}

// ---------------------------------------------------------------------------------------------------------------------

@Component({
  selector: 'app-account-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Login for {{ data.employee.fullName }}</h2>
    <mat-dialog-content>
      <p class="muted">Creates the account this employee signs in with, or resets its password. Their access follows the
        rights set on their record.</p>
      @if (done()) {
        <p class="ok">Done. They can now sign in as <strong>{{ form.value.username }}</strong>.</p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="save()" id="account-form">
          <mat-form-field appearance="outline" class="full">
            <mat-label>Username</mat-label>
            <input matInput formControlName="username">
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Password (8 characters or more)</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="new-password">
          </mat-form-field>
          @if (error()) { <p class="err">{{ error() }}</p> }
        </form>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ done() ? 'Close' : 'Cancel' }}</button>
      @if (!done()) {
        <button mat-flat-button color="primary" type="submit" form="account-form" [disabled]="form.invalid || saving()">Save login</button>
      }
    </mat-dialog-actions>
  `
})
export class AccountDialogComponent {
  private http = inject(HttpClient);
  data = inject<{ employee: any }>(MAT_DIALOG_DATA);
  saving = signal(false);
  done = signal(false);
  error = signal('');
  form = new FormGroup({
    username: new FormControl((this.data.employee.email ?? '').split('@')[0] ?? '', [Validators.required]),
    password: new FormControl('', [Validators.required, Validators.minLength(8)])
  });

  save(): void {
    this.saving.set(true);
    this.error.set('');
    this.http.post(`/api/employees/${this.data.employee.id}/account`, this.form.value).subscribe({
      next: () => { this.saving.set(false); this.done.set(true); },
      error: err => {
        this.saving.set(false);
        this.error.set(err?.error?.message ?? 'Could not save the login');
      }
    });
  }
}
