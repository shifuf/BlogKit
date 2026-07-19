<script setup>
import zhCN from 'ant-design-vue/es/locale/zh_CN';
import ImageCropModal from '@/components/ImageCropModal.vue';
</script>

<template>
  <a-config-provider :locale="zhCN">
    <router-view></router-view>
    <ImageCropModal />
  </a-config-provider>
</template>

<style>
/* 表单 label 统一左对齐（覆盖 Ant Design 部分布局默认右对齐） */
.ant-form-vertical .ant-form-item-label,
.ant-form-horizontal .ant-form-item-label,
.ant-form-inline .ant-form-item-label,
.ant-form-item-label,
.ant-col .ant-form-item-label {
  text-align: left !important;
  justify-content: flex-start !important;
}

.ant-form-vertical .ant-form-item-label > label,
.ant-form-horizontal .ant-form-item-label > label,
.ant-form-inline .ant-form-item-label > label,
.ant-form-item-label > label {
  justify-content: flex-start !important;
  display: inline-flex !important;
  align-items: center !important;
}

.ant-form-item-label > label::before {
  margin-right: 4px !important;
  margin-left: 0 !important;
}

.login-card .ant-form-item-label,
.login-container .ant-form-item-label {
  text-align: left !important;
}

.ant-card .ant-form-item-label {
  text-align: left !important;
}

.settings-form .ant-form-item-label {
  text-align: left !important;
}
</style>
