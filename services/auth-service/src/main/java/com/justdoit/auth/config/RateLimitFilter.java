package com.justdoit.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting por IP nos endpoints públicos de autenticação (login, register,
 * refresh e check-email). Sem isso, esses endpoints permitem enumeração de
 * e-mails em massa, credential stuffing e abuso dos lookups DNS do check-email.
 *
 * Token bucket em memória: cada IP tem um balde com {@code capacity} fichas,
 * reabastecido a {@code refillPerMinute} fichas/minuto. Estado local ao processo
 * — suficiente para a instância única atual; com múltiplas réplicas, migrar para
 * um balde compartilhado (ex.: Redis) ou limitar no proxy.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/auth/login", "/auth/register", "/auth/refresh", "/auth/check-email");

    // Evita crescimento sem limite do mapa de baldes (um por IP visto).
    private static final int MAX_TRACKED_IPS = 10_000;

    private final boolean enabled;
    private final int capacity;
    private final double refillPerMinute;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${auth.rate-limit.enabled:true}") boolean enabled,
                           @Value("${auth.rate-limit.capacity:20}") int capacity,
                           @Value("${auth.rate-limit.refill-per-minute:20}") double refillPerMinute) {
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !PUBLIC_AUTH_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(clientIp(request), ip -> {
            evictIfFull();
            return new Bucket(capacity);
        });
        if (bucket.tryConsume(capacity, refillPerMinute)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Muitas requisições. Tente novamente em instantes.\"}");
    }

    /**
     * Atrás do nginx do VPS o remoteAddr é sempre o proxy; o IP real do cliente
     * vem no X-Forwarded-For (primeiro valor). O header é confiável aqui porque o
     * proxy o sobrescreve; em acesso direto (dev local) cai no remoteAddr.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void evictIfFull() {
        if (buckets.size() < MAX_TRACKED_IPS) {
            return;
        }
        // Descarta baldes já cheios de volta (IPs inativos há tempo suficiente).
        buckets.values().removeIf(b -> b.isIdle(capacity, refillPerMinute));
        if (buckets.size() >= MAX_TRACKED_IPS) {
            buckets.clear(); // proteção extrema: nunca deixa a memória crescer sem limite
        }
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;

        private Bucket(int capacity) {
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        private synchronized boolean tryConsume(int capacity, double refillPerMinute) {
            refill(capacity, refillPerMinute);
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }

        private synchronized boolean isIdle(int capacity, double refillPerMinute) {
            refill(capacity, refillPerMinute);
            return tokens >= capacity;
        }

        private void refill(int capacity, double refillPerMinute) {
            long now = System.nanoTime();
            double elapsedMinutes = (now - lastRefillNanos) / 60_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedMinutes * refillPerMinute);
            lastRefillNanos = now;
        }
    }
}
