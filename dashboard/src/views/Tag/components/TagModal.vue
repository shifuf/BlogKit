<template>
  <a-modal
    :title="modalTitle"
    :open="visible"
    :confirm-loading="loading"
    :mask-closable="false"
    ok-text="确认"
    cancel-text="取消"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :label-col="{ span: 5 }"
      :wrapper-col="{ span: 19 }"
    >
      <a-form-item label="标签名称" name="name">
        <a-input v-model:value="form.name" placeholder="请输入标签名称" />
      </a-form-item>
      <a-form-item label="Slug" name="slug">
        <a-input v-model:value="form.slug" placeholder="留空自动生成" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { message } from 'ant-design-vue';
import { saveOrUpdateTag } from '@/api/Tag';
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  tagInfo: {
    type: Object,
    default: () => ({})
  }
});
const emit = defineEmits(['update:visible', 'success']);
const formRef = ref(null);
const loading = ref(false);
const form = reactive({
  id: null,
  name: '',
  slug: ''
});
const rules = {
  name: [{
    required: true,
    message: '请输入标签名称',
    trigger: 'blur'
  }, {
    max: 64,
    message: '标签名称不能超过64个字符',
    trigger: 'blur'
  }],
  slug: [{
    max: 64,
    message: 'Slug 不能超过64个字符',
    trigger: 'blur'
  }, {
    pattern: /^[a-zA-Z0-9_-]*$/,
    message: 'Slug 仅支持字母、数字、横线和下划线',
    trigger: 'blur'
  }]
};
const modalTitle = computed(() => form.id ? '编辑标签' : '添加标签');
const resetForm = () => {
  form.id = null;
  form.name = '';
  form.slug = '';
  formRef.value?.clearValidate();
};
watch(() => props.visible, visible => {
  if (!visible) {
    resetForm();
    return;
  }
  resetForm();
  if (props.tagInfo && Object.keys(props.tagInfo).length > 0) {
    form.id = props.tagInfo.id ?? null;
    form.name = props.tagInfo.name ?? '';
    form.slug = props.tagInfo.slug ?? '';
  }
});
const handleSubmit = async () => {
  await formRef.value.validate();
  loading.value = true;
  try {
    await saveOrUpdateTag({
      id: form.id,
      name: form.name.trim(),
      slug: form.slug.trim()
    });
    message.success(form.id ? '更新标签成功' : '添加标签成功');
    emit('success');
    emit('update:visible', false);
  } finally {
    loading.value = false;
  }
};
const handleCancel = () => {
  emit('update:visible', false);
};
</script>
