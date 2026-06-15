const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { spawn, spawnSync } = require("child_process");

const PORT = 3100;
const BASE = `http://localhost:${PORT}`;
const MVN = process.platform === "win32" ? "mvn.cmd" : "mvn";
const JAVA = process.platform === "win32" ? "java.exe" : "java";
const SEED_MAIN = "vn.edu.hcmuaf.fit.cuahangdienthoai.seed.SeedApplication";
let token = "";

function wait(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function api(pathname, method = "GET", body) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(BASE + pathname, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  });
  const data = await response.json();
  if (!response.ok) {
    const error = new Error(`${method} ${pathname}: ${JSON.stringify(data)}`);
    error.status = response.status;
    error.data = data;
    throw error;
  }
  return data;
}

async function waitForServer() {
  for (let i = 0; i < 120; i++) {
    try {
      await api("/api/catalog");
      return;
    } catch {
      await wait(500);
    }
  }
  throw new Error("Server Java không khởi động kịp.");
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function runShell(command, message) {
  const result = spawnSync(command, { cwd: process.cwd(), stdio: "inherit", shell: true });
  assert(result.status === 0, message);
}

async function expectError(pathname, method, body, status, text) {
  try {
    await api(pathname, method, body);
  } catch (error) {
    assert(error.status === status, `${pathname} phải trả HTTP ${status}.`);
    assert(String(error.data?.error || "").includes(text), `${pathname} phải báo lỗi chứa "${text}".`);
    return;
  }
  throw new Error(`${pathname} lẽ ra phải thất bại.`);
}

async function login(identifier) {
  const result = await api("/api/auth/login", "POST", { email: identifier, password: "123456" });
  token = result.token;
  return result;
}

async function main() {
  runShell(`${MVN} -q -DskipTests compile exec:java "-Dexec.mainClass=${SEED_MAIN}"`, "Tạo dữ liệu mẫu bằng Java thất bại.");

  runShell(`${MVN} -q -DskipTests package`, "Build jar Java thất bại.");

  const server = spawn(JAVA, ["-jar", "target/cuahangdienthoai-1.0.0.jar"], {
    cwd: process.cwd(),
    env: { ...process.env, PORT: String(PORT) },
    stdio: "ignore"
  });

  try {
    await waitForServer();

    const catalog = await api("/api/catalog");
    assert(catalog.products.length === 8, "Cần có 8 sản phẩm mẫu.");
    const invalidJson = await fetch(`${BASE}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{"
    });
    const invalidJsonBody = await invalidJson.json();
    assert(invalidJson.status === 400 && invalidJsonBody.error === "JSON không hợp lệ.", "JSON sai định dạng phải trả 400.");

    const userLogin = await login("user");
    assert(userLogin.user.name === "user", "Tài khoản user phải hiển thị tên user.");
    const userState = await api("/api/state");
    assert(userState.users.length === 1 && userState.users[0].username === "user", "Khách hàng chỉ được thấy dữ liệu của mình.");
    await expectError("/api/auth/register", "POST", {
      name: "Thiếu số điện thoại",
      email: "missing-phone@example.com",
      password: "123456",
      phone: "",
      address: "Test Address"
    }, 400, "đầy đủ");

    const registered = await api("/api/auth/register", "POST", {
      name: "Security Test",
      email: "security-test@example.com",
      password: "123456",
      phone: "0909000000",
      address: "Test Address"
    });
    token = registered.token;
    assert(registered.user.role === "CUSTOMER", "Đăng ký mới phải có vai trò CUSTOMER.");

    const { publicKey, privateKey } = crypto.generateKeyPairSync("rsa", { modulusLength: 2048 });
    const publicKeyPem = publicKey.export({ type: "spki", format: "pem" });
    const privateKeyPem = privateKey.export({ type: "pkcs8", format: "pem" });
    const weakPair = crypto.generateKeyPairSync("rsa", { modulusLength: 1024 });
    const weakPublicKeyPem = weakPair.publicKey.export({ type: "spki", format: "pem" });

    await expectError("/api/keys", "POST", { publicKeyPem }, 403, "admin duyệt");
    const keyRequest = await api("/api/key-requests", "POST", {});
    assert(keyRequest.request.status === "PENDING", "Yêu cầu cấp khóa mới phải ở trạng thái PENDING.");
    const customerToken = token;

    const adminLogin = await login("admin");
    assert(adminLogin.user.name === "admin", "Tài khoản admin phải hiển thị tên admin.");
    const approvedRequest = await api(`/api/key-requests/${keyRequest.request.id}/approve`, "POST", {});
    assert(approvedRequest.request.status === "APPROVED", "Admin phải duyệt được yêu cầu cấp khóa.");
    await expectError("/api/keys", "POST", { requestId: keyRequest.request.id, publicKeyPem }, 403, "khách hàng");

    token = customerToken;
    await expectError("/api/keys", "POST", {
      requestId: keyRequest.request.id,
      publicKeyPem: "-----BEGIN PUBLIC KEY-----\nSAI_DINH_DANG\n-----END PUBLIC KEY-----"
    }, 400, "không hợp lệ");
    await expectError("/api/keys", "POST", {
      requestId: keyRequest.request.id,
      publicKeyPem: weakPublicKeyPem
    }, 400, "2048-bit");
    const keyResult = await api("/api/keys", "POST", {
      requestId: keyRequest.request.id,
      publicKeyPem
    });
    assert(keyResult.key.status === "ACTIVE", "Khóa mới phải ở trạng thái ACTIVE.");
    assert(keyResult.key.requestId === keyRequest.request.id, "Khóa phải liên kết với yêu cầu đã duyệt.");
    assert(keyResult.key.certificateId === keyResult.certificate.id, "Public key phải có chứng nhận CA đi kèm.");
    assert(keyResult.certificate.status === "VALID", "Chứng nhận CA mới phải hợp lệ.");
    assert(keyResult.certificate.signatureAlgorithm === "SHA256withRSA", "Chứng nhận CA phải dùng chữ ký RSA/SHA-256.");
    assert(!keyResult.key.publicKeyPem, "API tạo khóa không được trả lại toàn bộ public key.");

    const completedState = await api("/api/state");
    const completedRequest = completedState.keyRequests.find(item => item.id === keyRequest.request.id);
    assert(completedRequest?.status === "COMPLETED", "Yêu cầu phải thành COMPLETED sau khi trình duyệt gửi public key.");
    assert(completedState.certificates.some(certificate => certificate.id === keyResult.certificate.id), "State của khách phải có chứng nhận public key.");

    const encrypted = await api("/api/tools/encrypt", "POST", {
      text: "Nội dung bí mật cần mã hóa.",
      passphrase: "123456"
    });
    assert(encrypted.algorithm === "AES/CBC/PKCS5Padding", "Tool mã hóa phải dùng AES-CBC với PKCS5/PKCS7 padding.");
    assert(encrypted.ciphertext && encrypted.ciphertext !== "Nội dung bí mật cần mã hóa.", "Ciphertext phải khác plaintext.");
    const decrypted = await api("/api/tools/decrypt", "POST", {
      text: encrypted.ciphertext,
      passphrase: "123456",
      salt: encrypted.salt,
      iv: encrypted.iv
    });
    assert(decrypted.plaintext === "Nội dung bí mật cần mã hóa.", "Tool giải mã phải khôi phục đúng plaintext.");

    await expectError("/api/orders", "POST", {
      buyer: { name: "Security Test", email: "security-test@example.com", phone: "0909000000", address: "Test Address" },
      items: [],
      promotionCodes: []
    }, 400, "Giỏ hàng");
    await expectError("/api/orders", "POST", {
      buyer: { name: "Security Test", email: "security-test@example.com", phone: "0909000000", address: "Test Address" },
      items: [{ productId: "ip15", quantity: 1 }],
      promotionCodes: ["SAI_MA"]
    }, 400, "Khuyến mãi");
    await expectError("/api/orders", "POST", {
      buyer: { name: "Security Test", email: "security-test@example.com", phone: "0909000000", address: "Test Address" },
      items: [{ productId: "ip15", quantity: 21 }],
      promotionCodes: []
    }, 400, "Số lượng");
    await expectError("/api/orders", "POST", {
      buyer: { name: "Security Test", email: "security-test@example.com", phone: "0909000000", address: "Test Address" },
      items: [{ productId: "ip15", quantity: 10 }, { productId: "ip15", quantity: 11 }],
      promotionCodes: []
    }, 400, "tối đa 20");

    const freeShipOrder = await api("/api/orders", "POST", {
      buyer: {
        name: "Security Test",
        email: "security-test@example.com",
        phone: "0909000000",
        address: "Test Address"
      },
      items: [{ productId: "ip15", quantity: 1 }],
      promotionCodes: ["FREESHIP"]
    });
    assert(freeShipOrder.order.discount === 0, "FREESHIP không được trừ thêm vào discount.");
    assert(freeShipOrder.order.shipping === 0, "FREESHIP phải đưa phí giao hàng về 0.");
    assert(freeShipOrder.order.total === 18990000, "Tổng FREESHIP phải bằng tiền hàng.");

    const created = await api("/api/orders", "POST", {
      buyer: {
        name: "Security Test",
        email: "security-test@example.com",
        phone: "0909000000",
        address: "Test Address"
      },
      items: [{ productId: "ip15", quantity: 1 }],
      promotionCodes: ["ATBM10"]
    });
    assert(created.order.status === "WAITING_SIGNATURE", "Đơn mới phải chờ ký.");
    assert(created.order.total === 17121000, "Tổng đơn ATBM10 phải bằng tiền hàng trừ 10% cộng phí ship.");
    assert(created.order.publicKeySnapshot.includes("BEGIN PUBLIC KEY"), "Đơn phải lưu bản chụp public key.");
    assert(created.order.lastModifiedAt === created.order.createdAt, "Đơn mới chưa được sửa đổi.");

    const signature = crypto.sign("RSA-SHA256", Buffer.from(created.order.hash, "hex"), privateKeyPem).toString("base64");
    const signed = await api(`/api/orders/${created.order.id}/signature`, "POST", { signature });
    assert(signed.order.status === "SIGNED", "Chữ ký hợp lệ phải tạo trạng thái SIGNED.");
    assert(signed.audit.hashValid && signed.audit.signatureValid && signed.audit.certificateValid, "Hash, chữ ký và chứng nhận CA phải hợp lệ.");
    assert(signed.order.signaturePublicKeyFingerprint === keyResult.key.fingerprint, "Đơn phải lưu fingerprint của khóa ký.");
    assert(signed.order.certificateSerialNumber === keyResult.certificate.serialNumber, "Đơn phải lưu serial chứng nhận CA.");
    await expectError(`/api/orders/${created.order.id}/signature`, "POST", { signature }, 409, "không còn");

    await expectError(`/api/orders/${created.order.id}/approve`, "POST", {}, 403, "Không đủ quyền");

    await login("admin");
    await expectError(`/api/orders/${freeShipOrder.order.id}/approve`, "POST", {}, 409, "chưa được khách hàng ký");
    const hashTool = await api(`/api/tools/hash/${created.order.id}`, "POST", {});
    assert(hashTool.calculatedHash === hashTool.storedHash, "Tool băm phải trả hash trùng với hash đã lưu.");

    const approved = await api(`/api/orders/${created.order.id}/approve`, "POST", {});
    assert(approved.order.status === "APPROVED", "Đơn hợp lệ phải được duyệt.");
    await expectError(`/api/orders/${created.order.id}/approve`, "POST", {}, 409, "đã được duyệt");

    token = customerToken;
    const lost = await api(`/api/keys/${keyResult.key.id}/lost`, "POST", {});
    assert(lost.key.status === "LOST" && lost.key.lostAt, "Báo mất khóa phải lưu thời điểm chính xác.");
    await expectError(`/api/keys/${keyResult.key.id}/lost`, "POST", {}, 409, "đã được báo mất");
    await wait(10);

    await login("admin");
    const edited = await api(`/api/orders/${created.order.id}/employee-edit`, "POST", { address: "Tampered Address" });
    assert(!edited.audit.hashValid, "Sửa trường bất biến phải làm hash sai.");
    assert(edited.audit.modifiedAfterKeyLoss, "Hệ thống phải phát hiện đơn bị sửa sau khi báo mất khóa.");
    assert(edited.order.status === "NEEDS_MANUAL_REVIEW", "Đơn bị sửa phải chuyển sang kiểm tra thủ công.");
    assert(edited.order.changeLog.length === 1, "Thay đổi phải được ghi vào changeLog.");

    const state = await api("/api/state");
    assert(state.users.some(user => user.role === "ADMIN"), "Admin phải thấy dữ liệu vai trò.");
    assert(state.alerts.some(alert => alert.severity === "high"), "Admin phải nhận cảnh báo mức high.");
    assert(state.keyLossReports.some(report => report.keyId === keyResult.key.id), "Phải lưu báo cáo mất khóa.");
    assert(!JSON.stringify(state).includes("passwordHash"), "API state không được lộ passwordHash.");

    const lostKey = state.keys.find(key => key.status === "LOST" && key.lostAt);
    const orderAfterLost = state.orders.find(order =>
      order.publicKeyId === lostKey.id && new Date(order.createdAt) > new Date(lostKey.lostAt)
    );
    assert(orderAfterLost, "Dữ liệu mẫu cần có đơn tạo sau thời điểm mất khóa.");
    const manualReview = await api(`/api/orders/${orderAfterLost.id}/approve`, "POST", {});
    assert(manualReview.order.status === "NEEDS_MANUAL_REVIEW", "Đơn tạo sau khi mất khóa phải kiểm tra thủ công.");
    assert(manualReview.audit.createdAfterKeyLoss, "Audit phải chỉ rõ đơn được tạo sau khi mất khóa.");

    const issuedRegistered = await api("/api/auth/register", "POST", {
      name: "Admin Issued",
      email: "admin-issued@example.com",
      password: "123456",
      phone: "0909333444",
      address: "Test Address"
    });
    token = issuedRegistered.token;
    const issueRequest = await api("/api/key-requests", "POST", {});
    await login("admin");
    const issuedByAdmin = await api(`/api/key-requests/${issueRequest.request.id}/issue-key`, "POST", {});
    assert(issuedByAdmin.oneTimePrivateKeyPem.includes("BEGIN PRIVATE KEY"), "Admin cấp key phải trả private key đúng một lần.");
    assert(issuedByAdmin.key.status === "ACTIVE", "Public key do admin cấp phải ACTIVE.");
    assert(issuedByAdmin.certificate.status === "VALID", "Public key do admin cấp phải có chứng nhận CA hợp lệ.");
    const stateAfterIssue = await api("/api/state");
    assert(!JSON.stringify(stateAfterIssue).includes("BEGIN PRIVATE KEY"), "State API không được lưu hoặc trả private key do admin cấp.");

    const rawDatabase = fs.readFileSync(path.join(process.cwd(), "data", "db.json"), "utf8");
    assert(!rawDatabase.includes("BEGIN PRIVATE KEY"), "Database tuyệt đối không được lưu private key.");

    console.log("All tests passed.");
  } finally {
    server.kill();
  }
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
