package com.proyecto.supply_core.caja.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.dto.CajaResponse;
import com.proyecto.supply_core.caja.dto.UsuarioCajaResponse;
import com.proyecto.supply_core.caja.entity.CajaUsuario;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.caja.repository.CajaUsuarioRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Cajas por persona (spec 025). El administrador elige quién puede abrir turno en cada caja. Regla de transición: una
 * persona sin ninguna caja asignada puede usar todas, así nadie se queda sin vender el día del cambio.
 */
@Service
public class CajaAsignacionService {

    private final CajaUsuarioRepository asignaciones;
    private final CajaRepository cajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CajaService cajaService;
    private final AuditoriaService auditoriaService;

    /**
     * @param asignaciones      cajas asignadas por persona
     * @param cajaRepository    cajas (para validar que sean de la empresa)
     * @param usuarioRepository personas (para validar que sean de la empresa)
     * @param cajaService       lista de cajas con su ocupación
     * @param auditoriaService  registro de cambios
     */
    public CajaAsignacionService(CajaUsuarioRepository asignaciones, CajaRepository cajaRepository,
            UsuarioRepository usuarioRepository, CajaService cajaService, AuditoriaService auditoriaService) {
        this.asignaciones = asignaciones;
        this.cajaRepository = cajaRepository;
        this.usuarioRepository = usuarioRepository;
        this.cajaService = cajaService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * @param cajaId caja
     * @return quiénes la pueden usar, por nombre
     * @throws NoEncontradoException si la caja no es de la empresa
     */
    @Transactional(readOnly = true)
    public List<UsuarioCajaResponse> usuariosDe(Long cajaId) {
        exigirCaja(cajaId);
        List<Long> ids = asignaciones.findByCajaId(cajaId).stream().map(CajaUsuario::getUsuarioId).toList();
        return usuarioRepository.findAllById(ids).stream()
                .map(u -> new UsuarioCajaResponse(u.getId(), u.getNombreCompleto()))
                .sorted(java.util.Comparator.comparing(UsuarioCajaResponse::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Reemplaza quiénes pueden usar la caja.
     *
     * @param cajaId     caja
     * @param usuarioIds personas de la empresa
     * @return la lista guardada
     * @throws NoEncontradoException si la caja no es de la empresa
     * @throws NegocioException      {@code USUARIO_INVALIDO} si alguna persona no es de la empresa
     */
    @Transactional
    public List<UsuarioCajaResponse> asignar(Long cajaId, List<Long> usuarioIds) {
        exigirCaja(cajaId);
        Set<Long> pedidos = usuarioIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
        Set<Long> existentes = usuarioRepository.findAllById(pedidos).stream().map(Usuario::getId).collect(Collectors.toSet());
        if (!existentes.containsAll(pedidos)) {
            throw new NegocioException("USUARIO_INVALIDO", "Alguna de las personas elegidas no es de la empresa");
        }
        List<CajaUsuario> actuales = asignaciones.findByCajaId(cajaId);
        Set<Long> yaEstan = new HashSet<>();
        for (CajaUsuario a : actuales) {
            if (pedidos.contains(a.getUsuarioId())) {
                yaEstan.add(a.getUsuarioId());
            } else {
                asignaciones.delete(a);
            }
        }
        for (Long u : pedidos) {
            if (!yaEstan.contains(u)) {
                CajaUsuario nueva = new CajaUsuario();
                nueva.setCajaId(cajaId);
                nueva.setUsuarioId(u);
                asignaciones.save(nueva);
            }
        }
        asignaciones.flush();
        auditoriaService.exito(AccionAuditoria.CAJA_EDITAR, EntidadAuditada.CAJA, cajaId, "usuarios=" + pedidos);
        return usuariosDe(cajaId);
    }

    /**
     * @param usuarioId persona
     * @return ids de sus cajas asignadas; vacío = puede usar todas
     */
    @Transactional(readOnly = true)
    public Set<Long> cajasDe(Long usuarioId) {
        return asignaciones.findByUsuarioId(usuarioId).stream().map(CajaUsuario::getCajaId).collect(Collectors.toSet());
    }

    /**
     * Cajas activas que la sesión puede usar para abrir turno ("Mi turno").
     *
     * @return sus cajas asignadas o, si no tiene ninguna, todas las activas
     */
    @Transactional(readOnly = true)
    public List<CajaResponse> misCajas() {
        Set<Long> propias = cajasDe(SesionActual.usuario().id());
        return cajaService.listar(true).stream().filter(c -> propias.isEmpty() || propias.contains(c.id())).toList();
    }

    /**
     * @param cajaId caja
     * @throws NoEncontradoException si no es de la empresa
     */
    private void exigirCaja(Long cajaId) {
        if (!cajaRepository.existsById(cajaId)) {
            throw new NoEncontradoException("Caja no encontrada");
        }
    }
}
