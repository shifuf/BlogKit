<template>
  <div class="import-export-page">
    <a-row :gutter="[20, 20]">
      <a-col :xs="24" :lg="12">
        <a-card class="ie-card" :bordered="false">
          <div class="ie-card__head">
            <div class="ie-card__icon ie-card__icon--blue">
              <SettingOutlined />
            </div>
            <div class="ie-card__head-text">
              <h3 class="ie-card__title">配置导入导出</h3>
              <p class="ie-card__desc">仅迁移系统设置（站点、首页、博客、通知、AI、OAuth、上传等配置项），不涉及文章、分类等内容数据。</p>
            </div>
          </div>

          <ul class="ie-card__scope">
            <li><CheckOutlined />基础设置 / 首页配置 / 博客配置</li>
            <li><CheckOutlined />通知 / AI / OAuth / 上传连接配置</li>
          </ul>

          <a-alert
            class="ie-card__alert"
            type="info"
            show-icon
            message="导入会按 group/key 覆盖同名配置项，不会修改文章、分类、标签等内容。"
          />

          <div class="ie-card__actions">
            <a-button type="primary" @click="downloadSettings">
              <template #icon><DownloadOutlined /></template>
              导出配置 JSON
            </a-button>
            <a-upload :show-upload-list="false" accept="application/json,.json" :before-upload="beforeImportSettings">
              <a-button :loading="importingSettings">
                <template #icon><UploadOutlined /></template>
                导入配置 JSON
              </a-button>
            </a-upload>
          </div>
        </a-card>
      </a-col>

      <a-col :xs="24" :lg="12">
        <a-card class="ie-card ie-card--danger" :bordered="false">
          <div class="ie-card__head">
            <div class="ie-card__icon ie-card__icon--orange">
              <DatabaseOutlined />
            </div>
            <div class="ie-card__head-text">
              <h3 class="ie-card__title">完整备份</h3>
              <p class="ie-card__desc">导出/导入站点全量数据，适合迁移服务器或定期备份。导入为高危操作，请谨慎执行。</p>
            </div>
          </div>

          <ul class="ie-card__scope">
            <li><CheckOutlined />账号与密钥 + 全部业务表与权限关系</li>
            <li><CheckOutlined />文章 + 评论 + 文件记录 + 互动 + 订阅与通知</li>
            <li class="ie-card__scope-warn"><WarningOutlined />不含 uploads 文件本体，需单独备份上传目录</li>
          </ul>

          <a-alert
            class="ie-card__alert"
            type="warning"
            show-icon
            message="版本2完整数据库恢复会替换当前全部业务数据。系统会先生成加密备份；请确认目标数据库版本一致。"
          />

          <div class="ie-card__actions">
            <a-button @click="createEncryptedBackup" :loading="operating">生成加密备份</a-button><a-button @click="drillBackup" :loading="operating">演练最近备份</a-button><a-button @click="loadBackupStatus">刷新状态</a-button>
            <a-button type="primary" @click="downloadBackup">
              <template #icon><DownloadOutlined /></template>
              导出完整备份
            </a-button>
            <a-upload :show-upload-list="false" accept="application/json,.json" :before-upload="beforeImportBackup">
              <a-button danger :loading="importingBackup">
                <template #icon><UploadOutlined /></template>
                导入完整备份
              </a-button>
            </a-upload>
          </div>
        </a-card>
      </a-col>
    </a-row>
    <a-card title="加密备份状态" :bordered="false" style="margin-top:20px"><p>数据库全表备份会保留权限、账号密钥和通知任务。上传文件本体由服务器物理备份保存；恢复时应同时还原文件目录。</p><p>目录：{{backupState?.backupDir}}</p><p v-if="backupState?.latestDrill">最近演练：{{backupState.latestDrill.ok?'成功':'失败'}} · {{backupState.latestDrill.message}}</p><a-table row-key="fileName" :data-source="backupState?.recentBackups||[]" :columns="[{title:'文件',dataIndex:'fileName'},{title:'字节',dataIndex:'sizeBytes'},{title:'时间',dataIndex:'createdAt'}]" :pagination="false" /></a-card>
  </div>
</template>

