import request from '@/utils/request';
import { openSealedPayload } from '@/utils/apiPayloadCrypto';

async function unwrapMusicPayload(data) {
  // 明文列表/对象直接返回；兼容旧版 AES 密封响应
  if (Array.isArray(data)) return data;
  if (data && typeof data === 'object' && data.enc === 'aes-gcm') {
    return openSealedPayload(data);
  }
  return data;
}

export const searchMusic = async keyword => {
  const data = await request.get('/music/search', { params: { keyword }, timeout: 60000 });
  return unwrapMusicPayload(data);
};

export const resolveMusic = async rid => {
  const data = await request.get('/music/resolve', { params: { rid }, timeout: 60000 });
  return unwrapMusicPayload(data);
};
