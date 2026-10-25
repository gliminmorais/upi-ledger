package in.upiledger.app;
import android.content.Context;
import java.time.LocalDate;
import java.util.*;
import org.json.*;
import java.io.*;
public final class SplitChecks {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static LedgerDb.Entry entry(String name,long paid,long own){LedgerDb.Entry e=new LedgerDb.Entry();e.vendor=name;e.paise=paid;e.ownPaise=own;e.category="Food";e.date=LocalDate.now().minusDays(1).toString();e.time="19:30";e.source="Manual";e.reference="";e.remarks="Dinner with friends";return e;}
    static LedgerDb.Repayment repayment(long id,String from,long amount){LedgerDb.Repayment r=new LedgerDb.Repayment();r.entryId=id;r.from=from;r.paise=amount;r.date=LocalDate.now().toString();r.remarks="Dinner share";return r;}
    public static void run(Context c)throws Exception{
        c.deleteDatabase("split-checks.db");
        try(LedgerDb db=new LedgerDb(c,"split-checks.db")){
            LedgerDb.Entry old=entry("Old payment",12500,-1);db.save(old,false,"");db.saveBudget(LocalDate.now().toString().substring(0,7),"Food",100000);
            JSONObject legacy=db.backup();legacy.put("version",4);legacy.remove("repayments");legacy.getJSONArray("entries").getJSONObject(0).remove("own_paise");db.restore(legacy);check(db.entries().get(0).spent()==12500,"Version 4 backup changed spending");
            db.getWritableDatabase().execSQL("DROP TABLE repayments");db.getWritableDatabase().execSQL("ALTER TABLE entries DROP COLUMN own_paise");db.getWritableDatabase().setVersion(4);
        }
        try(LedgerDb db=new LedgerDb(c,"split-checks.db")){
            check(db.entries().get(0).spent()==12500&&db.budgets().size()==1,"Schema 4 migration lost records");
            LedgerDb.Entry e=entry("Dinner House",90000,30000);db.save(e,false,"");LedgerDb.Repayment alice=repayment(e.id,"Alice",20000);db.addRepayment(alice);LedgerDb.Repayment bob=repayment(e.id,"Bob",40000);db.addRepayment(bob);
            check(db.received(e.id)==60000,"Separate friend repayments were not summed");check(db.entries().stream().filter(x->x.id==e.id).findFirst().get().spent()==30000,"Repayments changed personal spending");
            try{db.addRepayment(repayment(e.id,"Overpaid",1));throw new AssertionError("Overpayment accepted");}catch(IllegalArgumentException expected){}
            e.ownPaise=40000;try{db.save(e,false,"");throw new AssertionError("Edit invalidated repayments");}catch(IllegalArgumentException expected){}e.ownPaise=30000;
            LedgerDb.Entry zero=entry("Friends only",5000,0);db.save(zero,false,"");check(zero.spent()==0,"Zero own share unsupported");
            LedgerDb.Repayment badDate=repayment(zero.id,"Friend",5000);badDate.date=LocalDate.now().minusDays(5).toString();try{db.addRepayment(badDate);throw new AssertionError("Repayment before payment accepted");}catch(IllegalArgumentException expected){}
            db.addRepayment(repayment(zero.id,"Friend",5000));db.delete(zero.id);check(db.repayments().size()==2,"Deleting split did not remove linked repayments");
            JSONObject backup=db.backup();db.restore(backup);check(db.received(e.id)==60000&&db.repayments().size()==2,"Split backup round trip failed");
            JSONObject invalid=new JSONObject(backup.toString());invalid.getJSONArray("repayments").getJSONObject(0).put("paise",70000);try{db.restore(invalid);throw new AssertionError("Invalid repayment backup restored");}catch(JSONException expected){}check(db.received(e.id)==60000,"Invalid restore lost repayments");
            String csv=new String(SpendingReport.csv(Collections.singletonList(e),db.repayments()),java.nio.charset.StandardCharsets.UTF_8);check(csv.contains("900.00,300.00,600.00,0.00")&&csv.contains("Repayment")&&csv.contains("Alice"),"CSV split accounting incorrect");
            try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"split-report-preview.pdf"))){out.write(SpendingReport.pdf(Collections.singletonList(e),db.repayments(),e.date,LocalDate.now().toString()));}
            try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"split-report-preview.csv"))){out.write(SpendingReport.csv(Collections.singletonList(e),db.repayments()));}
            db.deleteRepayment(alice.id);check(db.received(e.id)==40000,"Repayment correction failed");db.delete(e.id);check(db.repayments().isEmpty(),"Payment delete left repayment records");
        }
    }
}
