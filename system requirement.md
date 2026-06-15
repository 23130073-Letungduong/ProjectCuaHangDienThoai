# Yêu Cầu Hệ Thống

## Mục Tiêu

Xây dựng website bán điện thoại dùng SHA-256 và chữ ký số RSA để bảo vệ tính toàn vẹn, xác thực nguồn ký và phát hiện thay đổi trái phép trên đơn hàng.

## Vai Trò

- `CUSTOMER`: đăng ký, đăng nhập, mua hàng, yêu cầu khóa, tạo khóa trên thiết bị, ký đơn và báo mất khóa.
- `ADMIN`: duyệt yêu cầu cấp khóa, quản lý đơn, dùng tool băm, xác minh chữ ký và nhận cảnh báo.
- `STAFF`: quyền API quản lý đơn tương tự admin, nhưng không được duyệt yêu cầu cấp khóa.

## Yêu Cầu Chức Năng

| ID | Yêu cầu | Trạng thái |
| --- | --- | --- |
| FR-01 | Đăng ký, đăng nhập, đăng xuất và phân quyền giao diện/API. | Hoàn thành |
| FR-02 | Chỉ hiển thị giỏ hàng, khóa và đơn hàng sau khi khách đăng nhập. | Hoàn thành |
| FR-03 | Khách gửi yêu cầu cấp khóa; admin duyệt yêu cầu hoặc admin cấp key một lần. | Hoàn thành |
| FR-04 | Sau khi được duyệt, trình duyệt khách tạo cặp RSA 2048-bit, hoặc admin cấp cặp khóa một lần theo đề bài. | Hoàn thành |
| FR-05 | Private key không lưu trong database; nếu admin cấp thì chỉ trả một lần, nếu khách tạo thì chỉ tồn tại phía client. | Hoàn thành |
| FR-06 | Server lưu public key gắn với user, yêu cầu đã duyệt và chứng nhận CA. | Hoàn thành |
| FR-07 | Báo mất khóa phải lưu chính xác `reportedAt`/`lostAt`; public key cũ vẫn được giữ. | Hoàn thành |
| FR-08 | Tạo đơn từ sản phẩm, khuyến mãi và thông tin người mua. | Hoàn thành |
| FR-09 | Server canonicalize nội dung bất biến và tạo hash SHA-256. | Hoàn thành |
| FR-10 | Khách ký hash bằng private key trên trình duyệt rồi gửi chữ ký lên server. | Hoàn thành |
| FR-11 | Đơn lưu chữ ký, thời điểm ký, bản chụp public key và chứng nhận CA tương ứng. | Hoàn thành |
| FR-12 | Admin có tool tính lại hash và so sánh với hash đã lưu. | Hoàn thành |
| FR-13 | Admin xác minh hash, chữ ký, chứng nhận CA và thời điểm báo mất khóa trước khi duyệt. | Hoàn thành |
| FR-14 | Đơn tạo hoặc bị sửa sau thời điểm báo mất khóa chuyển sang kiểm tra thủ công. | Hoàn thành |
| FR-15 | Sửa sản phẩm, khuyến mãi hoặc thông tin người mua làm hash sai và tạo cảnh báo admin. | Hoàn thành |
| FR-16 | Lưu lịch sử trạng thái, phiên bản và giá trị trước/sau của thay đổi. | Hoàn thành |
| FR-17 | Có 3-4 đơn hàng mẫu phục vụ kiểm thử. | Hoàn thành |
| FR-18 | Có Tool mã hóa AES-CBC/PKCS5-PKCS7 để minh họa bảo mật bí mật dữ liệu theo slide mode/padding. | Hoàn thành |
| FR-19 | Có chứng nhận CA mô phỏng X.509 và revoke chứng nhận khi báo mất private key. | Hoàn thành |

## Luồng Cấp Khóa

1. Khách hàng gửi yêu cầu cấp khóa.
2. Admin duyệt yêu cầu.
3. Trình duyệt khách tạo cặp khóa RSA bằng WebCrypto, hoặc admin dùng chức năng cấp key một lần.
4. Public key được gửi/lưu trên server; private key không ghi vào database và chỉ hiển thị một lần.
5. Yêu cầu chuyển từ `PENDING` sang `APPROVED`, rồi `COMPLETED`.
6. Khi tạo khóa mới, khóa `ACTIVE` trước đó chuyển thành `ROTATED`.
7. CA demo cấp chứng nhận cho public key để xác thực quyền sở hữu khóa công khai.

## Luồng Tạo Và Ký Đơn

1. Khách tạo đơn bằng public key đang `ACTIVE`.
2. Server chốt sản phẩm, giá, khuyến mãi và thông tin người mua.
3. Server tạo canonical JSON và hash SHA-256.
4. Trình duyệt ký hash bằng private key.
5. Server xác minh chữ ký bằng public key snapshot của đơn.
6. Server kiểm tra chứng nhận CA của public key.
7. Admin tính lại hash, kiểm tra chữ ký, chứng nhận và duyệt đơn hợp lệ.

