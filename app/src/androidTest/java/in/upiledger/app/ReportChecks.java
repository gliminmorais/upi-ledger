package in.upiledger.app;
import android.content.Context;
import org.json.*;
import java.util.*;
import java.io.*;
public final class ReportChecks {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void run(Context c)throws Exception{
        c.deleteDatabase("report-checks.db");check(SpendingReport.date("2028-02-29").equals("29-02-28"),"PDF date formatting failed");
        try(LedgerDb db=new LedgerDb(c,"report-checks.db")){
            db.saveBudget("2026-10","Food",500000);db.saveBudget("2026-11","Food",250000);db.saveBudget("2026-10","Food",450000);check(db.budgets().size()==2,"Budget update duplicated limit");
            LedgerDb.Budget food=new LedgerDb.Budget();food.month="2026-12";food.category="Food";food.paise=300000;LedgerDb.Budget shopping=new LedgerDb.Budget();shopping.month="2026-12";shopping.category="Shopping";shopping.paise=600000;
            db.saveBudgets(Arrays.asList(food,shopping));check(db.budgets().size()==4,"Multiple category budgets were not saved");shopping.paise=-1;food.paise=1;try{db.saveBudgets(Arrays.asList(food,shopping));throw new AssertionError("Invalid bulk budget accepted");}catch(IllegalArgumentException expected){}check(db.budgets().stream().anyMatch(b->b.month.equals("2026-12")&&b.category.equals("Food")&&b.paise==300000),"Bulk budget failure partially saved");db.saveBudget("2026-12","Food",0);db.saveBudget("2026-12","Shopping",0);
            JSONObject backup=db.backup();db.restore(backup);check(db.budgets().get(0).paise==450000,"Budget backup lost exact amount");
            JSONObject bad=new JSONObject(backup.toString());bad.getJSONArray("budgets").getJSONObject(0).put("month","bad");try{db.restore(bad);throw new AssertionError("Invalid budget restored");}catch(java.time.DateTimeException expected){}check(db.budgets().size()==2,"Invalid restore lost budgets");
            db.saveBudget("2026-11","Food",0);check(db.budgets().size()==1,"Budget removal failed");
            JSONObject legacy=new JSONObject(backup.toString());legacy.put("version",3);legacy.remove("budgets");db.restore(legacy);check(db.budgets().isEmpty(),"Old backup failed");db.restore(backup);
            db.getWritableDatabase().execSQL("DROP TABLE budgets");db.getWritableDatabase().setVersion(3);
        }
        try(LedgerDb db=new LedgerDb(c,"report-checks.db")){
            check(db.budgets().isEmpty()&&db.hasCategory("Food"),"Schema 3 upgrade failed");db.saveBudget("2026-10","Food",500000);
            List<LedgerDb.Entry> rows=new ArrayList<>();for(int i=0;i<90;i++){LedgerDb.Entry e=new LedgerDb.Entry();e.vendor=i==0?"=SUM(1,2)":"Coffee Shop "+i;e.category="Food";e.date="2026-10-24";e.time="17:54";e.paise=4000;e.source="Manual";e.reference="001234567890";e.remarks="Bread, jam and \"notebooks\"\nSecond line. "+String.join(" ",Collections.nCopies(12,"Long remark"));rows.add(e);}
            String csv=new String(SpendingReport.csv(rows),java.nio.charset.StandardCharsets.UTF_8);check(csv.contains("24-10-26")&&!csv.contains("2026-10-24"),"CSV date format incorrect");check(csv.charAt(0)=='\ufeff',"Missing Excel UTF8 marker");check(csv.contains("' =SUM")==false&&csv.contains("'=SUM(1,2)"),"Formula injection not escaped");check(csv.contains("40.00")&&csv.contains("\"\"notebooks\"\""),"CSV amount or quotes broken");
            File pdf=new File(c.getExternalFilesDir(null),"spending-report-check.pdf");try(FileOutputStream out=new FileOutputStream(pdf)){out.write(SpendingReport.pdf(rows,"2026-10-01","2026-10-31"));}
            try(android.os.ParcelFileDescriptor fd=android.os.ParcelFileDescriptor.open(pdf,android.os.ParcelFileDescriptor.MODE_READ_ONLY);android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(fd)){check(renderer.getPageCount()>2,"PDF pagination missing");}
            LedgerDb.Entry longRow=rows.get(0);longRow.remarks=String.join(" ",Collections.nCopies(200,"Long continuous remark for page wrapping."));
            try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"spending-report-long-row.pdf"))){out.write(SpendingReport.pdf(Collections.singletonList(longRow),"2026-10-01","2026-10-31"));}
            byte[] empty=SpendingReport.pdf(Collections.emptyList(),"2026-11-01","2026-11-30");check(empty.length>500,"Empty PDF failed");
        }
    }
}
