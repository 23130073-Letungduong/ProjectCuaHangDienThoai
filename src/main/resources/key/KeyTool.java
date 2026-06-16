package vn.edu.hcmuaf.fit.cuahangdienthoai.key;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Service;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.PublicKeyRecord;
import vn.edu.hcmuaf.fit.cuahangdienthoai.util.CryptoUtil;
import vn.edu.hcmuaf.fit.cuahangdienthoai.util.TimeUtil;

@Service
public class KeyTool {
  public void validatePublicKey(String publicKeyPem) {
    if (publicKeyPem == null || !publicKeyPem.contains("BEGIN PUBLIC KEY")) {
      throw new IllegalArgumentException("Public key PEM không hợp lệ.");
    }
    try {
      String base64 = publicKeyPem
          .replace("-----BEGIN PUBLIC KEY-----", "")
          .replace("-----END PUBLIC KEY-----", "")
          .replaceAll("\\s", "");
      byte[] encoded = Base64.getDecoder().decode(base64);
      var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
      if (!(key instanceof RSAPublicKey rsaKey) || rsaKey.getModulus().bitLength() < 2048) {
        throw new IllegalArgumentException("Public key RSA phải có độ dài tối thiểu 2048-bit.");
      }
    } catch (IllegalArgumentException ex) {
      if (!String.valueOf(ex.getMessage()).contains("2048-bit")) {
        throw new IllegalArgumentException("Public key PEM không hợp lệ.");
      }
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Public key PEM không hợp lệ.");
    }
  }

  public String fingerprint(String publicKeyPem) {
    validatePublicKey(publicKeyPem);
    return CryptoUtil.sha256Hex(publicKeyPem).substring(0, 32);
  }

  public PublicKeyRecord createRecord(String userId, String requestId, String publicKeyPem) {
    PublicKeyRecord key = new PublicKeyRecord();
    key.id = CryptoUtil.uuid();
    key.userId = userId;
    key.requestId = requestId;
    key.publicKeyPem = publicKeyPem;
    key.fingerprint = fingerprint(publicKeyPem);
    key.issueMode = "CLIENT_GENERATED";
    key.createdAt = TimeUtil.now();
    key.status = "ACTIVE";
    return key;
  }

  public IssuedKeyMaterial issueKeyPair(String userId, String requestId, String issuedBy) {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      PublicKeyRecord key = createRecord(userId, requestId, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
      key.issueMode = "ADMIN_ONE_TIME";
      key.issuedBy = issuedBy;
      return new IssuedKeyMaterial(key, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể tạo cặp khóa RSA.", ex);
    }
  }

  private String pem(String label, byte[] encoded) {
    String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
    return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----";
  }

  public record IssuedKeyMaterial(PublicKeyRecord key, String privateKeyPem) {}
}
