<template>
  <ModalToolbar
    :visible="visible"
    width="760px"
    height="auto"
    title="下载卡片"
    @onClick="open"
    @onClose="visible = false"
  >
    <template #trigger>
      <CloudDownloadOutlined />
    </template>

    <div class="bd-modal">
      <h3 class="bd-modal__title">{{ editingB64 ? '编辑下载卡片' : '插入下载卡片' }}</h3>
      <a-form layout="vertical">
        <div class="bd-modal__row">
          <a-form-item label="标题" class="bd-modal__col">
            <a-input v-model:value="form.title" placeholder="如：资源下载" />
          </a-form-item>
          <a-form-item label="可见门控" class="bd-modal__col bd-modal__col--gate">
            <a-select v-model:value="form.gate" :dropdown-style="dropdownStyle">
              <a-select-option value="none">所有人可见</a-select-option>
              <a-select-option value="login">登录可见</a-select-option>
              <a-select-option value="comment">评论后可见</a-select-option>
            </a-select>
          </a-form-item>
        </div>
        <a-form-item label="描述（选填）">
          <a-textarea v-model:value="form.description" :rows="2" placeholder="资源说明，支持纯文本" />
        </a-form-item>

        <div class="bd-items">
          <div v-for="(item, index) in form.items" :key="index" class="bd-item">
            <div class="bd-item__icon" v-html="iconSvg(item.platform)" />
            <div class="bd-item__fields">
              <div class="bd-item__line">
                <a-select v-model:value="item.platform" class="bd-item__platform" :dropdown-style="dropdownStyle">
                  <a-select-option v-for="opt in platforms" :key="opt.key" :value="opt.key">
                    {{ opt.label }}
                  </a-select-option>
                </a-select>
                <a-input v-model:value="item.name" class="bd-item__name" placeholder="资源名（选填）" />
                <a-input v-model:value="item.code" class="bd-item__code" placeholder="提取码（选填）" />
                <a-button danger type="text" @click="removeItem(index)">
                  <DeleteOutlined />
                </a-button>
              </div>
              <a-input v-model:value="item.url" placeholder="真实下载链接（保存后加密，不出现在页面源码）" />
            </div>
          </div>
        </div>

        <a-button type="dashed" block class="bd-modal__add" @click="addItem">
          <PlusOutlined /> 添加网盘
        </a-button>
      </a-form>

      <div class="bd-modal__footer">
        <a-button @click="visible = false">取消</a-button>
        <a-button type="primary" :loading="sealing" @click="confirm">{{ editingB64 ? '保存' : '插入' }}</a-button>
      </div>
    </div>
  </ModalToolbar>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ModalToolbar } from 'md-editor-v3';
