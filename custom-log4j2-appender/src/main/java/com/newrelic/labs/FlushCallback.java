package com.newrelic.labs;

import java.util.List;
import java.util.Map;

public interface FlushCallback {
  void onFailure(List<Map<String, Object>> failedLogEvents);

  void onSuccess();
}
