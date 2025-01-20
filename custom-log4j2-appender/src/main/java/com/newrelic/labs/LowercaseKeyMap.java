
package com.newrelic.labs;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("serial")
public class LowercaseKeyMap extends HashMap<String, Object> {
  @Override
  public Object put(String key, Object value) {
    return super.put(key.toLowerCase(), value);
  }

  @Override
  public void putAll(Map<? extends String, ? extends Object> m) {
    for (Map.Entry<? extends String, ? extends Object> entry : m.entrySet()) {
      this.put(entry.getKey().toLowerCase(), entry.getValue());
    }
  }
}
