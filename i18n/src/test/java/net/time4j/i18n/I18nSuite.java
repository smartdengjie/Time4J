package net.time4j.i18n;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;


@RunWith(Suite.class)
@SuiteClasses(
    {
        CalendricalNamesTest.class,
        DatePatternTest.class,
        DateTimePatternTest.class,
        EnumDisplayTest.class,
        IsoSanityTest.class,
        LocalizedGMTOffsetTest.class,
        NumberSymbolTest.class,
        PluralRulesTest.class,
        PrettyTimeTest.class,
        RootLocaleTest.class,
        WeekendTest.class
    }
)
public class I18nSuite {

}