// ===== LIGHT / DARK MODE =====
// I-load sa <head> (pagkatapos ng icons.js) para walang "flash" ng maling kulay.
(function () {
  const KEY = 'vk_theme';
  const mq = window.matchMedia('(prefers-color-scheme: light)');
  const saved = () => { try { return localStorage.getItem(KEY); } catch (e) { return null; } };
  const current = () => saved() || (mq.matches ? 'light' : 'dark');

  function apply(t) {
    document.documentElement.dataset.theme = t;
    document.querySelectorAll('.theme-toggle').forEach(b => {
      b.setAttribute('aria-label', t === 'light' ? 'Lumipat sa dark mode' : 'Lumipat sa light mode');
      b.title = b.getAttribute('aria-label');
    });
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = t === 'light' ? '#eef0f3' : '#15171c';
  }

  window.vkToggleTheme = function () {
    const t = current() === 'light' ? 'dark' : 'light';
    try { localStorage.setItem(KEY, t); } catch (e) {}
    apply(t);
  };

  apply(current());
  mq.addEventListener && mq.addEventListener('change', () => { if (!saved()) apply(current()); });

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.theme-toggle').forEach(b => {
      b.type = 'button';
      b.innerHTML = `<span class="tt-opt tt-light">${window.ICON ? ICON('sun') : '☀'}</span><span class="tt-opt tt-dark">${window.ICON ? ICON('moon') : '☾'}</span>`;
      b.addEventListener('click', e => { e.stopPropagation(); window.vkToggleTheme(); });
    });
    apply(current());
  });
})();
