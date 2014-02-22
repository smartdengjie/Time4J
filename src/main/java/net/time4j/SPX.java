/*
 * -----------------------------------------------------------------------
 * Copyright © 2012 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (SPX.java) is part of project Time4J.
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

import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.io.StreamCorruptedException;


/**
 * <p>Serialisierungsform f&uuml;r das Hauptpaket. </p>
 *
 * <p>Der Name &quot;SPX&quot; steht f&uuml;r <i>Serialization ProXy</i>.
 * Die Design-Idee stammt von Joshua Bloch in seinem popul&auml;ren Buch
 * &quot;Effective Java&quot; (Item 78). Zwar ist der Serialisierungsaufwand
 * im Vergleich zur Standard-Serialisierungsform etwas h&ouml;her, jedoch
 * kann dieser Nachteil durch verringerte Header-Gr&ouml;&szlig;en mit besseren
 * Netzlaufzeiten besonders bei kleinen Objektgraphen kompensiert werden. Die
 * Kompensation wird durch die Verwendung eines kurzen Klassennamens, durch
 * die gemeinsame Nutzung (<i>shared mode</i>) dieser Klasse durch mehrere
 * zu serialisierende Objekte und durch eine bit-komprimierte Datendarstellung
 * gew&auml;hrleistet. </p>
 *
 * @author  Meno Hochschild
 * @serial  include
 */
