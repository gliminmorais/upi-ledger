package in.upiledger.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class SubscriptionDatesTest {
    @Test public void monthEndReturnsToOriginalDay(){assertEquals("2027-02-28",SubscriptionDates.nextDue("2027-01-31",31));assertEquals("2027-03-31",SubscriptionDates.nextDue("2027-02-28",31));}
    @Test public void leapYearsAndDecember(){assertEquals("2028-02-29",SubscriptionDates.nextDue("2028-01-31",31));assertEquals("2027-01-15",SubscriptionDates.nextDue("2026-12-15",15));}
    @Test(expected=IllegalArgumentException.class)public void rejectsInvalidDay(){SubscriptionDates.nextDue("2026-10-25",32);}
}
