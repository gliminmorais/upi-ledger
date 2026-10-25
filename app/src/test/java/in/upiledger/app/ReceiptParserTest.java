package in.upiledger.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReceiptParserTest {
    @Test public void googleReceiptUsesPaymentDateAndExactAmount() {
        ReceiptParser.Draft d=ReceiptParser.parse("To Sample Person\n₹360\nCompleted\n29 Aug 2026, 11:16 pm\n₹360 was debited\nUPI transaction ID\n123456789012\non Google Pay");
        assertEquals(36000,d.paise);assertEquals("Sample Person",d.vendor);assertEquals("2026-08-29",d.date);assertEquals("23:16",d.time);assertEquals("123456789012",d.reference);assertEquals("Completed",d.status);assertEquals("GPay",d.source);
    }
    @Test public void bhimReceiptUnderstandsOrdinalDayAndTwoDigitYear() {
        ReceiptParser.Draft d=ReceiptParser.parse("BHIM\nPaid\n₹40.00\nBanking Name\nSAMPLE COFFEE SHOP\nTransaction ID\n123456789013\nDate & Time\n24th Oct 26,\n05:54 pm");
        assertEquals(4000,d.paise);assertEquals("SAMPLE COFFEE SHOP",d.vendor);assertEquals("2026-10-24",d.date);assertEquals("17:54",d.time);assertEquals("Food",ReceiptParser.suggest(d.vendor));
    }
    @Test public void failedOrIncomingReceiptNeedsConfirmation() {
        assertEquals("Not confirmed",ReceiptParser.parse("Payment failed\n₹30\nPaid").status);
        assertEquals("Incoming / check",ReceiptParser.parse("Money received from someone\n₹30\nCompleted").status);
    }
    @Test public void missingFieldsAreNeverInvented() {
        ReceiptParser.Draft d=ReceiptParser.parse("Hello");assertEquals("",d.date);assertEquals("",d.time);assertEquals("",d.vendor);assertEquals(0,d.paise);
        assertEquals("Uncategorized",ReceiptParser.suggest("Sample Person"));
    }
    @Test public void exactPaiseAndNoonMidnight() {
        assertEquals(123456,ReceiptParser.parse("INR 1,234.56").paise);
        assertEquals("00:01",ReceiptParser.parse("1 Jan 2026 12:01 am").time);
        assertEquals("12:01",ReceiptParser.parse("1 Jan 2026 12:01 pm").time);
    }
    @Test public void instamartSuggestionAndInvalidDates() {
        assertEquals("Food",ReceiptParser.suggest("Swiggy Instamart"));
        assertEquals("",ReceiptParser.parse("31 Feb 2026 08:00 pm").date);
    }
    @Test public void missingRupeeGlyphAndAccountNumbersAreHandledConservatively() {
        assertEquals(36000,ReceiptParser.parse("To Sample\n360\nState Bank of India 3140\n360 was debited").paise);
        assertEquals(4000,ReceiptParser.parse("BHIM\nPaid\n40.00\nBanking Name\nCoffee shop").paise);
        assertEquals(0,ReceiptParser.parse("UPI transaction ID\n123456789012").paise);
    }
    @Test public void bhimRupeeMisreadUsesSizeEvidenceNotBlindLeadingDigitRemoval(){
        assertEquals("₹40.00",ReceiptParser.correctCurrencyGlyph("740.00",32,50));
        assertEquals("₹740.00",ReceiptParser.correctCurrencyGlyph("7740.00",32,50));
        assertEquals("740.00",ReceiptParser.correctCurrencyGlyph("740.00",50,50));
        assertEquals("7400",ReceiptParser.correctCurrencyGlyph("7400",50,50));
        assertEquals(74000,ReceiptParser.parse("BHIM\nPaid\n₹740.00\nBanking Name\nShop").paise);
        assertEquals(740000,ReceiptParser.parse("BHIM\nPaid\n₹7,400.00\nBanking Name\nShop").paise);
        ReceiptParser.Draft ambiguous=ReceiptParser.parse("BHIM\nPaid\n740.00\nBanking Name\nShop");
        assertEquals(0,ambiguous.paise);assertTrue(ambiguous.amountNeedsReview);
        assertEquals(4000,ReceiptParser.parse("BHIM\nPaid\n"+ReceiptParser.correctCurrencyGlyph("740.00",32,50)+"\nBanking Name\nShop").paise);
    }
    @Test public void gpayMisreadCannotSilentlyBecomeAnExpense(){
        for(String source:new String[]{"Google Pay","BHIM","Unknown app"}){
            ReceiptParser.Draft d=ReceiptParser.parse(source+"\nPaid\n740.00\nState Bank\n740.00 was debited\n740.00 sent to Shop");
            assertEquals(0,d.paise);assertTrue(d.amountNeedsReview);
        }
        assertEquals(36000,ReceiptParser.parse("Google Pay\n7360\nState Bank\n360 was debited\n7360 sent to Shop").paise);
        assertEquals(36000,ReceiptParser.parse("Google Pay\n360\nState Bank\n7360 was debited\n360 sent to Shop").paise);
        assertEquals(74000,ReceiptParser.parse("Google Pay\n₹740\nState Bank\n7740 was debited").paise);
        assertEquals(740000,ReceiptParser.parse("Google Pay\n₹7,400\nState Bank\n77400 was debited").paise);
    }
    @Test public void contradictoryAmountsRequireReview(){
        ReceiptParser.Draft d=ReceiptParser.parse("Google Pay\n360\nState Bank\n450 was debited");
        assertEquals(0,d.paise);assertTrue(d.amountNeedsReview);
        d=ReceiptParser.parse("BHIM\n₹40\n₹740");assertEquals(0,d.paise);assertTrue(d.amountNeedsReview);
    }
    @Test public void skipsFalseDateAfterDecimalAmount(){
        ReceiptParser.Draft d=ReceiptParser.parse("Google Pay\nINR 360.00\nCompleted\n29 Aug 2026, 11:16 pm");
        assertEquals("2026-08-29",d.date);assertEquals("23:16",d.time);
    }
}
