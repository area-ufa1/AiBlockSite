// Text helpers shared by the matcher; same rules as the Android app (core/.../text/Text.kt).

const NON_WORD = /[^\p{L}\p{N}]+/u;
const SCHEME = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//;
const HOST = /^[\p{L}\p{N}-]+(\.[\p{L}\p{N}-]+)+$/u;

export function normalize(s) {
  return s.toLowerCase().replaceAll('ё', 'е').trim();
}

export function tokenize(s) {
  return normalize(s).split(NON_WORD).filter((t) => t.length > 0);
}

/** Host of `url` (scheme optional), lowercased, without `www.`, port or userinfo; null if it is not a host. */
export function extractHost(url) {
  const rest = url.trim().replace(SCHEME, '');
  const authority = rest.split('/')[0].split('?')[0].split('#')[0];
  const afterUser = authority.slice(authority.lastIndexOf('@') + 1);
  let host = normalize(afterUser.split(':')[0]);
  if (host.startsWith('www.')) host = host.slice(4);
  return HOST.test(host) ? host : null;
}
