<script setup lang="ts">
interface TocNode {
  id: string;
  text: string;
  level: 2 | 3 | 4;
  children: TocNode[];
}
interface HeadingPosition {
  id: string;
  top: number;
}
const props = withDefaults(defineProps<{
  contentSelector?: string;
  refreshKey?: string | number;
}>(), {
  contentSelector: '.single-article__content'
});
const route = useRoute();
const tocItems = ref<TocNode[]>([]);
const activeId = ref('');
const collapsedIds = ref<Set<string>>(new Set());
const panelCollapsed = ref(false);
const headingElements = shallowRef<HTMLElement[]>([]);
const headingPositions = shallowRef<HeadingPosition[]>([]);
let scrollFrame = 0;
let measureFrame = 0;
const refreshTimers: number[] = [];
const assignHeadingId = (heading: HTMLElement, index: number) => {
  if (heading.id) return heading.id;
  heading.id = `article-heading-${index + 1}`;
  return heading.id;
};
const buildTree = (headings: HTMLElement[]) => {
  const roots: TocNode[] = [];
  const stack: TocNode[] = [];
  headings.forEach((heading, index) => {
    const level = Number(heading.tagName.slice(1)) as 2 | 3 | 4;
    const item: TocNode = {
      id: assignHeadingId(heading, index),
      text: heading.textContent?.trim() || `标题 ${index + 1}`,
      level,
      children: []
    };
    while (stack.length && stack[stack.length - 1]!.level >= item.level) {
      stack.pop();
    }
    if (stack.length) {
      stack[stack.length - 1]!.children.push(item);
    } else {
      roots.push(item);
    }
    stack.push(item);
  });
  return roots;
};
const isTocHeading = (heading: HTMLElement) => !heading.closest('[data-toc-exclude], .related-posts, .article-end-panel, .comments-section');
const collectHeadings = async () => {
  await nextTick();
  if (!import.meta.client) return 0;
  const content = document.querySelector<HTMLElement>(props.contentSelector);
  const headings = Array.from(content?.querySelectorAll<HTMLElement>('h2, h3, h4') ?? []).filter(isTocHeading);
  headingElements.value = headings;
  tocItems.value = buildTree(headings);
  activeId.value = headings[0]?.id || '';
  measureHeadingPositions();
  return headings.length;
};
const refreshHeadings = async () => {
  await collectHeadings();
  requestMeasureAndUpdate();
};
const clearRefreshTimers = () => {
  refreshTimers.splice(0).forEach(timerId => window.clearTimeout(timerId));
};
const measureHeadingPositions = () => {
  if (!import.meta.client) return;
  headingPositions.value = headingElements.value.map(heading => ({
    id: heading.id,
    top: heading.getBoundingClientRect().top + window.scrollY
  }));
};
const updateActiveHeading = () => {
  if (!headingPositions.value.length || !import.meta.client) return;
  const offset = 84;
  const scrollTop = window.scrollY + offset;
  let current = headingPositions.value[0]!;
  for (const heading of headingPositions.value) {
    if (heading.top <= scrollTop) {
      current = heading;
    } else {
      break;
    }
  }
  activeId.value = current.id;
};
const requestActiveHeadingUpdate = () => {
  if (!import.meta.client || scrollFrame) return;
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = 0;
    updateActiveHeading();
  });
};
const requestMeasureAndUpdate = () => {
  if (!import.meta.client) return;
  if (measureFrame) {
    window.cancelAnimationFrame(measureFrame);
  }
  measureFrame = window.requestAnimationFrame(() => {
    measureFrame = 0;
    measureHeadingPositions();
    updateActiveHeading();
  });
};
const queueRefresh = (delay: number) => {
  if (!import.meta.client) return;
  const timerId = window.setTimeout(() => {
    void refreshHeadings();
  }, delay);
  refreshTimers.push(timerId);
};
const scrollToHeading = (id: string) => {
  if (!import.meta.client) return;
  const target = document.getElementById(id);
  if (!target) return;
  const top = target.getBoundingClientRect().top + window.scrollY - 72;
  window.scrollTo({
    top,
    behavior: 'smooth'
  });
  activeId.value = id;
};
const isCollapsed = (id: string) => collapsedIds.value.has(id);
const toggleNode = (id: string) => {
  const next = new Set(collapsedIds.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  collapsedIds.value = next;
};
onMounted(async () => {
  await refreshHeadings();
  window.addEventListener('scroll', requestActiveHeadingUpdate, {
    passive: true
  });
  window.addEventListener('resize', requestMeasureAndUpdate, {
    passive: true
  });
  queueRefresh(120);
  queueRefresh(480);
});
onUnmounted(() => {
  if (import.meta.client) {
    window.removeEventListener('scroll', requestActiveHeadingUpdate);
    window.removeEventListener('resize', requestMeasureAndUpdate);
    clearRefreshTimers();
    if (scrollFrame) {
      window.cancelAnimationFrame(scrollFrame);
    }
    if (measureFrame) {
      window.cancelAnimationFrame(measureFrame);
    }
  }
});
watch(() => route.fullPath, async () => {
  collapsedIds.value = new Set();
  clearRefreshTimers();
  await refreshHeadings();
  queueRefresh(120);
  queueRefresh(480);
});
watch(() => props.refreshKey, async () => {
  if (!import.meta.client) return;
  clearRefreshTimers();
  await refreshHeadings();
  queueRefresh(80);
});
</script>

<template>
  <section v-if="tocItems.length" id="aerocore_toc-2" class="aerocore-widget-follow-scroll widget widget_aerocore_toc">
    <div class="widget-title widget-title-mac toc-widget__title">
      <span>目录</span>
      <button class="toc-widget__panel-toggle" type="button" :aria-expanded="!panelCollapsed" @click="panelCollapsed = !panelCollapsed">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>
    </div>

    <div v-show="!panelCollapsed" id="aerocore-toc" class="aerocore-toc">
      <ul class="aerocore-toc__group aerocore-toc__group--l2">
        <li
          v-for="item in tocItems"
          :key="item.id"
          class="aerocore-toc__node"
          :class="[`aerocore-toc__node--l${item.level}`, { 'aerocore-toc__node--collapsed': isCollapsed(item.id) }]"
        >
          <button class="aerocore-toc__link" :class="{ 'aerocore-toc__link--active': activeId === item.id }" type="button" @click="scrollToHeading(item.id)">
            <span
              v-if="item.children.length"
              class="aerocore-toc__toggle"
              role="button"
              tabindex="0"
              aria-label="折叠或展开"
              @click.stop="toggleNode(item.id)"
              @keydown.enter.stop.prevent="toggleNode(item.id)"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="m8 10 4 4 4-4" />
              </svg>
            </span>
            <span v-else class="aerocore-toc__toggle aerocore-toc__toggle--empty" aria-hidden="true" />
            <span class="aerocore-toc__text">{{ item.text }}</span>
          </button>

          <ul v-if="item.children.length" class="aerocore-toc__group aerocore-toc__group--l3">
            <li
              v-for="child in item.children"
              :key="child.id"
              class="aerocore-toc__node"
              :class="[`aerocore-toc__node--l${child.level}`, { 'aerocore-toc__node--collapsed': isCollapsed(child.id) }]"
            >
              <button class="aerocore-toc__link" :class="{ 'aerocore-toc__link--active': activeId === child.id }" type="button" @click="scrollToHeading(child.id)">
                <span
                  v-if="child.children.length"
                  class="aerocore-toc__toggle"
                  role="button"
                  tabindex="0"
                  aria-label="折叠或展开"
                  @click.stop="toggleNode(child.id)"
                  @keydown.enter.stop.prevent="toggleNode(child.id)"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="m8 10 4 4 4-4" />
                  </svg>
                </span>
                <span v-else class="aerocore-toc__toggle aerocore-toc__toggle--empty" aria-hidden="true" />
                <span class="aerocore-toc__text">{{ child.text }}</span>
              </button>

              <ul v-if="child.children.length" class="aerocore-toc__group aerocore-toc__group--l4">
                <li v-for="grandchild in child.children" :key="grandchild.id" class="aerocore-toc__node" :class="`aerocore-toc__node--l${grandchild.level}`">
                  <button class="aerocore-toc__link" :class="{ 'aerocore-toc__link--active': activeId === grandchild.id }" type="button" @click="scrollToHeading(grandchild.id)">
                    <span class="aerocore-toc__toggle aerocore-toc__toggle--empty" aria-hidden="true" />
                    <span class="aerocore-toc__text">{{ grandchild.text }}</span>
                  </button>
                </li>
              </ul>
            </li>
          </ul>
        </li>
      </ul>
    </div>
  </section>
</template>
