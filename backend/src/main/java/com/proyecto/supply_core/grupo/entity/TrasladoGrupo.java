package com.proyecto.supply_core.grupo.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.proyecto.supply_core.grupo.enums.EstadoTrasladoGrupo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Traslado de mercancía entre dos empresas del mismo grupo (tabla {@code traslado_grupo}, spec 035). Toca dos empresas,
 * por eso NO tiene {@code @TenantId}: todo acceso pasa por {@code TrasladoGrupoService}, que filtra a mano por
 * {@code empresa_origen_id} o {@code empresa_destino_id} igual a la empresa del contexto.
 */
@Entity
@Table(name = "traslado_grupo")
@Getter
@Setter
@NoArgsConstructor
public class TrasladoGrupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grupo_id", nullable = false)
    private Long grupoId;

    @Column(nullable = false, length = 30)
    private String consecutivo;

    @Column(name = "empresa_origen_id", nullable = false)
    private Long empresaOrigenId;

    @Column(name = "empresa_destino_id", nullable = false)
    private Long empresaDestinoId;

    @Column(name = "bodega_origen_id", nullable = false)
    private Long bodegaOrigenId;

    @Column(name = "bodega_destino_id")
    private Long bodegaDestinoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTrasladoGrupo estado = EstadoTrasladoGrupo.EN_TRANSITO;

    @Column(length = 300)
    private String nota;

    @Column(name = "enviado_por_usuario_id", nullable = false)
    private Long enviadoPorUsuarioId;

    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn;

    @Column(name = "recibido_por_usuario_id")
    private Long recibidoPorUsuarioId;

    @Column(name = "recibido_en")
    private LocalDateTime recibidoEn;

    @Column(name = "nota_recepcion", length = 300)
    private String notaRecepcion;

    @Column(name = "anulado_por_usuario_id")
    private Long anuladoPorUsuarioId;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @OneToMany(mappedBy = "traslado", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<TrasladoGrupoLinea> lineas = new ArrayList<>();

    /** Fija la fecha de envío al insertar. */
    @PrePersist
    void alCrear() {
        if (enviadoEn == null) {
            enviadoEn = LocalDateTime.now();
        }
    }

    /**
     * Agrega una línea y la enlaza con este traslado.
     *
     * @param linea línea nueva
     */
    public void agregarLinea(TrasladoGrupoLinea linea) {
        linea.setTraslado(this);
        lineas.add(linea);
    }
}
