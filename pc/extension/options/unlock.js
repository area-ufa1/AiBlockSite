// Protection UI shared by the options page: status line, the unlock dialog (pause or partner PIN).
import { remainingCountdownMs, startCountdown, cancelCountdown, tryPin, isUnlocked, phraseMatches } from '../src/core/lock.js';
import { claudeUrl } from '../src/core/prompt.js';

const $ = (id) => document.getElementById(id);
const WANT_TO_DISABLE_PROMPT =
  'Мне хочется отключить защиту, которую я сам себе поставил от нежелательных сайтов и бесконечной ленты. ' +
  'Пожалуйста, мягко и без осуждения помоги разобраться, что со мной сейчас происходит, и справиться с этим желанием: ' +
  'предложи пару конкретных действий на ближайшие 10 минут.';

const minutes = (ms) => Math.ceil(ms / 60_000);

export function createUnlockUi(store) {
  let timer = null;

  async function renderStatus() {
    const now = Date.now();
    const lock = await store.getLock(now);
    const open = lock.mode !== null && isUnlocked(lock, now);
    const left = remainingCountdownMs(lock, now);
    let text;
    if (lock.mode === null) text = 'Защита от отключения не настроена';
    else if (open) text = `Изменения открыты ещё ${minutes(lock.unlockedUntil - now)} мин`;
    else if (left !== null) text = `Пауза: изменения откроются через ${minutes(left)} мин`;
    else text = lock.mode === 'pause' ? `Защита: пауза ${lock.pauseMinutes} мин` : 'Защита: PIN напарника';
    $('lock-status').textContent = text;
    $('weaken').hidden = lock.mode === null || open;
    $('weaken-wrap').hidden = $('weaken').hidden;
    $('change-mode').hidden = !(lock.mode === null || open);
    $('countdown').textContent = left !== null ? `Осталось ${minutes(left)} мин` : '';
    $('start-countdown').hidden = left !== null;
    $('phrase-label').hidden = left !== null;
    $('cancel-countdown').hidden = left === null;
    if (open && $('unlock').open) $('unlock').close();
    return lock;
  }

  async function show() {
    const lock = await store.getLock();
    $('unlock-pause').hidden = lock.mode !== 'pause';
    $('unlock-partner').hidden = lock.mode !== 'partner';
    $('pin-error').textContent = '';
    $('pin-input').value = '';
    $('phrase').value = '';
    $('start-countdown').disabled = true;
    if (!$('unlock').open) $('unlock').showModal();
    await renderStatus();
  }

  $('talk').href = claudeUrl(WANT_TO_DISABLE_PROMPT);
  $('weaken').addEventListener('click', show);
  // The phrase is typed, not pasted or dropped in.
  for (const type of ['paste', 'drop']) $('phrase').addEventListener(type, (e) => e.preventDefault());
  $('phrase').addEventListener('input', () => {
    $('start-countdown').disabled = !phraseMatches($('phrase').value);
  });
  $('unlock-close').addEventListener('click', () => $('unlock').close());
  $('start-countdown').addEventListener('click', async () => {
    if (!phraseMatches($('phrase').value)) return;
    await store.saveLock(startCountdown(await store.getLock(), Date.now()));
    await renderStatus();
  });
  $('cancel-countdown').addEventListener('click', async () => {
    await store.saveLock(cancelCountdown(await store.getLock()));
    await renderStatus();
  });
  $('pin-ok').addEventListener('click', async () => {
    const now = Date.now();
    const lock = await store.getLock(now);
    const { state, ok } = await tryPin(lock, $('pin-input').value, now);
    await store.saveLock(state);
    if (!ok) {
      $('pin-error').textContent = now < state.pinLockedUntil
        ? `Слишком много попыток. Попробуйте через ${minutes(state.pinLockedUntil - now)} мин`
        : 'Неверный PIN';
    }
    await renderStatus();
  });

  timer = setInterval(renderStatus, 5000);
  return { show, renderStatus, stop: () => clearInterval(timer) };
}
