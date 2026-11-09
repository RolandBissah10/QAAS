package com.qaas.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationUrlScopeTest {

    @Test
    void acceptsPagesOnTheSameOriginIncludingRelativePaths() {
        assertThat(ApplicationUrlScope.isSameOrigin(
                "https://app.example.test/start", "https://app.example.test/account?tab=profile"))
                .isTrue();
        assertThat(ApplicationUrlScope.isSameOrigin(
                "https://app.example.test/start", "/account?tab=profile"))
                .isTrue();
        assertThat(ApplicationUrlScope.isSameOrigin(
                "https://app.example.test/start", "https://app.example.test:443/account"))
                .isTrue();
    }

    @Test
    void rejectsOtherHostsSubdomainsSchemesAndPorts() {
        assertThat(ApplicationUrlScope.isSameOrigin(
                "https://app.example.test", "https://other.example.test/")).isFalse();
        assertThat(ApplicationUrlScope.isSameOrigin(
                "https://app.example.test", "https://api.app.example.test/")).isFalse();
        assertThat(ApplicationUrlScope.isSameOrigin(
                "https://app.example.test", "http://app.example.test/")).isFalse();
        assertThat(ApplicationUrlScope.isSameOrigin(
                "https://app.example.test", "https://app.example.test:8443/")).isFalse();
        assertThat(ApplicationUrlScope.isSameOrigin(
                "https://app.example.test", "https://user@app.example.test/")).isFalse();
    }
}
