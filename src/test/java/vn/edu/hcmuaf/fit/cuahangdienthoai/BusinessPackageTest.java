package vn.edu.hcmuaf.fit.cuahangdienthoai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.hcmuaf.fit.cuahangdienthoai.cart.CartService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.catalog.CatalogService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.certificate.CertificateAuthorityService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.crypto.EncryptionTool;
import vn.edu.hcmuaf.fit.cuahangdienthoai.key.KeyTool;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Buyer;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.CartItemRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.DigitalCertificate;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Order;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.PublicKeyRecord;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.User;
import vn.edu.hcmuaf.fit.cuahangdienthoai.order.HashTool;
import vn.edu.hcmuaf.fit.cuahangdienthoai.order.OrderAuditService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.order.OrderService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.order.SignatureTool;
import vn.edu.hcmuaf.fit.cuahangdienthoai.util.CryptoUtil;

class BusinessPackageTest {
  private final CatalogService catalogService = new CatalogService();
  private final CartService cartService = new CartService(catalogService);
  private final KeyTool keyTool = new KeyTool();
  private final CertificateAuthorityService certificateAuthorityService = new CertificateAuthorityService(new ObjectMapper());
  private final EncryptionTool encryptionTool = new EncryptionTool();
  private final OrderService orderService = new OrderService(new HashTool(new ObjectMapper()));
  private final SignatureTool signatureTool = new SignatureTool();
  private final OrderAuditService auditService = new OrderAuditService(orderService, signatureTool, certificateAuthorityService);

  @Test
  void cartServiceCalculatesPromotionsAndFreeShipping() {
    CartService.Totals totals = cartService.calculate(
        List.of(item("ip15", 1), item("ip15", 1)),
        List.of("ATBM10", "FREESHIP")
    );
    assertEquals(1, totals.items.size());
    assertEquals(2, totals.items.get(0).quantity);
    assertEquals(37_980_000L, totals.subtotal);
    assertEquals(3_798_000L, totals.discount);
    assertEquals(0L, totals.shipping);
    assertEquals(34_182_000L, totals.total);
  }

  @Test
  void cartServiceRejectsInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> cartService.calculate(List.of(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> cartService.calculate(List.of(item("ip15", 21)), List.of()));
    assertThrows(IllegalArgumentException.class, () -> cartService.calculate(List.of(item("ip15", 10), item("ip15", 11)), List.of()));
    assertThrows(IllegalArgumentException.class, () -> cartService.calculate(List.of(item("ip15", 1)), List.of("INVALID")));
  }

  @Test
  void keyToolAcceptsRsa2048AndRejectsWeakKey() throws Exception {
    String publicKeyPem = pem("PUBLIC KEY", keyPair(2048).getPublic().getEncoded());
    PublicKeyRecord record = keyTool.createRecord("user-1", "request-1", publicKeyPem);
    assertEquals("ACTIVE", record.status);
    assertEquals(32, record.fingerprint.length());

    String weakPublicKeyPem = pem("PUBLIC KEY", keyPair(1024).getPublic().getEncoded());
    assertThrows(IllegalArgumentException.class, () -> keyTool.validatePublicKey("-----BEGIN PUBLIC KEY-----\nSAI\n-----END PUBLIC KEY-----"));
    assertThrows(IllegalArgumentException.class, () -> keyTool.validatePublicKey(weakPublicKeyPem));
  }

  @Test
  void hashToolCanonicalizesObjectKeys() {
    HashTool hashTool = new HashTool(new ObjectMapper());
    String first = hashTool.hashObject(java.util.Map.of("b", 2, "a", java.util.Map.of("y", 1, "x", 2)));
    String second = hashTool.hashObject(java.util.Map.of("a", java.util.Map.of("x", 2, "y", 1), "b", 2));
    assertEquals(first, second);
  }

  @Test
  void certificateAuthoritySignsPublicKeyCertificateAndRejectsTampering() throws Exception {
    String publicKeyPem = pem("PUBLIC KEY", keyPair(2048).getPublic().getEncoded());
    PublicKeyRecord key = keyTool.createRecord("user-1", "request-1", publicKeyPem);
    key.id = "key-1";
    DigitalCertificate certificate = certificateAuthorityService.issueCertificate(user(), key, "2026-06-15T08:00:00Z", "2026-06-15T08:00:00Z");
    assertTrue(certificateAuthorityService.verifyCertificate(certificate));
    assertTrue(certificateAuthorityService.isUsableAt(certificate, "2026-06-15T09:00:00Z"));

    certificate.subjectEmail = "attacker@example.com";
    assertFalse(certificateAuthorityService.verifyCertificate(certificate));
  }

  @Test
  void encryptionToolUsesAesCbcAndRoundTripsPlaintext() {
    var encrypted = encryptionTool.encrypt("Nội dung cần bảo mật", "123456");
    assertEquals("AES/CBC/PKCS5Padding", encrypted.algorithm());
    assertNotEquals("Nội dung cần bảo mật", encrypted.ciphertext());
    var decrypted = encryptionTool.decrypt(encrypted.ciphertext(), "123456", encrypted.salt(), encrypted.iv());
    assertEquals("Nội dung cần bảo mật", decrypted.plaintext());
  }

  @Test
  void orderServiceHashesOnlyImmutablePayload() throws Exception {
    SignedFixture fixture = signedOrder(false, null, false, false);
    String originalHash = orderService.calculateHash(fixture.order);
    fixture.order.status = "APPROVED";
    assertEquals(originalHash, orderService.calculateHash(fixture.order));
    fixture.order.buyer.address = "Địa chỉ khác";
    assertNotEquals(originalHash, orderService.calculateHash(fixture.order));
  }

