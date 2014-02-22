/*
 * -----------------------------------------------------------------------
 * Copyright © 2012 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (TimeElement.java) is part of project Time4J.
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

import net.time4j.engine.BasicElement;
import net.time4j.engine.ChronoEntity;

import java.io.ObjectStreamException;


/**
 * <p>Repr&auml;sentiert eine Uhrzeitkomponente. </p>
 *
 * @author      Meno Hochschild
 * @concurrency <immutable>
 */
final class TimeElement
    extends BasicElement<PlainTime> {

    //~ Statische Felder/Initialisierungen --------------------------------

    /**
     * Singleton-Instanz.
     */
    static final TimeElement INSTANCE = new TimeElement();

    private static final long serialVersionUID = -3712256393866098916L;

    //~ Konstruktoren -----------------------------------------------------

    private TimeElement() {
        super("WALL_TIME");

    }

    //~ Methoden ----------------------------------------------------------

    @Override
    public Class<PlainTime> getType() {

        return PlainTime.class;

    }

    @Override
    public PlainTime getDefaultMinimum() {

        return PlainTime.MIN;

    }

    @Override
    public PlainTime getDefaultMaximum() {

        return PlainTime.MAX.minus(1, ClockUnit.NANOS);

    }

    @Override
    public int compare(
        ChronoEntity<?> o1,
        ChronoEntity<?> o2
    ) {

        return o1.get(this).compareTo(o2.get(this));

    }

    @Override
    public boolean isDateElement() {

        return false;

    }

    @Override
    public boolean isTimeElement() {

        return true;

    }

    private Object readResolve() throws ObjectStreamException {

        return INSTANCE;

    }

}