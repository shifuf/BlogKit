<template>
  <div class="admin-page">
    <a-card :bordered="false" class="appearance-card">
      <template #title>动态页外观</template>
      <template #extra>
        <a-button type="primary" :loading="apSaving" @click="saveAppearance">保存外观</a-button>
      </template>
      <div class="appearance-grid">
        <div class="appearance-fields">
          <div class="appearance-field">
            <label>动态页头像</label>
            <div class="appearance-inline">
              <a-input v-model:value="appearance.profileAvatarUrl" placeholder="头像 URL 或 /uploads 路径" />
              <a-upload :show-upload-list="false" accept="image/*" :custom-request="uploadAvatar">
                <a-button size="small" :loading="apUploading">上传</a-button>
              </a-upload>
              <a-button size="small" @click="openAppearancePicker('profileAvatarUrl')">图库</a-button>
            </div>
          </div>
          <div class="appearance-field">
            <label>动态页昵称</label>
            <a-input v-model:value="appearance.profileName" :maxlength="40" placeholder="动态页顶部昵称" />
          </div>
          <div class="appearance-field">
            <label>动态页简介</label>
            <a-textarea v-model:value="appearance.profileBio" :rows="3" :maxlength="300" placeholder="动态页默认个性签名" />
          </div>
          <div class="appearance-field">
            <label>动态页背景图</label>
            <div class="appearance-inline">
              <a-input v-model:value="appearance.dynamicsBackground" placeholder="图片 URL 或 /uploads 路径" />
              <a-upload :show-upload-list="false" :custom-request="uploadBackground">
                <a-button size="small" :loading="apUploading">{{ apUploading ? '上传中…' : '上传' }}</a-button>
              </a-upload>
              <a-button size="small" @click="openAppearancePicker('dynamicsBackground')">图库</a-button>
            </div>
          </div>
          <div class="appearance-field">
            <label>动态页签名覆盖（可选）</label>
            <a-textarea v-model:value="appearance.dynamicsSignature" :rows="2" :maxlength="200" placeholder="留空则使用上方简介" />
          </div>
        </div>
        <div class="appearance-preview">
          <div class="ap-preview" :style="apPreviewStyle">
            <div class="ap-preview__id">
              <img
                v-if="appearance.profileAvatarUrl"
                class="ap-preview__avatar"
                :src="resolveAdminAssetUrl(appearance.profileAvatarUrl)"
                alt="头像"
                loading="eager"
                decoding="async"
                @error="onAvatarPreviewError"
              />
              <div class="ap-preview__text">
                <strong>{{ appearance.profileName || '昵称' }}</strong>
                <span>{{ appearance.dynamicsSignature || appearance.profileBio || '个性签名预览' }}</span>
              </div>
            </div>
          </div>
          <div class="form-help">头像/昵称/简介写入基本配置，供动态页顶部与移动端抽屉使用；背景图与签名覆盖写入动态页设置。</div>
        </div>
      </div>
    </a-card>

    <a-card :bordered="false" class="appearance-card">
      <template #title>高德地图配置</template>
      <template #extra>
        <a-button type="primary" :loading="amapSaving" @click="saveAmap">保存地图配置</a-button>
      </template>
      <form class="amap-fields" autocomplete="off" @submit.prevent>
        <div class="appearance-field">
          <label>高德地图 Key</label>
          <a-input v-model:value="amapConfig.amapKey" name="amap-key" placeholder="高德开放平台申请的 Web端(JS API) Key" allow-clear />
          <div class="form-help">用于动态选点地图（后台）与前台动态位置解析。在高德开放平台「应用管理」创建 Web端(JS API) 类型的 Key。</div>
        </div>
        <div class="appearance-field">
          <label>高德安全密钥</label>
          <a-input-password
            v-model:value="amapConfig.amapSecurity"
            name="amap-security"
            autocomplete="new-password"
            placeholder="JS API 2.0 安全密钥 securityJsCode"
            allow-clear
          />
          <div class="form-help">高德 JS API 2.0 需配套的安全密钥（与 Key 在同一应用中获取）。仅后台地图选点使用。</div>
        </div>
      </form>
    </a-card>

    <a-card :bordered="false">
      <template #title>动态管理</template>
      <template #extra>
        <a-space>
          <span class="server-time" :title="serverTimeTip">
            🕒 服务器时间：{{ serverTime || '获取中…' }}
          </span>
          <a-button type="primary" @click="openCreateModal">新增动态</a-button>
        </a-space>
      </template>

      <a-table row-key="id" :columns="columns" :data-source="items" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'visible'">
            <a-tag :color="record.visible ? 'green' : 'default'">{{ record.visible ? '可见' : '隐藏' }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'action'">
            <a-space>
              <a @click="openEditModal(record)">编辑</a>
              <a-popconfirm title="确认删除该动态？" @confirm="removeItem(record)">
                <a class="danger-link">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="form.id ? '编辑动态' : '新增动态'"
      :width="modalWidth"
      :wrap-class-name="isMobileEditor ? 'dynamic-editor-modal dynamic-editor-modal--mobile' : 'dynamic-editor-modal'"
      :style="isMobileEditor ? { top: '8px', paddingBottom: 0, maxWidth: '100vw' } : undefined"
      @ok="submitForm"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="内容（支持 Markdown，可上传图片）" required>
          <MdEditor
            :key="editorModeKey"
            ref="editorRef"
            v-model="form.content"
            :toolbars="dynamicToolbars"
            :preview="editorPreviewEnabled"
            style="height: 360px"
            @onUploadImg="uploadDynamicImages"
          >
            <template #defToolbars>
              <GalleryToolbar @pick="insertGallery" />
              <MusicToolbar @insert="insertMusic" />
              <LibraryToolbar @open="attachmentPickerOpen = true" />
            </template>
          </MdEditor>
        </a-form-item>
        <a-form-item label="排序序号">
          <a-input-number v-model:value="form.displayOrder" :min="0" :precision="0" style="width: 100%" />
          <div class="form-help">数字越小越靠前；相同则按发布时间倒序。</div>
        </a-form-item>
        <a-form-item label="定位（可选）">
          <a-input-group compact>
            <a-input v-model:value="form.location" style="width: calc(100% - 200px)" placeholder="地点名称，如：浙江省杭州市西湖区" allow-clear />
            <a-button style="width: 100px" @click="openMapPicker">地图选点</a-button>
            <a-button style="width: 100px" :disabled="!form.location" @click="form.location = ''">清除</a-button>
          </a-input-group>
          <div class="form-help">点「地图选点」用高德地图选择位置，或直接手填。</div>
        </a-form-item>
        <a-form-item label="前台可见">
          <a-switch v-model:checked="form.visible" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="mapModalOpen" title="选择定位" width="760px" :footer="null" :after-open-change="onMapModalOpenChange" @cancel="closeMapPicker">
      <div class="map-picker">
        <div class="map-picker__bar">
          <a-input
            v-model:value="mapKeyword"
            placeholder="搜索地点名称（回车搜索），如：东莞市政府"
            allow-clear
            @press-enter="searchPlace"
          />
          <a-button type="primary" :loading="mapSearching" @click="searchPlace">搜索</a-button>
          <a-button :loading="locating" @click="locateCurrent">获取当前定位</a-button>
        </div>
        <a-alert
          v-if="mapError"
          class="map-picker__alert"
          type="error"
          show-icon
          :message="mapError"
        >
          <template #action>
            <a-button size="small" @click="retryMap">重试</a-button>
          </template>
        </a-alert>
        <ul v-if="mapResults.length" class="map-picker__results">
          <li v-for="(poi, i) in mapResults" :key="i" @click="pickPoi(poi)">
            <strong>{{ poi.name }}</strong>
            <span class="muted">{{ poi.districtText }}{{ poi.address }}</span>
          </li>
        </ul>
        <div class="map-picker__stage">
          <div ref="mapEl" class="map-picker__canvas"></div>
          <div v-if="mapLoading" class="map-picker__mask">
            <a-spin />
            <span>地图加载中…</span>
          </div>
        </div>
        <div class="map-picker__foot">
          <span class="map-picker__picked">已选：{{ pickedLocation || '在地图上点击 / 搜索 / 获取当前定位' }}</span>
          <a-button type="primary" :disabled="!pickedLocation" @click="confirmMapPick">使用此定位</a-button>
        </div>
        <div class="form-help">在地图上点击任意位置即可取该点地址（名称）；也可搜索地点或一键获取当前定位。</div>
      </div>
    </a-modal>
    <AttachmentPicker v-model:open="attachmentPickerOpen" @pick="insertLibraryImages" />
    <AttachmentPicker
      v-model:open="appearancePickerOpen"
      :multiple="false"
      :allow-crop="true"
      :crop-preset="appearancePickerKey === 'profileAvatarUrl' ? '1:1' : '16:9'"
      :crop-title="appearancePickerKey === 'profileAvatarUrl' ? '裁剪头像' : '裁剪背景图'"
      @pick="applyAppearancePicked"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, nextTick, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { MdEditor } from 'md-editor-v3';
import GalleryToolbar from '@/views/Article/components/GalleryToolbar.vue';
import MusicToolbar from '@/views/Article/components/MusicToolbar.vue';
import LibraryToolbar from '@/views/Article/components/LibraryToolbar.vue';
import AttachmentPicker from '@/components/AttachmentPicker.vue';
import 'md-editor-v3/lib/style.css';
import { createDynamic, deleteDynamic, listDynamics, updateDynamic, getServerTime } from '@/api/Dynamic';
import { listSettings, saveSettingGroup } from '@/api/Settings';
import { uploadImage } from '@/api/File';
import { cropImage, uploadImageWithCrop } from '@/utils/imageCrop';
import { formatDateTime } from '@/utils/date';
import { loadAMap } from '@/utils/amap';
const loading = ref(false);
const editorRef = ref();
const isMobileEditor = ref(false);
const attachmentPickerOpen = ref(false);
const syncEditorViewport = () => {
  isMobileEditor.value = window.matchMedia('(max-width: 768px)').matches;
};
// 桌面：编辑+预览；移动端：仅预览/编辑单栏（previewOnly），与文章编辑一致，避免两栏挤在小屏。
const desktopToolbars = ['bold', 'underline', 'italic', '-', 'title', 'quote', 'unorderedList', 'orderedList', '-', 'codeRow', 'code', 'link', 'image', 'table', '-', 0, 1, 2, '-', 'revoke', 'next', '-', 'preview', 'pageFullscreen', 'fullscreen'];
const mobileToolbars = ['bold', 'underline', 'italic', '-', 'title', 'quote', 'unorderedList', 'orderedList', '-', 'codeRow', 'code', 'link', 'image', 'table', '-', 0, 1, 2, '-', 'revoke', 'next', '-', 'previewOnly', 'pageFullscreen', 'fullscreen'];
const dynamicToolbars = computed(() => (isMobileEditor.value ? mobileToolbars : desktopToolbars));
const editorPreviewEnabled = computed(() => !isMobileEditor.value);
const editorModeKey = computed(() => (isMobileEditor.value ? 'mobile-dynamic-editor' : 'desktop-dynamic-editor'));
const modalWidth = computed(() => (isMobileEditor.value ? '100%' : '820px'));
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
// 图片组：多图上传后插入 :::bd-gallery 区块（前台动态会把图片抽成九宫格展示）。
const insertGallery = async files => {
  try {
    const cropped = await cropImage(files, { title: '裁剪图片组' });
    if (!cropped) return;
    const list = Array.isArray(cropped) ? cropped : [cropped];
    const hide = message.loading('图片上传中…', 0);
    try {
      const urls = [];
      for (const file of list) {
        const formData = new FormData();
        formData.append('file', file);
        // eslint-disable-next-line no-await-in-loop
        const result = await uploadImage(formData);
        if (result?.url) urls.push(result.url);
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
const insertMusic = code => editorRef.value?.insert(() => ({ targetValue: `\n${code}\n`, select: false }));
const items = ref([]);
const modalOpen = ref(false);
// 服务器当前时间(北京)：用于排查「动态时间固定/不实时」是否为宿主机时钟问题。
const serverTime = ref('');
const serverTimeTip = ref('若此处时间停滞或与真实北京时间不一致，则是运行后端的服务器系统时钟问题，需在宿主机校时(NTP)/重启容器。');
const loadServerTime = async () => {
  try {
    const res = await getServerTime();
    serverTime.value = (res?.display || '').replace('T', ' ').slice(0, 19);
  } catch {
    serverTime.value = '获取失败';
  }
};
// 动态页外观：头像/昵称/简介写 basic；背景/签名覆盖写 home。
const appearance = reactive({
  profileAvatarUrl: '',
  profileName: '',
  profileBio: '',
  dynamicsBackground: '',
  dynamicsSignature: ''
});
const apSaving = ref(false);
const amapConfig = reactive({ amapKey: '', amapSecurity: '' });
const amapSaving = ref(false);
const apUploading = ref(false);
const appearancePickerOpen = ref(false);
const appearancePickerKey = ref('');
const apPreviewStyle = computed(() => appearance.dynamicsBackground
  ? {
      backgroundImage: `url(${resolveAdminAssetUrl(appearance.dynamicsBackground)})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center'
    }
  : {});

/** 后台 base 是 /admin/，库里旧的 /layout-interaction/* 静态路径需补前缀，否则本地 404 */
const resolveAdminAssetUrl = url => {
  if (!url) return '';
  if (/^(https?:|blob:|data:)/i.test(url)) return url;
  if (url.startsWith('/layout-interaction/')) {
    return `${import.meta.env.BASE_URL}${url.slice(1)}`;
  }
  return url;
};

const onAvatarPreviewError = event => {
  const el = event?.target;
  if (!el || el.dataset.fallback === '1') return;
  el.dataset.fallback = '1';
  el.src = `${import.meta.env.BASE_URL}logo.png`;
};

const loadAppearance = async () => {
  try {
    const [home, basic] = await Promise.all([listSettings('home'), listSettings('basic')]);
    const homeMap = Object.fromEntries((home || []).map(item => [item.settingKey, item.settingValue]));
    const basicMap = Object.fromEntries((basic || []).map(item => [item.settingKey, item.settingValue]));
    appearance.dynamicsBackground = homeMap.dynamicsBackground || '';
    appearance.dynamicsSignature = homeMap.dynamicsSignature || '';
    appearance.profileAvatarUrl = basicMap.profileAvatarUrl || '';
    appearance.profileName = basicMap.profileName || '';
    appearance.profileBio = basicMap.profileBio || '';
    amapConfig.amapKey = homeMap.amapKey || '';
    amapConfig.amapSecurity = homeMap.amapSecurity || '';
  } catch {
    // 忽略：保持空白即可
  }
};

const openAppearancePicker = key => {
  appearancePickerKey.value = key;
  appearancePickerOpen.value = true;
};
const applyAppearancePicked = urls => {
  const url = (urls || []).filter(Boolean)[0];
  if (!url || !appearancePickerKey.value) return;
  appearance[appearancePickerKey.value] = url;
  message.success('已选择图片');
};
const uploadBackground = async ({ file, onSuccess, onError }) => {
  apUploading.value = true;
  try {
    const result = await uploadImageWithCrop(file, { defaultPreset: '16:9', title: '裁剪背景图' });
    if (!result) {
      onError?.(new Error('cancelled'));
      return;
    }
    if (!result?.url) throw new Error('上传未返回地址');
    appearance.dynamicsBackground = result.url;
    message.success('已上传');
    onSuccess?.(result);
  } catch (error) {
    if (error?.message !== 'cancelled') {
      message.error('上传失败');
    }
    onError?.(error);
  } finally {
    apUploading.value = false;
  }
};
const uploadAvatar = async ({ file, onSuccess, onError }) => {
  apUploading.value = true;
  try {
    const result = await uploadImageWithCrop(file, { defaultPreset: '1:1', title: '裁剪头像' });
    if (!result) {
      onError?.(new Error('cancelled'));
      return;
    }
    if (!result?.url) throw new Error('上传未返回地址');
    appearance.profileAvatarUrl = result.url;
    message.success('已上传');
    onSuccess?.(result);
  } catch (error) {
    if (error?.message !== 'cancelled') {
      message.error('上传失败');
    }
    onError?.(error);
  } finally {
    apUploading.value = false;
  }
};
const saveAppearance = async () => {
  apSaving.value = true;
  try {
    await Promise.all([
      saveSettingGroup('basic', {
        profileAvatarUrl: appearance.profileAvatarUrl || '',
        profileName: appearance.profileName || '',
        profileBio: appearance.profileBio || ''
      }),
      saveSettingGroup('home', {
        dynamicsBackground: appearance.dynamicsBackground || '',
        dynamicsSignature: appearance.dynamicsSignature || ''
      })
    ]);
    message.success('动态页外观已保存');
  } finally {
    apSaving.value = false;
  }
};
// 高德地图 Key/安全码独立保存：与外观分离，避免改地图配置时误存其它字段（key 存于 home 设置组）。
const saveAmap = async () => {
  amapSaving.value = true;
  try {
    await saveSettingGroup('home', {
      amapKey: amapConfig.amapKey || '',
      amapSecurity: amapConfig.amapSecurity || ''
    });
    message.success('高德地图配置已保存');
  } finally {
    amapSaving.value = false;
  }
};
// 动态正文图片上传：复用统一文件管理上传接口，成功后回调图片地址给编辑器。
const uploadDynamicImages = async (files, callback) => {
  try {
    const cropped = await cropImage(files, { title: '裁剪动态图片' });
    if (!cropped) return;
    const list = Array.isArray(cropped) ? cropped : [cropped];
    const urls = [];
    for (const file of list) {
      const formData = new FormData();
      formData.append('file', file);
      // eslint-disable-next-line no-await-in-loop
      const result = await uploadImage(formData);
      if (result?.url) urls.push(result.url);
    }
    if (!urls.length) throw new Error('上传未返回地址');
    callback(urls);
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || '图片上传失败');
  }
};
const form = reactive({
  id: null,
  content: '',
  displayOrder: 0,
  visible: true,
  location: ''
});
const columns = [{
  title: 'ID',
  dataIndex: 'id',
  width: 80
}, {
  title: '内容',
  dataIndex: 'content',
  ellipsis: true,
  customRender: ({ text }) => String(text || '').replace(/!\[[^\]]*\]\([^)]*\)/g, '[图片]').replace(/\s+/g, ' ').slice(0, 60) || '（无内容）'
}, {
  title: '排序',
  dataIndex: 'displayOrder',
  width: 90
}, {
  title: '状态',
  dataIndex: 'visible',
  width: 100
}, {
  title: '发布时间',
  dataIndex: 'publishedAt',
  width: 220,
  customRender: ({
    text
  }) => formatDateTime(text)
}, {
  title: '更新时间',
  dataIndex: 'updatedAt',
  width: 220,
  customRender: ({
    text
  }) => formatDateTime(text)
}, {
  title: '操作',
  dataIndex: 'action',
  width: 140,
  fixed: 'right'
}];
const resetForm = () => Object.assign(form, {
  id: null,
  content: '',
  displayOrder: 0,
  visible: true,
  location: ''
});
const loadItems = async () => {
  loading.value = true;
  try {
    items.value = await listDynamics();
  } finally {
    loading.value = false;
  }
};
const openCreateModal = () => {
  resetForm();
  modalOpen.value = true;
};
const openEditModal = record => {
  // 只取表单需要的字段：避免把 record 的 publishedAt/createdAt 等灌进 form，
  // 否则编辑后残留、再新建会复用旧 publishedAt，导致「时间固定/全部相同」。
  resetForm();
  Object.assign(form, {
    id: record.id,
    content: record.content || '',
    displayOrder: record.displayOrder ?? 0,
    visible: record.visible !== false,
    location: record.location || ''
  });
  modalOpen.value = true;
};
const submitForm = async () => {
  if (!form.content.trim()) {
    message.warning('请填写动态内容');
    return;
  }
  if (form.id) {
    await updateDynamic(form.id, form);
    message.success('动态已更新');
  } else {
    await createDynamic(form);
    message.success('动态已创建');
  }
  modalOpen.value = false;
  await loadItems();
};
const removeItem = async record => {
  await deleteDynamic(record.id);
  message.success('动态已删除');
  await loadItems();
};
// ===== 高德地图选点 =====
const mapModalOpen = ref(false);
const mapEl = ref(null);
const mapKeyword = ref('');
const mapSearching = ref(false);
const locating = ref(false);
const pickedLocation = ref('');
const mapResults = ref([]);
const mapLoading = ref(false);
const mapError = ref('');
let amap = null;
let mapInstance = null;
let mapMarker = null;
let geocoder = null;
let placeSearch = null;
let geolocation = null;
// 打开地图时是否自动定位一次（在地图 complete 后触发，避免容器未就绪）。
let autoLocateOnOpen = false;
const mapText = value => {
  if (Array.isArray(value)) return value.map(mapText).filter(Boolean).join('');
  return String(value || '').trim();
};
const isWeakLocationName = value => {
  const text = mapText(value);
  if (!text) return true;
  return /^[\d\s#号栋幢楼层单元室\-]+$/.test(text) || /^[\d.]+$/.test(text);
};
const buildDistrictText = address => [
  mapText(address?.city),
  mapText(address?.district)
].filter(Boolean).join('');
const resolveRegeoLocationName = (re, lng, lat) => {
  const address = re?.addressComponent || {};
  const candidates = [
    ...(Array.isArray(re?.pois) ? re.pois.map(poi => mapText(poi?.name)) : []),
    ...(Array.isArray(re?.aois) ? re.aois.map(aoi => mapText(aoi?.name)) : []),
    ...(Array.isArray(re?.roads) ? re.roads.map(road => mapText(road?.name)) : []),
    mapText(address?.streetNumber?.street),
    mapText(address?.township)
  ];
  const strongName = candidates.find(name => name && !isWeakLocationName(name));
  const districtText = buildDistrictText(address);
  if (strongName) {
    return `${districtText && !strongName.startsWith(districtText) ? districtText : ''}${strongName}`.trim();
  }
  const formatted = mapText(re?.formattedAddress || re?.formatted_address);
  return formatted || `${lng.toFixed(5)},${lat.toFixed(5)}`;
};
const setMarker = (lng, lat, zoom) => {
  if (!mapInstance) return;
  if (mapMarker) mapMarker.setPosition([lng, lat]);
  else {
    mapMarker = new amap.Marker({ position: [lng, lat], map: mapInstance });
  }
  mapInstance.setZoomAndCenter(zoom || mapInstance.getZoom(), [lng, lat]);
};
const reverseGeocode = (lng, lat) => {
  if (!geocoder) return;
  geocoder.getAddress([lng, lat], (status, result) => {
    if (status === 'complete' && result.regeocode) {
      pickedLocation.value = resolveRegeoLocationName(result.regeocode, lng, lat);
    } else {
      pickedLocation.value = `${lng.toFixed(5)},${lat.toFixed(5)}`;
    }
  });
};
const openMapPicker = () => {
  mapModalOpen.value = true;
  pickedLocation.value = form.location || '';
  mapResults.value = [];
  mapError.value = '';
  // 每次打开都自动定位到当前位置（complete 后触发），方便就近选点。
  autoLocateOnOpen = true;
  // 不依赖 a-modal 的 after-open-change（弹窗套弹窗时该回调可能不触发）。
  // 主动等弹窗渲染+开场动画后再建图，确保地图容器已挂载且拿到真实宽高。
  nextTick(() => {
    window.setTimeout(() => initMap(), 350);
  });
};
// 创建地图：可被「重试」复用。失败原因写入 mapError 并展示在弹窗内。
// JS API 2.0 最简初始化（new AMap.Map，不传 mapStyle），与 loadAMap 保持一致。
const initMap = async () => {
  mapError.value = '';
  mapLoading.value = true;
  try {
    amap = await loadAMap(amapConfig.amapKey, amapConfig.amapSecurity);
  } catch (e) {
    mapLoading.value = false;
    mapError.value = `高德地图加载失败：${e?.message || e}`;
    return;
  }
  await nextTick();
  if (!mapEl.value) {
    mapLoading.value = false;
    return;
  }
  if (mapInstance) {
    mapInstance.destroy?.();
    mapInstance = null;
    mapMarker = null;
  }
  try {
    // 不传 mapStyle：'amap://styles/normal' 属「个性化地图」服务，未在高德后台开通时样式瓦片返回空，
    // 底图加载不出来、只剩独立的文字标注层 → 正是「只剩零散数字、没有底图」的根因。默认标准底图无需该服务。
    mapInstance = new amap.Map(mapEl.value, {
      zoom: 16,
      center: [113.751, 23.020],
      resizeEnable: true
    });
  } catch (e) {
    mapLoading.value = false;
    mapError.value = `地图初始化失败：${e?.message || e}`;
    return;
  }
  // extensions:'all' + radius：逆地理返回附近 POI/AOI/道路，否则只返回行政区（点「三果酒店」却只得到「东莞市虎门镇」的根因）。
  geocoder = new amap.Geocoder({ extensions: 'all', radius: 200 });
  placeSearch = new amap.PlaceSearch({ pageSize: 8, pageIndex: 1 });
  geolocation = new amap.Geolocation({ enableHighAccuracy: true, timeout: 10000 });
  mapInstance.addControl?.(new amap.ToolBar());
  mapInstance.on('click', e => {
    const { lng, lat } = e.lnglat;
    setMarker(lng, lat);
    reverseGeocode(lng, lat);
  });
  // complete：地图瓦片/标注首次渲染完成。此时关掉 loading 并 resize 一次（容器动画后尺寸才稳定）。
  mapInstance.on('complete', () => {
    mapLoading.value = false;
    mapInstance?.resize?.();
    // 打开地图时自动定位到当前位置（仅在用户未已有选点时），方便就近选点。
    if (autoLocateOnOpen) {
      autoLocateOnOpen = false;
      locateCurrent(true);
    }
  });
  // 兜底：万一 complete 不触发（极端网络/瓦片失败），8s 后也强制收起 loading 并提示。
  window.setTimeout(() => {
    if (mapLoading.value) {
      mapLoading.value = false;
      if (!mapInstance) mapError.value = '地图渲染超时，请点重试或检查网络。';
    }
  }, 8000);
};
const retryMap = () => {
  initMap();
};
// 关键：等 a-modal 开场动画结束、容器拿到真实宽高后再创建地图。
// 否则地图在 0/动画中的宽度下初始化，瓦片按错误尺寸排布，complete 后再 resize 一次纠正。
const onMapModalOpenChange = async open => {
  // 建图已由 openMapPicker 主动驱动（弹窗套弹窗时此回调可能不触发，不能依赖它建图）。
  // 这里只负责关闭时清理。
  if (!open) {
    if (mapInstance) {
      mapInstance.destroy?.();
      mapInstance = null;
      mapMarker = null;
    }
    mapLoading.value = false;
    return;
  }
  // 打开时若 openMapPicker 的定时器尚未建图（极端时序），这里兜底补建一次。
  if (!mapInstance) await initMap();
};
// 拼出「市+区+名称」更贴近高德 App 的展示。
const poiLabel = poi => `${poi.cityname || ''}${poi.adname || ''}${poi.name}`.trim() || poi.name;
const searchPlace = () => {
  const kw = mapKeyword.value.trim();
  if (!kw || !placeSearch) return;
  mapSearching.value = true;
  mapResults.value = [];
  placeSearch.search(kw, (status, result) => {
    mapSearching.value = false;
    const pois = result?.poiList?.pois || [];
    if (status === 'complete' && pois.length) {
      mapResults.value = pois.map(p => ({
        name: p.name,
        address: p.address || '',
        districtText: `${p.cityname || ''}${p.adname || ''}`,
        lng: p.location?.lng,
        lat: p.location?.lat,
        label: poiLabel(p)
      }));
      const first = mapResults.value[0];
      if (Number.isFinite(Number(first?.lng)) && Number.isFinite(Number(first?.lat))) {
        setMarker(Number(first.lng), Number(first.lat), 16);
      }
    } else {
      message.warning('未找到相关地点，换个关键词试试');
    }
  });
};
const pickPoi = poi => {
  if (Number.isFinite(Number(poi.lng)) && Number.isFinite(Number(poi.lat))) {
    setMarker(Number(poi.lng), Number(poi.lat), 16);
  }
  pickedLocation.value = poi.label;
};
// 确保 Geolocation 实例存在：SDK/插件可能在地图初始化时漏建，这里按需补建，避免「定位组件未就绪」。
const ensureGeolocation = async () => {
  if (geolocation) return geolocation;
  if (!amap) {
    try {
      amap = await loadAMap(amapConfig.amapKey, amapConfig.amapSecurity);
    } catch {
      return null;
    }
  }
  try {
    geolocation = new amap.Geolocation({ enableHighAccuracy: true, timeout: 10000 });
  } catch {
    geolocation = null;
  }
  return geolocation;
};
// 获取当前定位：优先浏览器/SDK 定位，拿到坐标后逆地理为名称。
// silent=true 用于「打开地图自动定位」：失败不弹错误提示，且若用户已有选点则不覆盖名称（只移动视野）。
const locateCurrent = async (silent = false) => {
  const geo = await ensureGeolocation();
  if (!geo) {
    if (!silent) message.error('定位组件加载失败，请检查网络或高德密钥');
    return;
  }
  locating.value = true;
  geo.getCurrentPosition((status, result) => {
    locating.value = false;
    if (status === 'complete' && result.position) {
      const { lng, lat } = result.position;
      setMarker(lng, lat, 16);
      // 自动定位且用户已选过点：只移动视野，不覆盖已选名称。
      if (silent && pickedLocation.value) return;
      // result.formattedAddress 常已是名称；否则逆地理。
      if (result.formattedAddress) pickedLocation.value = result.formattedAddress;
      else reverseGeocode(lng, lat);
    } else if (!silent) {
      message.error('获取当前定位失败，请确认浏览器已授权定位（HTTP 下可能仅支持 IP 粗定位）');
    }
  });
};
const confirmMapPick = () => {
  form.location = pickedLocation.value;
  mapModalOpen.value = false;
};
const closeMapPicker = () => {
  mapModalOpen.value = false;
};
onMounted(() => {
  syncEditorViewport();
  window.addEventListener('resize', syncEditorViewport);
  loadItems();
  loadAppearance();
  loadServerTime();
});
onUnmounted(() => {
  window.removeEventListener('resize', syncEditorViewport);
});
</script>

<style lang="scss" scoped>
.admin-page {
  padding: 16px;
}

.danger-link {
  color: #ff4d4f;
}

.form-help {
  margin-top: 6px;
  color: #8c8c8c;
  font-size: 12px;
}

.appearance-inline {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;

  .ant-input {
    flex: 1;
    min-width: 180px;
  }
}

.server-time {
  font-size: 12px;
  color: #8c8c8c;
  cursor: help;
}

.map-picker__bar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.map-picker__results {
  list-style: none;
  margin: 0 0 10px;
  padding: 0;
  max-height: 160px;
  overflow-y: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}
.map-picker__results li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}
.map-picker__results li:last-child {
  border-bottom: 0;
}
.map-picker__results li:hover {
  background: #f0f7ff;
}
.map-picker__results .muted {
  color: #8c8c8c;
  font-size: 12px;
}
.map-picker__alert {
  margin-bottom: 10px;
}
.map-picker__stage {
  position: relative;
}
.map-picker__canvas {
  width: 100%;
  height: 380px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f0f0f0;
}
.map-picker__mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
  color: #8c8c8c;
  font-size: 13px;
}
.map-picker__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}
.map-picker__picked {
  font-size: 13px;
  color: #555;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.appearance-card {
  margin-bottom: 16px;
}
.appearance-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 20px;
}
.appearance-field {
  margin-bottom: 14px;
  display: grid;
  gap: 8px;
}
.appearance-field label {
  font-size: 13px;
  color: #555;
}
.ap-preview {
  height: 150px;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  background: linear-gradient(120deg, #2583e8, #4f46e5);
  display: flex;
  align-items: flex-end;
  padding: 14px;
}
.ap-preview__id {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ap-preview__avatar {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  object-fit: cover;
  border: 2px solid #fff;
}
.ap-preview__text {
  display: grid;
  gap: 2px;
  color: #fff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
}
.ap-preview__text strong {
  font-size: 16px;
}
.ap-preview__text span {
  font-size: 12px;
  opacity: 0.92;
}
@media screen and (max-width: 768px) {
  .appearance-grid {
    grid-template-columns: 1fr;
  }
  .admin-page {
    padding: 10px;
  }
}
</style>

<style lang="scss">
/* 动态编辑弹窗移动端：单栏全宽，与文章编辑 previewOnly 一致 */
.dynamic-editor-modal--mobile .ant-modal {
  max-width: 100vw;
  margin: 0;
  padding: 0;
}
.dynamic-editor-modal--mobile .ant-modal-content {
  border-radius: 12px 12px 0 0;
}
.dynamic-editor-modal--mobile .ant-modal-body {
  padding: 12px;
}
.dynamic-editor-modal--mobile .md-editor {
  height: 52vh !important;
  max-height: 520px;
}
</style>
