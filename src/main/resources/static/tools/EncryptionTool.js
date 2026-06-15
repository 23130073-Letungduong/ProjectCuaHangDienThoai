class EncryptionTool {
  static encoder = new TextEncoder();
  static decoder = new TextDecoder();

  static arrayBufferToBase64(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = "";
    bytes.forEach(byte => {
      binary += String.fromCharCode(byte);
    });
    return btoa(binary);
  }

  static base64ToArrayBuffer(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  }

  static randomBase64(length) {
    const bytes = new Uint8Array(length);
    crypto.getRandomValues(bytes);
    return this.arrayBufferToBase64(bytes);
  }

  static async deriveKey(passphrase, saltBase64) {
    if (!passphrase || passphrase.length < 6) throw new Error("Mật khẩu mã hóa phải có ít nhất 6 ký tự.");
    const keyMaterial = await crypto.subtle.importKey(
      "raw",
      this.encoder.encode(passphrase),
      "PBKDF2",
      false,
      ["deriveKey"]
    );
    return crypto.subtle.deriveKey(
      {
        name: "PBKDF2",
        salt: this.base64ToArrayBuffer(saltBase64),
        iterations: 120000,
        hash: "SHA-256"
      },
      keyMaterial,
      { name: "AES-CBC", length: 256 },
      false,
      ["encrypt", "decrypt"]
    );
  }

  static async encrypt(plaintext, passphrase) {
    if (!plaintext || !plaintext.trim()) throw new Error("Nội dung mã hóa không được để trống.");
    const salt = this.randomBase64(16);
    const iv = this.randomBase64(16);
    const key = await this.deriveKey(passphrase, salt);
    const ciphertext = await crypto.subtle.encrypt(
      { name: "AES-CBC", iv: this.base64ToArrayBuffer(iv) },
      key,
      this.encoder.encode(plaintext)
    );
    return {
      algorithm: "AES-CBC/PKCS7",
      keyDerivation: "PBKDF2-SHA256",
      iterations: 120000,
      salt,
      iv,
      ciphertext: this.arrayBufferToBase64(ciphertext)
    };
  }

  static async decrypt(ciphertext, passphrase, salt, iv) {
    if (!ciphertext || !salt || !iv) throw new Error("Cần nhập đủ ciphertext, salt và IV.");
    const key = await this.deriveKey(passphrase, salt);
    const plaintext = await crypto.subtle.decrypt(
      { name: "AES-CBC", iv: this.base64ToArrayBuffer(iv) },
      key,
      this.base64ToArrayBuffer(ciphertext)
    );
    return this.decoder.decode(plaintext);
  }
}

window.EncryptionTool = EncryptionTool;
