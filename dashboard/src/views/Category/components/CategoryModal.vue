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
      <a-form-item label="分类名称" name="name">
        <a-input v-model:value="form.name" placeholder="请输入分类名称" />
      </a-form-item>
      <a-form-item label="Slug" name="slug">
        <a-input v-model:value="form.slug" placeholder="留空自动生成" />
      </a-form-item>
      <a-form-item label="上级分类" name="parentId">
        <a-select
          v-model:value="form.parentId"
          allow-clear
          placeholder="不选择则为顶级分类"
          :disabled="hasChildren"
        >
          <a-select-option v-for="item in selectableParentOptions" :key="item.id" :value="item.id">
            {{ `${'　'.repeat(Math.max(Number(item.level || 1) - 1, 0))}${item.name}` }}
          </a-select-option>
        </a-select>
        <div v-if="hasChildren" class="form-help">该分类已有子分类，不能移动到其他父级分类下。</div>
      </a-form-item>
      <a-form-item label="外链地址" name="externalUrl">
        <a-input v-model:value="form.externalUrl" placeholder="填写后导航点击将打开外链，留空走默认 /category/{slug}" allow-clear />
        <div class="form-help">支持 http(s)://… 完整链接；自动以新标签打开。</div>
      </a-form-item>
      <a-form-item label="导航显示" name="navVisible">
        <a-switch
          v-model:checked="form.navVisible"
          checked-children="显示"
          un-checked-children="隐藏"
        />
      </a-form-item>
      <a-form-item label="排序" name="displayOrder">
        <a-input-number v-model:value="form.displayOrder" :min="0" :precision="0" style="width: 100%" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { message } from 'ant-design-vue';
import { saveOrUpdateCategory } from '@/api/Category';
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  categoryInfo: {
    type: Object,
    default: () => ({})
  },
  categories: {
    type: Array,
    default: () => []
  }
});
const emit = defineEmits(['update:visible', 'success']);
const formRef = ref(null);
const loading = ref(false);
const form = reactive({
  id: null,
  name: '',
  slug: '',
  parentId: undefined,
  iconCode: undefined,
  externalUrl: '',
  navVisible: true,
  displayOrder: 0
});
const rules = {
  name: [{
    required: true,
    message: '请输入分类名称',
    trigger: 'blur'
  }, {
    max: 64,
    message: '分类名称不能超过64个字符',
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
const isDescendant = (candidateId, parentId) => {
  if (!candidateId || !parentId) {
    return false;
  }
  let current = props.categories.find(item => item.id === candidateId);
  const visited = new Set();
  while (current?.parentId && !visited.has(current.parentId)) {
    if (current.parentId === parentId) {
      return true;
    }
    visited.add(current.parentId);
    current = props.categories.find(item => item.id === current.parentId);
  }
  return false;
};
const selectableParentOptions = computed(() => props.categories.filter(item => item.id !== form.id && Number(item.level || 1) < 3 && !isDescendant(item.id, form.id)));
const hasChildren = computed(() => Boolean(form.id) && props.categories.some(item => item.parentId === form.id));
const modalTitle = computed(() => form.id ? '编辑分类' : form.parentId ? '添加子分类' : '添加分类');
const resetForm = () => {
  form.id = null;
  form.name = '';
  form.slug = '';
  form.parentId = undefined;
  form.iconCode = undefined;
  form.externalUrl = '';
  form.navVisible = true;
  form.displayOrder = 0;
  formRef.value?.clearValidate();
};
watch(() => props.visible, visible => {
  if (!visible) {
    resetForm();
    return;
  }
  resetForm();
  if (props.categoryInfo && Object.keys(props.categoryInfo).length > 0) {
    form.id = props.categoryInfo.id ?? null;
    form.name = props.categoryInfo.name ?? '';
    form.slug = props.categoryInfo.slug ?? '';
    form.parentId = props.categoryInfo.parentId ?? undefined;
    form.iconCode = props.categoryInfo.iconCode ?? undefined;
    form.externalUrl = props.categoryInfo.externalUrl ?? '';
    form.navVisible = props.categoryInfo.navVisible !== false;
    form.displayOrder = props.categoryInfo.displayOrder ?? 0;
  }
});
const handleSubmit = async () => {
  await formRef.value.validate();
  loading.value = true;
  try {
    await saveOrUpdateCategory({
      id: form.id,
      name: form.name.trim(),
      slug: form.slug.trim(),
      parentId: form.parentId ?? null,
      iconCode: form.iconCode ?? null,
      externalUrl: form.externalUrl?.trim() || null,
      navVisible: form.navVisible,
      displayOrder: form.displayOrder ?? 0
    });
    message.success(form.id ? '更新分类成功' : '添加分类成功');
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

<style lang="scss" scoped>
.form-help {
  margin-top: 6px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
}
</style>
