import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { seccionesVisibles } from '../../core/navegacion/menu';
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
const titulo = (t: Element) => t.querySelector('h2')?.textContent?.trim();
const icono = (t: Element) => t.querySelector('.categoria-icono')?.textContent?.trim();

describe('Inicio: categorías del menú (spec 012)', () => {
  it('el dueño ve las seis categorías en el orden del menú, cada una con su icono', () => {
    const html = crear(['ADMIN_SISTEMA']);
    expect(tarjetas(html).map(t => [titulo(t), icono(t)])).toEqual([
      ['Ventas', 'point_of_sale'], ['Compras', 'shopping_cart'], ['Inventario', 'inventory_2'],
      ['Reportes', 'bar_chart'], ['Gestión humana', 'groups'], ['Gestión', 'settings'],
    ]);
  });

  it('cada tarjeta enlaza las mismas pantallas que la barra lateral', () => {
    const roles = ['AUDITOR'];
    const html = crear(roles);
    const esperado = seccionesVisibles(r => !r?.length || r.some(x => roles.includes(x)))
      .filter(s => s.titulo)
      .map(s => s.opciones.map(o => o.ruta));
    const enlaces = tarjetas(html).map(t => [...t.querySelectorAll('a.acceso')].map(a => a.getAttribute('href')));
    expect(enlaces).toEqual(esperado);
  });

  it('el cajero no ve la categoría Gestión', () => {
    const html = crear(['CAJERO']);
    expect(tarjetas(html).map(titulo)).not.toContain('Gestión');
    expect(tarjetas(html).map(titulo)).toContain('Ventas');
  });

  it('una opción de un módulo apagado sale con candado y sin enlace', () => {
    const html = crear(['ADMIN_SISTEMA'], ['CARTERA']);
    const ventas = tarjetas(html).find(t => titulo(t) === 'Ventas')!;
    const cartera = [...ventas.querySelectorAll('.acceso')].find(e => e.textContent?.includes('Cartera'))!;
    expect(cartera.tagName).toBe('SPAN');
    expect(cartera.classList).toContain('bloqueado');
    expect(cartera.querySelector('.candado')).not.toBeNull();
  });
});