## Xử Lý Mất Khóa Và Gian Lận

- Báo mất khóa không xóa public key cũ.
- Báo mất khóa sẽ revoke chứng nhận CA tương ứng từ đúng thời điểm báo mất.
- Đơn tạo trước `lostAt` vẫn có thể được xác minh bằng khóa cũ.
- Đơn có `createdAt > lostAt` nhận lý do `ORDER_CREATED_AFTER_KEY_LOSS`.
- Đơn có `lastModifiedAt > lostAt` nhận lý do `ORDER_MODIFIED_AFTER_KEY_LOSS`.
- Nội dung bất biến khác hash đã lưu nhận lý do `IMMUTABLE_CONTENT_CHANGED`.
- Đơn dùng chứng nhận đã bị revoke trước thời điểm tạo nhận lý do `CERTIFICATE_REVOKED_BEFORE_ORDER`.
- Các trường hợp trên chuyển thành `NEEDS_MANUAL_REVIEW`; hệ thống không tự kết luận gian lận hoặc tự hủy đơn.

## Yêu Cầu Bảo Mật

| ID | Yêu cầu | Cách đáp ứng |
| --- | --- | --- |
| SR-01 | Private key không được lưu trên server/database. | WebCrypto sinh và ký phía client; luồng admin cấp key chỉ trả private key một lần, không ghi vào database/state. |
| SR-02 | Mật khẩu không lưu plaintext. | PBKDF2-SHA256, salt riêng, 120.000 vòng. |
| SR-03 | API phải xác thực và phân quyền. | Bearer session và kiểm tra `CUSTOMER`/`ADMIN`/`STAFF`. |
| SR-04 | Nội dung đơn có biểu diễn ổn định. | Canonical JSON sắp xếp khóa trước khi băm. |
| SR-05 | Bảo vệ toàn vẹn đơn. | SHA-256 và so sánh hash khi audit. |
| SR-06 | Xác thực chữ ký. | RSA-SHA256 với public key snapshot. |
| SR-07 | Không mất khả năng kiểm tra đơn cũ. | Giữ khóa cũ và bản chụp khóa trong từng đơn. |
| SR-08 | Phát hiện sửa trái phép. | `lastModifiedAt`, `version`, `changeLog`, `history`, `alerts`. |
| SR-09 | Không lộ dữ liệu nhạy cảm qua state API. | Loại `passwordHash`, session và toàn bộ private key. |
| SR-10 | Chống giả mạo public key theo slide CA. | CA demo ký chứng nhận public key bằng SHA256withRSA. |
| SR-11 | Minh họa bảo mật bí mật dữ liệu. | Tool mã hóa AES-CBC với IV ngẫu nhiên và PBKDF2-SHA256. |

## Bảng Màu Giao Diện

| Mục đích | Màu |
| --- | --- |
| Nền | `#FFF7ED` |
| Surface/Card | `#FFFFFF` |
| Chính | `#F97316` |
| Chính khi hover | `#EA580C` |
| Nhấn | `#10B981` |
| Chữ chính | `#1F2937` |
| Chữ phụ | `#6B7280` |
| Viền | `#FED7AA` |
| Lỗi | `#EF4444` |

## Dữ Liệu Mẫu

- Tài khoản `user` và `admin`, mật khẩu `123456`.
- Một đơn hợp lệ đã duyệt.
- Một đơn bị sửa thông tin người mua và cần kiểm tra thủ công.
- Một đơn tạo trước thời điểm báo mất khóa.
- Một đơn tạo sau thời điểm báo mất khóa và cần kiểm tra thủ công.
- Hai chứng nhận CA mẫu: một còn hiệu lực, một đã bị revoke theo khóa LOST.

## Tiêu Chí Nghiệm Thu

- `npm run seed` tạo đủ dữ liệu mẫu.
- `npm test` vượt qua toàn bộ kiểm thử API và bảo mật.
- Khách chưa đăng nhập không thấy chức năng mua hàng, khóa hoặc đơn hàng.
- Khóa chỉ được tạo sau khi admin duyệt yêu cầu.
- Database không chứa `BEGIN PRIVATE KEY`.
- State API không chứa private key dù admin cấp key một lần.
- Public key có chứng nhận CA, audit trả về trạng thái `certificateValid`.
- Đơn mới ở `WAITING_SIGNATURE`; ký hợp lệ thành `SIGNED`; duyệt hợp lệ thành `APPROVED`.
- Đơn bị sửa hoặc có mốc thời gian sau khi mất khóa thành `NEEDS_MANUAL_REVIEW`.
- Admin thấy yêu cầu cấp khóa, tool băm, kết quả audit và cảnh báo.
