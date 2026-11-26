import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { PlataformaSesionService } from './plataforma-sesion.service';
import { Acceso } from './acceso';
import { SesionService } from './sesion.service';

/** Zona de la empresa ({@code /app}): usuario interno con sesión. El proveedor ya no tiene portal (spec 031). */
export const empresaGuard: CanActivateFn = () => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  if (!sesion.autenticado()) {
    return router.parseUrl('/ingreso');
  }
  return sesion.esProveedor() ? router.parseUrl('/ingreso') : true;
};

/**
 * Pantalla con roles y módulo: {@code data: {roles: [...], modulo: '...'}}. Sin permiso vuelve al inicio; el
 * backend igual responde 403 si alguien llega por otro camino.
 */
export const permisoGuard: CanActivateFn = route => {
  const sesion = inject(SesionService);
  const roles = route.data['roles'] as Acceso | undefined;
  const modulo = route.data['modulo'] as string | undefined;
  return sesion.tieneRol(roles) && sesion.tieneModulo(modulo) ? true : inject(Router).parseUrl('/app/inicio');
};

/** Consola del dueño ({@code /plataforma}): sesión de plataforma con MFA. */
export const plataformaGuard: CanActivateFn = () =>
  inject(PlataformaSesionService).autenticado() ? true : inject(Router).parseUrl('/plataforma/ingreso');

/** Pantalla de ingreso: si ya hay sesión, lleva a donde corresponde. */
export const invitadoGuard: CanActivateFn = () => {
  const sesion = inject(SesionService);
  if (!sesion.autenticado()) {
    return true;
  }
  return sesion.esProveedor() ? true : inject(Router).parseUrl('/app/inicio');
};
