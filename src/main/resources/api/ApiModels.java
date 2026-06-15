package vn.edu.hcmuaf.fit.cuahangdienthoai.api;

import java.util.List;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Buyer;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.CartItemRequest;

public final class ApiModels {
  private ApiModels() {}

  public static class RegisterRequest {
    public String name;
    public String email;
    public String password;
    public String phone;
    public String address;
  }

  public static class LoginRequest {
    public String email;
    public String username;
    public String password;
  }

  public static class CreateKeyRequest {
    public String requestId;
    public String publicKeyPem;
  }

  public static class CryptoToolRequest {
    public String text;
    public String passphrase;
    public String salt;
    public String iv;
  }

  public static class CreateOrderRequest {
    public Buyer buyer;
    public List<CartItemRequest> items;
    public List<String> promotionCodes;
  }

  public static class SignatureRequest {
    public String signature;
  }

  public static class EmployeeEditRequest {
    public String address;
  }
}
