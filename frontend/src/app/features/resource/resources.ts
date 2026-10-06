import { Section } from '../../core/auth.service';

export type FieldType = 'text' | 'number' | 'date' | 'email' | 'select' | 'textarea' | 'image';

export interface Field {
  key: string;                 // property of the record; "emergencyContact.name" reaches into a nested object
  label: string;
  type: FieldType;
  options?: string[];          // for select
  required?: boolean;
  /** Pick the value from another resource; `fill` copies more properties of the picked record into this form. */
  lookup?: {
    source: 'cars' | 'clients';
    label: (row: any) => string; value?: (row: any) => string; fill?: Record<string, (row: any) => unknown>;
    /** Offer only the cars that are free between these two date fields. */
    availableBetween?: [string, string];
  };
  full?: boolean;              // span the whole form row
  default?: string;            // initial value of a new record
  showWhen?: { key: string; value: string }; // only shown (and asked for) when another field has this value
}

export interface Column {
  key: string;
  label: string;
  format?: 'money' | 'status' | 'image' | 'date';
}

export interface ResourceConfig {
  path: string;                // route and API path: /cars -> /api/cars
  title: string;
  singular: string;
  icon: string;
  section: Section;
  idKey?: string;              // business id generated on create when empty
  idPrefix?: string;
  columns: Column[];
  fields: Field[];
  searchKeys: string[];
  rights?: boolean;            // employees: access-rights checkboxes and login creation
  pdf?: boolean;               // contracts: printable agreement and invoice
  quote?: boolean;             // contracts: the price comes from the pricing rules (/api/pricing/quote)
}

const STATUS_CARS = ['Available', 'Rented', 'Maintenance'];

