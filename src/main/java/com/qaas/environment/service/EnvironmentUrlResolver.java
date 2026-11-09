package com.qaas.environment.service;

import com.qaas.exception.BadRequestException;
import com.qaas.security.PublicHttpUrlValidator;

import java.net.URI;

public final class EnvironmentUrlResolver {
    private EnvironmentUrlResolver() {
    }

    public static String resolve(String baseUrl, String originalUrl) {
        URI base = parse(baseUrl);
        URI original = parse(originalUrl);
        String scheme = base.getScheme();
        if (scheme == null || original.getHost() == null
                || original.getScheme() == null
                || !(original.getScheme().equalsIgnoreCase("http")
                || original.getScheme().equalsIgnoreCase("https"))) {
            throw new BadRequestException("Test URL must be an absolute HTTP(S) URL");
        }

        String path = original.getRawPath() == null ? "" : original.getRawPath();
        String query = original.getRawQuery() == null ? "" : "?" + original.getRawQuery();
        String url = scheme + "://" + base.getRawAuthority() + path + query;
        try {
            URI resolved = URI.create(url);
            PublicHttpUrlValidator.validate(resolved.toASCIIString());
            return resolved.toASCIIString();
        } catch (RuntimeException exception) {
            throw new BadRequestException("Unable to resolve test URL for the selected environment");
        }
    }

    private static URI parse(String value) {
        try {
            return URI.create(value);
        } catch (RuntimeException exception) {
            throw new BadRequestException("Invalid environment or test URL");
        }
    }
}
