package vn.edu.hcmuaf.fit.cuahangdienthoai.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import vn.edu.hcmuaf.fit.cuahangdienthoai.cart.CartService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.catalog.CatalogService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.certificate.CertificateAuthorityService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.key.KeyTool;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Alert;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Buyer;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.CartItemRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Database;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.DigitalCertificate;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.KeyLossReport;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.KeyRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Order;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.OrderHistory;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.PublicKeyRecord;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.User;
import vn.edu.hcmuaf.fit.cuahangdienthoai.order.HashTool;
import vn.edu.hcmuaf.fit.cuahangdienthoai.order.OrderService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.security.PasswordService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.util.CryptoUtil;

public class SeedApplication {
  private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  private final PasswordService passwordService = new PasswordService();
  private final KeyTool keyTool = new KeyTool();
  private final CertificateAuthorityService certificateAuthorityService = new CertificateAuthorityService(new ObjectMapper());
  private final CatalogService catalogService = new CatalogService();
  private final CartService cartService = new CartService(catalogService);
  private final OrderService orderService = new OrderService(new HashTool(new ObjectMapper()));

  public static void main(String[] args) throws Exception {
    new SeedApplication().run();
  }

  private void run() throws Exception {
    Instant now = Instant.now();
    Database db = new Database();
    User customer = user("user", "user", "user@example.com", "CUSTOMER", "0909123456", "12 Nguyễn Văn Bảo, Gò Vấp, TP.HCM", now.minusSeconds(5L * 24 * 60 * 60).toString());
    User admin = user("admin", "admin", "admin@example.com", "ADMIN", "0900000001", "Phòng quản trị.", now.minusSeconds(6L * 24 * 60 * 60).toString());
    db.users.add(customer);
    db.users.add(admin);

    KeyMaterial active = keyMaterial(customer, now.minusSeconds(4L * 24 * 60 * 60).toString(), "ACTIVE", null);
    String lostAt = now.minusSeconds(2L * 60 * 60).toString();
    KeyMaterial lost = keyMaterial(customer, now.minusSeconds(3L * 24 * 60 * 60).toString(), "LOST", lostAt);
    active.key.requestId = CryptoUtil.uuid();
    lost.key.requestId = CryptoUtil.uuid();
    active.certificate.publicKeyId = active.key.id;
    active.key.certificateId = active.certificate.id;
    lost.certificate.publicKeyId = lost.key.id;
    lost.key.certificateId = lost.certificate.id;
    db.keys.add(active.key);
    db.keys.add(lost.key);
    db.certificates.add(active.certificate);
    db.certificates.add(lost.certificate);
    db.keyRequests.add(completedRequest(active.key.requestId, customer.id, admin.id, active.key.id, active.certificate.id, now.minusSeconds(5L * 24 * 60 * 60).toString(), active.key.createdAt));
    db.keyRequests.add(completedRequest(lost.key.requestId, customer.id, admin.id, lost.key.id, lost.certificate.id, now.minusSeconds(4L * 24 * 60 * 60).toString(), lost.key.createdAt));
    KeyLossReport report = new KeyLossReport();
    report.id = CryptoUtil.uuid();
    report.keyId = lost.key.id;
    report.userId = customer.id;
    report.reportedAt = lostAt;
    report.reportedBy = customer.id;
    db.keyLossReports.add(report);

    Order approved = order(CryptoUtil.uuid(), customer, active, now.minusSeconds(2L * 24 * 60 * 60).toString(),
        buyer("user", "user@example.com", "0909123456", "12 Nguyễn Văn Bảo, Gò Vấp, TP.HCM"),
        List.of(item("ip15", 1), item("oppo-reno", 1)), List.of("ATBM10", "FREESHIP"), "APPROVED", false);
    Order tampered = order(CryptoUtil.uuid(), customer, active, now.minusSeconds(90L * 60).toString(),
        buyer("user", "user@example.com", "0909123456", "88 Phan Văn Trị, Bình Thạnh, TP.HCM"),
        List.of(item("s24", 1)), List.of("ATBM10"), "SIGNED", true);
    Order beforeLost = order(CryptoUtil.uuid(), customer, lost, now.minusSeconds(3L * 60 * 60).toString(),
        buyer("user", "user@example.com", "0909123456", "20 Lê Lợi, Quận 1, TP.HCM"),
        List.of(item("xiaomi14", 1)), List.of("FREESHIP"), "SIGNED", false);
    Order afterLost = order(CryptoUtil.uuid(), customer, lost, now.minusSeconds(60L * 60).toString(),
        buyer("user", "user@example.com", "0909123456", "99 Cách Mạng Tháng 8, Quận 3, TP.HCM"),
        List.of(item("oppo-reno", 2)), List.of(), "NEEDS_MANUAL_REVIEW", false);
    afterLost.manualReviewReasons.add("ORDER_CREATED_AFTER_KEY_LOSS");
    db.orders.addAll(List.of(approved, tampered, beforeLost, afterLost));
    db.alerts.add(alert(tampered.id, "Nhân viên đã sửa thông tin người mua của đơn " + tampered.id.substring(0, 8) + ". Đơn được chuyển sang kiểm tra thủ công.", "high", now.minusSeconds(70L * 60).toString()));
    db.alerts.add(alert(null, "Người dùng báo mất khóa " + lost.key.fingerprint + ". Đơn tạo hoặc sửa sau " + lostAt + " cần được kiểm tra thủ công.", "medium", lostAt));

    Files.createDirectories(Path.of("data"));
    mapper.writeValue(Path.of("data", "db.json").toFile(), db);
    System.out.printf("Seeded %d users, 2 keys, %d orders, %d alerts.%n", db.users.size(), db.orders.size(), db.alerts.size());
  }

