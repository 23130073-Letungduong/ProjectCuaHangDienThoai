package vn.edu.hcmuaf.fit.cuahangdienthoai.catalog;

import java.util.List;
import org.springframework.stereotype.Service;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Product;
import vn.edu.hcmuaf.fit.cuahangdienthoai.model.Entities.Promotion;

@Service
public class CatalogService {
  private final List<Product> products = List.of(
      new Product("ip15", "iPhone 15 128GB", 18_990_000L, 22_990_000L, "Apple", "/assets/products/phone-black.png", "6.1 inch · 128GB · Camera kép 48MP"),
      new Product("s24", "Samsung Galaxy S24 256GB", 16_990_000L, 20_990_000L, "Samsung", "/assets/products/phone-blue.png", "6.2 inch · 256GB · Galaxy AI"),
      new Product("xiaomi14", "Xiaomi 14 5G 256GB", 15_990_000L, 18_990_000L, "Xiaomi", "/assets/products/phone-gold.png", "6.36 inch · 256GB · Camera 50MP"),
      new Product("oppo-reno", "OPPO Reno11 F 5G", 8_990_000L, 10_990_000L, "OPPO", "/assets/products/phone-coral.png", "6.7 inch · 256GB · Sạc nhanh 67W"),
      new Product("pixel9", "Google Pixel 9 128GB", 17_990_000L, 19_990_000L, "Google", "/assets/products/phone-black.png", "6.3 inch · 128GB · Camera AI"),
      new Product("iphone16e", "iPhone 16e 128GB", 16_990_000L, 18_990_000L, "Apple", "/assets/products/phone-coral.png", "6.1 inch · Chip A18 · Camera 48MP"),
      new Product("s24fe", "Samsung Galaxy S24 FE", 13_990_000L, 16_990_000L, "Samsung", "/assets/products/phone-blue.png", "6.7 inch · 256GB · Pin 4700mAh"),
      new Product("redmi-note", "Xiaomi Redmi Note 14 Pro", 9_990_000L, 11_990_000L, "Xiaomi", "/assets/products/phone-gold.png", "6.67 inch · 256GB · Camera 200MP")
  );

  private final List<Promotion> promotions = List.of(
      new Promotion("ATBM10", "Giảm 10% đồ án ATBM", 10, null, null),
      new Promotion("FREESHIP", "Miễn phí vận chuyển", null, null, true)
  );

  public List<Product> products() {
    return products;
  }

  public List<Promotion> promotions() {
    return promotions;
  }
}
