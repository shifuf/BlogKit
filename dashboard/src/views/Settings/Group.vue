<template>
  <div class="settings-group">
    <section class="settings-panel">
      <header class="settings-panel__head">
        <div class="settings-panel__title-wrap">
          <h2 class="settings-panel__title">{{ title }}</h2>
          <p class="settings-panel__desc">直接填写配置值并保存，无需维护配置键。</p>
        </div>
        <div class="settings-panel__actions">
          <a-button @click="loadSettings">刷新</a-button>
          <a-button v-if="group === 'ai'" :loading="testingAi" @click="handleTestAi">连通测试</a-button>
          <a-button v-if="group === 'upload'" :loading="testingUpload" @click="handleTestUpload">连通测试</a-button>
          <a-button v-if="group === 'notification'" :loading="testingNotification" @click="handleTestNotification">发送测试通知</a-button>
          <a-button v-if="group === 'notification'" :loading="testingReplyEmail" @click="handleTestReplyEmail">测试评论回邮</a-button>
          <a-button type="primary" :loading="saving" @click="submitForm">保存设置</a-button>
        </div>
      </header>

      <a-alert
        v-if="group === 'music'"
        class="settings-tip"
        type="info"
        show-icon
        message="播放源自动选择，失败时自动切换"
        description="密钥保存后立即生效。保留星号表示不修改，清空后保存可停用该接口。ChKSz 的 QQ、网易云和酷狗共用一个密钥；搜索和解析有缓存，鉴权或额度错误会暂停调用。"
      />
      <a-alert v-if="group === 'music' && musicErrors" class="settings-tip" type="warning" show-icon :message="musicErrors" />
      <a-alert
        v-if="group === 'blog'"
        class="settings-tip"
        type="warning"
        show-icon
        message="小程序审核提示"
        description="个人主体小程序建议在提交审核前关闭“小程序评论展示兼容开关”。文章是否允许评论请在文章编辑页单独控制。"
      />
      <a-alert
        v-if="group === 'notification'"
        class="settings-tip"
        type="info"
        show-icon
        message="模板选择依赖消息平台开放接口 GET /api/templates"
        description="若线上仍返回 404，说明消息平台尚未部署该接口；本地代码已完善。部署后可在此选择模板 code 与名称。"
      />

      <a-skeleton v-if="loading" active :paragraph="{ rows: 8 }" />
      <a-form v-else layout="vertical" class="settings-form">
        <template v-for="section in fieldSections" :key="section.title || 'default'">
          <div v-if="section.title" class="settings-section-title">{{ section.title }}</div>
          <a-row :gutter="[18, 4]" class="settings-section-row">
            <a-col v-for="field in section.fields" :key="field.key" :xs="24" :lg="field.full ? 24 : 12">
            <a-form-item :label="field.label" :extra="field.extra" class="settings-form-item">
              <a-switch
                v-if="field.type === 'switch'"
                v-model:checked="formValues[field.key]"
                checked-children="开启"
                un-checked-children="关闭"
              />
              <a-input-number
                v-else-if="field.type === 'number'"
                v-model:value="formValues[field.key]"
                :min="field.min || 0"
                :max="field.max || 9999"
                style="width: 100%"
              />
              <a-select v-else-if="field.type === 'select'" v-model:value="formValues[field.key]">
                <a-select-option v-for="option in field.options" :key="option.value" :value="option.value">
                  {{ option.label }}
                </a-select-option>
              </a-select>
              <a-select
                v-else-if="field.type === 'category-multi'"
                v-model:value="formValues[field.key]"
                mode="multiple"
                allow-clear
                :loading="categoryLoading"
                placeholder="选择首页展示的分类栏目（可多选，按选择顺序）"
                option-label-prop="label"
                style="width: 100%"
              >
                <a-select-option
                  v-for="option in categoryOptions"
                  :key="option.slug"
                  :value="option.slug"
                  :label="option.name"
                >
                  {{ option.name }}
                  <span class="category-option-slug">{{ option.slug }}</span>
                </a-select-option>
              </a-select>
              <a-select
                v-else-if="field.type === 'article-picker'"
                v-model:value="formValues[field.key]"
                show-search
                allow-clear
                :loading="articleLoading"
                :filter-option="false"
                :not-found-content="articleLoading ? undefined : '输入关键字搜索文章'"
                placeholder="输入标题关键字搜索并选择文章"
                option-label-prop="label"
                style="width: 100%"
                @search="searchArticleOptions"
                @focus="searchArticleOptions('')"
              >
                <a-select-option
                  v-for="option in articleOptions"
                  :key="option.id"
                  :value="String(option.id)"
                  :label="option.title"
                >
                  {{ option.title }}
                  <span class="category-option-slug">ID:{{ option.id }}</span>
                </a-select-option>
              </a-select>
              <IconPicker
                v-else-if="field.type === 'icon'"
                v-model="formValues[field.key]"
                :placeholder="field.placeholder || '选择图标'"
              />
              <div v-else-if="field.type === 'image'" class="settings-image-field">
                <a-input v-model:value="formValues[field.key]" :maxlength="field.maxlength" placeholder="填写图片 URL 或 /uploads 路径" />
                <a-space wrap>
                  <a-upload :show-upload-list="false" :custom-request="opts => uploadSettingImage(field.key, opts)">
                    <a-button size="small" :loading="uploadingImageKey === field.key">{{ uploadingImageKey === field.key ? '上传中…' : '上传图片' }}</a-button>
                  </a-upload>
                  <a-button size="small" @click="openImagePicker(field.key)">从图库选择</a-button>
                </a-space>
                <img v-if="formValues[field.key]" :src="formValues[field.key]" :alt="field.label" />
              </div>
              <BannerListEditor
                v-else-if="field.type === 'banner-list'"
                v-model="formValues[field.key]"
              />
              <div v-else-if="field.type === 'ai-model'" class="settings-ai-model-field">
                <a-select
                  v-model:value="formValues[field.key]"
                  show-search
                  allow-clear
                  :options="aiModelOptions"
                  :filter-option="filterAiModelOption"
                  placeholder="点击「获取模型」拉取列表，或直接输入模型名"
                  style="flex: 1"
                />
                <a-button :loading="loadingAiModels" @click="handleFetchAiModels">获取模型</a-button>
              </div>
              <div v-else-if="field.type === 'email-template'" class="settings-template-field">
                <a-input
                  v-model:value="formValues[field.key]"
                  :placeholder="field.defaultValue || '选择邮件模板 code'"
                  allow-clear
                />
                <a-button @click="openTemplatePicker(field)">选择模板</a-button>
                <div v-if="field.variableHints?.length" class="settings-template-vars">
                  <div class="settings-template-vars__title">可用变量</div>
                  <div class="settings-template-vars__list">
                    <span v-for="hint in field.variableHints" :key="hint.key" class="settings-template-vars__item">
                      <code>{{ hint.key }}</code>
                      <span>{{ hint.label }}</span>
                    </span>
                  </div>
                </div>
              </div>
              <a-input-password
                v-else-if="field.type === 'password'"
                v-model:value="formValues[field.key]"
                :name="`setting-${field.key}`"
                autocomplete="new-password"
                placeholder="留空表示不配置"
              />
              <a-textarea
                v-else-if="field.type === 'textarea'"
                v-model:value="formValues[field.key]"
                :rows="field.rows || 4"
                :maxlength="field.maxlength"
                show-count
              />
              <a-input v-else v-model:value="formValues[field.key]" :maxlength="field.maxlength" />
            </a-form-item>
            </a-col>
          </a-row>
        </template>
      </a-form>
    </section>

    <AttachmentPicker v-model:open="imagePickerOpen" :multiple="false" @pick="applyPickedImage" />

    <a-modal
      v-model:open="templatePickerOpen"
      title="选择邮件模板"
      width="720px"
      :footer="null"
      destroy-on-close
      @cancel="closeTemplatePicker"
    >
      <div class="template-picker">
        <div class="template-picker__toolbar">
          <a-input
            v-model:value="templateSearch"
            allow-clear
            placeholder="搜索模板名称 / code"
            style="max-width: 280px"
          />
          <a-button :loading="loadingEmailTemplates" @click="loadEmailTemplates">刷新列表</a-button>
        </div>
        <a-alert
          type="info"
          show-icon
          style="margin-bottom: 12px"
          message="列表来自消息通知平台（项目模板 + 全局启用模板）。选中后会写入模板 code。"
        />
        <a-spin :spinning="loadingEmailTemplates">
          <a-empty v-if="!filteredEmailTemplates.length" description="暂无模板，请先在消息平台创建或点刷新" />
          <div v-else class="template-picker__list">
            <button
              v-for="item in filteredEmailTemplates"
              :key="`${item.projectId || 'global'}-${item.code}`"
              type="button"
              class="template-picker__item"
              :class="{ active: formValues[templatePickerField?.key] === item.code }"
              @click="selectEmailTemplate(item)"
            >
              <div class="template-picker__item-head">
                <strong>{{ item.name || item.code }}</strong>
                <code>{{ item.code }}</code>
              </div>
              <div v-if="item.subject" class="template-picker__item-subject">主题：{{ item.subject }}</div>
              <div v-if="item.variables?.length" class="template-picker__item-vars">
                变量：
                <span v-for="v in item.variables" :key="v" class="template-picker__chip">
                  {{ v }}{{ VARIABLE_LABELS[v] ? `（${VARIABLE_LABELS[v]}）` : '' }}
                </span>
              </div>
            </button>
          </div>
        </a-spin>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { listSettings, saveSettingGroup, musicProviderStatus, testAiConnection, testUploadConnection, testNotificationPlatform, testReplyEmail, listAiModels, listEmailTemplates } from '@/api/Settings';
