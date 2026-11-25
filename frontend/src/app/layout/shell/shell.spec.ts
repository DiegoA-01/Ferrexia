import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { Shell } from './shell';

const proveedor = (id: number, razonSocial: string) => ({ id, razonSocial, nit: '800123456-1', estado: 'PENDIENTE' });
const pagina = (contenido: unknown[]) => ({ contenido, pagina: 0, tamano: 100, totalElementos: contenido.length, totalPaginas: 1 });

/** Sesión mínima: solo lo que lee el marco. */
const sesionCon = (roles: string[]) => ({
  empresa: signal({ razonSocial: 'Ferretería Demo' }),
  modulos: signal({ plan: 'EMPRESARIAL', estado: 'ACTIVA', modulos: ['COMPRAS', 'INVENTARIO', 'PROVEEDORES'] }),
  usuario: signal({ nombre: 'Dueño Ferretería', correo: 'a@x.co', roles }),
  soloLectura: () => false,
  tieneRol: (r?: readonly string[]) => !r?.length || r.some(x => roles.includes(x)),
  tieneModulo: () => true,
  salir: () => undefined,
  salirDeTodo: () => undefined,
});

describe('Marco de la empresa: campana y menú', () => {
  let fixture: ComponentFixture<Shell>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const html = () => fixture.nativeElement as HTMLElement;

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: sesionCon(roles) }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    backend.match(r => r.url === '/api/notificaciones/no-leidas').forEach(r => r.flush({ cantidad: 0 }));
    backend.match(r => r.url === '/api/solicitudes/pendientes-aprobacion').forEach(r => r.flush(pagina([])));
    backend.match(r => r.url === '/api/productos/alertas-stock').forEach(r => r.flush([]));
  };

  afterEach(() => backend.verify());

  describe('quien administra compras', () => {
    // Es el primer marco que se crea en el archivo y paga la compilación de su plantilla; con toda la suite corriendo
    // en paralelo puede pasar de los 10 s por defecto, así que este paso tiene su propio límite.
    beforeEach(() => {
      crear(['ADMIN_SISTEMA', 'ADMIN_COMPRAS']);
      backend.expectOne(r => r.url === '/api/proveedores' && r.params.get('estado') === 'PENDIENTE')
        .flush(pagina([proveedor(7, 'Electro Sur'), proveedor(8, 'Cables del Norte')]));
      fixture.detectChanges();
    }, 30_000);

    it('la campana muestra cuántas decisiones esperan', () => {
      expect(c()['totalAvisos']()).toBe(2);
      expect(html().querySelector('.campana .insignia')?.textContent?.trim()).toBe('2');
    });

    it('aceptar habilita al proveedor y lo saca de la lista', () => {
      c()['aceptarProveedor'](7);
      const p = backend.expectOne('/api/proveedores/7/habilitar');
      expect(p.request.method).toBe('PATCH');
      p.flush(proveedor(7, 'Electro Sur'));

      expect(c()['pendientes']().map((x: any) => x.id)).toEqual([8]);
    });

    it('rechazar manda el motivo y lo saca de la lista', () => {
      c()['pedirMotivo'](8);
      c()['motivo'].set('No tiene certificado vigente');
      c()['rechazarProveedor'](8);
      const p = backend.expectOne('/api/proveedores/8/rechazar');
      expect(p.request.body).toEqual({ motivo: 'No tiene certificado vigente' });
      p.flush(proveedor(8, 'Cables del Norte'));

      expect(c()['pendientes']().map((x: any) => x.id)).toEqual([7]);
      expect(c()['rechazando']()).toBeNull();
    });

    it('si el servidor falla, el proveedor sigue en la lista y se avisa', () => {
      c()['aceptarProveedor'](7);
      backend.expectOne('/api/proveedores/7/habilitar').flush({ mensaje: 'x' }, { status: 409, statusText: 'Conflict' });

      expect(c()['pendientes']().length).toBe(2);
      expect(c()['errorDecision']()).toContain('No se pudo aceptar');
    });
  });

  it('quien no administra compras no consulta proveedores pendientes', () => {
    crear(['ALMACENISTA']);
    backend.expectNone(r => r.url === '/api/proveedores');
    expect(c()['totalAvisos']()).toBe(0);
  });

  it('el menú lateral ya no ofrece "Ver como dueño" a nadie de la empresa', () => {
    crear(['ADMIN_SISTEMA']);
    expect(html().textContent).not.toContain('Ver como dueño');
  });

  describe('menú por categorías (specs 011 y 013)', () => {
    const titulos = () => [...html().querySelectorAll('nav .categoria .texto')].map(p => p.textContent?.trim());
    const textos = () => [...html().querySelectorAll('nav .opcion .texto')].map(s => s.textContent?.trim());
    const categoria = (t: string) => [...html().querySelectorAll<HTMLButtonElement>('nav .categoria')]
      .find(b => b.querySelector('.texto')?.textContent?.trim() === t)!;
    const ir = async (url: string) => {
      await TestBed.inject(Router).navigateByUrl(url);
      fixture.detectChanges();
    };

    beforeEach(() => localStorage.removeItem('sc.menu.abiertas'));

    it('el dueño ve las seis categorías con título e Inicio arriba sin título', () => {
      crear(['ADMIN_SISTEMA']);
      backend.match(r => r.url === '/api/proveedores').forEach(r => r.flush(pagina([])));
      fixture.detectChanges();
      expect(titulos()).toEqual(['Ventas', 'Compras', 'Inventario', 'Reportes', 'Gestión humana', 'Gestión']);
      expect(textos()).toEqual(['Inicio']);
    });

    it('el cajero no ve Gestión y en Gestión humana solo tiene Delegaciones', () => {
      crear(['CAJERO']);
      expect(titulos()).not.toContain('Gestión');
      categoria('Gestión humana').click();
      fixture.detectChanges();
      expect(textos()).toContain('Delegaciones');
      expect(textos()).not.toContain('Usuarios y roles');
    });

    it('abre la categoría de la pantalla actual y deja las demás recogidas', async () => {
      crear(['CAJERO']);
      await ir('/app/clientes');
      expect(categoria('Ventas').getAttribute('aria-expanded')).toBe('true');
      expect(categoria('Compras').getAttribute('aria-expanded')).toBe('false');
      expect(textos()).toContain('Clientes');
      expect(textos()).not.toContain('Solicitudes');
    });

    it('abrir otra categoría recoge la que estaba abierta (spec 017), otro clic la recoge, y se recuerda', async () => {
      crear(['CAJERO']);
      await ir('/app/clientes');
      categoria('Compras').click();
      fixture.detectChanges();
      expect(textos()).toContain('Solicitudes');
      expect(textos()).not.toContain('Clientes');
      expect(html().querySelectorAll('nav .categoria[aria-expanded="true"]').length).toBe(1);
      expect(JSON.parse(localStorage.getItem('sc.menu.abiertas')!)).toEqual(['Compras']);

      categoria('Compras').click();
      fixture.detectChanges();
      expect(textos()).not.toContain('Solicitudes');
      expect(categoria('Compras').getAttribute('aria-expanded')).toBe('false');
    });

    it('al navegar queda abierta solo la categoría de la pantalla actual (spec 017)', async () => {
      crear(['CAJERO']);
      categoria('Inventario').click();
      fixture.detectChanges();
      await ir('/app/clientes');
      expect(html().querySelectorAll('nav .categoria[aria-expanded="true"]').length).toBe(1);
      expect(categoria('Ventas').getAttribute('aria-expanded')).toBe('true');
      expect(categoria('Inventario').getAttribute('aria-expanded')).toBe('false');
    });

    it('si quedaron guardadas varias (de la regla vieja), abre solo una', () => {
      localStorage.setItem('sc.menu.abiertas', JSON.stringify(['Inventario', 'Compras']));
      crear(['CAJERO']);
      expect(html().querySelectorAll('nav .categoria[aria-expanded="true"]').length).toBe(1);
    });

    it('recupera las categorías guardadas al volver a entrar', () => {
      localStorage.setItem('sc.menu.abiertas', JSON.stringify(['Inventario']));
      crear(['CAJERO']);
      expect(textos()).toContain('Productos y stock');
    });

    it('una categoría recogida muestra la suma de los contadores de sus pantallas', () => {
      crear(['CAJERO']);
      c()['contadores'].set({ bajoMinimo: 3 });
      fixture.detectChanges();
      expect(categoria('Inventario').querySelector('.contador')?.textContent?.trim()).toBe('3');
      categoria('Inventario').click();
      fixture.detectChanges();
      expect(categoria('Inventario').querySelector('.contador')).toBeNull();
    });
  });

  describe('pestañas de la categoría actual (spec 014)', () => {
    const pestanas = () => [...html().querySelectorAll('main nav.pestanas .pestana')];
    const ir = async (url: string) => {
      await TestBed.inject(Router).navigateByUrl(url);
      fixture.detectChanges();
    };

    const textos = () => pestanas().map(p => p.querySelector('span:not(.material-symbols-outlined)')?.textContent?.trim());

    it('el auditor en el Tablero ve sus cinco pantallas de Reportes, con el Tablero activo', async () => {
      crear(['AUDITOR']);
      await ir('/app/tablero');
      expect(textos()).toEqual([
        'Tablero del dueño', 'Reportes de compras', 'Reportes de ventas', 'Rotación de productos', 'Precios y margen',
      ]);
      const activa = html().querySelector('main nav.pestanas .pestana.activa');
      expect(activa?.getAttribute('href')).toBe('/app/tablero');
      expect(activa?.getAttribute('aria-current')).toBe('page');
    });

    it('el dueño ve en Reportes sus cinco pantallas (sin Reportes de compras)', async () => {
      crear(['ADMIN_SISTEMA']);
      backend.match(r => r.url === '/api/proveedores').forEach(r => r.flush(pagina([])));
      await ir('/app/tablero');
      expect(textos()).toEqual([
        'Tablero del dueño', 'Reportes de ventas', 'Rotación de productos', 'Informe de compras', 'Precios y margen',
      ]);
    });

    it('en una subpágina muestra las pestañas de su categoría', async () => {
      crear(['ADMIN_COMPRAS']);
      backend.match(r => r.url === '/api/proveedores').forEach(r => r.flush(pagina([])));
      await ir('/app/ordenes/15');
      expect(html().querySelector('main nav.pestanas')?.getAttribute('aria-label')).toBe('Pantallas de Compras');
    });

    it('el cajero en el punto de venta ve las seis pantallas de Ventas de su rol', async () => {
      crear(['CAJERO']);
      await ir('/app/pos');
      expect(pestanas().map(p => p.getAttribute('href'))).toEqual([
        '/app/pos', '/app/caja/mi-turno', '/app/pedidos-venta', '/app/ventas', '/app/clientes', '/app/cartera',
      ]);
    });

    it('una pantalla de un módulo apagado sale con candado y sin enlace', async () => {
      crear(['AUDITOR']);
      (c()['sesion'] as { tieneModulo: (m?: string) => boolean }).tieneModulo = m => m !== 'VENTAS';
      await ir('/app/tablero');
      const precios = pestanas().find(p => p.textContent?.includes('Precios y margen'))!;
      expect(precios.tagName).toBe('SPAN');
      expect(precios.getAttribute('href')).toBeNull();
      expect(precios.querySelector('.candado')).not.toBeNull();
    });

    it('al bajar, las pestañas pasan a la versión compacta y al subir vuelven (spec 017)', async () => {
      crear(['CAJERO']);
      await ir('/app/pos');
      const ancla = html().querySelector<HTMLElement>('.ancla-pestanas')!;
      const fila = () => html().querySelector('main nav.pestanas')!;
      ancla.getBoundingClientRect = () => ({ top: -40 } as DOMRect);
      window.dispatchEvent(new Event('scroll'));
      fixture.detectChanges();
      expect(fila().classList).toContain('compacta');
      ancla.getBoundingClientRect = () => ({ top: 120 } as DOMRect);
      window.dispatchEvent(new Event('scroll'));
      fixture.detectChanges();
      expect(fila().classList).not.toContain('compacta');
    });

    it('las pestañas no oscilan cerca del borde: entran a compactas pasados 32 px y salen solo al verse el ancla', async () => {
      crear(['CAJERO']);
      await ir('/app/pos');
      const ancla = html().querySelector<HTMLElement>('.ancla-pestanas')!;
      const fila = () => html().querySelector('main nav.pestanas')!;
      /** @param top posición del ancla tras desplazar */
      const desplazar = (top: number) => {
        ancla.getBoundingClientRect = () => ({ top } as DOMRect);
        window.dispatchEvent(new Event('scroll'));
        fixture.detectChanges();
      };
      desplazar(-20);
      expect(fila().classList).not.toContain('compacta');
      desplazar(-33);
      expect(fila().classList).toContain('compacta');
      // Al encogerse la fila, la página se acorta y el ancla baja unos 16 px: sigue compacta
      desplazar(-17);
      expect(fila().classList).toContain('compacta');
      desplazar(-1);
      expect(fila().classList).toContain('compacta');
      desplazar(0);
      expect(fila().classList).not.toContain('compacta');
    });

    it('en Inicio y en el perfil no hay fila de pestañas', async () => {
      crear(['CAJERO']);
      await ir('/app/inicio');
      expect(html().querySelector('main nav.pestanas')).toBeNull();
      await ir('/app/perfil');
      expect(html().querySelector('main nav.pestanas')).toBeNull();
    });
  });

  it('cada aviso lleva a su pantalla', () => {
    crear(['ADMIN_COMPRAS']);
    backend.expectOne(r => r.url === '/api/proveedores').flush(pagina([]));
    expect(c()['rutaDe']({ evento: 'COTIZACION_RECIBIDA' })).toBe('/app/cotizaciones');
    expect(c()['rutaDe']({ evento: 'PROVEEDOR_PENDIENTE' })).toBe('/app/proveedores');
    expect(c()['rutaDe']({ evento: 'PAGO_REGISTRADO' })).toBeUndefined();
  });
});