final class SPX
    implements Externalizable {

    //~ Statische Felder/Initialisierungen ----------------------------

    /** Serialisierungstyp von {@code PlainDate}. */
    static final int DATE_TYPE = 1;

    /** Serialisierungstyp von {@code PlainTime}. */
    static final int TIME_TYPE = 2;

    /** Serialisierungstyp von {@code Weekmodel}. */
    static final int WEEKMODEL_TYPE = 3;

    /** Serialisierungstyp von {@code Moment}. */
    static final int MOMENT_TYPE = 4;

    /** Serialisierungstyp von {@code PlainTimestamp}. */
    static final int TIMESTAMP_TYPE = 5;

    private static final long serialVersionUID = 1L;

    //~ Instanzvariablen ----------------------------------------------

    private transient Object obj;
    private transient int type;

    //~ Konstruktoren -------------------------------------------------

    /**
     * <p>Benutzt in der Deserialisierung gem&auml;&szlig; dem Kontrakt
     * von {@code Externalizable}. </p>
     */
    public SPX() {
        super();

    }

    /**
     * <p>Benutzt in der Serialisierung (writeReplace). </p>
     *
     * @param   obj     object to be serialized
     * @param   type    serialization type (corresponds to type of obj)
     */
    SPX(
        Object obj,
        int type
    ) {
        super();

        this.obj = obj;
        this.type = type;

    }

    //~ Methoden ------------------------------------------------------

    /**
     * <p>Implementierungsmethode des Interface {@link Externalizable}. </p>
     *
     * <p>Das erste Byte enth&auml;lt um 4 Bits nach links verschoben den
     * Typ des zu serialisierenden Objekts. Danach folgen die Daten-Bits
     * in einer bit-komprimierten Darstellung. </p>
     *
     * @serialData  data layout see {@code writeReplace()}-method of object
     *              to be serialized
     * @param       out     output stream
     * @throws      IOException
     */
    @Override
    public void writeExternal(ObjectOutput out)
        throws IOException {

        switch (this.type) {
            case DATE_TYPE:
                this.writeDate(out);
                break;
            case TIME_TYPE:
                this.writeTime(out);
                break;
            case WEEKMODEL_TYPE:
                this.writeWeekmodel(out);
                break;
            case MOMENT_TYPE:
                this.writeMoment(out);
                break;
            case TIMESTAMP_TYPE:
                this.writeTimestamp(out);
                break;
            default:
                throw new InvalidClassException("Unknown serialized type.");
        }

    }

    /**
     * <p>Implementierungsmethode des Interface {@link Externalizable}. </p>
     *
     * @param   in      input stream
     * @throws  IOException
     * @throws  ClassNotFoundException
     */
    @Override
    public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {

        byte header = in.readByte();

        switch (header >> 4) {
            case DATE_TYPE:
                this.obj = this.readDate(in, header);
                break;
            case TIME_TYPE:
                this.obj = this.readTime(in);
                break;
            case WEEKMODEL_TYPE:
                this.obj = this.readWeekmodel(in, header);
                break;
            case MOMENT_TYPE:
                this.obj = this.readMoment(in, header);
                break;
            case TIMESTAMP_TYPE:
                this.obj = this.readTimestamp(in);
                break;
            default:
                throw new StreamCorruptedException("Unknown serialized type.");
        }

    }

    private Object readResolve() throws ObjectStreamException {

        return this.obj;

    }

    private void writeDate(ObjectOutput out)
        throws IOException {

        PlainDate date = (PlainDate) this.obj;
        int year = date.getYear();

        // Bit 0-3 => type (4)
        // Bit 4-7 => month (4)
        // Bit 8 => unused
        // Bit 9-10 => year-range (2)
        // Bit 11-15 => day-of-month (5)
        // byte - short - int => year

        int range;

        if (year >= 1850 && year <= 2100) {
            range = 1;
        } else if (Math.abs(year) < 10000) {
            range = 2;
        } else {
            range = 3;
        }

        int header = DATE_TYPE;
        header <<= 4;
        header |= date.getMonth();
        out.writeByte(header);

        int header2 = range;
        header2 <<= 5;
        header2 |= date.getDayOfMonth();
        out.writeByte(header2);

        if (range == 1) {
            out.writeByte(year - 1850 - 128);
        } else if (range == 2) {
            out.writeShort(year);
        } else {
            out.writeInt(year);
        }

    }

    private Object readDate(
        ObjectInput in,
        byte header
    ) throws IOException {

        int month = header & 0xF;
        int header2 = in.readByte();
        int range = (header2 >> 5) & 3;
        int day = header2 & 31;
        int year;

        switch (range) {
            case 1:
                year = in.readByte() + 1850 + 128;
                break;
            case 2:
                year = in.readShort();
                break;
            case 3:
                year = in.readInt();
                break;
            default:
                throw new StreamCorruptedException("Unknown year range.");
        }

        return PlainDate.of(year, Month.valueOf(month), day);

    }

    private void writeTime(ObjectOutput out)
        throws IOException {

        PlainTime time = (PlainTime) this.obj;
        out.writeByte(TIME_TYPE << 4);

        if (time.getNanosecond() == 0) {
            if (time.getSecond() == 0) {
                if (time.getMinute() == 0) {
                    out.writeByte(~time.getHour());
                } else {
                    out.writeByte(time.getHour());
                    out.writeByte(~time.getMinute());
                }
            } else {
                out.writeByte(time.getHour());
                out.writeByte(time.getMinute());
                out.writeByte(~time.getSecond());
            }
        } else {
            out.writeByte(time.getHour());
            out.writeByte(time.getMinute());
            out.writeByte(time.getSecond());
            out.writeInt(time.getNanosecond());
        }

    }

    private Object readTime(ObjectInput in)
        throws IOException {

        int minute = 0, second = 0, nano = 0;
        int hour = in.readByte();

        if (hour < 0) {
            return PlainTime.of(~hour);
        } else {
            minute = in.readByte();

            if (minute < 0) {
                minute = ~minute;
            } else {
                second = in.readByte();

                if (second < 0) {
                    second = ~second;
                } else {
                    nano = in.readInt();
                }
            }

            return new PlainTime(hour, minute, second, nano);
        }

    }

    private void writeWeekmodel(ObjectOutput out)
        throws IOException {

        Weekmodel wm = (Weekmodel) this.obj;

        boolean isoWeekend = (
            (wm.getStartOfWeekend() == Weekday.SATURDAY)
            && (wm.getEndOfWeekend() == Weekday.SUNDAY)
        );

        int header = WEEKMODEL_TYPE;
        header <<= 4;
        if (!isoWeekend) {
            header |= 1;
        }
        out.writeByte(header);

        int state = wm.getFirstDayOfWeek().getValue();
        state <<= 4;
        state |= wm.getMinimalDaysInFirstWeek();
        out.writeByte(state);

        if (!isoWeekend) {
            state = wm.getStartOfWeekend().getValue();
            state <<= 4;
            state |= wm.getEndOfWeekend().getValue();
            out.writeByte(state);
        }

    }

    private Object readWeekmodel(
        ObjectInput in,
        byte header
    ) throws IOException {

        int data = in.readByte();
        Weekday firstDayOfWeek = Weekday.valueOf(data >> 4);
        int minimalDaysInFirstWeek = (data & 0xF);

        Weekday startOfWeekend = Weekday.SATURDAY;
        Weekday endOfWeekend = Weekday.SUNDAY;

        if ((header & 0xF) == 1) {
            data = in.readByte();
            startOfWeekend = Weekday.valueOf(data >> 4);
            endOfWeekend = Weekday.valueOf(data & 0xF);
        }

        return Weekmodel.of(
            firstDayOfWeek,
            minimalDaysInFirstWeek,
            startOfWeekend,
            endOfWeekend
        );

    }

    private void writeMoment(ObjectOutput out)
        throws IOException {

        Moment ut = (Moment) this.obj;
        ut.writeTimestamp(out);

    }

    private Object readMoment(
        ObjectInput in,
        byte header
    ) throws IOException {

        int lsBit = (header & 0x1);
        int fractionBit = ((header & 0x2) >>> 1);

        if (lsBit < 0 || fractionBit < 0 || lsBit > 1 || fractionBit > 1) {
            throw new StreamCorruptedException();
        }

        boolean positiveLS = (lsBit != 0);
        boolean hasNanos = (fractionBit != 0);

        return Moment.readTimestamp(in, positiveLS, hasNanos);

    }

    private void writeTimestamp(ObjectOutput out)
        throws IOException {

        PlainTimestamp ts = (PlainTimestamp) this.obj;
        out.writeByte(TIMESTAMP_TYPE << 4);
        out.writeObject(ts.getCalendarDate());
        out.writeObject(ts.getWallTime());

    }

    private Object readTimestamp(ObjectInput in)
        throws IOException, ClassNotFoundException {

        Object date = in.readObject();
        Object time = in.readObject();

        if (
            (date instanceof PlainDate)
            && (time instanceof PlainTime)
        ) {
            return new PlainTimestamp(
                (PlainDate) date,
                (PlainTime) time
            );
        } else {
            throw new InvalidObjectException("Missing date or time object.");
        }

    }

}