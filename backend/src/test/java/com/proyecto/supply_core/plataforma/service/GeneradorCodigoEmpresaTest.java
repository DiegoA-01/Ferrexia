package com.proyecto.supply_core.plataforma.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Código de ingreso generado desde la razón social (spec 020). */
class GeneradorCodigoEmpresaTest {

    @Test
    void quitaTildesMayusculasYLaSiglaSocietaria() {
        assertEquals("ferreteria-el-tornillo", GeneradorCodigoEmpresa.base("Ferretería El Tornillo S.A.S."));
        assertEquals("electricos-del-sur", GeneradorCodigoEmpresa.base("ELÉCTRICOS DEL SUR LTDA."));
        assertEquals("repuestos-martinez", GeneradorCodigoEmpresa.base("Repuestos Martínez S.A."));
        assertEquals("pinturas-gomez", GeneradorCodigoEmpresa.base("Pinturas Gómez y Cía."));
        assertEquals("ferrecentro", GeneradorCodigoEmpresa.base("Ferrecentro SAS"));
        assertEquals("tornillos-y-tuercas", GeneradorCodigoEmpresa.base("Tornillos & Tuercas E.U."));
    }

    @Test
    void conservaLosNumerosYUneConUnSoloGuion() {
        assertEquals("ferreteria-2000", GeneradorCodigoEmpresa.base("  Ferretería   2000 -- "));
        assertEquals("nino-y-pena", GeneradorCodigoEmpresa.base("Niño y Peña"));
    }

    @Test
    void cortaEntrePalabrasSinPasarDeTreintaCaracteres() {
        String codigo = GeneradorCodigoEmpresa.base("Distribuidora Nacional de Materiales para Construcción S.A.S.");
        assertTrue(codigo.length() <= 30, codigo);
        assertEquals("distribuidora-nacional", codigo);   // sin el "de" suelto que dejaría el corte
    }

    @Test
    void unaPalabraMuyLargaSeCortaYUnNombreSinLetrasUsaElRespaldo() {
        assertEquals(30, GeneradorCodigoEmpresa.base("a".repeat(45)).length());
        assertEquals("empresa", GeneradorCodigoEmpresa.base("S.A.S."));
        assertEquals("empresa", GeneradorCodigoEmpresa.base("¡¡!!"));
        assertEquals("empresa", GeneradorCodigoEmpresa.base(null));
        assertEquals("empresa", GeneradorCodigoEmpresa.base("Xy"));
    }

    @Test
    void elNumeroSeAgregaSinPasarDeTreinta() {
        assertEquals("ferrecentro-2", GeneradorCodigoEmpresa.conNumero("ferrecentro", 2));
        String largo = GeneradorCodigoEmpresa.base("Distribuidora Nacional de Materiales");
        String conNumero = GeneradorCodigoEmpresa.conNumero(largo, 12);
        assertTrue(conNumero.length() <= 30, conNumero);
        assertTrue(conNumero.endsWith("-12"), conNumero);
        assertTrue(!conNumero.contains("--"), conNumero);
    }
}
