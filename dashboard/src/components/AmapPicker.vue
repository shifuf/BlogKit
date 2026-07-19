<template>
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
</template>

<script setup>
// 高德地图选点组件：从「动态管理」页抽取的可复用选点器（动态发布 + 文章定位共用）。
// 用法：<AmapPicker ref="picker" @pick="name => form.location = name" />，父组件调用 picker.value.open(当前值)。
// key/安全码由组件自己从后台「首页配置(home)」设置读取（amapKey/amapSecurity），与保存位置一致。
import { nextTick, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { listSettings } from '@/api/Settings';
import { loadAMap } from '@/utils/amap';

const emit = defineEmits(['pick']);

const amapKey = ref('');
const amapSecurity = ref('');
const loadAmapConfig = async () => {
  try {
    const home = await listSettings('home');
    const homeMap = Object.fromEntries((home || []).map(item => [item.settingKey, item.settingValue]));
    amapKey.value = homeMap.amapKey || '';
    amapSecurity.value = homeMap.amapSecurity || '';
  } catch {
    // 忽略：拿不到配置时 loadAMap 会因空 key 报错并在弹窗里提示。
  }
};
onMounted(loadAmapConfig);

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
// 对外暴露：打开选点弹窗，传入当前已选值作为初始显示。
const open = current => {
  mapModalOpen.value = true;
  pickedLocation.value = current || '';
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
defineExpose({ open });
const closeMapPicker = () => {
  mapModalOpen.value = false;
};
// 创建地图：可被「重试」复用。失败原因写入 mapError 并展示在弹窗内。
const initMap = async () => {
  mapError.value = '';
  mapLoading.value = true;
  try {
    amap = await loadAMap(amapKey.value, amapSecurity.value);
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
  // extensions:'all' + radius：逆地理返回附近 POI/AOI/道路，否则只返回行政区。
  geocoder = new amap.Geocoder({ extensions: 'all', radius: 200 });
  placeSearch = new amap.PlaceSearch({ pageSize: 8, pageIndex: 1 });
  geolocation = new amap.Geolocation({ enableHighAccuracy: true, timeout: 10000 });
  mapInstance.addControl?.(new amap.ToolBar());
  mapInstance.on('click', e => {
    const { lng, lat } = e.lnglat;
    setMarker(lng, lat);
    reverseGeocode(lng, lat);
  });
  mapInstance.on('complete', () => {
    mapLoading.value = false;
    mapInstance?.resize?.();
    if (autoLocateOnOpen) {
      autoLocateOnOpen = false;
      locateCurrent(true);
    }
  });
  // 兜底：万一 complete 不触发，8s 后也强制收起 loading 并提示。
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
const onMapModalOpenChange = async open => {
  if (!open) {
    if (mapInstance) {
      mapInstance.destroy?.();
      mapInstance = null;
      mapMarker = null;
    }
    mapLoading.value = false;
    return;
  }
  if (!mapInstance) await initMap();
};
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
// 确保 Geolocation 实例存在：SDK/插件可能在地图初始化时漏建，这里按需补建。
const ensureGeolocation = async () => {
  if (geolocation) return geolocation;
  if (!amap) {
    try {
      amap = await loadAMap(amapKey.value, amapSecurity.value);
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
    if (!silent) message.warning('定位组件未就绪，请重试');
    return;
  }
  locating.value = true;
  geo.getCurrentPosition((status, result) => {
    locating.value = false;
    if (status === 'complete' && result.position) {
      const { lng, lat } = result.position;
      setMarker(lng, lat, 16);
      if (!silent || !pickedLocation.value) {
        reverseGeocode(lng, lat);
      }
    } else if (!silent) {
      message.warning('定位失败，请检查浏览器定位权限或改用搜索');
    }
  });
};
const confirmMapPick = () => {
  if (!pickedLocation.value) return;
  emit('pick', pickedLocation.value);
  mapModalOpen.value = false;
};
</script>

<style lang="scss" scoped>
.map-picker__bar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.map-picker__alert {
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
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}
.map-picker__results li:last-child {
  border-bottom: 0;
}
.map-picker__results li:hover {
  background: #f5f8ff;
}
.map-picker__results .muted {
  display: block;
  color: #8c8c8c;
  font-size: 12px;
}
.map-picker__stage {
  position: relative;
  height: 360px;
  border-radius: 8px;
  overflow: hidden;
}
.map-picker__canvas {
  width: 100%;
  height: 100%;
}
.map-picker__mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.7);
}
.map-picker__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}
.map-picker__picked {
  color: #595959;
  font-size: 13px;
}
.form-help {
  margin-top: 6px;
  color: #8c8c8c;
  font-size: 12px;
}
</style>
