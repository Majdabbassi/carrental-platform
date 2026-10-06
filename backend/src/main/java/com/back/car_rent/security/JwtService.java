package com.back.car_rent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final JwtProperties props;

    public JwtService(JwtProperties props, org.springframework.core.env.Environment environment) {
        this.props = props;
        String secret = props.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set to at least 32 characters (openssl rand -hex 32)");
        }
        // The repository ships a development key so that `docker compose up` works; a production
        // deployment must bring its own, otherwise anyone could forge tokens.
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod")) && secret.startsWith("dev-only")) {
            throw new IllegalStateException("JWT_SECRET is the published development key: set a real secret");
        }
    }

    private Key getSigningKey() {
        // the secret is used as plain text (at least 32 characters), exactly as written in JWT_SECRET
        return Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, Role role, Map<String, Object> extraClaims) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + props.getExpiration());
        JwtBuilder builder = Jwts.builder()
                .setSubject(username)
                .claim("role", role.name())
                .addClaims(extraClaims == null ? Map.of() : extraClaims)
                .claim("type", "access")
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256);
        return builder.compact();
    }

    // Generate a long-lived refresh JWT with type=refresh
    public String generateRefreshToken(String username, Role role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + props.getRefreshExpiration());
        JwtBuilder builder = Jwts.builder()
                .setSubject(username)
                .claim("role", role.name())
                .claim("type", "refresh")
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256);
        return builder.compact();
    }

    public boolean isRefreshTokenValid(String token, String username) {
        try {
            Claims c = extractAllClaims(token);
            String type = c.get("type", String.class);
            String sub = c.getSubject();
            Date exp = c.getExpiration();
            return "refresh".equals(type) && sub != null && sub.equals(username) && exp.after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    // Short-lived password reset token with hash of current password to invalidate on change
    public String generatePasswordResetToken(String username, String currentPasswordHash) {
        Date now = new Date();
        long twoHoursMillis = 2 * 60 * 60 * 1000L;
        Date exp = new Date(now.getTime() + twoHoursMillis);
        JwtBuilder builder = Jwts.builder()
                .setSubject(username)
                .claim("type", "reset")
                .claim("ph", currentPasswordHash)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256);
        return builder.compact();
    }

    public boolean isPasswordResetTokenValid(String token, String username, String currentPasswordHash) {
        try {
            Claims c = extractAllClaims(token);
            String type = c.get("type", String.class);
            String sub = c.getSubject();
            String ph = c.get("ph", String.class);
            Date exp = c.getExpiration();
            return "reset".equals(type) && sub != null && sub.equals(username)
                    && ph != null && ph.equals(currentPasswordHash)
                    && exp.after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    /** Only a token issued as an access token opens the API (refresh and reset tokens must not). */
    public boolean isAccessTokenValid(String token, String username) {
        try {
            return "access".equals(extractAllClaims(token).get("type", String.class)) && isTokenValid(token, username);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTokenValid(String token, String username) {
        String u = extractUsername(token);
        return (u != null && u.equals(username) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        Date exp = extractClaim(token, Claims::getExpiration);
        return exp.before(new Date());
    }

    public <T> T extractClaim(String token, java.util.function.Function<Claims, T> resolver) {
        final Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}