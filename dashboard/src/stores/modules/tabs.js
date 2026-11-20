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
      const existing = this.tabsList.find(item => item.path === tab.path);
      if (existing) {
        Object.assign(existing, tab);
        return;
      }
      this.tabsList.push(tab);
    }
  }
});
