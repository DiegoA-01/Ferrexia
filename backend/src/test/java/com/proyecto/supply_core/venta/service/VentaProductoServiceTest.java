package com.proyecto.supply_core.venta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.inventario.entity.Producto;

/** Limpieza del texto que el cajero escribe o escanea antes de buscar. */
class VentaProductoServiceTest {

    @Test
    void elTextoSeRecortaYNoPasaDeCienCaracteres() {
        assertEquals("", VentaProductoService.limpiar(null));
        assertEquals("", VentaProductoService.limpiar("   "));
        assertEquals("CAB-12", VentaProductoService.limpiar("  CAB-12 "));
        assertEquals(100, VentaProductoService.limpiar("x".repeat(150)).length());
    }

    @Test
    void losComodinesSeBuscanComoTextoYNoComoPatron() {
        // "%" no debe traer todo el catálogo y "_" no debe valer por cualquier letra
        assertEquals("!%", VentaProductoService.escaparLike("%"));
        assertEquals("tor!_12", VentaProductoService.escaparLike("tor_12"));
        assertEquals("a!!b", VentaProductoService.escaparLike("a!b"));
        assertEquals("cable 12", VentaProductoService.escaparLike("cable 12"));
    }

    /**
     * @param id          producto
     * @param categoriaId categoría
     * @return producto de esa categoría
     */
    private static Producto producto(long id, long categoriaId) {
        CategoriaProducto c = new CategoriaProducto();
        c.setId(categoriaId);
        Producto p = new Producto();
        p.setId(id);
        p.setCategoria(c);
        return p;
    }

    /** Con texto y categoría (spec 027), de lo encontrado quedan solo los de la categoría, en el mismo orden. */
    @Test
    void conTextoYCategoriaQuedanSoloLosDeLaCategoria() {
        List<Producto> encontrados = List.of(producto(1, 7), producto(2, 8), producto(3, 7));

        List<Producto> filtrados = VentaProductoService.deCategoria(encontrados, 7L);

        assertEquals(List.of(1L, 3L), filtrados.stream().map(Producto::getId).toList());
        assertEquals(0, VentaProductoService.deCategoria(encontrados, 99L).size());
    }
}
