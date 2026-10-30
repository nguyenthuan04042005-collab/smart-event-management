package com.event.sems.common;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimeUtil {
  public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private TimeUtil() {}

  public static LocalDateTime now() {
    return LocalDateTime.now(ZONE);
  }
}
