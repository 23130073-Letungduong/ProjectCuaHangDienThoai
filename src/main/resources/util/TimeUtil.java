package vn.edu.hcmuaf.fit.cuahangdienthoai.util;

import java.time.Instant;

public final class TimeUtil {
  private TimeUtil() {}

  public static String now() {
    return Instant.now().toString();
  }

  public static String plusSeconds(long seconds) {
    return Instant.now().plusSeconds(seconds).toString();
  }
}
