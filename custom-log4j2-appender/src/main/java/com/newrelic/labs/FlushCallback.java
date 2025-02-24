package com.newrelic.labs;

import java.util.List;
import java.util.Map;

public interface FlushCallback {
    void onSuccess();

    void onFailure(List<Map<String, Object>> failedLogEvents);
}