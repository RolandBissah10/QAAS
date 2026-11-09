package com.qaas.crawler.service.impl;

import com.qaas.security.PublicHttpUrlValidator;

final class CrawlTargetValidator {

    private CrawlTargetValidator() {
    }

    static void validate(String value) {
        PublicHttpUrlValidator.validate(value);
    }
}
