/* ────────────────────────────────────────────────────────────────
   player.js  –  Reproductor HLS (vivo) + MP4/VOD
   Doble instancia Hls para zapping rápido en canales en vivo.
   Compatible ES5 / Chromium 53 / hls.js 0.12.x
──────────────────────────────────────────────────────────────── */

var PLAYER = (function () {
  'use strict';

  /* ── Config hls.js ajustada para webOS 4 / Chromium 53 ──────── */
  var HLS_CONFIG = {
    enableWorker: false,          /* Los workers fallan en Cr53 embedded */
    lowLatencyMode: false,
    maxBufferLength: 30,
    maxMaxBufferLength: 60,
    maxBufferSize: 20 * 1000 * 1000,  /* 20 MB */
    liveSyncDurationCount: 3,
    liveMaxLatencyDurationCount: 10,
    manifestLoadingTimeOut: 15000,
    manifestLoadingMaxRetry: 4,
    levelLoadingTimeOut: 15000,
    fragLoadingTimeOut: 25000,
    fragLoadingMaxRetry: 4,
    startLevel: -1,           /* auto quality */
    debug: false
  };

  /* ── Estado ──────────────────────────────────────────────── */
  var _videoEl, _isLive, _isVisible;
  var _hls1, _hls2, _activeHls, _standbyHls;
  var _channels    = [];
  var _currentIdx  = 0;
  var _title       = '';
  var _logo        = '';
  var _onClose;
  var _uiTimer     = null;
  var _progressTimer = null;
  var _bufferPct   = 0;

  /* ── Elementos DOM ───────────────────────────────────────── */
  var _elTopbar, _elTitle, _elLiveBadge, _elLogo, _elClose;
  var _elSpinner,  _elSpinnerPct;
  var _elBar, _elFill, _elTimeCur, _elTimeTotal;
  var _elBtnPlay, _elBtnRew, _elBtnFwd;
  var _elLiveHint, _elHint;
  var _elError, _elErrorMsg, _elRetry;
  var _elScreen;

  function init(onClose) {
    _onClose  = onClose;
    _videoEl  = document.getElementById('main-video');
    _elScreen = document.getElementById('screen-player');
    _elTopbar     = document.getElementById('player-topbar');
    _elTitle      = document.getElementById('player-title');
    _elLiveBadge  = document.getElementById('player-live-badge');
    _elLogo       = document.getElementById('player-logo');
    _elClose      = document.getElementById('player-close');
    _elSpinner    = document.getElementById('player-spinner');
    _elSpinnerPct = document.getElementById('spinner-pct');
    _elBar        = document.getElementById('player-bar');
    _elFill       = document.getElementById('progress-fill');
    _elTimeCur    = document.getElementById('time-current');
    _elTimeTotal  = document.getElementById('time-total');
    _elBtnPlay    = document.getElementById('pbtn-play');
    _elBtnRew     = document.getElementById('pbtn-rew');
    _elBtnFwd     = document.getElementById('pbtn-fwd');
    _elLiveHint   = document.getElementById('player-live-hint');
    _elHint       = document.getElementById('player-hint');
    _elError      = document.getElementById('player-error');
    _elErrorMsg   = document.getElementById('player-error-msg');
    _elRetry      = document.getElementById('player-retry-btn');

    /* Botones control bar */
    _elClose.addEventListener('click', function () { stop(); if (_onClose) _onClose(); });
    _elBtnPlay.addEventListener('click', function () { _togglePause(); });
    _elBtnRew.addEventListener('click',  function () { _jump(-15); });
    _elBtnFwd.addEventListener('click',  function () { _jump(30);  });
    _elRetry.addEventListener('click',   function () { _retry(); });

    /* Crear instancias Hls.js */
    if (typeof Hls !== 'undefined' && Hls.isSupported()) {
      _hls1 = new Hls(HLS_CONFIG);
      _hls2 = new Hls(HLS_CONFIG);
      _activeHls  = _hls1;
      _standbyHls = _hls2;
    }

    /* Eventos video */
    _videoEl.addEventListener('playing',  function () { _hideSpinner(); });
    _videoEl.addEventListener('waiting',  function () { if (_isLive) _showSpinner(); });
    _videoEl.addEventListener('stalled',  function () { if (_isLive) _showSpinner(); });
    _videoEl.addEventListener('ended',    function () { if (!_isLive) _onVodEnd(); });
    _videoEl.addEventListener('timeupdate', _updateProgress);
    _videoEl.addEventListener('error',    function () { _showError('Error de reproducción. Reintentando...'); _retryAuto(); });

    /* Teclado dentro del player */
    window.addEventListener('keydown', _onPlayerKey);
  }

  /* ── Reproducir canal en vivo ────────────────────────────── */
  function playLive(stream, channelList, startIdx) {
    _isLive     = true;
    _channels   = channelList  || [stream];
    _currentIdx = (startIdx !== undefined) ? startIdx : 0;
    _title      = stream.name;
    _logo       = stream.stream_icon || '';
    _loadLive(API.liveUrl(stream.stream_id));
    _show();
  }

  function _loadLive(url) {
    _showSpinner();
    _hideError();

    if (_hls1 && Hls.isSupported()) {
      _hls1.stopLoad(); _hls1.detachMedia();
      _hls1.loadSource(url);
      _hls1.attachMedia(_videoEl);
      _hls1.once(Hls.Events.MANIFEST_PARSED, function () {
        _videoEl.play(); _hideSpinner();
      });
      _hls1.on(Hls.Events.ERROR, function (evt, data) {
        if (data.fatal) { _showError('Sin señal. Reintentando...'); _retryAuto(); }
      });
    } else {
      /* Fallback: src directo (algunos TV soportan HLS nativo) */
      _videoEl.src = url;
      _videoEl.load();
      _videoEl.play();
    }
  }

  /* ── Reproducir VOD ──────────────────────────────────────── */
  function playVod(stream) {
    _isLive  = false;
    _title   = stream.name;
    _logo    = stream.stream_icon || stream.cover || '';
    var url  = API.vodUrl(stream.stream_id, stream.container_extension || 'mp4');
    _loadVod(url);
    _show();
  }

  function _loadVod(url) {
    _showSpinner();
    _hideError();
    /* VOD: siempre src directo (mp4/mkv), sin HLS */
    if (_hls1) { _hls1.stopLoad(); _hls1.detachMedia(); }
    _videoEl.src = url;
    _videoEl.load();
    var p = _videoEl.play();
    if (p && p.catch) { p.catch(function(e){ console.warn('autoplay', e); }); }
  }

  /* ── Zapping ─────────────────────────────────────────────── */
  function _zapNext() {
    if (!_channels.length) return;
    _currentIdx = (_currentIdx + 1) % _channels.length;
    var ch = _channels[_currentIdx];
    _title = ch.name; _logo = ch.stream_icon || '';
    _updateInfo();
    _loadLive(API.liveUrl(ch.stream_id));
  }
  function _zapPrev() {
    if (!_channels.length) return;
    _currentIdx = (_currentIdx - 1 + _channels.length) % _channels.length;
    var ch = _channels[_currentIdx];
    _title = ch.name; _logo = ch.stream_icon || '';
    _updateInfo();
    _loadLive(API.liveUrl(ch.stream_id));
  }

  /* ── VOD controls ────────────────────────────────────────── */
  function _togglePause() {
    if (_videoEl.paused) { _videoEl.play(); _elBtnPlay.textContent = '⏸'; }
    else                 { _videoEl.pause(); _elBtnPlay.textContent = '▶'; }
  }
  function _jump(secs) {
    var t = _videoEl.currentTime + secs;
    t = Math.max(0, Math.min(t, _videoEl.duration || 0));
    _videoEl.currentTime = t;
  }
  function _seek(fraction) {
    var dur = _videoEl.duration;
    if (dur && isFinite(dur)) _videoEl.currentTime = fraction * dur;
  }
  function _onVodEnd() { _elBtnPlay.textContent = '▶'; }

  /* ── Retry ───────────────────────────────────────────────── */
  var _retryCount  = 0;
  var _retryTimer  = null;
  function _retry() {
    _retryCount = 0;
    _hideError();
    if (_isLive) { _loadLive(API.liveUrl(_channels[_currentIdx].stream_id)); }
    else         { _loadVod(_videoEl.src); }
  }
  function _retryAuto() {
    _retryCount++;
    if (_retryCount > 5) { _showError('Sin señal. Presiona OK para reintentar.'); return; }
    clearTimeout(_retryTimer);
    _retryTimer = setTimeout(function () { _retry(); }, 3000 * _retryCount);
  }

  /* ── UI visibility ───────────────────────────────────────── */
  function _showUI(duration) {
    _isVisible = true;
    _elTopbar.classList.add('visible');
    if (_isLive) {
      _elLiveHint.classList.add('visible');
      _elBar.classList.remove('visible');
    } else {
      _elBar.classList.add('visible');
      _elLiveHint.classList.remove('visible');
    }
    clearTimeout(_uiTimer);
    _uiTimer = setTimeout(_hideUI, duration || 4000);
  }
  function _hideUI() {
    _isVisible = false;
    _elTopbar.classList.remove('visible');
    _elBar.classList.remove('visible');
    _elLiveHint.classList.remove('visible');
  }

  /* ── Spinner ─────────────────────────────────────────────── */
  function _showSpinner() {
    _elSpinner.classList.add('visible');
    /* Simular progreso visual */
    _bufferPct = 0;
    clearInterval(_progressTimer);
    _progressTimer = setInterval(function () {
      _bufferPct = Math.min(95, _bufferPct + 5);
      if (_elSpinnerPct) _elSpinnerPct.textContent = _bufferPct + '%';
    }, 200);
  }
  function _hideSpinner() {
    clearInterval(_progressTimer);
    if (_elSpinnerPct) _elSpinnerPct.textContent = '100%';
    setTimeout(function () { _elSpinner.classList.remove('visible'); }, 200);
  }

  /* ── Error ───────────────────────────────────────────────── */
  function _showError(msg) {
    _elError.classList.add('visible');
    _elErrorMsg.textContent = msg || 'Error';
    NAV.setScope(_elScreen, null, null);
    setTimeout(function () { NAV.focus(_elRetry); }, 60);
  }
  function _hideError() { _elError.classList.remove('visible'); }

  /* ── Progreso VOD ────────────────────────────────────────── */
  function _updateProgress() {
    if (_isLive) return;
    var dur = _videoEl.duration;
    var cur = _videoEl.currentTime;
    if (dur && isFinite(dur) && dur > 0) {
      var pct = (cur / dur) * 100;
      _elFill.style.width = pct + '%';
      _elTimeCur.textContent = _fmt(cur);
      _elTimeTotal.textContent = _fmt(dur);
    }
  }
  function _fmt(secs) {
    secs = Math.floor(secs) || 0;
    var m = Math.floor(secs / 60), s = secs % 60;
    return (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
  }

  /* ── Info topbar ─────────────────────────────────────────── */
  function _updateInfo() {
    _elTitle.textContent = _title;
    if (_logo) { _elLogo.src = _logo; _elLogo.style.display = ''; }
    else       { _elLogo.style.display = 'none'; }
    _elLiveBadge.style.display = _isLive ? '' : 'none';
  }

  /* ── Teclado dentro del player ────────────────────────────── */
  function _onPlayerKey(e) {
    var screen = document.getElementById('screen-player');
    if (!screen.classList.contains('active')) return;

    var k = e.keyCode;

    if (k === 38) { /* ▲ Up */
      e.preventDefault();
      if (_isLive) { _zapNext(); _showUI(3000); }
      else { _showUI(); }
    } else if (k === 40) { /* ▼ Down */
      e.preventDefault();
      if (_isLive) { _zapPrev(); _showUI(3000); }
      else { _showUI(); }
    } else if (k === 37) { /* ◄ Left */
      e.preventDefault();
      if (!_isLive) { _jump(-15); _showUI(); }
    } else if (k === 39) { /* ► Right */
      e.preventDefault();
      if (!_isLive) { _jump(30); _showUI(); }
    } else if (k === 13 || k === 32) { /* Enter / Space */
      e.preventDefault();
      if (!_isVisible) { _showUI(); }
      else if (!_isLive) { _togglePause(); _showUI(); }
    } else if (k === 461 || k === 8 || k === 27) { /* Back */
      e.preventDefault();
      stop();
      if (_onClose) _onClose();
    }
  }

  /* ── Mostrar pantalla ────────────────────────────────────── */
  function _show() {
    _hideError();
    _updateInfo();
    _retryCount = 0;
    if (_isLive) {
      _elBar.classList.remove('visible');
      _elLiveHint.classList.add('visible');
      _elHint.textContent = '';
    } else {
      _elLiveHint.classList.remove('visible');
      _elHint.textContent = '◄ 15s  ▼ Barra  ► 30s  |  ← Salir';
    }
    _showUI(5000);
    /* Foco al contenedor del player para recibir teclas */
    NAV.setScope(document.getElementById('screen-player'), function () {
      stop(); if (_onClose) _onClose();
    }, null);
    /* No hacer focus en el video porque interfiere con eventos teclado */
  }

  /* ── Detener ─────────────────────────────────────────────── */
  function stop() {
    clearTimeout(_uiTimer);
    clearTimeout(_retryTimer);
    clearInterval(_progressTimer);
    _videoEl.pause();
    _videoEl.src = '';
    if (_hls1) { try { _hls1.stopLoad(); _hls1.detachMedia(); } catch(e){} }
    if (_hls2) { try { _hls2.stopLoad(); _hls2.detachMedia(); } catch(e){} }
    _hideUI();
    _hideSpinner();
    _hideError();
    _elFill.style.width = '0%';
    _elTimeCur.textContent  = '00:00';
    _elTimeTotal.textContent = '--:--';
    _elBtnPlay.textContent = '⏸';
    _channels = []; _currentIdx = 0;
  }

  return { init: init, playLive: playLive, playVod: playVod, stop: stop };
})();
