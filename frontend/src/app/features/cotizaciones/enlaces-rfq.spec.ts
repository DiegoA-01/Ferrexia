import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { EnlaceCotizacion } from '../../core/api/modelos';
import { EnlacesRfq } from './enlaces-rfq';

const pendiente: EnlaceCotizacion = { proveedorId: 40, proveedor: 'Eléctricos del Norte', correo: 'ventas@norte.com',
  telefono: '300 123 4567', url: 'http://localhost/cotizar/1.abc', estado: 'PENDIENTE', venceEn: '2026-12-01T18:00:00' };
const usado: EnlaceCotizacion = { proveedorId: 41, proveedor: 'Cables SAS', estado: 'USADO', venceEn: '2026-12-01T18:00:00',
  usadoEn: '2026-11-27T10:00:00' };

/** Spec 031: enlaces para cotizar sin cuenta en la RFQ. */
describe('EnlacesRfq', () => {
  let fixture: ComponentFixture<EnlacesRfq>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EnlacesRfq);
    fixture.componentRef.setInput('rfqId', 3);
    fixture.componentRef.setInput('consecutivo', 'RFQ-000003');
    fixture.detectChanges();
    backend.expectOne('/api/rfqs/3/enlaces').flush([pendiente, usado]);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('muestra el estado de cada enlace y solo deja copiar el pendiente', () => {
    expect(texto()).toContain('enviado a ventas@norte.com');
    expect(texto()).toContain('Ya cotizó');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('a[href^="https://wa.me/"]').length).toBe(1);
  });

  it('arma el WhatsApp al celular del proveedor con el enlace', () => {
    const url = c()['whatsapp'](pendiente) as string;
    expect(url.startsWith('https://wa.me/573001234567?text=')).toBe(true);
    expect(decodeURIComponent(url)).toContain('http://localhost/cotizar/1.abc');
  });

  it('regenerar cambia el enlace en la lista', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    c()['regenerar'](pendiente);
    backend.expectOne('/api/rfqs/3/enlaces/40/regenerar').flush({ ...pendiente, url: 'http://localhost/cotizar/1.nuevo' });
    expect(c()['enlaces']()[0].url).toBe('http://localhost/cotizar/1.nuevo');
  });
});
