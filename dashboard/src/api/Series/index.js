import request from '@/utils/request';

export function getArticleSeriesList() {
  return request({ url: '/admin/article-series', method: 'get' });
}

export function createArticleSeries(data) {
  return request({ url: '/admin/article-series', method: 'post', data });
}

export function updateArticleSeries(id, data) {
  return request({ url: `/admin/article-series/${id}`, method: 'put', data });
}

export function deleteArticleSeries(id) {
  return request({ url: `/admin/article-series/${id}`, method: 'delete' });
}
