package vn.edu.hcmuaf.fit.cuahangdienthoai.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class EncryptionTool {
  public static final String ALGORITHM = "AES/CBC/PKCS5Padding";
  public static final String KDF = "PBKDF2WithHmacSHA256";
  public static final int ITERATIONS = 120_000;
  public static final int KEY_BITS = 256;
  public static final int SALT_BYTES = 16;
  public static final int IV_BYTES = 16;
  private final SecureRandom random = new SecureRandom();

  public EncryptionResult encrypt(String plaintext, String passphrase) {
    if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("Nội dung mã hóa không được để trống.");
    if (passphrase == null || passphrase.length() < 6) throw new IllegalArgumentException("Mật khẩu mã hóa phải có ít nhất 6 ký tự.");
    byte[] salt = randomBytes(SALT_BYTES);
    byte[] iv = randomBytes(IV_BYTES);
    byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, plaintext.getBytes(StandardCharsets.UTF_8), passphrase, salt, iv);
    return new EncryptionResult(ALGORITHM, KDF, ITERATIONS, Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(iv), Base64.getEncoder().encodeToString(ciphertext), null);
  }

  public EncryptionResult decrypt(String ciphertextBase64, String passphrase, String saltBase64, String ivBase64) {
    if (ciphertextBase64 == null || ciphertextBase64.isBlank()) throw new IllegalArgumentException("Ciphertext không được để trống.");
    if (passphrase == null || passphrase.length() < 6) throw new IllegalArgumentException("Mật khẩu giải mã phải có ít nhất 6 ký tự.");
    try {
      byte[] salt = Base64.getDecoder().decode(saltBase64);
      byte[] iv = Base64.getDecoder().decode(ivBase64);
      byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
      byte[] plaintext = crypt(Cipher.DECRYPT_MODE, ciphertext, passphrase, salt, iv);
      return new EncryptionResult(ALGORITHM, KDF, ITERATIONS, saltBase64, ivBase64, ciphertextBase64, new String(plaintext, StandardCharsets.UTF_8));
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Không thể giải mã. Kiểm tra lại mật khẩu, salt, IV và ciphertext.");
    }
  }

  private byte[] crypt(int mode, byte[] input, String passphrase, byte[] salt, byte[] iv) {
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(mode, deriveKey(passphrase, salt), new IvParameterSpec(iv));
      return cipher.doFinal(input);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Thao tác mã hóa không thành công.");
    }
  }

  private SecretKeySpec deriveKey(String passphrase, byte[] salt) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS);
    byte[] key = SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
    return new SecretKeySpec(key, "AES");
  }

  private byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    random.nextBytes(bytes);
    return bytes;
  }

  public record EncryptionResult(
      String algorithm,
      String keyDerivation,
      int iterations,
      String salt,
      String iv,
      String ciphertext,
      String plaintext
  ) {}
}
