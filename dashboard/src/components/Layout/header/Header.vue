<template>
  <div class="header-bg">
    <div class="header-left">
      <Collapse></Collapse>
      <Bred></Bred>
    </div>
    <div class="header-right">
      <a-dropdown>
        <div class="avatar-wrapper">
          <a-avatar :size="32" class="avatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <span class="username" v-if="userInfo">{{ userInfo.displayName || userInfo.username }}</span>
        </div>
        <template #overlay>
          <a-menu>
            
            <a-menu-item key="2" @click="handleLogout">
              <LogoutOutlined />
              退出登录
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </div>
</template>

<script setup>
  import { UserOutlined, LogoutOutlined } from '@ant-design/icons-vue';
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/modules/user';
import { logout } from '@/api/Auth';
import { message } from 'ant-design-vue';
import Collapse from './Collapse.vue';
import Bred from '../menu/Bred.vue';
const router = useRouter();
const userStore = useUserStore();
const userInfo = computed(() => userStore.getUserInfo);
const handleLogout = async () => {
  try {
    await logout();
  } catch (error) {
    console.error(error);
  }
  userStore.logout();
  message.success('退出成功');
  router.push('/login');
};
</script>
<style lang="scss" scoped>
.header-bg{
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    height: 100%;

    .header-left {
      display: flex;
      align-items: center;
      height: 100%;
    }
    
    .header-right {
      margin-right: 20px;
      
      .avatar-wrapper {
        cursor: pointer;
        display: flex;
        align-items: center;
        
        &:hover {
          opacity: 0.8;
        }
        
        .username {
          margin-left: 8px;
          color: #fff;
        }
      }
      
      .avatar {
        background-color: #fff;
        color: #2f54eb;
      }
    }
}
@media (max-width: 768px) {
  .header-bg {
    .header-right {
      margin-right: 8px;
      .avatar-wrapper .username {
        display: none;
      }
    }
  }
}
</style>
