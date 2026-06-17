package vn.edu.hcmuaf.fit.cuahangdienthoai.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmuaf.fit.cuahangdienthoai.api.ApiModels.CreateKeyRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.api.ApiModels.CreateOrderRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.api.ApiModels.CryptoToolRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.api.ApiModels.EmployeeEditRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.api.ApiModels.LoginRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.api.ApiModels.RegisterRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.api.ApiModels.SignatureRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.cart.CartService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.catalog.CatalogService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.certificate.CertificateAuthorityService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.crypto.EncryptionTool;
import vn.edu.hcmuaf.fit.cuahangdienthoai.database.DatabaseService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.key.KeyTool;
import vn.edu.hcmuaf.fit.cuahangdienthoai.key.KeyTool.IssuedKeyMaterial;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Alert;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Buyer;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.ChangeLog;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Database;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.DigitalCertificate;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.KeyLossReport;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.KeyRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Order;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.OrderHistory;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.PublicKeyRecord;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.SessionToken;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.User;
import vn.edu.hcmuaf.fit.cuahangdienthoai.order.OrderAuditService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.order.OrderAuditService.AuditResult;
import vn.edu.hcmuaf.fit.cuahangdienthoai.order.OrderService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.security.PasswordService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.util.CryptoUtil;
import vn.edu.hcmuaf.fit.cuahangdienthoai.util.TimeUtil;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final DatabaseService databaseService;
  private final CatalogService catalogService;
  private final PasswordService passwordService;
  private final KeyTool keyTool;
  private final CertificateAuthorityService certificateAuthorityService;
  private final EncryptionTool encryptionTool;
  private final CartService cartService;
  private final OrderService orderService;
  private final OrderAuditService auditService;
  private final ObjectMapper mapper;

  public ApiController(
      DatabaseService databaseService,
      CatalogService catalogService,
      PasswordService passwordService,
      KeyTool keyTool,
      CertificateAuthorityService certificateAuthorityService,
      EncryptionTool encryptionTool,
      CartService cartService,
      OrderService orderService,
      OrderAuditService auditService,
      ObjectMapper mapper
  ) {
    this.databaseService = databaseService;
    this.catalogService = catalogService;
    this.passwordService = passwordService;
    this.keyTool = keyTool;
    this.certificateAuthorityService = certificateAuthorityService;
    this.encryptionTool = encryptionTool;
    this.cartService = cartService;
    this.orderService = orderService;
    this.auditService = auditService;
    this.mapper = mapper;
  }

  @GetMapping("/catalog")
  public Map<String, Object> catalog() {
    return Map.of("products", catalogService.products(), "promotions", catalogService.promotions());
  }

  @GetMapping("/state")
  public Map<String, Object> state(@RequestHeader(value = "Authorization", required = false) String authorization) {
    Database db = databaseService.read();
    AuthContext auth = requireAuth(db, authorization);
    return scopedDb(db, auth.user);
  }

  @GetMapping("/auth/me")
  public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
    Database db = databaseService.read();
    AuthContext auth = getAuth(db, authorization);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("user", auth == null ? null : publicUser(auth.user));
    return result;
  }

  @PostMapping("/auth/register")
  public Map<String, Object> register(@RequestBody RegisterRequest body) {
    Database db = databaseService.read();
    String email = clean(body.email).toLowerCase();
    String name = clean(body.name);
    String password = body.password == null ? "" : body.password;
    String phone = clean(body.phone);
    String address = clean(body.address);
    if (email.isBlank() || name.isBlank() || password.length() < 6 || phone.isBlank() || address.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Cần nhập đầy đủ họ tên, email, mật khẩu từ 6 ký tự, điện thoại và địa chỉ.");
    }
    if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Email không hợp lệ.");
    }
    if (db.users.stream().anyMatch(user -> email.equals(user.email))) {
      throw new ApiException(HttpStatus.CONFLICT, "Email đã tồn tại.");
    }
    User user = new User();
    user.id = CryptoUtil.uuid();
    user.name = name;
    user.username = email;
    user.email = email;
    user.passwordHash = passwordService.hash(password);
    user.role = "CUSTOMER";
    user.phone = phone;
    user.address = address;
    user.createdAt = TimeUtil.now();
    SessionToken session = createSession(user.id);
    db.users.add(user);
    db.sessions.add(session);
    databaseService.write(db);
    return Map.of("token", session.token, "user", publicUser(user));
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@RequestBody LoginRequest body) {
    Database db = databaseService.read();
    String identifier = clean(body.email != null ? body.email : body.username).toLowerCase();
    String password = body.password == null ? "" : body.password;
    User user = db.users.stream()
        .filter(candidate -> identifier.equals(candidate.email) || identifier.equals(String.valueOf(candidate.username).toLowerCase()))
        .findFirst()
        .orElse(null);
    if (user == null || !passwordService.verify(password, user.passwordHash)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng.");
    }
    SessionToken session = createSession(user.id);
    db.sessions.add(session);
    databaseService.write(db);
    return Map.of("token", session.token, "user", publicUser(user));
  }

  @PostMapping("/auth/logout")
  public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
    Database db = databaseService.read();
    AuthContext auth = getAuth(db, authorization);
    if (auth != null) {
      db.sessions.removeIf(session -> session.token.equals(auth.session.token));
      databaseService.write(db);
    }
    return Map.of("ok", true);
  }

  @PostMapping("/key-requests")
  public Map<String, Object> requestKey(@RequestHeader(value = "Authorization", required = false) String authorization) {
    Database db = databaseService.read();
    AuthContext auth = requireAuth(db, authorization);
    if (!"CUSTOMER".equals(auth.user.role)) throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ khách hàng được yêu cầu cấp khóa.");
    KeyRequest openRequest = db.keyRequests.stream()
        .filter(request -> auth.user.id.equals(request.userId) && List.of("PENDING", "APPROVED").contains(request.status))
        .findFirst()
        .orElse(null);
    if (openRequest != null) {
      throw new ApiException(HttpStatus.CONFLICT, "Bạn đã có một yêu cầu cấp khóa đang xử lý.");
    }
    KeyRequest request = new KeyRequest();
    request.id = CryptoUtil.uuid();
    request.userId = auth.user.id;
    request.requestedAt = TimeUtil.now();
    request.status = "PENDING";
    db.keyRequests.add(0, request);
    databaseService.write(db);
    return Map.of("request", request);
  }

  @PostMapping("/key-requests/{requestId}/approve")
  public Map<String, Object> approveKeyRequest(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String requestId) {
    Database db = databaseService.read();
    AuthContext auth = requireRole(db, authorization, List.of("ADMIN"));
    KeyRequest request = db.keyRequests.stream().filter(item -> item.id.equals(requestId)).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu cấp khóa."));
    if (!"PENDING".equals(request.status)) {
      throw new ApiException(HttpStatus.CONFLICT, "Yêu cầu này không còn ở trạng thái chờ duyệt.");
    }
    request.status = "APPROVED";
    request.approvedAt = TimeUtil.now();
    request.approvedBy = auth.user.id;
    databaseService.write(db);
    return Map.of("request", request);
  }

  @PostMapping("/key-requests/{requestId}/issue-key")
  public Map<String, Object> issueKeyForUser(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String requestId) {
    Database db = databaseService.read();
    AuthContext auth = requireRole(db, authorization, List.of("ADMIN"));
    KeyRequest request = db.keyRequests.stream().filter(item -> item.id.equals(requestId)).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu cấp khóa."));
    if (!List.of("PENDING", "APPROVED").contains(request.status)) {
      throw new ApiException(HttpStatus.CONFLICT, "Yêu cầu này đã hoàn tất hoặc không còn có thể cấp khóa.");
    }
    User owner = db.users.stream().filter(user -> user.id.equals(request.userId)).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng yêu cầu khóa."));
    for (PublicKeyRecord oldKey : db.keys) {
      if (owner.id.equals(oldKey.userId) && "ACTIVE".equals(oldKey.status)) oldKey.status = "ROTATED";
    }
    IssuedKeyMaterial issued = keyTool.issueKeyPair(owner.id, request.id, auth.user.id);
    PublicKeyRecord key = issued.key();
    DigitalCertificate certificate = certificateAuthorityService.issueCertificate(owner, key);
    key.certificateId = certificate.id;
    db.keys.add(key);
    db.certificates.add(certificate);
    request.status = "COMPLETED";
    request.approvedAt = request.approvedAt == null ? TimeUtil.now() : request.approvedAt;
    request.approvedBy = request.approvedBy == null ? auth.user.id : request.approvedBy;
    request.completedAt = TimeUtil.now();
    request.keyId = key.id;
    request.certificateId = certificate.id;
    request.issueMode = "ADMIN_ONE_TIME";
    databaseService.write(db);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("request", request);
    result.put("key", publicKey(key));
    result.put("certificate", certificate);
    result.put("oneTimePrivateKeyPem", issued.privateKeyPem());
    result.put("warning", "Private key chỉ hiển thị một lần trong phản hồi này và không được lưu vào database.");
    return result;
  }

  @PostMapping("/keys")
  public Map<String, Object> createKey(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody CreateKeyRequest body) {
    Database db = databaseService.read();
    AuthContext auth = requireAuth(db, authorization);
    if (!"CUSTOMER".equals(auth.user.role)) throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ khách hàng được tạo khóa.");
    KeyRequest request = db.keyRequests.stream()
        .filter(item -> item.id.equals(body.requestId) && auth.user.id.equals(item.userId) && "APPROVED".equals(item.status))
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Cần có yêu cầu cấp khóa đã được admin duyệt."));
    try {
      keyTool.validatePublicKey(body.publicKeyPem);
    } catch (IllegalArgumentException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    for (PublicKeyRecord oldKey : db.keys) {
      if (auth.user.id.equals(oldKey.userId) && "ACTIVE".equals(oldKey.status)) oldKey.status = "ROTATED";
    }
    PublicKeyRecord key = keyTool.createRecord(auth.user.id, request.id, body.publicKeyPem);
    DigitalCertificate certificate = certificateAuthorityService.issueCertificate(auth.user, key);
    key.certificateId = certificate.id;
    db.keys.add(key);
    db.certificates.add(certificate);
    request.status = "COMPLETED";
    request.completedAt = TimeUtil.now();
    request.keyId = key.id;
    request.certificateId = certificate.id;
    request.issueMode = "CLIENT_GENERATED";
    databaseService.write(db);
    return Map.of("key", publicKey(key), "certificate", certificate);
  }

  @PostMapping("/keys/{keyId}/lost")
  public Map<String, Object> reportLostKey(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String keyId) {
    Database db = databaseService.read();
    AuthContext auth = requireAuth(db, authorization);
    PublicKeyRecord key = db.keys.stream().filter(item -> item.id.equals(keyId)).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa."));
    if ("CUSTOMER".equals(auth.user.role) && !auth.user.id.equals(key.userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Không đủ quyền báo mất khóa này.");
    }
    if ("LOST".equals(key.status)) throw new ApiException(HttpStatus.CONFLICT, "Khóa này đã được báo mất trước đó.");
    key.lostAt = TimeUtil.now();
    key.status = "LOST";
    db.certificates.stream()
        .filter(certificate -> key.id.equals(certificate.publicKeyId))
        .forEach(certificate -> certificateAuthorityService.revoke(certificate, key.lostAt, "PRIVATE_KEY_LOST"));
    KeyLossReport report = new KeyLossReport();
    report.id = CryptoUtil.uuid();
    report.keyId = key.id;
    report.userId = key.userId;
    report.reportedAt = key.lostAt;
    report.reportedBy = auth.user.id;
    db.keyLossReports.add(0, report);
    addAlert(db, null, "Người dùng báo mất khóa " + key.fingerprint + ". Đơn tạo hoặc sửa sau " + key.lostAt + " cần được kiểm tra thủ công.", "medium");
    databaseService.write(db);
    return Map.of("key", publicKey(key));
  }

  @PostMapping("/orders")
  public Map<String, Object> createOrder(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody CreateOrderRequest body) {
    Database db = databaseService.read();
    AuthContext auth = requireAuth(db, authorization);
    if (!"CUSTOMER".equals(auth.user.role)) throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ khách hàng được tạo đơn hàng.");
    PublicKeyRecord activeKey = db.keys.stream().filter(key -> auth.user.id.equals(key.userId) && "ACTIVE".equals(key.status)).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "User chưa có public key đang hoạt động."));
    Buyer buyer = new Buyer();
    buyer.name = clean(body.buyer == null || body.buyer.name == null ? auth.user.name : body.buyer.name);
    buyer.email = clean(body.buyer == null || body.buyer.email == null ? auth.user.email : body.buyer.email).toLowerCase();
    buyer.phone = clean(body.buyer == null ? "" : body.buyer.phone);
    buyer.address = clean(body.buyer == null ? "" : body.buyer.address);
    if (buyer.name.isBlank() || buyer.email.isBlank() || buyer.phone.isBlank() || buyer.address.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Thông tin người mua chưa đầy đủ.");
    }
    CartService.Totals totals;
    try {
      totals = cartService.calculate(body.items, body.promotionCodes);
    } catch (IllegalArgumentException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    String createdAt = TimeUtil.now();
    DigitalCertificate certificate = db.certificates.stream()
        .filter(item -> activeKey.certificateId != null && activeKey.certificateId.equals(item.id))
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Public key chưa có chứng nhận CA."));
    if (!certificateAuthorityService.isUsableAt(certificate, createdAt)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Chứng nhận public key không hợp lệ hoặc đã bị hủy.");
    }
    Order order = new Order();
    order.id = CryptoUtil.uuid();
    order.userId = auth.user.id;
    order.publicKeyId = activeKey.id;
    order.publicKeySnapshot = activeKey.publicKeyPem;
    order.publicKeyFingerprint = activeKey.fingerprint;
    order.certificateId = certificate.id;
    order.certificateSerialNumber = certificate.serialNumber;
    order.certificateIssuer = certificate.issuerName;
    order.certificateSnapshot = certificate;
    order.createdAt = createdAt;
    order.lastModifiedAt = createdAt;
    order.version = 1;
    order.buyer = buyer;
    order.hashAlgorithm = "SHA-256";
    order.status = "WAITING_SIGNATURE";
    OrderHistory history = new OrderHistory();
    history.at = createdAt;
    history.action = "CREATE_ORDER";
    history.status = "WAITING_SIGNATURE";
    order.history.add(history);
    cartService.applyTotals(order, totals);
    order.hash = orderService.calculateHash(order);
    db.orders.add(0, order);
    databaseService.write(db);
    return Map.of("order", order);
  }

  @PostMapping("/orders/{orderId}/signature")
  public Map<String, Object> signOrder(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String orderId, @RequestBody SignatureRequest body) {
    Database db = databaseService.read();
    AuthContext auth = requireAuth(db, authorization);
    if (!"CUSTOMER".equals(auth.user.role)) throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ khách hàng được ký đơn hàng.");
    Order order = findOrder(db, orderId);
    if (!auth.user.id.equals(order.userId)) throw new ApiException(HttpStatus.FORBIDDEN, "Không đủ quyền ký đơn này.");
    if (!List.of("WAITING_SIGNATURE", "SIGNATURE_INVALID").contains(order.status)) {
      throw new ApiException(HttpStatus.CONFLICT, "Đơn hàng không còn ở trạng thái cho phép ký.");
    }
    if (body.signature == null || body.signature.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Chữ ký không được để trống.");
    order.signature = body.signature;
    order.signedAt = TimeUtil.now();
    order.signatureAlgorithm = "RSA-SHA256";
    order.signaturePublicKeyPem = order.publicKeySnapshot;
    order.signaturePublicKeyFingerprint = order.publicKeyFingerprint;
    AuditResult audit = auditService.audit(order, db.keys, db.certificates);
    order.status = audit.signatureValid() && audit.hashValid() ? "SIGNED" : "SIGNATURE_INVALID";
    order.manualReviewReasons = audit.reviewReasons();
    OrderHistory history = new OrderHistory();
    history.at = TimeUtil.now();
    history.action = "SIGN_ORDER";
    history.status = order.status;
    order.history.add(history);
    databaseService.write(db);
    return Map.of("order", order, "audit", publicAudit(audit));
  }

  @PostMapping("/orders/{orderId}/approve")
  public Map<String, Object> approveOrder(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String orderId) {
    Database db = databaseService.read();
    requireRole(db, authorization, List.of("ADMIN", "STAFF"));
    Order order = findOrder(db, orderId);
    if ("WAITING_SIGNATURE".equals(order.status)) throw new ApiException(HttpStatus.CONFLICT, "Đơn hàng chưa được khách hàng ký.");
    if ("APPROVED".equals(order.status)) throw new ApiException(HttpStatus.CONFLICT, "Đơn hàng đã được duyệt trước đó.");
    AuditResult audit = auditService.audit(order, db.keys, db.certificates);
    if (!audit.signatureValid()) {
      order.status = "SIGNATURE_INVALID";
      addAlert(db, order.id, "Đơn " + order.id.substring(0, 8) + " có chữ ký không hợp lệ.", "high");
    } else if (audit.needsManualReview()) {
      order.status = "NEEDS_MANUAL_REVIEW";
      order.manualReviewReasons = audit.reviewReasons();
      addAlert(db, order.id, "Đơn " + order.id.substring(0, 8) + " cần kiểm tra thủ công: " + String.join(", ", audit.reviewReasons()) + ".", "high");
    } else {
      order.status = "APPROVED";
      order.manualReviewReasons = new ArrayList<>();
    }
    OrderHistory history = new OrderHistory();
    history.at = TimeUtil.now();
    history.action = "ADMIN_APPROVE";
    history.status = order.status;
    order.history.add(history);
    databaseService.write(db);
    return Map.of("order", order, "audit", publicAudit(audit));
  }

  @PostMapping("/orders/{orderId}/employee-edit")
  public Map<String, Object> employeeEdit(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String orderId, @RequestBody EmployeeEditRequest body) {
    Database db = databaseService.read();
    AuthContext auth = requireRole(db, authorization, List.of("ADMIN", "STAFF"));
    Order order = findOrder(db, orderId);
    String oldAddress = order.buyer.address;
    String newAddress = clean(body.address == null ? oldAddress + " - nhân viên đã sửa." : body.address);
    if (newAddress.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Địa chỉ mới không được để trống.");
    if (newAddress.equals(oldAddress)) throw new ApiException(HttpStatus.CONFLICT, "Thông tin mới không có thay đổi.");
    String changedAt = TimeUtil.now();
    order.buyer.address = newAddress;
    order.lastModifiedAt = changedAt;
    order.version += 1;
    ChangeLog change = new ChangeLog();
    change.at = changedAt;
    change.actorId = auth.user.id;
    change.field = "buyer.address";
    change.before = oldAddress;
    change.after = newAddress;
    order.changeLog.add(change);
    OrderHistory history = new OrderHistory();
    history.at = changedAt;
    history.action = "EMPLOYEE_EDIT_IMMUTABLE_FIELD";
    history.field = "buyer.address";
    order.history.add(history);
    AuditResult audit = auditService.audit(order, db.keys, db.certificates);
    order.status = "NEEDS_MANUAL_REVIEW";
    order.manualReviewReasons = audit.reviewReasons();
    addAlert(db, order.id, "Nhân viên đã sửa thông tin người mua của đơn " + order.id.substring(0, 8) + ". Đơn được chuyển sang kiểm tra thủ công.", "high");
    databaseService.write(db);
    return Map.of("order", order, "audit", publicAudit(audit));
  }

  @PostMapping("/orders/{orderId}/audit")
  public Map<String, Object> audit(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String orderId) {
    Database db = databaseService.read();
    AuthContext auth = requireAuth(db, authorization);
    Order order = findOrder(db, orderId);
    if ("CUSTOMER".equals(auth.user.role) && !auth.user.id.equals(order.userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Không đủ quyền xem đơn này.");
    }
    return Map.of("audit", publicAudit(auditService.audit(order, db.keys, db.certificates)));
  }

  @PostMapping("/tools/hash/{orderId}")
  public Map<String, Object> hashTool(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String orderId) {
    Database db = databaseService.read();
    requireRole(db, authorization, List.of("ADMIN"));
    return orderService.inspectHash(findOrder(db, orderId));
  }

  @PostMapping("/tools/encrypt")
  public Map<String, Object> encryptTool(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody CryptoToolRequest body) {
    Database db = databaseService.read();
    requireAuth(db, authorization);
    try {
      return mapper.convertValue(encryptionTool.encrypt(body.text, body.passphrase), new TypeReference<>() {});
    } catch (IllegalArgumentException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @PostMapping("/tools/decrypt")
  public Map<String, Object> decryptTool(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody CryptoToolRequest body) {
    Database db = databaseService.read();
    requireAuth(db, authorization);
    try {
      return mapper.convertValue(encryptionTool.decrypt(body.text, body.passphrase, body.salt, body.iv), new TypeReference<>() {});
    } catch (IllegalArgumentException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  private Order findOrder(Database db, String orderId) {
    return db.orders.stream().filter(item -> item.id.equals(orderId)).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
  }

  private void addAlert(Database db, String orderId, String message, String severity) {
    Alert alert = new Alert();
    alert.id = CryptoUtil.uuid();
    alert.orderId = orderId;
    alert.message = message;
    alert.severity = severity;
    alert.createdAt = TimeUtil.now();
    alert.read = false;
    db.alerts.add(0, alert);
  }

  private SessionToken createSession(String userId) {
    SessionToken session = new SessionToken();
    session.token = CryptoUtil.randomHex(32);
    session.userId = userId;
    session.createdAt = TimeUtil.now();
    session.expiresAt = Instant.now().plusSeconds(7L * 24 * 60 * 60).toString();
    return session;
  }

  private AuthContext requireRole(Database db, String authorization, List<String> roles) {
    AuthContext auth = requireAuth(db, authorization);
    if (!roles.contains(auth.user.role)) throw new ApiException(HttpStatus.FORBIDDEN, "Không đủ quyền.");
    return auth;
  }

  private AuthContext requireAuth(Database db, String authorization) {
    AuthContext auth = getAuth(db, authorization);
    if (auth == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập.");
    return auth;
  }

  private AuthContext getAuth(Database db, String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) return null;
    String token = authorization.substring(7);
    SessionToken session = db.sessions.stream().filter(item -> token.equals(item.token)).findFirst().orElse(null);
    if (session == null || Instant.parse(session.expiresAt).isBefore(Instant.now())) return null;
    User user = db.users.stream().filter(item -> item.id.equals(session.userId)).findFirst().orElse(null);
    return user == null ? null : new AuthContext(user, session);
  }

  private Map<String, Object> scopedDb(Database db, User user) {
    if ("ADMIN".equals(user.role) || "STAFF".equals(user.role)) return publicDb(db);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("users", List.of(publicUser(user)));
    result.put("keyRequests", db.keyRequests.stream().filter(request -> user.id.equals(request.userId)).toList());
    result.put("keyLossReports", db.keyLossReports.stream().filter(report -> user.id.equals(report.userId)).toList());
    result.put("keys", db.keys.stream().filter(key -> user.id.equals(key.userId)).map(this::publicKey).toList());
    result.put("certificates", db.certificates.stream().filter(certificate -> user.id.equals(certificate.subjectUserId)).toList());
    result.put("orders", db.orders.stream().filter(order -> user.id.equals(order.userId)).toList());
    result.put("alerts", List.of());
    return result;
  }

  private Map<String, Object> publicDb(Database db) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("users", db.users.stream().map(this::publicUser).toList());
    result.put("keyRequests", db.keyRequests);
    result.put("keyLossReports", db.keyLossReports);
    result.put("keys", db.keys.stream().map(this::publicKey).toList());
    result.put("certificates", db.certificates);
    result.put("orders", db.orders);
    result.put("alerts", db.alerts);
    return result;
  }

  private Map<String, Object> publicUser(User user) {
    Map<String, Object> map = mapper.convertValue(user, new TypeReference<>() {});
    map.remove("passwordHash");
    return map;
  }

  private Map<String, Object> publicKey(PublicKeyRecord key) {
    Map<String, Object> map = mapper.convertValue(key, new TypeReference<>() {});
    map.remove("publicKeyPem");
    return map;
  }

  private Map<String, Object> publicAudit(AuditResult audit) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("hashValid", audit.hashValid());
    result.put("signatureValid", audit.signatureValid());
    result.put("certificateValid", audit.certificateValid());
    result.put("certificateRevokedBeforeOrder", audit.certificateRevokedBeforeOrder());
    result.put("createdAfterKeyLoss", audit.createdAfterKeyLoss());
    result.put("modifiedAfterKeyLoss", audit.modifiedAfterKeyLoss());
    result.put("needsManualReview", audit.needsManualReview());
    result.put("reviewReasons", audit.reviewReasons());
    result.put("currentHash", audit.currentHash());
    result.put("storedHash", audit.storedHash());
    return result;
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private record AuthContext(User user, SessionToken session) {}
}