<script setup>
import { ref,onMounted } from 'vue';
import { message,Modal } from 'ant-design-vue';
import request from '@/utils/request';
import {
  CheckOutlined,
  DatabaseOutlined,
  DownloadOutlined,
  SettingOutlined,
  UploadOutlined,
  WarningOutlined
} from '@ant-design/icons-vue';
import { exportSettings, importSettings, exportBackup, importBackup } from '@/api/Settings';
const backupState=ref(null),operating=ref(false);
const loadBackupStatus=async()=>{backupState.value=await request.get('/admin/settings/backup/status')};
const createEncryptedBackup=async()=>{operating.value=true;try{await request.post('/admin/settings/backup/create');message.success('加密备份已保存');await loadBackupStatus()}finally{operating.value=false}};
const drillBackup=async()=>{operating.value=true;try{const result=await request.post('/admin/settings/backup/drill');result.ok?message.success(result.message):message.error(result.message);await loadBackupStatus()}finally{operating.value=false}};
onMounted(loadBackupStatus);
const importingSettings = ref(false);
const importingBackup = ref(false);
const timestamp = () => {
  const now = new Date();
  const pad = value => String(value).padStart(2, '0');
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
};
const downloadJson = (data, filename) => {
  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: 'application/json;charset=utf-8'
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};
const readJsonFile = file => new Promise((resolve, reject) => {
  const reader = new FileReader();
  reader.onload = () => {
    try {
      resolve(JSON.parse(String(reader.result || '{}')));
    } catch (error) {
      reject(error);
    }
  };
  reader.onerror = reject;
  reader.readAsText(file, 'utf-8');
});
const downloadSettings = async () => {
  const data = await exportSettings();
  downloadJson(data, `blog-settings-${timestamp()}.json`);
  message.success('配置 JSON 已下载');
};
const beforeImportSettings = file => {
  importSettingsFile(file);
  return false;
};
const importSettingsFile = async file => {
  importingSettings.value = true;
  try {
    const payload = await readJsonFile(file);
    await importSettings(payload);
    message.success('配置已导入');
  } catch {
    message.error('导入失败，请检查 JSON 文件格式');
  } finally {
    importingSettings.value = false;
  }
};
const downloadBackup = async () => {
  const data = await exportBackup();
  downloadJson(data, `blog-backup-${timestamp()}.json`);
  message.success('完整备份 JSON 已下载');
};
const beforeImportBackup = file => {
  Modal.confirm({title:'恢复版本2完整数据库？',content:'将替换当前全部业务表内容。恢复前会生成加密备份，上传文件需另行恢复。',okText:'确认恢复',async onOk(){await importBackupFile(file)}});
  return false;
};
const importBackupFile = async file => {
  importingBackup.value = true;
  try {
    const payload = await readJsonFile(file);
    const result = await importBackup(payload);
    const total = Object.values(result || {}).reduce((sum, value) => sum + Number(value || 0), 0);
    message.success(`完整备份已导入，共处理 ${total} 项`);
  } catch {
    message.error('导入失败，请检查完整备份 JSON 文件格式');
  } finally {
    importingBackup.value = false;
  }
};
</script>

<style lang="scss" scoped>
.import-export-page {
  width: 100%;
}
.ie-card {
  height: 100%;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04), 0 4px 16px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f0f0;
}
.ie-card--danger {
  border-color: #ffe0d6;
}
.ie-card__head {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.ie-card__icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
}
.ie-card__icon--blue {
  background: linear-gradient(135deg, #4096ff, #2f54eb);
}
.ie-card__icon--orange {
  background: linear-gradient(135deg, #ffa940, #fa541c);
}
.ie-card__title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
}
.ie-card__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(0, 0, 0, 0.5);
}
.ie-card__scope {
  margin: 18px 0;
  padding: 14px 16px;
  list-style: none;
  background: #fafbfc;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  li {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: rgba(0, 0, 0, 0.7);
    line-height: 2;
    .anticon {
      color: #52c41a;
    }
  }
}
.ie-card__scope-warn {
  color: #d46b08 !important;
  .anticon {
    color: #fa8c16 !important;
  }
}
.ie-card__alert {
  line-height: 1.7;
  :deep(.ant-alert-message) {
    line-height: 1.7;
  }
}
.ie-card__actions {
  margin-top: 18px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
@media (max-width: 768px) {
  .ie-card__actions {
    flex-direction: column;
    .ant-btn,
    :deep(.ant-upload) {
      width: 100%;
    }
    :deep(.ant-upload .ant-btn) {
      width: 100%;
    }
  }
}
</style>
