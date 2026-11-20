<template>
  <div class="article-editor-page">
    <a-card :bordered="false">
      <template #title>{{ isEdit ? '文章编辑' : '新建文章' }}</template>
      <template #extra>
        <a-space>
          <span v-if="draftSavedAt" class="draft-saved-hint">已自动保存 {{ draftSavedAt }}</span>
          <a-button v-if="isEdit" @click="openVersionDrawer">版本历史</a-button>
          <a-button :loading="qualityLoading" @click="runQualityCheck(true)">检查质量与链接</a-button>
          <a-button @click="goBack">返回列表</a-button>
          <a-button type="primary" :loading="saving" @click="submitArticle">保存文章</a-button>
        </a-space>
      </template>

      <a-form ref="formRef" layout="vertical" :model="form" :rules="rules">
        <div class="article-editor-grid">
          <section>
            <a-form-item label="标题" name="title">
              <a-input v-model:value="form.title" placeholder="请输入文章标题" />
            </a-form-item>
            <a-form-item label="摘要" name="summary">
              <template #label>
                <span>摘要</span>
                <a-button type="link" size="small" class="summary-extract-btn" @click="extractSummary">从正文提取</a-button>
              </template>
              <a-textarea v-model:value="form.summary" :rows="3" show-count :maxlength="500" placeholder="用于列表和 SEO 展示；可点「从正文提取」自动截取正文开头" />
            </a-form-item>
            <a-form-item name="aiSummary">
              <template #label>
                <span>AI 摘要</span>
                <a-button type="link" size="small" :loading="aiSummaryLoading" @click="generateAiSummary('generate')">AI 生成</a-button>
                <a-button type="link" size="small" :disabled="!form.aiSummary || aiSummaryLoading" @click="generateAiSummary('polish')">继续润色</a-button>
                <a-switch
                  v-model:checked="form.aiSummaryEnabled"
                  class="ai-summary-switch"
                  size="small"
                  checked-children="前台展示"
                  un-checked-children="不展示"
                />
              </template>
              <a-textarea
                v-model:value="form.aiSummary"
                :rows="4"
                show-count
                :maxlength="500"
                placeholder="点「AI 生成」按标题+正文生成摘要；可手动修改，或用「继续润色」让 AI 精炼。开启「前台展示」后在文章详情正文上方显示。"
              />
              <div class="form-help">AI 摘要独立于上方 SEO 摘要：仅在开启「前台展示」时于文章详情页正文上方以卡片展示。需先在「系统设置 - AI 服务」配置好接口。</div>
            </a-form-item>
            <a-form-item label="正文" name="content">
              <a-alert
                type="info"
                show-icon
                style="margin-bottom:12px"
                :message="bodyImagesPublic ? '公开 / 隐藏文章的新增正文图片作为公开素材上传，可独立访问；之后修改文章权限不会自动收回素材权限。' : '新增正文图片按私有素材上传，读者需通过文章授权预览；从图库插入的已有素材保留原权限。'"
              />
              <div ref="editorWrapRef" class="markdown-editor">
                <MdEditor
                  :key="editorModeKey"
                  ref="editorRef"
                  v-model="form.content"
                  :toolbars="toolbars"
                  :preview="editorPreviewEnabled"
                  :no-katex="true"
                  :no-mermaid="true"
                  :no-echarts="true"
                  :no-prettier="true"
                  code-theme="atom"
                  style="height: 620px"
                  @onUploadImg="uploadEditorImages"
                >
                  <template #defToolbars>
                    <DownloadToolbar ref="downloadToolbarRef" :article-id="articleId" @insert="insertBlock" @replace="replaceBlock" />
                    <SecretToolbar :users="userOptions" @select="insertSecret" />
                    <ButtonToolbar ref="buttonToolbarRef" :article-id="articleId" @insert="insertBlock" @replace="replaceBlock" />
                    <GalleryToolbar @pick="insertGallery" />
                    <MusicToolbar @insert="insertMusic" />
                    <LibraryToolbar @open="attachmentPickerOpen = true" />
                    <TimeToolbar @insert="insertBlock" />
                  </template>
                </MdEditor>
              </div>
            </a-form-item>
          </section>

          <aside>
            <a-form-item label="访问标识">
              <a-input v-model:value="form.slug" placeholder="留空按标题生成" />
            </a-form-item>
            <a-form-item label="分类" name="categoryId">
              <a-select v-model:value="form.categoryId" placeholder="请选择分类">
                <a-select-option v-for="item in categories" :key="item.id" :value="item.id">
                  {{ item.level === 2 ? `　${item.name}` : item.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="标签">
              <a-select
                v-model:value="form.tagIds"
                mode="multiple"
                show-search
                placeholder="搜索或选择标签"
                :filter-option="filterTagOption"
                @search="handleTagSearch"
              >
                <template #dropdownRender="{ menuNode }">
                  <v-nodes :vnodes="menuNode" />
                  <a-divider v-if="canCreateTag" class="tag-create-divider" />
                  <div v-if="canCreateTag" class="tag-create-option" @mousedown.prevent>
                    <a-button type="link" size="small" :loading="creatingTag" @click="handleCreateTag">
                      <template #icon><plus-outlined /></template>
                      新增“{{ trimmedTagKeyword }}”
                    </a-button>
                  </div>
                </template>
                <a-select-option v-for="item in tags" :key="item.id" :value="item.id" :label="item.name">
                  {{ item.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="发布状态" name="status">
              <a-select v-model:value="form.status">
                <a-select-option value="DRAFT">草稿</a-select-option>
                <a-select-option value="PENDING_REVIEW">待审核</a-select-option>
                <a-select-option value="SCHEDULED">定时发布</a-select-option>
                <a-select-option value="PUBLISHED">已发布</a-select-option>
                <a-select-option value="REJECTED">已驳回</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item v-if="form.status === 'SCHEDULED'" label="发布时间">
              <a-input v-model:value="form.scheduledPublishAt" type="datetime-local" />
              <div class="form-help">保存为定时发布时，发布时间必须晚于当前时间。</div>
            </a-form-item>
            <a-form-item label="所属系列">
              <a-select v-model:value="form.seriesId" allow-clear placeholder="不属于系列">
                <a-select-option v-for="item in seriesOptions" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item v-if="form.seriesId" label="系列内顺序"><a-input-number v-model:value="form.seriesOrder" :min="0" /></a-form-item>
            <a-form-item label="定时下架"><a-input v-model:value="form.scheduledUnpublishAt" type="datetime-local" allow-clear /></a-form-item>
            <a-alert v-if="serverQuality.length" type="warning" message="服务器检查结果" style="margin-bottom:16px"><template #description><ul><li v-for="issue in serverQuality" :key="issue.code">[{{ issue.severity }}] {{ issue.message }}</li></ul></template></a-alert>
            <a-alert v-if="qualityIssues.length" type="warning" message="发布前质量检查" style="margin-bottom:16px">
              <template #description><ul><li v-for="issue in qualityIssues" :key="issue">{{ issue }}</li></ul></template>
            </a-alert>
            <a-form-item label="评论">
              <a-switch
                v-model:checked="form.commentsEnabled"
                checked-children="允许"
                un-checked-children="关闭"
              />
              <div class="form-help">关闭后前台不再展示评论列表，后端拒绝新增评论和评论点赞。</div>
            </a-form-item>
            <a-form-item label="置顶">
              <a-switch
                v-model:checked="form.sticky"
                checked-children="置顶"
                un-checked-children="普通"
              />
              <div class="form-help">置顶文章会优先出现在公开文章列表和后台文章列表前面。</div>
            </a-form-item>
            <a-form-item v-if="form.sticky" label="置顶序号">
              <a-input-number v-model:value="form.stickyOrder" :min="0" :step="1" style="width: 100%" placeholder="数字越小越靠前" />
              <div class="form-help">多篇置顶时，数字越小越靠前；也可在文章列表直接拖拽排序。</div>
            </a-form-item>
            <a-form-item label="来源">
              <a-select v-model:value="form.sourceType">
                <a-select-option value="ORIGINAL">原创</a-select-option>
                <a-select-option value="REPRINT">转载</a-select-option>
              </a-select>
              <a-input
                v-if="form.sourceType === 'REPRINT'"
                v-model:value="form.sourceUrl"
                class="visibility-extra"
                placeholder="转载来源链接（选填）"
              />
              <div class="form-help">原创文章底部展示「CC BY-NC-ND 4.0」版权声明；转载可填来源链接。</div>
            </a-form-item>
            <a-form-item label="定位">
              <a-input-group compact>
                <a-input v-model:value="form.location" style="width: calc(100% - 168px)" placeholder="地点名称，如：浙江省杭州市西湖区" allow-clear />
                <a-button style="width: 84px" @click="amapPickerRef?.open(form.location)">地图选点</a-button>
                <a-button style="width: 84px" :disabled="!form.location" @click="form.location = ''">清除</a-button>
              </a-input-group>
              <div class="form-help">可选。点「地图选点」用高德地图选择位置，或直接手填；前台文章详情会展示。</div>
            </a-form-item>
            <a-form-item label="访问控制">
              <a-select v-model:value="form.visibility">
                <a-select-option value="PUBLIC">所有人可见</a-select-option>
                <a-select-option value="LOGIN">登录可见</a-select-option>
                <a-select-option value="PASSWORD">密码可见</a-select-option>
                <a-select-option value="PRIVATE">指定用户可见</a-select-option>
                <a-select-option value="UNLISTED">隐藏（仅直链访问）</a-select-option>
              </a-select>
              <div class="form-help">非公开文章默认不出现在公开列表/搜索/sitemap，仅可通过直链访问并在详情页门控（管理员与作者始终可见）。</div>
              <a-input-password
                v-if="form.visibility === 'PASSWORD'"
                v-model:value="form.accessPassword"
                class="visibility-extra"
                :placeholder="hasExistingPassword ? '留空则保留原密码' : '设置访问密码'"
                autocomplete="new-password"
                :maxlength="100"
              />
              <a-select
                v-if="form.visibility === 'PRIVATE'"
                v-model:value="form.allowedUserIds"
                class="visibility-extra"
                mode="multiple"
                show-search
                option-filter-prop="label"
                placeholder="选择可查看的用户"
              >
                <a-select-option v-for="u in userOptions" :key="u.id" :value="u.id" :label="`${u.displayName || u.username}`">
                  {{ u.displayName || u.username }}<span v-if="u.displayName"> ({{ u.username }})</span>
                </a-select-option>
              </a-select>
              <a-checkbox
                v-if="form.visibility !== 'PUBLIC' && form.visibility !== 'UNLISTED'"
                v-model:checked="form.listedInPublic"
                class="visibility-extra"
              >在公开列表/首页展示（以锁定态露出，点击需满足条件）</a-checkbox>
            </a-form-item>
            <a-form-item label="封面图">
              <a-input v-model:value="form.coverImageUrl" placeholder="封面图 URL" />
              <a-space class="cover-uploader" direction="vertical" style="width: 100%">
                <a-upload :show-upload-list="false" :custom-request="uploadCover">
                  <a-button block :loading="coverUploading">{{ coverUploading ? '上传中…' : '上传封面' }}</a-button>
                </a-upload>
                <a-button block @click="coverPickerOpen = true">从图库选择</a-button>
              </a-space>
              <div class="form-help">封面用于公开列表，上传封面为公开素材；图库素材保留原权限。</div>
              <img v-if="form.coverImageUrl" class="cover-preview" :src="previewImageUrl(form.coverImageUrl)" alt="封面预览" />
            </a-form-item>
          </aside>
        </div>
      </a-form>
    </a-card>

    <a-drawer
      v-model:open="versionDrawerOpen"
      title="版本历史"
      :width="versionDrawerWidth"
      placement="right"
    >
      <a-spin :spinning="versionLoading">
        <a-empty v-if="!articleVersions.length" description="暂无版本记录" />
        <div v-else class="version-list">
          <div v-for="version in articleVersions" :key="version.id" class="version-item">
            <div class="version-item__main">
              <div class="version-item__title">{{ version.title || '未命名版本' }}</div>
              <div class="version-item__meta">
                {{ version.changeNote || '文章变更' }} · {{ version.editorName || '系统' }} · {{ formatDateTime(version.createdAt) }}
              </div>
              <div class="version-item__summary">{{ versionExcerpt(version) }}</div>
            </div>
            <div class="version-item__actions">
              <a-button size="small" @click="openVersionDiff(version)">对比</a-button>
              <a-popconfirm
              title="确定恢复到此版本吗？当前内容会被覆盖，并生成一条新的版本记录。"
              ok-text="恢复"
              cancel-text="取消"
              @confirm="() => restoreVersion(version)"
            >
                <a-button size="small" :loading="restoringVersionId === version.id">恢复</a-button>
              </a-popconfirm>
              <a-popconfirm title="确定永久删除这条历史版本吗？删除后不能恢复。" ok-text="删除" cancel-text="取消" @confirm="() => removeVersion(version)">
                <a-button size="small" danger :loading="deletingVersionId === version.id">删除</a-button>
              </a-popconfirm>
            </div>
          </div>
        </div>
      </a-spin>
    </a-drawer>

    <a-modal v-model:open="conflictOpen" title="文章已被其他窗口更新" :width="versionDiffWidth" :footer="null">
      <p>服务器版本 {{ conflictServer?.version }} 与本地编辑发生冲突。下方红色为服务器内容，绿色为本地内容。</p>
      <div class="diff-view"><div v-for="(line,index) in conflictLines" :key="index" class="diff-line" :class="`diff-line--${line.type}`"><span>{{ line.type === 'added' ? '+' : line.type === 'removed' ? '-' : ' ' }}</span><code>{{ line.text }}</code></div></div>
      <a-space style="margin-top:16px"><a-button @click="acceptServer">载入服务器版本</a-button><a-button type="primary" @click="keepLocal">保留本地，重新确认保存</a-button></a-space>
    </a-modal>
    <a-modal v-model:open="versionDiffOpen" title="版本差异" :width="versionDiffWidth" :footer="null" wrap-class-name="version-diff-modal">
      <div v-if="diffVersion" class="diff-head">历史版本 #{{ diffVersion.id }} 与当前编辑内容对比</div>
      <div class="diff-legend"><span class="added">新增</span><span class="removed">删除</span><span>未变化</span></div>
      <div class="diff-view"><div v-for="(line,index) in versionDiffLines" :key="index" class="diff-line" :class="`diff-line--${line.type}`"><span class="diff-mark">{{ line.type==='added'?'+':line.type==='removed'?'-':' ' }}</span><code>{{ line.text || ' ' }}</code></div></div>
    </a-modal>

    <AmapPicker ref="amapPickerRef" @pick="name => (form.location = name)" />
    <AttachmentPicker v-model:open="attachmentPickerOpen" @previews="mergeMediaPreviews" @pick="insertLibraryImages" />
    <AttachmentPicker
      v-model:open="coverPickerOpen"
      :multiple="false"
      :allow-crop="true"
      crop-preset="16:9"
      crop-title="裁剪封面"
      @previews="mergeMediaPreviews"
      @pick="pickCoverFromLibrary"
    />
  </div>
</template>

<script setup>
import { computed, onActivated, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import { useRoute, useRouter } from 'vue-router';
import { MdEditor, config } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import { checkArticleQuality, deleteArticleVersion, generateArticleAiSummary, getArticleDetail, getArticleMediaPreviews, listArticleVersions, restoreArticleVersion, saveOrUpdateArticle } from '@/api/Article';
import { getAttachmentList } from '@/api/Attachment';
import { getCategoryList } from '@/api/Category';
import { createTag, getTagList } from '@/api/Tag';
import { uploadImage } from '@/api/File';
import { getArticleSeriesList } from '@/api/Series';
import { cropImage, uploadImageWithCrop } from '@/utils/imageCrop';
import { resolveImagePreview, stripUfmAccessParams } from '@/utils/imageCropUrl';
import { listSettings } from '@/api/Settings';
import { getUserList } from '@/api/User';
import { applyBlogContainer, decodeBlockPayload } from '@/utils/blog-markdown';
import { unsealDownload } from '@/api/Download';
import { formatDateTime } from '@/utils/date';
import DownloadToolbar from './components/DownloadToolbar.vue';
import SecretToolbar from './components/SecretToolbar.vue';
import TimeToolbar from './components/TimeToolbar.vue';
import ButtonToolbar from './components/ButtonToolbar.vue';
import GalleryToolbar from './components/GalleryToolbar.vue';
import MusicToolbar from './components/MusicToolbar.vue';
import LibraryToolbar from './components/LibraryToolbar.vue';
import AmapPicker from '@/components/AmapPicker.vue';
import AttachmentPicker from '@/components/AttachmentPicker.vue';
const mediaPreviews = ref({});
const previewImageUrl = url => resolveImagePreview(url, mediaPreviews.value);
config({
  editorExtensions: {
    // 本地化代码高亮，避免后台部署到国内时从 unpkg 拉取失败/超时。
    highlight: {
      js: '/admin/vendor/highlightjs/highlight.min.js',
      css: {
        atom: {
          light: '/admin/vendor/highlightjs/styles/atom-one-light.min.css',
          dark: '/admin/vendor/highlightjs/styles/atom-one-dark.min.css'
        }
      }
    }
  },
  markdownItConfig(md) {
    applyBlogContainer(md);
    const renderImage = md.renderer.rules.image;
    md.renderer.rules.image = (tokens, index, options, env, renderer) => {
      const token = tokens[index];
      const stable = token.attrGet('src');
      token.attrSet('src', previewImageUrl(stable));
      try {
        return renderImage ? renderImage(tokens, index, options, env, renderer) : renderer.renderToken(tokens, index, options);
      } finally {
        token.attrSet('src', stable);
      }
    };
  }
});
const desktopToolbars = ['bold', 'underline', 'italic', '-', 'title', 'strikeThrough', 'quote', 'unorderedList', 'orderedList', 'task', '-', 'codeRow', 'code', 'link', 'image', 'table', '-', 0, 1, 2, 3, 4, 5, 6, '-', 'revoke', 'next', '-', 'preview', 'htmlPreview', 'catalog', '-', 'pageFullscreen', 'fullscreen'];
const mobileToolbars = ['bold', 'underline', 'italic', '-', 'title', 'strikeThrough', 'quote', 'unorderedList', 'orderedList', 'task', '-', 'codeRow', 'code', 'link', 'image', 'table', '-', 0, 1, 2, 3, 4, 5, 6, '-', 'revoke', 'next', '-', 'previewOnly', 'catalog', '-', 'pageFullscreen', 'fullscreen'];
const attachmentPickerOpen = ref(false);
const coverPickerOpen = ref(false);
const route = useRoute();
const router = useRouter();
const formRef = ref();
const editorRef = ref();
const amapPickerRef = ref();
const editorWrapRef = ref();
const downloadToolbarRef = ref();
const buttonToolbarRef = ref();
const saving = ref(false);
const coverUploading = ref(false);
const creatingTag = ref(false);
const tagKeyword = ref('');
const categories = ref([]);
const tags = ref([]);
const versionDrawerOpen = ref(false);
const versionLoading = ref(false);
const articleVersions = ref([]);
const restoringVersionId = ref(null);
const deletingVersionId = ref(null);
const versionDiffOpen = ref(false);
const diffVersion = ref(null);
const versionDiffLines = ref([]);
const isMobileEditor = ref(false);
const versionDrawerWidth = computed(() => isMobileEditor.value ? '100%' : 720);
const versionDiffWidth = computed(() => isMobileEditor.value ? 'calc(100vw - 16px)' : 1100);
const articleId = computed(() => Number(route.query.id || 0));
const isEdit = computed(() => articleId.value > 0);
const toolbars = computed(() => isMobileEditor.value ? mobileToolbars : desktopToolbars);
const editorPreviewEnabled = computed(() => !isMobileEditor.value);
const editorModeKey = computed(() => isMobileEditor.value ? 'mobile-editor' : 'desktop-editor');
const form = reactive({
  id: null,
  title: '',
  slug: '',
  summary: '',
  aiSummary: '',
  aiSummaryEnabled: false,
  content: '',
  coverImageUrl: '',
  categoryId: undefined,
  tagIds: [],
  commentsEnabled: true,
  sticky: false,
  stickyOrder: 0,
  sourceType: 'ORIGINAL',
  sourceUrl: '',
  location: '',
  status: 'DRAFT',
  scheduledPublishAt: '',
  visibility: 'PUBLIC',
  accessPassword: '',
  allowedUserIds: [],
  listedInPublic: false,
  version: null,
  seriesId: undefined,
  seriesOrder: 0,
  scheduledUnpublishAt: ''
});
const bodyImagesPublic = computed(() => ['PUBLIC', 'UNLISTED'].includes(form.visibility));
const mergeMediaPreviews = values => {
  mediaPreviews.value = { ...mediaPreviews.value, ...values };
  editorRef.value?.rerender();
};
const rememberUploadPreview = asset => {
  if (asset?.url && asset.previewUrl) mergeMediaPreviews({ [stripUfmAccessParams(asset.url)]: asset.previewUrl });
};
let mediaPreviewRequest = 0;
const loadMediaPreviews = async () => {
  const request = ++mediaPreviewRequest;
  const id = articleId.value;
  const loads = [getAttachmentList({ pageNum: 1, pageSize: 100 }).then(page =>
    Object.fromEntries((page.items || []).filter(asset => asset.url && asset.previewUrl).map(asset => [asset.url, asset.previewUrl])))];
  if (id) loads.push(getArticleMediaPreviews(id));
  const results = await Promise.allSettled(loads);
  if (request !== mediaPreviewRequest || id !== articleId.value) return;
  for (const result of results) if (result.status === 'fulfilled') mergeMediaPreviews(result.value);
};
const insertMusic = code => editorRef.value?.insert(() => ({ targetValue: `\n${code}\n`, select: false }));
const aiSummaryLoading = ref(false);
const hasExistingPassword = ref(false);
const userOptions = ref([]);
const rules = {
  title: [{
    required: true,
    message: '请输入文章标题',
    trigger: 'blur'
  }],
  summary: [{
    required: false,
    max: 500,
    message: '摘要最多 500 字',
    trigger: 'blur'
  }],
  content: [{
    required: true,
    message: '请输入正文内容',
    trigger: 'blur'
  }],
  categoryId: [{
    required: true,
    message: '请选择分类',
    trigger: 'change'
  }],
  status: [{
    required: true,
    message: '请选择状态',
    trigger: 'change'
  }]
};
const draftKey = computed(() => `bd-article-draft-${articleId.value || 'new'}`);
const draftSavedAt = ref('');
let draftTimer = 0;
let draftReady = false;
const snapshotForm = () => ({
  title: form.title,
  slug: form.slug,
  summary: form.summary,
  aiSummary: form.aiSummary,
  aiSummaryEnabled: form.aiSummaryEnabled,
  content: form.content,
  coverImageUrl: form.coverImageUrl,
  categoryId: form.categoryId,
  tagIds: [...form.tagIds],
  commentsEnabled: form.commentsEnabled,
  sticky: form.sticky,
  stickyOrder: form.stickyOrder,
  sourceType: form.sourceType,
  sourceUrl: form.sourceUrl,
  location: form.location,
  status: form.status,
  scheduledPublishAt: form.scheduledPublishAt,
  visibility: form.visibility,
  allowedUserIds: [...form.allowedUserIds],
  listedInPublic: form.listedInPublic,
  version: form.version, seriesId: form.seriesId, seriesOrder: form.seriesOrder,
  scheduledUnpublishAt: form.scheduledUnpublishAt
});
const saveDraft = () => {
  try {
    localStorage.setItem(draftKey.value, JSON.stringify({
      ...snapshotForm(),
      savedAt: Date.now()
    }));
    const now = new Date();
    draftSavedAt.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
  } catch {}
};
const clearDraft = () => {
  try {
    localStorage.removeItem(draftKey.value);
  } catch {}
  draftSavedAt.value = '';
};
const scheduleSaveDraft = () => {
  if (!draftReady) return;
  if (draftTimer) clearTimeout(draftTimer);
  draftTimer = setTimeout(saveDraft, 1500);
};
watch(form, scheduleSaveDraft, {
  deep: true
});
const maybeRestoreDraft = () => {
  let raw = null;
  try {
    raw = localStorage.getItem(draftKey.value);
  } catch {
    raw = null;
  }
  if (!raw) {
    draftReady = true;
    return;
  }
  let draft = null;
  try {
    draft = JSON.parse(raw);
  } catch {
    draftReady = true;
    return;
  }
  if (draft.title === form.title && draft.content === form.content && draft.summary === form.summary) {
    draftReady = true;
    return;
  }
  Modal.confirm({
    title: '发现未保存的草稿',
    content: `检测到 ${new Date(draft.savedAt).toLocaleString()} 的本地草稿，是否恢复？恢复后可继续编辑再保存。`,
    okText: '恢复草稿',
    cancelText: '丢弃草稿',
    onOk: () => {
      Object.assign(form, {
        title: draft.title ?? form.title,
        slug: draft.slug ?? form.slug,
        summary: draft.summary ?? form.summary,
        content: draft.content ?? form.content,
        coverImageUrl: draft.coverImageUrl ?? form.coverImageUrl,
        categoryId: draft.categoryId ?? form.categoryId,
        tagIds: Array.isArray(draft.tagIds) ? draft.tagIds : form.tagIds,
        commentsEnabled: draft.commentsEnabled ?? form.commentsEnabled,
        sticky: draft.sticky ?? form.sticky,
        stickyOrder: draft.stickyOrder ?? form.stickyOrder,
        sourceType: draft.sourceType ?? form.sourceType,
        sourceUrl: draft.sourceUrl ?? form.sourceUrl,
        aiSummary: draft.aiSummary ?? form.aiSummary,
        aiSummaryEnabled: draft.aiSummaryEnabled ?? form.aiSummaryEnabled,
        status: draft.status ?? form.status,
        scheduledPublishAt: draft.scheduledPublishAt ?? form.scheduledPublishAt,
        visibility: draft.visibility ?? form.visibility,
        allowedUserIds: Array.isArray(draft.allowedUserIds) ? draft.allowedUserIds : form.allowedUserIds,
        listedInPublic: draft.listedInPublic ?? form.listedInPublic,
        seriesId: draft.seriesId ?? form.seriesId,
        seriesOrder: draft.seriesOrder ?? form.seriesOrder,
        scheduledUnpublishAt: draft.scheduledUnpublishAt ?? form.scheduledUnpublishAt
      });
      draftReady = true;
    },
    onCancel: () => {
      clearDraft();
      draftReady = true;
    }
  });
};
const VNodes = {
  props: {
    vnodes: {
      type: [Object, Array],
      required: true
    }
  },
  render() {
    return this.vnodes;
  }
};
const trimmedTagKeyword = computed(() => tagKeyword.value.trim());
const canCreateTag = computed(() => {
  const keyword = trimmedTagKeyword.value.toLowerCase();
  return Boolean(keyword) && !tags.value.some(item => (item.name || '').toLowerCase() === keyword);
});
// 将表单重置为新建时的默认值。keep-alive 复用组件时（编辑A→新建、编辑A→编辑B）
// 先重置再按当前路由加载，避免残留上一次表单内容。
const resetForm = () => {
  mediaPreviewRequest += 1;
  mediaPreviews.value = {};
  Object.assign(form, {
    id: null,
    title: '',
    slug: '',
    summary: '',
    content: '',
    coverImageUrl: '',
    categoryId: undefined,
    tagIds: [],
    commentsEnabled: true,
    sticky: false,
    stickyOrder: 0,
    sourceType: 'ORIGINAL',
    sourceUrl: '',
    location: '',
    aiSummary: '',
    aiSummaryEnabled: false,
    status: 'DRAFT',
    scheduledPublishAt: '',
    visibility: 'PUBLIC',
    accessPassword: '',
    allowedUserIds: [],
    listedInPublic: false,
  version: null,
  seriesId: undefined,
  seriesOrder: 0,
  scheduledUnpublishAt: ''
  });
};
const goBack = () => router.push('/articles');
const insertBlock = text => {
  editorRef.value?.insert(() => ({
    targetValue: text,
    select: false,
    deviationStart: 0,
    deviationEnd: 0
  }));
};
const insertSecret = gate => {
  editorRef.value?.insert(selectedText => {
    const body = selectedText && selectedText.trim() ? selectedText : '在此填写隐藏内容';
    return {
      targetValue: `\n:::bd-secret ${gate}\n${body}\n:::\n`,
      select: false,
      deviationStart: 0,
      deviationEnd: 0
    };
  });
};
const pickCoverFromLibrary = urls => {
  const url = (urls || []).filter(Boolean)[0];
  if (!url) return;
  form.coverImageUrl = url;
  message.success('封面已选择');
};
// 从图库选择已有附件 URL，直接插入 Markdown 图片，无需再上传。
const insertLibraryImages = urls => {
  const list = (urls || []).filter(Boolean);
  if (!list.length) return;
  const body = list.map(u => `![](${u})`).join('\n');
  editorRef.value?.insert(() => ({
    targetValue: `\n${body}\n`,
    select: false,
    deviationStart: 0,
    deviationEnd: 0
  }));
  message.success(`已插入 ${list.length} 张图片`);
};
// 图片组：多图上传后插入 :::bd-gallery 区块，前台按九宫格展示。
const insertGallery = async files => {
  try {
    const cropped = await cropImage(files, { title: '裁剪图片组' });
    if (!cropped) return;
    const list = Array.isArray(cropped) ? cropped : [cropped];
    const hide = message.loading(`图片上传中…(0/${list.length})`, 0);
    try {
      const urls = [];
      for (const file of list) {
        const formData = new FormData();
        formData.append('file', file);
        // eslint-disable-next-line no-await-in-loop
        const result = await uploadImage(formData, bodyImagesPublic.value);
        rememberUploadPreview(result);
        if (result?.url) urls.push(stripUfmAccessParams(result.url));
      }
      hide();
      if (!urls.length) {
        message.error('图片上传失败');
        return;
      }
      const body = urls.map(u => `![](${u})`).join('\n');
      editorRef.value?.insert(() => ({
        targetValue: `\n:::bd-gallery\n${body}\n:::\n`,
        select: false,
        deviationStart: 0,
        deviationEnd: 0
      }));
      message.success(`已插入图片组（${urls.length} 张）`);
    } catch (e) {
      hide();
      message.error(e?.message || '图片上传失败');
    }
  } catch (e) {
    message.error(e?.message || '图片上传失败');
  }
};
const findBlockRange = (kind, b64) => {
  const directive = `:::bd-${kind} ${b64}`;
  const start = form.content.indexOf(directive);
  if (start === -1) return null;
  const close = form.content.indexOf('\n:::', start + directive.length);
  if (close === -1) return null;
  let end = close + '\n:::'.length;
  let begin = start;
  if (begin > 0 && form.content[begin - 1] === '\n') begin -= 1;
  if (form.content[end] === '\n') end += 1;
  return {
    start: begin,
    end
  };
};
const replaceBlock = ({
  kind,
  oldB64,
  newText
}) => {
  const range = findBlockRange(kind, oldB64);
  if (!range) {
    message.warning('未找到对应区块，可能已被修改');
    return;
  }
  form.content = form.content.slice(0, range.start) + newText + form.content.slice(range.end);
  message.success('区块已更新');
};
const removeBlock = (kind, b64) => {
  const range = findBlockRange(kind, b64);
  if (!range) {
    message.warning('未找到对应区块');
    return;
  }
  form.content = form.content.slice(0, range.start) + form.content.slice(range.end);
  message.success('区块已删除');
};
const handleBlockEdit = async (kind, b64) => {
  const descriptor = decodeBlockPayload(b64);
  if (!descriptor || !descriptor.token) {
    message.warning('该区块无法解析，请删除后重建');
    return;
  }
  let plain;
  try {
    plain = await unsealDownload(descriptor.token);
  } catch {
    return;
  }
  const toolbar = kind === 'download' ? downloadToolbarRef.value : buttonToolbarRef.value;
  toolbar?.openForEdit({
    descriptor,
    plain,
    oldB64: b64
  });
};
const onPreviewClick = event => {
  const btn = event.target.closest?.('[data-bd-action]');
  if (!btn) return;
  event.preventDefault();
  event.stopPropagation();
  const action = btn.dataset.bdAction;
  const kind = btn.dataset.bdKind;
  const b64 = btn.dataset.bdToken;
  if (!kind || !b64) return;
  if (action === 'delete') {
    removeBlock(kind, b64);
  } else if (action === 'edit') {
    void handleBlockEdit(kind, b64);
  }
};
const syncEditorViewport = () => {
  isMobileEditor.value = window.matchMedia('(max-width: 768px)').matches;
};
const loadTaxonomy = async () => {
  const [categoryList, tagList] = await Promise.all([getCategoryList(), getTagList()]);
  categories.value = categoryList;
  tags.value = tagList;
  if (!form.categoryId && categories.value.length) {
    form.categoryId = categories.value[0].id;
  }
};
const handleTagSearch = value => {
  tagKeyword.value = value || '';
};
const filterTagOption = (input, option) => {
  const label = String(option?.label || option?.value || '');
  return label.toLowerCase().includes(String(input || '').toLowerCase());
};
const handleCreateTag = async () => {
  if (!canCreateTag.value) {
    return;
  }
  creatingTag.value = true;
  try {
    const tag = await createTag({
      name: trimmedTagKeyword.value
    });
    if (tag && !tags.value.some(item => item.id === tag.id)) {
      tags.value.push(tag);
    }
    if (tag?.id && !form.tagIds.includes(tag.id)) {
      form.tagIds = [...form.tagIds, tag.id];
    }
    message.success('标签已添加');
    tagKeyword.value = '';
  } finally {
    creatingTag.value = false;
  }
};
const toDateTimeLocal = value => {
  if (!value) {
    return '';
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const pad = part => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};
const toOffsetDateTime = value => {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
};
const applyArticleDetail = detail => {
  Object.assign(form, {
    id: detail.id,
    title: detail.title,
    slug: detail.slug,
    summary: detail.summary,
    content: detail.content,
    coverImageUrl: detail.coverImageUrl || '',
    categoryId: detail.categoryId,
    tagIds: detail.tagIds || [],
    commentsEnabled: detail.commentsEnabled !== false,
    sticky: detail.sticky === true,
    stickyOrder: detail.stickyOrder ?? 0,
    sourceType: detail.sourceType || 'ORIGINAL',
    sourceUrl: detail.sourceUrl || '',
    location: detail.location || '',
    aiSummary: detail.aiSummary || '',
    aiSummaryEnabled: detail.aiSummaryEnabled === true,
    status: detail.status,
    scheduledPublishAt: toDateTimeLocal(detail.scheduledPublishAt),
    visibility: detail.visibility || 'PUBLIC',
    accessPassword: '',
    allowedUserIds: detail.allowedUserIds || [],
    listedInPublic: detail.listedInPublic === true,
    version: detail.version,
    seriesId: detail.seriesId || undefined, seriesOrder: detail.seriesOrder || 0,
    scheduledUnpublishAt: toDateTimeLocal(detail.scheduledUnpublishAt)
  });
  hasExistingPassword.value = detail.hasPassword === true;
};
const loadArticle = async () => {
  if (!isEdit.value) {
    return;
  }
  const detail = await getArticleDetail(articleId.value);
  applyArticleDetail(detail);
};
const loadUserOptions = async () => {
  try {
    const page = await getUserList({
      page: 0,
      size: 200
    });
    userOptions.value = (page.items || []).filter(u => (u.roleCode || '').toUpperCase() !== 'GUEST');
  } catch {
    userOptions.value = [];
  }
};
const loadArticleDefaults = async () => {
  if (isEdit.value) {
    return;
  }
  try {
    const settings = await listSettings('blog');
    const defaultStatus = settings.find(item => item.settingKey === 'defaultArticleStatus')?.settingValue;
    if (['DRAFT', 'PENDING_REVIEW', 'SCHEDULED', 'PUBLISHED'].includes(defaultStatus)) {
      form.status = defaultStatus;
    }
  } catch {
    form.status = 'DRAFT';
  }
};
const uploadFormData = async (file, cropOptions = {}) => {
  const result = await uploadImageWithCrop(file, cropOptions);
  if (!result?.url) {
    throw new Error(result === null ? 'cancelled' : '上传未返回图片地址');
  }
  return result;
};
const uploadCover = async ({
  file,
  onSuccess,
  onError
}) => {
  coverUploading.value = true;
  try {
    const result = await uploadFormData(file, { publicAccess: true, defaultPreset: '16:9', title: '裁剪封面' });
    rememberUploadPreview(result);
    form.coverImageUrl = result.url;
    message.success('封面已上传');
    onSuccess(result);
  } catch (error) {
    if (error?.message !== 'cancelled') {
      message.error('封面上传失败');
    }
    onError(error);
  } finally {
    coverUploading.value = false;
  }
};
const uploadEditorImages = async (files, callback) => {
  try {
    const cropped = await cropImage(files, { title: '裁剪正文图片' });
    if (!cropped) return;
    const list = Array.isArray(cropped) ? cropped : [cropped];
    const hide = message.loading('正文图片上传中…', 0);
    try {
      const results = [];
      for (const file of list) {
        const formData = new FormData();
        formData.append('file', file);
        // eslint-disable-next-line no-await-in-loop
        const result = await uploadImage(formData, bodyImagesPublic.value);
        rememberUploadPreview(result);
        if (result?.url) results.push(result);
      }
      const urls = results.map(item => stripUfmAccessParams(item.url)).filter(Boolean);
      if (!urls.length) {
        throw new Error('上传未返回图片地址');
      }
      hide();
      message.success(`已上传 ${urls.length} 张图片`);
      callback(urls);
    } catch {
      hide();
      message.error('正文图片上传失败');
    }
  } catch {
    message.error('正文图片上传失败');
  }
};
// 从正文提取纯文字摘要：去掉自定义块/代码/图片/链接等，禁止出现 :::bd-* 与 URL。
const extractSummary = () => {
  const raw = form.content || '';
  if (!raw.trim()) {
    message.warning('正文为空，无法提取摘要');
    return;
  }
  const text = stripContentToPlainText(raw);
  if (!text) {
    message.warning('正文没有可提取的文字内容');
    return;
  }
  form.summary = text.length > 140 ? `${text.slice(0, 140)}…` : text;
  message.success('已从正文提取摘要');
};

function stripContentToPlainText(raw) {
  return String(raw || '')
    // 多行自定义块 :::bd-xxx ... :::
    .replace(/:::[\w-]+[^\n]*\n[\s\S]*?:::/g, ' ')
    // 单行自定义块 :::bd-download base64...
    .replace(/:::[\w-]+\s+\S+/g, ' ')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/https?:\/\/\S+/gi, ' ')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^\s*>+\s?/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+\.\s+/gm, '')
    .replace(/[*_~#>|]/g, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}
// AI 摘要：把标题+正文（或现有摘要）发给后端 AI 生成/润色，结果回填到 form.aiSummary。
// mode=generate 重新生成；mode=polish 在已有 aiSummary 基础上润色。不落库，随文章保存。
const generateAiSummary = async mode => {
  if (mode === 'polish' && !form.aiSummary.trim()) {
    message.warning('还没有 AI 摘要可润色，请先「AI 生成」');
    return;
  }
  if (!form.content.trim() && !form.title.trim()) {
    message.warning('请先填写标题或正文再生成摘要');
    return;
  }
  aiSummaryLoading.value = true;
  const hide = message.loading(mode === 'polish' ? 'AI 润色中…' : 'AI 生成摘要中…', 0);
  try {
    const result = await generateArticleAiSummary({
      title: form.title,
      content: form.content,
      mode,
      currentSummary: form.aiSummary
    });
    hide();
    const summary = result?.summary || '';
    if (summary) {
      form.aiSummary = summary;
      if (!form.aiSummaryEnabled) {
        form.aiSummaryEnabled = true;
      }
      message.success(mode === 'polish' ? 'AI 已润色摘要' : 'AI 已生成摘要');
    } else {
      message.warning('AI 未返回摘要内容');
    }
  } catch {
    hide();
    // 失败提示已由 request 拦截器统一弹出。
  } finally {
    aiSummaryLoading.value = false;
  }
};
const loadVersions = async () => {
  if (!isEdit.value) {
    return;
  }
  versionLoading.value = true;
  try {
    articleVersions.value = await listArticleVersions(articleId.value);
  } finally {
    versionLoading.value = false;
  }
};
const openVersionDrawer = async () => {
  versionDrawerOpen.value = true;
  await loadVersions();
};
const versionExcerpt = version => {
  const raw = version.summary || version.content || '';
  const text = stripContentToPlainText(raw);
  return text.length > 120 ? `${text.slice(0, 120)}…` : (text || '无内容摘要');
};
const buildLineDiff = (oldText, newText) => {
  const left=String(oldText||'').split('\n').slice(0,800),right=String(newText||'').split('\n').slice(0,800);
  const dp=Array.from({length:left.length+1},()=>new Uint16Array(right.length+1));
  for(let i=left.length-1;i>=0;i--)for(let j=right.length-1;j>=0;j--)dp[i][j]=left[i]===right[j]?dp[i+1][j+1]+1:Math.max(dp[i+1][j],dp[i][j+1]);
  const out=[];let i=0,j=0;while(i<left.length&&j<right.length){if(left[i]===right[j]){out.push({type:'same',text:left[i++]});j++;}else if(dp[i+1][j]>=dp[i][j+1])out.push({type:'removed',text:left[i++]});else out.push({type:'added',text:right[j++]});}while(i<left.length)out.push({type:'removed',text:left[i++]});while(j<right.length)out.push({type:'added',text:right[j++]});return out;
};
const openVersionDiff=version=>{diffVersion.value=version;versionDiffLines.value=buildLineDiff(version.content,form.content);versionDiffOpen.value=true;};
const restoreVersion = async version => {
  restoringVersionId.value = version.id;
  try {
    const detail = await restoreArticleVersion(articleId.value, version.id);
    applyArticleDetail(detail);
    message.success('已恢复到选中版本');
    await loadVersions();
  } finally {
    restoringVersionId.value = null;
  }
};
const removeVersion = async version => {
  deletingVersionId.value = version.id;
  try {
    await deleteArticleVersion(articleId.value, version.id);
    articleVersions.value = articleVersions.value.filter(item => item.id !== version.id);
    if (diffVersion.value?.id === version.id) versionDiffOpen.value = false;
    message.success('历史版本已删除');
  } finally {
    deletingVersionId.value = null;
  }
};
const qualityLoading = ref(false);
const serverQuality = ref([]);
const runQualityCheck = async (checkLinks = false) => {
  qualityLoading.value = true;
  try {
    const result = await checkArticleQuality({ title: form.title, content: form.content, summary: form.summary,
      coverImageUrl: form.coverImageUrl, slug: form.slug, seriesId: form.seriesId || null,
      seriesOrder: form.seriesOrder, scheduledPublishAt: toOffsetDateTime(form.scheduledPublishAt) || null,
      scheduledUnpublishAt: toOffsetDateTime(form.scheduledUnpublishAt) || null, checkLinks });
    serverQuality.value = result.issues || [];
    if (!serverQuality.value.length) message.success('质量检查通过');
    return result.ready;
  } finally { qualityLoading.value = false; }
};
const seriesOptions = ref([]);
const conflictOpen = ref(false);
const conflictServer = ref(null);
const conflictLines = computed(() => buildLineDiff(conflictServer.value?.content || '', form.content));
const acceptServer = () => { applyArticleDetail(conflictServer.value); conflictOpen.value = false; };
const keepLocal = () => {
  // Explicit reconciliation: keep local text and use the freshly reviewed server revision.
  form.version = conflictServer.value.version;
  conflictOpen.value = false;
  message.info('已保留本地编辑，请确认差异后再次保存');
};
const qualityIssues = computed(() => {
  const issues = [];
  if (!form.title.trim()) issues.push('请填写标题');
  if (!form.content.trim()) issues.push('请填写正文');
  if (!form.summary.trim()) issues.push('建议填写公开摘要，明确受限文章可公开的内容');
  if (!form.coverImageUrl.trim()) issues.push('建议添加封面');
  if (/!\[\s*\]\(/.test(form.content)) issues.push('部分图片缺少替代文字');
  if (/\]\(\s*(?:javascript|data):/i.test(form.content)) issues.push('正文包含不安全链接');
  if (form.scheduledUnpublishAt && new Date(form.scheduledUnpublishAt).getTime() <= Date.now()) issues.push('定时下架时间已过期');
  return issues;
});
const submitArticle = async () => {
  await formRef.value.validate();
  if (form.visibility === 'PASSWORD' && !hasExistingPassword.value && !form.accessPassword.trim()) {
    message.warning('密码可见的文章请设置访问密码');
    return;
  }
  const scheduledPublishAt = form.status === 'SCHEDULED' ? toOffsetDateTime(form.scheduledPublishAt) : null;
  if (form.status === 'SCHEDULED') {
    if (!scheduledPublishAt) {
      message.warning('请设置定时发布时间');
      return;
    }
    if (new Date(scheduledPublishAt).getTime() <= Date.now()) {
      message.warning('定时发布时间需晚于当前时间');
      return;
    }
  }
  if (form.status === 'PUBLISHED' && !(await runQualityCheck(false))) {
    message.warning('请先处理质量检查中的错误'); return;
  }
  saving.value = true;
  try {
    await saveOrUpdateArticle({
      id: form.id,
      version: form.version,
      seriesId: form.seriesId || null,
      seriesOrder: Number(form.seriesOrder) || 0,
      scheduledUnpublishAt: toOffsetDateTime(form.scheduledUnpublishAt) || null,
      title: form.title.trim(),
      slug: form.slug?.trim(),
      summary: form.summary.trim(),
      content: form.content,
      coverImageUrl: form.coverImageUrl?.trim(),
      categoryId: form.categoryId,
      tagIds: form.tagIds,
      commentsEnabled: form.commentsEnabled,
      sticky: form.sticky,
      stickyOrder: form.sticky ? Number(form.stickyOrder) || 0 : 0,
      sourceType: form.sourceType,
      sourceUrl: form.sourceType === 'REPRINT' ? (form.sourceUrl?.trim() || '') : '',
      location: form.location?.trim() || '',
      aiSummary: form.aiSummary?.trim() || '',
      aiSummaryEnabled: form.aiSummaryEnabled,
      status: form.status,
      scheduledPublishAt,
      visibility: form.visibility,
      accessPassword: form.visibility === 'PASSWORD' ? form.accessPassword.trim() ? form.accessPassword : null : null,
      allowedUserIds: form.visibility === 'PRIVATE' ? form.allowedUserIds : [],
      listedInPublic: form.listedInPublic
    });
    message.success('文章已保存，缓存已清除');
    clearDraft();
    goBack();
  } catch (error) {
    if (error.status === 409 || error.response?.status === 409) {
      conflictServer.value = await getArticleDetail(form.id);
      conflictOpen.value = true;
    } else throw error;
  } finally {
    saving.value = false;
  }
};
// 页面数据加载：抽出以便 onMounted 首次进入与 onActivated（keep-alive 复用组件再次进入）都调用。
// keep-alive 会缓存本组件，从「编辑A」→「新建」或「编辑A」→「编辑B」时组件不卸载、onMounted 不再触发，
// 若不在此重载/重置，表单会残留上一次的内容（新建带出旧文章、切换文章仍显示旧文章）。
const loadPageData = async () => {
  // 暂停草稿自动保存：resetForm/loadArticle 会改动 form 触发 deep watch，
  // 若不先关掉 draftReady，会把「重置后的空表单」当草稿写回、覆盖真实草稿。
  // maybeRestoreDraft 完成后（恢复或放弃）会重新置 draftReady=true。
  draftReady = false;
  if (draftTimer) clearTimeout(draftTimer);
  resetForm();
  hasExistingPassword.value = false;
  await loadArticleDefaults();
  seriesOptions.value = await getArticleSeriesList();
  await loadArticle();
  maybeRestoreDraft();
  void loadMediaPreviews();
};
let pageInitialized = false;
onMounted(async () => {
  syncEditorViewport();
  window.addEventListener('resize', syncEditorViewport);
  editorWrapRef.value?.addEventListener('click', onPreviewClick);
  await loadTaxonomy();
  void loadUserOptions();
  await loadPageData();
  pageInitialized = true;
});
onActivated(() => {
  // 首次挂载时 onMounted 已加载，onActivated 紧随其后会重复触发（草稿弹窗弹两次），故跳过首次。
  if (!pageInitialized) return;
  // keep-alive 复用时重新按当前路由(新建/某文章)加载，避免残留上一次表单内容。
  void loadPageData();
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', syncEditorViewport);
  editorWrapRef.value?.removeEventListener('click', onPreviewClick);
  if (draftTimer) clearTimeout(draftTimer);
});
</script>

<style lang="scss" scoped>
.article-editor-page {
  padding: 16px;
}

.article-editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 24px;
}
/* 网格子项必须 min-width:0，否则 md-editor 工具栏固有宽度会撑开 1fr 列导致整页横向溢出；
   之后由编辑器内部 overflow-x:auto 接管，工具栏横向滚动。 */
.article-editor-grid > section,
.article-editor-grid > aside {
  min-width: 0;
}

.markdown-editor {
  border: 1px solid #f0f0f0;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}
.markdown-editor :deep(.md-editor) {
  max-width: 100%;
}

.cover-uploader {
  display: block;
  margin-top: 10px;
}

.form-help {
  margin-top: 8px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
}

/* AI 摘要开关：在表单项 label 行右侧展示，与「AI 生成/继续润色」按钮同排。 */
.ai-summary-switch {
  margin-left: 8px;
}

.draft-saved-hint {
  color: #52c41a;
  font-size: 12px;
}

.visibility-extra {
  margin-top: 10px;
  width: 100%;
}

.tag-create-divider {
  margin: 4px 0;
}

.tag-create-option {
  padding: 4px 8px 8px;
}

.cover-preview {
  width: 100%;
  max-height: 180px;
  object-fit: cover;
  margin-top: 12px;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.version-list {
  display: grid;
  gap: 12px;
}

.version-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.version-item__main {
  min-width: 0;
}

.version-item__title {
  margin-bottom: 4px;
  font-weight: 600;
  color: #1f2937;
}

.version-item__meta {
  margin-bottom: 8px;
  color: #8c8c8c;
  font-size: 12px;
}

.version-item__summary {
  color: #595959;
  font-size: 13px;
  line-height: 1.6;
}
.version-item__actions{display:flex;flex:0 0 auto;gap:8px;flex-wrap:wrap}

@media screen and (max-width: 960px) {
  .article-editor-grid {
    grid-template-columns: 1fr;
  }
}
/* 移动端：编辑器降低固定高度，避免占满整屏不便操作；整页内边距收紧 */
@media screen and (max-width: 768px) {
  .article-editor-page {
    padding: 10px;
  }
  .markdown-editor :deep(.md-editor) {
    height: 460px !important;
  }
  .version-item{flex-direction:column;align-items:stretch;padding:10px}
  .version-item__actions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));width:100%}
  .version-item__actions :deep(.ant-btn){width:100%}
  .version-item__meta{line-height:1.7;word-break:break-word}
  .diff-head{font-size:13px;line-height:1.5}
  .diff-legend{gap:10px;flex-wrap:wrap}
  .diff-view{max-height:70vh;font-size:11px;border-radius:6px}
  .diff-mark{flex-basis:22px}
  .diff-line code{min-width:0;overflow-wrap:anywhere;white-space:pre-wrap}
}
.diff-head{font-weight:600;margin-bottom:10px}.diff-legend{display:flex;gap:16px;margin-bottom:10px;font-size:12px}.diff-legend .added{color:#15803d}.diff-legend .removed{color:#b91c1c}.diff-view{max-height:65vh;overflow:auto;border:1px solid #e5e7eb;border-radius:8px;background:#fff;font:12px/1.65 Consolas,monospace}.diff-line{display:flex;min-height:21px;white-space:pre-wrap;word-break:break-all}.diff-line--added{background:#dcfce7}.diff-line--removed{background:#fee2e2}.diff-mark{flex:0 0 28px;text-align:center;color:#64748b;user-select:none}.diff-line code{color:inherit;background:transparent}
</style>

<style lang="scss">
.md-editor-preview {
  .bd-block {
    margin: 18px 0;
  }
  .bd-card {
    border: 1px solid #e8eaef;
    border-radius: 12px;
    padding: 14px 16px;
    background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
  }
  .bd-card__bar {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
  .bd-card__badge {
    font-size: 12px;
    font-weight: 600;
    color: #fff;
    background: #3b82f6;
    border-radius: 6px;
    padding: 2px 8px;
  }
  .bd-card__badge--lock {
    background: #f59e0b;
  }
  .bd-card__title {
    font-size: 15px;
    font-weight: 600;
    color: #1f2937;
  }
  .bd-card__gate {
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 999px;
    background: #eef2ff;
    color: #4f46e5;
  }
  .bd-card__gate--none {
    background: #f1f5f9;
    color: #64748b;
  }
  .bd-card__hint {
    font-size: 12px;
    color: #9ca3af;
  }
  .bd-card__ops {
    margin-left: auto;
    display: inline-flex;
    gap: 6px;
  }
  .bd-card__op {
    border: 1px solid #d9d9d9;
    background: #fff;
    color: #4b5563;
    font-size: 12px;
    line-height: 1;
    padding: 4px 10px;
    border-radius: 6px;
    cursor: pointer;
    transition: border-color 0.2s, color 0.2s;
  }
  .bd-card__op:hover {
    border-color: #3b82f6;
    color: #3b82f6;
  }
  .bd-card__op--danger:hover {
    border-color: #ef4444;
    color: #ef4444;
  }
  .bd-card__desc {
    margin: 10px 0 0;
    font-size: 13px;
    color: #6b7280;
    white-space: pre-wrap;
  }
  .bd-card__items {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 10px;
    margin-top: 12px;
  }
  .bd-card__item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px;
    border: 1px solid #eef0f3;
    border-radius: 10px;
    background: #fff;
  }
  .bd-card__icon {
    width: 34px;
    height: 34px;
    flex: 0 0 34px;
    svg {
      width: 100%;
      height: 100%;
    }
  }
  .bd-card__meta {
    min-width: 0;
  }
  .bd-card__name {
    display: block;
    font-size: 13.5px;
    font-weight: 600;
    color: #1f2937;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .bd-card__sub {
    display: block;
    margin-top: 2px;
    font-size: 12px;
    color: #9ca3af;
  }
  .bd-card__empty {
    font-size: 13px;
    color: #9ca3af;
  }
  .bd-card__linkbtn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
    padding: 8px 16px;
    border-radius: 8px;
    background: var(--bd-btn-color, #3b82f6);
    color: #fff;
    font-size: 14px;
    font-weight: 500;
  }
  .bd-card__linkicon {
    width: 18px;
    height: 18px;
    display: inline-flex;
    svg {
      width: 100%;
      height: 100%;
    }
  }
  .bd-card--secret .bd-card__secret-body {
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px dashed #e5e7eb;
  }
  .bd-card--music { display: flex; align-items: center; gap: 14px; color: var(--ant-color-text, #1f2937); background: var(--ant-color-bg-container, #fff); }
  .bd-card__music-cover { width: 64px; height: 64px; flex: 0 0 64px; border-radius: 10px; object-fit: cover; margin: 0; }
  .bd-card__music-cover--empty { display: grid; place-items: center; background: var(--ant-color-fill-secondary, #eef2ff); font-size: 26px; }
  .bd-card__music-meta { display: flex; flex-direction: column; min-width: 0; gap: 5px; }
  .bd-card__music-name { font-size: 15px; overflow-wrap: anywhere; }
  .bd-card__music-artist { font-size: 13px; color: var(--ant-color-text-secondary, #64748b); overflow-wrap: anywhere; }
  .bd-card--broken {
    color: #b91c1c;
    background: #fef2f2;
    border-color: #fecaca;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 10px;
  }
}
</style>
