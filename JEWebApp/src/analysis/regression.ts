/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { numericSamples, type RowView } from './model'

/*
 * Multivariate linear regression (chart type REGRESSION): one row (the target, e.g. energy consumption) is explained
 * by the other rows of the chart (the drivers, e.g. degree days and production) with ordinary least squares and an
 * intercept, as for an energy baseline per ISO 50006. Only the timestamps at which every row has a value are used,
 * so the rows should share one aggregation (day, week, month).
 */

export type RegressionProblem = 'NO_TARGET' | 'NO_DRIVERS' | 'TOO_FEW_POINTS' | 'COLLINEAR'

export interface RegressionTerm {
  /** null for the intercept */
  row: RowView | null
  coefficient: number
  standardError: number
  t: number
  /** two-sided p value of the t test (coefficient = 0) */
  p: number
}

export interface RegressionFit {
  target: RowView
  drivers: RowView[]
  /** intercept first, then one term per driver */
  terms: RegressionTerm[]
  /** number of timestamps used */
  n: number
  rSquared: number
  adjustedRSquared: number
  /** standard error of the regression, in the unit of the target */
  rmse: number
  /** RMSE relative to the mean of the target (null when the mean is 0) */
  cvRmse: number | null
  /** [timestamp, measured, modelled] */
  points: [number, number, number][]
}

export type RegressionResult = { ok: true; fit: RegressionFit } | { ok: false; problem: RegressionProblem; target: RowView | null; drivers: RowView[] }

/**
 * Target and drivers: the row marked Y (else the first row) is the target; the rows marked X are the drivers, or, if
 * no row is marked X, all other rows. Rows the user may not read or without values are left out.
 */
export function regressionRoles(rows: RowView[]): { target: RowView | null; drivers: RowView[] } {
  const usable = rows.filter((r) => r.readable && r.state === 'OK')
  const target = usable.find((r) => r.bubbleType === 'Y') ?? usable.find((r) => r.bubbleType !== 'X') ?? null
  const others = usable.filter((r) => r !== target)
  const marked = others.filter((r) => r.bubbleType === 'X')
  return { target, drivers: marked.length ? marked : others.filter((r) => r.bubbleType !== 'SIZE') }
}

/** Timestamps at which the target and every driver have a value: [timestamp, y, x1..xk]. */
export function alignRows(target: RowView, drivers: RowView[]): { ts: number[]; y: number[]; x: number[][] } {
  const maps = drivers.map((d) => new Map(numericSamples(d).map(([t, v]) => [t, v])))
  const ts: number[] = []
  const y: number[] = []
  const x: number[][] = []
  for (const [t, v] of numericSamples(target)) {
    const row: number[] = []
    for (const m of maps) {
      const value = m.get(t)
      if (value === undefined) break
      row.push(value)
    }
    if (row.length !== drivers.length) continue
    ts.push(t)
    y.push(v)
    x.push(row)
  }
  return { ts, y, x }
}

/** Inverse of a symmetric positive definite matrix (Gauss-Jordan with partial pivoting); null if it is singular. */
function invert(a: number[][]): number[][] | null {
  const n = a.length
  const m = a.map((row, i) => [...row, ...Array.from({ length: n }, (_, j) => (i === j ? 1 : 0))])
  const scale = Math.max(1e-300, ...a.map((row, i) => Math.abs(row[i])))
  for (let c = 0; c < n; c++) {
    let pivot = c
    for (let r = c + 1; r < n; r++) if (Math.abs(m[r][c]) > Math.abs(m[pivot][c])) pivot = r
    if (Math.abs(m[pivot][c]) <= 1e-10 * scale) return null
    ;[m[c], m[pivot]] = [m[pivot], m[c]]
    const d = m[c][c]
    for (let j = 0; j < 2 * n; j++) m[c][j] /= d
    for (let r = 0; r < n; r++) {
      if (r === c || m[r][c] === 0) continue
      const f = m[r][c]
      for (let j = 0; j < 2 * n; j++) m[r][j] -= f * m[c][j]
    }
  }
  return m.map((row) => row.slice(n))
}

/** ln Γ(x) (Lanczos approximation, x > 0). */
function logGamma(x: number): number {
  const g = [76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5]
  let y = x
  const tmp = x + 5.5 - (x + 0.5) * Math.log(x + 5.5)
  let ser = 1.000000000190015
  for (const c of g) ser += c / ++y
  return -tmp + Math.log((2.5066282746310005 * ser) / x)
}

