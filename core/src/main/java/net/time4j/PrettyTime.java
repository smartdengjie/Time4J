/*
 * -----------------------------------------------------------------------
 * Copyright © 2013-2014 Meno Hochschild, <http://www.menodata.de/>
 * -----------------------------------------------------------------------
 * This file (PrettyTime.java) is part of project Time4J.
 *
 * Time4J is free software: You can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * Time4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Time4J. If not, see <http://www.gnu.org/licenses/>.
 * -----------------------------------------------------------------------
 */

package net.time4j;

import net.time4j.base.MathUtils;
import net.time4j.base.TimeSource;
import net.time4j.base.UnixTime;
import net.time4j.engine.TimeSpan;
import net.time4j.format.NumberSymbolProvider;
import net.time4j.format.NumberType;
import net.time4j.format.PluralCategory;
import net.time4j.format.PluralRules;
import net.time4j.format.TextWidth;
import net.time4j.format.UnitPatterns;
import net.time4j.tz.TZID;
import net.time4j.tz.Timezone;
import net.time4j.tz.ZonalOffset;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static net.time4j.CalendarUnit.DAYS;
import static net.time4j.CalendarUnit.MONTHS;
import static net.time4j.CalendarUnit.WEEKS;
import static net.time4j.CalendarUnit.YEARS;
import static net.time4j.ClockUnit.HOURS;
import static net.time4j.ClockUnit.MICROS;
import static net.time4j.ClockUnit.MILLIS;
import static net.time4j.ClockUnit.MINUTES;
import static net.time4j.ClockUnit.NANOS;
import static net.time4j.ClockUnit.SECONDS;


/**
 * <p>Enables formatted output as usually used in social media in different
 * languages. </p>
 *
 * <p>Parsing is not included because there is no general solution for all
 * locales. Instead users must keep the backing duration object and use it
 * for printing. </p>
 *
 * @author  Meno Hochschild
 * @since   1.2
 * @concurrency <immutable>
 */
/*[deutsch]
 * <p>Erm&ouml;glicht formatierte Ausgaben einer Dauer f&uuml;r soziale Medien
 * (&quot;social media style&quot;) in verschiedenen Sprachen. </p>
 *
 * <p>Der R&uuml;ckweg der Interpretation (<i>parsing</i>) ist nicht enthalten,
 * weil so nicht alle Sprachen unterst&uuml;tzt werden k&ouml;nnen. Stattdessen
 * werden Anwender angehalten, das korrespondierende Dauer-Objekt im Hintergrund
 * zu halten und es f&uuml;r die formatierte Ausgabe zu nutzen. </p>
 *
 * @author  Meno Hochschild
 * @since   1.2
 * @concurrency <immutable>
 */
public final class PrettyTime {

    //~ Statische Felder/Initialisierungen --------------------------------

    private static final NumberSymbolProvider NUMBER_SYMBOLS;

    static {
        NumberSymbolProvider p = null;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        if (cl == null) {
            cl = NumberSymbolProvider.class.getClassLoader();
        }

        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }

        for (
            NumberSymbolProvider tmp
            : ServiceLoader.load(NumberSymbolProvider.class, cl)
        ) {
            p = tmp;
            break;
        }

        if (p == null) {
            p = NumberSymbolProvider.DEFAULT;
        }

