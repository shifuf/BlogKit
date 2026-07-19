<template>
      <a-layout class="layout">
      <a-layout-sider
        class="asside"
        v-model:collapsed="collapsed"
        :trigger="null"
        collapsible
        breakpoint="lg"
        :collapsed-width="0"
        @breakpoint="onBreakpoint"
      >
        <MenuLogo></MenuLogo>
        <menu-bar></menu-bar>
      </a-layout-sider>
      <!-- 窄屏抽屉式侧边栏的遮罩：点击关闭 -->
      <div v-if="!collapsed" class="asside-mask" @click="closeSider"></div>
      <a-layout>
        <a-layout-header class="header">
          <Header></Header>
        </a-layout-header>
        <a-layout-content class="main">

          <Tabs class="tabs"></Tabs>
          <div class="main-container">
            <router-view v-slot="{ Component }">
              <keep-alive>
                <component :is="Component" />
              </keep-alive>
            </router-view>
          </div>
        </a-layout-content>
      </a-layout>
    </a-layout>
</template>

<script setup>
import { computed } from 'vue';
import MenuBar from './menu/MenuBar.vue';
import MenuLogo from './menu/MenuLogo.vue';
import Header from './header/Header.vue';
import { collapseStore } from '../../stores/modules/collapse.js';
import Tabs from './tabs/Tabs.vue';
const store = collapseStore();
const collapsed = computed({
  get: () => store.getCollapse,
  set: value => store.setCollapse(value)
});
// 窄屏(lg 以下)自动折叠侧边栏(收到 0 宽)，宽屏自动展开。
const onBreakpoint = broken => {
  store.setCollapse(broken);
};
const closeSider = () => store.setCollapse(true);
</script>
<style lang="scss" scoped>
.layout{
    height: 100vh;
    overflow: hidden;
    .asside{
        background-color: #fff;
        width: 200px;
    }
    // 顶栏固定：内容区内部滚动，头部始终钉在视口顶部。
    .header{
        position: sticky;
        top: 0;
        z-index: 100;
        flex: 0 0 50px;
        background-color: #2f54eb;
        height: 50px;
        line-height: 50px;
        display: flex;
        padding: 0 20px;
        align-items: center;
        justify-content: space-between;
    }
    .main{
        min-width: 0;
        min-height: 0;
        height: calc(100vh - 50px);
        overflow: hidden;
        display: flex;
        flex-direction: column;
        .tabs{
          flex: 0 0 50px;
          height:50px;
          width: 100%;
          background-color: #fff;
          margin-bottom: 3px;
        }
        .main-container{
          flex: 1;
          min-height: 0;
          background-color: #f0f2f5;
          padding: 20px;
          overflow-y: auto;
        }
    }
}
.asside-mask{
    display: none;
}
// 窄屏：侧边栏浮在内容上方（抽屉式），并显示遮罩；内容区缩小内边距。
@media (max-width: 992px){
    .layout{
        .asside{
            position: fixed;
            z-index: 1001;
            height: 100vh;
            left: 0;
            top: 0;
            box-shadow: 2px 0 8px rgba(0,0,0,0.15);
        }
        .main .main-container{
            padding: 12px;
        }
    }
    .asside-mask{
        display: block;
        position: fixed;
        inset: 0;
        z-index: 1000;
        background: rgba(0,0,0,0.45);
    }
}
</style>
