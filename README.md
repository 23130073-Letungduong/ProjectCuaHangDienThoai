# Cửa Hàng Điện Thoại

Đồ án môn An Toàn Bảo Mật Hệ Thống Thông Tin. Website bán điện thoại dùng Java Spring Boot, AES-CBC, SHA-256, chữ ký số RSA và chứng nhận CA demo để minh họa các nội dung trong slide bài giảng.

## Yêu Cầu Môi Trường

- Java 17 trở lên.
- Maven 3.9 trở lên.
- Node.js 18 trở lên và npm để chạy Playwright/integration test.

## Cài Đặt Và Chạy

```powershell
npm install
npm run seed
npm start
```

Mở `http://localhost:3000`.

## Tài Khoản Mẫu

| Vai trò | Tên đăng nhập | Mật khẩu |
| --- | --- | --- |
| Khách hàng | `user` | `123456` |
| Quản trị viên | `admin` | `123456` |

## Kiểm Thử

```powershell
npm run test:unit
npm test
```

`npm run test:unit` chạy JUnit 5 trong `src/test/java`.

`npm test` chạy JUnit, tạo dữ liệu mẫu bằng Java, build jar Spring Boot và kiểm thử tích hợp API.

Kiểm thử giao diện bằng Playwright khi server đang chạy:

```powershell
npm run screenshot
```

Ảnh kết quả được tạo trong thư mục `screenshots` và không đưa lên GitHub.

## Luồng Nghiệp Vụ Chính

1. Khách hàng đăng ký hoặc đăng nhập.
2. Khách gửi yêu cầu cấp khóa.
3. Admin duyệt yêu cầu.
4. Trình duyệt khách tạo cặp khóa RSA, hoặc admin cấp cặp khóa một lần rồi gửi private key cho khách.
5. Public key được CA demo cấp chứng nhận; private key không được lưu trong database.
6. Khách tạo đơn, nhận hash SHA-256 và ký bằng private key.
7. Admin kiểm tra hash, chữ ký, chứng nhận CA và thời điểm báo mất khóa trước khi duyệt.
8. Đơn bị sửa hoặc có thời điểm tạo/sửa sau khi mất khóa chuyển sang `NEEDS_MANUAL_REVIEW`.

## Cấu Trúc

```text
ProjectCuaHangDienThoai/
|-- data/
|   `-- db.json
|-- src/
|   |-- main/
|   |   |-- java/
|   |   |   `-- vn/edu/hcmuaf/fit/cuahangdienthoai/
|   |   |       |-- PhoneShopApplication.java
|   |   |       |-- api/
|   |   |       |-- cart/
|   |   |       |-- catalog/
|   |   |       |-- certificate/
|   |   |       |-- crypto/
|   |   |       |-- database/
|   |   |       |-- key/
|   |   |       |-- model/
|   |   |       |-- order/
|   |   |       |-- security/
|   |   |       |-- seed/
|   |   |       `-- util/
|   |   `-- resources/
|   |       |-- application.yml
|   |       `-- static/
|   |           |-- index.html
|   |           |-- styles.css
|   |           |-- app.js
|   |           `-- tools/
|   |               |-- KeyTool.js
|   |               `-- SignatureTool.js
|   `-- test/
|       `-- java/
|           `-- vn/edu/hcmuaf/fit/cuahangdienthoai/
|               `-- BusinessPackageTest.java
|-- scripts/
|   |-- test.js
|   `-- screenshot.js
|-- database.md
|-- system requirement.md
|-- ARCHITECTURE.md
|-- pom.xml
|-- package.json
`-- package-lock.json
```

Chi tiết package và class nằm trong `ARCHITECTURE.md`. Cấu trúc database nằm trong `database.md`. Yêu cầu chức năng và bảo mật nằm trong `system requirement.md`.
