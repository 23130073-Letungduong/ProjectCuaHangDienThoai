package vn.edu.hcmuaf.fit.cuahangdienthoai.order;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Order;

@Service
public class OrderService {
  private final HashTool hashTool;

  public OrderService(HashTool hashTool) {
    this.hashTool = hashTool;
  }

  public Map<String, Object> immutablePayload(Order order) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("orderId", order.id);
    payload.put("createdAt", order.createdAt);
    payload.put("buyer", order.buyer);
    payload.put("items", order.items);
    payload.put("promotions", order.promotions);
    payload.put("subtotal", order.subtotal);
    payload.put("discount", order.discount);
    payload.put("shipping", order.shipping);
    payload.put("total", order.total);
    return payload;
  }

  public String calculateHash(Order order) {
    return hashTool.hashObject(immutablePayload(order));
  }

  public Map<String, Object> inspectHash(Order order) {
    Map<String, Object> result = new LinkedHashMap<>();
    Object payload = immutablePayload(order);
    result.put("algorithm", "SHA-256");
    result.put("canonicalPayload", hashTool.canonicalString(payload));
    result.put("calculatedHash", hashTool.hashObject(payload));
    result.put("storedHash", order.hash);
    return result;
  }
}
