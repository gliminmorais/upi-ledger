package in.upiledger.app;

import android.app.Instrumentation;
import android.os.Bundle;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.concurrent.TimeUnit;
import android.graphics.*;
import org.json.*;

/** Uses an isolated test database, never the user's ledger database. */
public final class LedgerChecks extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private String evaluate(android.webkit.WebView web,String script) throws Exception {
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<String> value=new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(()->web.evaluateJavascript(script,r->{value.set(r);latch.countDown();}));
        check(latch.await(10,TimeUnit.SECONDS),"Web UI did not respond");return value.get();
    }
    private LedgerDb.Entry entry(String category,String reference){LedgerDb.Entry e=new LedgerDb.Entry();e.vendor="Swiggy Instamart";e.paise=4000;e.category=category;e.reference=reference;e.date="2026-10-24";e.time="17:54";e.remarks="Notebooks";e.source="BHIM";return e;}
    private Bitmap sampleReceipt(int index){
        Bitmap image=Bitmap.createBitmap(1000,1100,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);
        Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.BLACK);ink.setTextSize(42);ink.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        String[] lines=index==1?new String[]{"Google Pay","To Sample Person","INR 360.00","Completed","29 Aug 2026, 11:16 pm","INR 360.00 was debited","UPI transaction ID","123456789012"}:new String[]{"BHIM","Paid","INR 40.00","Banking Name","Sample Coffee Shop","Transaction ID","123456789013","Date & Time","24th Oct 26, 05:54 pm"};
        for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],50,80+i*80,ink);
        return image;
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        getTargetContext().deleteDatabase("integration-tests.db");
        try(LedgerDb db=new LedgerDb(getTargetContext(),"integration-tests.db")) {
            SubscriptionChecks.run(getTargetContext());ReportChecks.run(getTargetContext());SplitChecks.run(getTargetContext());
            LedgerDb.Entry first=entry("Food","111111111111");db.save(first,false,"");
            db.save(entry("Education & stationery","222222222222"),false,"Dynamic checkout");
            check(db.resolve("Swiggy Instamart").category.equals("Food"),"Override changed vendor default");
            check(db.resolve("Dynamic checkout").name.equals("Swiggy Instamart"),"Alias resolution failed");
            check(db.entries().size()==2,"Expected two transactions");
            LedgerDb.Vendor vendor=db.resolve("Swiggy Instamart");db.updateVendor(vendor.id,vendor.name,"Shopping");
            check(db.entries().get(0).category.equals("Education & stationery"),"Vendor default rewrote history");
            check(db.duplicate("111111111111",0),"Duplicate reference not detected");
            check(!db.duplicate("111111111111",first.id),"Editing self detected as duplicate");
            db.addCategory(" Travel ");check(db.hasCategory("Travel"),"Custom category was not trimmed/persisted");
            try{db.addCategory("travel");throw new AssertionError("Duplicate category accepted");}catch(IllegalArgumentException expected){}
            db.addVendor("Holiday Hotel","Travel");check(db.resolve("Holiday Hotel").category.equals("Travel"),"Standalone vendor not created");
            check(db.entries().size()==2,"Creating a vendor created a payment");
            first.category="Travel";db.save(first,false,"");check(db.resolve("Swiggy Instamart").category.equals("Shopping"),"Custom override changed vendor default");
            JSONObject backup=db.backup();db.delete(first.id);db.restore(backup);
            check(db.entries().size()==2,"Backup restore lost transactions");
            check(db.hasCategory("Travel")&&db.resolve("Holiday Hotel")!=null,"Backup lost custom category or unused vendor");
            JSONObject legacy=new JSONObject(backup.toString());legacy.put("version",1);legacy.remove("categories");db.restore(legacy);check(db.hasCategory("Travel"),"Legacy backup did not retain used category");db.restore(backup);
            check(db.entries().get(0).remarks.equals("Notebooks"),"Remarks did not survive backup");
            JSONObject bad=new JSONObject(backup.toString());bad.getJSONArray("entries").getJSONObject(0).put("paise",-1);
            try{db.restore(bad);throw new AssertionError("Invalid restore accepted");}catch(android.database.sqlite.SQLiteException|JSONException expected){}
            check(db.entries().size()==2,"Failed restore destroyed data");
            getTargetContext().deleteDatabase("migration-check.db");
            try(LedgerDb old=new LedgerDb(getTargetContext(),"migration-check.db")){
                old.addCategory("Other");old.addVendor("Legacy Vendor","Other");
                old.getWritableDatabase().execSQL("DROP TABLE categories");old.getWritableDatabase().setVersion(1);
            }
            try(LedgerDb upgraded=new LedgerDb(getTargetContext(),"migration-check.db")){
                check(upgraded.hasCategory("Other")&&upgraded.resolve("Legacy Vendor")!=null,"Migration lost legacy vendor/category");
                check(upgraded.hasCategory("Food"),"Migration lost standard categories");
            }
            TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            try {
                for(int i=1;i<=2;i++) {
                    Bitmap bitmap=sampleReceipt(i);
                    String raw=ReceiptText.ordered(Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),60,TimeUnit.SECONDS));bitmap.recycle();
                    ReceiptParser.Draft d=ReceiptParser.parse(raw);
                    check(d.paise==(i==1?36000:4000),"Generated receipt "+i+": amount extraction ("+d.paise+") header="+raw.substring(0,Math.min(220,raw.length())));
                    check(d.vendor.equals(i==1?"Sample Person":"Sample Coffee Shop"),"Generated receipt "+i+": vendor extraction");
                    check(d.date.equals(i==1?"2026-08-29":"2026-10-24"),"Generated receipt "+i+": date extraction ("+d.date+") text="+raw);
                    check(d.time.equals(i==1?"23:16":"17:54"),"Generated receipt "+i+": time extraction");
                    check(!d.reference.isEmpty(),"Generated receipt "+i+": reference missing");
                    check(d.status.equals("Completed"),"Generated receipt "+i+": status extraction");
                }
            }finally{recognizer.close();}
            android.content.Intent intent=new android.content.Intent(getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            MainActivity activity=(MainActivity)startActivitySync(intent);
            java.lang.reflect.Field field=MainActivity.class.getDeclaredField("web");field.setAccessible(true);android.webkit.WebView web=(android.webkit.WebView)field.get(activity);
            long deadline=android.os.SystemClock.uptimeMillis()+15000;String loaded="false";
            while(android.os.SystemClock.uptimeMillis()<deadline){loaded=evaluate(web,"typeof window.openImport === 'function' && !!document.querySelector('h1')");if("true".equals(loaded))break;android.os.SystemClock.sleep(100);}
            check("true".equals(loaded),"Offline glass UI did not load");
            check(evaluate(web,"document.querySelectorAll('.dock button').length").equals("5"),"Navigation not rendered");
            check(evaluate(web,"document.querySelector('[data-action=payments]').click(); !!document.querySelector('#search')").equals("true"),"Payments navigation failed");
            check(evaluate(web,"document.querySelector('[data-action=manual]').click(); !!document.querySelector('#paymentForm')").equals("true"),"Payment editor failed");
            check(evaluate(web,"document.querySelector('[name=remarks]') !== null && document.querySelector('[name=setDefault]').checked === false").equals("true"),"Remarks or override controls missing");
            check(evaluate(web,"document.querySelector('[name=confirmed]') === null").equals("true"),"Unwanted success checkbox still shown");
            check(evaluate(web,"!!document.querySelector('select[name=category] option[value=__create__]')").equals("true"),"Create category option missing");
            java.lang.reflect.Field database=MainActivity.class.getDeclaredField("db");database.setAccessible(true);database.set(activity,db);
            MainActivity.Bridge bridge=activity.new Bridge();
            JSONObject invalid=new JSONObject();invalid.put("vendor","Native Test");invalid.put("amount","0.001");invalid.put("date","2026-10-24");invalid.put("time","12:30");invalid.put("category","Food");
            int originalSize=db.entries().size();bridge.savePayment(invalid.toString());check(db.entries().size()==originalSize,"Bridge accepted fractional paise");
            invalid.put("amount","10.50");invalid.put("remarks","Glass editor test");bridge.savePayment(invalid.toString());check(db.entries().size()==originalSize+1,"Bridge save failed");
            check(evaluate(web,"document.querySelector('[data-action=insights]').click();document.body.innerText.includes('Monthly budgets')").equals("true"),"Budget section missing");
            check(evaluate(web,"document.querySelector('[data-action=budgets]').click();!!document.querySelector('#budgetForm')").equals("true"),"Budget editor missing");
            JSONObject budget=new JSONObject();budget.put("month",java.time.LocalDate.now().toString().substring(0,7));budget.put("category","Food");budget.put("amount","50.00");bridge.saveBudget(budget.toString());
            check(evaluate(web,"document.body.innerText.includes('remaining')").equals("true"),"Budget remaining did not render");
            check(evaluate(web,"document.querySelector('[data-action=budgets]').click();document.querySelectorAll('[data-budget-category]').length>2").equals("true"),"Multi-category budget fields missing");
            evaluate(web,"document.querySelector('[data-budget-category=Food]').value='75';document.querySelector('[data-budget-category=Shopping]').value='250';document.querySelector('#budgetForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));true");
            long budgetDeadline=android.os.SystemClock.uptimeMillis()+5000;while(android.os.SystemClock.uptimeMillis()<budgetDeadline&&db.budgets().size()<2)android.os.SystemClock.sleep(50);
            check(db.budgets().stream().anyMatch(b->b.category.equals("Food")&&b.paise==7500)&&db.budgets().stream().anyMatch(b->b.category.equals("Shopping")&&b.paise==25000),"Multi-category form did not save both limits");
            evaluate(web,"editBudgets();true");
            check(evaluate(web,"document.querySelector('[data-budget-category=Food]').value==='75.00'&&document.querySelector('[data-budget-category=Shopping]').value==='250.00'").equals("true"),"Budget editor lost saved category amounts");
            android.os.SystemClock.sleep(300);android.graphics.Bitmap budgetScreen=getUiAutomation().takeScreenshot();if(budgetScreen!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"multi-budget-preview.png"))){budgetScreen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}budgetScreen.recycle();}evaluate(web,"closeSheet();true");

            check(evaluate(web,"document.querySelector('[data-action=report]').click();document.querySelector('#reportForm [name=format]').options.length===2").equals("true"),"CSV/PDF export options missing");
            evaluate(web,"closeSheet();true");
            evaluate(web,"openEditor({vendor:'Split UI Dinner',amount:'900',category:'Food'});document.querySelector('[name=split]').checked=true;document.querySelector('[name=split]').dispatchEvent(new Event('change',{bubbles:true}));document.querySelector('[name=ownAmount]').value='300';submitPayment(true);true");
            long splitDeadline=android.os.SystemClock.uptimeMillis()+5000;while(android.os.SystemClock.uptimeMillis()<splitDeadline&&!db.entries().stream().anyMatch(e->e.vendor.equals("Split UI Dinner")))android.os.SystemClock.sleep(50);
            LedgerDb.Entry split=db.entries().stream().filter(e->e.vendor.equals("Split UI Dinner")).findFirst().get();check(split.paise==90000&&split.spent()==30000,"Split editor saved wrong own share");
            check(evaluate(web,"navigate('home');document.body.innerText.includes('Friends paid you back')").equals("true"),"Home repayment summary missing");
            for(String friend:new String[]{"Alice","Bob"}){evaluate(web,"repaymentEditor("+split.id+");document.querySelector('#repaymentForm [name=from]').value='"+friend+"';document.querySelector('#repaymentForm [name=amount]').value='"+(friend.equals("Alice")?"200":"400")+"';document.querySelector('#repaymentForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));true");long due=android.os.SystemClock.uptimeMillis()+5000;long expected=friend.equals("Alice")?20000:60000;while(android.os.SystemClock.uptimeMillis()<due&&db.received(split.id)<expected)android.os.SystemClock.sleep(50);check(db.received(split.id)==expected,"Repayment form failed for "+friend);}
            check(evaluate(web,"data.entries.find(e=>e.id==="+split.id+").paise===30000&&data.repayments.reduce((n,r)=>n+r.paise,0)===60000").equals("true"),"Repayments changed spending or Home totals");
            evaluate(web,"navigate('home');true");android.os.SystemClock.sleep(600);android.graphics.Bitmap splitScreen=getUiAutomation().takeScreenshot();if(splitScreen!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"split-home-preview.png"))){splitScreen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}splitScreen.recycle();}
            check(evaluate(web,"document.querySelector('[data-action=edit][data-id=\""+split.id+"\"]').click();document.querySelector('[name=amount]').value==='900.00'&&document.querySelector('[name=ownAmount]').value==='300.00'").equals("true"),"Editing a split replaced full payment with own share");
            runOnMainSync(()->activity.finish());
            result.putString("stream","PASS: vendor overrides, aliases, duplicate IDs, remarks, backup rollback, generated receipt OCR examples, offline glass UI, navigation, editor controls, monthly budgets, CSV/PDF export and native save validation.");finish(-1,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e.toString());finish(0,result);}
    }
}
