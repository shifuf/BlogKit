<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FriendLinkSummary } from '~/types/blog'

const { fetchFriendLinks, applyFriendLink, fetchFriendLinkCaptcha } = useApiClient()
const { siteSettings, loadSiteSettings } = useSiteSettings()
await loadSiteSettings()

const { data: links, pending, refresh } = await useAsyncData('friends-page-links', async () => {
  try {
    const response = await fetchFriendLinks()
    if (!response.success) return [] as FriendLinkSummary[]
    return (response.data || []).filter(link => link.showInPage !== false)
  } catch {
    return [] as FriendLinkSummary[]
  }
}, { default: () => [] as FriendLinkSummary[] })

const initialOf = (name?: string | null) => {
  const text = String(name || '友').trim()
  return text ? text.charAt(0).toUpperCase() : '友'
}

const exchangeName = computed(() => siteSettings.value.friendExchangeName || siteSettings.value.siteName || '本站')
const exchangeBio = computed(() => siteSettings.value.friendExchangeBio || siteSettings.value.siteDescription || siteSettings.value.siteSubtitle || '')
const exchangeUrl = computed(() => siteSettings.value.friendExchangeUrl || (import.meta.client ? window.location.origin : ''))
const exchangeLogo = computed(() => siteSettings.value.friendExchangeLogo || siteSettings.value.profileAvatarUrl || '')
const pageIntro = computed(() => siteSettings.value.friendPageIntro || '志同道合的站点。欢迎互换友链，申请后将自动检测回链。')

const applyOpen = ref(false)
const submitting = ref(false)
const captchaLoading = ref(false)
const captchaImage = ref('')
const form = reactive({
  name: '',
  url: '',
  description: '',
  email: '',
  captchaId: '',
  captchaCode: ''
})

const loadCaptcha = async () => {
  captchaLoading.value = true
  try {
    const res = await fetchFriendLinkCaptcha()
    if (res.success && res.data) {
      form.captchaId = res.data.captchaId
      form.captchaCode = ''
      captchaImage.value = res.data.imageData || ''
    }
  } catch {
    ElMessage.error('验证码加载失败')
  } finally {
    captchaLoading.value = false
  }
}

const openApply = async () => {
  applyOpen.value = true
  if (!form.captchaId) await loadCaptcha()
}

const closeApply = () => {
  applyOpen.value = false
}

const submitApply = async () => {
  if (!form.name.trim() || !form.url.trim()) {
    ElMessage.warning('请填写站点名称和链接')
    return
  }
  if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  if (!form.captchaId || !form.captchaCode.trim()) {
    ElMessage.warning('请填写验证码')
    return
  }
  submitting.value = true
  try {
    const res = await applyFriendLink({
      name: form.name.trim(),
      url: form.url.trim(),
      description: form.description.trim(),
      email: form.email.trim() || undefined,
      captchaId: form.captchaId,
      captchaCode: form.captchaCode.trim()
    })
    if (res.success) {
      const auto = res.data?.status === 'APPROVED' || res.data?.backlinkFound
      ElMessage.success(res.message || (auto ? '检测到回链，已自动通过' : '申请已提交，等待审核'))
      form.name = ''
      form.url = ''
      form.description = ''
      form.email = ''
      form.captchaCode = ''
      applyOpen.value = false
      if (auto) await refresh()
      else await loadCaptcha()
    } else {
      ElMessage.error(res.message || '提交失败')
      await loadCaptcha()
    }
  } catch (error: any) {
    ElMessage.error(error?.data?.message || error?.message || '提交失败')
    await loadCaptcha()
  } finally {
    submitting.value = false
  }
}

