package com.proyecto.supply_core.grupo.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Línea de un traslado entre empresas del grupo (tabla {@code traslado_grupo_linea}): el producto de la empresa origen
 * y su pareja en la destino, lo enviado, lo recibido y el costo unitario con que salió.
 */
@Entity
@Table(name = "traslado_grupo_linea")
@Getter
@Setter
@NoArgsConstructor
public class TrasladoGrupoLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "traslado_id", nullable = false)
    private TrasladoGrupo traslado;

    @Column(name = "producto_origen_id", nullable = false)
    private Long productoOrigenId;

    @Column(name = "producto_destino_id", nullable = false)
    private Long productoDestinoId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidad;

    @Column(name = "cantidad_recibida", precision = 18, scale = 4)
    private BigDecimal cantidadRecibida;

    @Column(name = "costo_unitario", nullable = false, precision = 18, scale = 4)
    private BigDecimal costoUnitario;

    /**
     * Lo que salió y no llegó: cantidad menos lo recibido. Mientras no se reciba, no hay faltante.
     *
     * @return faltante o {@code null} si el traslado aún no se recibe
     */
    public BigDecimal faltante() {
        return cantidadRecibida == null ? null : cantidad.subtract(cantidadRecibida);
    }
}
