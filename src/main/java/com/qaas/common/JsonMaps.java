package com.qaas.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonMaps {
    private JsonMaps() {
    }

    public static Map<String, Object> copyOrEmpty(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
}
