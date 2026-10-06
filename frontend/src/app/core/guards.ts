import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { AuthService, Section } from './auth.service';

/** Must be signed in (the profile is loaded once per page load). */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.hasToken) {
    return router.createUrlTree(['/login']);
  }
  if (auth.profile()) {
    return true;
  }
  return auth.loadProfile().pipe(map(profile => (profile ? true : router.createUrlTree(['/login']))));
};

/** Must be allowed to use one section of the back office; otherwise go to the first section that is allowed. */
export function sectionGuard(section: Section): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (auth.can(section)) {
      return of(true);
    }
    return of(router.createUrlTree([auth.firstAllowedRoute()]));
  };
}
