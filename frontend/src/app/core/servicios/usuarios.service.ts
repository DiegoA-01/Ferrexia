import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Pagina, Permiso, PermisoUsuario, Rol, RolNuevo, Usuario, UsuarioCambios, UsuarioNuevo } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Usuarios de la empresa (los maneja su administrador) y el perfil propio. */
@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);

  /** @param filtros texto (nombre o correo), estado y rol */
  buscar(filtros: { texto?: string | null; activo?: boolean | null; rol?: string | null }): Observable<Pagina<Usuario>> {
    return this.http.get<Pagina<Usuario>>(api('/api/usuarios'), {
      params: parametros({ texto: filtros.texto, activo: filtros.activo, rol: filtros.rol, size: 100, sort: 'nombreCompleto,asc' }),
    });
  }

  /** Plantillas del sistema y roles propios de la empresa, con sus permisos (spec 022). */
  roles(): Observable<Rol[]> {
    return this.http.get<Rol[]>(api('/api/roles'));
  }

  /** Catálogo de permisos por módulo. */
  catalogoPermisos(): Observable<Permiso[]> {
    return this.http.get<Permiso[]>(api('/api/roles/permisos'));
  }

  /** @param rol nombre, descripción y permisos del rol propio */
  crearRol(rol: RolNuevo): Observable<Rol> {
    return this.http.post<Rol>(api('/api/roles'), rol);
  }

  /** @param id rol propio; @param rol datos nuevos */
  editarRol(id: number, rol: RolNuevo): Observable<Rol> {
    return this.http.put<Rol>(api(`/api/roles/${id}`), rol);
  }

  /** @param id rol propio sin personas */
  eliminarRol(id: number): Observable<void> {
    return this.http.delete<void>(api(`/api/roles/${id}`));
  }

  /** @param id persona; su ficha de permisos */
  permisosDe(id: number): Observable<PermisoUsuario[]> {
    return this.http.get<PermisoUsuario[]>(api(`/api/usuarios/${id}/permisos`));
  }

  /** @param id persona; @param ajustes permisos sumados (true) o quitados (false); reemplaza los anteriores */
  cambiarPermisos(id: number, ajustes: { permiso: string; concedido: boolean }[]): Observable<PermisoUsuario[]> {
    return this.http.put<PermisoUsuario[]>(api(`/api/usuarios/${id}/permisos`), { ajustes });
  }

  /** @param usuario datos del usuario nuevo con su contraseña inicial */
  crear(usuario: UsuarioNuevo): Observable<Usuario> {
    return this.http.post<Usuario>(api('/api/usuarios'), usuario);
  }

  /** @param id usuario; @param cambios nombre, correo, roles y centro de costo */
  actualizar(id: number, cambios: UsuarioCambios): Observable<Usuario> {
    return this.http.put<Usuario>(api(`/api/usuarios/${id}`), cambios);
  }

  /** @param id usuario; @param activo nuevo estado (desactivar no borra su historial) */
  cambiarEstado(id: number, activo: boolean): Observable<Usuario> {
    return this.http.patch<Usuario>(api(`/api/usuarios/${id}/${activo ? 'activar' : 'desactivar'}`), {});
  }

  /** @param id usuario; el administrador borra su vínculo con la app autenticadora (la vuelve a configurar al ingresar) */
  reiniciarMfa(id: number): Observable<void> {
    return this.http.patch<void>(api(`/api/usuarios/${id}/mfa/reiniciar`), {});
  }

  /** @param id usuario; @param passwordNueva clave o PIN que el administrador le entrega (nunca vuelve del servidor) */
  restablecerPassword(id: number, passwordNueva: string): Observable<void> {
    return this.http.put<void>(api(`/api/usuarios/${id}/password`), { passwordNueva });
  }

  /** Datos del usuario de la sesión. */
  perfil(): Observable<Usuario> {
    return this.http.get<Usuario>(api('/api/perfil'));
  }

  /** @param passwordActual la que usa hoy; @param passwordNueva la nueva */
  cambiarPassword(passwordActual: string, passwordNueva: string): Observable<void> {
    return this.http.put<void>(api('/api/perfil/password'), { passwordActual, passwordNueva });
  }
}
