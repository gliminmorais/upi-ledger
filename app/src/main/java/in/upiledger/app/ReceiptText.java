package in.upiledger.app;

import com.google.mlkit.vision.text.Text;
import java.util.*;
import java.util.regex.Pattern;

/** Reconstruct visual reading order; OCR block order is unreliable on two-column receipts. */
public final class ReceiptText {
    public static String ordered(Text text) {
        List<Text.Line> lines=new ArrayList<>();
        for(Text.TextBlock b:text.getTextBlocks())for(Text.Line l:b.getLines())if(l.getBoundingBox()!=null)lines.add(l);
        lines.sort(Comparator.comparingInt((Text.Line l)->l.getBoundingBox().top).thenComparingInt(l->l.getBoundingBox().left));
        int detailsTop=Integer.MAX_VALUE;
        for(Text.Line l:lines)if(l.getText().trim().equalsIgnoreCase("Banking Name"))detailsTop=Math.min(detailsTop,l.getBoundingBox().top);
        StringBuilder out=new StringBuilder();
        for(Text.Line l:lines){
            String value=l.getText();
            java.util.regex.Matcher amountLine=Pattern.compile("^\\s*(7[0-9][0-9,]*(?:\\.[0-9]{1,2})?)(\\s+(?:was debited|sent to)\\b.*)?\\s*$",Pattern.CASE_INSENSITIVE).matcher(value);
            if(amountLine.matches()&&(l.getBoundingBox().top<detailsTop||amountLine.group(2)!=null)){
                List<Text.Symbol> symbols=new ArrayList<>();for(Text.Element e:l.getElements())symbols.addAll(e.getSymbols());
                List<Integer> heights=new ArrayList<>();
                for(int i=1;i<Math.min(symbols.size(),amountLine.group(1).replace(" ","").length());i++){Text.Symbol s=symbols.get(i);if(s.getText().matches("[0-9]")&&s.getBoundingBox()!=null)heights.add(s.getBoundingBox().height());}
                if(!symbols.isEmpty()&&symbols.get(0).getText().equals("7")&&symbols.get(0).getBoundingBox()!=null&&!heights.isEmpty()){
                    Collections.sort(heights);value=ReceiptParser.correctCurrencyGlyph(amountLine.group(1),symbols.get(0).getBoundingBox().height(),heights.get(heights.size()/2))+(amountLine.group(2)==null?"":amountLine.group(2));
                }
            }
            out.append(value).append('\n');
        }
        // Keep reference label and value adjacent in a separate extraction hint when
        // another column's heading appears between them in visual reading order.
        for(Text.Line label:lines)if(Pattern.compile("(?i)^(UPI )?transaction ID[: ]*$").matcher(label.getText().trim()).matches()) {
            Text.Line closest=null;int distance=Integer.MAX_VALUE;
            for(Text.Line candidate:lines) {
                int dy=candidate.getBoundingBox().top-label.getBoundingBox().bottom;
                int dx=Math.abs(candidate.getBoundingBox().left-label.getBoundingBox().left);
                if(dy>=-3&&dy<160&&dx<80&&candidate.getText().trim().matches("[0-9]{10,18}")&&dy<distance){closest=candidate;distance=dy;}
            }
            if(closest!=null)out.append("\nUPI transaction ID\n").append(closest.getText().trim()).append('\n');
        }
        return out.toString();
    }
}
