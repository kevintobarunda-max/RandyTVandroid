/* ────────────────────────────────────────────────────────────────
   vod.js  –  Pantalla Películas (VOD)
   Compatible ES5 / Chromium 53
──────────────────────────────────────────────────────────────── */

var VOD = (function () {
  'use strict';

  var _onHome, _onPlay;
  var _selectedCat = '';

  var _elTitle, _elCatbar, _elGrid, _elHomeBtn, _elSearch;

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

  function show() {
    _elSearch.value = '';
    _elTitle.textContent = 'PELÍCULAS';
    _elTitle.style.color = '#00E676';

    var cats = API.getVodCategories();
    _selectedCat = cats.length ? cats[0].category_id : '';

    _renderCatbar();
    _renderGrid();

    var screen = document.getElementById('screen-list');
    NAV.setScope(screen, function () { if (_onHome) _onHome(); }, null);
    setTimeout(function () {
      var first = _elGrid.querySelector('.focusable');
      if (first) NAV.focus(first); else NAV.focus(_elHomeBtn);
    }, 60);
  }

  function _renderCatbar() {
    _elCatbar.innerHTML = '';
    var cats = API.getVodCategories();
    for (var i = 0; i < cats.length; i++) {
      (function (cat) {
        var chip = document.createElement('div');
        chip.className = 'cat-chip focusable' +
          (cat.category_id === _selectedCat ? ' selected' : '');
        chip.textContent = cat.category_name;
        chip.addEventListener('click', function () {
          _selectedCat = cat.category_id;
          _renderCatbar();
          _renderGrid();
          setTimeout(function () {
            var first = _elGrid.querySelector('.focusable');
            if (first) NAV.focus(first);
          }, 40);
        });
        _elCatbar.appendChild(chip);
      })(cats[i]);
    }
  }

  function _renderGrid() {
    _elGrid.className = 'grid vod-grid'; /* 6 columnas */
    _elGrid.innerHTML = '';

    var q = (_elSearch.value || '').toLowerCase().trim();
    var streams = API.getVodByCategory(_selectedCat, q);

    for (var i = 0; i < streams.length; i++) {
      (function (stream) {
        var card = _makeCard(stream);
        card.addEventListener('click', function () {
          if (_onPlay) _onPlay(stream);
        });
        _elGrid.appendChild(card);
      })(streams[i]);
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
    thumb.className = 'card-thumb rect';

    var img = document.createElement('img');
    img.alt = stream.name;
    img.src = API.imgUrl(stream) || '';
    img.onerror = function () { this.style.display = 'none'; };
    thumb.appendChild(img);

    var rat = API.parseRating(stream);
    if (rat && rat > 0) {
      var badge = document.createElement('div');
      badge.className = 'card-rating';
      badge.textContent = rat.toFixed(1);
      thumb.appendChild(badge);
    }

    card.appendChild(thumb);

    var name = document.createElement('div');
    name.className = 'card-name';
    name.textContent = stream.name;
    card.appendChild(name);

    return card;
  }

  return { init: init, show: show };
})();