import { uploadImageWithCrop } from '@/utils/imageCrop';
import { getCategoryList } from '@/api/Category';
import { getArticleList, getArticleDetail } from '@/api/Article';
import IconPicker from '@/components/IconPicker.vue';
import AttachmentPicker from '@/components/AttachmentPicker.vue';
import BannerListEditor from './components/BannerListEditor.vue';

// 邮件模板变量中文备注（与消息平台 comment_reply / new_article / friend_link_review 对齐）
const VARIABLE_LABELS = {
  siteName: '站点名称',
  appName: '应用名称（同站点名）',
  articleTitle: '文章标题',
  title: '标题（兼容字段）',
  actorName: '回复者昵称',
  parentSnippet: '原评论纯文本摘要',
  parentSnippetHtml: '原评论 HTML 摘要',
  replySnippet: '回复纯文本摘要',
  replySnippetHtml: '回复 HTML 摘要',
  viewUrl: '查看链接',
  url: '链接（兼容 viewUrl）',
  actionUrl: '操作按钮链接',
  body: '正文说明',
  unsubscribeUrl: '退订链接',
  friendName: '友链站点名称',
  friendUrl: '友链站点地址',
  approved: '是否通过（true/false）',
  statusText: '审核状态文案'
};
const props = defineProps({
  group: {
    type: String,
    required: true
  },
  title: {
    type: String,
    required: true
  }
});
const loading = ref(false);
const musicErrors = ref('');
const saving = ref(false);
const uploadingImageKey = ref('');
const imagePickerOpen = ref(false);
const imagePickerKey = ref('');
const testingAi = ref(false);
const testingUpload = ref(false);
const testingNotification = ref(false);
const testingReplyEmail = ref(false);
const aiModelOptions = ref([]);
const loadingAiModels = ref(false);
const emailTemplates = ref([]);
const loadingEmailTemplates = ref(false);
const templatePickerOpen = ref(false);
const templatePickerField = ref(null);
const templateSearch = ref('');
const formValues = reactive({});
const categoryOptions = ref([]);
const categoryLoading = ref(false);
const articleOptions = ref([]);
const articleLoading = ref(false);
let articleSearchTimer = null;
const fieldGroups = {
  music: [{
    key: 'cyQqKey', label: '迟言 QQ 音乐 API Key', type: 'password', defaultValue: '',
    extra: '用于迟言 QQ 音乐接口。'
  }, {
    key: 'cyNeteaseKey', label: '迟言网易云 API Key', type: 'password', defaultValue: '',
    extra: '可与迟言 QQ 使用相同密钥，也可分别设置。'
  }, {
    key: 'chkszKey', label: 'ChKSz 统一 API Key', type: 'password', defaultValue: '', full: true,
    extra: 'QQ、网易云、酷狗共用；请注意账户的每日额度和每分钟调用限制。'
  }],
  basic: [{
    key: 'siteName',
    label: '站点名称',
    type: 'text',
    defaultValue: '我的博客',
    maxlength: 100
  }, {
    key: 'siteSubtitle',
    label: '站点副标题',
    type: 'text',
    defaultValue: '记录技术、项目与生活',
    maxlength: 120
  }, {
    key: 'siteDescription',
    label: '站点描述',
    type: 'textarea',
    defaultValue: '',
    full: true,
    rows: 3,
    maxlength: 500
  }, {
    key: 'siteKeywords',
    label: '站点关键词',
    type: 'text',
    defaultValue: '',
    extra: '多个关键词用英文逗号分隔。'
  }, {
    key: 'icp',
    label: '备案号',
    type: 'text',
    defaultValue: ''
  }, {
    key: 'footerText',
    label: '底部文案',
    type: 'textarea',
    defaultValue: '',
    full: true,
    rows: 3,
    maxlength: 300
  }, {
    key: 'footerInfo',
    label: '底部富文本信息',
    type: 'textarea',
    defaultValue: '',
    full: true,
    rows: 6,
    maxlength: 2000,
    extra: '支持 HTML，会优先于“底部文案”展示。'
  }, {
    key: 'announcementEnabled',
    label: '开启站点公告条',
    type: 'switch',
    defaultValue: false,
    extra: '开启后前台顶部展示公告条。'
  }, {
    key: 'announcementContent',
    label: '公告内容',
    type: 'text',
    defaultValue: '',
    full: true,
    maxlength: 200,
    extra: '简短公告文案，建议 80 字内。'
  }, {
    key: 'announcementLinkUrl',
    label: '公告链接',
    type: 'text',
    defaultValue: '',
    full: true,
    extra: '可选。填写后点击公告可跳转。'
  }],
  blog: [{
    key: 'allowRegister',
    label: '开放用户注册',
    type: 'switch',
    defaultValue: true,
    extra: '关闭后前台注册入口不可用，后端也会拒绝注册请求。'
  }, {
    key: 'commentsEnabled',
    label: '小程序评论展示兼容开关',
    type: 'switch',
    defaultValue: false,
    extra: '关闭后小程序会隐藏评论入口、评论列表和评论统计；文章是否允许评论请在文章编辑页单独控制。'
  }, {
    key: 'commentModerationEnabled',
    label: '开启评论审核',
    type: 'switch',
    defaultValue: false,
    extra: '开启后新评论先进入待审核，管理员通过后前台才展示。'
  }, {
    key: 'blockedCommentWords',
    label: '评论屏蔽词',
    type: 'textarea',
    defaultValue: '',
    full: true,
    rows: 4,
    maxlength: 1000,
    extra: '多个词用逗号或换行分隔；命中后评论进入待审核。'
  }, {
    key: 'defaultArticleStatus',
    label: '新文章默认状态',
    type: 'select',
    defaultValue: 'PUBLISHED',
    options: [{
      label: '已发布',
      value: 'PUBLISHED'
    }, {
      label: '草稿',
      value: 'DRAFT'
    }, {
      label: '待审核',
      value: 'PENDING_REVIEW'
    }]
  }, {
    key: 'articlePageSize',
    label: '文章默认分页大小',
    type: 'number',
    defaultValue: 10,
    min: 1,
    max: 50
  }],
  home: [{
    key: 'heroEnabled',
    label: '显示主 Banner',
    type: 'switch',
    defaultValue: true,
    section: '主 Banner',
    extra: '关闭后首页不展示顶部 Banner。'
  }, {
    key: 'heroSlides',
    label: '主 Banner',
    type: 'banner-list',
    defaultValue: '',
    full: true,
    section: '主 Banner',
    extra: '只加 1 张不显示左右切换；多张自动轮播并显示箭头。每张可单独设链接与签名；指定文章且签名标题留空时默认用文章标题。'
  }, {
    key: 'articleListEnabled',
    label: '显示首页文章板块',
    type: 'switch',
    defaultValue: true,
    section: '文章列表',
    extra: '关闭后首页不展示文章栏目与列表（Banner、动态入口等不受影响）。'
  }, {
    key: 'tabLatestEnabled',
    label: '显示「最新」栏目',
    type: 'switch',
    defaultValue: true,
    section: '文章列表',
    extra: '关闭后首页文章列表将隐藏「最新文章」Tab。'
  }, {
    key: 'tabHotEnabled',
    label: '显示「最热」栏目',
    type: 'switch',
    defaultValue: false,
    section: '文章列表',
    extra: '开启后首页文章列表会增加「最热」Tab，按访问量排序。'
  }, {
    key: 'tabCategorySlugs',
    label: '首页文章板块（栏目）',
    type: 'category-multi',
    defaultValue: '',
    full: true,
    section: '文章列表',
    extra: '选择首页除「最新/最热」外要展示的分类栏目，可多选并按选择顺序排列；留空则自动取前两个导航分类。'
  }, {
    key: 'weatherEnabled',
    label: '显示实时天气',
    type: 'switch',
    defaultValue: false,
    section: '侧栏组件',
    extra: '开启后在文章页/移动端抽屉展示心知天气挂件；默认关闭。'
  }],
  notification: [{
    key: 'platformEnabled',
    label: '启用统一消息通知',
    type: 'switch',
    defaultValue: false,
    extra: '开启后，定时发布失败、评论审核等通知通过 POST /api/webhook + Bearer 交给消息通知平台；渠道与故障切换由平台项目统一配置。'
  }, {
    key: 'commentNotifyEnabled',
    label: '评论审核通知',
    type: 'switch',
    defaultValue: false,
    extra: '评论进入审核队列时通知管理员（走平台 webhook 策略）。'
  }, {
    key: 'commentReplyEmailEnabled',
    label: '评论回复邮件通知',
    type: 'switch',
    defaultValue: false,
    extra: '开启后，前台评论可勾选「有回复时通知我」；收到回复时通过消息平台 POST /api/email 发到用户邮箱。需平台已绑定并启用 SMTP。'
  }, {
    key: 'commentEmailMxCheck',
    label: '邮箱域名 MX 预检',
    type: 'switch',
    defaultValue: true,
    extra: '勾选邮件通知时校验邮箱域名是否有 MX/A 记录，拦截明显假域名；DNS 超时放行。默认开启。'
  }, {
    key: 'platformBaseUrl',
    label: '消息通知平台地址',
    type: 'text',
    defaultValue: '',
    extra: '例如 https://notify.example.com（不要带 /api）。内网地址需加入后端出站白名单。'
  }, {
    key: 'platformApiKey',
    label: '项目 API Key',
    type: 'password',
    defaultValue: '',
    extra: '消息平台项目 API Key；请求头使用 Authorization: Bearer <Key>。评论回邮/新文订阅/友链审核邮件走 /api/email，管理员策略走 /api/webhook。'
  }, {
    key: 'templateCommentReply',
    label: '评论回复邮件模板',
    type: 'email-template',
    defaultValue: 'comment_reply',
    full: true,
    extra: '从消息通知平台选择模板。默认 code：comment_reply。',
    variableHints: [
      { key: 'siteName', label: '站点名称' },
      { key: 'articleTitle', label: '文章标题' },
      { key: 'actorName', label: '回复者昵称' },
      { key: 'parentSnippet', label: '原评论纯文本' },
      { key: 'parentSnippetHtml', label: '原评论 HTML' },
      { key: 'replySnippet', label: '回复纯文本' },
      { key: 'replySnippetHtml', label: '回复 HTML' },
      { key: 'viewUrl', label: '查看文章评论链接' },
      { key: 'unsubscribeUrl', label: '取消邮件通知链接' }
    ]
  }, {
    key: 'templateNewArticle',
    label: '新文订阅邮件模板',
    type: 'email-template',
    defaultValue: 'new_article',
    full: true,
    extra: '新文章发布时给订阅邮箱用的模板。默认 code：new_article。',
    variableHints: [
      { key: 'siteName', label: '站点名称' },
      { key: 'appName', label: '应用名称（同站点名）' },
      { key: 'articleTitle', label: '文章标题' },
      { key: 'title', label: '标题（兼容字段）' },
      { key: 'viewUrl', label: '文章链接' },
      { key: 'url', label: '链接（兼容 viewUrl）' },
      { key: 'body', label: '正文说明' },
      { key: 'actionUrl', label: '操作按钮链接' },
      { key: 'unsubscribeUrl', label: '退订链接' }
    ]
  }, {
    key: 'templateFriendLinkReview',
    label: '友链审核邮件模板',
    type: 'email-template',
    defaultValue: 'friend_link_review',
    full: true,
    extra: '友链申请审核结果邮件模板。默认 code：friend_link_review。',
    variableHints: [
      { key: 'siteName', label: '站点名称' },
      { key: 'friendName', label: '友链站点名称' },
      { key: 'friendUrl', label: '友链站点地址' },
      { key: 'approved', label: '是否通过（true/false）' },
      { key: 'statusText', label: '审核状态文案' },
      { key: 'viewUrl', label: '友链页链接' },
      { key: 'title', label: '邮件标题文案' },
      { key: 'body', label: '正文说明' },
      { key: 'actionUrl', label: '操作按钮链接' }
    ]
  }],
  ai: [{
    key: 'provider',
    label: 'AI 服务商',
    type: 'text',
    defaultValue: '',
    extra: '例如 OpenAI、DeepSeek、通义千问。'
  }, {
    key: 'model',
    label: 'AI 模型',
    type: 'ai-model',
    defaultValue: '',
    extra: '填好接口地址与密钥后点「获取模型」拉取可选模型，也可直接输入模型名。'
  }, {
    key: 'baseUrl',
    label: '接口地址',
    type: 'text',
    defaultValue: ''
  }, {
    key: 'apiKey',
    label: '接口密钥',
    type: 'password',
    defaultValue: ''
  }],
  oauth: [{
    key: 'enabled',
    label: '启用 OAuth 登录',
    type: 'switch',
    defaultValue: false
  }, {
    key: 'baseUrl',
    label: '认证中心地址',
    type: 'text',
    defaultValue: '',
    extra: '统一登录认证中心地址。'
  }, {
    key: 'clientId',
    label: 'Client ID',
    type: 'text',
    defaultValue: ''
  }, {
    key: 'clientSecret',
    label: 'Client Secret',
    type: 'password',
    defaultValue: ''
  }, {
    key: 'redirectUri',
    label: '固定回调地址',
    type: 'text',
    defaultValue: '',
    full: true,
    extra: '必须与认证中心后台登记的回调地址完全一致，包括 http/https、域名、端口、路径和末尾斜杠。'
  }],
  upload: [{
    key: 'maxFileSizeMb',
    label: '上传大小限制 MB',
    type: 'number',
    defaultValue: 20,
    min: 1,
    max: 200
  }, {
    key: 'allowedImageTypes',
    label: '允许图片类型',
    type: 'text',
    defaultValue: 'jpg,jpeg,png,gif,webp,svg'
  }, {
    key: 'publicPath',
    label: '公开访问路径',
    type: 'text',
    defaultValue: '/uploads'
  }, {
    key: 'storageType',
    label: '存储类型',
    type: 'select',
    defaultValue: 'unified',
    options: [{
      label: '统一文件管理',
      value: 'unified'
    }],
    extra: '所有上传统一走统一文件管理，不再支持本地存储。'
  }, {
    key: 'fileManagerBaseUrl',
    label: '文件服务地址',
    type: 'text',
    full: true,
    defaultValue: '',
    extra: '统一文件管理服务地址，如 https://files.example.com（必填，否则无法上传）。'
  }, {
    key: 'fileManagerAppId',
    label: '应用 ID（AppId）',
    type: 'text',
    defaultValue: ''
  }, {
    key: 'fileManagerAppSecret',
    label: '应用密钥（AppSecret）',
    type: 'password',
    defaultValue: '',
    extra: '留空表示不修改；保存后以密文存储。'
  }, {
    key: 'fileManagerApiPrefix',
    label: '接口前缀',
    type: 'text',
    defaultValue: '/api'
  }, {
    key: 'fileManagerFolderPath',
    label: '归档目录',
    type: 'text',
    defaultValue: 'blog-system'
  }, {
    key: 'fileManagerDuplicateStrategy',
    label: '同名文件策略',
    type: 'select',
    defaultValue: 'reuse',
    options: [{
      label: '复用已存在文件（reuse）',
      value: 'reuse'
    }, {
      label: '每次新建（rename）',
      value: 'rename'
    }]
  }, {
    key: 'fileManagerTimeoutSeconds',
    label: '请求超时（秒）',
    type: 'number',
    defaultValue: 45,
    min: 5,
    max: 300
  }]
};
const group = computed(() => props.group);
const fields = computed(() => fieldGroups[group.value] || []);
// 按 showWhen 条件过滤出当前应展示的字段（如指定文章/自定义链接只在对应链接方式下显示）。
const visibleFields = computed(() => fields.value.filter(field => typeof field.showWhen !== 'function' || field.showWhen(formValues)));
// 按 section 分组渲染（首页配置等分区标题）；无 section 的字段归入默认区。
const fieldSections = computed(() => {
  const map = new Map();
  for (const field of visibleFields.value) {
    const title = field.section || '';
    if (!map.has(title)) map.set(title, []);
    map.get(title).push(field);
  }
  return Array.from(map.entries()).map(([title, sectionFields]) => ({ title, fields: sectionFields }));
});
const parseValue = (field, rawValue) => {
  const value = rawValue ?? field.defaultValue;
  if (field.type === 'switch') return value === true || value === 'true';
  if (field.type === 'number') return Number(value || field.defaultValue || 0);
  // 逗号分隔字符串 → 数组（首页栏目多选）
  if (field.type === 'category-multi' || field.type === 'channel-multi') {
    return String(value || '').split(',').map(slug => slug.trim()).filter(Boolean);
  }
  return value ?? '';
};
const serializeValue = field => {
  const value = formValues[field.key];
  if (field.type === 'switch') return String(Boolean(value));
  if (field.type === 'number') return String(Number(value || 0));
  // 数组 → 逗号分隔字符串
  if (field.type === 'category-multi' || field.type === 'channel-multi') {
    return (Array.isArray(value) ? value : []).filter(Boolean).join(',');
  }
  return value || '';
};
const loadCategoryOptions = async () => {
  if (categoryOptions.value.length || categoryLoading.value) return;
  categoryLoading.value = true;
  try {
    const rows = await getCategoryList();
    const list = Array.isArray(rows) ? rows : rows?.data || [];
    categoryOptions.value = list.map(item => ({ slug: item.slug, name: item.name }));
  } catch {
    categoryOptions.value = [];
  } finally {
    categoryLoading.value = false;
  }
};
// 远程搜索文章作为「指定文章」选项；防抖 300ms，避免每次输入都请求。
const fetchArticleOptions = async keyword => {
  articleLoading.value = true;
  try {
    const page = await getArticleList({ page: 0, size: 20, keyword: keyword || '', status: 'PUBLISHED' });
    const items = page?.items || page?.data?.items || [];
    articleOptions.value = items.map(item => ({ id: item.id, title: item.title }));
  } catch {
    articleOptions.value = [];
  } finally {
    articleLoading.value = false;
  }
};
const searchArticleOptions = keyword => {
  if (articleSearchTimer) clearTimeout(articleSearchTimer);
  articleSearchTimer = setTimeout(() => fetchArticleOptions(keyword), 300);
};
// 回填已选文章标题：配置里存的是 id，进入页面需补一条选项才能显示标题而非裸 id。
const ensureSelectedArticleOption = async id => {
  const articleId = String(id || '').trim();
  if (!articleId) return;
  if (articleOptions.value.some(item => String(item.id) === articleId)) return;
  try {
    const detail = await getArticleDetail(articleId);
    const data = detail?.data || detail;
    if (data?.id) {
      articleOptions.value = [{ id: data.id, title: data.title }, ...articleOptions.value];
    }
  } catch {
    // 文章可能已删除，保持裸 id 显示即可。
  }
};
const resetFormValues = () => {
  Object.keys(formValues).forEach(key => delete formValues[key]);
  fields.value.forEach(field => {
    formValues[field.key] = parseValue(field);
  });
};
const loadSettings = async () => {
  loading.value = true;
  try {
    // 含「首页栏目多选」字段时，预加载分类列表供选择。
    if (fields.value.some(field => field.type === 'category-multi')) {
      loadCategoryOptions();
    }
    const rows = await listSettings(group.value);
    if (group.value === 'music') {
      const statuses = await musicProviderStatus();
      musicErrors.value = Object.values(statuses || {}).join('；');
    }
    const values = Object.fromEntries((rows || []).map(item => [item.settingKey, item.settingValue]));
    resetFormValues();
    fields.value.forEach(field => {
      formValues[field.key] = parseValue(field, values[field.key]);
    });
    // 回填「指定文章」选项标题
    const articleField = fields.value.find(field => field.type === 'article-picker');
    if (articleField && formValues[articleField.key]) {
      ensureSelectedArticleOption(formValues[articleField.key]);
    }
  } finally {
    loading.value = false;
  }
};
const submitForm = async () => {
  saving.value = true;
  try {
    const payload = {};
    fields.value.forEach(field => {
      payload[field.key] = serializeValue(field);
    });
    await saveSettingGroup(group.value, payload);
    message.success('设置已保存');
    await loadSettings();
  } finally {
    saving.value = false;
  }
};
const buildPayload = () => {
  const payload = {};
  fields.value.forEach(field => {
    payload[field.key] = serializeValue(field);
  });
  return payload;
};
// 「AI 模型」下拉搜索：按 label 大小写不敏感匹配。
const filterAiModelOption = (input, option) => String(option?.value || '').toLowerCase().includes(String(input || '').toLowerCase());

