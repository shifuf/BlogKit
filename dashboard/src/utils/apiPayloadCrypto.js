/**
 * 与后端 ApiPayloadCipher 对齐：SHA-256 派生密钥 + AES-GCM。
 * 密钥取 VITE_API_PAYLOAD_SECRET（构建时由 BLOG_API_PAYLOAD_SECRET 注入），勿与下载密钥相同。
 *
 * 注意：Web Crypto（crypto.subtle）仅在安全上下文可用（HTTPS 或 localhost）。
 * 通过 http://IP 访问后台时 subtle 为 undefined，调用方应对明文响应做兼容。
 */
const DEFAULT_SECRET = 'blog-api-payload-obfuscation-key-change-me';
const IV_LENGTH = 12;

function resolveSecret() {
  return String(import.meta.env.VITE_API_PAYLOAD_SECRET || '').trim() || DEFAULT_SECRET;
}

function getSubtle() {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) {
    throw new Error('当前页面非安全上下文（需 HTTPS 或 localhost），无法解密加密响应');
  }
  return subtle;
}

async function deriveKey(secret) {
  const subtle = getSubtle();
  const digest = await subtle.digest('SHA-256', new TextEncoder().encode(secret));
  return subtle.importKey('raw', digest, { name: 'AES-GCM' }, false, ['decrypt']);
}

function b64UrlToBytes(value) {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/');
  const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4));
  const binary = atob(padded + pad);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) out[i] = binary.charCodeAt(i);
  return out;
}

export async function openSealedPayload(payload) {
  if (payload && typeof payload === 'object' && !payload.enc && !payload.payload) {
    return payload;
  }
  if (!payload || payload.enc !== 'aes-gcm' || !payload.payload) {
    throw new Error('加密响应格式无效');
  }
  const combined = b64UrlToBytes(String(payload.payload));
  if (combined.length <= IV_LENGTH) throw new Error('密文过短');
  const iv = combined.slice(0, IV_LENGTH);
  const data = combined.slice(IV_LENGTH);
  const subtle = getSubtle();
  const key = await deriveKey(resolveSecret());
  const plainBuf = await subtle.decrypt({ name: 'AES-GCM', iv }, key, data);
  return JSON.parse(new TextDecoder().decode(plainBuf));
}
