/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.regression;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.util.*;

/**
 * Multivariate linear regression with intercept (ordinary least squares) of a target series on driver series, over
 * the timestamps at which every series has a value; the same figures as the web app's chart type REGRESSION
 * (JEWebApp/src/analysis/regression.ts): coefficients with standard error, t and two-sided p, R², adjusted R², RMSE,
 * CV(RMSE) and the model value per timestamp.
 */
public final class MultipleRegression {

    public enum Problem {NO_DRIVERS, TOO_FEW_POINTS, COLLINEAR}

    private final Problem problem;
    private final double[] coefficients;
    private final double[] standardErrors;
    private final double[] t;
    private final double[] p;
    private final double rSquared;
    private final double adjustedRSquared;
    private final double rmse;
    private final Double cvRmse;
    private final long[] timestamps;
    private final double[] measured;
    private final double[] modelled;

    private MultipleRegression(Problem problem) {
        this.problem = problem;
        coefficients = standardErrors = t = p = measured = modelled = new double[0];
        timestamps = new long[0];
        rSquared = adjustedRSquared = rmse = Double.NaN;
        cvRmse = null;
    }

    private MultipleRegression(double[] coefficients, double[] standardErrors, double[] t, double[] p, double rSquared,
                               double adjustedRSquared, double rmse, Double cvRmse, long[] timestamps, double[] measured,
                               double[] modelled) {
        this.problem = null;
        this.coefficients = coefficients;
        this.standardErrors = standardErrors;
        this.t = t;
        this.p = p;
        this.rSquared = rSquared;
        this.adjustedRSquared = adjustedRSquared;
        this.rmse = rmse;
        this.cvRmse = cvRmse;
        this.timestamps = timestamps;
        this.measured = measured;
        this.modelled = modelled;
    }

    /**
     * @param target  timestamp (ms) → value of the target
     * @param drivers per driver timestamp (ms) → value
     */
    public static MultipleRegression fit(SortedMap<Long, Double> target, List<? extends Map<Long, Double>> drivers) {
        if (drivers.isEmpty()) return new MultipleRegression(Problem.NO_DRIVERS);
        List<Long> ts = new ArrayList<>();
        List<double[]> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : target.entrySet()) {
            if (entry.getValue() == null || !Double.isFinite(entry.getValue())) continue;
            double[] row = new double[drivers.size()];
            boolean complete = true;
            for (int j = 0; j < drivers.size() && complete; j++) {
                Double x = drivers.get(j).get(entry.getKey());
                if (x == null || !Double.isFinite(x)) complete = false;
                else row[j] = x;
            }
            if (!complete) continue;
            ts.add(entry.getKey());
            xs.add(row);
            ys.add(entry.getValue());
        }
        int n = ys.size(), k = drivers.size();
        if (n <= k + 1) return new MultipleRegression(Problem.TOO_FEW_POINTS);
        double[] y = new double[n];
        double[][] x = new double[n][];
        for (int i = 0; i < n; i++) {
            y[i] = ys.get(i);
            x[i] = xs.get(i);
        }
        OLSMultipleLinearRegression ols = new OLSMultipleLinearRegression();
        try {
            ols.newSampleData(y, x);
            double[] beta = ols.estimateRegressionParameters();
            double[] se = ols.estimateRegressionParametersStandardErrors();
            int df = n - k - 1;
            TDistribution dist = new TDistribution(df);
            double[] tv = new double[beta.length], pv = new double[beta.length];
            for (int j = 0; j < beta.length; j++) {
                tv[j] = se[j] > 0 ? beta[j] / se[j] : (beta[j] == 0 ? 0 : Double.POSITIVE_INFINITY);
                pv[j] = Double.isInfinite(tv[j]) ? 0 : 2 * (1 - dist.cumulativeProbability(Math.abs(tv[j])));
            }
            double[] fitted = new double[n];
            double mean = 0;
            for (int i = 0; i < n; i++) {
                double v = beta[0];
                for (int j = 0; j < k; j++) v += beta[j + 1] * x[i][j];
                fitted[i] = v;
                mean += y[i];
            }
            mean /= n;
            double rmse = ols.estimateRegressionStandardError();
            long[] times = new long[n];
            for (int i = 0; i < n; i++) times[i] = ts.get(i);
            double r2, adj;
            try {
                r2 = ols.calculateRSquared();
                adj = ols.calculateAdjustedRSquared();
            } catch (Exception e) {
                r2 = adj = 1;
            }
            return new MultipleRegression(beta, se, tv, pv, r2, adj, rmse, mean != 0 ? rmse / Math.abs(mean) : null, times, y, fitted);
        } catch (SingularMatrixException e) {
            return new MultipleRegression(Problem.COLLINEAR);
        }
    }

    /** null if the model could be computed */
    public Problem getProblem() {
        return problem;
    }

    /** intercept first, then one per driver */
    public double[] getCoefficients() {
        return coefficients;
    }

    public double[] getStandardErrors() {
        return standardErrors;
    }

    public double[] getT() {
        return t;
    }

    public double[] getP() {
        return p;
    }

    public double getRSquared() {
        return rSquared;
    }

    public double getAdjustedRSquared() {
        return adjustedRSquared;
    }

    public double getRmse() {
        return rmse;
    }

    public Double getCvRmse() {
        return cvRmse;
    }

    public int getN() {
        return measured.length;
    }

    public long[] getTimestamps() {
        return timestamps;
    }

    public double[] getMeasured() {
        return measured;
    }

    public double[] getModelled() {
        return modelled;
    }
}
