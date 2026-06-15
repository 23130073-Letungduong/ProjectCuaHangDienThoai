# Cấu Trúc Database

Ứng dụng demo lưu dữ liệu trong `data/db.json`. Private key không xuất hiện trong database, API hoặc log của server.

## Tổng Quan

```json
{
  "users": [],
  "sessions": [],
  "keyRequests": [],
  "keyLossReports": [],
  "keys": [],
  "certificates": [],
  "orders": [],
  "alerts": []
}
```

## Bảng `users`

| Field | Type | Mô tả |
| --- | --- | --- |
| `id` | UUID | Khóa chính. |
| `name` | string | Họ tên hiển thị. |
| `username` | string | Tên đăng nhập. |
| `email` | string | Email duy nhất. |
| `passwordHash` | string | Mật khẩu PBKDF2-SHA256 kèm salt. |
| `role` | enum | `CUSTOMER`, `STAFF` hoặc `ADMIN`. |
| `phone` | string | Số điện thoại. |
| `address` | string | Địa chỉ mặc định. |
| `createdAt` | ISO datetime | Thời điểm tạo. |

## Bảng `sessions`

| Field | Type | Mô tả |
| --- | --- | --- |
| `token` | string | Bearer token. |
| `userId` | UUID | Người dùng đăng nhập. |
| `createdAt` | ISO datetime | Thời điểm đăng nhập. |
| `expiresAt` | ISO datetime | Thời điểm hết hạn. |

`sessions` và `passwordHash` không được trả về qua API trạng thái.

## Bảng `keyRequests`

Lưu quy trình yêu cầu cấp khóa. Admin chỉ phê duyệt; cặp khóa được tạo trên trình duyệt của khách hàng sau khi yêu cầu được duyệt.

| Field | Type | Mô tả |
| --- | --- | --- |
| `id` | UUID | Mã yêu cầu. |
| `userId` | UUID | Khách hàng yêu cầu khóa. |
| `requestedAt` | ISO datetime | Thời điểm yêu cầu. |
| `approvedAt` | ISO datetime/null | Thời điểm admin duyệt. |
| `approvedBy` | UUID/null | Admin duyệt yêu cầu. |
| `completedAt` | ISO datetime/null | Thời điểm public key được gửi lên server. |
| `keyId` | UUID/null | Public key được tạo từ yêu cầu. |
| `certificateId` | UUID/null | Chứng nhận CA cấp cho public key. |
| `issueMode` | enum/null | `CLIENT_GENERATED`, `ADMIN_ONE_TIME` hoặc `SEED`. |
| `status` | enum | `PENDING`, `APPROVED`, `COMPLETED`. |

## Bảng `keyLossReports`

| Field | Type | Mô tả |
| --- | --- | --- |
| `id` | UUID | Mã báo cáo. |
| `keyId` | UUID | Khóa bị mất hoặc lộ. |
| `userId` | UUID | Chủ sở hữu khóa. |
| `reportedAt` | ISO datetime | Thời điểm báo mất chính xác. |
| `reportedBy` | UUID | Người thực hiện báo mất. |

## Bảng `keys`

Chỉ lưu public key. Khóa cũ không bị xóa để tiếp tục xác minh các đơn hàng cũ.

| Field | Type | Mô tả |
| --- | --- | --- |
| `id` | UUID | Mã public key. |
| `userId` | UUID | Chủ sở hữu. |
| `requestId` | UUID | Yêu cầu cấp khóa đã được duyệt. |
| `publicKeyPem` | PEM | Public key RSA. |
| `fingerprint` | string | 32 ký tự đầu của SHA-256 public key. |
| `certificateId` | UUID/null | Chứng nhận CA xác nhận quyền sở hữu public key. |
| `issueMode` | enum | Nguồn cấp khóa: client tự tạo, admin cấp một lần hoặc seed. |
| `issuedBy` | UUID/null | Admin cấp khóa nếu dùng luồng `ADMIN_ONE_TIME`. |
| `createdAt` | ISO datetime | Thời điểm tạo. |
| `lostAt` | ISO datetime/null | Thời điểm báo mất hoặc lộ khóa. |
| `status` | enum | `ACTIVE`, `LOST`, `ROTATED`. |

## Bảng `certificates`

Mô phỏng chứng nhận X.509 v3 trong slide CA. CA ký thông tin chứng nhận bằng RSA/SHA-256 để chống giả mạo public key.

| Field | Type | Mô tả |
| --- | --- | --- |
| `id` | UUID | Mã chứng nhận. |
| `version` | string | `X.509 v3 demo`. |
| `serialNumber` | string | Số serial do CA cấp. |
| `issuerName` | string | Tên CA phát hành. |
| `subjectUserId`, `subjectName`, `subjectEmail` | string | Chủ thể sở hữu public key. |
| `publicKeyId`, `publicKeyFingerprint` | string | Public key được chứng nhận. |
| `hashAlgorithm` | string | `SHA-256`. |
| `signatureAlgorithm` | string | `SHA256withRSA`. |
| `validFrom`, `validTo`, `issuedAt` | ISO datetime | Thời hạn và thời điểm cấp. |
| `revokedAt`, `revokedReason` | ISO datetime/string/null | Thông tin hủy chứng nhận khi báo mất key. |
| `status` | enum | `VALID`, `REVOKED`. |
| `certificateHash` | SHA-256 hex | Hash canonical của thông tin chứng nhận. |
| `certificateSignature` | base64 | Chữ ký CA trên thông tin chứng nhận. |

