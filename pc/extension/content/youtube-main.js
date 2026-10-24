// Runs in the YouTube page itself (MAIN world): reads the uploader's category of the open video
// from the player data and hands it to youtube-bridge.js. YouTube switches videos without
// reloading, so the check repeats on its navigation event and once a second.
(() => {
  let reported = '';

  function playerGenre(videoId) {
    const live = document.getElementById('movie_player')?.getPlayerResponse?.();
    for (const response of [live, window.ytInitialPlayerResponse]) {
      // Data left over from the previous video must not be reported for the new one.
      if (response?.videoDetails?.videoId !== videoId) continue;
      const genre = response.microformat?.playerMicroformatRenderer?.category;
      if (genre) return genre;
    }
    return null;
  }

  function report() {
    if (location.pathname !== '/watch') return;
    const videoId = new URLSearchParams(location.search).get('v');
    const genre = videoId && playerGenre(videoId);
    if (!genre || reported === `${videoId}|${genre}`) return;
    reported = `${videoId}|${genre}`;
    window.postMessage({ source: 'tabguard-youtube', genre }, location.origin);
  }

  document.addEventListener('yt-navigate-finish', report);
  setInterval(report, 1000);
  report();
})();
