# Kiến Trúc Source

Dự án dùng Java Spring Boot cho backend và frontend tĩnh HTML, CSS, JavaScript. JavaScript phía frontend chỉ dùng để gọi API, render giao diện và chạy WebCrypto vì trình duyệt phải giữ private key, ký đơn và chạy Tool mã hóa phía client.

## Phần I. Giỏ Hàng Và Tool Tạo Key

Package backend:

- `vn.edu.hcmuaf.fit.cuahangdienthoai.cart`
- `vn.edu.hcmuaf.fit.cuahangdienthoai.key`

| Class | Trách nhiệm |
| --- | --- |
| `CartService` | Kiểm tra sản phẩm, số lượng, gộp sản phẩm trùng, áp dụng khuyến mãi và tính tổng giỏ hàng. |
| `KeyTool` | Kiểm tra public key RSA tối thiểu 2048-bit, tạo fingerprint SHA-256 và tạo bản ghi public key. |

Frontend:

- `src/main/resources/static/tools/KeyTool.js`

Chức năng:

- Tạo cặp RSA 2048-bit bằng WebCrypto.
- Xuất public key và private key dạng PEM.
- Cho phép tải private key hoặc mở mail client để gửi private key.
- Private key chỉ tồn tại trong trình duyệt, không gửi lên backend và không ghi vào database.
- Admin cũng có luồng cấp key một lần theo đề bài: private key chỉ trả trong một response, public key được lưu, private key không ghi vào database.

## Tool Mã Hóa Theo Slide Mode/Padding

Package backend:

- `vn.edu.hcmuaf.fit.cuahangdienthoai.crypto`

Frontend:

- `src/main/resources/static/tools/EncryptionTool.js`

| Class | Trách nhiệm |
| --- | --- |
| `EncryptionTool` | Mã hóa/giải mã AES-CBC với IV ngẫu nhiên, PBKDF2-SHA256 và PKCS5/PKCS7 padding. |

Tool này minh họa `Data Confidentiality` trong slide tổng quan và các nội dung ECB/CBC/padding trong slide mode. Giao diện client-side ưu tiên không gửi nội dung nhạy cảm lên server.

## Phần II. Tạo Đơn Hàng Và Tool Băm

Package backend:

- `vn.edu.hcmuaf.fit.cuahangdienthoai.order`

| Class | Trách nhiệm |
| --- | --- |
| `HashTool` | Canonicalize dữ liệu, sắp xếp key object và tạo hash SHA-256. |
| `OrderService` | Chọn nội dung bất biến của đơn, tính hash đơn và cung cấp dữ liệu cho tool băm. |

Nội dung bất biến gồm:

- Mã đơn và thời điểm tạo đơn.
- Thông tin người mua.
- Danh sách sản phẩm đã chốt.
- Danh sách khuyến mãi đã áp dụng.
- `subtotal`, `discount`, `shipping`, `total`.

## Phần III. Quản Lí Đơn Và Tool Ký

Package backend:

- `vn.edu.hcmuaf.fit.cuahangdienthoai.order`

| Class | Trách nhiệm |
| --- | --- |
| `SignatureTool` | Xác minh chữ ký RSA-SHA256 bằng public key snapshot của đơn. |
| `OrderAuditService` | Kiểm tra hash, chữ ký, chứng nhận CA, thời điểm mất khóa và các lý do cần kiểm tra thủ công. |

Frontend:

- `src/main/resources/static/tools/SignatureTool.js`

Chức năng:

- Nhập private key PEM trên thiết bị khách hàng.
- Ký hash đơn bằng WebCrypto.
- Chỉ gửi chữ ký Base64 lên backend.

## API Và Bảo Mật

Package backend:

- `vn.edu.hcmuaf.fit.cuahangdienthoai.api`
- `vn.edu.hcmuaf.fit.cuahangdienthoai.certificate`
- `vn.edu.hcmuaf.fit.cuahangdienthoai.security`
- `vn.edu.hcmuaf.fit.cuahangdienthoai.database`

| Class | Trách nhiệm |
| --- | --- |
| `ApiController` | Cung cấp API đăng ký, đăng nhập, khóa, đơn hàng, audit và tool băm. |
| `ApiException` | Lỗi nghiệp vụ có HTTP status rõ ràng. |
| `GlobalExceptionHandler` | Chuẩn hóa lỗi API, bao gồm JSON không hợp lệ. |
| `CertificateAuthorityService` | Cấp, ký, xác minh và revoke chứng nhận public key kiểu X.509 demo. |
| `PasswordService` | Hash và verify mật khẩu bằng PBKDF2-SHA256, salt riêng. |
| `DatabaseService` | Đọc/ghi `data/db.json`, bảo đảm cấu trúc dữ liệu demo. |

## Model Và Dữ Liệu Mẫu

Package backend:

- `vn.edu.hcmuaf.fit.cuahangdienthoai.model`
- `vn.edu.hcmuaf.fit.cuahangdienthoai.catalog`
- `vn.edu.hcmuaf.fit.cuahangdienthoai.seed`
- `vn.edu.hcmuaf.fit.cuahangdienthoai.util`

| Class | Trách nhiệm |
| --- | --- |
| `Entities` | Chứa các class dữ liệu: user, key request, key, order, alert và các object con. |
| `CatalogService` | Cung cấp 8 sản phẩm và mã khuyến mãi mẫu. |
| `SeedApplication` | Tạo dữ liệu mẫu bằng Java, gồm user/admin, public key, đơn hàng, báo mất khóa và cảnh báo. |
| `CryptoUtil` | UUID, random hex, SHA-256, encode/decode hex. |
| `TimeUtil` | Tạo timestamp ISO. |

## Frontend

Thư mục `src/main/resources/static` gồm:

- `index.html`: cấu trúc các màn hình đăng nhập, cửa hàng, khóa, đơn hàng và admin.
- `styles.css`: giao diện theo palette màu yêu cầu, responsive desktop/mobile.
- `app.js`: state, API client, render và điều phối sự kiện.
- `tools/KeyTool.js`: Tool Tạo Key phía client.
- `tools/EncryptionTool.js`: Tool mã hóa AES-CBC phía client.
- `tools/SignatureTool.js`: Tool Ký phía client.

## Unit Test JUnit

Thư mục `src/test/java/vn/edu/hcmuaf/fit/cuahangdienthoai` chứa JUnit 5 cho các package nghiệp vụ.

| File | Nội dung test |
| --- | --- |
| `BusinessPackageTest.java` | Test `CartService`, `KeyTool`, `EncryptionTool`, `CertificateAuthorityService`, `HashTool`, `OrderService`, `SignatureTool` và `OrderAuditService`. |

Chạy:

```powershell
npm run test:unit
```

Lệnh trên gọi Maven và chạy JUnit thật trong Java.
