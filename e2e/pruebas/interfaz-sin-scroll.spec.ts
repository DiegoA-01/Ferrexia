import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { Page, expect, test } from '@playwright/test';

/** Cuentas que deja `scripts/sembrar_demo.py` (archivo local, nunca se sube a git). */
interface Credenciales {
  empresa: string;
  cajero?: string;
  cuentas: Record<string, string>;
}

const ARCHIVO = process.env.E2E_CREDENCIALES ?? resolve(__dirname, '..', '..', 'credenciales-docker.local.json');
const ANCHOS = [360, 768, 1280];

/** Pantallas que recorre cada cuenta (solo navega: no crea ni cambia datos). */
const RECORRIDOS: { correo: string; rutas: string[] }[] = [
  {
    correo: 'auditor@ferreteriademo.local',
    rutas: ['/app/inicio', '/app/solicitudes', '/app/cotizaciones', '/app/adjudicaciones', '/app/pedidos', '/app/ordenes',
      '/app/recepcion', '/app/pagos', '/app/proveedores', '/app/catalogos', '/app/inventario', '/app/inventario/movimientos',
      '/app/despachos', '/app/inventario/reabastecimiento', '/app/tablero', '/app/reportes', '/app/ventas/reportes',
      '/app/inventario/rotacion', '/app/precios', '/app/ventas', '/app/clientes', '/app/cartera', '/app/caja',
      '/app/usuarios', '/app/delegaciones', '/app/configuracion', '/app/auditoria', '/app/perfil'],
  },
  {
    correo: 'cajero@ferreteriademo.local',
    rutas: ['/app/inicio', '/app/caja/mi-turno', '/app/pedidos-venta', '/app/ventas', '/app/clientes', '/app/cartera'],
  },
];

/** @returns las cuentas de la demo sembrada */
function credenciales(): Credenciales {
  return JSON.parse(readFileSync(ARCHIVO, 'utf-8')) as Credenciales;
}

/** @param page página @param c cuentas @param correo usuario */
async function entrar(page: Page, c: Credenciales, correo: string): Promise<void> {
  await page.goto('/ingreso');
  await page.locator('#empresa').fill(c.empresa);
  await page.locator('#correo').fill(correo);
  await page.locator('#password').fill(c.cuentas[correo]);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(/\/app\//);
}

/**
 * Navega dentro de la aplicación, sin recargar la página. Recargar en cada pantalla renueva la sesión cada vez y el
 * límite de peticiones por IP de /api/auth/refresh termina mandando al ingreso (la prueba mediría la pantalla de ingreso).
 *
 * @param page página
 * @param ruta pantalla
 */
async function ir(page: Page, ruta: string): Promise<void> {
  await page.evaluate(r => {
    history.pushState(null, '', r);
    dispatchEvent(new PopStateEvent('popstate'));
  }, ruta);
  await page.waitForURL(url => url.pathname === ruta || url.pathname.startsWith('/ingreso') || url.pathname.startsWith('/app/'));
  await page.waitForTimeout(700);
}

/** @returns cuántos píxeles se sale la página de lado (0 = nada) */
async function desborde(page: Page): Promise<number> {
  return page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
}

/**
 * Spec 017: ninguna pantalla tiene scroll horizontal a 360, 768 y 1280 px. Solo navega; no crea datos.
 */
for (const recorrido of RECORRIDOS) {
  test(`sin scroll horizontal: pantallas de ${recorrido.correo}`, async ({ page }) => {
    test.setTimeout(300_000);
    const c = credenciales();
    test.skip(!c.cuentas[recorrido.correo], `la siembra no trae ${recorrido.correo}`);
    await entrar(page, c, recorrido.correo);
    const fallas: string[] = [];
    for (const ancho of ANCHOS) {
      await page.setViewportSize({ width: ancho, height: 900 });
      for (const ruta of recorrido.rutas) {
        await ir(page, ruta);
        // si la sesión se cayó, la prueba no puede dar verde midiendo la pantalla de ingreso
        expect(new URL(page.url()).pathname, `la sesión se perdió al ir a ${ruta}`).not.toMatch(/^\/ingreso/);
        await expect(page.locator('main')).toBeVisible();
        const sobra = await desborde(page);
        if (sobra > 1) {
          fallas.push(`${ruta} a ${ancho} px se sale ${sobra} px`);
        }
      }
    }
    expect(fallas, fallas.join('\n')).toEqual([]);
  });
}

test('las pestañas de la categoría siguen a la vista al bajar', async ({ page }) => {
  const c = credenciales();
  const correo = 'auditor@ferreteriademo.local';
  test.skip(!c.cuentas[correo], `la siembra no trae ${correo}`);
  await entrar(page, c, correo);
  await page.setViewportSize({ width: 1280, height: 600 });
  await ir(page, '/app/inventario/movimientos');
  const pestanas = page.locator('main nav.pestanas');
  await expect(pestanas).toBeVisible();
  // se garantiza una página larga para poder bajar
  await page.evaluate(() => document.querySelector('main')!.insertAdjacentHTML('beforeend', '<div style="height:2000px"></div>'));
  await page.mouse.wheel(0, 1200);
  await expect(pestanas).toHaveClass(/compacta/);
  const caja = await pestanas.boundingBox();
  expect(caja!.y).toBeGreaterThanOrEqual(0);
  expect(caja!.y).toBeLessThan(40);
});
