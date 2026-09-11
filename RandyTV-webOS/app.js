/* ────────────────────────────────────────────────────────────────
   app.js  –  Orquestador principal de RandyTV webOS
   Gestiona pantallas: splash → home → live/vod → player → home
   Compatible ES5 / Chromium 53
──────────────────────────────────────────────────────────────── */

(function () {
  'use strict';

  /* ── Pantallas ────────────────────────────────────────────── */
  var SCREENS = ['splash', 'home', 'list', 'player'];
  var _current = 'splash';
  var _prevList = 'home'; /* para saber adónde volver desde el player */

  function showScreen(name) {
    for (var i = 0; i < SCREENS.length; i++) {
      var el = document.getElementById('screen-' + SCREENS[i]);
      if (el) {
        if (SCREENS[i] === name) el.classList.add('active');
        else el.classList.remove('active');
      }
    }
    _current = name;
  }

  /* ── Navegación ──────────────────────────────────────────── */
  function goHome() {
    PLAYER.stop();
    showScreen('home');
    HOME.show();
  }
  function goLive(mode) {
    _prevList = 'live:' + (mode || 'live');
    showScreen('list');
    LIVE.show(mode || 'live');
  }
  function goVod() {
    _prevList = 'vod';
    showScreen('list');
    VOD.show();
  }
  function goPlayer() {
    showScreen('player');
  }
  function goBack() {
    PLAYER.stop();
    /* Volver a donde estabamos */
    if (_prevList === 'vod') { goVod(); }
    else if (_prevList && _prevList.indexOf('live') === 0) {
      var mode = _prevList.split(':')[1] || 'live';
      goLive(mode);
    } else {
      goHome();
    }
  }

  /* ── Wiring ──────────────────────────────────────────────── */
  function wireAll() {
    NAV.init();
    SPLASH.init();

    /* Home */
    HOME.init(function (action) {
      if (action === 'live')     { goLive('live');     }
      else if (action === 'vod') { goVod();            }
      else if (action === 'simpsons') { goLive('simpsons'); }
      else if (action === 'series') {
        /* Series: por ahora redirige a VOD con mensaje */
        goVod();
      }
    });

    /* Live */
    LIVE.init(
      function () { goHome(); },
      function (stream, channelList) {
        var idx = channelList.indexOf(stream);
        PLAYER.playLive(stream, channelList, idx >= 0 ? idx : 0);
        goPlayer();
      }
    );

    /* VOD */
    VOD.init(
      function () { goHome(); },
      function (stream) {
        PLAYER.playVod(stream);
        goPlayer();
      }
    );

    /* Player */
    PLAYER.init(function () { goBack(); });
  }

  /* ── Carga de datos ──────────────────────────────────────── */
  function startLoad() {
    var liveDone = false;

    API.loadAll(
      /* onProgress */
      function (p) {
        SPLASH.setProgress(p);
      },
      /* onLiveDone: ir a home ya con TV disponible */
      function () {
        if (!liveDone) {
          liveDone = true;
          SPLASH.setProgress(0.5);
          SPLASH.setMsg('TV en vivo lista. Cargando películas...');
          setTimeout(function () {
            showScreen('home');
            HOME.show();
          }, 400);
        }
      },
      /* onAllDone */
      function () {
        SPLASH.setProgress(1);
        /* Si por alguna razón sigue en splash, pasar a home */
        if (_current === 'splash') {
          showScreen('home');
          HOME.show();
        }
      }
    );

    /* Safety fallback: si a los 10s sigue en splash, pasar igual */
    setTimeout(function () {
      if (_current === 'splash') {
        showScreen('home');
        HOME.show();
      }
    }, 10000);
  }

  /* ── Boot ────────────────────────────────────────────────── */
  function boot() {
    try {
      wireAll();
      showScreen('splash');
      SPLASH.setProgress(0);
      startLoad();
    } catch(e) {
      document.body.style.background = '#000';
      document.body.style.color = '#fff';
      document.body.style.fontSize = '24px';
      document.body.style.padding = '40px';
      document.body.innerHTML = '<h1 style="color:#f44">ERROR AL ARRANCAR</h1><pre style="color:#ff0;font-size:18px;white-space:pre-wrap">' + e.message + '\n' + e.stack + '</pre>';
    }
  }

  /* Arrancar cuando el DOM esté listo */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }

})();