const copyText = async (text: string, label: string) => {
  if (!import.meta.client || !text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制`)
  } catch {
    ElMessage.info(`请手动复制：${text}`)
  }
}

useHead({
  title: '友情链接',
  meta: [{ name: 'description', content: pageIntro.value }]
})
</script>

<template>
  <main class="friends-page page-container">
    <header class="friends-page__head">
      <div>
        <h1>友情链接</h1>
        <p>{{ pageIntro }}</p>
      </div>
      <button type="button" class="friends-page__apply" @click="openApply">申请友链</button>
    </header>

    <section class="friends-exchange">
      <h2>本站信息</h2>
      <p class="friends-exchange__tip">申请友链时请添加以下本站信息，系统会自动检测回链。</p>
      <div class="friends-exchange__card">
        <div class="friends-exchange__logo">
          <img v-if="exchangeLogo" :src="exchangeLogo" :alt="exchangeName" />
          <span v-else>{{ initialOf(exchangeName) }}</span>
        </div>
        <dl class="friends-exchange__meta">
          <div>
            <dt>名称</dt>
            <dd>
              <span>{{ exchangeName }}</span>
              <button type="button" @click="copyText(exchangeName, '名称')">复制</button>
            </dd>
          </div>
          <div>
            <dt>简介</dt>
            <dd>
              <span>{{ exchangeBio || '—' }}</span>
              <button v-if="exchangeBio" type="button" @click="copyText(exchangeBio, '简介')">复制</button>
            </dd>
          </div>
          <div>
            <dt>链接</dt>
            <dd>
              <a :href="exchangeUrl" target="_blank" rel="noopener noreferrer">{{ exchangeUrl || '—' }}</a>
              <button v-if="exchangeUrl" type="button" @click="copyText(exchangeUrl, '链接')">复制</button>
            </dd>
          </div>
          <div>
            <dt>图标</dt>
            <dd>
              <span class="friends-exchange__logo-url">{{ exchangeLogo || '—' }}</span>
              <button v-if="exchangeLogo" type="button" @click="copyText(exchangeLogo, '图标地址')">复制</button>
            </dd>
          </div>
        </dl>
      </div>
    </section>

    <el-skeleton v-if="pending" :rows="4" animated />
    <el-empty v-else-if="!links.length" description="暂无友链，欢迎申请" />
    <ul v-else class="friends-grid">
      <li v-for="link in links" :key="link.id" class="friend-card">
        <a class="friend-card__link" :href="link.url" target="_blank" rel="nofollow noopener" :title="link.description || link.name">
          <span class="friend-card__logo" :class="{ 'has-img': !!link.logoUrl }">
            <img v-if="link.logoUrl" :src="link.logoUrl" :alt="link.name" loading="lazy" />
            <span v-else>{{ initialOf(link.name) }}</span>
          </span>
          <span class="friend-card__meta">
            <strong>{{ link.name }}</strong>
            <small>{{ link.description || link.url }}</small>
          </span>
        </a>
      </li>
    </ul>

    <Teleport to="body">
      <div v-if="applyOpen" class="friends-modal" role="dialog" aria-modal="true" aria-label="申请友链" @click.self="closeApply">
        <div class="friends-modal__panel">
          <header class="friends-modal__head">
            <strong>申请友链</strong>
            <button type="button" class="friends-modal__close" aria-label="关闭" @click="closeApply">×</button>
          </header>
          <p class="friends-modal__desc">
            提交时会自动检查贵站是否已添加本站链接：已添加则自动通过，未添加则进入人工审核。
            邮箱选填；填写后将接收审核结果通知。
          </p>
          <form class="friends-modal__form" @submit.prevent="submitApply">
            <label>
              <span>站点名称 *</span>
              <input v-model="form.name" maxlength="80" required />
            </label>
            <label>
              <span>站点链接 *</span>
              <input v-model="form.url" type="url" placeholder="https://..." required />
            </label>
            <label>
              <span>简介</span>
              <textarea v-model="form.description" rows="3" maxlength="200" />
            </label>
            <label>
              <span>联系邮箱（选填，填写则发送审核结果）</span>
              <input v-model="form.email" type="email" placeholder="your@email.com" />
            </label>
            <label>
              <span>验证码 *</span>
              <div class="friends-modal__captcha">
                <input v-model="form.captchaCode" maxlength="8" placeholder="计算结果" required />
                <button type="button" class="friends-modal__captcha-btn" :disabled="captchaLoading" @click="loadCaptcha">
                  <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
                  <span v-else>{{ captchaLoading ? '…' : '刷新' }}</span>
                </button>
              </div>
            </label>
            <div class="friends-modal__actions">
              <button type="button" class="ghost" @click="closeApply">取消</button>
              <button type="submit" class="primary" :disabled="submitting">{{ submitting ? '提交中…' : '提交申请' }}</button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </main>
</template>

<style scoped>
.friends-page {
  max-width: 1080px;
  padding: 28px 16px 56px;
}
.friends-page__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}
.friends-page__head h1 {
  margin: 0 0 8px;
  font-size: 28px;
}
.friends-page__head p {
  margin: 0;
  color: var(--text-secondary);
  max-width: 640px;
  line-height: 1.6;
}
.friends-page__apply {
  flex: 0 0 auto;
  height: 38px;
  padding: 0 16px;
  border: 0;
  border-radius: 999px;
  background: var(--theme-color, #409eff);
  color: #fff;
  display: inline-flex;
  align-items: center;
  font-size: 14px;
  cursor: pointer;
}
.friends-exchange {
  margin-bottom: 28px;
  padding: 18px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: var(--bg-card);
}
.friends-exchange h2 {
  margin: 0 0 6px;
  font-size: 18px;
}
.friends-exchange__tip {
  margin: 0 0 14px;
  color: var(--text-secondary);
  font-size: 13px;
}
.friends-exchange__card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.friends-exchange__logo {
  width: 72px;
  height: 72px;
  border-radius: 16px;
  overflow: hidden;
  flex: 0 0 72px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  color: #64748b;
  background: #eef2f7;
}
.friends-exchange__logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.friends-exchange__meta {
  flex: 1;
  min-width: 0;
  margin: 0;
  display: grid;
  gap: 10px;
}
.friends-exchange__meta > div {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}
.friends-exchange__meta dt {
  color: var(--text-muted, #94a3b8);
  font-size: 13px;
}
.friends-exchange__meta dd {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
  word-break: break-all;
}
.friends-exchange__meta button {
  border: 0;
  background: color-mix(in srgb, var(--theme-color, #409eff) 12%, transparent);
  color: var(--theme-color, #409eff);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  cursor: pointer;
}
.friends-exchange__logo-url {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.friends-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}
.friend-card {
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: var(--bg-card);
  overflow: hidden;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease;
}
.friend-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--theme-color, #409eff) 40%, var(--border-color));
  box-shadow: 0 12px 28px rgba(15, 23, 42, .08);
}
.friend-card__link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  color: inherit;
  text-decoration: none;
}
.friend-card__logo {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 48px;
  overflow: hidden;
  font-weight: 700;
  font-size: 18px;
  color: #64748b;
  background: #eef2f7;
}
.friend-card__logo.has-img {
  background: #f1f5f9;
}
.friend-card__logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.friend-card__meta {
  min-width: 0;
  display: grid;
  gap: 4px;
}
.friend-card__meta strong {
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.friend-card__meta small {
  color: var(--text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.friends-modal {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(15, 23, 42, .45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}
.friends-modal__panel {
  width: min(520px, 100%);
  max-height: min(92vh, 760px);
  overflow: auto;
  background: var(--bg-card, #fff);
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, .28);
}
.friends-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 0;
}
.friends-modal__close {
  border: 0;
  background: transparent;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
  color: var(--text-secondary);
}
.friends-modal__desc {
  margin: 8px 18px 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
}
.friends-modal__form {
  display: grid;
  gap: 12px;
  padding: 16px 18px 18px;
}
.friends-modal__form label {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
.friends-modal__form input,
.friends-modal__form textarea {
  width: 100%;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--bg-body, #fff);
  color: inherit;
  font: inherit;
  box-sizing: border-box;
}
.friends-modal__captcha {
  display: grid;
  grid-template-columns: 1fr 120px;
  gap: 8px;
}
.friends-modal__captcha-btn {
  height: 42px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-soft, #f8fafc);
  overflow: hidden;
  padding: 0;
  cursor: pointer;
}
.friends-modal__captcha-btn img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.friends-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
.friends-modal__actions button {
  height: 38px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  cursor: pointer;
  font-weight: 600;
}
.friends-modal__actions .ghost {
  background: transparent;
  color: var(--text-secondary);
}
.friends-modal__actions .primary {
  border-color: transparent;
  background: var(--theme-color, #409eff);
  color: #fff;
}
.friends-modal__actions .primary:disabled {
  opacity: .65;
  cursor: not-allowed;
}
@media (max-width: 640px) {
  .friends-page__head {
    flex-direction: column;
    align-items: stretch;
  }
  .friends-page__apply {
    justify-content: center;
  }
  .friends-exchange__card {
    flex-direction: column;
  }
  .friends-exchange__meta > div {
    grid-template-columns: 1fr;
  }
  .friends-modal {
    align-items: center;
    justify-content: center;
    padding: 16px;
  }
  .friends-modal__panel {
    width: min(520px, 100%);
    max-height: min(92vh, 760px);
    border-radius: 16px;
  }
}
</style>
