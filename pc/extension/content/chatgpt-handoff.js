(function installTabGuardHandoff() {
  const params = new URLSearchParams(window.location.search);
  const nonce = params.get('tabguard_handoff');
  if (!nonce || sessionStorage.getItem(`tabguard-handoff:${nonce}`)) return;

  const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

  function visible(element) {
    if (!element || element.disabled) return false;
    const style = window.getComputedStyle(element);
    return style.display !== 'none' && style.visibility !== 'hidden' && element.getClientRects().length > 0;
  }

  function findComposer() {
    const candidates = [
      document.querySelector('textarea[data-id]'),
      document.querySelector('textarea[placeholder*="Message"]'),
      document.querySelector('textarea[placeholder*="Сообщение"]'),
      document.querySelector('[contenteditable="true"][role="textbox"]'),
      document.querySelector('[contenteditable="true"]'),
      document.querySelector('textarea'),
    ];
    return candidates.find(visible) || null;
  }

  function setComposerValue(element, value) {
    if (element instanceof HTMLTextAreaElement || element instanceof HTMLInputElement) {
      const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set;
      if (setter) setter.call(element, value);
      else element.value = value;
    } else {
      element.textContent = value;
    }
    element.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function findSendButton() {
    const candidates = [
      document.querySelector('[data-testid="send-button"]'),
      document.querySelector('button[aria-label*="Send"]'),
      document.querySelector('button[aria-label*="Отправить"]'),
      document.querySelector('form button[type="submit"]'),
    ];
    return candidates.find(visible) || null;
  }

  function assistantElements() {
    return [...document.querySelectorAll('[data-message-author-role="assistant"], .markdown, [class*="agent-turn"]')]
      .filter(visible);
  }

  function parseActions(answer) {
    const lines = String(answer || '').split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    const numbered = lines.filter((line) => /^(?:\d+|[•*-])[.)]?\s+/.test(line));
    const candidates = (numbered.length ? numbered : lines).map((line) => line.replace(/^(?:\d+|[•*-])[.)]?\s+/, ''));
    return candidates.slice(0, 3).map((line) => line.replace(/^\*+|\*+$/g, '').replace(/\s+/g, ' ').trim().slice(0, 200)).filter((title) => title.length >= 3);
  }

  async function waitForAnswer(before) {
    const deadline = Date.now() + 90000;
    let last = '';
    let stableReads = 0;
    while (Date.now() < deadline) {
      const candidates = assistantElements().filter((element) => !before.has(element));
      const text = candidates.map((element) => element.innerText || element.textContent || '').join('\n').trim();
      if (text && text === last) stableReads += 1;
      else stableReads = 0;
      last = text;
      if (text && stableReads >= 3) return text;
      await wait(500);
    }
    return last;
  }

  function showActionPanel(actions, answer, sourceUrl) {
    if (!actions.length || document.getElementById('tabguard-focus-actions')) return;
    const style = document.createElement('style');
    style.id = 'tabguard-focus-actions-style';
    style.textContent = '#tabguard-focus-actions{position:fixed;right:20px;bottom:20px;z-index:2147483647;width:min(380px,calc(100vw - 40px));padding:16px;border:1px solid #d6d9e4;border-radius:14px;background:#fff;color:#202334;box-shadow:0 12px 36px #0002;font:14px/1.45 system-ui,sans-serif}#tabguard-focus-actions h3{margin:0 0 8px;font-size:16px}#tabguard-focus-actions p{margin:0 0 10px;color:#667085}#tabguard-focus-actions .tabguard-action{display:flex;gap:8px;align-items:flex-start;margin:8px 0;padding:8px;border:1px solid #e4e7ec;border-radius:9px}#tabguard-focus-actions .tabguard-action span{flex:1}#tabguard-focus-actions button{border:0;border-radius:7px;padding:6px 8px;background:#4338ca;color:#fff;cursor:pointer}#tabguard-focus-actions button:disabled{opacity:.55;cursor:default}';
    document.documentElement.appendChild(style);
    const panel = document.createElement('aside');
    panel.id = 'tabguard-focus-actions';
    const heading = document.createElement('h3');
    heading.textContent = 'Можно начать с малого';
    panel.appendChild(heading);
    const intro = document.createElement('p');
    intro.textContent = 'Выберите действие, которое стоит добавить в выбранную колонку Kanban.';
    panel.appendChild(intro);
    actions.forEach((title) => {
      const row = document.createElement('div');
      row.className = 'tabguard-action';
      const text = document.createElement('span');
      text.textContent = title;
      const button = document.createElement('button');
      button.textContent = 'В Kanban';
      button.addEventListener('click', async () => {
        button.disabled = true;
        button.textContent = 'Добавляю…';
        const result = await chrome.runtime.sendMessage({
          type: 'tabguard-create-card',
          action: { title, description: title },
          sourceUrl,
        }).catch(() => ({ ok: false, error: 'Не удалось связаться с TabGuard' }));
        button.textContent = result?.ok ? 'Добавлено' : (result?.error || 'Ошибка');
        if (!result?.ok) button.disabled = false;
      });
      row.append(text, button);
      panel.appendChild(row);
    });
    document.body.appendChild(panel);
  }

  async function requestHandoff() {
    return chrome.runtime.sendMessage({ type: 'tabguard-get-handoff', nonce });
  }

  async function run() {
    const handoff = await requestHandoff();
    if (!handoff?.ok || !handoff.prompt) return;
    chrome.runtime.sendMessage({ type: 'tabguard-handoff-status', nonce, status: 'opened' });
    const deadline = Date.now() + 20000;
    const beforeAnswers = new Set(assistantElements());
    let composer = null;
    let sendButton = null;
    while (Date.now() < deadline) {
      composer = findComposer();
      if (composer) {
        setComposerValue(composer, handoff.prompt);
        sendButton = findSendButton();
        if (sendButton) break;
      }
      await wait(250);
    }
    if (!composer || !sendButton) {
      chrome.runtime.sendMessage({ type: 'tabguard-handoff-status', nonce, status: 'fallback' });
      return;
    }
    sendButton.click();
    sessionStorage.setItem(`tabguard-handoff:${nonce}`, 'sent');
    chrome.runtime.sendMessage({ type: 'tabguard-handoff-status', nonce, status: 'sent' });
    const cleanUrl = new URL(window.location.href);
    cleanUrl.searchParams.delete('tabguard_handoff');
    window.history.replaceState({}, document.title, cleanUrl.href);
    const answer = await waitForAnswer(beforeAnswers);
    showActionPanel(parseActions(answer), answer, handoff.sourceUrl);
  }

  run().catch(() => chrome.runtime.sendMessage({ type: 'tabguard-handoff-status', nonce, status: 'failed' }));
}());
