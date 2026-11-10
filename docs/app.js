/* RebuyTracker · web pública. Lee games.json de la rama "data" y pinta las vistas. */
(function () {
  'use strict';

  const S = window.Stats;
  const $app = document.getElementById('app');

  // El usuario y el repo se deducen de la URL de GitHub Pages: <usuario>.github.io/<repo>/
  const CONFIG = (() => {
    const host = location.hostname;
    const owner = host.endsWith('.github.io') ? host.split('.')[0] : 'Ando144';
    const repo = location.pathname.split('/').filter(Boolean)[0] || 'RebuyTracker';
    return { owner, repo, branch: 'data', file: 'games.json' };
  })();

  const PALETTE = ['#3E9C77', '#C8A464', '#6EA8FE', '#E26D64', '#B48EF0', '#5CC8C2', '#F0A35E', '#D97AB4', '#9CC46B', '#A9B4BD'];

  let data = null;
  let demo = false;
  let season = readSeason();
  let chart = null;
  const hidden = new Set();

  // ---------- Utilidades ----------

  function esc(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  const cls = (cents) => (cents > 0 ? 'gain' : cents < 0 ? 'loss' : 'muted');
  const money = (cents) => `<span class="${cls(cents)}">${S.signed(cents)}</span>`;
  const initial = (name) => esc((name || '?').trim().charAt(0).toUpperCase());
  const avatar = (name) => `<span class="avatar">${initial(name)}</span>`;
  const playerHref = (id) => `#/jugador/${encodeURIComponent(id)}`;
  const gameHref = (id) => `#/partida/${id}`;

  function readSeason() {
    try { return localStorage.getItem('rt-season') || 'all'; } catch (e) { return 'all'; }
  }
  function saveSeason(s) {
    season = s;
    try { localStorage.setItem('rt-season', s); } catch (e) { /* sin almacenamiento */ }
  }

  function colorFor(id) {
    const ids = data.players.map((p) => p.id);
    const i = ids.indexOf(id);
    return PALETTE[(i < 0 ? 0 : i) % PALETTE.length];
  }

  function games() { return S.filterGames(data.games, season, Date.now()); }

  function sparkline(series, color) {
    if (series.length < 2) return '<svg class="spark"></svg>';
    const w = 110, h = 32, pad = 3;
    const min = Math.min(0, ...series), max = Math.max(0, ...series);
    const span = max - min || 1;
    const x = (i) => pad + (i * (w - 2 * pad)) / (series.length - 1);
    const y = (v) => h - pad - ((v - min) * (h - 2 * pad)) / span;
    const d = series.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join('');
    return `<svg class="spark" viewBox="0 0 ${w} ${h}" aria-hidden="true">
      <line x1="${pad}" x2="${w - pad}" y1="${y(0)}" y2="${y(0)}" stroke="#323940" stroke-dasharray="2 3"/>
      <path d="${d}" fill="none" stroke="${color}" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round"/>
    </svg>`;
  }

  function seasonChips() {
    return `<div class="chips" role="group" aria-label="Temporada">${S.seasons(data.games)
      .map((s) => `<button class="chip ${s.id === season ? 'on' : ''}" data-season="${s.id}">${esc(s.label)}</button>`)
      .join('')}</div>`;
  }

  function head(title, sub) {
    return `<div class="page-head"><div><h1>${title}</h1>${sub ? `<p>${sub}</p>` : ''}</div>${seasonChips()}</div>`;
  }

  function empty(message) {
    return `<div class="panel empty"><h2>Nada por aquí</h2><p>${message}</p></div>`;
  }

  function demoBanner() {
    return demo ? `<div class="banner">Estás viendo <strong>datos de ejemplo</strong>. <a href="${location.pathname}">Ver los datos reales</a></div>` : '';
  }

  // ---------- Vistas ----------

  function viewRanking() {
    const gs = games();
    const board = S.leaderboard(data, gs);
    const sum = S.summary(gs);
    let html = demoBanner() + head('Clasificación', `${sum.games} partidas`);
    if (!board.length) return html + empty('No hay partidas en este periodo.');

    html += `<div class="stats">
      <div class="stat"><div class="k">Partidas</div><div class="v">${sum.games}</div><div class="s">${board.length} jugadores</div></div>
      <div class="stat"><div class="k">Fichas en juego</div><div class="v">${S.euros(sum.table)}</div><div class="s">media ${S.euros(sum.avgTable)} por noche</div></div>
      <div class="stat"><div class="k">Bizums</div><div class="v">${S.euros(sum.moved)}</div><div class="s">dinero que ha cambiado de manos</div></div>
      <div class="stat"><div class="k">Líder</div><div class="v ${cls(board[0].net)}">${S.signed(board[0].net)}</div><div class="s">${esc(board[0].name)}</div></div>
    </div>`;

    if (board.length >= 3) {
      const spot = (p, n) => `<a class="spot p${n}" href="${playerHref(p.id)}">
          <div class="medal">${n}º</div>${avatar(p.name)}
          <div class="name">${esc(p.name)}</div><div class="net ${cls(p.net)}">${S.signed(p.net)}</div>
          <div class="muted small">${p.games} partidas</div></a>`;
      html += `<div class="podium">${spot(board[1], 2)}${spot(board[0], 1)}${spot(board[2], 3)}</div>`;
    }

    html += `<div class="label">Ranking</div><div class="panel"><div class="rows">${board.map((p, i) => `
      <a class="row" href="${playerHref(p.id)}">
        <span class="pos ${i < 3 ? 'top' : ''}">${i + 1}</span>
        ${avatar(p.name)}
        <span class="main">
          <span class="title">${esc(p.name)}</span>
          <span class="sub">${p.games} partidas · ${S.pct(p.positiveRate)} en positivo<span class="hide-sm"> · media ${S.signed(p.avg)}</span></span>
        </span>
        ${sparkline(p.series, colorFor(p.id))}
        <span class="end"><span class="big ${cls(p.net)}">${S.signed(p.net)}</span><br><span class="muted small">mejor ${S.signed(p.best)}</span></span>
      </a>`).join('')}</div></div>`;
    return html;
  }

  function viewEvolution() {
    const gs = games();
    let html = demoBanner() + head('Evolución', 'Saldo acumulado de cada jugador, partida a partida');
    if (gs.length < 2) return html + empty('Hacen falta al menos dos partidas en este periodo para dibujar la evolución.');
    html += `<div class="panel"><div class="chart-box"><canvas id="chart" aria-label="Gráfico de evolución"></canvas></div></div>
      <div class="legend" id="legend"></div>`;
    return html;
  }

  function drawEvolution() {
    const canvas = document.getElementById('chart');
    if (!canvas) return;
    if (!window.Chart) { canvas.parentElement.innerHTML = '<p class="muted small">No se ha podido cargar el gráfico.</p>'; return; }
    const evo = S.evolution(data, games());
    const datasets = evo.series.map((s) => ({
      label: s.name,
      id: s.id,
      data: s.points,
      borderColor: colorFor(s.id),
      backgroundColor: colorFor(s.id),
      borderWidth: 2,
      pointRadius: evo.labels.length > 30 ? 0 : 2.5,
      pointHoverRadius: 5,
      tension: 0.25,
      hidden: hidden.has(s.id),
    }));
    if (chart) chart.destroy();
    chart = new Chart(canvas, {
      type: 'line',
      data: { labels: evo.labels, datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: {
            itemSort: (a, b) => b.parsed.y - a.parsed.y,
            callbacks: { label: (c) => ` ${c.dataset.label}: ${S.signed(Math.round(c.parsed.y * 100))}` },
          },
        },
        scales: {
          x: { grid: { color: '#1E2327' }, ticks: { color: '#8F9AA3', maxRotation: 0, autoSkipPadding: 12 } },
          y: {
            grid: { color: (c) => (c.tick.value === 0 ? '#3A424A' : '#1E2327') },
            ticks: { color: '#8F9AA3', callback: (v) => `${v} €` },
          },
        },
      },
    });
    const legend = document.getElementById('legend');
    legend.innerHTML = evo.series.map((s) =>
      `<button data-toggle="${esc(s.id)}" class="${hidden.has(s.id) ? 'off' : ''}"><i style="background:${colorFor(s.id)}"></i>${esc(s.name)}</button>`).join('');
  }

  function viewAwards() {
    const gs = games();
    const awards = S.awards(data, gs);
    const records = S.records(data, gs);
    let html = demoBanner() + head('Premios', 'Lo mejor y lo peor de la mesa');
    if (!awards.length) return html + empty('No hay partidas en este periodo.');
    html += `<div class="awards">${awards.map((a) => `
      <a class="award" href="${playerHref(a.player.id)}">
        <div class="icon">${a.icon}</div>
        <div class="t">${esc(a.title)}</div>
        <div class="d">${esc(a.desc)}</div>
        <div class="who">${avatar(a.player.name)}<div><div>${esc(a.player.name)}</div><div class="val">${esc(a.value)}</div></div></div>
      </a>`).join('')}</div>`;
    if (records.length) {
      html += `<div class="label">Récords</div><div class="panel"><div class="rows">${records.map((r) => {
        const inner = `<span class="pos" style="font-size:20px">${r.icon}</span>
          <span class="main"><span class="title">${esc(r.title)}</span><span class="sub">${esc(r.who)}${r.when ? ' · ' + S.date(r.when.date) : ''}</span></span>
          <span class="end big" style="color:var(--brass)">${esc(r.value)}</span>`;
        return r.when ? `<a class="row" href="${gameHref(r.when.id)}">${inner}</a>` : `<div class="row">${inner}</div>`;
      }).join('')}</div></div>`;
    }
    return html;
  }

  function viewGames() {
    const gs = games().slice().reverse();
    const name = S.nameMap(data);
    let html = demoBanner() + head('Partidas', `${gs.length} partidas`);
    if (!gs.length) return html + empty('No hay partidas en este periodo.');
    html += `<div class="panel"><div class="rows">${gs.map((g) => {
      const sorted = [...g.results].sort((a, b) => b.netCents - a.netCents);
      const top = sorted[0];
      const rebuys = g.results.reduce((s, r) => s + (r.rebuys || 0), 0);
      return `<a class="row" href="${gameHref(g.id)}">
        <span class="main">
          <span class="title">${S.date(g.date)}</span>
          <span class="sub">${g.results.length} jugadores${g.manual ? ' · resultado a mano' : ` · ${rebuys} rebuys · mesa ${S.euros(g.tableCents || 0)}`}</span>
        </span>
        <span class="end"><span class="muted small">gana</span> ${esc(name(top.playerId))}<br>${money(top.netCents)}</span>
      </a>`;
    }).join('')}</div></div>`;
    return html;
  }

  function viewGame(id) {
    const g = data.games.find((x) => String(x.id) === String(id));
    const name = S.nameMap(data);
    if (!g) return `<a class="back" href="#/partidas">← Partidas</a>` + empty('No se encuentra esa partida.');
    const sorted = [...g.results].sort((a, b) => b.netCents - a.netCents);
    let html = demoBanner() + `<a class="back" href="#/partidas">← Partidas</a>
      <div class="page-head"><div><h1>${S.date(g.date)}</h1><p>${g.results.length} jugadores${g.manual ? ' · resultado registrado a mano' : ''}</p></div></div>`;
    if (!g.manual) {
      const rebuys = g.results.reduce((s, r) => s + (r.rebuys || 0), 0);
      const rebuyCents = g.results.reduce((s, r) => s + (r.rebuyCents || 0), 0);
      html += `<div class="stats">
        <div class="stat"><div class="k">En la mesa</div><div class="v">${S.euros(g.tableCents || 0)}</div></div>
        <div class="stat"><div class="k">Entrada</div><div class="v">${S.euros(g.buyInCents || 0)}</div><div class="s">${(g.chipsPerBuyIn || 0).toLocaleString('es-ES')} fichas</div></div>
        <div class="stat"><div class="k">Rebuys</div><div class="v">${rebuys}</div><div class="s">${S.euros(rebuyCents)}</div></div>
        <div class="stat"><div class="k">Bizums</div><div class="v">${g.transfers.length}</div></div>
      </div>`;
    }
    html += `<div class="label">Resultados</div><div class="panel"><div class="rows">${sorted.map((r, i) => `
      <a class="row" href="${playerHref(r.playerId)}">
        <span class="pos ${i === 0 ? 'top' : ''}">${i + 1}</span>${avatar(name(r.playerId))}
        <span class="main"><span class="title">${esc(name(r.playerId))}</span>
          ${g.manual ? '' : `<span class="sub">puso ${S.euros(r.investedCents || 0)}${r.rebuys ? ` · ${r.rebuys} rebuy${r.rebuys > 1 ? 's' : ''}` : ''} · ${(r.finalChips || 0).toLocaleString('es-ES')} fichas</span>`}
        </span>
        <span class="end big">${money(r.netCents)}</span>
      </a>`).join('')}</div></div>`;
    html += `<div class="label">Bizums</div><div class="panel"><div class="rows">${g.transfers.length ? g.transfers.map((t) => `
      <div class="row"><span class="main"><span class="title">${esc(name(t.from))}<span class="arrow">→</span>${esc(name(t.to))}</span></span>
      <span class="end big" style="color:var(--felt)">${S.euros(t.cents)}</span></div>`).join('') : '<div class="row muted">Nadie debía nada.</div>'}</div></div>`;
    return html;
  }

  function viewPlayer(id) {
    const gs = games();
    const p = S.player(data, gs, id);
    const back = `<a class="back" href="#/">← Clasificación</a>`;
    if (!p) return demoBanner() + back + head('Jugador', '') + empty('Este jugador no tiene partidas en el periodo elegido.');
    const nights = (n) => `${n} noche${n === 1 ? '' : 's'}`;
    let html = demoBanner() + back + `<div class="page-head"><div class="hero">${avatar(p.name)}<div><h1>${esc(p.name)}</h1>
        <div class="rank">${p.rank}º de ${p.of} · ${p.games} partidas</div></div></div>${seasonChips()}</div>`;
    html += `<div class="stats">
      <div class="stat"><div class="k">Saldo</div><div class="v ${cls(p.net)}">${S.signed(p.net)}</div><div class="s">media ${S.signed(p.avg)} por noche</div></div>
      <div class="stat"><div class="k">En positivo</div><div class="v">${S.pct(p.positiveRate)}</div><div class="s">${nights(p.wins)} de ${p.games}</div></div>
      <div class="stat"><div class="k">Mejor / peor</div><div class="v"><span class="${cls(p.best)}">${S.signed(p.best)}</span></div><div class="s">peor ${S.signed(p.worst)}</div></div>
      <div class="stat"><div class="k">Rebuys</div><div class="v">${p.rebuys}</div><div class="s">${S.euros(p.rebuyCents)} en total</div></div>
    </div>
    <div class="stats" style="margin-top:12px">
      <div class="stat"><div class="k">Racha actual</div><div class="v">${p.currentStreak}</div><div class="s">${p.currentStreak ? 'noches seguidas en positivo' : 'sin racha ahora mismo'}</div></div>
      <div class="stat"><div class="k">Mejor racha</div><div class="v">${p.bestStreak}</div><div class="s">noches seguidas en positivo</div></div>
      <div class="stat"><div class="k">Dinero puesto</div><div class="v">${S.euros(p.invested)}</div><div class="s">entradas y rebuys</div></div>
      <div class="stat"><div class="k">Fichas cedidas</div><div class="v">${S.euros(p.sold)}</div><div class="s">vendidas a otros jugadores</div></div>
    </div>`;
    if (p.series.length >= 2) {
      html += `<div class="label">Evolución</div><div class="panel"><div class="chart-box" style="height:260px"><canvas id="pchart"></canvas></div></div>`;
    }
    const flowList = (list, empty) => list.length
      ? list.slice(0, 5).map((f) => `<a class="row" href="${playerHref(f.from === id ? f.to : f.from)}">${avatar(f.name)}
          <span class="main"><span class="title">${esc(f.name)}</span><span class="sub">${f.count} bizum${f.count > 1 ? 's' : ''}</span></span>
          <span class="end big">${S.euros(f.cents)}</span></a>`).join('')
      : `<div class="row muted small">${empty}</div>`;
    html += `<div class="grid2">
      <div><div class="label">Le paga a</div><div class="panel"><div class="rows">${flowList(p.paysTo, 'No ha tenido que pagar a nadie.')}</div></div></div>
      <div><div class="label">Cobra de</div><div class="panel"><div class="rows">${flowList(p.paidBy, 'Nadie le ha pagado todavía.')}</div></div></div>
    </div>`;
    html += `<div class="label">Sus partidas</div><div class="panel"><div class="rows">${p.history.map((h) => `
      <a class="row" href="${gameHref(h.game.id)}">
        <span class="main"><span class="title">${S.date(h.game.date)}</span>
        <span class="sub">${h.position}º de ${h.of}${h.result.rebuys ? ` · ${h.result.rebuys} rebuy${h.result.rebuys > 1 ? 's' : ''}` : ''}</span></span>
        <span class="end big">${money(h.result.netCents)}</span>
      </a>`).join('')}</div></div>`;
    return html;
  }

  function drawPlayerChart(id) {
    const canvas = document.getElementById('pchart');
    if (!canvas || !window.Chart) return;
    const p = S.player(data, games(), id);
    const color = colorFor(id);
    if (chart) chart.destroy();
    chart = new Chart(canvas, {
      type: 'line',
      data: {
        labels: p.results.map((r) => S.shortDate(r.date)),
        datasets: [{ data: p.series.map((v) => v / 100), borderColor: color, backgroundColor: color + '22', fill: true, tension: 0.25, borderWidth: 2, pointRadius: 2.5 }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false }, tooltip: { callbacks: { label: (c) => ` ${S.signed(Math.round(c.parsed.y * 100))}` } } },
        scales: {
          x: { grid: { color: '#1E2327' }, ticks: { color: '#8F9AA3', maxRotation: 0, autoSkipPadding: 12 } },
          y: { grid: { color: (c) => (c.tick.value === 0 ? '#3A424A' : '#1E2327') }, ticks: { color: '#8F9AA3', callback: (v) => `${v} €` } },
        },
      },
    });
  }

  // ---------- Router ----------

  function render() {
    if (!data) return;
    if (chart) { chart.destroy(); chart = null; }
    const parts = location.hash.replace(/^#\/?/, '').split('/');
    const route = parts[0] || '';
    const arg = decodeURIComponent(parts[1] || '');
    const tab = { '': 'ranking', jugador: 'ranking', evolucion: 'evolucion', premios: 'premios', partidas: 'partidas', partida: 'partidas' }[route] || 'ranking';
    document.querySelectorAll('.tabs a').forEach((a) => a.classList.toggle('active', a.dataset.tab === tab));

    if (!data.games.length) {
      $app.innerHTML = `<div class="panel empty"><h2>Aún no hay partidas publicadas</h2>
        <p>Cierra una partida en la app con la publicación activada y aparecerá aquí.</p>
        <p><a href="?demo">Ver una demo con datos de ejemplo</a></p></div>`;
      return;
    }

    switch (route) {
      case 'evolucion': $app.innerHTML = viewEvolution(); drawEvolution(); break;
      case 'premios': $app.innerHTML = viewAwards(); break;
      case 'partidas': $app.innerHTML = viewGames(); break;
      case 'partida': $app.innerHTML = viewGame(arg); break;
      case 'jugador': $app.innerHTML = viewPlayer(arg); drawPlayerChart(arg); break;
      default: $app.innerHTML = viewRanking();
    }
  }

  $app.addEventListener('click', (e) => {
    const chip = e.target.closest('[data-season]');
    if (chip) { saveSeason(chip.dataset.season); render(); return; }
    const toggle = e.target.closest('[data-toggle]');
    if (toggle && chart) {
      const id = toggle.dataset.toggle;
      hidden.has(id) ? hidden.delete(id) : hidden.add(id);
      const ds = chart.data.datasets.find((d) => d.id === id);
      if (ds) ds.hidden = hidden.has(id);
      chart.update();
      toggle.classList.toggle('off', hidden.has(id));
    }
  });

  window.addEventListener('hashchange', () => { render(); window.scrollTo({ top: 0 }); });

  // ---------- Carga de datos ----------

  async function fetchJson(url, options) {
    const res = await fetch(url, options);
    if (!res.ok) throw new Error(`${res.status} ${url}`);
    return res.json();
  }

  async function load() {
    document.getElementById('repo-link').href = `https://github.com/${CONFIG.owner}/${CONFIG.repo}`;
    demo = new URLSearchParams(location.search).has('demo');
    try {
      if (demo) {
        data = await fetchJson('demo.json');
      } else {
        const raw = `https://raw.githubusercontent.com/${CONFIG.owner}/${CONFIG.repo}/${CONFIG.branch}/${CONFIG.file}?t=${Date.now()}`;
        try {
          data = await fetchJson(raw, { cache: 'no-store' });
        } catch (e) {
          const api = `https://api.github.com/repos/${CONFIG.owner}/${CONFIG.repo}/contents/${CONFIG.file}?ref=${CONFIG.branch}`;
          data = await fetchJson(api, { headers: { Accept: 'application/vnd.github.raw+json' } });
        }
      }
    } catch (e) {
      data = { players: [], games: [] };
    }
    data.players = data.players || [];
    data.games = data.games || [];
    if (data.generatedAt) {
      document.getElementById('updated').textContent = `Actualizado ${S.date(data.generatedAt)}`;
    }
    if (!S.seasons(data.games).some((s) => s.id === season)) saveSeason('all');
    render();
  }

  load();
})();
