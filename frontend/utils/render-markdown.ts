import MarkdownIt from 'markdown-it';
import { applyBlogContainer } from './blog-markdown.ts';
import { sanitizeArticleHtml } from './sanitize-html.ts';
const renderer = new MarkdownIt({ html: true, breaks: true, linkify: true });
applyBlogContainer(renderer);
const originalHeading = renderer.renderer.rules.heading_open;
renderer.renderer.rules.heading_open = (tokens, index, options, env, self) => {
  const text = tokens[index + 1]?.content || '';
  const base = text.trim().toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '') || 'heading';
  env.headingIds ||= {};
  const count = env.headingIds[base] || 0;
  env.headingIds[base] = count + 1;
  tokens[index]!.attrSet('id', count ? `${base}-${count}` : base);
  return originalHeading ? originalHeading(tokens, index, options, env, self) : self.renderToken(tokens, index, options);
};
export const renderMarkdown = (content: string): string => sanitizeArticleHtml(renderer.render(String(content || ''), {}));
