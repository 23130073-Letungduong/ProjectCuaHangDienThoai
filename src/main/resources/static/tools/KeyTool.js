class KeyTool {
  static arrayBufferToPem(buffer, label) {
    const bytes = new Uint8Array(buffer);
    let binary = "";
    bytes.forEach(byte => {
      binary += String.fromCharCode(byte);
    });
    const base64 = btoa(binary).match(/.{1,64}/g).join("\n");
    return `-----BEGIN ${label}-----\n${base64}\n-----END ${label}-----`;
  }

  static async generate() {
    const keyPair = await crypto.subtle.generateKey(
      {
        name: "RSASSA-PKCS1-v1_5",
        modulusLength: 2048,
        publicExponent: new Uint8Array([1, 0, 1]),
        hash: "SHA-256"
      },
      true,
      ["sign", "verify"]
    );

    return {
      publicKeyPem: this.arrayBufferToPem(
        await crypto.subtle.exportKey("spki", keyPair.publicKey),
        "PUBLIC KEY"
      ),
      privateKeyPem: this.arrayBufferToPem(
        await crypto.subtle.exportKey("pkcs8", keyPair.privateKey),
        "PRIVATE KEY"
      )
    };
  }
}

window.KeyTool = KeyTool;
