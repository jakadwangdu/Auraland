(function () {
  'use strict';
  var REPO = '__REPO__';
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var fine = window.matchMedia('(pointer: fine)').matches;
  var $ = function (s) { return document.querySelector(s); };
  var $$ = function (s) { return Array.prototype.slice.call(document.querySelectorAll(s)); };

  // Smooth scrolling (Lenis, MIT). Page works normally if it fails to load.
  var lenis = null;
  if (window.Lenis && !reduce) {
    lenis = new Lenis({ lerp: 0.085 });
    (function raf(t) { lenis.raf(t); requestAnimationFrame(raf); })(0);
  }

  // Hero island cycles on its own
  var hero = $('#island');
  var order = ['notif', 'idle', 'charge', 'idle', 'music', 'idle'], i = 0;
  if (!reduce) {
    setTimeout(function () { hero.setAttribute('data-state', 'notif'); }, 1800);
    setInterval(function () { i = (i + 1) % order.length; hero.setAttribute('data-state', order[i]); }, 3800);
  } else { hero.setAttribute('data-state', 'notif'); }

  // Cursor glow and island tilt (fine pointers only)
  if (fine && !reduce) {
    var spot = $('.spot'), tx = 0, ty = 0, cx = 0, cy = 0, run = false;
    function loop() {
      cx += (tx - cx) * 0.08; cy += (ty - cy) * 0.08;
      spot.style.transform = 'translate3d(' + cx + 'px,' + cy + 'px,0)';
      if (Math.abs(tx - cx) + Math.abs(ty - cy) > 0.5) requestAnimationFrame(loop); else run = false;
    }
    window.addEventListener('pointermove', function (e) {
      tx = e.clientX; ty = e.clientY; spot.classList.add('on');
      if (!run) { run = true; requestAnimationFrame(loop); }
      var nx = e.clientX / window.innerWidth - 0.5, ny = e.clientY / window.innerHeight - 0.5;
      hero.style.setProperty('--ry', (nx * 14).toFixed(2) + 'deg');
      hero.style.setProperty('--rx', (-ny * 10).toFixed(2) + 'deg');
    }, { passive: true });
    $$('.btn').forEach(function (b) {   // gentle magnetic buttons
      b.addEventListener('pointermove', function (e) {
        var r = b.getBoundingClientRect();
        b.style.transform = 'translate(' + ((e.clientX - r.left - r.width / 2) * 0.18) + 'px,' + ((e.clientY - r.top - r.height / 2) * 0.28) + 'px)';
      });
      b.addEventListener('pointerleave', function () { b.style.transform = ''; });
    });
  }

  // Scroll-driven pieces
  var story = $('#moments'), steps = $$('.step'), dots = $$('.dots button'), phone = $('#phone'), mini = $('#island2');
  var lines = $$('.line'), bar = $('.progress'), cur = -1, tick = false;
  var states = ['notif', 'charge', 'music', 'hidden'];

  function setStep(n) {
    if (n === cur) return; cur = n;
    steps.forEach(function (s, k) { s.classList.toggle('on', k === n); });
    dots.forEach(function (d, k) { d.classList.toggle('on', k === n); d.setAttribute('aria-selected', String(k === n)); });
    phone.classList.toggle('rot', n === 3);
    mini.setAttribute('data-state', n === 3 ? 'hidden' : 'idle');
    if (n < 3) setTimeout(function () { if (cur === n) mini.setAttribute('data-state', states[n]); }, 250);
  }

  function paint() {
    var h = window.innerHeight, y = window.scrollY, max = document.documentElement.scrollHeight - h;
    bar.style.transform = 'scaleX(' + (max > 0 ? Math.min(1, y / max) : 0).toFixed(4) + ')';
    var top = story.offsetTop, span = story.offsetHeight - h;
    var p = Math.min(0.999, Math.max(0, (y - top) / span));
    setStep(Math.floor(p * 4));
    lines.forEach(function (el) {
      var r = el.getBoundingClientRect(), c = r.top + r.height / 2;
      el.style.setProperty('--p', (1 - Math.min(1, Math.max(0, (c - h * 0.5) / (h * 0.4)))).toFixed(3));
    });
    tick = false;
  }
  function queue() { if (!tick) { tick = true; requestAnimationFrame(paint); } }
  window.addEventListener('scroll', queue, { passive: true });
  window.addEventListener('resize', queue);
  dots.forEach(function (d) {
    d.addEventListener('click', function () {
      var n = +d.getAttribute('data-go'), h = window.innerHeight;
      var y = story.offsetTop + (story.offsetHeight - h) * ((n + 0.5) / 4);
      if (lenis) lenis.scrollTo(y); else window.scrollTo({ top: y, behavior: reduce ? 'auto' : 'smooth' });
    });
  });
  paint();

  // Latest version and size on the download buttons
  if (REPO.indexOf('__') === -1) {
    fetch('https://api.github.com/repos/' + REPO + '/releases/latest')
      .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
      .then(function (rel) {
        var a = (rel.assets || []).filter(function (x) { return x.name === 'Auraland.apk'; })[0];
        if (a) $('#meta').textContent = rel.tag_name + ', ' + (a.size / 1048576).toFixed(1) + ' MB, free for Android 8 and up';
      }).catch(function () {});
  }
})();
