package ru.workbit.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.JWTService;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JWTAuthFilter extends OncePerRequestFilter {
    private final JWTService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain chain) throws ServletException, IOException {
        authenticate(request);
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            return;
        }

        String token = header.substring(7);

        if (!jwtService.isTokenValid(token)) {
            return;
        }

        String email = jwtService.extractEmail(token);
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (userDetails.isEnabled()) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (userDetails instanceof CustomUserDetails details) {
                    MDC.put("uid", details.getId().toString());
                }
            }
        } catch (UsernameNotFoundException ignored) {
        }
    }
}
