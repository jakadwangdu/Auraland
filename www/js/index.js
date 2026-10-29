(function () {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var pill = $('pill'), timer = null;
  var demos = {
    notif:  ['Maya', 'Dinner at 8? I can book a table.'],
    charge: ['Charging', '82%'],
    music:  ['Slow Tides', 'Halden, Evening']
  };

  function showPreview(kind) {
    $('pTitle').textContent = demos[kind][0];   // textContent only: untrusted text is never parsed as HTML
    $('pSub').textContent = demos[kind][1];
    pill.classList.add('open');
    clearTimeout(timer);
    timer = setTimeout(function () { pill.classList.remove('open'); }, 4000);
  }
  pill.addEventListener('click', function () { pill.classList.toggle('open'); });
  Array.prototype.forEach.call(document.querySelectorAll('[data-demo]'), function (b) {
    b.addEventListener('click', function () { showPreview(b.getAttribute('data-demo')); });
  });
  showPreview('notif');

  function say(t) { $('msg').textContent = t || ''; }
  function badge(el, ok) { el.textContent = ok ? 'On' : 'Off'; el.className = 'badge ' + (ok ? 'on' : 'off'); }
  function native() { return window.AuraIsland || null; }

  function refresh() {
    var n = native();
    if (!n) { say('Running in a browser: native features are available only in the Android app.'); return; }
    n.status().then(function (s) {
      badge($('sOverlay'), s.overlay); badge($('sNotif'), s.notifications);
      $('tEnable').checked = s.running; $('tHide').checked = s.hidePreview;
    }, function (e) { say('Status error: ' + e); });
  }

  function guard(fn) { return function () { var n = native(); if (!n) return say('Android only.'); fn(n); }; }
  $('bOverlay').addEventListener('click', guard(function (n) { n.requestOverlay(); }));
  $('bNotif').addEventListener('click', guard(function (n) { n.openNotificationAccess(); }));
  $('bBattery').addEventListener('click', guard(function (n) { n.openBatterySettings(); }));
  $('bTest').addEventListener('click', guard(function (n) {
    n.test().then(function () { say(''); }, function (e) { say(String(e)); });
  }));
  $('tHide').addEventListener('change', guard(function (n) { n.setHidePreview($('tHide').checked); }));
  $('tEnable').addEventListener('change', guard(function (n) {
    var on = $('tEnable').checked;
    (on ? n.start() : n.stop()).then(refresh, function (e) {
      $('tEnable').checked = false; say(String(e));
    });
  }));

  document.addEventListener('deviceready', refresh, false);
  document.addEventListener('resume', refresh, false);
  refresh();
})();
