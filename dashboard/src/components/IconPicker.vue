<template>
  <a-space class="xicon-picker-wrap" :size="8">
    <a-popover trigger="click" placement="bottomLeft">
      <template #content>
        <div class="xicon-picker-grid">
          <a-tooltip v-for="item in iconOptions" :key="item.value" :title="`${item.label}（${item.value}）`">
            <button
              type="button"
              class="xicon-picker-cell"
              :class="{ 'is-active': item.value === modelValue }"
              @click="selectIcon(item.value)"
            >
              <Icon size="24">
                <component :is="item.component" />
              </Icon>
            </button>
          </a-tooltip>
        </div>
      </template>

      <a-button class="xicon-picker-trigger">
        <template v-if="selectedIcon">
          <Icon size="18">
            <component :is="selectedIcon.component" />
          </Icon>
          <span>{{ selectedIcon.label }}</span>
        </template>
        <span v-else>{{ placeholder }}</span>
      </a-button>
    </a-popover>

    <a-button v-if="allowClear && modelValue" type="link" danger @click="selectIcon(undefined)">清除</a-button>
  </a-space>
</template>

<script setup>
import { computed } from 'vue';
import { Icon } from '@vicons/utils';
import {
  ArticleOutlined,
  BoltOutlined,
  BookOutlined,
  BuildOutlined,
  CategoryOutlined,
  ChatOutlined,
  CodeOutlined,
  DataObjectOutlined,
  DnsOutlined,
  EmojiEventsOutlined,
  ExtensionOutlined,
  FavoriteOutlined,
  FlagOutlined,
  FolderOutlined,
  HomeOutlined,
  ImageOutlined,
  Inventory2Outlined,
  LanguageOutlined,
  LayersOutlined,
  LightbulbOutlined,
  LocalFireDepartmentOutlined,
  MemoryOutlined,
  MovieOutlined,
  MusicNoteOutlined,
  NewReleasesOutlined,
  NotificationsOutlined,
  PaletteOutlined,
  PetsOutlined,
  PhoneAndroidOutlined,
  RocketLaunchOutlined,
  SchoolOutlined,
  SecurityOutlined,
  SettingsOutlined,
  SmartToyOutlined,
  SportsEsportsOutlined,
  StarOutlined,
  StorageOutlined,
  TerminalOutlined,
  TrendingUpOutlined,
  VideogameAssetOutlined,
  WhatshotOutlined,
  WidgetsOutlined,
  WorkOutlined
} from '@vicons/material';
const props = defineProps({
  modelValue: {
    type: String,
    default: undefined
  },
  placeholder: {
    type: String,
    default: '选择图标'
  },
  allowClear: {
    type: Boolean,
    default: true
  }
});
const emit = defineEmits(['update:modelValue']);
// 图标库：value 字段是存到数据库的图标 code，前台 NavSvgIcon 用同一套 code 渲染。
const iconOptions = [{
  label: '主页',
  value: 'Home',
  component: HomeOutlined
}, {
  label: '资源',
  value: 'Inventory2',
  component: Inventory2Outlined
}, {
  label: '工具',
  value: 'Build',
  component: BuildOutlined
}, {
  label: '代码',
  value: 'Code',
  component: CodeOutlined
}, {
  label: '移动端',
  value: 'PhoneAndroid',
  component: PhoneAndroidOutlined
}, {
  label: '站点',
  value: 'Language',
  component: LanguageOutlined
}, {
  label: '框架',
  value: 'Bolt',
  component: BoltOutlined
}, {
  label: '括号',
  value: 'DataObject',
  component: DataObjectOutlined
}, {
  label: '安全',
  value: 'Security',
  component: SecurityOutlined
}, {
  label: '服务',
  value: 'Dns',
  component: DnsOutlined
}, {
  label: 'JSON',
  value: 'Article',
  component: ArticleOutlined
}, {
  label: '模块',
  value: 'Widgets',
  component: WidgetsOutlined
}, {
  label: '层级',
  value: 'Layers',
  component: LayersOutlined
}, {
  label: '二进制',
  value: 'Memory',
  component: MemoryOutlined
}, {
  label: '文档',
  value: 'Folder',
  component: FolderOutlined
}, {
  label: '终端',
  value: 'Terminal',
  component: TerminalOutlined
}, {
  label: '机器人',
  value: 'SmartToy',
  component: SmartToyOutlined
}, {
  label: '图片',
  value: 'Image',
  component: ImageOutlined
}, {
  label: '存储',
  value: 'Storage',
  component: StorageOutlined
}, {
  label: '最热',
  value: 'Whatshot',
  component: WhatshotOutlined
}, {
  label: '火苗',
  value: 'LocalFireDepartment',
  component: LocalFireDepartmentOutlined
}, {
  label: '最新',
  value: 'NewReleases',
  component: NewReleasesOutlined
}, {
  label: '上升',
  value: 'TrendingUp',
  component: TrendingUpOutlined
}, {
  label: '星标',
  value: 'Star',
  component: StarOutlined
}, {
  label: '收藏',
  value: 'Favorite',
  component: FavoriteOutlined
}, {
  label: '书籍',
  value: 'Book',
  component: BookOutlined
}, {
  label: '分类',
  value: 'Category',
  component: CategoryOutlined
}, {
  label: '聊天',
  value: 'Chat',
  component: ChatOutlined
}, {
  label: '通知',
  value: 'Notifications',
  component: NotificationsOutlined
}, {
  label: '设置',
  value: 'Settings',
  component: SettingsOutlined
}, {
  label: '奖杯',
  value: 'EmojiEvents',
  component: EmojiEventsOutlined
}, {
  label: '扩展',
  value: 'Extension',
  component: ExtensionOutlined
}, {
  label: '旗帜',
  value: 'Flag',
  component: FlagOutlined
}, {
  label: '灯泡',
  value: 'Lightbulb',
  component: LightbulbOutlined
}, {
  label: '影视',
  value: 'Movie',
  component: MovieOutlined
}, {
  label: '音乐',
  value: 'MusicNote',
  component: MusicNoteOutlined
}, {
  label: '设计',
  value: 'Palette',
  component: PaletteOutlined
}, {
  label: '宠物',
  value: 'Pets',
  component: PetsOutlined
}, {
  label: '火箭',
  value: 'RocketLaunch',
  component: RocketLaunchOutlined
}, {
  label: '学习',
  value: 'School',
  component: SchoolOutlined
}, {
  label: '电竞',
  value: 'SportsEsports',
  component: SportsEsportsOutlined
}, {
  label: '游戏',
  value: 'VideogameAsset',
  component: VideogameAssetOutlined
}, {
  label: '工作',
  value: 'Work',
  component: WorkOutlined
}];
const selectedIcon = computed(() => iconOptions.find(item => item.value === props.modelValue));
const selectIcon = value => {
  emit('update:modelValue', value);
};
</script>

<style lang="scss" scoped>
.xicon-picker-wrap {
  width: 100%;
}

.xicon-picker-trigger {
  min-width: 168px;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
}

.xicon-picker-grid {
  width: 360px;
  max-height: 320px;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  padding: 2px;
}

.xicon-picker-cell {
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #d9d9d9;
  border-radius: 5px;
  color: #555;
  background: #fff;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.xicon-picker-cell:hover,
.xicon-picker-cell.is-active {
  color: #1677ff;
  border-color: #1677ff;
  background: #e6f4ff;
}
</style>
