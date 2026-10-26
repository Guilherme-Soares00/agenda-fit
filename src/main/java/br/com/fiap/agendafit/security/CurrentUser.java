package br.com.fiap.agendafit.security;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public Optional<String> email() {
        return authentication().map(Authentication::getName);
    }

    public boolean isAdmin() {
        return authentication().map(authentication -> authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))).orElse(false);
    }

    public boolean isAuthenticated() {
        return authentication().isPresent();
    }

    private Optional<Authentication> authentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken));
    }
}
