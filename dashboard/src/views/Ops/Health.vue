<template>
  <div class="ops-page">
    <a-row :gutter="[16, 16]">
      <a-col :span="24"><a-card title="运行监控" :bordered="false"><template #extra><a-button @click="loadMetrics">刷新指标</a-button></template><div class="metric-grid"><a-statistic title="请求总数" :value="metrics?.requests||0"/><a-statistic title="服务端错误" :value="metrics?.errors||0"/><a-statistic title="平均耗时" :value="metrics?.averageMs||0" suffix="ms"/><a-statistic title="最大耗时" :value="metrics?.maxMs||0" suffix="ms"/><a-statistic title="缓存命中率" :value="metrics?.cacheHitRate||0" suffix="%"/><a-statistic title="数据库活跃连接" :value="metrics?.dbActive||0"/><a-statistic title="数据库等待连接" :value="metrics?.dbPending||0"/><a-statistic title="JVM 已用内存" :value="formatBytes(metrics?.jvmUsedBytes)"/></div></a-card></a-col>
      <a-col :span="24">
        <a-card :bordered="false">
          <template #title>站点健康检查</template>
          <template #extra>
            <a-button type="primary" :loading="healthLoading" @click="loadHealth">立即检查</a-button>
          </template>

          <div v-if="health" class="health-summary">
            <a-statistic title="正常" :value="health.okCount" />
            <a-statistic title="警告" :value="health.warnCount" />
            <a-statistic title="失败" :value="health.failCount" />
          </div>

          <a-table
            row-key="key"
            :columns="healthColumns"
            :data-source="health?.items || []"
            :loading="healthLoading"
            :pagination="false"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'status'">
                <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
              </template>
              <template v-if="column.dataIndex === 'message'">
                <span class="message-text">{{ record.message }}</span>
                <div class="muted">HTTP {{ record.httpStatus || '-' }} / {{ record.durationMs }} ms</div>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { getRuntimeMetrics, getSiteHealth } from '@/api/Ops';

const health = ref(null);
const healthLoading = ref(false);
const metrics=ref(null);

const healthColumns = [
  { title: '检测项', dataIndex: 'name', width: 180 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '结果', dataIndex: 'message' }
];

const statusColor = status => {
  if (status === 'OK') return 'green';
  if (status === 'WARN') return 'orange';
  return 'red';
};

const loadHealth = async () => {
  healthLoading.value = true;
  try {
    health.value = await getSiteHealth();
  } finally {
    healthLoading.value = false;
  }
};

onMounted(loadHealth);
const loadMetrics=async()=>{metrics.value=await getRuntimeMetrics()};const formatBytes=value=>{const n=Number(value||0);if(n<1024)return `${n} B`;if(n<1048576)return `${(n/1024).toFixed(1)} KB`;if(n<1073741824)return `${(n/1048576).toFixed(1)} MB`;return `${(n/1073741824).toFixed(2)} GB`};
onMounted(loadMetrics);
</script>

<style scoped lang="scss">
.ops-page {
  padding: 16px;
}

.health-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:18px}@media(max-width:900px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}

.message-text {
  word-break: break-word;
}

.muted {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}
</style>
