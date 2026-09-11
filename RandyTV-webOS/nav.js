/* ────────────────────────────────────────────────────────────────
   nav.js  –  Motor de navegación por control remoto
   Teclas: ▲38  ▼40  ◄37  ►39  OK(13/32)  BACK(461/8/27)
   Compatible ES5 / Chromium 53
──────────────────────────────────────────────────────────────── */

var NAV = (function () {
  'use strict';

  var _focused   = null;   /* elemento DOM con foco actual */
  var _scope     = null;   /* contenedor que limita los focusables */
  var _onBack    = null;   /* callback Back */
  var _onEnter   = null;   /* callback Enter (alternativo) */

  /* Agregar una entrada al historial para que Back funcione */
  function _pushHistory() {
    try { history.pushState({ rtv: 1 }, ''); } catch (e) {}
  }

  /* ── Inicializar ─────────────────────────────────────────── */
  function init() {
    window.addEventListener('keydown', _onKey);
    window.addEventListener('popstate', function (e) {
      /* webOS dispara popstate cuando se pulsa Back */
      if (_onBack) { _onBack(); _pushHistory(); }
    });
    /* Empujar estado inicial para capturar el primer Back */
    _pushHistory();
  }

  /* ── Cambiar scope & callback ────────────────────────────── */
  function setScope(container, backCb, enterCb) {
    _scope   = container;
    _onBack  = backCb  || null;
    _onEnter = enterCb || null;
  }

  /* ── Foco ────────────────────────────────────────────────── */
  function focus(el) {
    if (!el) return;
    if (_focused && _focused !== el) _focused.classList.remove('focused');
    _focused = el;
    el.classList.add('focused');
    /* Scroll automático: llevar al elemento visible */
    try { el.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch(e) {}
  }

  function focusFirst() {
    var container = _scope || document.body;
    var els = container.querySelectorAll('.focusable');
    if (els.length) focus(els[0]);
  }

  function focused() { return _focused; }

  /* ── Obtener lista ordenada de focusables en el scope ──────── */
  function _getFocusables() {
    var container = _scope || document.body;
    var nl = container.querySelectorAll('.focusable');
    var arr = [];
    for (var i = 0; i < nl.length; i++) arr.push(nl[i]);
    return arr;
  }

  /* ── Rect helper ─────────────────────────────────────────── */
  function _rect(el) { return el.getBoundingClientRect(); }

  /* ── Navegación espacial ─────────────────────────────────── */
  function _navigate(dir) {
    var current = _focused;
    if (!current) { focusFirst(); return; }

    var all = _getFocusables();
    if (all.length === 0) return;

    var cr = _rect(current);
    var cx = cr.left + cr.width  / 2;
    var cy = cr.top  + cr.height / 2;

    var best = null, bestScore = Infinity;

    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el === current) continue;
      var r  = _rect(el);
      var ex = r.left + r.width  / 2;
      var ey = r.top  + r.height / 2;

      var dx = ex - cx;
      var dy = ey - cy;
      var valid = false;

      if (dir === 'up'    && dy < -4) valid = true;
      if (dir === 'down'  && dy >  4) valid = true;
      if (dir === 'left'  && dx < -4) valid = true;
      if (dir === 'right' && dx >  4) valid = true;

      if (!valid) continue;

      /* Score: distancia primaria + penalización por desviación perpendicular */
      var primary, perp;
      if (dir === 'up' || dir === 'down') {
        primary = Math.abs(dy); perp = Math.abs(dx);
      } else {
        primary = Math.abs(dx); perp = Math.abs(dy);
      }
      var score = primary + perp * 2;
      if (score < bestScore) { bestScore = score; best = el; }
    }

    if (best) focus(best);
  }

  /* ── Teclado ─────────────────────────────────────────────── */
  function _onKey(e) {
    var k = e.keyCode;

    /* Ignorar si el foco está en un input de texto */
    if (document.activeElement &&
        (document.activeElement.tagName === 'INPUT' ||
         document.activeElement.tagName === 'TEXTAREA')) {
      if (k === 27 || k === 461) { /* ESC / Back sale del input */
        document.activeElement.blur();
        e.preventDefault();
      }
      return;
    }

    switch (k) {
      case 38: /* ▲ Up    */ e.preventDefault(); _navigate('up');    break;
      case 40: /* ▼ Down  */ e.preventDefault(); _navigate('down');  break;
      case 37: /* ◄ Left  */ e.preventDefault(); _navigate('left');  break;
      case 39: /* ► Right */ e.preventDefault(); _navigate('right'); break;

      case 13:  /* Enter */
      case 32:  /* Space (webOS magic remote OK) */
        e.preventDefault();
        if (_focused) {
          _focused.click();
        } else if (_onEnter) {
          _onEnter();
        }
        break;

      case 461: /* Back webOS */
      case 8:   /* Backspace  */
      case 27:  /* ESC        */
        e.preventDefault();
        if (_onBack) _onBack();
        break;

      default: break;
    }
  }

  /* ── Helpers públicos ────────────────────────────────────── */
  function clearFocus() {
    if (_focused) { _focused.classList.remove('focused'); _focused = null; }
  }

  return {
    init: init,
    setScope: setScope,
    focus: focus,
    focusFirst: focusFirst,
    focused: focused,
    clearFocus: clearFocus,
    navigate: _navigate   /* expuesto para uso directo si hace falta */
  };
})();
