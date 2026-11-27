import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { Inicio } from './inicio';

/** Sesión mínima con los roles dados y los módulos encendidos salvo los de {@code apagados}. */
const sesionCon = (roles: string[], apagados: string[] = []) => ({
  empresa: signal({ razonSocial: 'Ferretería Demo' }),
  modulos: signal({ plan: 'EMPRESARIAL', estado: 'ACTIVA', modulos: [] }),
  usuario: signal({ nombre: 'Dueño Ferretería', correo: 'a@x.co', roles }),
  soloLectura: () => false,
  tieneRol: (r?: readonly string[]) => !r?.length || r.some(x => roles.includes(x)),
  tieneModulo: (m?: string) => !m || !apagados.includes(m),
});

/** Crea la página de Inicio con la sesión dada; las peticiones al backend quedan sin responder. */
const crear = (roles: string[], apagados: string[] = []) => {
  TestBed.configureTestingModule({
    providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting(),
      { provide: SesionService, useValue: sesionCon(roles, apagados) }],
  });
  const fixture = TestBed.createComponent(Inicio);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
};

const tarjetas = (html: HTMLElement) => [...html.querySelectorAll('.accesos .categoria-acceso')];
const titulo = (t: Element) => t.querySelector('.categoria-titulo')?.textContent?.trim();
const icono = (t: Element) => t.querySelector('.categoria-icono')?.textContent?.trim();
const tarjeta = (html: HTMLElement, t: string) => tarjetas(html).find(x => titulo(x) === t)!;

describe('Inicio: categorías del menú (specs 012 y 013)', () => {
  it('el dueño ve las seis categorías en el orden del menú, cada una con su icono', () => {
    const html = crear(['ADMIN_SISTEMA']);
    expect(tarjetas(html).map(t => [titulo(t), icono(t)])).toEqual([
      ['Ventas', 'point_of_sale'], ['Compras', 'shopping_cart'], ['Inventario', 'inventory_2'],
      ['Reportes', 'bar_chart'], ['Gestión humana', 'groups'], ['Gestión', 'settings'],
    ]);
  });

  it('cada tarjeta entra directo a la primera pantalla de su categoría, sin desplegar nada', () => {
    const html = crear(['ADMIN_SISTEMA']);
    expect(tarjetas(html).map(t => [t.tagName, t.getAttribute('href')])).toEqual([
      ['A', '/app/pedidos-venta'], ['A', '/app/solicitudes'], ['A', '/app/inventario'],
      ['A', '/app/tablero'], ['A', '/app/usuarios'], ['A', '/app/configuracion'],
    ]);
    expect(html.querySelectorAll('.accesos .acceso, .accesos [aria-expanded]').length).toBe(0);
  });

  it('el destino depende del rol: el cajero entra a Ventas por el punto de venta y no ve Gestión', () => {
    const html = crear(['CAJERO']);
    expect(tarjeta(html, 'Ventas').getAttribute('href')).toBe('/app/pos');
    expect(tarjeta(html, 'Ventas').querySelector('.categoria-cuenta')?.textContent?.trim()).toBe('7 pantallas');
    expect(tarjetas(html).map(titulo)).not.toContain('Gestión');
  });

  it('si la primera pantalla tiene el módulo apagado, entra por la siguiente disponible', () => {
    const html = crear(['CAJERO'], ['VENTAS']);
    expect(tarjeta(html, 'Ventas').getAttribute('href')).toBe('/app/caja/mi-turno');
  });

  it('con todos los módulos de la categoría apagados, la tarjeta lleva candado y no navega', () => {
    const html = crear(['AUDITOR'], ['VENTAS', 'CARTERA', 'CAJA']);
    const ventas = tarjeta(html, 'Ventas');
    expect(ventas.tagName).toBe('DIV');
    expect(ventas.getAttribute('href')).toBeNull();
    expect(ventas.getAttribute('aria-disabled')).toBe('true');
    expect(ventas.querySelector('.candado')).not.toBeNull();
  });
});

describe('Inicio: solo categorías y lo que requiere atención (spec 036)', () => {
  it('no tiene periodo, cifras ni gráficas', () => {
    const html = crear(['ADMIN_COMPRAS']);
    expect(html.textContent).not.toContain('Este mes');
    expect(html.textContent).not.toContain('Trimestre');
    expect(html.querySelector('.kpis, .grafico, sc-selector-sede')).toBeNull();
    expect(html.textContent).not.toContain('Mejores proveedores');
    expect(html.textContent).toContain('Requiere tu atención');
  });

  it('la tarjeta de la categoría dice lo que tiene pendiente', () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: sesionCon(['ADMIN_COMPRAS']) }],
    });
    const fixture = TestBed.createComponent(Inicio);
    fixture.detectChanges();
    const c = fixture.componentInstance as unknown as Record<string, any>;
    c['porAprobar'].set([{ id: 1, consecutivo: 'SOL-1' }, { id: 2, consecutivo: 'SOL-2' }]);
    c['bajoMinimo'].set([{ id: 9, stockTotal: 0 }]);
    fixture.detectChanges();
    const html = fixture.nativeElement as HTMLElement;
    const pendiente = (t: string) => tarjeta(html, t).querySelector('.categoria-pendiente')?.textContent?.trim();
    expect(pendiente('Compras')).toBe('2 por aprobar');
    expect(pendiente('Inventario')).toBe('1 bajo el mínimo');
    expect(tarjeta(html, 'Reportes').querySelector('.categoria-pendiente')).toBeNull();
    expect(html.textContent).toContain('1 producto bajo el mínimo');
  });
});