const filteredEmailTemplates = computed(() => {
  const q = String(templateSearch.value || '').trim().toLowerCase();
  const rows = Array.isArray(emailTemplates.value) ? emailTemplates.value : [];
  if (!q) return rows;
  return rows.filter(item => {
    const hay = `${item.code || ''} ${item.name || ''} ${item.subject || ''}`.toLowerCase();
    return hay.includes(q);
  });
});

const loadEmailTemplates = async () => {
  loadingEmailTemplates.value = true;
  try {
    const rows = await listEmailTemplates();
    emailTemplates.value = Array.isArray(rows) ? rows : (rows?.data || []);
    if (!emailTemplates.value.length) {
      message.warning('未拉取到模板，请确认消息平台地址/API Key，并在平台中创建或 seed 默认模板');
    }
  } catch {
    emailTemplates.value = [];
  } finally {
    loadingEmailTemplates.value = false;
  }
};

const openTemplatePicker = async field => {
  templatePickerField.value = field;
  templateSearch.value = '';
  templatePickerOpen.value = true;
  if (!emailTemplates.value.length) {
    await loadEmailTemplates();
  }
};

const closeTemplatePicker = () => {
  templatePickerOpen.value = false;
  templatePickerField.value = null;
};

const selectEmailTemplate = item => {
  if (!templatePickerField.value?.key || !item?.code) return;
  formValues[templatePickerField.value.key] = item.code;
  message.success(`已选择模板：${item.name || item.code}`);
  closeTemplatePicker();
};

