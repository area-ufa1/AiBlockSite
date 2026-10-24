// YouTube video categories ("genre" chosen by the uploader) that count as entertainment.
import { extractHost } from './text.js';

/** As YouTube reports them (English, independent of the interface language). */
const ENTERTAINMENT_GENRES = new Set([
  'Entertainment', 'Comedy', 'Gaming', 'People & Blogs', 'Film & Animation', 'Pets & Animals', 'Music',
]);

export function isEntertainmentGenre(genre) {
  return typeof genre === 'string' && ENTERTAINMENT_GENRES.has(genre.trim());
}

function onYoutube(url) {
  const host = extractHost(url) ?? '';
  return ['youtube.com', 'youtu.be'].some((h) => host === h || host.endsWith(`.${h}`));
}

/** The enabled «YouTube: развлечения» category if this YouTube video's genre is entertainment. */
export function matchGenre(tab, categories) {
  if (!isEntertainmentGenre(tab.genre) || !onYoutube(tab.url)) return null;
  const category = categories.find((c) => c.enabled && c.bundledList === 'youtube-fun');
  return category ? { categoryId: category.id, categoryName: category.name, source: 'genre' } : null;
}
