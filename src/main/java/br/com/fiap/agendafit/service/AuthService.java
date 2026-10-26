package br.com.fiap.agendafit.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.time.Instant;

import br.com.fiap.agendafit.dto.LoginRequest;
import br.com.fiap.agendafit.dto.LoginResponse;
import br.com.fiap.agendafit.dto.RegisterRequest;
import br.com.fiap.agendafit.dto.UserResponse;
import br.com.fiap.agendafit.model.Role;
import br.com.fiap.agendafit.model.User;
import br.com.fiap.agendafit.repository.UserRepository;
import br.com.fiap.agendafit.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH = new BCryptPasswordEncoder().encode("dummy-password-not-a-user");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public LoginResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email().toLowerCase(Locale.ROOT));
        String hash = user.map(User::getPassword).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordWithinLimit = request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean passwordMatches = passwordEncoder.matches(passwordWithinLimit ? request.password() : "", hash);
        if (!passwordWithinLimit || user.isEmpty() || !passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        User authenticated = user.orElseThrow();
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .subject(authenticated.getEmail())
                .claim("name", authenticated.getName())
                .claim("role", authenticated.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.expirationSeconds()))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, jwtProperties.expirationSeconds());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password must have at most 72 UTF-8 bytes");
        }
        String email = request.email().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        try {
            User user = userRepository.saveAndFlush(User.builder()
                    .name(request.name())
                    .email(email)
                    .password(passwordEncoder.encode(request.password()))
                    .role(Role.USER)
                    .build());
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
    }
}
