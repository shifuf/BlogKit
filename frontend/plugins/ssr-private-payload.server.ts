export default defineNuxtPlugin({
  name: 'private-payload',
  dependsOn: ['pinia'],
  hooks: {
    'app:rendered'() {
      const app = useNuxtApp();
      const pinia = app.payload.pinia as Record<string, Record<string, unknown>> | undefined;
      if (pinia?.user) app.payload.pinia = { ...pinia, user: { ...pinia.user, accessToken: '' } };
    }
  }
});
