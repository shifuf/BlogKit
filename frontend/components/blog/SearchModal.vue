<script setup lang="ts">
import { Search, X } from '@lucide/vue';
const props = defineProps<{
  modelValue: boolean;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();
const keyword = ref('');
const inputRef = ref<HTMLInputElement | null>(null);
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
});
const submit = async () => {
  const q = keyword.value.trim();
  if (!q) return;
  visible.value = false;
  await navigateTo({
    path: '/search',
    query: {
      q
    }
  });
};
watch(() => props.modelValue, async value => {
  if (!value) return;
  await nextTick();
  inputRef.value?.focus();
});
</script>

<template>
  <Teleport to="body">
    <div class="search-modal" :class="{ open: visible }" @keydown.esc="visible = false">
      <div class="search-modal__overlay" @click="visible = false" />
      <div class="search-modal__content">
        <form class="search-modal__form" role="search" @submit.prevent="submit">
          <Search class="search-modal__icon" :size="20" aria-hidden="true" />
          <input
            ref="inputRef"
            v-model="keyword"
            type="text"
            name="s"
            class="search-modal__input"
            placeholder="搜索文章..."
            autocomplete="off"
          >
          <button class="search-modal__close" type="button" aria-label="关闭" @click="visible = false">
            <X :size="20" aria-hidden="true" />
          </button>
        </form>
      </div>
    </div>
  </Teleport>
</template>