// 获取 AI 服务商可用模型列表（OpenAI 兼容 GET /models），填充「AI 模型」下拉。
const handleFetchAiModels = async () => {
  loadingAiModels.value = true;
  try {
    const list = await listAiModels(buildPayload());
    const models = Array.isArray(list) ? list : [];
    aiModelOptions.value = models.map(id => ({ label: id, value: id }));
    if (!aiModelOptions.value.length) {
      message.warning('未获取到可用模型，请确认接口地址与密钥');
    } else {
      message.success(`已获取 ${aiModelOptions.value.length} 个模型`);
    }
  } catch {
    // 失败提示已由 request 拦截器统一弹出，这里不再重复 message.error。
  } finally {
    loadingAiModels.value = false;
  }
};
const handleTestAi = async () => {
  testingAi.value = true;
  try {
    const result = await testAiConnection(buildPayload());
    const suffix = result?.latencyMs ? `（${result.latencyMs}ms）` : '';
    if (result?.ok) {
      message.success(`${result.message || '连接成功'}${suffix}`);
    } else {
      message.error(`${result?.message || '连接失败'}${suffix}`);
    }
  } finally {
    testingAi.value = false;
  }
};
const handleTestUpload = async () => {
  testingUpload.value = true;
  try {
    const result = await testUploadConnection();
    if (result?.ok) {
      message.success(result.message || '统一文件管理连接成功');
    } else {
      message.error(result?.message || '统一文件管理连接失败');
    }
  } finally {
    testingUpload.value = false;
  }
};
const handleTestNotification = async () => {
  testingNotification.value = true;
  try {
    const result = await testNotificationPlatform(buildPayload());
    if (result?.ok) {
      message.success(result.message || '测试通知已发送');
    } else {
      message.error(result?.message || '发送失败');
    }
  } finally {
    testingNotification.value = false;
  }
};
const handleTestReplyEmail = async () => {
  let to = '';
  try {
    to = window.prompt('请输入测试收件人邮箱', '') || '';
  } catch {
    to = '';
  }
  to = to.trim();
  if (!to) {
    message.warning('请填写测试收件人邮箱');
    return;
  }
  testingReplyEmail.value = true;
  try {
    const result = await testReplyEmail({ to });
    if (result?.ok) {
      message.success(result.message || '测试回邮已发送');
    } else {
      message.error(result?.message || '发送失败');
    }
  } finally {
    testingReplyEmail.value = false;
  }
};
const openImagePicker = key => {
  imagePickerKey.value = key;
  imagePickerOpen.value = true;
};
const applyPickedImage = urls => {
  const url = (urls || []).filter(Boolean)[0];
  if (!url || !imagePickerKey.value) return;
  formValues[imagePickerKey.value] = url;
  message.success('已选择图片');
};
const uploadSettingImage = async (key, { file, onSuccess, onError }) => {
  uploadingImageKey.value = key;
  try {
    const result = await uploadImageWithCrop(file, { publicAccess: true });
    if (!result) {
      onError?.(new Error('cancelled'));
      return;
    }
    if (!result?.url) {
      throw new Error('上传未返回地址');
    }
    formValues[key] = result.url;
    message.success('已上传');
    onSuccess?.(result);
  } catch (error) {
    if (error?.message !== 'cancelled') {
      message.error('上传失败');
    }
    onError?.(error);
  } finally {
    uploadingImageKey.value = '';
  }
};
watch(() => props.group, loadSettings);
onMounted(loadSettings);
</script>

