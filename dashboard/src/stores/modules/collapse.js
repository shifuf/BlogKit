import { defineStore } from "pinia";
export const collapseStore = defineStore('isCollapse', {
  state: () => {
    return {
      isCollapse: false
    };
  },
  getters: {
    getCollapse: state => {
      return state.isCollapse;
    }
  },
  actions: {
    // 不传参数=切换；传布尔值=显式设置（供响应式断点/v-model 使用）。
    setCollapse(value) {
      this.isCollapse = typeof value === 'boolean' ? value : !this.isCollapse;
    }
  }
});