package vn.edu.hcmuaf.fit.cuahangdienthoai.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import vn.edu.hcmuaf.fit.cuahangdienthoai.util.CryptoUtil;

@Service
public class HashTool {
  private final ObjectMapper mapper;

  public HashTool(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String canonicalString(Object value) {
    try {
      return mapper.writeValueAsString(canonicalize(value));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public String hashObject(Object value) {
    return CryptoUtil.sha256Hex(canonicalString(value));
  }

  @SuppressWarnings("unchecked")
  private Object canonicalize(Object value) {
    Object converted = mapper.convertValue(value, Object.class);
    if (converted instanceof Map<?, ?> map) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      map.forEach((key, val) -> sorted.put(String.valueOf(key), canonicalize(val)));
      return sorted;
    }
    if (converted instanceof List<?> list) {
      List<Object> result = new ArrayList<>();
      for (Object item : list) result.add(canonicalize(item));
      return result;
    }
    return converted;
  }
}
