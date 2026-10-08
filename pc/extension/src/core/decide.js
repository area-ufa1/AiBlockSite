import { isAllowlisted, matchTab } from './matcher.js';
import { buildSupportPrompt, claudeUrl } from './prompt.js';

/**
 * Whether to send the tab to Claude. Only http(s) pages are checked, so browser and extension
 * pages (including our own settings) can never be blocked.
 * @returns {{categoryId: string, categoryName: string, prompt: string, redirectUrl: string} | null}
 */
export function decide(tab, categories, bundled) {
  if (!tab.url || !/^https?:\/\//i.test(tab.url) || isAllowlisted(tab.url)) return null;
  // Browsers report URLs percent-encoded (q=%D0%BF…); match and show the readable form.
  const readable = { url: safeDecode(tab.url), title: tab.title };
  const match = matchTab(readable, categories, bundled);
  if (!match) return null;
  const prompt = buildSupportPrompt(readable, match.categoryName);
  return { categoryId: match.categoryId, categoryName: match.categoryName, prompt, redirectUrl: claudeUrl(prompt) };
}

function safeDecode(url) {
  try {
    return decodeURIComponent(url.replace(/\+/g, ' '));
  } catch {
    return url;
  }
}
