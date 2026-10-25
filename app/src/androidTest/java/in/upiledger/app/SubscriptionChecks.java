package in.upiledger.app;
import android.content.Context;
import org.json.*;

public final class SubscriptionChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void run(Context context)throws Exception{
        context.deleteDatabase("subscription-checks.db");
        try(LedgerDb db=new LedgerDb(context,"subscription-checks.db")){
            JSONObject empty=db.backup();
            LedgerDb.Subscription s=new LedgerDb.Subscription();s.name="Spotify";s.vendor="ignored";s.category="ignored";s.paise=11900;s.nextDue="2027-01-31";s.renewalDay=31;s.active=true;s.remarks="Music";
            db.saveSubscription(s);check(db.entries().isEmpty(),"Creating a future subscription created an expense");
            db.postDueSubscriptions("2027-03-31");check(db.entries().size()==3,"Missed-month catch-up failed");
            check(db.entries().get(1).date.equals("2027-02-28"),"February month-end failed");
            check(db.subscriptions().get(0).nextDue.equals("2027-04-30"),"Renewal day did not return to month end");
            for(LedgerDb.Entry e:db.entries())check(e.vendor.equals("Spotify")&&e.category.equals("Subscription")&&e.paise==11900,"Incorrect subscription debit fields");
            db.postDueSubscriptions("2027-03-31");check(db.entries().size()==3,"Opening twice duplicated renewals");
            db.cancelSubscription(s.id);db.postDueSubscriptions("2027-12-31");check(db.entries().size()==3,"Cancelled subscription still posted");
            JSONObject backup=db.backup();db.restore(backup);check(db.entries().size()==3&&!db.subscriptions().get(0).active,"Subscription backup round-trip failed");
            JSONObject bad=new JSONObject(backup.toString());bad.getJSONArray("subscriptions").getJSONObject(0).put("renewal_day",32);
            try{db.restore(bad);throw new AssertionError("Invalid renewal restored");}catch(android.database.sqlite.SQLiteException|JSONException|IllegalArgumentException expected){}
            check(db.entries().size()==3,"Invalid subscription backup lost records");
            empty.put("version",2);empty.remove("subscriptions");db.restore(empty);check(db.subscriptions().isEmpty(),"Version 2 backup compatibility failed");
            db.restore(backup);long entry=db.entries().get(0).id;db.delete(entry);db.postDueSubscriptions("2027-12-31");check(db.entries().size()==2,"Deleting a debit recreated it");
        }
    }
}
