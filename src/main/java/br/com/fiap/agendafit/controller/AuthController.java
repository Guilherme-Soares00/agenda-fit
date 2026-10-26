package br.com.fiap.agendafit.controller;

import br.com.fiap.agendafit.dto.ErrorResponse;
import br.com.fiap.agendafit.dto.LoginRequest;
import br.com.fiap.agendafit.dto.LoginResponse;
import br.com.fiap.agendafit.dto.RegisterRequest;
import br.com.fiap.agendafit.dto.UserResponse;
import br.com.fiap.agendafit.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "User registration and JWT authentication")
@ApiResponse(responseCode = "400", description = "Invalid request",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "401", description = "Authentication required or invalid credentials",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Access denied",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "Resource not found",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "409", description = "Email already registered",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Authenticate and issue an access token")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Register a user")
    @ApiResponse(responseCode = "201", description = "User registered")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody @Valid RegisterRequest request) {
        return authService.register(request);
    }
}
