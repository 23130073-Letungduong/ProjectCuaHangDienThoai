const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const ROOT = path.join(__dirname, "..");
const OUT_DIR = path.join(ROOT, "screenshots");
const BASE = process.env.BASE_URL || "http://localhost:3000";

async function main() {
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  async function fullPageShot(activePage, filename) {
    await activePage.evaluate(() => window.scrollTo(0, 0));
    await activePage.waitForTimeout(100);
    await activePage.screenshot({ path: path.join(OUT_DIR, filename), fullPage: true });
  }
  await page.goto(BASE, { waitUntil: "networkidle" });

  await page.waitForSelector("#auth.active");
  if (await page.locator('[data-role-tab="customer"]:visible').count()) {
    throw new Error("Khách chưa đăng nhập không được thấy chức năng khách hàng.");
  }
  if (await page.locator('[data-role-tab="manager"]:visible').count()) {
    throw new Error("Khách chưa đăng nhập không được thấy chức năng quản lý.");
  }
  await fullPageShot(page, "auth.png");

  await page.fill("#loginEmail", "user");
  await page.fill("#loginPassword", "123456");
  await page.click("#loginBtn");
  await page.waitForSelector("#shop.active .product-card");
  if (await page.locator("[data-auth-only]:visible").count()) {
    throw new Error("Nút Tài khoản phải được ẩn sau khi user đăng nhập.");
  }
  if ((await page.inputValue("#buyerName")) !== "user") {
    throw new Error("Họ tên người mua phải hiển thị là user.");
  }
  if (await page.locator('[data-role-tab="manager"]:visible').count()) {
    throw new Error("CUSTOMER không được thấy chức năng quản lý đơn.");
  }
  await fullPageShot(page, "shop.png");

  await page.click('button[data-tab="keys"]');
  await page.waitForSelector("#keys.active");
  await fullPageShot(page, "keys.png");

  await page.click("#logoutBtn");
  await page.fill("#loginEmail", "admin");
  await page.fill("#loginPassword", "123456");
  await page.click("#loginBtn");
  await page.waitForSelector("#adminOrders .item");
  if (await page.locator("[data-auth-only]:visible").count()) {
    throw new Error("Nút Tài khoản phải được ẩn sau khi admin đăng nhập.");
  }
  if (await page.locator('[data-role-tab="customer"]:visible').count()) {
    throw new Error("ADMIN không được thấy các chức năng mua hàng của khách.");
  }
  const adminText = await page.locator("#admin").innerText();
  if (!adminText.includes("NEEDS_MANUAL_REVIEW")) throw new Error("Admin screen must show manual-review sample order");
  if (!adminText.includes("Yêu cầu cấp khóa")) throw new Error("Admin screen must show key requests");
  if (!adminText.includes("Báo mất khóa") && !adminText.includes("LOST")) throw new Error("Admin screen must show key loss controls/status");
  await fullPageShot(page, "admin.png");

  const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  await mobile.goto(BASE, { waitUntil: "networkidle" });
  await mobile.fill("#loginEmail", "user");
  await mobile.fill("#loginPassword", "123456");
  await mobile.click("#loginBtn");
  await mobile.waitForSelector("#shop.active .product-card");
  const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  if (overflow) throw new Error("Mobile layout has horizontal overflow");
  await fullPageShot(mobile, "mobile-shop.png");

  await page.click("#logoutBtn");
  await page.fill("#registerName", "Kiểm thử cấp khóa");
  await page.fill("#registerEmail", "key-flow@example.com");
  await page.fill("#registerPassword", "123456");
  await page.fill("#registerPhone", "0909111222");
  await page.fill("#registerAddress", "123 Đường Kiểm Thử, TP.HCM");
  await page.click("#registerBtn");
  await page.waitForSelector("#keys.active");
  await page.click("#requestKeyBtn");
  await page.waitForFunction(() => document.querySelector("#keyRequestStatus")?.textContent.includes("đang chờ admin"));

  await page.click("#logoutBtn");
  await page.fill("#loginEmail", "admin");
  await page.fill("#loginPassword", "123456");
  await page.click("#loginBtn");
  await page.waitForSelector("[data-approve-key-request]");
  await page.click("[data-approve-key-request]");
  await page.waitForFunction(() => !document.querySelector("[data-approve-key-request]"));

  await page.click("#logoutBtn");
  await page.fill("#loginEmail", "key-flow@example.com");
  await page.fill("#loginPassword", "123456");
  await page.click("#loginBtn");
  await page.click('button[data-tab="keys"]');
  await page.waitForSelector("#keys.active");
  await page.waitForFunction(() => !document.querySelector("#generateKeyBtn").disabled);
  await page.click("#generateKeyBtn");
  await page.waitForFunction(() => document.querySelector("#privateKeyBox")?.value.includes("BEGIN PRIVATE KEY"));
  await page.waitForFunction(() => !document.querySelector("#keyList")?.textContent.includes("Chưa có public key nào."));
  const keyListText = await page.locator("#keyList").innerText();
  if (!keyListText.includes("CA:")) throw new Error("Key screen must show CA certificate metadata");
  await page.fill("#encryptPlainText", "Nội dung cần mã hóa.");
  await page.fill("#encryptPassword", "123456");
  await page.click("#encryptBtn");
  await page.waitForFunction(() => document.querySelector("#cryptoCiphertext")?.value.length > 20);
  await page.click("#decryptBtn");
  await page.waitForFunction(() => document.querySelector("#cryptoPlaintextResult")?.value.includes("Nội dung cần mã hóa."));
  const publicState = await page.evaluate(async () => {
    const response = await fetch("/api/state", {
      headers: { Authorization: `Bearer ${localStorage.getItem("authToken")}` }
    });
    return response.text();
  });
  if (publicState.includes("BEGIN PRIVATE KEY")) throw new Error("Private key must never appear in API state");
  await fullPageShot(page, "key-flow.png");
  await page.click("#logoutBtn");
  await page.waitForSelector("#auth.active");
  const privateKeyAfterLogout = await page.inputValue("#privateKeyBox");
  if (privateKeyAfterLogout) throw new Error("Private key must be cleared from the browser UI after logout");

  await browser.close();
  console.log(`Screenshots saved to ${OUT_DIR}`);
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