<style lang="scss" scoped>
.settings-group {
  padding-top: 0;
}

.settings-panel {
  border: 1px solid #eef0f5;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.settings-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px 16px;
  border-bottom: 1px solid #f0f2f7;
  background: linear-gradient(180deg, #fbfcff 0%, #fff 100%);
}

.settings-panel__title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
}

.settings-panel__desc {
  margin: 0;
  font-size: 13px;
  color: #8b93a7;
}

.settings-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.settings-tip {
  margin: 16px 22px 0;
}

.settings-form {
  margin: 0;
  padding: 8px 22px 22px;
}

.settings-section-title {
  margin: 18px 0 10px;
  padding-left: 10px;
  border-left: 3px solid #2f54eb;
  font-size: 14px;
  font-weight: 700;
  color: #334155;
  line-height: 1.2;
}

.settings-section-row + .settings-section-title {
  margin-top: 22px;
}

.settings-form-item {
  margin-bottom: 14px;
}

.settings-form :deep(.ant-form-item-label > label) {
  font-weight: 600;
  color: #334155;
}

.settings-form :deep(.ant-form-item-extra) {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}

.category-option-slug {
  margin-left: 8px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.4);
}

.settings-channel-field {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.preview-card {
  display: flex;
  align-items: center;
  gap: 16px;

  img {
    width: 72px;
    height: 72px;
    border-radius: 50%;
    object-fit: cover;
    background: #f5f5f5;
  }

  h3 { margin: 0 0 6px; }
  p { margin: 0 0 6px; color: #606266; }
  small { color: #8c8c8c; }
}

.settings-image-field {
  display: grid;
  gap: 10px;
  img {
    width: 180px;
    max-width: 100%;
    height: 76px;
    border: 1px solid #f0f0f0;
    border-radius: 6px;
    object-fit: contain;
    background: #fafafa;
  }
}

.settings-ai-model-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.settings-template-field {
  display: grid;
  gap: 10px;
  grid-template-columns: 1fr auto;
  align-items: start;
}

.settings-template-vars {
  grid-column: 1 / -1;
  padding: 10px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;
}

.settings-template-vars__title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #595959;
}

.settings-template-vars__list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.settings-template-vars__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #f0f0f0;
  font-size: 12px;
  color: #595959;

  code {
    color: #1677ff;
    font-size: 12px;
  }
}

.template-picker__toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.template-picker__list {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow: auto;
}

.template-picker__item {
  width: 100%;
  text-align: left;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  background: #fff;
  padding: 12px 14px;
  cursor: pointer;
  transition: border-color .15s, box-shadow .15s;
}

.template-picker__item:hover,
.template-picker__item.active {
  border-color: #91caff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.08);
}

.template-picker__item-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 6px;

  strong { color: #141414; }
  code { color: #1677ff; font-size: 12px; }
}

.template-picker__item-subject {
  font-size: 13px;
  color: #595959;
  margin-bottom: 6px;
}

.template-picker__item-vars {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: #8c8c8c;
}

.template-picker__chip {
  display: inline-flex;
  padding: 1px 8px;
  border-radius: 999px;
  background: #f5f5f5;
  color: #595959;
}

@media (max-width: 768px) {
  .settings-panel__head {
    flex-direction: column;
    padding: 16px;
  }
  .settings-panel__actions {
    width: 100%;
    justify-content: flex-start;
  }
  .settings-form {
    padding: 4px 16px 16px;
  }
}
</style>