import { message } from 'ant-design-vue';
import { CloudDownloadOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue';
import { NETDISK_ICONS, netdiskIcon } from '@/utils/netdisk-icons';
import { encodeBlockPayload } from '@/utils/blog-markdown';
import { sealDownload } from '@/api/Download';
const props = defineProps({
  articleId: {
    type: Number,
    default: 0
  }
});
const emit = defineEmits(['insert', 'replace']);
const platforms = NETDISK_ICONS;
const visible = ref(false);
const sealing = ref(false);
const dropdownStyle = {
  zIndex: 21000
};
const editingB64 = ref(null);
const newItem = () => ({
  platform: 'baidu',
  name: '',
  url: '',
  code: ''
});
const form = reactive({
  title: '资源下载',
  description: '',
  gate: 'none',
  items: [newItem()]
});
const iconSvg = key => netdiskIcon(key).svg;
const addItem = () => form.items.push(newItem());
const removeItem = index => {
  form.items.splice(index, 1);
  if (form.items.length === 0) form.items.push(newItem());
};
const open = () => {
  editingB64.value = null;
  resetForm();
  visible.value = true;
};
const openForEdit = ({
  descriptor,
  plain,
  oldB64
}) => {
  const src = plain || {};
  const items = Array.isArray(src.items) && src.items.length ? src.items : [newItem()];
  form.title = src.title || descriptor?.title || '资源下载';
  form.description = src.description || descriptor?.desc || '';
  form.gate = src.gate || descriptor?.gate || 'none';
  form.items = items.map(it => ({
    platform: it.platform || 'baidu',
    name: it.name || '',
    url: it.url || '',
    code: it.code || ''
  }));
  editingB64.value = oldB64;
  visible.value = true;
};
const resetForm = () => {
  form.title = '资源下载';
  form.description = '';
  form.gate = 'none';
  form.items = [newItem()];
};
const confirm = async () => {
  if (!props.articleId) { message.warning('请先保存文章草稿，再插入授权卡片'); return; }
  const items = form.items.map(it => ({
    platform: it.platform,
    name: (it.name || '').trim(),
    url: (it.url || '').trim(),
    code: (it.code || '').trim()
  })).filter(it => it.url);
  if (items.length === 0) {
    message.warning('请至少填写一条带链接的网盘');
    return;
  }
  sealing.value = true;
  try {
    const {
      token
    } = await sealDownload({
      kind: 'download',
      gate: form.gate,
      articleId: props.articleId || null,
      title: (form.title || '').trim(),
      description: (form.description || '').trim(),
      items
    });
    const descriptor = {
      gate: form.gate,
      title: (form.title || '').trim(),
      desc: (form.description || '').trim(),
      items: items.map(it => ({
        platform: it.platform,
        name: it.name,
        hasCode: !!it.code
      })),
      token
    };
    const text = `\n:::bd-download ${encodeBlockPayload(descriptor)}\n:::\n`;
    if (editingB64.value) {
      emit('replace', {
        kind: 'download',
        oldB64: editingB64.value,
        newText: text
      });
    } else {
      emit('insert', text);
    }
    visible.value = false;
    editingB64.value = null;
    resetForm();
  } catch {} finally {
    sealing.value = false;
  }
};
defineExpose({
  openForEdit
});
</script>

<style scoped>
.bd-modal {
  padding: 8px 4px;
}
.bd-modal__title {
  margin: 0 0 16px;
  font-size: 16px;
}
.bd-modal__row {
  display: flex;
  gap: 16px;
}
.bd-modal__col {
  flex: 1;
}
.bd-modal__col--gate {
  max-width: 200px;
}
.bd-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.bd-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px;
  border: 1px solid #eaecef;
  border-radius: 8px;
}
.bd-item__icon {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
}
.bd-item__icon :deep(svg) {
  width: 100%;
  height: 100%;
}
.bd-item__fields {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bd-item__line {
  display: flex;
  gap: 8px;
  align-items: center;
}
.bd-item__platform {
  width: 130px;
  flex: 0 0 130px;
}
.bd-item__name {
  flex: 1;
}
.bd-item__code {
  width: 130px;
  flex: 0 0 130px;
}
.bd-modal__add {
  margin-top: 12px;
}
.bd-modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 18px;
}

@media (max-width: 768px) {
  .bd-modal {
    padding: 0;
  }
  .bd-modal__title {
    margin-bottom: 12px;
    font-size: 15px;
  }
  .bd-modal__row,
  .bd-item,
  .bd-item__line {
    flex-direction: column;
    align-items: stretch;
  }
  .bd-modal__row {
    gap: 0;
  }
  .bd-modal__col,
  .bd-modal__col--gate,
  .bd-item__platform,
  .bd-item__name,
  .bd-item__code {
    width: 100%;
    max-width: 100%;
    flex: 0 0 auto;
  }
  .bd-item {
    gap: 8px;
    padding: 10px;
  }
  .bd-item__icon {
    align-self: center;
  }
  .bd-item__line {
    gap: 8px;
  }
  .bd-item__line .ant-btn {
    width: 100%;
  }
  .bd-modal__footer {
    gap: 8px;
  }
  .bd-modal__footer .ant-btn {
    flex: 1 1 0;
    min-width: 0;
  }
}
</style>
