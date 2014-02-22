/*
 * -----------------------------------------------------------------------
 * Copyright © 2013 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (ProportionalFunction.java) is part of project Time4J.
 *
 * Time4J is free software: You can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Time4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Time4J. If not, see <http://www.gnu.org/licenses/>.
 * -----------------------------------------------------------------------
 */

package net.time4j;

import net.time4j.engine.ChronoElement;
import net.time4j.engine.ChronoEntity;
import net.time4j.engine.ChronoFunction;

import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * <p>Ermittelt eine Verh&auml;ltniszahl. </p>
 *
 * @param       <T> generic type of temporal context
 * @author      Meno Hochschild
 * @concurrency <immutable>
 */
final class ProportionalFunction
    implements ChronoFunction<ChronoEntity<?>, BigDecimal> {

    //~ Instanzvariablen --------------------------------------------------

    private final ChronoElement<? extends Number> element;
    private final boolean closedRange;

    //~ Konstruktoren -----------------------------------------------------

    /**
     * <p>Erzeugt eine neue Abfrage. </p>
     *
     * @param   element         element this query is related to
     * @param   closedRange     is the range closed to ceiling?
     */
    ProportionalFunction(
        ChronoElement<? extends Number> element,
        boolean closedRange
    ) {
        super();

        this.element = element;
        this.closedRange = closedRange;

    }

    //~ Methoden ----------------------------------------------------------

    @Override
    public BigDecimal apply(ChronoEntity<?> context) {

        long value = context.get(this.element).longValue();
        long min = context.getMinimum(this.element).longValue();
        long max = context.getMaximum(this.element).longValue();

        if (value > max) {
            value = max; // Schutz gegen Anomalien
        }

        if (this.closedRange) {
            if (value == max) {
                return BigDecimal.ONE;
            }
            max--;
        }

        BigDecimal count = new BigDecimal(value - min).setScale(15);
        BigDecimal divisor = new BigDecimal(max - min + 1);

        return count.divide(divisor, RoundingMode.HALF_UP).stripTrailingZeros();

    }

}