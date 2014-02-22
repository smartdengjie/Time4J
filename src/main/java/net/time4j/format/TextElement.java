/*
 * -----------------------------------------------------------------------
 * Copyright © 2012 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (TextElement.java) is part of project Time4J.
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

package net.time4j.format;

import net.time4j.engine.AttributeQuery;
import net.time4j.engine.ChronoElement;
import net.time4j.engine.ChronoEntity;

import java.io.IOException;


/**
 * <p>Repr&auml;sentiert ein chronologisches Element, das als Text dargestellt
 * und interpretiert werden kann. </p>
 *
 * @param   <V> generic type of element values
 * @author  Meno Hochschild
 */
public interface TextElement<V>
    extends ChronoElement<V> {

    //~ Methoden ----------------------------------------------------------

    /**
     * <p>Wandelt dieses im angegebenen Zeitwertkontext enthaltene Element zu
     * einem Text um. </p>
     *
     * <p>Implementierungshinweis: Der konkrete Elementwert ist durch den
     * Ausdruck {@link ChronoEntity#get(ChronoElement) context.get(this)}
     * gegeben. </p>
     *
     * @param   context     time context with the value of this element
     * @param   buffer      format buffer any text output will be sent to
     * @param   attributes  query for control attributes
     * @throws  IOException if writing to buffer fails
     */
    void print(
        ChronoEntity<?> context,
        Appendable buffer,
        AttributeQuery attributes
    ) throws IOException;

    /**
     * <p>Interpretiert den angegebenen Text ab einer bestimmten Position
     * als Elementwert. </p>
     *
     * <p>Implementierungshinweis: Eine Implementierung wird den Text
     * erst ab der angegebenen Position {@link ParseLog#getPosition()
     * status.getPosition()} auswerten und nach erfolgreicher Interpretierung
     * den Index neu setzen oder im Fehlerfall {@code null} zur&uuml;ckgeben.
     * </p>
     *
     * @param   text        text to be parsed
     * @param   status      current parsing position
     * @param   attributes  query for control attributes
     * @return  parsed element value or {@code null} if parsing
     *          was not successful
     */
    V parse(
        CharSequence text,
        ParseLog status,
        AttributeQuery attributes
    );

}