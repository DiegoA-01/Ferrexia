package com.proyecto.supply_core.usuario.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.usuario.enums.TipoCredencial;

/** Clave fuerte para quien administra, PIN o clave para el personal y la de siempre para proveedores (spec 022, D10). */
class ReglasCredencialTest {

    @Test
    void quienAdministraNecesitaClaveFuerte() {
        assertThatThrownBy(() -> ReglasCredencial.validar(Set.of("ADMIN_SISTEMA"), null, 1L, "Clave1234"))
                .isInstanceOf(ParametroInvalidoException.class).hasMessageContaining("12 a 72");
        assertThatThrownBy(() -> ReglasCredencial.validar(Set.of("ADMINISTRADOR"), null, 1L, "123456"))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThat(ReglasCredencial.validar(Set.of("ADMIN_SISTEMA"), null, 1L, "Ferreteria#2026")).isEqualTo(TipoCredencial.CLAVE);
    }

    @Test
    void elPersonalEntraConPinDeSeisDigitosOConClave() {
        assertThat(ReglasCredencial.validar(Set.of("CAJERO"), null, 1L, "482913")).isEqualTo(TipoCredencial.PIN);
        assertThat(ReglasCredencial.validar(Set.of("VENDEDOR", "CAJERO"), null, 1L, "Cajero2026")).isEqualTo(TipoCredencial.CLAVE);
        assertThatThrownBy(() -> ReglasCredencial.validar(Set.of("CAJERO"), null, 1L, "12345"))
                .isInstanceOf(ParametroInvalidoException.class).hasMessageContaining("PIN de 6 dígitos");
        assertThatThrownBy(() -> ReglasCredencial.validar(Set.of("CAJERO"), null, 1L, "soloLetras"))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void elProveedorSigueConLaClaveDeSiempreYNoUsaPin() {
        assertThat(ReglasCredencial.validar(Set.of("PROVEEDOR"), null, 1L, "Proveedor1")).isEqualTo(TipoCredencial.CLAVE);
        assertThatThrownBy(() -> ReglasCredencial.validar(Set.of("PROVEEDOR"), null, 1L, "123456"))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void reconoceAQuienAdministraPorElPermisoYNoPorElNombreDelRol() {
        assertThat(ReglasCredencial.administra(Set.of("ADMIN_SISTEMA"), null, 1L)).isTrue();
        assertThat(ReglasCredencial.administra(Set.of("ADMINISTRADOR"), null, 1L)).isTrue();
        assertThat(ReglasCredencial.administra(Set.of("SUPERVISOR_VENTAS", "AUDITOR"), null, 1L)).isFalse();
        assertThat(ReglasCredencial.esFuerte("Ferreteria#2026")).isTrue();
        assertThat(ReglasCredencial.esFuerte("ferreteria#2026")).isFalse();
    }
}
