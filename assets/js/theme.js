// ===== LIGHT / DARK MODE =====
// I-load sa <head> para walang "flash" ng maling kulay.
(function () {
  const KEY = 'vk_theme';
  const mq = window.matchMedia('(prefers-color-scheme: light)');
  const saved = () => { try { return localStorage.getItem(KEY); } catch (e) { return null; } };
  const current = () => saved() || (mq.matches ? 'light' : 'dark');

  function apply(t) {
    document.documentElement.dataset.theme = t;
    document.querySelectorAll('.theme-toggle').forEach(b => {
      b.textContent = t === 'light' ? '🌙' : '☀️';
      b.title = t === 'light' ? 'Dark mode' : 'Light mode';
      b.setAttribute('aria-label', b.title);
    });
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = t === 'light' ? '#f5f2fb' : '#0b0716';
  }

  window.vkToggleTheme = function () {
    const t = current() === 'light' ? 'dark' : 'light';
    try { localStorage.setItem(KEY, t); } catch (e) {}
    apply(t);
  };

  apply(current());
  // Sumunod sa setting ng device kung hindi pa pumipili ang user
  mq.addEventListener && mq.addEventListener('change', () => { if (!saved()) apply(current()); });

  document.addEventListener('DOMContentLoaded', () => {
    apply(current());
    document.querySelectorAll('.theme-toggle').forEach(b => b.addEventListener('click', e => {
      e.stopPropagation();
      window.vkToggleTheme();
    }));
  });
})();