        NUMBER_SYMBOLS = p;
    }

    private static final int MIO = 1000000;

    private static final ConcurrentMap<Locale, PrettyTime> LANGUAGE_MAP =
        new ConcurrentHashMap<Locale, PrettyTime>();
    private static final IsoUnit[] STD_UNITS;
    private static final IsoUnit[] TSP_UNITS;
    private static final Set<IsoUnit> SUPPORTED_UNITS;

    static {
        IsoUnit[] stdUnits =
            {YEARS, MONTHS, WEEKS, DAYS, HOURS, MINUTES, SECONDS};
        STD_UNITS = stdUnits;
        IsoUnit[] tspUnits =
            {YEARS, MONTHS, DAYS, HOURS, MINUTES, SECONDS};
        TSP_UNITS = tspUnits;

        Set<IsoUnit> tmp = new HashSet<IsoUnit>();
        for (IsoUnit unit : stdUnits) {
            tmp.add(unit);
        }
        tmp.add(NANOS);
        SUPPORTED_UNITS = Collections.unmodifiableSet(tmp);
    }

    //~ Instanzvariablen --------------------------------------------------

    private final PluralRules rules;
    private final Locale locale;
    private final TimeSource<?> refClock;
    private final char zeroDigit;
    private final IsoUnit emptyUnit;
    private final String minusSign;
    private final boolean weekToDays;

    //~ Konstruktoren -----------------------------------------------------

    private PrettyTime(
        Locale loc,
        TimeSource<?> refClock,
        char zeroDigit,
        String minusSign,
        IsoUnit emptyUnit,
        boolean weekToDays
    ) {
        super();

        if (emptyUnit == null) {
            throw new NullPointerException("Missing zero time unit.");
        } else if (refClock == null) {
            throw new NullPointerException("Missing reference clock.");
        }

        // throws NPE if language == null
        this.rules = PluralRules.of(loc, NumberType.CARDINALS);
        this.locale = loc;
        this.refClock = refClock;
        this.zeroDigit = zeroDigit;
        this.emptyUnit = emptyUnit;
        this.minusSign = minusSign;
        this.weekToDays = weekToDays;

    }

    //~ Methoden ----------------------------------------------------------

    /**
     * <p>Gets an instance of {@code PrettyTime} for given language,
     * possibly cached. </p>
     *
     * @param   locale    the language an instance is searched for
     * @return  pretty time object for formatting durations or relative time
     * @since   1.2
     */
    /*[deutsch]
     * <p>Liefert eine Instanz von {@code PrettyTime} f&uuml;r die angegebene
     * Sprache, eventuell aus dem Cache. </p>
     *
     * @param   locale    the language an instance is searched for
     * @return  pretty time object for formatting durations or relative time
     * @since   1.2
     */
    public static PrettyTime of(Locale locale) {

        PrettyTime ptime = LANGUAGE_MAP.get(locale);

        if (ptime == null) {
            ptime =
                new PrettyTime(
                    locale,
                    SystemClock.INSTANCE,
                    NUMBER_SYMBOLS.getZeroDigit(locale),
                    NUMBER_SYMBOLS.getMinusSign(locale),
                    SECONDS,
                    false);
            PrettyTime old = LANGUAGE_MAP.putIfAbsent(locale, ptime);

            if (old != null) {
                ptime = old;
            }
        }

        return ptime;

    }

    /**
     * <p>Gets the language of this instance. </p>
     *
     * @return  language
     * @since   1.2
     */
    /*[deutsch]
     * <p>Liefert die Bezugssprache. </p>
     *
     * @return  Spracheinstellung
     * @since   1.2
     */
    public Locale getLocale() {

        return this.locale;

    }

    /**
     * <p>Yields the reference clock for formatting of relative times. </p>
     *
     * @return  reference clock or system clock if not yet specified
     * @since   1.2
     * @see     #withReferenceClock(TimeSource)
     * @see     #printRelative(UnixTime, TZID)
     * @see     #printRelative(UnixTime, String)
     */
    /*[deutsch]
     * <p>Liefert die Bezugsuhr f&uuml;r formatierte Ausgaben der relativen
     * Zeit. </p>
     *
     * @return  Zeitquelle oder die Systemuhr, wenn noch nicht angegeben
     * @since   1.2
     * @see     #withReferenceClock(TimeSource)
     * @see     #printRelative(UnixTime, TZID)
     * @see     #printRelative(UnixTime, String)
     */
    public TimeSource<?> getReferenceClock() {

        return this.refClock;

    }

    /**
     * <p>Yields a changed copy of this instance with given reference
     * clock. </p>
     *
     * @param   clock   new reference clock
     * @return  new instance of {@code PrettyTime} with changed reference clock
     * @since   1.2
     * @see     #getReferenceClock()
     * @see     #printRelative(UnixTime, TZID)
     * @see     #printRelative(UnixTime, String)
     */
    /*[deutsch]
     * <p>Legt die Bezugszeit f&uuml;r relative Zeitangaben neu fest. </p>
     *
     * @param   clock   new reference clock
     * @return  new instance of {@code PrettyTime} with changed reference clock
     * @since   1.2
     * @see     #getReferenceClock()
     * @see     #printRelative(UnixTime, TZID)
     * @see     #printRelative(UnixTime, String)
     */
    public PrettyTime withReferenceClock(TimeSource<?> clock) {

        return new PrettyTime(
            this.locale,
            clock,
            this.zeroDigit,
            this.minusSign,
            this.emptyUnit,
            this.weekToDays);

    }

    /**
     * <p>Defines the localized zero digit. </p>
     *
     * <p>In most languages the zero digit is just ASCII-&quot;0&quot;,
     * but for example in arabic locales the digit can also be the char
     * {@code U+0660}. By default Time4J will try to use the configuration
     * of the module i18n or else the JDK-setting. This method can override
     * it however. </p>
     *
     * @param   zeroDigit   localized zero digit
     * @return  changed copy of this instance
     * @since   1.2
     * @see     java.text.DecimalFormatSymbols#getZeroDigit()
     * @see     net.time4j.format.NumberSymbolProvider#getZeroDigit(Locale)
     */
    /*[deutsch]
     * <p>Definiert die lokalisierte Nullziffer. </p>
     *
     * <p>In den meisten Sprachen ist die Nullziffer ASCII-&quot;0&quot;,
     * aber etwa im arabischen Sprachraum kann das Zeichen auch {@code U+0660}
     * sein. Per Vorgabe wird Time4J versuchen, die Konfiguration des
     * i18n-Moduls oder sonst die JDK-Einstellung zu verwenden. Diese
     * Methode &uuml;berschreibt jedoch den Standard. </p>
     *
     * @param   zeroDigit   localized zero digit
     * @return  changed copy of this instance
     * @since   1.2
     * @see     java.text.DecimalFormatSymbols#getZeroDigit()
     * @see     net.time4j.format.NumberSymbolProvider#getZeroDigit(Locale)
     */
    public PrettyTime withZeroDigit(char zeroDigit) {

        if (this.zeroDigit == zeroDigit) {
            return this;
        }

        return new PrettyTime(
            this.locale,
            this.refClock,
            zeroDigit,
            this.minusSign,
            this.emptyUnit,
            this.weekToDays);

    }

    /**
     * <p>Defines the localized minus sign. </p>
     *
     * <p>In most languages the minus sign is just {@code U+002D}. By default
     * Time4J will try to use the configuration of the module i18n or else the
     * JDK-setting. This method can override it however. Especially for arabic,
     * it might make sense to first add a unicode marker (either LRM
     * {@code U+200E} or RLM {@code U+200F}) in front of the minus sign
     * in order to control the orientation in right-to-left-style. </p>
     *
     * @param   minusSign   localized minus sign (possibly with unicode markers)
     * @return  changed copy of this instance
     * @since   2.1
     * @see     java.text.DecimalFormatSymbols#getMinusSign()
     * @see     net.time4j.format.NumberSymbolProvider#getMinusSign(Locale)
     */
    /*[deutsch]
     * <p>Definiert das lokalisierte Minuszeichen. </p>
     *
     * <p>In den meisten Sprachen ist es einfach das Zeichen {@code U+002D}.
     * Per Vorgabe wird Time4J versuchen, die Konfiguration des
     * i18n-Moduls oder sonst die JDK-Einstellung zu verwenden. Diese
     * Methode &uuml;berschreibt jedoch den Standard. Besonders f&uuml;r
     * Arabisch kann es sinnvoll sein, vor dem eigentlichen Minuszeichen
     * einen Unicode-Marker (entweder LRM {@code U+200E} oder RLM
     * {@code U+200F}) einzuf&uuml;gen, um die Orientierung des Minuszeichens
     * in der traditionellen Rechts-nach-links-Schreibweise zu
     * kontrollieren. </p>
     *
     * @param   minusSign   localized minus sign (possibly with unicode markers)
     * @return  changed copy of this instance
     * @since   2.1
     * @see     java.text.DecimalFormatSymbols#getMinusSign()
     * @see     net.time4j.format.NumberSymbolProvider#getMinusSign(Locale)
     */
    public PrettyTime withMinusSign(String minusSign) {

        if (minusSign.equals(this.minusSign)) { // NPE-check
            return this;
        }

        return new PrettyTime(
            this.locale,
            this.refClock,
            this.zeroDigit,
            minusSign,
            this.emptyUnit,
            this.weekToDays);

    }

    /**
     * <p>Defines the time unit used for formatting an empty duration. </p>
     *
     * <p>Time4J uses seconds as default. This method can override the
     * default however. </p>
     *
     * @param   emptyUnit   time unit for usage in an empty duration
     * @return  changed copy of this instance
     * @since   1.2
     * @see     #print(Duration, TextWidth)
     */
    /*[deutsch]
     * <p>Definiert die Zeiteinheit f&uuml;r die Verwendung in der
     * Formatierung einer leeren Dauer. </p>
     *
     * <p>Vorgabe ist die Sekundeneinheit. Diese Methode kann die Vorgabe
     * jedoch &uuml;berschreiben. </p>
     *
     * @param   emptyUnit   time unit for usage in an empty duration
     * @return  changed copy of this instance
     * @since   1.2
     * @see     #print(Duration, TextWidth)
     */
    public PrettyTime withEmptyUnit(CalendarUnit emptyUnit) {

        if (this.emptyUnit.equals(emptyUnit)) {
            return this;
        }

        return new PrettyTime(
            this.locale,
            this.refClock,
            this.zeroDigit,
            this.minusSign,
            emptyUnit,
            this.weekToDays);

    }

    /**
     * <p>Defines the time unit used for formatting an empty duration. </p>
     *
     * <p>Time4J uses seconds as default. This method can override the
     * default however. </p>
     *
     * @param   emptyUnit   time unit for usage in an empty duration
     * @return  changed copy of this instance
     * @since   1.2
     * @see     #print(Duration, TextWidth)
     */
    /*[deutsch]
     * <p>Definiert die Zeiteinheit f&uuml;r die Verwendung in der
     * Formatierung einer leeren Dauer. </p>
     *
     * <p>Vorgabe ist die Sekundeneinheit. Diese Methode kann die Vorgabe
     * jedoch &uuml;berschreiben. </p>
     *
     * @param   emptyUnit   time unit for usage in an empty duration
     * @return  changed copy of this instance
     * @since   1.2
     * @see     #print(Duration, TextWidth)
     */
    public PrettyTime withEmptyUnit(ClockUnit emptyUnit) {

        if (this.emptyUnit.equals(emptyUnit)) {
            return this;
        }

        return new PrettyTime(
            this.locale,
            this.refClock,
            this.zeroDigit,
            this.minusSign,
            emptyUnit,
            this.weekToDays);

    }

    /**
     * <p>Determines that weeks will always be normalized to days. </p>
     *
     * @return  changed copy of this instance
     * @since   2.0
     */
    /*[deutsch]
     * <p>Legt fest, da&szlig; Wochen immer zu Tagen normalisiert werden. </p>
     *
     * @return  changed copy of this instance
     * @since   2.0
     */
    public PrettyTime withWeeksToDays() {

        if (this.weekToDays) {
            return this;
        }

        return new PrettyTime(
            this.locale,
            this.refClock,
            this.zeroDigit,
            this.minusSign,
            this.emptyUnit,
            true);

    }

    /**
     * <p>Formats given duration in calendar units. </p>
     *
     * <p>Note: Millennia, centuries and decades are automatically normalized
     * to years while quarter-years are normalized to months. </p>
     *
     * @param   amount  count of units (quantity)
     * @param   unit    calendar unit
     * @param   width   text width (ABBREVIATED as synonym for SHORT)
     * @return  formatted output
     * @since   1.2
     * @see     #print(Duration, TextWidth)
     */
    /*[deutsch]
     * <p>Formatiert die angegebene Dauer in kalendarischen Zeiteinheiten. </p>
     *
     * <p>Hinweis: Jahrtausende, Jahrhunderte und Dekaden werden automatisch
     * zu Jahren normalisiert, w&auml;hrend Quartale zu Monaten normalisiert
     * werden. </p>
     *
     * @param   amount  Anzahl der Einheiten
     * @param   unit    kalendarische Zeiteinheit
     * @param   width   text width (ABBREVIATED as synonym for SHORT)
     * @return  formatierte Ausgabe
     * @since   1.2
     * @see     #print(Duration, TextWidth)
     */
    public String print(
        long amount,
        CalendarUnit unit,
        TextWidth width
    ) {

        UnitPatterns p = UnitPatterns.of(this.locale);
        String pattern;

        switch (unit) {
            case MILLENNIA:
                amount = MathUtils.safeMultiply(amount, 1000);
                pattern = p.getYears(width, this.getCategory(amount));
                break;
            case CENTURIES:
                amount = MathUtils.safeMultiply(amount, 100);
                pattern = p.getYears(width, this.getCategory(amount));
                break;
            case DECADES:
                amount = MathUtils.safeMultiply(amount, 10);
                pattern = p.getYears(width, this.getCategory(amount));
                break;
            case YEARS:
                pattern = p.getYears(width, this.getCategory(amount));
                break;
            case QUARTERS:
                amount = MathUtils.safeMultiply(amount, 3);
                pattern = p.getMonths(width, this.getCategory(amount));
                break;
            case MONTHS:
                pattern = p.getMonths(width, this.getCategory(amount));
                break;
            case WEEKS:
                if (this.weekToDays) {
                    amount = MathUtils.safeMultiply(amount, 7);
                    pattern = p.getDays(width, this.getCategory(amount));
                } else {
                    pattern = p.getWeeks(width, this.getCategory(amount));
                }
                break;
            case DAYS:
                pattern = p.getDays(width, this.getCategory(amount));
                break;
            default:
                throw new UnsupportedOperationException(unit.name());
        }

        return this.format(pattern, amount);

    }

    /**
     * <p>Formats given duration in clock units. </p>
     *
     * @param   amount  count of units (quantity)
     * @param   unit    clock unit
     * @param   width   text width (ABBREVIATED as synonym for SHORT)
     * @return  formatted output
     * @since   1.2
     * @see     #print(Duration, TextWidth)
     */
    /*[deutsch]
     * <p>Formatiert die angegebene Dauer in Uhrzeiteinheiten. </p>
     *
     * @param   amount  Anzahl der Einheiten
     * @param   unit    Uhrzeiteinheit
     * @param   width   text width (ABBREVIATED as synonym for SHORT)
     * @return  formatierte Ausgabe
     * @since   1.2
     * @see     #print(Duration, TextWidth)
     */
    public String print(
        long amount,
        ClockUnit unit,
        TextWidth width
    ) {

        UnitPatterns p = UnitPatterns.of(this.locale);
        String pattern;

        switch (unit) {
            case HOURS:
                pattern = p.getHours(width, this.getCategory(amount));
                break;
            case MINUTES:
                pattern = p.getMinutes(width, this.getCategory(amount));
                break;
            case SECONDS:
                pattern = p.getSeconds(width, this.getCategory(amount));
                break;
            case MILLIS:
                pattern = p.getMillis(width, this.getCategory(amount));
                break;
            case MICROS:
                pattern = p.getMicros(width, this.getCategory(amount));
                break;
            case NANOS:
                pattern = p.getNanos(width, this.getCategory(amount));
                break;
            default:
                throw new UnsupportedOperationException(unit.name());
        }

        return this.format(pattern, amount);

    }

    /**
     * <p>Formats the total given duration. </p>
     *
     * <p>A localized output is only supported for the units
     * {@link CalendarUnit#YEARS}, {@link CalendarUnit#MONTHS},
     * {@link CalendarUnit#WEEKS}, {@link CalendarUnit#DAYS} and
     * all {@link ClockUnit}-units. This method performs an internal
     * normalization if any other unit is involved. </p>
     *
     * <p>Note: If the local script variant is from right to left
     * then a unicode-RLM-marker will automatically be inserted
     * before each number. </p>
     *
     * @param   duration    object representing a duration which might contain
     *                      several units and quantities
     * @param   width       text width (ABBREVIATED as synonym for SHORT)
     * @return  formatted list output
     * @since   1.2
     */
    /*[deutsch]
     * <p>Formatiert die gesamte angegebene Dauer. </p>
     *
     * <p>Eine lokalisierte Ausgabe ist nur f&uuml;r die Zeiteinheiten
     * {@link CalendarUnit#YEARS}, {@link CalendarUnit#MONTHS},
     * {@link CalendarUnit#WEEKS}, {@link CalendarUnit#DAYS} und
     * alle {@link ClockUnit}-Instanzen vorhanden. Bei Bedarf werden
     * andere Einheiten zu diesen normalisiert. </p>
     *
     * <p>Hinweis: Wenn die lokale Skript-Variante von rechts nach links
     * geht, wird automatisch ein Unicode-RLM-Marker vor jeder Nummer
     * eingef&uuml;gt. </p>
     *
     * @param   duration    object representing a duration which might contain
     *                      several units and quantities
     * @param   width       text width (ABBREVIATED as synonym for SHORT)
     * @return  formatted list output
     * @since   1.2
     */
    public String print(
        Duration<?> duration,
        TextWidth width
    ) {

        return this.print(duration, width, false, Integer.MAX_VALUE);

    }

    /**
     * <p>Formats given duration. </p>
     *
     * <p>Like {@link #print(Duration, TextWidth)}, but offers the
     * option to limit the count of displayed duration items and also
     * to print items with zero amount. The first printed duration item
     * has always a non-zero amount however. Example: </p>
     *
     * <pre>
     *  Duration&lt;?&gt; dur =
     *      Duration.ofZero().plus(1, DAYS).plus(4, ClockUnit.MINUTES);
     *  System.out.println(
     *      PrettyTime.of(Locale.FRANCE).print(dur, TextWidth.WIDE, true, 3));
     *  // output: 1 jour, 0 heure et 4 minutes
     * </pre>
     *
     * @param   duration    object representing a duration which might contain
     *                      several units and quantities
     * @param   width       text width (ABBREVIATED as synonym for SHORT)
     * @param   printZero   determines if zero amounts shall be printed, too
     * @param   maxLength   maximum count of displayed items
     * @return  formatted list output
     * @throws  IllegalArgumentException if maxLength is smaller than {@code 1}
     * @since   2.0
     */
    /*[deutsch]
     * <p>Formatiert die angegebene Dauer. </p>
     *
     * <p>Wie {@link #print(Duration, TextWidth)}, aber mit der Option, die
     * Anzahl der Dauerelemente zu begrenzen und auch Elemente mit dem
     * Betrag {@code 0} auszugeben. Das erste ausgegebene Element hat aber
     * immer einen Betrag ungleich {@code 0}. Beispiel: </p>
     *
     * <pre>
     *  Duration&lt;?&gt; dur =
     *      Duration.ofZero().plus(1, DAYS).plus(4, ClockUnit.MINUTES);
     *  System.out.println(
     *      PrettyTime.of(Locale.FRANCE).print(dur, TextWidth.WIDE, true, 3));
     *  // output: 1 jour, 0 heure et 4 minutes
     * </pre>
     *
     * @param   duration    object representing a duration which might contain
     *                      several units and quantities
     * @param   width       text width (ABBREVIATED as synonym for SHORT)
     * @param   printZero   determines if zero amounts shall be printed, too
     * @param   maxLength   maximum count of displayed items
     * @return  formatted list output
     * @throws  IllegalArgumentException if maxLength is smaller than {@code 1}
     * @since   2.0
     */
    public String print(
        Duration<?> duration,
        TextWidth width,
        boolean printZero,
        int maxLength
    ) {

        if (maxLength < 1) {
            throw new IllegalArgumentException(
                "Max length is invalid: " + maxLength);
        }

        // special case of empty duration
        if (duration.isEmpty()) {
            if (this.emptyUnit.isCalendrical()) {
                CalendarUnit unit = CalendarUnit.class.cast(this.emptyUnit);
                return this.print(0, unit, width);
            } else {
                ClockUnit unit = ClockUnit.class.cast(this.emptyUnit);
                return this.print(0, unit, width);
            }
        }

        // fill values-array from duration
        boolean negative = duration.isNegative();
        long[] values = new long[8];
        pushDuration(values, duration, this.refClock, this.weekToDays);

        // format duration items
        List<Object> parts = new ArrayList<Object>();
        int count = 0;

        for (int i = 0; i < values.length; i++) {
            if (
                (count < maxLength)
                && (!this.weekToDays || (i != 2))
                && ((printZero && (count > 0)) || (values[i] > 0))
            ) {
                IsoUnit unit = ((i == 7) ? NANOS : STD_UNITS[i]);
                parts.add(this.format(values[i], unit, negative, width));
                count++;
            }
        }

        // duration is not empty here
        assert (count > 0);

        // special case of only one item
        if (count == 1) {
            return parts.get(0).toString();
        }

        // multiple items >= 2
        return MessageFormat.format(
            UnitPatterns.of(this.locale).getListPattern(width, count),
            parts.toArray(new Object[count]));

    }

    /**
     * <p>Formats given time point relative to the current time of
     * {@link #getReferenceClock()} as duration in at most second
     * precision or less. </p>
     *
     * @param   moment      relative time point
     * @param   tzid        time zone id for translating to a local duration
     * @return  formatted output of relative time, either in past or in future
     * @since   1.2
     */
    /*[deutsch]
     * <p>Formatiert den angegebenen Zeitpunkt relativ zur aktuellen Zeit
     * der Referenzuhr {@link #getReferenceClock()} als Dauer in maximal
     * Sekundengenauigkeit. </p>
     *
     * @param   moment      relative time point
     * @param   tzid        time zone id for translating to a local duration
     * @return  formatted output of relative time, either in past or in future
     * @since   1.2
     */
    public String printRelative(
        UnixTime moment,
        TZID tzid
    ) {

        return this.print(moment, Timezone.of(tzid));

    }

    /**
     * <p>Formats given time point relative to the current time of
     * {@link #getReferenceClock()} as duration in at most second
     * precision or less. </p>
     *
     * @param   moment      relative time point
     * @param   tzid        time zone id for translating to a local duration
     * @return  formatted output of relative time, either in past or in future
     * @since   1.2
     */
    /*[deutsch]
     * <p>Formatiert den angegebenen Zeitpunkt relativ zur aktuellen Zeit
     * der Referenzuhr {@link #getReferenceClock()} als Dauer in maximal
     * Sekundengenauigkeit. </p>
     *
     * @param   moment      relative time point
     * @param   tzid        time zone id for translating to a local duration
     * @return  formatted output of relative time, either in past or in future
     * @since   1.2
     */
    public String printRelative(
        UnixTime moment,
        String tzid
    ) {

        return this.print(moment, Timezone.of(tzid));

    }

    private String print(
        UnixTime ut,
        Timezone tz
    ) {

        UnixTime ref = this.getReferenceClock().currentTime();

        PlainTimestamp start =
            PlainTimestamp.from(
                ref,
                tz.getOffset(ref));
        PlainTimestamp end =
            PlainTimestamp.from(
                ut,
                tz.getOffset(ut));

        IsoUnit[] units = (this.weekToDays ? TSP_UNITS : STD_UNITS);
        Duration<IsoUnit> duration =
            Duration.in(tz, units).between(start, end);

        if (duration.isEmpty()) {
            return UnitPatterns.of(this.locale).getNowWord();
        }

        TimeSpan.Item<IsoUnit> item = duration.getTotalLength().get(0);
        long amount = item.getAmount();
        IsoUnit unit = item.getUnit();
        String pattern;

        if (duration.isNegative()) {
            if (unit.isCalendrical()) {
                pattern = this.getPastPattern(amount, (CalendarUnit) unit);
            } else {
                pattern = this.getPastPattern(amount, (ClockUnit) unit);
            }
        } else {
            if (unit.isCalendrical()) {
                pattern = this.getFuturePattern(amount, (CalendarUnit) unit);
            } else {
                pattern = this.getFuturePattern(amount, (ClockUnit) unit);
            }
        }

        return this.format(pattern, amount);

    }

    private String getPastPattern(
        long amount,
        CalendarUnit unit
    ) {

        UnitPatterns patterns = UnitPatterns.of(this.locale);
        PluralCategory category = this.getCategory(amount);

        switch (unit) {
            case YEARS:
                return patterns.getYearsInPast(category);
            case MONTHS:
                return patterns.getMonthsInPast(category);
            case WEEKS:
                return patterns.getWeeksInPast(category);
            case DAYS:
                return patterns.getDaysInPast(category);
            default:
                throw new UnsupportedOperationException(unit.name());
        }

    }

    private String getFuturePattern(
        long amount,
        CalendarUnit unit
    ) {

        UnitPatterns patterns = UnitPatterns.of(this.locale);
        PluralCategory category = this.getCategory(amount);

        switch (unit) {
            case YEARS:
                return patterns.getYearsInFuture(category);
            case MONTHS:
                return patterns.getMonthsInFuture(category);
            case WEEKS:
                return patterns.getWeeksInFuture(category);
            case DAYS:
                return patterns.getDaysInFuture(category);
            default:
                throw new UnsupportedOperationException(unit.name());
        }

    }

    private String getPastPattern(
        long amount,
        ClockUnit unit
    ) {

        UnitPatterns patterns = UnitPatterns.of(this.locale);
        PluralCategory category = this.getCategory(amount);

        switch (unit) {
            case HOURS:
                return patterns.getHoursInPast(category);
            case MINUTES:
                return patterns.getMinutesInPast(category);
            case SECONDS:
                return patterns.getSecondsInPast(category);
            default:
                throw new UnsupportedOperationException(unit.name());
        }

    }

    private String getFuturePattern(
        long amount,
        ClockUnit unit
    ) {

        UnitPatterns patterns = UnitPatterns.of(this.locale);
        PluralCategory category = this.getCategory(amount);

        switch (unit) {
            case HOURS:
                return patterns.getHoursInFuture(category);
            case MINUTES:
                return patterns.getMinutesInFuture(category);
            case SECONDS:
                return patterns.getSecondsInFuture(category);
            default:
                throw new UnsupportedOperationException(unit.name());
        }

    }

    private PluralCategory getCategory(long amount) {

        return this.rules.getCategory(Math.abs(amount));

    }

    private static void pushDuration(
        long[] values,
        Duration<?> duration,
        TimeSource<?> refClock,
        boolean weekToDays
    ) {

        int len = duration.getTotalLength().size();

        for (int i = 0; i < len; i++) {
            TimeSpan.Item<? extends IsoUnit> item =
                duration.getTotalLength().get(i);
            IsoUnit unit = item.getUnit();
            long amount = item.getAmount();

            if (unit instanceof CalendarUnit) {
                push(values, CalendarUnit.class.cast(unit), amount, weekToDays);
            } else if (unit instanceof ClockUnit) {
                push(values, ClockUnit.class.cast(unit), amount);
            } else if (unit instanceof OverflowUnit) {
                push(
                    values,
                    OverflowUnit.class.cast(unit).getCalendarUnit(),
                    amount,
                    weekToDays);
            } else if (unit.equals(CalendarUnit.weekBasedYears())) {
                values[0] = MathUtils.safeAdd(amount, values[0]); // YEARS
            } else { // approximated duration by normalization without nanos
                Moment unix = Moment.from(refClock.currentTime());
                PlainTimestamp start = unix.toZonalTimestamp(ZonalOffset.UTC);
                PlainTimestamp end = start.plus(amount, unit);
                IsoUnit[] units = (weekToDays ? TSP_UNITS : STD_UNITS);
                Duration<?> part = Duration.in(units).between(start, end);
                pushDuration(values, part, refClock, weekToDays);
            }
        }

    }

    private static void push(
        long[] values,
        CalendarUnit unit,
        long amount,
        boolean weekToDays
    ) {

        int index;

        switch (unit) {
            case MILLENNIA:
                amount = MathUtils.safeMultiply(amount, 1000);
                index = 0; // YEARS
                break;
            case CENTURIES:
                amount = MathUtils.safeMultiply(amount, 100);
                index = 0; // YEARS
                break;
            case DECADES:
                amount = MathUtils.safeMultiply(amount, 10);
                index = 0; // YEARS
                break;
            case YEARS:
                index = 0; // YEARS
                break;
            case QUARTERS:
                amount = MathUtils.safeMultiply(amount, 3);
                index = 1; // MONTHS
                break;
            case MONTHS:
                index = 1; // MONTHS
                break;
            case WEEKS:
                if (weekToDays) {
                    amount = MathUtils.safeMultiply(amount, 7);
                    index = 3; // DAYS
                } else {
                    index = 2; // WEEKS
                }
                break;
            case DAYS:
                index = 3; // DAYS
                break;
            default:
                throw new UnsupportedOperationException(unit.name());
        }

        values[index] = MathUtils.safeAdd(amount, values[index]);

    }

    private static void push(
        long[] values,
        ClockUnit unit,
        long amount
    ) {

        int index;

        switch (unit) {
            case HOURS:
                index = 4;
                break;
            case MINUTES:
                index = 5;
                break;
            case SECONDS:
                index = 6;
                break;
            case MILLIS:
                amount = MathUtils.safeMultiply(amount, MIO);
                index = 7; // NANOS
                break;
            case MICROS:
                amount = MathUtils.safeMultiply(amount, 1000);
                index = 7; // NANOS
                break;
            case NANOS:
                index = 7; // NANOS
                break;
            default:
                throw new UnsupportedOperationException(unit.name());
        }

        values[index] = MathUtils.safeAdd(amount, values[index]);

    }

    private String format(
        long amount,
        IsoUnit unit,
        boolean negative,
        TextWidth width
    ) {

        long value = amount;

        if (negative) {
            value = MathUtils.safeNegate(amount);
        }

        if (SUPPORTED_UNITS.contains(unit)) {
            if (unit.isCalendrical()) {
                CalendarUnit u = CalendarUnit.class.cast(unit);
                return this.print(value, u, width);
            } else {
                ClockUnit u = ClockUnit.class.cast(unit);
                if (u == NANOS) {
                    if ((amount % MIO) == 0) {
                        u = MILLIS;
                        value = value / MIO;
                    } else if ((amount % 1000) == 0) {
                        u = MICROS;
                        value = value / 1000;
                    }
                }
                return this.print(value, u, width);
            }
        }

        throw new UnsupportedOperationException("Unknown unit: " + unit);

    }

    private String format(
        String pattern,
        long amount
    ) {

        for (int i = 0, n = pattern.length(); i < n; i++) {
            if (
                (i < n - 2)
                && (pattern.charAt(i) == '{')
                && (pattern.charAt(i + 1) == '0')
                && (pattern.charAt(i + 2) == '}')
            ) {
                StringBuilder sb = new StringBuilder(pattern);
                sb.replace(i, i + 3, this.format(amount));
                return sb.toString();
            }
        }

        return pattern;

    }

    private String format(long amount) {

        String num = String.valueOf(Math.abs(amount));
        char zero = this.zeroDigit;
        StringBuilder sb = new StringBuilder();

        if (amount < 0) {
            sb.append(this.minusSign);
        }

        for (int i = 0, n = num.length(); i < n; i++) {
            char c = num.charAt(i);
            if (zero != '0') {
                c = (char) (c + zero - '0');
            }
            sb.append(c);
        }

        return sb.toString();

    }

}
