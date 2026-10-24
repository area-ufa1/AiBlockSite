import { isAllowlisted, matchTab } from './matcher.js';
import { buildSupportPrompt, claudeUrl } from './prompt.js';
import { matchGenre } from './youtube.js';

/**
 * Whether to send the tab to Claude. Only http(s) pages are checked, so browser and extension
 * pages (including our own settings) can never be blocked. `tab.genre` is the YouTube video
 * category when the page reported it.
 * @returns {{categoryId: string, categoryName: string, prompt: string, redirectUrl: string} | null}
 */
export function decide(tab, categories, bundled, bundledKeywords = new Map()) {
  if (!tab.url || !/^https?:\/\//i.test(tab.url) || isAllowlisted(tab.url)) return null;
  // Browsers report URLs percent-encoded (q=%D0%BF…); match and show the readable form.
  const readable = { url: safeDecode(tab.url), title: tab.title };
  const match = matchTab(readable, categories, bundled, bundledKeywords) ?? matchGenre(tab, categories);
  if (!match) return null;
  const category = categories.find((c) => c.id === match.categoryId);
  const kind = category?.bundledList === 'youtube-fun' ? 'youtube' : 'adult';
  const prompt = buildSupportPrompt(readable, match.categoryName, kind);
  return { categoryId: match.categoryId, categoryName: match.categoryName, prompt, redirectUrl: claudeUrl(prompt) };
}

function safeDecode(url) {
  try {
    return decodeURIComponent(url.replace(/\+/g, ' '));
  } catch {
    return url;
  }
}
