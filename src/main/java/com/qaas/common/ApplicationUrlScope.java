package com.qaas.common;

import java.net.URI;

public final class ApplicationUrlScope {
    private ApplicationUrlScope() {
    }

    public static boolean isSameOrigin(String applicationUrl, String candidateUrl) {
        try {
            URI application = URI.create(applicationUrl);
            URI candidate = application.resolve(URI.create(candidateUrl));
            String applicationScheme = application.getScheme();
            String candidateScheme = candidate.getScheme();
            if (applicationScheme == null || candidateScheme == null
                    || application.getHost() == null || candidate.getHost() == null
                    || application.getRawUserInfo() != null || candidate.getRawUserInfo() != null) {
                return false;
            }

            return applicationScheme.equalsIgnoreCase(candidateScheme)
                    && application.getHost().equalsIgnoreCase(candidate.getHost())
                    && effectivePort(application) == effectivePort(candidate);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
