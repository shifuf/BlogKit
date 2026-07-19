import { defineStore } from "pinia";
export const tabsStore = defineStore('tabsStore', {
  state: () => {
    return {
      tabsList: []
    };
  },
  getters: {
    getTabs: state => {
      return state.tabsList;
    }
  },
  actions: {
    addTab(tab) {
      if (tab.path == '/login') return;
      if (this.tabsList.some(item => item.path === tab.path)) {
        return;
      }
      this.tabsList.push(tab);
    }
  }
});