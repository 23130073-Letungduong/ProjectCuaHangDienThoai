package vn.edu.hcmuaf.fit.cuahangdienthoai.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class CryptoUtil {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final HexFormat HEX = HexFormat.of();

  private CryptoUtil() {}

  public static String uuid() {
    return java.util.UUID.randomUUID().toString();
  }

  public static String randomHex(int byteLength) {
    byte[] bytes = new byte[byteLength];
    SECURE_RANDOM.nextBytes(bytes);
    return HEX.formatHex(bytes);
  }

  public static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static byte[] hexToBytes(String hex) {
    return HEX.parseHex(hex);
  }
}
