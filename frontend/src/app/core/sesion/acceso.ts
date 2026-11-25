/**
 * Lista de roles que además dice qué permiso la reemplaza (spec 022). Las pantallas siguen escribiendo
 * {@code sesion.tieneRol(ROLES_VENTAS)}, pero cuando la sesión ya conoce sus permisos efectivos decide el permiso: así
 * un rol propio o un permiso sumado a mano también abre la pantalla. Con los roles de siempre el resultado es el mismo,
 * porque cada permiso lo tienen exactamente esos roles.
 */
export type Acceso = readonly string[] & {
  /** Permiso (o cualquiera de varios) que da el mismo acceso. */
  readonly permiso?: string | readonly string[];
  /** {@code true} si basta con ser usuario interno (todos menos el proveedor). */
  readonly interno?: boolean;
};

/**
 * @param roles   roles que tenían el acceso
 * @param permiso permiso equivalente (o varios: basta con uno)
 * @returns la misma lista de roles con el permiso pegado
 */
export function conPermiso(roles: string[], permiso: string | string[]): Acceso {
  return Object.defineProperty([...roles], 'permiso', { value: permiso, enumerable: false }) as Acceso;
}

/**
 * @param roles roles internos de siempre
 * @returns la lista marcada como "cualquier usuario interno"
 */
export function soloInternos(roles: string[]): Acceso {
  return Object.defineProperty([...roles], 'interno', { value: true, enumerable: false }) as Acceso;
}
