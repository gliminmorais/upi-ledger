package in.upiledger.app;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;
/** Local reports; generated only from structured saved records. */
public final class SpendingReport {
    private SpendingReport(){}
    static String amount(long paise){return BigDecimal.valueOf(paise,2).toPlainString();}
    static String cell(String value){String s=value==null?"":value;String trimmed=s.replaceFirst("^\\s+", "");if(!trimmed.isEmpty()&&"=+-@".indexOf(trimmed.charAt(0))>=0)s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
    public static byte[] csv(List<LedgerDb.Entry> rows){StringBuilder s=new StringBuilder("\ufeffDate,Time,Vendor,Category,Debit (INR),Remarks,Transaction ID,Source\r\n");for(LedgerDb.Entry e:rows)s.append(cell(e.date)).append(',').append(cell(e.time)).append(',').append(cell(e.vendor)).append(',').append(cell(e.category)).append(',').append(amount(e.paise)).append(',').append(cell(e.remarks)).append(',').append(cell(e.reference)).append(',').append(cell(e.source)).append("\r\n");return s.toString().getBytes(StandardCharsets.UTF_8);}
    public static byte[] pdf(List<LedgerDb.Entry> rows,List<LedgerDb.Budget> budgets,String from,String to)throws IOException{
        PdfDocument doc=new PdfDocument();try{
            Writer w=new Writer(doc,from+" to "+to);long total=0;Map<String,Long> categories=new TreeMap<>();for(LedgerDb.Entry e:rows){total+=e.paise;categories.merge(e.category,e.paise,Long::sum);}
            w.line("SPENDING SUMMARY",true);w.line("Total debits: INR "+amount(total)+"    Payments: "+rows.size(),false);w.line("Includes automatically recorded subscription renewals.",false);w.line("Category totals",true);for(Map.Entry<String,Long> c:categories.entrySet())w.line(c.getKey()+": INR "+amount(c.getValue()),false);
            w.line("MONTHLY BUDGETS",true);boolean any=false;for(LedgerDb.Budget b:budgets){String first=b.month+"-01",last=java.time.YearMonth.parse(b.month).atEndOfMonth().toString();if(last.compareTo(from)<0||first.compareTo(to)>0)continue;any=true;long spent=0;for(LedgerDb.Entry e:rows)if(e.date.startsWith(b.month)&&e.category.equals(b.category))spent+=e.paise;w.line(b.month+" / "+b.category+" / Limit INR "+amount(b.paise),false);w.line("Spent in report range INR "+amount(spent)+" / "+(spent>b.paise?"Over":"Remaining")+" INR "+amount(Math.abs(b.paise-spent)),false);}if(!any)w.line("No category budgets set for this range.",false);w.line("For partial months, budget spending includes only the report dates.",false);
            w.line("PAYMENT DETAILS",true);if(rows.isEmpty())w.line("No payments saved in this range.",false);for(LedgerDb.Entry e:rows){w.line(e.date+" "+e.time+" / INR "+amount(e.paise),true);w.line(e.vendor+" / "+e.category,false);w.line("Source: "+e.source+(e.reference.isEmpty()?"":" / ID: "+e.reference),false);if(!e.remarks.isEmpty())w.line("Remarks: "+e.remarks,false);}
            w.finish();ByteArrayOutputStream out=new ByteArrayOutputStream();doc.writeTo(out);return out.toByteArray();
        }finally{doc.close();}
    }
    private static final class Writer {
        final PdfDocument doc;final String range;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);PdfDocument.Page page;Canvas canvas;int number;float y;
        Writer(PdfDocument d,String range){doc=d;this.range=range;newPage();}
        void newPage(){if(page!=null)finish();page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,++number).create());canvas=page.getCanvas();paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setColor(Color.rgb(28,81,64));paint.setTextSize(23);canvas.drawText("UPI LEDGER",40,49,paint);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(10);canvas.drawText(range,40,70,paint);y=103;}
        void line(String text,boolean bold){paint.setTextSize(11);paint.setColor(Color.rgb(28,52,44));paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));String[] lines=text.replace('\r',' ').split("\n",-1);for(String line:lines){if(line.isEmpty())line=" ";int start=0;while(start<line.length()){int count=paint.breakText(line,start,line.length(),true,515,null);if(count<1)count=1;if(start+count<line.length()){int space=line.lastIndexOf(' ',start+count);if(space>start)count=space-start+1;}if(y>778){newPage();paint.setTextSize(11);paint.setColor(Color.rgb(28,52,44));paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));}canvas.drawText(line.substring(start,start+count),40,y,paint);y+=16;start+=count;}}if(bold)y+=5;}
        void finish(){paint.setTextSize(9);paint.setColor(Color.GRAY);canvas.drawText("Saved records only | Page "+number,40,816,paint);doc.finishPage(page);page=null;}
    }
}
