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

  async function requestHandoff() {
    return chrome.runtime.sendMessage({ type: 'tabguard-get-handoff', nonce });
  }

  async function run() {
    const handoff = await requestHandoff();
    if (!handoff?.ok || !handoff.prompt) return;
    chrome.runtime.sendMessage({ type: 'tabguard-handoff-status', nonce, status: 'opened' });
    const deadline = Date.now() + 20000;
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
  }

  run().catch(() => chrome.runtime.sendMessage({ type: 'tabguard-handoff-status', nonce, status: 'failed' }));
}());
