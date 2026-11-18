/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { describe, expect, it } from 'vitest'
import { buildChartViews, chartKind, type ChartView } from '../src/analysis/model'
import { chartOption, type OptionContext } from '../src/analysis/options'
import { alignRows, fitOls, regression, regressionRoles, tTestP } from '../src/analysis/regression'
import { chart, config, data, row } from './fixtures/analysis'

const ctx: OptionContext = { locale: 'de', labels: { noData: 'Keine Daten', noPermission: 'Keine Berechtigung', error: 'Fehler', count: 'Anzahl', model: 'Modell' } }

type Samples = [number, number, null][]
const series = (values: number[], skip: number[] = []): Samples =>
  values.map((v, i) => [i + 1, v, null] as [number, number, null]).filter(([t]) => !skip.includes(t))

function regressionChart(rows: { key: string; role?: string; samples: Samples }[]): ChartView {
  const [view] = buildChartViews(
    config([chart(0, 'REGRESSION', rows.map((r) => row(r.key, { bubbleType: r.role ?? 'NONE' })))]),
    data([{ id: 0, rows: rows.map((r) => ({ key: r.key, status: 'OK', samples: r.samples })) }]),
    '(kein Zugriff)',
  )
  return view
}

describe('multivariate regression', () => {
  it('fits a simple regression like a statistics package', () => {
    // x = 1..5, y = 2,4,5,4,5: y = 2.2 + 0.6 x, R² = 0.6, SE(slope) = 0.2828, t = 2.121, p = 0.124 (df 3)
    const fit = fitOls([2, 4, 5, 4, 5], [[1], [2], [3], [4], [5]])
    if (typeof fit === 'string') throw new Error(fit)
    expect(fit.terms[0].coefficient).toBeCloseTo(2.2, 10)
    expect(fit.terms[1].coefficient).toBeCloseTo(0.6, 10)
    expect(fit.rSquared).toBeCloseTo(0.6, 10)
    expect(fit.adjustedRSquared).toBeCloseTo(0.4667, 4)
    expect(fit.rmse).toBeCloseTo(Math.sqrt(0.8), 10)
    expect(fit.terms[1].standardError).toBeCloseTo(0.28284, 5)
    expect(fit.terms[1].t).toBeCloseTo(2.12132, 5)
    expect(fit.terms[1].p).toBeCloseTo(0.124, 3)
  })

  it('computes p values of the t distribution', () => {
    expect(tTestP(2.228139, 10)).toBeCloseTo(0.05, 5)
    expect(tTestP(1.959964, 1e7)).toBeCloseTo(0.05, 4)
    expect(tTestP(0, 5)).toBeCloseTo(1, 10)
    expect(tTestP(Infinity, 5)).toBe(0)
  })

  it('recovers the coefficients of two drivers', () => {
    const a = [3, 1, 4, 1, 5, 9, 2, 6]
    const b = [2, 7, 1, 8, 2, 8, 1, 8]
    const y = a.map((v, i) => 10 + 2 * v - 3 * b[i])
    const result = regression(regressionChart([{ key: 'y', samples: series(y) }, { key: 'a', samples: series(a) }, { key: 'b', samples: series(b) }]).rows)
    if (!result.ok) throw new Error(result.problem)
    expect(result.fit.terms.map((t) => t.coefficient).map((c) => Math.round(c * 1e9) / 1e9)).toEqual([10, 2, -3])
    expect(result.fit.terms.map((t) => t.row?.key ?? null)).toEqual([null, 'a', 'b'])
    expect(result.fit.rSquared).toBeCloseTo(1, 10)
    expect(result.fit.n).toBe(8)
    expect(result.fit.points[0]).toEqual([1, y[0], expect.closeTo(y[0], 9)])
  })

  it('uses only the timestamps at which every row has a value', () => {
    const view = regressionChart([
      { key: 'y', samples: series([1, 2, 3, 4, 5]) },
      { key: 'a', samples: series([1, 2, 3, 4, 5], [2]) },
      { key: 'b', samples: series([5, 3, 4, 1, 2], [4]) },
    ])
    const { target, drivers } = regressionRoles(view.rows)
    expect(alignRows(target!, drivers).ts).toEqual([1, 3, 5])
  })

  it('takes the target and drivers from the row roles', () => {
    const view = regressionChart([
      { key: 'a', role: 'X', samples: series([1, 2]) },
      { key: 'other', samples: series([1, 2]) },
      { key: 'y', role: 'Y', samples: series([1, 2]) },
    ])
    const roles = regressionRoles(view.rows)
    expect(roles.target?.key).toBe('y')
    expect(roles.drivers.map((r) => r.key)).toEqual(['a'])
    const plain = regressionRoles(regressionChart([{ key: 'p', samples: series([1]) }, { key: 'q', samples: series([1]) }, { key: 'r', samples: series([1]) }]).rows)
    expect(plain.target?.key).toBe('p')
    expect(plain.drivers.map((r) => r.key)).toEqual(['q', 'r'])
  })

  it('reports why there is no model', () => {
    const problem = (rows: { key: string; role?: string; samples: Samples }[]) => {
      const r = regression(regressionChart(rows).rows)
      return r.ok ? 'OK' : r.problem
    }
    expect(problem([{ key: 'y', samples: series([1, 2, 3]) }])).toBe('NO_DRIVERS')
    expect(problem([{ key: 'y', samples: series([1, 2, 3]) }, { key: 'a', samples: series([1, 2]) }])).toBe('TOO_FEW_POINTS')
    expect(problem([{ key: 'y', samples: series([1, 3, 2, 5]) }, { key: 'a', samples: series([1, 2, 3, 4]) }, { key: 'b', samples: series([2, 4, 6, 8]) }])).toBe('COLLINEAR')
    expect(problem([{ key: 'y', samples: [] }, { key: 'a', samples: series([1]) }])).toBe('NO_DRIVERS')
  })

  it('draws the target and the model on a time axis', () => {
    expect(chartKind('REGRESSION')).toBe('regression')
    const view = regressionChart([{ key: 'y', samples: series([2, 4, 5, 4, 5]) }, { key: 'x', samples: series([1, 2, 3, 4, 5]) }])
    const option = chartOption(view, ctx) as { series: Record<string, unknown>[]; xAxis: { type: string } }
    expect(option.xAxis.type).toBe('time')
    expect(option.series.map((s) => s.name)).toEqual(['Meter y', 'Modell: Meter y'])
    expect(option.series[1].lineStyle).toMatchObject({ type: 'dashed' })
    expect((option.series[1].data as unknown[][])[0][1]).toBeCloseTo(2.8, 10)
  })
})
