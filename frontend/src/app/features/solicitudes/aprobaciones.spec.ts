import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { Aprobaciones } from './aprobaciones';

describe('Solicitudes: detalle con a quién se pidió y su recorrido (spec 033)', () => {
  let backend: HttpTestingController;
  afterEach(() => backend.verify());

  it('el solicitante ve a qué proveedores pidió y el recorrido hasta la orden', () => {
    const roles = ['SOLICITANTE'];
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: {
          tieneRol: (r?: string[]) => !r?.length || r.some(x => roles.includes(x)),
          tieneModulo: () => true, soloLectura: () => false,
          empresa: signal({ slaAprobacionHoras: 24 }), usuario: signal({ id: 10, nombre: 'Sara' }),
        } }],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Aprobaciones);
    fixture.detectChanges();
    backend.expectOne('/api/centros-costo').flush([]);
    backend.expectOne(r => r.url.startsWith('/api/politicas')).flush([]);
    backend.expectOne(r => r.url === '/api/solicitudes').flush({ contenido: [{ id: 3, consecutivo: 'SOL-000003', estado: 'EN_RFQ' }],
      pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 });
    backend.expectOne(r => r.url === '/api/solicitudes/pendientes-aprobacion').flush({ contenido: [], totalElementos: 0 });
    backend.expectOne('/api/solicitudes/3/recorrido').flush([
      { fecha: '2026-11-20T09:00:00', tipo: 'SOLICITUD', titulo: 'Solicitud creada', detalle: 'SOL-000003' },
      { fecha: '2026-11-21T10:00:00', tipo: 'APROBACION', titulo: 'Aprobada por Ana' },
      { fecha: '2026-11-22T11:00:00', tipo: 'ORDEN', titulo: 'Orden OC-000004 a Ferro Andes', detalle: '$ 120000 · EMITIDA' },
    ]);
    backend.expectOne('/api/solicitudes/3').flush({ id: 3, consecutivo: 'SOL-000003', estado: 'EN_RFQ', usuarioSolicitanteId: 10,
      modoProveedor: 'ESPECIFICA', proveedores: [{ id: 7, razonSocial: 'Ferro Andes' }], lineas: [], aprobaciones: [] });
    fixture.detectChanges();

    const html = fixture.nativeElement as HTMLElement;
    expect(html.textContent).toContain('A quién se le pide');
    expect(html.textContent).toContain('Ferro Andes');
    const pasos = [...html.querySelectorAll('.recorrido li strong')].map(x => x.textContent?.trim());
    expect(pasos).toEqual(['Solicitud creada', 'Aprobada por Ana', 'Orden OC-000004 a Ferro Andes']);
  });
});
