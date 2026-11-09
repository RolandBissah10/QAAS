package com.qaas.environment.service;

import com.qaas.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentUrlResolverTest {

    @Test
    void replacesTheOriginAndKeepsTheTestPathAndQuery() {
        assertThat(EnvironmentUrlResolver.resolve(
                "https://8.8.8.8", "http://1.1.1.1/account/profile?tab=security"))
                .isEqualTo("https://8.8.8.8/account/profile?tab=security");
    }

    @Test
    void preservesEncodedPathAndQueryComponents() {
        assertThat(EnvironmentUrlResolver.resolve(
                "https://8.8.8.8", "http://1.1.1.1/a%2Fb?next=%2Fchild%26mode%3D1"))
                .isEqualTo("https://8.8.8.8/a%2Fb?next=%2Fchild%26mode%3D1");
    }

    @Test
    void rejectsPrivateOrNonHttpEnvironmentDestinations() {
        assertThatThrownBy(() -> EnvironmentUrlResolver.resolve(
                "http://127.0.0.1", "https://8.8.8.8/"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> EnvironmentUrlResolver.resolve(
                "file:///tmp", "https://8.8.8.8/"))
                .isInstanceOf(BadRequestException.class);
    }
}
