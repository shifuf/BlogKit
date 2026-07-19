import { defineStore } from 'pinia';
export const useUserStore = defineStore('user', {
  state: () => ({
    userId: 0,
    username: '',
    displayName: '',
    roleCode: '',
    tokenName: 'Authorization',
    accessToken: ''
  }),
  getters: {
    isAuthenticated: state => Boolean(state.accessToken),
    isAdmin: state => state.roleCode === 'ADMIN',
    authorizationHeader: state => state.accessToken ? `Bearer ${state.accessToken}` : ''
  },
  actions: {
    setSession(session: {
      userId: number;
      username: string;
      displayName: string;
      roleCode?: string;
      tokenName: string;
      tokenValue: string;
    }) {
      this.userId = session.userId;
      this.username = session.username;
      this.displayName = session.displayName;
      this.roleCode = session.roleCode || 'USER';
      this.tokenName = session.tokenName || 'Authorization';
      this.accessToken = session.tokenValue;
    },
    clearSession() {
      this.userId = 0;
      this.username = '';
      this.displayName = '';
      this.roleCode = '';
      this.tokenName = 'Authorization';
      this.accessToken = '';
    },
    updateProfile(profile: {
      displayName: string;
    }) {
      this.displayName = profile.displayName;
    }
  },
  persist: true
});