## Bảng `orders`

| Field | Type | Mô tả |
| --- | --- | --- |
| `id` | UUID | Mã đơn hàng. |
| `userId` | UUID | Khách hàng tạo đơn. |
| `publicKeyId` | UUID | Khóa được chọn lúc tạo đơn. |
| `publicKeySnapshot` | PEM | Bản chụp public key tại thời điểm tạo đơn. |
| `publicKeyFingerprint` | string | Fingerprint của khóa tạo đơn. |
| `certificateId` | UUID | Chứng nhận CA của public key. |
| `certificateSerialNumber` | string | Serial chứng nhận. |
| `certificateIssuer` | string | CA phát hành. |
| `certificateSnapshot` | object | Bản chụp chứng nhận tại thời điểm tạo đơn. |
| `createdAt` | ISO datetime | Thời điểm tạo đơn. |
| `lastModifiedAt` | ISO datetime | Lần sửa gần nhất. |
| `version` | number | Phiên bản đơn hàng. |
| `buyer` | object | Tên, email, điện thoại và địa chỉ người mua. |
| `items` | array | Sản phẩm, đơn giá và số lượng đã chốt. |
| `promotions` | array | Khuyến mãi đã áp dụng. |
| `subtotal`, `discount`, `shipping`, `total` | number | Các giá trị thanh toán. |
| `hash` | SHA-256 hex | Hash của nội dung bất biến. |
| `hashAlgorithm` | string | `SHA-256`. |
| `signature` | base64/null | Chữ ký của hash. |
| `signatureAlgorithm` | string/null | `RSA-SHA256`. |
| `signaturePublicKeyPem` | PEM/null | Bản chụp khóa dùng xác minh chữ ký. |
| `signaturePublicKeyFingerprint` | string/null | Fingerprint khóa ký. |
| `signedAt` | ISO datetime/null | Thời điểm ký. |
| `status` | enum | Trạng thái xử lý. |
| `manualReviewReasons` | array | Các lý do cần kiểm tra thủ công. |
| `changeLog` | array | Giá trị trước/sau, người sửa và thời điểm sửa. |
| `history` | array | Lịch sử trạng thái và thao tác. |

### Nội Dung Bất Biến

Hệ thống tạo canonical JSON rồi băm SHA-256 các trường:

- `orderId`, `createdAt`;
- `buyer`;
- `items`, `promotions`;
- `subtotal`, `discount`, `shipping`, `total`.

### Trạng Thái Đơn Hàng

| Status | Mô tả |
| --- | --- |
| `WAITING_SIGNATURE` | Đã tạo hash, đang chờ khách ký. |
| `SIGNED` | Hash và chữ ký hợp lệ. |
| `APPROVED` | Admin xác minh và duyệt. |
| `SIGNATURE_INVALID` | Chữ ký không hợp lệ. |
| `NEEDS_MANUAL_REVIEW` | Nội dung bị sửa, hoặc đơn được tạo/sửa sau thời điểm báo mất khóa. |

Các mã trong `manualReviewReasons`:

- `IMMUTABLE_CONTENT_CHANGED`;
- `ORDER_CREATED_AFTER_KEY_LOSS`;
- `ORDER_MODIFIED_AFTER_KEY_LOSS`.
- `CERTIFICATE_INVALID`;
- `CERTIFICATE_REVOKED_BEFORE_ORDER`.

## Bảng `alerts`

| Field | Type | Mô tả |
| --- | --- | --- |
| `id` | UUID | Mã cảnh báo. |
| `orderId` | UUID/null | Đơn liên quan. |
| `message` | string | Nội dung cảnh báo. |
| `severity` | enum | `high`, `medium`, `low`. |
| `createdAt` | ISO datetime | Thời điểm tạo. |
| `read` | boolean | Trạng thái đã đọc. |

## Quan Hệ

- `users` 1-n `sessions`, `keyRequests`, `keyLossReports`, `keys`, `orders`.
- `keyRequests` 1-0..1 `keys` và `certificates`.
- `keys` 1-1 `certificates`, 1-n `orders` và `keyLossReports`.
- `certificates` 1-n `orders`.
- `orders` 1-n `alerts`.

## Dữ Liệu Mẫu

Chạy `npm run seed` để tạo:

- Hai tài khoản `user` và `admin`, mật khẩu `123456`.
- Hai public key: một `ACTIVE`, một `LOST`.
- Hai chứng nhận CA: một `VALID`, một `REVOKED`.
- Bốn đơn: hợp lệ đã duyệt, bị sửa, tạo trước khi mất khóa, tạo sau khi mất khóa.
- Hai yêu cầu cấp khóa hoàn tất, một báo cáo mất khóa và hai cảnh báo.