/** Continued fraction of the incomplete beta function (modified Lentz). */
function betaFraction(a: number, b: number, x: number): number {
  const tiny = 1e-30
  let c = 1
  let d = 1 - ((a + b) * x) / (a + 1)
  if (Math.abs(d) < tiny) d = tiny
  d = 1 / d
  let h = d
  for (let m = 1; m <= 300; m++) {
    const m2 = 2 * m
    let aa = (m * (b - m) * x) / ((a + m2 - 1) * (a + m2))
    d = 1 + aa * d
    if (Math.abs(d) < tiny) d = tiny
    c = 1 + aa / c
    if (Math.abs(c) < tiny) c = tiny
    d = 1 / d
    h *= d * c
    aa = (-(a + m) * (a + b + m) * x) / ((a + m2) * (a + m2 + 1))
    d = 1 + aa * d
    if (Math.abs(d) < tiny) d = tiny
    c = 1 + aa / c
    if (Math.abs(c) < tiny) c = tiny
    d = 1 / d
    const del = d * c
    h *= del
    if (Math.abs(del - 1) < 1e-14) break
  }
  return h
}

/** Regularized incomplete beta function I_x(a, b). */
export function incompleteBeta(x: number, a: number, b: number): number {
  if (x <= 0) return 0
  if (x >= 1) return 1
  const front = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b) + a * Math.log(x) + b * Math.log(1 - x))
  return x < (a + 1) / (a + b + 2) ? (front * betaFraction(a, b, x)) / a : 1 - (front * betaFraction(b, a, 1 - x)) / b
}

/** Two-sided p value of Student's t distribution with `df` degrees of freedom. */
export function tTestP(t: number, df: number): number {
  if (!Number.isFinite(t)) return 0
  return incompleteBeta(df / (df + t * t), df / 2, 0.5)
}

/** Ordinary least squares with intercept: y ≈ b0 + b1·x1 + … + bk·xk. */
export function fitOls(y: number[], x: number[][]): { terms: Omit<RegressionTerm, 'row'>[]; rSquared: number; adjustedRSquared: number; rmse: number; fitted: number[] } | RegressionProblem {
  const n = y.length
  const k = x[0]?.length ?? 0
  const p = k + 1
  if (n <= p) return 'TOO_FEW_POINTS'
  const design = x.map((row) => [1, ...row])
  const xtx = Array.from({ length: p }, (_, i) => Array.from({ length: p }, (_, j) => design.reduce((s, r) => s + r[i] * r[j], 0)))
  const xty = Array.from({ length: p }, (_, i) => design.reduce((s, r, idx) => s + r[i] * y[idx], 0))
  const inverse = invert(xtx)
  if (!inverse) return 'COLLINEAR'
  const beta = inverse.map((row) => row.reduce((s, v, j) => s + v * xty[j], 0))
  const fitted = design.map((r) => r.reduce((s, v, j) => s + v * beta[j], 0))
  const mean = y.reduce((s, v) => s + v, 0) / n
  const sse = y.reduce((s, v, i) => s + (v - fitted[i]) ** 2, 0)
  const sst = y.reduce((s, v) => s + (v - mean) ** 2, 0)
  const df = n - p
  const sigma2 = sse / df
  const rSquared = sst > 0 ? 1 - sse / sst : 1
  const terms = beta.map((coefficient, j) => {
    const standardError = Math.sqrt(Math.max(0, sigma2 * inverse[j][j]))
    const t = standardError > 0 ? coefficient / standardError : coefficient === 0 ? 0 : Infinity
    return { coefficient, standardError, t, p: tTestP(t, df) }
  })
  return { terms, rSquared, adjustedRSquared: 1 - ((1 - rSquared) * (n - 1)) / df, rmse: Math.sqrt(sigma2), fitted }
}

/** Regression of the chart's target row on its drivers. */
export function regression(rows: RowView[]): RegressionResult {
  const { target, drivers } = regressionRoles(rows)
  if (!target) return { ok: false, problem: 'NO_TARGET', target, drivers }
  if (!drivers.length) return { ok: false, problem: 'NO_DRIVERS', target, drivers }
  const { ts, y, x } = alignRows(target, drivers)
  const fit = fitOls(y, x)
  if (typeof fit === 'string') return { ok: false, problem: fit, target, drivers }
  const mean = y.reduce((s, v) => s + v, 0) / y.length
  return {
    ok: true,
    fit: {
      target,
      drivers,
      terms: fit.terms.map((term, j) => ({ ...term, row: j === 0 ? null : drivers[j - 1] })),
      n: y.length,
      rSquared: fit.rSquared,
      adjustedRSquared: fit.adjustedRSquared,
      rmse: fit.rmse,
      cvRmse: mean !== 0 ? fit.rmse / Math.abs(mean) : null,
      points: ts.map((t, i) => [t, y[i], fit.fitted[i]]),
    },
  }
}
