import request from '@/utils/request';

export function uploadImage(formData, publicAccess = false) {
  return request({
    url: '/uploads',
    params: { publicAccess: publicAccess === true },
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
}

/** 通用附件上传（图片/文档/压缩包等，后端按类型分流） */
export function uploadFile(formData) {
  return request({
    url: '/uploads/file',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
}
