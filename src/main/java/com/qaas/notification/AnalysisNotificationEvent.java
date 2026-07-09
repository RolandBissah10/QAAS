package com.qaas.notification;

import java.util.UUID;

public record AnalysisNotificationEvent(
        UUID analysisId,
        UUID projectId,
        String url,
        String status
) {}