export const RESOURCES: Record<string, ResourceConfig> = {
  cars: {
    path: 'cars', title: 'Cars', singular: 'car', icon: 'directions_car', section: 'cars',
    columns: [
      { key: 'imageUrl', label: '', format: 'image' },
      { key: 'make', label: 'Make' }, { key: 'model', label: 'Model' }, { key: 'year', label: 'Year' },
      { key: 'licensePlate', label: 'Plate' }, { key: 'category', label: 'Category' },
      { key: 'dailyRate', label: 'Per day', format: 'money' }, { key: 'status', label: 'Status', format: 'status' }
    ],
    searchKeys: ['make', 'model', 'licensePlate', 'category', 'status'],
    fields: [
      { key: 'make', label: 'Make', type: 'text', required: true },
      { key: 'model', label: 'Model', type: 'text', required: true },
      { key: 'year', label: 'Year', type: 'number' },
      { key: 'licensePlate', label: 'License plate', type: 'text', required: true },
      { key: 'vin', label: 'VIN', type: 'text' },
      { key: 'color', label: 'Color', type: 'text' },
      { key: 'category', label: 'Category', type: 'select', options: ['Sedan', 'SUV', 'Luxury', 'Van', 'Compact'] },
      { key: 'status', label: 'Status', type: 'select', options: STATUS_CARS, required: true },
      { key: 'fuelType', label: 'Fuel', type: 'select', options: ['Gasoline', 'Diesel', 'Hybrid', 'Electric'] },
      { key: 'transmission', label: 'Transmission', type: 'select', options: ['Automatic', 'Manual'] },
      { key: 'mileage', label: 'Mileage (km)', type: 'number' },
      { key: 'dailyRate', label: 'Daily rate', type: 'number', required: true },
      { key: 'weeklyRate', label: 'Weekly rate', type: 'number' },
      { key: 'monthlyRate', label: 'Monthly rate', type: 'number' },
      { key: 'insuranceExpiry', label: 'Insurance expires', type: 'date' },
      { key: 'registrationExpiry', label: 'Registration expires', type: 'date' },
      { key: 'lastMaintenance', label: 'Last maintenance', type: 'date' },
      { key: 'nextMaintenance', label: 'Next maintenance', type: 'date' },
      { key: 'imageUrl', label: 'Picture', type: 'image', full: true }
    ]
  },
  clients: {
    path: 'clients', title: 'Clients', singular: 'client', icon: 'groups', section: 'clients',
    idKey: 'clientId', idPrefix: 'CL',
    columns: [
      { key: 'clientId', label: 'ID' }, { key: 'fullName', label: 'Name' }, { key: 'email', label: 'Email' },
      { key: 'phone', label: 'Phone' }, { key: 'totalRentals', label: 'Rentals' },
      { key: 'status', label: 'Status', format: 'status' }
    ],
    searchKeys: ['clientId', 'fullName', 'email', 'phone', 'status', 'nationalId'],
    fields: [
      { key: 'fullName', label: 'Full name', type: 'text', required: true },
      { key: 'email', label: 'Email', type: 'email' },
      { key: 'phone', label: 'Phone', type: 'text', required: true },
      { key: 'gender', label: 'Gender', type: 'select', options: ['Male', 'Female'] },
      { key: 'dateOfBirth', label: 'Date of birth', type: 'date' },
      { key: 'status', label: 'Status', type: 'select', options: ['Active', 'Pending Verification', 'Blacklisted'], required: true },
      { key: 'nationalId', label: 'National ID', type: 'text' },
      { key: 'drivingLicenseNumber', label: 'Driving license no.', type: 'text' },
      { key: 'licenseExpiryDate', label: 'License expires', type: 'date' },
      { key: 'address', label: 'Address', type: 'text', full: true },
      { key: 'emergencyContact.name', label: 'Emergency contact', type: 'text' },
      { key: 'emergencyContact.phone', label: 'Emergency phone', type: 'text' },
      { key: 'notes', label: 'Notes', type: 'textarea', full: true }
    ]
  },
  contracts: {
    path: 'contracts', title: 'Contracts', singular: 'contract', icon: 'description', section: 'contracts', pdf: true, quote: true,
    idKey: 'contractId', idPrefix: 'CT',
    columns: [
      { key: 'contractId', label: 'Contract' }, { key: 'clientName', label: 'Client' },
      { key: 'carMake', label: 'Make' }, { key: 'carModel', label: 'Model' },
      { key: 'startDate', label: 'From', format: 'date' }, { key: 'endDate', label: 'To', format: 'date' },
      { key: 'totalValue', label: 'Total', format: 'money' },
      { key: 'paymentStatus', label: 'Payment', format: 'status' }, { key: 'status', label: 'Status', format: 'status' }
    ],
    searchKeys: ['contractId', 'clientName', 'carMake', 'carModel', 'licensePlate', 'status', 'paymentStatus'],
    fields: [
      {
        key: 'clientName', label: 'Client', type: 'select', required: true,
        lookup: {
          source: 'clients', label: (c: any) => c.fullName,
          fill: { client: (c: any) => c.fullName, clientPhone: (c: any) => c.phone }
        }
      },
      {
        key: 'licensePlate', label: 'Car', type: 'select', required: true,
        lookup: {
          source: 'cars', label: (c: any) => `${c.make} ${c.model} (${c.licensePlate})`, value: (c: any) => c.licensePlate,
          availableBetween: ['startDate', 'endDate'],
          fill: { carMake: (c: any) => c.make, carModel: (c: any) => c.model, car: (c: any) => `${c.make} ${c.model}`, dailyRate: (c: any) => c.dailyRate }
        }
      },
      { key: 'rentalType', label: 'Rental type', type: 'select', options: ['Daily', 'Weekly', 'Monthly'] },
      { key: 'startDate', label: 'Start date', type: 'date', required: true },
      { key: 'endDate', label: 'End date', type: 'date', required: true },
      { key: 'dailyRate', label: 'Daily rate', type: 'number' },
      { key: 'totalValue', label: 'Total value', type: 'number' },
      { key: 'deposit', label: 'Deposit', type: 'number' },
      { key: 'status', label: 'Status', type: 'select', options: ['Reserved', 'Active', 'Completed', 'Canceled'], required: true },
      { key: 'paymentStatus', label: 'Payment', type: 'select', options: ['Pending', 'Partial', 'Paid'] },
      { key: 'paymentMethod', label: 'Payment method', type: 'select', options: ['Cash', 'Card', 'Bank transfer'] },
      { key: 'notes', label: 'Notes', type: 'textarea', full: true }
    ]
  },
  expenses: {
    path: 'expenses', title: 'Expenses', singular: 'expense', icon: 'receipt_long', section: 'expenses',
    idKey: 'expenseId', idPrefix: 'EX',
    columns: [
      { key: 'expenseId', label: 'ID' }, { key: 'category', label: 'Category' }, { key: 'description', label: 'Description' },
      { key: 'date', label: 'Date', format: 'date' }, { key: 'amount', label: 'Amount', format: 'money' },
      { key: 'status', label: 'Status', format: 'status' }
    ],
    searchKeys: ['expenseId', 'category', 'description', 'paidBy', 'status', 'linkedCar'],
    fields: [
      { key: 'category', label: 'Category', type: 'select', options: ['Maintenance', 'Insurance', 'Fuel', 'Marketing', 'Rent', 'Salary', 'Other'], required: true },
      { key: 'description', label: 'Description', type: 'text', required: true, full: true },
      { key: 'amount', label: 'Amount', type: 'number', required: true },
      { key: 'date', label: 'Date', type: 'date', required: true },
      { key: 'paidBy', label: 'Paid by', type: 'text' },
      { key: 'status', label: 'Status', type: 'select', options: ['paid', 'pending', 'overdue'], required: true },
      { key: 'linkedCar', label: 'Linked car', type: 'select', lookup: { source: 'cars', label: (c: any) => `${c.make} ${c.model}` } },
      { key: 'notes', label: 'Notes', type: 'textarea', full: true }
    ]
  },
  partners: {
    path: 'partners', title: 'Companies', singular: 'company', icon: 'handshake', section: 'partners',
    idKey: 'partnerId', idPrefix: 'PT',
    columns: [
      { key: 'companyName', label: 'Company' }, { key: 'type', label: 'Type' }, { key: 'contactPerson', label: 'Contact' },
      { key: 'phone', label: 'Phone' }, { key: 'status', label: 'Status', format: 'status' }
    ],
    searchKeys: ['companyName', 'type', 'contactPerson', 'email', 'status'],
    fields: [
      { key: 'companyName', label: 'Company name', type: 'text', required: true },
      { key: 'type', label: 'Type', type: 'select', options: ['Insurance', 'Maintenance', 'Tour operator', 'Dealer', 'Other'] },
      { key: 'contactPerson', label: 'Contact person', type: 'text' },
      { key: 'phone', label: 'Phone', type: 'text' },
      { key: 'email', label: 'Email', type: 'email' },
      { key: 'website', label: 'Website', type: 'text' },
      { key: 'status', label: 'Status', type: 'select', options: ['Active', 'Pending Approval', 'Suspended'], required: true },
      { key: 'partnershipStartDate', label: 'Partner since', type: 'date' },
      { key: 'agreementReference', label: 'Agreement reference', type: 'text' },
      { key: 'address', label: 'Address', type: 'text', full: true },
      { key: 'notes', label: 'Notes', type: 'textarea', full: true }
    ]
  },
  'pricing-rules': {
    path: 'pricing-rules', title: 'Pricing rules', singular: 'pricing rule', icon: 'sell', section: 'employees',
    columns: [
      { key: 'name', label: 'Rule' }, { key: 'type', label: 'Type' }, { key: 'startDate', label: 'From', format: 'date' },
      { key: 'endDate', label: 'To', format: 'date' }, { key: 'multiplier', label: 'Rate x' },
      { key: 'minDays', label: 'From days' }, { key: 'discountPercent', label: 'Discount %' }, { key: 'category', label: 'Category' }
    ],
    searchKeys: ['name', 'type', 'category'],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true, full: true },
      { key: 'type', label: 'Kind of rule', type: 'select', options: ['SEASON', 'LONG_STAY'], required: true, default: 'SEASON' },
      { key: 'category', label: 'Only for category (empty: all)', type: 'select', options: ['Sedan', 'SUV', 'Luxury', 'Van', 'Compact'] },
      { key: 'startDate', label: 'Season starts', type: 'date', required: true, showWhen: { key: 'type', value: 'SEASON' } },
      { key: 'endDate', label: 'Season ends', type: 'date', required: true, showWhen: { key: 'type', value: 'SEASON' } },
      { key: 'multiplier', label: 'Daily rate multiplier (1.3 = +30%)', type: 'number', required: true, showWhen: { key: 'type', value: 'SEASON' } },
      { key: 'minDays', label: 'From how many days', type: 'number', required: true, showWhen: { key: 'type', value: 'LONG_STAY' } },
      { key: 'discountPercent', label: 'Discount on the total (%)', type: 'number', required: true, showWhen: { key: 'type', value: 'LONG_STAY' } }
    ]
  },
  employees: {
    path: 'employees', title: 'Employees', singular: 'employee', icon: 'badge', section: 'employees', rights: true,
    idKey: 'employeeId', idPrefix: 'EMP',
    columns: [
      { key: 'fullName', label: 'Name' }, { key: 'role', label: 'Job title' }, { key: 'department', label: 'Department' },
      { key: 'email', label: 'Email' }, { key: 'status', label: 'Status', format: 'status' }
    ],
    searchKeys: ['fullName', 'role', 'department', 'email', 'status'],
    fields: [
      { key: 'fullName', label: 'Full name', type: 'text', required: true },
      { key: 'email', label: 'Email (also links the login)', type: 'email', required: true },
      { key: 'phone', label: 'Phone', type: 'text' },
      { key: 'role', label: 'Job title', type: 'text' },
      { key: 'department', label: 'Department', type: 'select', options: ['Managers Department', 'Accounts Department', 'Marketing Department', 'IT Department', 'Operations'] },
      { key: 'status', label: 'Status', type: 'select', options: ['Active', 'On Leave', 'Suspended'], required: true },
      { key: 'dateJoined', label: 'Joined', type: 'date' },
      { key: 'birthdate', label: 'Birth date', type: 'date' },
      { key: 'gender', label: 'Gender', type: 'select', options: ['Male', 'Female'] },
      { key: 'salary', label: 'Salary', type: 'number' },
      { key: 'address', label: 'Address', type: 'text', full: true },
      { key: 'notes', label: 'Notes', type: 'textarea', full: true }
    ]
  }
};

/** Sections an employee can be given; each one unlocks the matching screens. */
export const RIGHTS: { key: string; label: string }[] = [
  { key: 'dashboard', label: 'Dashboard' }, { key: 'cars', label: 'Cars' }, { key: 'clients', label: 'Clients' },
  { key: 'contracts', label: 'Contracts and calendar' }, { key: 'payments', label: 'Payments' },
  { key: 'partners', label: 'Companies' }, { key: 'expenses', label: 'Expenses' }, { key: 'reports', label: 'Reports' }
];

export function getPath(row: any, path: string): any {
  return path.split('.').reduce((value, key) => (value == null ? undefined : value[key]), row);
}

export function setPath(row: any, path: string, value: unknown): void {
  const keys = path.split('.');
  let target = row;
  keys.slice(0, -1).forEach(key => (target = target[key] ??= {}));
  target[keys[keys.length - 1]] = value;
}
