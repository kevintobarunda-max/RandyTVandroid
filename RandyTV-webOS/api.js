/* ────────────────────────────────────────────────────────────────
   api.js  –  Cliente Xtream + TMDB para RandyTV webOS
   Compatible ES5 / Chromium 53
──────────────────────────────────────────────────────────────── */

var API = (function () {
  'use strict';

  var SERVER   = 'http://tv.streamid.tv:8080';
  var USERNAME = 'cristobalignacio6834';
  var PASSWORD = 'Ajud4CU6dH3Q';
  var TMDB_KEY = 'eb55a71c3a8f3526e1a448ba8b77bc30';

  /* ── Proxy CORS local (Mac en misma red) ──────────────────────
     El proxy corre en tu Mac: npm install -g local-cors-proxy
     lcp --proxyUrl http://tv.streamid.tv:8080 --port 8010
     Cambia PROXY_IP por la IP de tu Mac (ej: 172.20.10.14)
  ─────────────────────────────────────────────────────────────── */
  var PROXY = 'http://172.20.10.14:8010';

  /* ── URLs ─────────────────────────────────────────────────── */
  function apiUrl(action) {
    return PROXY + '/player_api.php?username=' + USERNAME +
           '&password=' + PASSWORD + '&action=' + action;
  }
  function liveUrl(streamId) {
    /* Streams de video van directo al servidor (no pasan por proxy) */
    return SERVER + '/live/' + USERNAME + '/' + PASSWORD + '/' + streamId + '.m3u8';
  }
  function vodUrl(streamId, ext) {
    return SERVER + '/movie/' + USERNAME + '/' + PASSWORD + '/' + streamId + '.' + (ext || 'mp4');
  }
  function seriesUrl(episodeId, ext) {
    return SERVER + '/series/' + USERNAME + '/' + PASSWORD + '/' + episodeId + '.' + (ext || 'mp4');
  }

  /* ── Fetch genérico (XMLHttpRequest para Chromium 53) ──────── */
  function fetchJSON(url, callback) {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', url, true);
    xhr.setRequestHeader('User-Agent', 'RandyTV/1.0');
    xhr.timeout = 20000;
    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4) return;
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          var data = JSON.parse(xhr.responseText);
          callback(null, data);
        } catch (e) {
          callback('JSON parse error: ' + e.message, null);
        }
      } else {
        callback('HTTP ' + xhr.status, null);
      }
    };
    xhr.ontimeout = function () { callback('timeout', null); };
    xhr.onerror  = function () { callback('network error', null); };
    xhr.send();
  }

  /* ── Filtros de contenido adulto ──────────────────────────── */
  var ADULT_KW = ['xxx','adult','porn','erotic','playboy','hustle',
                  'penthouse','brazzers','18+','sexy','venus',
                  'desnud','vivid'];
  function isAdult(name) {
    var l = name.toLowerCase();
    for (var i = 0; i < ADULT_KW.length; i++) {
      if (l.indexOf(ADULT_KW[i]) !== -1) return true;
    }
    return false;
  }

  /* ── Filtros especiales canales ────────────────────────────── */
  function is2MB(stream) {
    var l = stream.name.toLowerCase();
    return l.indexOf('2mb') !== -1 || l.indexOf('| 2') !== -1 ||
           l.indexOf('|2') !== -1  || l.indexOf(' 2 mb') !== -1 ||
           l.indexOf('[2]') !== -1 || l.indexOf('(2)') !== -1  ||
           l.indexOf('2 mb') !== -1|| l.indexOf('sd|') !== -1  ||
           l.indexOf('|sd') !== -1 || l.indexOf(' sd ') !== -1;
  }
  function is24h(stream) {
    var l = stream.name.toLowerCase();
    return l.indexOf('24') !== -1 || l.indexOf('24h') !== -1 ||
           l.indexOf('24/7') !== -1 || l.indexOf('24 h') !== -1;
  }
  function isSimpsons(stream) {
    return stream.name.toLowerCase().indexOf('simpson') !== -1;
  }

  /* ── Helpers rating ────────────────────────────────────────── */
  function parseRating(stream) {
    var r = parseFloat(stream.rating);
    if (!isNaN(r) && r > 0) return r;
    r = parseFloat(stream.rating_5based);
    if (!isNaN(r) && r > 0) return r * 2;
    return null;
  }
  function imgUrl(stream) {
    return stream.stream_icon || stream.cover || '';
  }

  /* ── Endpoints Xtream ──────────────────────────────────────── */
  var _liveCategories  = [];
  var _vodCategories   = [];
  var _liveStreams      = [];
  var _vodStreams       = [];
  var _seriesList      = [];
  var _seriesCategories= [];
  var _loadStep        = 0;
  var _totalSteps      = 4;  /* live cats + live streams + vod cats + vod streams */

  function _tick(onProgress) {
    _loadStep++;
    if (onProgress) onProgress(_loadStep / _totalSteps);
  }

  /* Carga en 2 fases: primero vivo (crítico), luego VOD en paralelo */
  function loadAll(onProgress, onLiveDone, onAllDone) {
    /* Fase 1 – live */
    var liveCatDone = false, liveStreamDone = false;
    function checkLive() {
      if (liveCatDone && liveStreamDone) { if (onLiveDone) onLiveDone(); loadVod(); }
    }
    fetchJSON(apiUrl('get_live_categories'), function (err, data) {
      _liveCategories = (err || !Array.isArray(data)) ? [] : data.filter(function (c) { return !isAdult(c.category_name); });
      liveCatDone = true; _tick(onProgress); checkLive();
    });
    fetchJSON(apiUrl('get_live_streams'), function (err, data) {
      _liveStreams = (err || !Array.isArray(data)) ? [] : data;
      liveStreamDone = true; _tick(onProgress); checkLive();
    });

    /* Fase 2 – VOD + series (en background) */
    var vodCatDone = false, vodStreamDone = false;
    function checkVod() {
      if (vodCatDone && vodStreamDone) { if (onAllDone) onAllDone(); }
    }
    function loadVod() {
      fetchJSON(apiUrl('get_vod_categories'), function (err, data) {
        _vodCategories = (err || !Array.isArray(data)) ? [] : data.filter(function (c) { return !isAdult(c.category_name); });
        vodCatDone = true; _tick(onProgress); checkVod();
      });
      fetchJSON(apiUrl('get_vod_streams'), function (err, data) {
        _vodStreams = (err || !Array.isArray(data)) ? [] : data;
        vodStreamDone = true; _tick(onProgress); checkVod();
      });
    }
  }

  /* ── Getters de listas filtradas ──────────────────────────── */
  function getLiveCategories() { return _liveCategories; }
  function getVodCategories()  { return _vodCategories; }

  function getLive2MB() {
    return _liveStreams.filter(function (s) { return !isAdult(s.name) && is2MB(s); })
      .sort(function (a, b) { return a.name.toLowerCase().localeCompare(b.name.toLowerCase()); });
  }
  function getLive24h() {
    return _liveStreams.filter(function (s) { return !isAdult(s.name) && is24h(s); })
      .sort(function (a, b) { return a.name.toLowerCase().localeCompare(b.name.toLowerCase()); });
  }
  function getLiveSimpsons() {
    return _liveStreams.filter(function (s) { return !isAdult(s.name) && isSimpsons(s); });
  }
  function getLiveByCategory(catId) {
    return _liveStreams.filter(function (s) { return s.category_id === catId && !isAdult(s.name); });
  }
  function getVodByCategory(catId, search) {
    var r = _vodStreams.filter(function (s) { return !isAdult(s.name) && (!catId || s.category_id === catId); });
    if (search) { var q = search.toLowerCase(); r = r.filter(function (s) { return s.name.toLowerCase().indexOf(q) !== -1; }); }
    r.sort(function (a, b) { return (parseRating(b) || 0) - (parseRating(a) || 0); });
    return r.slice(0, 120);
  }

  /* ── URLs públicas ─────────────────────────────────────────── */
  return {
    liveUrl: liveUrl,
    vodUrl: vodUrl,
    seriesUrl: seriesUrl,
    loadAll: loadAll,
    getLiveCategories: getLiveCategories,
    getVodCategories: getVodCategories,
    getLive2MB: getLive2MB,
    getLive24h: getLive24h,
    getLiveSimpsons: getLiveSimpsons,
    getLiveByCategory: getLiveByCategory,
    getVodByCategory: getVodByCategory,
    parseRating: parseRating,
    imgUrl: imgUrl,
    isAdult: isAdult,
    TMDB_KEY: TMDB_KEY
  };
})();
