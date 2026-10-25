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
    public static byte[] pdf(List<LedgerDb.Entry> rows,String from,String to)throws IOException{
        PdfDocument doc=new PdfDocument();try{
            long total=0;Map<String,Long> categories=new TreeMap<>();for(LedgerDb.Entry e:rows){total+=e.paise;categories.merge(e.category,e.paise,Long::sum);}
            Writer w=new Writer(doc,from+" to "+to);w.summary(total,rows.size());
            w.table("SPENDING BY CATEGORY",new String[]{"Category","Spent (INR)"},new float[]{590,180},1);
            if(categories.isEmpty())w.row(new String[]{"No spending recorded","0.00"});
            for(Map.Entry<String,Long> c:categories.entrySet())w.row(new String[]{c.getKey(),amount(c.getValue())});
            w.table("PAYMENTS",new String[]{"Date / Time","Vendor","Category","Spent (INR)","Remarks","Source"},new float[]{82,155,110,88,250,85},3);
            if(rows.isEmpty())w.row(new String[]{"","No payments saved in this range.","","","",""});
            for(LedgerDb.Entry e:rows)w.row(new String[]{e.date+"\n"+e.time,e.vendor,e.category,amount(e.paise),e.remarks,e.source});
            w.finish();ByteArrayOutputStream out=new ByteArrayOutputStream();doc.writeTo(out);return out.toByteArray();
        }finally{doc.close();}
    }
    /** Landscape A4 tables, repeated headings, wrapped cells and oversized-row continuation. */
    private static final class Writer {
        static final float LEFT=36,RIGHT=806,BOTTOM=551,LINE=12;
        final PdfDocument doc;final String range;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PdfDocument.Page page;Canvas canvas;int number,rowNumber,rightColumn;float y;String title;String[] headers;float[] widths;
        Writer(PdfDocument d,String range){doc=d;this.range=range;newPage();}
        void text(String value,float x,float baseline,float size,boolean bold,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));paint.setTextSize(size);canvas.drawText(value,x,baseline,paint);}
        void rect(float x,float top,float right,float bottom,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);canvas.drawRect(x,top,right,bottom,paint);}
        void newPage(){if(page!=null)finish();page=doc.startPage(new PdfDocument.PageInfo.Builder(842,595,++number).create());canvas=page.getCanvas();text("UPI LEDGER",LEFT,35,18,true,Color.rgb(28,81,64));text("Spending report",650,34,12,false,Color.DKGRAY);text(range,LEFT,55,10,false,Color.DKGRAY);y=77;}
        void summary(long total,int count){rect(LEFT,y,RIGHT,y+46,Color.rgb(234,244,239));text("TOTAL SPENT (INR)",LEFT+12,y+16,9,true,Color.rgb(55,97,79));text(amount(total),LEFT+12,y+36,18,true,Color.rgb(28,81,64));text(count+" payments | Includes saved subscription entries",360,y+28,10,false,Color.DKGRAY);y+=61;}
        void table(String title,String[] headers,float[] widths,int rightColumn){this.title=title;this.headers=headers;this.widths=widths;this.rightColumn=rightColumn;rowNumber=0;if(y+83>BOTTOM)newPage();heading(false);}
        void heading(boolean continued){text(title+(continued?" (continued)":""),LEFT,y+12,11,true,Color.rgb(28,81,64));y+=23;rect(LEFT,y,RIGHT,y+25,Color.rgb(28,81,64));float x=LEFT;for(int i=0;i<headers.length;i++){float pos=x+7;if(i==rightColumn){paint.setTextSize(9);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));pos=x+widths[i]-7-paint.measureText(headers[i]);}text(headers[i],pos,y+16,9,true,Color.WHITE);x+=widths[i];}y+=25;}
        List<String> wrap(String value,float width){paint.setTextSize(9);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));List<String> lines=new ArrayList<>();for(String part:(value==null?"":value).replace('\r',' ').split("\n",-1)){if(part.isEmpty()){lines.add("");continue;}int start=0;while(start<part.length()){int count=paint.breakText(part,start,part.length(),true,width,null);if(count<1)count=1;if(start+count<part.length()){int space=part.lastIndexOf(' ',start+count);if(space>start)count=space-start+1;}lines.add(part.substring(start,start+count).trim());start+=count;}}return lines;}
        void row(String[] values){List<List<String>> cells=new ArrayList<>();int lines=1;for(int i=0;i<values.length;i++){List<String> cell=wrap(values[i],widths[i]-14);cells.add(cell);lines=Math.max(lines,cell.size());}
            float fullHeight=Math.max(28,lines*LINE+14);if(y+fullHeight>BOTTOM&&fullHeight<=BOTTOM-125){newPage();heading(true);}
            int offset=0;while(offset<lines){int capacity=(int)((BOTTOM-y-14)/LINE);if(capacity<1||BOTTOM-y<28){newPage();heading(true);capacity=(int)((BOTTOM-y-14)/LINE);}int count=Math.min(lines-offset,capacity);float height=Math.max(28,count*LINE+14);rect(LEFT,y,RIGHT,y+height,rowNumber%2==0?Color.rgb(246,250,248):Color.WHITE);float x=LEFT;for(int i=0;i<values.length;i++){for(int j=0;j<count;j++){int index=offset+j;String value=index<cells.get(i).size()?cells.get(i).get(index):"";if(offset>0&&i==0&&j==0&&value.isEmpty())value="(continued)";float pos=x+7;if(i==rightColumn){paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(9);pos=x+widths[i]-7-paint.measureText(value);}text(value,pos,y+16+j*LINE,9,false,Color.rgb(30,49,42));}paint.setColor(Color.rgb(214,226,219));paint.setStrokeWidth(.5f);canvas.drawLine(x,y,x,y+height,paint);x+=widths[i];}paint.setColor(Color.rgb(214,226,219));canvas.drawLine(RIGHT,y,RIGHT,y+height,paint);canvas.drawLine(LEFT,y+height,RIGHT,y+height,paint);y+=height;offset+=count;if(offset<lines){newPage();heading(true);}}rowNumber++;}
        void finish(){text("Based on saved payments only",LEFT,576,8,false,Color.GRAY);text("Page "+number,760,576,8,false,Color.GRAY);doc.finishPage(page);page=null;}
    }
}
