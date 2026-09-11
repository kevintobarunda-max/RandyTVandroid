/* ────────────────────────────────────────────────────────────────
   live.js  –  Pantalla TV en Vivo y Simpsons 24h
   Compatible ES5 / Chromium 53
──────────────────────────────────────────────────────────────── */

var LIVE = (function () {
  'use strict';

  var _onHome,    /* callback → volver a Home */
      _onPlay;    /* callback(stream, channelList) */

  var _selectedCat = '2mb';
  var _mode        = 'live';   /* 'live' | 'simpsons' */
  var _catScroll   = 0;        /* índice de inicio visible en catbar */
  var _MAX_CATS    = 12;       /* cuántas categorías mostrar a la vez */

  var _elTitle, _elCatbar, _elGrid, _elHomeBtn, _elSearch;

  /* ── Inicialización ───────────────────────────────────────── */
  function init(onHome, onPlay) {
    _onHome = onHome;
    _onPlay = onPlay;

    _elTitle   = document.getElementById('list-title');
    _elCatbar  = document.getElementById('catbar');
    _elGrid    = document.getElementById('content-grid');
    _elHomeBtn = document.getElementById('list-home-btn');
    _elSearch  = document.getElementById('search-input');

    _elHomeBtn.addEventListener('click', function () {
      if (_onHome) _onHome();
    });

    _elSearch.addEventListener('input', function () { _renderGrid(); });
  }

  /* ── Mostrar ──────────────────────────────────────────────── */
  function show(mode) {
    _mode = mode || 'live';
    _selectedCat = (_mode === 'simpsons') ? 'simpsons' : '2mb';
    _elSearch.value = '';

    _elTitle.textContent = (_mode === 'simpsons') ? 'SIMPSONS 24H' : 'TV EN VIVO';
    _elTitle.style.color = (_mode === 'simpsons') ? '#FFD700' : '#00E676';

    _renderCatbar();
    _renderGrid();

    var screen = document.getElementById('screen-list');
    NAV.setScope(screen, function () { if (_onHome) _onHome(); }, null);
    setTimeout(function () {
      /* Foco al primer canal */
      var first = _elGrid.querySelector('.focusable');
      if (first) NAV.focus(first); else NAV.focus(_elHomeBtn);
    }, 60);
  }

  /* ── Catbar ───────────────────────────────────────────────── */
  function _renderCatbar() {
    _elCatbar.innerHTML = '';
    if (_mode === 'simpsons') return; /* Sin categorías en modo Simpsons */

    var cats = [
      { id: '2mb',  name: '2MB'      },
      { id: '24h',  name: '24 Horas' }
    ].concat(API.getLiveCategories());

    var visible = cats.slice(_catScroll, _catScroll + _MAX_CATS);

    for (var i = 0; i < visible.length; i++) {
      (function (cat) {
        var chip = document.createElement('div');
        chip.className = 'cat-chip focusable' + (cat.id === _selectedCat ? ' selected' : '');
        chip.textContent = cat.name || cat.category_name || cat.id;
        chip.addEventListener('click', function () {
          _selectedCat = cat.id;
          _renderCatbar();
          _renderGrid();
          /* volver foco al grid */
          setTimeout(function () {
            var first = _elGrid.querySelector('.focusable');
            if (first) NAV.focus(first);
          }, 40);
        });
        _elCatbar.appendChild(chip);
      })(visible[i]);
    }
  }

  /* ── Grid de canales ──────────────────────────────────────── */
  function _renderGrid() {
    _elGrid.className = 'grid'; /* 7 columnas (canales cuadrados) */
    _elGrid.innerHTML = '';

    var streams;
    if (_mode === 'simpsons') {
      streams = API.getLiveSimpsons();
    } else if (_selectedCat === '2mb') {
      streams = API.getLive2MB();
    } else if (_selectedCat === '24h') {
      streams = API.getLive24h();
    } else {
      streams = API.getLiveByCategory(_selectedCat);
    }

    /* Filtro de búsqueda */
    var q = (_elSearch.value || '').toLowerCase().trim();
    if (q) {
      streams = streams.filter(function (s) {
        return s.name.toLowerCase().indexOf(q) !== -1;
      });
    }

    /* Limitar a 150 para no saturar el DOM */
    streams = streams.slice(0, 150);

    /* Construir lista para zapping */
    var channelList = streams.slice();

    for (var i = 0; i < streams.length; i++) {
      (function (stream, list) {
        var card = _makeCard(stream);
        card.addEventListener('click', function () {
          if (_onPlay) _onPlay(stream, list);
        });
        _elGrid.appendChild(card);
      })(streams[i], channelList);
    }

    if (streams.length === 0) {
      var empty = document.createElement('div');
      empty.style.cssText = 'grid-column:1/-1;color:rgba(255,255,255,0.3);font-size:18px;padding:40px;text-align:center;';
      empty.textContent = 'Sin resultados';
      _elGrid.appendChild(empty);
    }
  }

  function _makeCard(stream) {
    var card  = document.createElement('div');
    card.className = 'card focusable';

    var thumb = document.createElement('div');
    thumb.className = 'card-thumb sq';

    var img = document.createElement('img');
    img.alt = stream.name;
    img.src = API.imgUrl(stream) || '';
    /* Fallback si la imagen no carga */
    img.onerror = function () { this.style.display = 'none'; };
    /* Lazy load: solo asignar src cuando está cerca de ser visible */
    thumb.appendChild(img);
    card.appendChild(thumb);

    var name = document.createElement('div');
    name.className = 'card-name';
    name.textContent = stream.name;
    card.appendChild(name);

    return card;
  }

  return { init: init, show: show };
})();
