import request from '@/utils/request';
export const listSettings = group => request.get(group === 'music' ? '/admin/music/settings' : '/admin/settings', {
  params: {
    group
  }
});
export const saveSetting = data => request.post('/admin/settings', data);
export const saveSettingGroup = (group, data) => request.put(group === 'music' ? '/admin/music/settings' : `/admin/settings/${group}`, data);
export const musicProviderStatus = () => request.get('/admin/music/status');
export const exportSettings = () => request.get('/admin/settings/export');
export const importSettings = data => request.post('/admin/settings/import', data);
export const testAiConnection = data => request.post('/admin/settings/ai/test', data);
export const listAiModels = data => request.post('/admin/settings/ai/models', data);
export const fetchAiModels = listAiModels;
export const testUploadConnection = () => request.post('/admin/uploads/test-connection');
export const testNotificationPlatform = data => request.post('/admin/settings/notification/test', data);
export const testReplyEmail = data => request.post('/admin/settings/notification/test-reply-email', data);
export const listEmailTemplates = () => request.get('/admin/settings/notification/email-templates');
export const exportBackup = () => request.get('/admin/settings/backup');
export const importBackup = data => request.post('/admin/settings/backup/import', data);
