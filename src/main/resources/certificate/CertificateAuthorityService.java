package vn.edu.hcmuaf.fit.cuahangdienthoai.certificate;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.DigitalCertificate;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.PublicKeyRecord;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.User;
import vn.edu.hcmuaf.fit.cuahangdienthoai.util.CryptoUtil;
import vn.edu.hcmuaf.fit.cuahangdienthoai.util.TimeUtil;

@Service
public class CertificateAuthorityService {
  public static final String ISSUER_NAME = "ATBM Demo Root CA";
  private static final byte[] DEMO_CA_SEED = "ATBMHTTT-DEMO-ROOT-CA-2026".getBytes(StandardCharsets.UTF_8);

  private final ObjectMapper mapper;
  private final KeyPair caKeyPair;

  public CertificateAuthorityService(ObjectMapper mapper) {
    this.mapper = mapper;
    this.caKeyPair = createDemoRootKeyPair();
  }

  public DigitalCertificate issueCertificate(User user, PublicKeyRecord key) {
    String now = TimeUtil.now();
    return issueCertificate(user, key, now, now);
  }

  public DigitalCertificate issueCertificate(User user, PublicKeyRecord key, String issuedAt, String validFrom) {
    try {
      DigitalCertificate certificate = new DigitalCertificate();
      certificate.id = CryptoUtil.uuid();
      certificate.version = "X.509 v3 demo";
      certificate.serialNumber = CryptoUtil.randomHex(12).toUpperCase();
      certificate.issuerName = ISSUER_NAME;
      certificate.subjectUserId = user.id;
      certificate.subjectName = user.name;
      certificate.subjectEmail = user.email;
      certificate.publicKeyId = key.id;
      certificate.publicKeyFingerprint = key.fingerprint;
      certificate.hashAlgorithm = "SHA-256";
      certificate.signatureAlgorithm = "SHA256withRSA";
      certificate.validFrom = validFrom;
      certificate.validTo = Instant.parse(validFrom).plusSeconds(365L * 24 * 60 * 60).toString();
      certificate.issuedAt = issuedAt;
      certificate.status = "VALID";
      String canonical = canonicalPayload(certificate);
      certificate.certificateHash = CryptoUtil.sha256Hex(canonical);
      certificate.certificateSignature = sign(canonical);
      return certificate;
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể cấp chứng nhận public key.", ex);
    }
  }

  public boolean verifyCertificate(DigitalCertificate certificate) {
    if (certificate == null || certificate.certificateSignature == null || certificate.certificateSignature.isBlank()) return false;
    try {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(caKeyPair.getPublic());
      verifier.update(canonicalPayload(certificate).getBytes(StandardCharsets.UTF_8));
      return verifier.verify(Base64.getDecoder().decode(certificate.certificateSignature));
    } catch (Exception ex) {
      return false;
    }
  }

  public boolean isUsableAt(DigitalCertificate certificate, String orderTime) {
    if (!verifyCertificate(certificate)) return false;
    Instant checkedAt = Instant.parse(orderTime);
    if (checkedAt.isBefore(Instant.parse(certificate.validFrom))) return false;
    if (checkedAt.isAfter(Instant.parse(certificate.validTo))) return false;
    return certificate.revokedAt == null || !checkedAt.isAfter(Instant.parse(certificate.revokedAt));
  }

  public void revoke(DigitalCertificate certificate, String revokedAt, String reason) {
    if (certificate == null || "REVOKED".equals(certificate.status)) return;
    certificate.status = "REVOKED";
    certificate.revokedAt = revokedAt;
    certificate.revokedReason = reason;
  }

  public String rootFingerprint() {
    return CryptoUtil.sha256Hex(rootPublicKeyPem()).substring(0, 32);
  }

  public String rootPublicKeyPem() {
    return pem("PUBLIC KEY", caKeyPair.getPublic().getEncoded());
  }

  private String canonicalPayload(DigitalCertificate certificate) throws Exception {
    Map<String, Object> payload = new TreeMap<>();
    payload.put("version", certificate.version);
    payload.put("serialNumber", certificate.serialNumber);
    payload.put("issuerName", certificate.issuerName);
    payload.put("subjectUserId", certificate.subjectUserId);
    payload.put("subjectName", certificate.subjectName);
    payload.put("subjectEmail", certificate.subjectEmail);
    payload.put("publicKeyId", certificate.publicKeyId);
    payload.put("publicKeyFingerprint", certificate.publicKeyFingerprint);
    payload.put("hashAlgorithm", certificate.hashAlgorithm);
    payload.put("signatureAlgorithm", certificate.signatureAlgorithm);
    payload.put("validFrom", certificate.validFrom);
    payload.put("validTo", certificate.validTo);
    payload.put("issuedAt", certificate.issuedAt);
    return mapper.writeValueAsString(payload);
  }

  private String sign(String canonicalPayload) throws Exception {
    PrivateKey privateKey = caKeyPair.getPrivate();
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(signature.sign());
  }

  private KeyPair createDemoRootKeyPair() {
    try {
      SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
      secureRandom.setSeed(DEMO_CA_SEED);
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048, secureRandom);
      return generator.generateKeyPair();
    } catch (Exception ex) {
      throw new IllegalStateException("Không thể khởi tạo Demo Root CA.", ex);
    }
  }

  private String pem(String label, byte[] encoded) {
    String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded);
    return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----";
  }
}
