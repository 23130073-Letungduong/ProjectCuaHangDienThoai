package vn.edu.hcmuaf.fit.cuahangdienthoai.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
  private static final int ITERATIONS = 120_000;
  private static final int KEY_BITS = 256;
  private static final HexFormat HEX = HexFormat.of();
  private final SecureRandom random = new SecureRandom();

  public String hash(String password) {
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    return HEX.formatHex(salt) + ":" + HEX.formatHex(hash(password, salt));
  }

  public boolean verify(String password, String stored) {
    if (stored == null || !stored.contains(":")) return false;
    String[] parts = stored.split(":", 2);
    byte[] expected = HEX.parseHex(parts[1]);
    byte[] actual = hash(password, HEX.parseHex(parts[0]));
    return expected.length == actual.length && MessageDigest.isEqual(expected, actual);
  }

  private byte[] hash(String password, byte[] salt) {
    try {
      PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
