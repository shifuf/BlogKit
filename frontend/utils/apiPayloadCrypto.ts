/**
 * 与后端 ApiPayloadCipher 对齐的 AES-GCM 密封。
 * 密钥：BLOG_API_PAYLOAD_SECRET（仅接口混淆，勿与 BLOG_DOWNLOAD_SECRET_KEY 相同）。
 * 兼容 NUXT_PUBLIC_API_PAYLOAD_SECRET 作为同值别名（构建/运行时注入）。
 */

// 与后端默认混淆密钥对齐；生产务必通过环境变量注入独立密钥。
const DEFAULT_SECRET = 'blog-api-payload-obfuscation-key-change-me';
const IV_LENGTH = 12;

function resolveSecret() {
  try {
    const config = useRuntimeConfig();
    const fromConfig = String((config.public as any)?.apiPayloadSecret || '').trim();
    if (fromConfig) return fromConfig;
  } catch {
    // outside nuxt context
  }
  if (typeof process !== 'undefined') {
    const fromEnv = String(
      process.env?.NUXT_PUBLIC_API_PAYLOAD_SECRET
      || process.env?.BLOG_API_PAYLOAD_SECRET
      || ''
    ).trim();
    if (fromEnv) return fromEnv;
  }
  return DEFAULT_SECRET;
}

async function deriveKey(secret: string): Promise<CryptoKey> {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) {
    throw new Error('当前页面非安全上下文（需 HTTPS 或 localhost），无法解密加密响应');
  }
  const digest = await subtle.digest('SHA-256', new TextEncoder().encode(secret));
  return subtle.importKey('raw', digest, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt']);
}

function b64UrlToBytes(value: string): Uint8Array {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/');
  const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4));
  const binary = atob(padded + pad);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) out[i] = binary.charCodeAt(i);
  return out;
}

function bytesToB64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach(b => { binary += String.fromCharCode(b); });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

export async function openSealedPayload<T = any>(payload: any): Promise<T> {
  // 兼容未加密旧响应
  if (payload && typeof payload === 'object' && !payload.enc && !payload.payload) {
    return payload as T;
  }
  if (!payload || payload.enc !== 'aes-gcm' || !payload.payload) {
    throw new Error('加密响应格式无效');
  }
  const combined = b64UrlToBytes(String(payload.payload));
  if (combined.length <= IV_LENGTH) throw new Error('密文过短');
  const iv = combined.slice(0, IV_LENGTH);
  const data = combined.slice(IV_LENGTH);
  const key = await deriveKey(resolveSecret());
  const plainBuf = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, data);
  const text = new TextDecoder().decode(plainBuf);
  return JSON.parse(text) as T;
}

export async function sealPayload(value: unknown): Promise<{ enc: string; v: string; payload: string }> {
  const key = await deriveKey(resolveSecret());
  const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH));
  const encoded = new TextEncoder().encode(JSON.stringify(value ?? {}));
  const cipherBuf = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, encoded);
  const cipher = new Uint8Array(cipherBuf);
  const combined = new Uint8Array(iv.length + cipher.length);
  combined.set(iv, 0);
  combined.set(cipher, iv.length);
  return { enc: 'aes-gcm', v: '1', payload: bytesToB64Url(combined) };
}

/** 解包 ApiResponse：data 可能是密封体或明文 */
export async function unwrapApiData<T = any>(payload: any): Promise<T> {
  const data = payload?.data ?? payload;
  if (data && typeof data === 'object' && data.enc === 'aes-gcm') {
    return openSealedPayload<T>(data);
  }
  return data as T;
}
