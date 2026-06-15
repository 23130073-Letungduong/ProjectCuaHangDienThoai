package vn.edu.hcmuaf.fit.cuahangdienthoai.model;

import java.util.ArrayList;
import java.util.List;

public final class Entities {
  private Entities() {}

  public static class Database {
    public List<User> users = new ArrayList<>();
    public List<SessionToken> sessions = new ArrayList<>();
    public List<KeyRequest> keyRequests = new ArrayList<>();
    public List<KeyLossReport> keyLossReports = new ArrayList<>();
    public List<PublicKeyRecord> keys = new ArrayList<>();
    public List<DigitalCertificate> certificates = new ArrayList<>();
    public List<Order> orders = new ArrayList<>();
    public List<Alert> alerts = new ArrayList<>();
  }

  public static class User {
    public String id;
    public String name;
    public String username;
    public String email;
    public String passwordHash;
    public String role;
    public String phone;
    public String address;
    public String createdAt;
  }

  public static class SessionToken {
    public String token;
    public String userId;
    public String createdAt;
    public String expiresAt;
  }

  public static class KeyRequest {
    public String id;
    public String userId;
    public String requestedAt;
    public String approvedAt;
    public String approvedBy;
    public String completedAt;
    public String keyId;
    public String certificateId;
    public String issueMode;
    public String status;
  }

  public static class KeyLossReport {
    public String id;
    public String keyId;
    public String userId;
    public String reportedAt;
    public String reportedBy;
  }

  public static class PublicKeyRecord {
    public String id;
    public String userId;
    public String requestId;
    public String publicKeyPem;
    public String fingerprint;
    public String certificateId;
    public String issueMode;
    public String issuedBy;
    public String createdAt;
    public String lostAt;
    public String status;
  }

  public static class DigitalCertificate {
    public String id;
    public String version;
    public String serialNumber;
    public String issuerName;
    public String subjectUserId;
    public String subjectName;
    public String subjectEmail;
    public String publicKeyId;
    public String publicKeyFingerprint;
    public String hashAlgorithm;
    public String signatureAlgorithm;
    public String validFrom;
    public String validTo;
    public String issuedAt;
    public String revokedAt;
    public String revokedReason;
    public String status;
    public String certificateHash;
    public String certificateSignature;
  }

  public static class Product {
    public String id;
    public String name;
    public long price;
    public Long oldPrice;
    public String brand;
    public String image;
    public String spec;

    public Product() {}

    public Product(String id, String name, long price, Long oldPrice, String brand, String image, String spec) {
      this.id = id;
      this.name = name;
      this.price = price;
      this.oldPrice = oldPrice;
      this.brand = brand;
      this.image = image;
      this.spec = spec;
    }
  }

  public static class Promotion {
    public String code;
    public String name;
    public Integer percent;
    public Long amount;
    public Boolean freeShipping;

    public Promotion() {}

    public Promotion(String code, String name, Integer percent, Long amount, Boolean freeShipping) {
      this.code = code;
      this.name = name;
      this.percent = percent;
      this.amount = amount;
      this.freeShipping = freeShipping;
    }
  }

  public static class Buyer {
    public String name;
    public String email;
    public String phone;
    public String address;
  }

  public static class CartItemRequest {
    public String productId;
    public int quantity;
  }

  public static class OrderItem {
    public String productId;
    public String name;
    public String brand;
    public long unitPrice;
    public int quantity;
    public long lineTotal;
  }

  public static class Order {
    public String id;
    public String userId;
    public String publicKeyId;
    public String publicKeySnapshot;
    public String publicKeyFingerprint;
    public String certificateId;
    public String certificateSerialNumber;
    public String certificateIssuer;
    public DigitalCertificate certificateSnapshot;
    public String createdAt;
    public String lastModifiedAt;
    public int version;
    public Buyer buyer;
    public List<OrderItem> items = new ArrayList<>();
    public List<Promotion> promotions = new ArrayList<>();
    public long subtotal;
    public long discount;
    public long shipping;
    public long total;
    public String hash;
    public String hashAlgorithm;
    public String signature;
    public String signatureAlgorithm;
    public String signaturePublicKeyPem;
    public String signaturePublicKeyFingerprint;
    public String signedAt;
    public String status;
    public List<String> manualReviewReasons = new ArrayList<>();
    public List<ChangeLog> changeLog = new ArrayList<>();
    public List<OrderHistory> history = new ArrayList<>();
  }

  public static class ChangeLog {
    public String at;
    public String actorId;
    public String field;
    public String before;
    public String after;
  }

  public static class OrderHistory {
    public String at;
    public String action;
    public String status;
    public String field;
  }

  public static class Alert {
    public String id;
    public String orderId;
    public String message;
    public String severity;
    public String createdAt;
    public boolean read;
  }
}
