(function () {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var N = function () { return window.AuraIsland || null; };
  var pill = $('pill'), timer = null;
  var demos = {
    notif:  ['Maya', 'Dinner at 8? I can book a table.'],
    charge: ['Charging', '82%'],
    music:  ['Slow Tides', 'Halden, Evening']
  };
  function showPreview(k) {
    $('pTitle').textContent = demos[k][0];   // textContent only: untrusted text is never parsed as HTML
    $('pSub').textContent = demos[k][1];
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
  function guard(fn) { return function () { var n = N(); if (!n) return say('Android only.'); fn(n); }; }

  // ---- chat ----
  function add(role, text) {
    var d = document.createElement('div'); d.className = 'm ' + role; d.textContent = text;
    $('log').appendChild(d); d.scrollIntoView({ block: 'nearest' }); return d;
  }
  function send() {
    var t = $('q').value.trim(); if (!t) return;
    $('q').value = ''; add('me', t);
    var n = N(); if (!n) { add('ai', 'Open the Android app to chat.'); return; }
    var el = add('ai', '...');
    n.ai(t).then(function (r) { el.textContent = r; }, function () { el.textContent = 'Something went wrong.'; });
  }
  $('send').addEventListener('click', send);
  $('q').addEventListener('keydown', function (e) { if (e.key === 'Enter') send(); });

  // ---- setup / toggles ----
  function refresh() {
    var n = N();
    if (!n) { say('Running in a browser: native features work only in the Android app.'); return; }
    n.status().then(function (s) {
      badge($('sOverlay'), s.overlay); badge($('sNotif'), s.notifications);
      $('tEnable').checked = s.running; $('tHide').checked = s.hidePreview;
    }, function (e) { say('Status error: ' + e); });
    n.getPosition().then(function (p) { setSliders(p.x, p.y); });
    n.getShortcuts().then(function (j) { try { chosen = JSON.parse(j); } catch (e) { chosen = []; } drawChosen(); });
    n.getAi().then(function (a) { $('aiUrl').value = a.url; $('aiModel').value = a.model; });
  }
  $('bOverlay').addEventListener('click', guard(function (n) { n.requestOverlay(); }));
  $('bNotif').addEventListener('click', guard(function (n) { n.openNotificationAccess(); }));
  $('bBattery').addEventListener('click', guard(function (n) { n.openBatterySettings(); }));
  $('bTest').addEventListener('click', guard(function (n) {
    n.test().then(function () { say(''); }, function (e) { say(String(e)); });
  }));
  $('tHide').addEventListener('change', guard(function (n) { n.setHidePreview($('tHide').checked); }));
  $('tEnable').addEventListener('change', guard(function (n) {
    var on = $('tEnable').checked;
    (on ? n.start() : n.stop()).then(refresh, function (e) { $('tEnable').checked = false; say(String(e)); });
  }));

  // ---- position ----
  var posTimer;
  function setSliders(x, y) { $('sx').value = x; $('sy').value = y; $('vx').textContent = x; $('vy').textContent = y; }
  function push() {
    $('vx').textContent = $('sx').value; $('vy').textContent = $('sy').value;
    clearTimeout(posTimer);
    posTimer = setTimeout(function () { var n = N(); if (n) n.setPosition(+$('sx').value, +$('sy').value); }, 40);
  }
  $('sx').addEventListener('input', push); $('sy').addEventListener('input', push);
  $('bReset').addEventListener('click', function () { setSliders(0, 8); push(); });
  $('bAlign').addEventListener('click', guard(function (n) {
    $('posMsg').textContent = '';
    n.autoAlign().then(function (p) { setSliders(p.x, p.y); $('posMsg').textContent = 'Aligned. Fine-tune with the sliders.'; },
      function (e) { $('posMsg').textContent = String(e); });
  }));

  // ---- quick menu apps ----
  var chosen = [], all = [];
  function drawChosen() {
    $('chosen').textContent = chosen.length ? chosen.map(function (a) { return a.label; }).join(', ') : 'None chosen. Pick up to four apps for the island menu.';
  }
  function save() { var n = N(); if (n) n.setShortcuts(JSON.stringify(chosen)); drawChosen(); }
  function drawList() {
    var q = $('appq').value.trim().toLowerCase(), box = $('appList'); box.textContent = '';
    all.filter(function (a) { return !q || a.label.toLowerCase().indexOf(q) >= 0; }).forEach(function (a) {
      var row = document.createElement('label'); row.className = 'app';
      var name = document.createElement('span'); name.textContent = a.label;
      var cb = document.createElement('input'); cb.type = 'checkbox';
      cb.checked = chosen.some(function (c) { return c.pkg === a.pkg; });
      cb.addEventListener('change', function () {
        if (cb.checked) {
          if (chosen.length >= 4) { cb.checked = false; return; }
          chosen.push({ label: a.label, pkg: a.pkg });
        } else { chosen = chosen.filter(function (c) { return c.pkg !== a.pkg; }); }
        save();
      });
      row.appendChild(name); row.appendChild(cb); box.appendChild(row);
    });
  }
  $('bApps').addEventListener('click', guard(function (n) {
    var p = $('picker'); p.hidden = !p.hidden;
    if (!p.hidden && !all.length) n.listApps().then(function (l) { all = l; drawList(); });
  }));
  $('appq').addEventListener('input', drawList);

  // ---- AI settings ----
  $('bAi').addEventListener('click', guard(function (n) { n.setAi($('aiUrl').value.trim(), $('aiModel').value.trim()); }));

  document.addEventListener('deviceready', refresh, false);
  document.addEventListener('resume', refresh, false);
  refresh();
})();
