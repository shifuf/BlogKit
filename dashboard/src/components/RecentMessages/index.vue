<template>
  <div class="recent-messages">
    <a-card title="最新留言" :loading="loading">
      <template #extra>
        <router-link to="/comments">更多评论</router-link>
      </template>

      <a-empty v-if="!messageList.length" description="暂无留言" />

      <a-list v-else :data-source="messageList" :pagination="false">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta>
              <template #title>
                <router-link to="/comments">{{ item.content }}</router-link>
              </template>
              <template #description>
                <div class="message-meta">
                  <span class="message-author">{{ item.authorName }}</span>
                  <span class="message-time">{{ formatDate(item.createdAt) }}</span>
                </div>
              </template>
              <template #avatar>
                <a-avatar>{{ item.authorName?.charAt(0) }}</a-avatar>
              </template>
            </a-list-item-meta>
          </a-list-item>
        </template>
      </a-list>
    </a-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { getMessageList } from '@/api/Message';
import { formatDateTime } from '@/utils/date';
const props = defineProps({
  limit: {
    type: Number,
    default: 5
  }
});
const messageList = ref([]);
const loading = ref(false);
const loadRecentMessages = async () => {
  try {
    loading.value = true;
    const page = await getMessageList({
      pageNum: 1,
      pageSize: props.limit
    });
    messageList.value = page.items || [];
  } catch (error) {
    console.error('获取最近留言出错', error);
  } finally {
    // 成功时也要复位 loading，否则 a-card 一直处于 loading 态导致列表渲染不出来。
    loading.value = false;
  }
};
const formatDate = formatDateTime;
onMounted(() => {
  loadRecentMessages();
});
</script>

<style lang="scss" scoped>
.recent-messages {
  .message-meta {
    display: flex;
    justify-content: space-between;
    
    .message-author {
      font-weight: 500;
    }
    
    .message-time {
      color: rgba(0, 0, 0, 0.45);
    }
  }
  
  :deep(.ant-list-item-meta-title) {
    margin-bottom: 8px;
    
    a {
      color: rgba(0, 0, 0, 0.85);
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      
      &:hover {
        color: #1890ff;
      }
    }
  }
}
</style> 
