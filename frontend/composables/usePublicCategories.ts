import type { CategorySummary } from '~/types/blog';
export const usePublicCategories = () => {
  const {
    fetchCategories
  } = useApiClient();
  return useAsyncData<CategorySummary[]>('public-categories', async () => {
    try {
      const response = await fetchCategories();
      return response.success ? response.data : [];
    } catch {
      return [];
    }
  }, {
    default: () => [],
    dedupe: 'defer'
  });
};
