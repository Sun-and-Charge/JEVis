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
package org.jevis.commons.trc;

import org.joda.time.DateTime;

import java.util.*;

/**
 * Result of {@link ResultCalculator#calculate}: one cell per template output, the results of all formulas and the
 * warnings.
 */
public class TrcResult {

    public enum Kind {
        /**
         * formula with a number result ({@link Cell#getValue()}, {@code null} if it could not be calculated)
         */
        VALUE,
        /**
         * text formula ({@link Cell#getText()})
         */
        TEXT,
        /**
         * output without formula, only the name
         */
        LABEL,
        /**
         * separator line
         */
        SEPARATOR
    }

    public enum WarningType {
        /**
         * the formula could not be evaluated
         */
        FORMULA_FAILED,
        /**
         * the input has no object
         */
        INPUT_NO_OBJECT,
        /**
         * the object of the input does not exist or may not be read; it was not read
         */
        INPUT_NOT_READABLE,
        /**
         * the input has no values in the time frame (or its attribute does not exist)
         */
        INPUT_NO_VALUES,
        /**
         * the formulas depend on each other in a circle
         */
        FORMULA_CYCLE
    }

    private final DateTime from;
    private final DateTime to;
    private final List<Cell> cells = new ArrayList<>();
    private final List<Warning> warnings = new ArrayList<>();
    private final Map<String, Double> formulaResults = new LinkedHashMap<>();

    public TrcResult(DateTime from, DateTime to) {
        this.from = from;
        this.to = to;
    }

    public DateTime getFrom() {
        return from;
    }

    public DateTime getTo() {
        return to;
    }

    /**
     * @return one cell per output, in the order of the template
     */
    public List<Cell> getCells() {
        return cells;
    }

    public List<Warning> getWarnings() {
        return warnings;
    }

    /**
     * @return the number results by formula id (as the other formulas use them: {@code 0} for failed, text or
     * NaN/infinite results)
     */
    public Map<String, Double> getFormulaResults() {
        return formulaResults;
    }

    /**
     * @return the cell of the output or {@code null}
     */
    public Cell cell(String outputId) {
        for (Cell cell : cells) {
            if (cell.getOutput() != null && Objects.equals(cell.getOutput().getId(), outputId)) return cell;
        }
        return null;
    }

    public void addWarning(Warning warning) {
        if (!warnings.contains(warning)) warnings.add(warning);
    }

    /**
     * One output of the grid.
     */
    public static class Cell {
        private final TemplateOutput output;
        private final Kind kind;
        private Double value;
        private String text;
        private Long analysisObjectId;
        private String analysisAttribute;
        private DateTime analysisFrom;
        private DateTime analysisTo;

        public Cell(TemplateOutput output, Kind kind) {
            this.output = output;
            this.kind = kind;
        }

        public TemplateOutput getOutput() {
            return output;
        }

        public Kind getKind() {
            return kind;
        }

        public int getRow() {
            return output.getRow() != null ? output.getRow() : 0;
        }

        public int getColumn() {
            return output.getColumn() != null ? output.getColumn() : 0;
        }

        public int getRowSpan() {
            return output.getRowSpan() != null ? output.getRowSpan() : 1;
        }

        public int getColumnSpan() {
            return output.getColSpan() != null ? output.getColSpan() : 1;
        }

        public String getName() {
            return output.getName();
        }

        public boolean isNameBold() {
            return Boolean.TRUE.equals(output.getNameBold());
        }

        public boolean isResultBold() {
            return Boolean.TRUE.equals(output.getResultBold());
        }

        public String getUnit() {
            return output.getUnit();
        }

        /**
         * @return the tooltip if the output shows one, else {@code null}
         */
        public String getTooltip() {
            return Boolean.TRUE.equals(output.getShowTooltip()) ? output.getTooltip() : null;
        }

        /**
         * @return {@code horizontal} if the separator spans more than one column, {@code vertical} if it spans more
         * than one row (as the Control Center, the latter wins), else {@code null}
         */
        public String getOrientation() {
            if (kind != Kind.SEPARATOR) return null;
            String orientation = null;
            if (getColumnSpan() > 1) orientation = "horizontal";
            if (getRowSpan() > 1) orientation = "vertical";
            return orientation;
        }

        /**
         * @return the number result (NaN/infinite are {@code 0}); {@code null} if it could not be calculated
         */
        public Double getValue() {
            return value;
        }

        public void setValue(Double value) {
            this.value = value;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        /**
         * @return the object of the analysis link (output with "show analysis link" whose formula has exactly one
         * input), else {@code null}
         */
        public Long getAnalysisObjectId() {
            return analysisObjectId;
        }

        public String getAnalysisAttribute() {
            return analysisAttribute;
        }

        /**
         * @return start of the analysis link (the time frame of the formula)
         */
        public DateTime getAnalysisFrom() {
            return analysisFrom;
        }

        public DateTime getAnalysisTo() {
            return analysisTo;
        }

        public void setAnalysis(Long objectId, String attribute, DateTime from, DateTime to) {
            this.analysisObjectId = objectId;
            this.analysisAttribute = attribute;
            this.analysisFrom = from;
            this.analysisTo = to;
        }
    }

    /**
     * A problem of the calculation, with the formula and input it concerns (names, not translated).
     */
    public static class Warning {
        private final WarningType type;
        private final String formula;
        private final String input;

        public Warning(WarningType type, String formula, String input) {
            this.type = type;
            this.formula = formula;
            this.input = input;
        }

        public WarningType getType() {
            return type;
        }

        /**
         * @return the name of the formula, may be {@code null}
         */
        public String getFormula() {
            return formula;
        }

        /**
         * @return the variable name of the input, may be {@code null}
         */
        public String getInput() {
            return input;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Warning)) return false;
            Warning other = (Warning) o;
            return type == other.type && Objects.equals(formula, other.formula) && Objects.equals(input, other.input);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, formula, input);
        }

        @Override
        public String toString() {
            return type + (formula != null ? " formula=" + formula : "") + (input != null ? " input=" + input : "");
        }
    }
}
