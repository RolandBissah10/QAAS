package com.qaas.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;

public class JwtUtil {
    private static final String SECRET = "change-me-in-prod";
    public static String generateToken(String subject) {
        return Jwts.builder().setSubject(subject).setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 3600_000)).signWith(SignatureAlgorithm.HS256, SECRET.getBytes()).compact();
    }
}
