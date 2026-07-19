import { defineStore } from 'pinia';
import { getCurrentUser } from '@/api/User';
export const useUserStore = defineStore('user', {
  state: () => ({
    userInfo: {},
    token: '',
    tokenName: 'Bearer',
    isLoggedIn: false
  }),
  actions: {
    async fetchUserInfo() {
      const userInfo = await getCurrentUser();
      this.userInfo = userInfo;
      this.isLoggedIn = Boolean(this.token);
      return userInfo;
    },
    setSession(session) {
      this.token = session.tokenValue;
      this.tokenName = session.tokenName === 'Authorization' ? 'Bearer' : session.tokenName || 'Bearer';
      this.userInfo = {
        id: session.userId,
        username: session.username,
        displayName: session.displayName,
        roleCode: session.roleCode
      };
      this.isLoggedIn = true;
    },
    logout() {
      this.userInfo = {};
      this.token = '';
      this.tokenName = 'Bearer';
      this.isLoggedIn = false;
    }
  },
  getters: {
    getUserInfo: state => state.userInfo,
    getIsLoggedIn: state => state.isLoggedIn && Boolean(state.token),
    isAdmin: state => state.userInfo?.roleCode === 'ADMIN'
  },
  persist: true
});
