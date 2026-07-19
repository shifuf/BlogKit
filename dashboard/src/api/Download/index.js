import request from '@/utils/request';
export function sealDownload(resource) {
  return request({
    url: '/admin/downloads/seal',
    method: 'post',
    data: resource
  });
}
export function unsealDownload(token) {
  return request({
    url: '/admin/downloads/unseal',
    method: 'post',
    data: {
      token
    }
  });
}
