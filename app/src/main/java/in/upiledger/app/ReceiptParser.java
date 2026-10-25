package in.upiledger.app;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/** Conservative extraction: missing or ambiguous fields must be completed in review. */
public final class ReceiptParser {
    public static final class Draft {
        public String vendor = "", reference = "", source = "Unknown", status = "Unverified", date = "", time = "";
        public long paise;
        public boolean amountNeedsReview;
    }
    public static Draft parse(String raw) {
        Draft d = new Draft();
        String text = raw.replace('\u00a0', ' ').replace("\r", "");
        String low = text.toLowerCase(Locale.ROOT);
        d.source = low.contains("bhim") ? "BHIM" : low.contains("google") || low.contains("gpay") ? "GPay" : "Unknown";
        boolean bad = Pattern.compile("(?i)\\b(failed|pending|processing|declined|reversed|refunded)\\b").matcher(text).find();
        boolean incoming = Pattern.compile("(?i)\\b(received from|money received|credited to your)\\b").matcher(text).find();
        d.status = bad ? "Not confirmed" : incoming ? "Incoming / check" : Pattern.compile("(?i)\\b(completed|paid|successful|success)\\b").matcher(text).find() ? "Completed" : "Unverified";
        readAmount(text,d);
        Matcher ref = Pattern.compile("(?i)(?:UPI\\s+transaction\\s+ID|transaction\\s+ID|UTR|UPI\\s+ref(?:erence)?(?:\\s+no\\.?)?)\\s*[:#]?\\s*([0-9]{10,18})").matcher(text);
        if (ref.find()) d.reference = ref.group(1);
        String[] lines = text.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equalsIgnoreCase("Banking Name") && i + 1 < lines.length) { d.vendor = lines[i + 1].trim(); break; }
            Matcher to = Pattern.compile("(?i)^To\\s*:?\\s+(.+)$").matcher(line);
            if (to.find() && !to.group(1).toLowerCase(Locale.ROOT).startsWith("upi")) { d.vendor = to.group(1).trim(); break; }
        }
        Matcher date = Pattern.compile("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+(\\d{4}|\\d{2})\\b").matcher(text);
        while (date.find()) {
            try {
                String month = date.group(2).substring(0, 3).toLowerCase(Locale.ROOT);
                int m = Arrays.asList("jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec").indexOf(month) + 1;
                int y = Integer.parseInt(date.group(3)); if (y < 100) y += 2000;
                d.date = LocalDate.of(y, m, Integer.parseInt(date.group(1))).toString();
                Matcher time = Pattern.compile("(?i)(\\d{1,2}):(\\d{2})\\s*(am|pm)?").matcher(text.substring(date.end()));
                if (time.find()) {
                    int h = Integer.parseInt(time.group(1)), min = Integer.parseInt(time.group(2));
                    if (time.group(3) != null) { if(h < 1 || h > 12) throw new IllegalArgumentException(); h = h % 12 + (time.group(3).equalsIgnoreCase("pm") ? 12 : 0); }
                    d.time = LocalTime.of(h, min).format(DateTimeFormatter.ofPattern("HH:mm"));
                }
                break;
            } catch (RuntimeException ignored) { /* Leave invalid fields for review. */ }
        }
        return d;
    }
    private static long paise(String amount){
        try{return new BigDecimal(amount.replace(",", "")).movePointRight(2).longValueExact();}catch(RuntimeException e){return 0;}
    }
    private static void readAmount(String text,Draft d){
        Set<Long> explicit=new LinkedHashSet<>();
        Matcher money=Pattern.compile("(?:₹|(?i:INR|Rs\\.?))\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)").matcher(text);
        while(money.find()){long value=paise(money.group(1));if(value>0)explicit.add(value);}
        if(explicit.size()==1){d.paise=explicit.iterator().next();return;}
        if(explicit.size()>1){d.amountNeedsReview=true;return;}
        List<String> candidates=new ArrayList<>();
        String header=text.split("(?i)Banking Name|State Bank|Transaction ID|UPI ID",2)[0];
        Matcher isolated=Pattern.compile("(?m)^\\s*[Tt]?\\s*([0-9]{1,7}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?)\\s*$").matcher(header);
        if(isolated.find())candidates.add(isolated.group(1).replace(",", ""));
        Matcher debit=Pattern.compile("(?im)^\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+(?:was debited|sent to)\\b").matcher(text);
        while(debit.find())candidates.add(debit.group(1).replace(",", ""));
        if(candidates.isEmpty())return;
        Set<Long> trusted=new LinkedHashSet<>();
        for(String value:candidates)if(!value.startsWith("7")&&paise(value)>0)trusted.add(paise(value));
        if(trusted.size()==1){
            long amount=trusted.iterator().next();boolean consistent=true;
            for(String value:candidates){long parsed=paise(value);if(parsed!=amount&&!(value.startsWith("7")&&paise(value.substring(1))==amount)){consistent=false;break;}}
            if(consistent){d.paise=amount;return;}
        }
        // This applies to every receipt source, including GPay, BHIM and unknown
        // share layouts. Repetition alone does not prove a leading 7 is a digit.
        d.amountNeedsReview=true;
    }
    public static String correctCurrencyGlyph(String amount,int prefixHeight,int digitHeight){
        String compact=amount.replaceAll("\\s+","");
        if(compact.matches("7[0-9][0-9,]*(?:\\.[0-9]{1,2})?")&&prefixHeight>0&&digitHeight>0&&prefixHeight<digitHeight*0.85)
            return "₹"+compact.substring(1);
        return amount;
    }
    public static String suggest(String vendor) {
        String s = vendor.toLowerCase(Locale.ROOT);
        if (s.matches(".*(swiggy|zomato|coffee|restaurant|bakery|cafe).*")) return "Food";
        if (s.matches(".*(stationer|bookshop|book store).*")) return "Education & stationery";
        if (s.matches(".*(netflix|spotify|cinema|pvr).*")) return "Entertainment";
        if (s.matches(".*(uber|rapido|irctc).*")) return "Transport";
        return "Uncategorized";
    }
}