  @Test
  void signatureToolVerifiesValidSignatureAndRejectsInvalidSignature() throws Exception {
    SignedFixture fixture = signedOrder(false, null, false, false);
    assertTrue(signatureTool.verify(fixture.order, fixture.key));
    fixture.order.signature = Base64.getEncoder().encodeToString("chu-ky-sai".getBytes());
    assertFalse(signatureTool.verify(fixture.order, fixture.key));
  }

  @Test
  void auditDetectsImmutableContentChanged() throws Exception {
    SignedFixture fixture = signedOrder(true, null, false, false);
    var audit = auditService.audit(fixture.order, List.of(fixture.key));
    assertFalse(audit.hashValid());
    assertTrue(audit.needsManualReview());
    assertEquals(List.of("IMMUTABLE_CONTENT_CHANGED"), audit.reviewReasons());
  }

  @Test
  void auditDetectsOrderCreatedAfterKeyLoss() throws Exception {
    SignedFixture fixture = signedOrder(false, "2026-06-15T10:00:00Z", false, true);
    var audit = auditService.audit(fixture.order, List.of(fixture.key));
    assertTrue(audit.signatureValid());
    assertTrue(audit.createdAfterKeyLoss());
    assertTrue(audit.reviewReasons().contains("ORDER_CREATED_AFTER_KEY_LOSS"));
  }

  @Test
  void auditDetectsOrderModifiedAfterKeyLoss() throws Exception {
    SignedFixture fixture = signedOrder(false, "2026-06-15T10:00:00Z", true, false);
    var audit = auditService.audit(fixture.order, List.of(fixture.key));
    assertTrue(audit.signatureValid());
    assertTrue(audit.modifiedAfterKeyLoss());
    assertTrue(audit.reviewReasons().contains("ORDER_MODIFIED_AFTER_KEY_LOSS"));
  }

  @Test
  void auditDetectsCertificateRevokedBeforeOrder() throws Exception {
    SignedFixture fixture = signedOrder(false, null, false, false);
    DigitalCertificate certificate = certificateAuthorityService.issueCertificate(user(), fixture.key, "2026-06-15T08:00:00Z", "2026-06-15T08:00:00Z");
    certificateAuthorityService.revoke(certificate, "2026-06-15T09:45:00Z", "PRIVATE_KEY_LOST");
    fixture.order.certificateId = certificate.id;
    fixture.order.certificateSnapshot = certificate;
    fixture.order.createdAt = "2026-06-15T10:30:00Z";
    fixture.order.lastModifiedAt = fixture.order.createdAt;
    fixture.order.hash = orderService.calculateHash(fixture.order);
    var audit = auditService.audit(fixture.order, List.of(fixture.key), List.of(certificate));
    assertFalse(audit.certificateValid());
    assertTrue(audit.certificateRevokedBeforeOrder());
    assertTrue(audit.reviewReasons().contains("CERTIFICATE_REVOKED_BEFORE_ORDER"));
  }

  private SignedFixture signedOrder(boolean tamper, String lostAt, boolean modifiedAfterLost, boolean createdAfterLost) throws Exception {
    String createdAt = createdAfterLost ? "2026-06-15T10:30:00Z" : "2026-06-15T09:30:00Z";
    String lastModifiedAt = modifiedAfterLost ? "2026-06-15T10:45:00Z" : createdAt;
    KeyPair pair = keyPair(2048);
    String publicKeyPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
    PublicKeyRecord key = keyTool.createRecord("user-1", "request-1", publicKeyPem);
    key.id = "key-1";
    key.lostAt = lostAt;
    key.status = lostAt == null ? "ACTIVE" : "LOST";

    Order order = new Order();
    order.id = "order-1";
    order.userId = "user-1";
    order.publicKeyId = key.id;
    order.publicKeySnapshot = publicKeyPem;
    order.publicKeyFingerprint = key.fingerprint;
    order.createdAt = createdAt;
    order.lastModifiedAt = lastModifiedAt;
    order.version = modifiedAfterLost ? 2 : 1;
    order.buyer = buyer();
    cartService.applyTotals(order, cartService.calculate(List.of(item("ip15", 1)), List.of("ATBM10")));
    order.hash = orderService.calculateHash(order);
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(pair.getPrivate());
    signature.update(CryptoUtil.hexToBytes(order.hash));
    order.signature = Base64.getEncoder().encodeToString(signature.sign());
    order.signaturePublicKeyPem = publicKeyPem;
    order.status = "SIGNED";
    if (tamper) order.buyer.address = "Địa chỉ bị sửa";
    return new SignedFixture(order, key);
  }

  private Buyer buyer() {
    Buyer buyer = new Buyer();
    buyer.name = "user";
    buyer.email = "user@example.com";
    buyer.phone = "0909123456";
    buyer.address = "12 Nguyễn Văn Bảo";
    return buyer;
  }

  private User user() {
    User user = new User();
    user.id = "user-1";
    user.name = "user";
    user.username = "user";
    user.email = "user@example.com";
    user.role = "CUSTOMER";
    user.phone = "0909123456";
    user.address = "12 Nguyễn Văn Bảo";
    user.createdAt = "2026-06-15T08:00:00Z";
    return user;
  }

  private CartItemRequest item(String productId, int quantity) {
    CartItemRequest item = new CartItemRequest();
    item.productId = productId;
    item.quantity = quantity;
    return item;
  }

  private KeyPair keyPair(int size) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(size);
    return generator.generateKeyPair();
  }

  private String pem(String label, byte[] encoded) {
    String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
    return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----";
  }

  private record SignedFixture(Order order, PublicKeyRecord key) {}
}
