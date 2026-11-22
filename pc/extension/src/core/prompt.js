// Prompt opened in ChatGPT after a block. The prompt is also copied to the clipboard.

const MAX_PAGE = 200;

/** @param {'adult'|'youtube'} kind which supportive text to use */
export function buildSupportPrompt(tab, categoryName, kind = 'adult') {
  const title = (tab.title || '').trim().slice(0, MAX_PAGE);
  const url = (tab.url || '').trim().slice(0, MAX_PAGE);
  if (kind === 'youtube') {
    return [
      'Я только что открыл развлекательное видео на YouTube и хочу не залипнуть в бесконечной ленте.',
      `Название видео: «${title || 'неизвестно'}»`,
      `Ссылка: ${url || 'неизвестно'}`,
      '',
      'Проанализируй этот ролик по доступной странице и помоги принять осознанное решение.',
      'Ответь в формате:',
      '1. О чём видео и какую пользу или развлечение оно даёт.',
      '2. Стоит ли смотреть его сейчас с учётом моей цели не отвлекаться.',
      '3. Если смотреть не стоит, предложи короткое действие на ближайшие 10 минут.',
      'Если ссылку нельзя открыть, честно скажи об этом и опирайся только на название.',
    ].join('\n');
  }
  return `Я только что чуть не открыл страницу из категории «${categoryName}»: «${title || url}».\n` +
    'Я поставил себе приложение, чтобы не заходить на такие сайты. Пожалуйста, мягко и без осуждения помоги вернуться к делу прямо сейчас и предложи пару конкретных действий на ближайшие 10 минут.';
}

export function chatgptUrl(prompt) {
  return `https://chatgpt.com/?q=${encodeURIComponent(prompt)}`;
}
