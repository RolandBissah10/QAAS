package com.qaas.crawler.service.impl;

import com.qaas.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlTargetValidatorTest {

    @Test
    void acceptsPublicHttpAndHttpsTargets() {
        assertThatCode(() -> CrawlTargetValidator.validate("https://8.8.8.8/"))
                .doesNotThrowAnyException();
        assertThatCode(() -> CrawlTargetValidator.validate("http://1.1.1.1/"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedSchemesAndUrlCredentials() {
        assertRejected("file:///etc/passwd");
        assertRejected("https://user:password@example.com/");
    }

    @Test
    void rejectsLocalAndPrivateIpv4Destinations() {
        assertRejected("http://127.0.0.1/");
        assertRejected("http://10.0.0.1/");
        assertRejected("http://169.254.169.254/");
        assertRejected("http://192.168.1.1/");
    }

    @Test
    void rejectsLocalIpv6AndLocalHostNames() {
        assertRejected("http://[::1]/");
        assertRejected("http://localhost/");
        assertRejected("http://service.local/");
    }

    @Test
    void rejectsHostnamesThatDoNotResolve() {
        assertRejected("https://qaas-test.invalid/");
    }

    private void assertRejected(String url) {
        assertThatThrownBy(() -> CrawlTargetValidator.validate(url))
                .isInstanceOf(BadRequestException.class);
    }
}
