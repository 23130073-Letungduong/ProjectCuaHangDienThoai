const state = {
  token: localStorage.getItem("authToken") || "",
  user: null,
  catalog: { products: [], promotions: [] },
  db: { users: [], keyRequests: [], keyLossReports: [], keys: [], certificates: [], orders: [], alerts: [] },
  privateKeyPem: "",
  cart: {},
  productSearch: "",
  activeBrand: "ALL"
};

const fmt = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });

function qs(selector) {
  return document.querySelector(selector);
}

function qsa(selector) {
  return [...document.querySelectorAll(selector)];
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function api(path, options = {}) {
  const headers = { "Content-Type": "application/json" };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const res = await fetch(path, {
    headers,
    ...options,
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || "Lỗi API.");
  return data;
}

function requireLogin() {
  if (state.user) return true;
  showTab("auth");
  alert("Hãy đăng nhập trước.");
  return false;
}

function canManage() {
  return state.user && ["ADMIN", "STAFF"].includes(state.user.role);
}

function resetPrivateKey() {
  state.privateKeyPem = "";
  qs("#privateKeyBox").value = "";
  qs("#signPrivateKey").value = "";
  qs("#downloadPrivateBtn").disabled = true;
  qs("#emailPrivateLink").classList.add("disabled");
  qs("#emailPrivateLink").removeAttribute("href");
}

async function loadMe() {
  if (!state.token) return;
  try {
    const { user } = await api("/api/auth/me");
    state.user = user;
  } catch {
    resetPrivateKey();
    localStorage.removeItem("authToken");
    state.token = "";
    state.user = null;
  }
}

async function refreshState() {
  if (!state.user) {
    state.db = { users: [], keyRequests: [], keyLossReports: [], keys: [], certificates: [], orders: [], alerts: [] };
  } else {
    state.db = await api("/api/state");
  }
  renderSession();
  renderKeys();
  renderOrders();
  renderAdmin();
}

function renderSession() {
  const badge = qs("#sessionBadge");
  const logout = qs("#logoutBtn");
  const authOnly = qsa("[data-auth-only]");
  const customerTabs = qsa('[data-role-tab="customer"]');
  const managerTabs = qsa('[data-role-tab="manager"]');
  if (!state.user) {
    badge.textContent = "Chưa đăng nhập.";
    logout.hidden = true;
    authOnly.forEach(item => item.hidden = false);
    customerTabs.forEach(tab => tab.hidden = true);
    managerTabs.forEach(tab => tab.hidden = true);
    qs("#currentUserText").textContent = "Đăng nhập để tạo đơn hàng.";
    return;
  }
  badge.textContent = state.user.name;
  logout.hidden = false;
  authOnly.forEach(item => item.hidden = true);
  customerTabs.forEach(tab => tab.hidden = canManage());
  managerTabs.forEach(tab => tab.hidden = !canManage());
  qs("#buyerName").value = state.user.name || "";
  qs("#buyerEmail").value = state.user.email || "";
  qs("#buyerPhone").value = state.user.phone || qs("#buyerPhone").value;
  qs("#buyerAddress").value = state.user.address || qs("#buyerAddress").value;
  qs("#currentUserText").textContent = `Đang đăng nhập: ${state.user.name} <${state.user.email}>.`;
}

function activePromos() {
  return qsa(".promoCode").filter(input => input.checked).map(input => input.value);
}

function selectedItems() {
  return Object.entries(state.cart)
    .filter(([, quantity]) => quantity > 0)
    .map(([productId, quantity]) => ({ productId, quantity }));
}

function renderProducts() {
  const wrap = qs("#products");
  wrap.innerHTML = "";
  const template = qs("#productTemplate");
  const normalizedSearch = state.productSearch.trim().toLocaleLowerCase("vi");
  const products = state.catalog.products.filter(product => {
    const brandMatches = state.activeBrand === "ALL" || product.brand === state.activeBrand;
    const searchMatches = !normalizedSearch || `${product.name} ${product.brand} ${product.spec || ""}`.toLocaleLowerCase("vi").includes(normalizedSearch);
    return brandMatches && searchMatches;
  });

  for (const product of products) {
    const node = template.content.cloneNode(true);
    node.querySelector(".brand").textContent = product.brand;
    node.querySelector("h3").textContent = product.name;
    node.querySelector(".spec").textContent = product.spec || "Điện thoại chính hãng · Bảo hành 12 tháng";
    node.querySelector(".price").textContent = fmt.format(product.price);
    node.querySelector(".old-price").textContent = product.oldPrice ? fmt.format(product.oldPrice) : "";
    const discount = product.oldPrice ? Math.round((1 - product.price / product.oldPrice) * 100) : 0;
    node.querySelector(".discount").textContent = discount ? `Giảm ${discount}%` : "Giá tốt";
    const image = node.querySelector("img");
    image.src = product.image || "/assets/products/phone-black.png";
    image.alt = product.name;
    const input = node.querySelector("input");
    input.value = state.cart[product.id] || 0;
    input.addEventListener("input", () => {
      const quantity = Number.parseInt(input.value, 10);
      state.cart[product.id] = Number.isInteger(quantity) ? Math.min(20, Math.max(0, quantity)) : 0;
      input.value = state.cart[product.id];
      renderCartTotal();
    });
    wrap.appendChild(node);
  }
  if (!products.length) {
    wrap.innerHTML = `<div class="empty-state">Không tìm thấy sản phẩm phù hợp.</div>`;
  }
  renderCartTotal();
}

function renderCartTotal() {
  const subtotal = selectedItems().reduce((sum, item) => {
    const product = state.catalog.products.find(p => p.id === item.productId);
    return sum + product.price * item.quantity;
  }, 0);
  const hasDiscount = activePromos().includes("ATBM10");
  const hasFreeship = activePromos().includes("FREESHIP");
  const discount = hasDiscount ? Math.round(subtotal * 0.1) : 0;
  const shipping = hasFreeship || subtotal === 0 ? 0 : 30000;
  qs("#cartTotal").textContent = `Tạm tính: ${fmt.format(subtotal - discount + shipping)}`;
  const quantity = selectedItems().reduce((sum, item) => sum + item.quantity, 0);
  qs("#headerCartCount").textContent = `${quantity} sản phẩm`;
}

function renderKeys() {
  const keys = state.db.keys || [];
  const requests = state.db.keyRequests || [];
  const certificates = state.db.certificates || [];
  const latestRequest = requests[0];
  const requestButton = qs("#requestKeyBtn");
  const generateButton = qs("#generateKeyBtn");
  const requestStatus = qs("#keyRequestStatus");

  if (state.user && !canManage()) {
    if (!latestRequest) {
      requestStatus.textContent = "Chưa có yêu cầu cấp khóa.";
      requestButton.disabled = false;
      generateButton.disabled = true;
    } else if (latestRequest.status === "PENDING") {
      requestStatus.textContent = `Yêu cầu gửi lúc ${new Date(latestRequest.requestedAt).toLocaleString("vi-VN")} đang chờ admin duyệt.`;
      requestButton.disabled = true;
      generateButton.disabled = true;
    } else if (latestRequest.status === "APPROVED") {
      requestStatus.textContent = "Admin đã duyệt. Bạn có thể tạo cặp khóa trên thiết bị ngay bây giờ.";
      requestButton.disabled = true;
      generateButton.disabled = false;
    } else {
      requestStatus.textContent = "Yêu cầu gần nhất đã hoàn tất. Bạn có thể gửi yêu cầu mới khi cần thay khóa.";
      requestButton.disabled = false;
      generateButton.disabled = true;
    }
  }

  qs("#keyList").innerHTML = keys.map(key => `
    <div class="item">
      <div class="row">
        <span class="status ${escapeHtml(key.status)}">${escapeHtml(key.status)}</span>
        <strong>${escapeHtml(key.fingerprint)}</strong>
      </div>
      <div class="meta">Tạo: ${new Date(key.createdAt).toLocaleString("vi-VN")}${key.lostAt ? ` | Báo mất: ${new Date(key.lostAt).toLocaleString("vi-VN")}` : ""}</div>
      ${certificates.find(certificate => certificate.id === key.certificateId) ? `<div class="meta">CA: ${escapeHtml(certificates.find(certificate => certificate.id === key.certificateId).issuerName)} | Serial: ${escapeHtml(certificates.find(certificate => certificate.id === key.certificateId).serialNumber)} | Cert: ${escapeHtml(certificates.find(certificate => certificate.id === key.certificateId).status)}</div>` : ""}
      ${state.user && key.userId === state.user.id && key.status === "ACTIVE" ? `<button class="danger" data-lost-key="${escapeHtml(key.id)}">Báo mất khóa</button>` : ""}
    </div>
  `).join("") || `<p class="hint">Chưa có public key nào.</p>`;

  const adminKeys = state.db.keys || [];
  qs("#lostKeyList").innerHTML = adminKeys.map(key => {
    const owner = (state.db.users || []).find(user => user.id === key.userId);
    return `
      <div class="item">
        <div><strong>${escapeHtml(key.fingerprint)}</strong></div>
        <div class="meta">${owner ? `${escapeHtml(owner.name)} &lt;${escapeHtml(owner.email)}&gt;` : "Không rõ user."}</div>
        ${certificates.find(certificate => certificate.id === key.certificateId) ? `<div class="meta">Chứng nhận CA: ${escapeHtml(certificates.find(certificate => certificate.id === key.certificateId).serialNumber)} · ${escapeHtml(certificates.find(certificate => certificate.id === key.certificateId).status)}</div>` : ""}
        <div class="meta">Trạng thái: ${escapeHtml(key.status)}</div>
        <button class="danger" data-lost-key="${escapeHtml(key.id)}" ${key.status === "LOST" ? "disabled" : ""}>Báo mất khóa</button>
      </div>
    `;
  }).join("") || `<p class="hint">Chưa có khóa.</p>`;

  qsa("[data-lost-key]").forEach(btn => {
    btn.addEventListener("click", async () => {
      await api(`/api/keys/${btn.dataset.lostKey}/lost`, { method: "POST", body: {} });
      if (state.user && !canManage()) resetPrivateKey();
      await refreshState();
    });
  });
}

function orderSummary(order) {
  const items = order.items.map(item => `${escapeHtml(item.name)} x${escapeHtml(item.quantity)}`).join(", ");
  return `
    <div class="row">
      <span class="status ${escapeHtml(order.status)}">${escapeHtml(order.status)}</span>
      <strong>Đơn ${escapeHtml(order.id.slice(0, 8))}</strong>
      <span>${fmt.format(order.total)}</span>
    </div>
    <div>${items}</div>
    <div class="meta">Hash: ${escapeHtml(order.hash)}</div>
    <div class="meta">Public key: ${escapeHtml(order.publicKeyFingerprint || "Chưa có snapshot")}</div>
    <div class="meta">Certificate: ${escapeHtml(order.certificateSerialNumber || "Chưa có chứng nhận")} · ${escapeHtml(order.certificateIssuer || "Không rõ CA")}</div>
    <div class="meta">Tạo: ${new Date(order.createdAt).toLocaleString("vi-VN")} | Sửa nội dung: ${new Date(order.lastModifiedAt || order.createdAt).toLocaleString("vi-VN")}${order.signedAt ? ` | Ký: ${new Date(order.signedAt).toLocaleString("vi-VN")}` : ""}</div>
    ${(order.manualReviewReasons || []).length ? `<div class="meta">Lý do kiểm tra thủ công: ${order.manualReviewReasons.map(escapeHtml).join(", ")}</div>` : ""}
  `;
}

function renderOrders() {
  const orders = state.db.orders || [];
  qs("#userOrders").innerHTML = orders.map(order => `
    <div class="item">
      ${orderSummary(order)}
      <button data-sign-order="${escapeHtml(order.id)}" ${order.status !== "WAITING_SIGNATURE" ? "disabled" : ""}>Ký hash bằng private key</button>
    </div>
  `).join("") || `<p class="hint">Chưa có đơn hàng.</p>`;

  qsa("[data-sign-order]").forEach(btn => {
    btn.addEventListener("click", async () => {
      const pem = qs("#signPrivateKey").value.trim();
      if (!pem) return alert("Hãy dán private key PEM để ký đơn.");
      const order = state.db.orders.find(o => o.id === btn.dataset.signOrder);
      const signature = await window.SignatureTool.signHash(order.hash, pem);
      await api(`/api/orders/${order.id}/signature`, { method: "POST", body: { signature } });
      await refreshState();
    });
  });
}

function renderAdmin() {
  if (!canManage()) {
    qs("#alerts").innerHTML = `<p class="hint">Chỉ admin hoặc nhân viên mới xem được.</p>`;
    qs("#adminOrders").innerHTML = "";
    return;
  }
  qs("#statOrders").textContent = state.db.orders.length;
  qs("#statPending").textContent = state.db.orders.filter(order => ["WAITING_SIGNATURE", "SIGNED"].includes(order.status)).length;
  qs("#statAlerts").textContent = state.db.alerts.filter(alert => !alert.read).length;
  qs("#statLostKeys").textContent = state.db.keys.filter(key => key.status === "LOST").length;
  qs("#alerts").innerHTML = state.db.alerts.map(alert => `
    <div class="item">
      <strong>${escapeHtml(alert.severity.toUpperCase())}</strong>
      <div>${escapeHtml(alert.message)}</div>
      <div class="meta">${new Date(alert.createdAt).toLocaleString("vi-VN")}</div>
    </div>
  `).join("") || `<p class="hint">Chưa có cảnh báo.</p>`;

  qs("#keyRequests").innerHTML = (state.db.keyRequests || []).map(request => {
    const owner = (state.db.users || []).find(user => user.id === request.userId);
    return `
      <div class="item">
        <div class="row"><span class="status ${escapeHtml(request.status)}">${escapeHtml(request.status)}</span><strong>${escapeHtml(owner?.name || "Không rõ user")}</strong></div>
        <div class="meta">Yêu cầu: ${new Date(request.requestedAt).toLocaleString("vi-VN")}</div>
        ${request.status === "PENDING" ? `<button data-approve-key-request="${escapeHtml(request.id)}">Duyệt yêu cầu</button>` : ""}
        ${["PENDING", "APPROVED"].includes(request.status) ? `<button data-issue-key-request="${escapeHtml(request.id)}" class="warning">Admin cấp key một lần</button>` : ""}
      </div>
    `;
  }).join("") || `<p class="hint">Chưa có yêu cầu cấp khóa.</p>`;

  qs("#adminOrders").innerHTML = state.db.orders.map(order => `
    <div class="item">
      ${orderSummary(order)}
      <div class="row">
        <button data-audit-order="${escapeHtml(order.id)}" class="secondary">Kiểm tra</button>
        <button data-hash-order="${escapeHtml(order.id)}" class="secondary">Tool băm</button>
        <button data-approve-order="${escapeHtml(order.id)}">Duyệt đơn</button>
        <button data-edit-order="${escapeHtml(order.id)}" class="warning">Giả lập nhân viên sửa</button>
      </div>
      <div id="audit-${escapeHtml(order.id)}" class="meta"></div>
    </div>
  `).join("") || `<p class="hint">Chưa có đơn hàng.</p>`;

  qsa("[data-audit-order]").forEach(btn => btn.addEventListener("click", async () => {
    const { audit } = await api(`/api/orders/${btn.dataset.auditOrder}/audit`, { method: "POST", body: {} });
    document.getElementById(`audit-${btn.dataset.auditOrder}`).textContent =
      `Hash hợp lệ: ${audit.hashValid} | Chữ ký hợp lệ: ${audit.signatureValid} | Chứng nhận CA hợp lệ: ${audit.certificateValid} | Cần kiểm tra thủ công: ${audit.needsManualReview} | Lý do: ${audit.reviewReasons.join(", ") || "Không có"}.`;
  }));
  qsa("[data-hash-order]").forEach(btn => btn.addEventListener("click", async () => {
    const result = await api(`/api/tools/hash/${btn.dataset.hashOrder}`, { method: "POST", body: {} });
    document.getElementById(`audit-${btn.dataset.hashOrder}`).textContent =
      `Tool băm ${result.algorithm}: ${result.calculatedHash} | Hash đã lưu: ${result.storedHash}.`;
  }));
  qsa("[data-approve-key-request]").forEach(btn => btn.addEventListener("click", async () => {
    await api(`/api/key-requests/${btn.dataset.approveKeyRequest}/approve`, { method: "POST", body: {} });
    await refreshState();
  }));
  qsa("[data-issue-key-request]").forEach(btn => btn.addEventListener("click", async () => {
    const result = await api(`/api/key-requests/${btn.dataset.issueKeyRequest}/issue-key`, { method: "POST", body: {} });
    qs("#adminPrivateKeyBox").value = result.oneTimePrivateKeyPem || "";
    alert("Private key chỉ hiển thị một lần trong ô của admin. Hãy gửi cho đúng khách hàng rồi xóa khỏi thiết bị.");
    await refreshState();
  }));
  qsa("[data-approve-order]").forEach(btn => btn.addEventListener("click", async () => {
    await api(`/api/orders/${btn.dataset.approveOrder}/approve`, { method: "POST", body: {} });
    await refreshState();
  }));
  qsa("[data-edit-order]").forEach(btn => btn.addEventListener("click", async () => {
    await api(`/api/orders/${btn.dataset.editOrder}/employee-edit`, { method: "POST", body: { address: "Địa chỉ đã bị nhân viên thay đổi." } });
    await refreshState();
  }));
}

async function login(email, password) {
  resetPrivateKey();
  const result = await api("/api/auth/login", { method: "POST", body: { email, password } });
  state.token = result.token;
  state.user = result.user;
  localStorage.setItem("authToken", state.token);
  await refreshState();
  showTab(canManage() ? "admin" : "shop");
}

async function register() {
  resetPrivateKey();
  const result = await api("/api/auth/register", {
    method: "POST",
    body: {
      name: qs("#registerName").value,
      email: qs("#registerEmail").value,
      password: qs("#registerPassword").value,
      phone: qs("#registerPhone").value,
      address: qs("#registerAddress").value
    }
  });
  state.token = result.token;
  state.user = result.user;
  localStorage.setItem("authToken", state.token);
  await refreshState();
  showTab("keys");
}

async function logout() {
  if (state.token) await api("/api/auth/logout", { method: "POST", body: {} }).catch(() => {});
  resetPrivateKey();
  state.token = "";
  state.user = null;
  localStorage.removeItem("authToken");
  await refreshState();
  showTab("auth");
}

async function generateKeyPair() {
  if (!requireLogin()) return;
  const approvedRequest = (state.db.keyRequests || []).find(request => request.status === "APPROVED");
  if (!approvedRequest) throw new Error("Chưa có yêu cầu cấp khóa được admin duyệt.");
  const generateButton = qs("#generateKeyBtn");
  const requestStatus = qs("#keyRequestStatus");
  generateButton.disabled = true;
  requestStatus.textContent = "Đang tạo cặp khóa RSA và lưu public key.";
  try {
    const { publicKeyPem, privateKeyPem } = await window.KeyTool.generate();
    await api("/api/keys", { method: "POST", body: { requestId: approvedRequest.id, publicKeyPem } });
    state.privateKeyPem = privateKeyPem;
    qs("#privateKeyBox").value = privateKeyPem;
    qs("#signPrivateKey").value = privateKeyPem;
    qs("#downloadPrivateBtn").disabled = false;
    qs("#emailPrivateLink").classList.remove("disabled");
    qs("#emailPrivateLink").href = `mailto:${state.user.email}?subject=Private key - Cửa hàng Điện Thoại&body=${encodeURIComponent(privateKeyPem)}`;
    await refreshState();
  } catch (error) {
    resetPrivateKey();
    await refreshState();
    throw error;
  }
}

async function requestKey() {
  if (!requireLogin()) return;
  await api("/api/key-requests", { method: "POST", body: {} });
  await refreshState();
}

function downloadPrivateKey() {
  const blob = new Blob([state.privateKeyPem], { type: "application/x-pem-file" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `private-key-${Date.now()}.pem`;
  link.click();
  URL.revokeObjectURL(url);
}

async function encryptLocalText() {
  if (!requireLogin()) return;
  const result = await window.EncryptionTool.encrypt(qs("#encryptPlainText").value, qs("#encryptPassword").value);
  qs("#cryptoSalt").value = result.salt;
  qs("#cryptoIv").value = result.iv;
  qs("#cryptoCiphertext").value = result.ciphertext;
  qs("#cryptoPlaintextResult").value = "";
}

async function decryptLocalText() {
  if (!requireLogin()) return;
  const plaintext = await window.EncryptionTool.decrypt(
    qs("#cryptoCiphertext").value.trim(),
    qs("#encryptPassword").value,
    qs("#cryptoSalt").value.trim(),
    qs("#cryptoIv").value.trim()
  );
  qs("#cryptoPlaintextResult").value = plaintext;
}

async function createOrder() {
  if (!requireLogin()) return;
  const items = selectedItems();
  if (!items.length) return alert("Hãy chọn ít nhất một sản phẩm.");
  await api("/api/orders", {
    method: "POST",
    body: {
      buyer: {
        name: qs("#buyerName").value,
        email: qs("#buyerEmail").value,
        phone: qs("#buyerPhone").value,
        address: qs("#buyerAddress").value
      },
      items,
      promotionCodes: activePromos()
    }
  });
  await refreshState();
  showTab("orders");
}

function showTab(tabId) {
  if (!state.user && tabId !== "auth") tabId = "auth";
  if (state.user && canManage() && ["shop", "keys", "orders"].includes(tabId)) tabId = "admin";
  if (state.user && !canManage() && tabId === "admin") tabId = "shop";
  qsa(".tab").forEach(tab => tab.classList.toggle("active", tab.id === tabId));
  qsa("button[data-tab]").forEach(btn => btn.classList.toggle("active", btn.dataset.tab === tabId));
}

async function init() {
  state.catalog = await api("/api/catalog");
  renderProducts();
  await loadMe();
  await refreshState();
  qsa("button[data-tab]").forEach(btn => btn.addEventListener("click", () => showTab(btn.dataset.tab)));
  qsa(".promoCode").forEach(input => input.addEventListener("change", renderCartTotal));
  qs("#productSearch").addEventListener("input", event => {
    state.productSearch = event.target.value;
    renderProducts();
  });
  qsa("#brandFilters [data-brand]").forEach(button => button.addEventListener("click", () => {
    state.activeBrand = button.dataset.brand;
    qsa("#brandFilters [data-brand]").forEach(item => item.classList.toggle("active", item === button));
    renderProducts();
  }));
  qs("#loginBtn").addEventListener("click", () => login(qs("#loginEmail").value, qs("#loginPassword").value).catch(error => alert(error.message)));
  qs("#registerBtn").addEventListener("click", () => register().catch(error => alert(error.message)));
  qs("#logoutBtn").addEventListener("click", () => logout().catch(error => alert(error.message)));
  qs("#requestKeyBtn").addEventListener("click", () => requestKey().catch(error => alert(error.message)));
  qs("#generateKeyBtn").addEventListener("click", () => generateKeyPair().catch(error => alert(error.message)));
  qs("#downloadPrivateBtn").addEventListener("click", downloadPrivateKey);
  qs("#encryptBtn").addEventListener("click", () => encryptLocalText().catch(error => alert(error.message)));
  qs("#decryptBtn").addEventListener("click", () => decryptLocalText().catch(error => alert(error.message)));
  qs("#createOrderBtn").addEventListener("click", () => createOrder().catch(error => alert(error.message)));
  showTab(state.user ? (canManage() ? "admin" : "shop") : "auth");
}

init().catch(error => {
  console.error(error);
  alert(error.message);
});
