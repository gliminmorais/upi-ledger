package in.upiledger.app;
import java.time.LocalDate;
import java.time.YearMonth;

public final class SubscriptionDates {
    private SubscriptionDates() {}
    public static String nextDue(String date,int renewalDay){
        if(renewalDay<1||renewalDay>31)throw new IllegalArgumentException("Choose a renewal day from 1 to 31.");
        YearMonth next=YearMonth.from(LocalDate.parse(date)).plusMonths(1);
        return next.atDay(Math.min(renewalDay,next.lengthOfMonth())).toString();
    }
}
