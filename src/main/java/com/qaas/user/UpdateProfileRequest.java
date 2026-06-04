package com.qaas.user;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@Size(max = 120) String displayName) {
}
