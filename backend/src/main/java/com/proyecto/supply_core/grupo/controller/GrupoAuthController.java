package com.proyecto.supply_core.grupo.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.grupo.dto.GrupoDtos.LoginGrupoRequest;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.MfaGrupoRequest;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.SesionGrupoResponse;
import com.proyecto.supply_core.grupo.service.GrupoAuthService;
import com.proyecto.supply_core.plataforma.dto.DesafioMfaResponse;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;

/**
 * Login del super administrador de un grupo, en dos pasos y con MFA obligatorio. Rutas públicas con límite de intentos
 * por IP (spec 035).
 */
@RestController
@RequestMapping("/api/grupo/auth")
public class GrupoAuthController {

    private final GrupoAuthService authService;

    /**
     * @param authService lógica del login de grupo
     */
    public GrupoAuthController(GrupoAuthService authService) {
        this.authService = authService;
    }

    /**
     * Paso 1: contraseña. Entrega el desafío de MFA (y el secreto para vincular la app la primera vez).
     *
     * @param request correo y contraseña
     * @return desafío de MFA
     */
    @PostMapping("/login")
    @SecurityRequirements
    public DesafioMfaResponse login(@Valid @RequestBody LoginGrupoRequest request) {
        return authService.login(request);
    }

    /**
     * Paso 2: código de la app de autenticación. Entrega la sesión de grupo.
     *
     * @param request desafío y código de 6 dígitos
     * @return sesión de grupo
     */
    @PostMapping("/mfa")
    @SecurityRequirements
    public SesionGrupoResponse mfa(@Valid @RequestBody MfaGrupoRequest request) {
        return authService.verificarMfa(request);
    }
}
