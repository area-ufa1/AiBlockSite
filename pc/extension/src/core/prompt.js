// Supportive prompt opened on claude.ai after a block; text matches the Android app (SupportPrompt.kt).

const MAX_PAGE = 200;

export function buildSupportPrompt(tab, categoryName) {
  const title = tab.title?.trim();
  const page = (title || tab.url || '').trim().slice(0, MAX_PAGE);
  return `Я только что чуть не открыл страницу из категории «${categoryName}»: «${page}».\n` +
    'Я поставил себе приложение, чтобы не заходить на такие сайты. Пожалуйста, мягко и без осуждения ' +
    'отговори меня возвращаться туда, поддержи и помоги справиться с тягой прямо сейчас: ' +
    'предложи пару конкретных действий на ближайшие 10 минут.';
}

export function claudeUrl(prompt) {
  return `https://claude.ai/new?q=${encodeURIComponent(prompt)}`;
}
