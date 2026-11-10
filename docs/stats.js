/*
 * RebuyTracker · cálculos de la web pública.
 * Funciones puras sobre el JSON que publica la app (games.json). Sin dependencias.
 */
(function (root) {
  'use strict';

  const DAY = 24 * 60 * 60 * 1000;

  // ---------- Formato ----------

  const eurFmt = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });
  const euros = (cents) => eurFmt.format(cents / 100);
  const signed = (cents) => (cents > 0 ? '+' : '') + euros(cents);
  const pct = (x) => Math.round(x * 100) + ' %';
  const dateFmt = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
  const shortDateFmt = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short' });
  const date = (ms) => dateFmt.format(new Date(ms));
  const shortDate = (ms) => shortDateFmt.format(new Date(ms));

  // ---------- Temporadas ----------

  /** Opciones de filtro: todo, cada año con partidas y últimos 30 días. */
  function seasons(games) {
    const years = [...new Set(games.map((g) => new Date(g.date).getFullYear()))].sort((a, b) => b - a);
    return [
      { id: 'all', label: 'Todo' },
      ...years.map((y) => ({ id: String(y), label: String(y) })),
      { id: '30d', label: 'Últimos 30 días' },
    ];
  }

  function filterGames(games, season, now) {
    const sorted = [...games].sort((a, b) => a.date - b.date);
    if (!season || season === 'all') return sorted;
    if (season === '30d') return sorted.filter((g) => g.date >= (now ?? Date.now()) - 30 * DAY);
    return sorted.filter((g) => String(new Date(g.date).getFullYear()) === season);
  }

  // ---------- Clasificación ----------

  function nameMap(data) {
    const m = new Map();
    data.players.forEach((p) => m.set(p.id, p.name));
    return (id) => m.get(id) ?? id.replace(/^name:/, '');
  }

  /** Estadísticas por jugador sobre las partidas dadas (ordenadas por fecha). */
  function leaderboard(data, games) {
    const name = nameMap(data);
    const acc = new Map();
    const get = (id) => {
      if (!acc.has(id)) {
        acc.set(id, {
          id, name: name(id), games: 0, wins: 0, net: 0, best: -Infinity, worst: Infinity,
          invested: 0, rebuys: 0, rebuyCents: 0, sold: 0, results: [],
        });
      }
      return acc.get(id);
    };
    games.forEach((g) => {
      g.results.forEach((r) => {
        const a = get(r.playerId);
        a.games += 1;
        if (r.netCents > 0) a.wins += 1;
        a.net += r.netCents;
        a.best = Math.max(a.best, r.netCents);
        a.worst = Math.min(a.worst, r.netCents);
        a.invested += r.investedCents ?? 0;
        a.rebuys += r.rebuys ?? 0;
        a.rebuyCents += r.rebuyCents ?? 0;
        a.sold += r.soldCents ?? 0;
        a.results.push({ gameId: g.id, date: g.date, net: r.netCents });
      });
    });
    return [...acc.values()]
      .map((a) => {
        let run = 0, bestRun = 0, cumulative = 0;
        const series = a.results.map((r) => (cumulative += r.net));
        a.results.forEach((r) => { run = r.net > 0 ? run + 1 : 0; bestRun = Math.max(bestRun, run); });
        let current = 0;
        for (let i = a.results.length - 1; i >= 0 && a.results[i].net > 0; i--) current++;
        return {
          ...a,
          avg: a.games ? Math.round(a.net / a.games) : 0,
          positiveRate: a.games ? a.wins / a.games : 0,
          bestStreak: bestRun,
          currentStreak: current,
          series,
        };
      })
      .sort((x, y) => y.net - x.net || x.name.localeCompare(y.name));
  }

  function summary(games) {
    const table = games.reduce((s, g) => s + (g.tableCents ?? 0), 0);
    const moved = games.reduce((s, g) => s + g.transfers.reduce((t, x) => t + x.cents, 0), 0);
    const biggest = games.reduce((b, g) => ((g.tableCents ?? 0) > (b?.tableCents ?? -1) ? g : b), null);
    return { games: games.length, table, moved, biggest, avgTable: games.length ? Math.round(table / games.length) : 0 };
  }

  // ---------- Evolución ----------

  /** Saldo acumulado de cada jugador después de cada partida (se mantiene si no juega). */
  function evolution(data, games) {
    const board = leaderboard(data, games);
    const totals = new Map(board.map((p) => [p.id, 0]));
    const points = new Map(board.map((p) => [p.id, []]));
    games.forEach((g) => {
      g.results.forEach((r) => totals.set(r.playerId, totals.get(r.playerId) + r.netCents));
      board.forEach((p) => points.get(p.id).push(totals.get(p.id) / 100));
    });
    return {
      labels: games.map((g) => shortDate(g.date)),
      series: board.map((p) => ({ id: p.id, name: p.name, points: points.get(p.id) })),
    };
  }

  // ---------- Premios y récords ----------

  function pairFlows(games) {
    const flows = new Map();
    games.forEach((g) => g.transfers.forEach((t) => {
      const k = t.from + '→' + t.to;
      flows.set(k, (flows.get(k) ?? { from: t.from, to: t.to, cents: 0, count: 0 }));
      const f = flows.get(k);
      f.cents += t.cents;
      f.count += 1;
    }));
    return [...flows.values()].sort((a, b) => b.cents - a.cents);
  }

  function maxBy(list, f) {
    let best = null, bestV = -Infinity;
    list.forEach((x) => { const v = f(x); if (v > bestV) { best = x; bestV = v; } });
    return best;
  }

  /** Premios de la temporada. Cada uno: icono, título, ganador, valor y explicación. */
  function awards(data, games) {
    const name = nameMap(data);
    const board = leaderboard(data, games);
    if (!board.length) return [];
    const list = [];
    const add = (o) => o && o.player && list.push(o);
    const regulars = board.filter((p) => p.games >= Math.min(3, Math.max(1, Math.floor(games.length / 3))));

    const shark = board[0];
    add(shark.net > 0 && { icon: '🦈', title: 'El Tiburón', desc: 'Mayor saldo acumulado', player: shark, value: signed(shark.net) });

    const atm = board[board.length - 1];
    add(atm.net < 0 && { icon: '🏧', title: 'El Cajero Automático', desc: 'El que más ha repartido a la mesa', player: atm, value: signed(atm.net) });

    const rebuyKing = maxBy(board, (p) => p.rebuys * 1e9 + p.rebuyCents);
    add(rebuyKing?.rebuys > 0 && { icon: '🔁', title: 'Rey del Rebuy', desc: 'Más rebuys pagados', player: rebuyKing, value: `${rebuyKing.rebuys} rebuys · ${euros(rebuyKing.rebuyCents)}` });

    const patron = maxBy(board, (p) => p.invested);
    add(patron?.invested > 0 && { icon: '💸', title: 'El Mecenas', desc: 'Más dinero puesto sobre la mesa', player: patron, value: euros(patron.invested) });

    const dealer = maxBy(board, (p) => p.sold);
    add(dealer?.sold > 0 && { icon: '🤝', title: 'El Proveedor', desc: 'Más fichas cedidas a otros jugadores', player: dealer, value: euros(dealer.sold) });

    const reliable = maxBy(regulars, (p) => p.positiveRate * 1000 + p.games);
    add(reliable && { icon: '🎯', title: 'El Fiable', desc: 'Mejor % de noches en positivo', player: reliable, value: `${pct(reliable.positiveRate)} de ${reliable.games}` });

    const streak = maxBy(board, (p) => p.bestStreak * 1000 + p.net / 1e6);
    add(streak?.bestStreak >= 2 && { icon: '🔥', title: 'En Racha', desc: 'Más noches seguidas en positivo', player: streak, value: `${streak.bestStreak} seguidas` });

    const regular = maxBy(board, (p) => p.games);
    add(regular && { icon: '📅', title: 'El Fijo', desc: 'Más partidas jugadas', player: regular, value: `${regular.games} partidas` });

    const unlucky = maxBy(regulars, (p) => -p.positiveRate * 1000 + p.games);
    add(unlucky && unlucky !== reliable && unlucky.positiveRate < 0.5 && { icon: '🌧️', title: 'Nube Negra', desc: 'Peor % de noches en positivo', player: unlucky, value: `${pct(unlucky.positiveRate)} de ${unlucky.games}` });

    return list;
  }

  /** Récords individuales de una noche y de la mesa. */
  function records(data, games) {
    const name = nameMap(data);
    const all = games.flatMap((g) => g.results.map((r) => ({ ...r, game: g })));
    const out = [];
    const bestNight = maxBy(all, (r) => r.netCents);
    if (bestNight) out.push({ icon: '🚀', title: 'Noche de gloria', who: name(bestNight.playerId), value: signed(bestNight.netCents), when: bestNight.game });
    const worstNight = maxBy(all, (r) => -r.netCents);
    if (worstNight && worstNight.netCents < 0) out.push({ icon: '💥', title: 'El Batacazo', who: name(worstNight.playerId), value: signed(worstNight.netCents), when: worstNight.game });
    const mostRebuys = maxBy(all, (r) => (r.rebuys ?? 0) * 1e9 + (r.rebuyCents ?? 0));
    if (mostRebuys?.rebuys > 0) out.push({ icon: '🩹', title: 'Más rebuys en una noche', who: name(mostRebuys.playerId), value: `${mostRebuys.rebuys} (${euros(mostRebuys.rebuyCents)})`, when: mostRebuys.game });
    const bigTransfer = maxBy(games.flatMap((g) => g.transfers.map((t) => ({ ...t, game: g }))), (t) => t.cents);
    if (bigTransfer) out.push({ icon: '📲', title: 'El Bizum más gordo', who: `${name(bigTransfer.from)} → ${name(bigTransfer.to)}`, value: euros(bigTransfer.cents), when: bigTransfer.game });
    const bigPot = maxBy(games.filter((g) => g.tableCents), (g) => g.tableCents);
    if (bigPot) out.push({ icon: '🪙', title: 'La mesa más cara', who: `${bigPot.results.length} jugadores`, value: euros(bigPot.tableCents), when: bigPot });
    const flow = pairFlows(games)[0];
    if (flow) out.push({ icon: '💞', title: 'La pareja de hecho', who: `${name(flow.from)} → ${name(flow.to)}`, value: `${euros(flow.cents)} en ${flow.count} bizum${flow.count > 1 ? 's' : ''}`, when: null });
    return out;
  }

  // ---------- Jugador ----------

  function player(data, games, id) {
    const board = leaderboard(data, games);
    const index = board.findIndex((p) => p.id === id);
    if (index < 0) return null;
    const name = nameMap(data);
    const p = board[index];
    const flows = pairFlows(games);
    const paysTo = flows.filter((f) => f.from === id).map((f) => ({ ...f, name: name(f.to) }));
    const paidBy = flows.filter((f) => f.to === id).map((f) => ({ ...f, name: name(f.from) }));
    const history = games
      .filter((g) => g.results.some((r) => r.playerId === id))
      .map((g) => {
        const sorted = [...g.results].sort((a, b) => b.netCents - a.netCents);
        const r = g.results.find((x) => x.playerId === id);
        return { game: g, result: r, position: sorted.findIndex((x) => x.playerId === id) + 1, of: sorted.length };
      })
      .reverse();
    return { ...p, rank: index + 1, of: board.length, paysTo, paidBy, history };
  }

  const api = { euros, signed, pct, date, shortDate, seasons, filterGames, leaderboard, summary, evolution, awards, records, player, nameMap };
  root.Stats = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? window : globalThis);
