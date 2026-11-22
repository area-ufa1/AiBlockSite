// Prompt opened in ChatGPT after a block. The prompt is also copied to the clipboard.

const MAX_PAGE = 200;

/** @param {'adult'|'youtube'} kind which supportive text to use */
export function buildSupportPrompt(tab, categoryName, kind = 'adult', options = {}) {
  const title = (tab.title || '').trim().slice(0, MAX_PAGE);
  const url = (tab.url || '').trim().slice(0, MAX_PAGE);
  const kanbanContext = String(options.kanbanContext || '').trim();
  const contextBlock = kanbanContext ? `\n\nКонтекст моего Kanban:\n${kanbanContext}\n\nИспользуй этот контекст только для выбора реалистичных следующих шагов. Не придумывай карточки, сроки или обязанности, которых здесь нет.` : '';
  if (kind === 'youtube') {
    return [
      'Я только что открыл развлекательное видео на YouTube и остановился, потому что хочу вернуть себе управление вниманием, а не провалиться в бесконечную ленту.',
      `Название видео: «${title || 'неизвестно'}»`,
      `Ссылка: ${url || 'неизвестно'}`,
      '',
      'Проанализируй этот ролик по доступной странице и помоги принять спокойное, осознанное решение.',
      'Ответь в формате:',
      '1. Коротко объясни, о чём видео и какую пользу или эмоцию оно может дать.',
      '2. Оцени, подходит ли оно мне сейчас с учётом цели не отвлекаться.',
      '3. Если просмотр лучше отложить, предложи ровно три очень лёгких действия, к которым я могу приступить прямо сейчас, чтобы выйти из прокрастинации. Сделай их конкретными, короткими и выполнимыми за 5–15 минут.',
      'Поддерживай меня без стыда и морализаторства. Если ссылку нельзя открыть, честно скажи об этом и опирайся только на название.',
    'В конце добавь блок <tabguard-focus-options> с JSON вида {"options":[{"title":"...","description":"..."}]} для тех же трёх действий.',
    ].join('\n') + contextBlock;
  }
  return [
    `Я только что чуть не открыл страницу из категории «${categoryName}»: «${title || url}».`,
    'Я поставил себе защиту, чтобы не уходить в автоматическую прокрастинацию.',
    'Пожалуйста, мягко и без осуждения помоги вернуться к делу прямо сейчас, без стыда и морализаторства.',
    'Сначала коротко признай момент переключения, затем предложи ровно три конкретных лёгких действия на ближайшие 5–15 минут. Они должны быть достаточно маленькими, чтобы начать сразу, и по возможности опираться на карточки Kanban.',
    'Заверши одним вопросом, с какого действия мне начать прямо сейчас.',
    'В конце добавь блок <tabguard-focus-options> с JSON вида {"options":[{"title":"...","description":"..."}]} для тех же трёх действий.',
  ].join('\n') + contextBlock;
}

export function chatgptUrl(prompt) {
  return `https://chatgpt.com/?q=${encodeURIComponent(prompt)}`;
}

export function chatgptHandoffUrl(nonce) {
  return `https://chatgpt.com/?tabguard_handoff=${encodeURIComponent(nonce)}`;
}
