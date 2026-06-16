package vn.edu.hcmuaf.fit.cuahangdienthoai.cart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.edu.hcmuaf.fit.cuahangdienthoai.catalog.CatalogService;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.CartItemRequest;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Order;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.OrderItem;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Product;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Promotion;

@Service
public class CartService {
  private final CatalogService catalog;

  public CartService(CatalogService catalog) {
    this.catalog = catalog;
  }

  public Totals calculate(List<CartItemRequest> items, List<String> promotionCodes) {
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("Giỏ hàng phải có ít nhất một sản phẩm.");
    }
    if (promotionCodes == null) promotionCodes = List.of();

    Map<String, Integer> quantities = new LinkedHashMap<>();
    for (CartItemRequest item : items) {
      String productId = item == null ? "" : String.valueOf(item.productId);
      int quantity = item == null ? 0 : item.quantity;
      if (quantity < 1 || quantity > 20) throw new IllegalArgumentException("Số lượng sản phẩm không hợp lệ.");
      int newQuantity = quantities.getOrDefault(productId, 0) + quantity;
      if (newQuantity > 20) throw new IllegalArgumentException("Mỗi sản phẩm chỉ được đặt tối đa 20 chiếc.");
      quantities.put(productId, newQuantity);
    }

    Totals totals = new Totals();
    for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
      Product product = catalog.products().stream()
          .filter(candidate -> candidate.id.equals(entry.getKey()))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại: " + entry.getKey() + "."));
      OrderItem item = new OrderItem();
      item.productId = product.id;
      item.name = product.name;
      item.brand = product.brand;
      item.unitPrice = product.price;
      item.quantity = entry.getValue();
      item.lineTotal = product.price * entry.getValue();
      totals.items.add(item);
      totals.subtotal += item.lineTotal;
    }

    List<String> uniqueCodes = promotionCodes.stream().map(code -> String.valueOf(code).trim()).distinct().toList();
    for (String code : uniqueCodes) {
      Promotion promotion = catalog.promotions().stream()
          .filter(candidate -> candidate.code.equals(code))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Khuyến mãi không tồn tại: " + code + "."));
      totals.promotions.add(copy(promotion));
      if (promotion.percent != null) totals.discount += Math.round(totals.subtotal * promotion.percent / 100.0);
      if (promotion.amount != null && !Boolean.TRUE.equals(promotion.freeShipping)) totals.discount += promotion.amount;
    }
    totals.discount = Math.min(totals.discount, totals.subtotal);
    totals.shipping = totals.promotions.stream().anyMatch(promotion -> Boolean.TRUE.equals(promotion.freeShipping)) ? 0 : 30_000;
    totals.total = totals.subtotal - totals.discount + totals.shipping;
    return totals;
  }

  public void applyTotals(Order order, Totals totals) {
    order.items = totals.items;
    order.promotions = totals.promotions;
    order.subtotal = totals.subtotal;
    order.discount = totals.discount;
    order.shipping = totals.shipping;
    order.total = totals.total;
  }

  private Promotion copy(Promotion promotion) {
    return new Promotion(promotion.code, promotion.name, promotion.percent, promotion.amount, promotion.freeShipping);
  }

  public static class Totals {
    public List<OrderItem> items = new ArrayList<>();
    public List<Promotion> promotions = new ArrayList<>();
    public long subtotal;
    public long discount;
    public long shipping;
    public long total;
  }
}
