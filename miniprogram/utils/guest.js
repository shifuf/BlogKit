// 游客标识：用于动态/文章的游客点赞、评论归属去重（与前台 blog-blocks.ts 的 getGuestKey 同规则）。
// 规则：localStorage key 'bd-guest-key'，值为 'g_' + uuid去横线，截断 64 字符，跨会话持久。
const GUEST_KEY_STORAGE = 'bd-guest-key';

// 小程序无 crypto.randomUUID，用时间戳 + 随机数拼一个足够唯一的串。
function randomToken() {
  const rand = Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
  return `${Date.now().toString(36)}${rand}`;
}

function getGuestKey() {
  let key = '';
  try {
    key = wx.getStorageSync(GUEST_KEY_STORAGE) || '';
  } catch (error) {
    key = '';
  }
  if (key) {
    return key;
  }
  key = `g_${randomToken()}`.replace(/-/g, '').slice(0, 64);
  try {
    wx.setStorageSync(GUEST_KEY_STORAGE, key);
  } catch (error) {
    // 存储失败不阻塞，返回内存值即可（本次会话有效）。
  }
  return key;
}

module.exports = {
  getGuestKey
};
