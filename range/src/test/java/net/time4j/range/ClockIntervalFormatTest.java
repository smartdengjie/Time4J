package net.time4j.range;

import net.time4j.Iso8601Format;
import net.time4j.PlainTime;
import net.time4j.format.ChronoFormatter;

import java.text.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


@RunWith(JUnit4.class)
public class ClockIntervalFormatTest {

    @Test
    public void printSHOW_NEVER() {
        PlainTime start = PlainTime.of(12, 0, 0);
        PlainTime end = PlainTime.of(14, 15, 30);
        ClockInterval interval = ClockInterval.between(start, end);
        ChronoFormatter<PlainTime> formatter =
            Iso8601Format.EXTENDED_WALL_TIME;
        assertThat(
            interval.print(formatter, BracketPolicy.SHOW_NEVER),
            is("12:00/14:15:30"));
        assertThat(
            interval.withOpenEnd().print(formatter, BracketPolicy.SHOW_NEVER),
            is("12:00/14:15:30"));
    }

    @Test
    public void printSHOW_WHEN_NON_STANDARD() {
        PlainTime start = PlainTime.of(12, 20, 0);
        PlainTime end = PlainTime.of(14, 15, 30);
        ClockInterval interval = ClockInterval.between(start, end);
        ChronoFormatter<PlainTime> formatter =
            Iso8601Format.BASIC_WALL_TIME;
        assertThat(
            interval.print(formatter, BracketPolicy.SHOW_WHEN_NON_STANDARD),
            is("1220/141530"));
        assertThat(
            interval.withClosedEnd().print(
                formatter, BracketPolicy.SHOW_WHEN_NON_STANDARD),
            is("[1220/141530]"));
    }

    @Test
    public void printSHOW_ALWAYS() {
        PlainTime start = PlainTime.of(12, 20, 0);
        PlainTime end = PlainTime.of(14, 15, 30);
        ClockInterval interval = ClockInterval.between(start, end);
        ChronoFormatter<PlainTime> formatter =
            Iso8601Format.BASIC_WALL_TIME;
        assertThat(
            interval.print(formatter, BracketPolicy.SHOW_ALWAYS),
            is("[1220/141530)"));
    }

    @Test
    public void parseISOBasic() throws ParseException {
        PlainTime start = PlainTime.of(12, 20, 0);
        PlainTime end = PlainTime.of(14, 15, 30);
        ClockInterval interval = ClockInterval.between(start, end);
        assertThat(
            ClockInterval.parseISO("1220/141530"),
            is(interval));
    }

    @Test
    public void parseISOExtended() throws ParseException {
        PlainTime start = PlainTime.of(12, 20, 0);
        PlainTime end = PlainTime.of(14, 15, 30);
        ClockInterval interval = ClockInterval.between(start, end);
        assertThat(
            ClockInterval.parseISO("12:20/14:15:30"),
            is(interval));
    }

}