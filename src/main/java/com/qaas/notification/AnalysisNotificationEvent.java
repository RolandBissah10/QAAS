package com.qaas.notification;

import java.util.UUID;

public record AnalysisNotificationEvent(
        UUID analysisId,
        UUID projectId,
        UUID triggeredByUserId,
        String url,
        String status
) {}