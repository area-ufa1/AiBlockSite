// Offscreen document: the only place an MV3 extension can write to the clipboard without a page.
chrome.runtime.onMessage.addListener((msg) => {
  if (msg?.target !== 'offscreen' || msg.type !== 'copy') return;
  const area = document.getElementById('buffer');
  area.value = msg.text;
  area.select();
  document.execCommand('copy');
});