  private User user(String idPrefix, String name, String email, String role, String phone, String address, String createdAt) {
    User user = new User();
    user.id = CryptoUtil.uuid();
    user.name = name;
    user.username = idPrefix;
    user.email = email;
    user.passwordHash = passwordService.hash("123456");
    user.role = role;
    user.phone = phone;
    user.address = address;
    user.createdAt = createdAt;
    return user;
  }

  private KeyMaterial keyMaterial(User user, String createdAt, String status, String lostAt) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    String publicKeyPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
    PublicKeyRecord key = keyTool.createRecord(user.id, null, publicKeyPem);
    key.createdAt = createdAt;
    key.status = status;
    key.lostAt = lostAt;
    key.issueMode = "SEED";
    DigitalCertificate certificate = certificateAuthorityService.issueCertificate(user, key, createdAt, createdAt);
    if (lostAt != null) {
      certificateAuthorityService.revoke(certificate, lostAt, "PRIVATE_KEY_LOST");
    }
    return new KeyMaterial(key, certificate, pair.getPrivate());
  }

  private KeyRequest completedRequest(String id, String userId, String adminId, String keyId, String certificateId, String requestedAt, String completedAt) {
    KeyRequest request = new KeyRequest();
    request.id = id;
    request.userId = userId;
    request.requestedAt = requestedAt;
    request.approvedAt = Instant.parse(completedAt).minusSeconds(5 * 60).toString();
    request.approvedBy = adminId;
    request.completedAt = completedAt;
    request.keyId = keyId;
    request.certificateId = certificateId;
    request.issueMode = "SEED";
    request.status = "COMPLETED";
    return request;
  }

  private Order order(String id, User user, KeyMaterial key, String createdAt, Buyer buyer, List<CartItemRequest> items, List<String> promotions, String status, boolean tamper) throws Exception {
    Order order = new Order();
    order.id = id;
    order.userId = user.id;
    order.publicKeyId = key.key.id;
    order.publicKeySnapshot = key.key.publicKeyPem;
    order.publicKeyFingerprint = key.key.fingerprint;
    order.certificateId = key.certificate.id;
    order.certificateSerialNumber = key.certificate.serialNumber;
    order.certificateIssuer = key.certificate.issuerName;
    order.certificateSnapshot = key.certificate;
    order.createdAt = createdAt;
    order.lastModifiedAt = createdAt;
    order.version = 1;
    order.buyer = buyer;
    order.hashAlgorithm = "SHA-256";
    order.signatureAlgorithm = "RSA-SHA256";
    order.signaturePublicKeyPem = key.key.publicKeyPem;
    order.signaturePublicKeyFingerprint = key.key.fingerprint;
    order.status = status;
    cartService.applyTotals(order, cartService.calculate(items, promotions));
    order.hash = orderService.calculateHash(order);
    order.signature = sign(order.hash, key.privateKey);
    order.signedAt = Instant.parse(createdAt).plusSeconds(5 * 60).toString();
    OrderHistory create = new OrderHistory();
    create.at = createdAt;
    create.action = "CREATE_ORDER";
    create.status = "WAITING_SIGNATURE";
    order.history.add(create);
    OrderHistory sign = new OrderHistory();
    sign.at = order.signedAt;
    sign.action = "SIGN_ORDER";
    sign.status = status;
    order.history.add(sign);
    if (tamper) {
      String changedAt = Instant.parse(createdAt).plusSeconds(20 * 60).toString();
      order.buyer.address = "Địa chỉ đã bị nhân viên thay đổi.";
      order.lastModifiedAt = changedAt;
      order.version = 2;
      order.status = "NEEDS_MANUAL_REVIEW";
      order.manualReviewReasons.add("IMMUTABLE_CONTENT_CHANGED");
      OrderHistory edit = new OrderHistory();
      edit.at = changedAt;
      edit.action = "EMPLOYEE_EDIT_IMMUTABLE_FIELD";
      edit.field = "buyer.address";
      order.history.add(edit);
    }
    return order;
  }

  private String sign(String hash, PrivateKey privateKey) throws Exception {
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(CryptoUtil.hexToBytes(hash));
    return Base64.getEncoder().encodeToString(signature.sign());
  }

  private String pem(String label, byte[] encoded) {
    String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
    return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----";
  }

  private Buyer buyer(String name, String email, String phone, String address) {
    Buyer buyer = new Buyer();
    buyer.name = name;
    buyer.email = email;
    buyer.phone = phone;
    buyer.address = address;
    return buyer;
  }

  private CartItemRequest item(String productId, int quantity) {
    CartItemRequest item = new CartItemRequest();
    item.productId = productId;
    item.quantity = quantity;
    return item;
  }

  private Alert alert(String orderId, String message, String severity, String createdAt) {
    Alert alert = new Alert();
    alert.id = CryptoUtil.uuid();
    alert.orderId = orderId;
    alert.message = message;
    alert.severity = severity;
    alert.createdAt = createdAt;
    alert.read = false;
    return alert;
  }

  private record KeyMaterial(PublicKeyRecord key, DigitalCertificate certificate, PrivateKey privateKey) {}
}
