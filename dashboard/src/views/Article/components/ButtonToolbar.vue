<template>
  <ModalToolbar
    :visible="visible"
    width="560px"
    height="auto"
    title="自定义按钮"
    @onClick="open"
    @onClose="visible = false"
  >
    <template #trigger>
      <LinkOutlined />
    </template>

    <div class="bd-modal">
      <h3 class="bd-modal__title">{{ editingB64 ? '编辑自定义按钮' : '插入自定义图标按钮' }}</h3>
      <a-form layout="vertical">
        <a-form-item label="按钮文案">
          <a-input v-model:value="form.text" placeholder="如：在线演示" />
        </a-form-item>
        <a-form-item label="链接地址">
          <a-input v-model:value="form.url" placeholder="https://（保存后加密，不出现在页面源码）" />
        </a-form-item>
        <div class="bd-modal__row">
          <a-form-item label="图标" class="bd-modal__col">
            <a-select v-model:value="form.icon" :dropdown-style="dropdownStyle">
              <a-select-option value="link">通用链接</a-select-option>
              <a-select-option v-for="opt in platforms" :key="opt.key" :value="opt.key">
                {{ opt.label }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="主题色" class="bd-modal__col">
            <input v-model="form.color" type="color" class="bd-color" />
          </a-form-item>
          <a-form-item label="可见门控" class="bd-modal__col">
            <a-select v-model:value="form.gate" :dropdown-style="dropdownStyle">
              <a-select-option value="none">所有人可见</a-select-option>
              <a-select-option value="login">登录可见</a-select-option>
              <a-select-option value="comment">评论后可见</a-select-option>
            </a-select>
          </a-form-item>
        </div>
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
import { LinkOutlined } from '@ant-design/icons-vue';
import { NETDISK_ICONS } from '@/utils/netdisk-icons';
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
const form = reactive({
  text: '',
  url: '',
  icon: 'link',
  color: '#3b82f6',
  gate: 'none'
});
const resetForm = () => {
  form.text = '';
  form.url = '';
  form.icon = 'link';
  form.color = '#3b82f6';
  form.gate = 'none';
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
  const btn = plain && plain.button || {};
  form.text = btn.text || descriptor?.text || '';
  form.url = btn.url || '';
  form.icon = btn.icon || descriptor?.icon || 'link';
  form.color = btn.color || descriptor?.color || '#3b82f6';
  form.gate = plain && plain.gate || descriptor?.gate || 'none';
  editingB64.value = oldB64;
  visible.value = true;
};
const confirm = async () => {
  const text = form.text.trim();
  const url = form.url.trim();
  if (!text || !url) {
    message.warning('请填写按钮文案和链接');
    return;
  }
  sealing.value = true;
  try {
    const {
      token
    } = await sealDownload({
      kind: 'button',
      gate: form.gate,
      articleId: props.articleId || null,
      button: {
        text,
        url,
        icon: form.icon,
        color: form.color
      }
    });
    const descriptor = {
      gate: form.gate,
      text,
      icon: form.icon,
      color: form.color,
      token
    };
    const blockText = `\n:::bd-button ${encodeBlockPayload(descriptor)}\n:::\n`;
    if (editingB64.value) {
      emit('replace', {
        kind: 'button',
        oldB64: editingB64.value,
        newText: blockText
      });
    } else {
      emit('insert', blockText);
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
.bd-color {
  width: 100%;
  height: 32px;
  padding: 2px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
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
  .bd-modal__row {
    flex-direction: column;
    gap: 0;
  }
  .bd-modal__col {
    width: 100%;
    flex: 0 0 auto;
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
