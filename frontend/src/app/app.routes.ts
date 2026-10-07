import { Routes } from '@angular/router';
import { authGuard, sectionGuard } from './core/guards';
import { RESOURCES } from './features/resource/resources';

const resourceRoutes: Routes = Object.values(RESOURCES).map(resource => ({
  path: resource.path,
  canActivate: [sectionGuard(resource.section)],
  data: { resource: resource.path },
  loadComponent: () => import('./features/resource/resource-page.component').then(m => m.ResourcePageComponent)
}));

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      {
        path: 'dashboard',
        canActivate: [sectionGuard('dashboard')],
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'calendar',
        canActivate: [sectionGuard('contracts')],
        loadComponent: () => import('./features/calendar/calendar.component').then(m => m.CalendarComponent)
      },
      {
        path: 'reports',
        canActivate: [sectionGuard('reports')],
        loadComponent: () => import('./features/reports/reports.component').then(m => m.ReportsComponent)
      },
      ...resourceRoutes,
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' }
    ]
  },
  { path: '**', redirectTo: '' }
];
