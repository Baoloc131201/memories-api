package com.memories.security;

import com.memories.repository.RevokedTokenRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String CLAIMS_ATTR = "jwtClaims";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RevokedTokenRepository revokedTokens;
    private final AuthCookies authCookies;

    public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService,
                         RevokedTokenRepository revokedTokens, AuthCookies authCookies) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.revokedTokens = revokedTokens;
        this.authCookies = authCookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        authCookies.readAccess(request)
                .flatMap(jwtService::parse)
                .filter(claims -> !revokedTokens.existsById(claims.getId()))
                .ifPresent(claims -> authenticate(claims, request));
        chain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        try {
            UserDetails user = userDetailsService.loadUserByUsername(claims.getSubject());
            var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
            request.setAttribute(CLAIMS_ATTR, claims);
        } catch (UsernameNotFoundException ignored) {
            // user bị xoá -> coi như chưa đăng nhập
        }
    }
}
