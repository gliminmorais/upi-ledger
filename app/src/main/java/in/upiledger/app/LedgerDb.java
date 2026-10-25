package in.upiledger.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public final class LedgerDb extends SQLiteOpenHelper {
    public static final String[] CATEGORIES = {"Uncategorized", "Food", "Groceries", "Shopping", "Entertainment", "Education & stationery", "Transport", "Bills", "Health", "Personal transfers", "Subscription"};
    public static class Vendor { public long id; public String name, category; }
    public static class Entry { public long id, vendorId, paise; public long ownPaise=-1; public long spent(){return ownPaise<0?paise:ownPaise;} public String vendor, category, date, time, remarks, reference, source; }
    public static class Subscription {public long id,vendorId,paise;public String name,vendor,category,nextDue,remarks;public int renewalDay;public boolean active;}
    public LedgerDb(Context c) { super(c, "ledger.db", null, 5); }
    LedgerDb(Context c,String testDatabase) { super(c,testDatabase,null,5); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        createCategories(db);
        db.execSQL("CREATE TABLE vendors (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE UNIQUE, category TEXT NOT NULL)");
        db.execSQL("CREATE TABLE aliases (alias TEXT PRIMARY KEY COLLATE NOCASE, vendor_id INTEGER NOT NULL REFERENCES vendors(id))");
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY, vendor_id INTEGER NOT NULL REFERENCES vendors(id), paise INTEGER NOT NULL CHECK(paise>0), category TEXT NOT NULL, day TEXT NOT NULL, time TEXT NOT NULL, remarks TEXT NOT NULL, reference TEXT NOT NULL, source TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX unique_reference ON entries(reference) WHERE reference <> ''");
        createSubscriptions(db);createBudgets(db);createSplits(db);
    }
    private void createSubscriptions(SQLiteDatabase db){
        ContentValues category=new ContentValues();category.put("name","Subscription");db.insertWithOnConflict("categories",null,category,SQLiteDatabase.CONFLICT_IGNORE);
        db.execSQL("CREATE TABLE IF NOT EXISTS subscriptions (id INTEGER PRIMARY KEY,name TEXT NOT NULL,vendor_id INTEGER NOT NULL REFERENCES vendors(id),paise INTEGER NOT NULL CHECK(paise>0),category TEXT NOT NULL,next_due TEXT NOT NULL,renewal_day INTEGER NOT NULL CHECK(renewal_day BETWEEN 1 AND 31),active INTEGER NOT NULL CHECK(active IN (0,1)),remarks TEXT NOT NULL)");
        Set<String> columns=new HashSet<>();try(Cursor c=db.rawQuery("PRAGMA table_info(entries)",null)){while(c.moveToNext())columns.add(c.getString(c.getColumnIndexOrThrow("name")));}
        if(!columns.contains("subscription_id"))db.execSQL("ALTER TABLE entries ADD COLUMN subscription_id INTEGER REFERENCES subscriptions(id) ON DELETE SET NULL");
        if(!columns.contains("subscription_due"))db.execSQL("ALTER TABLE entries ADD COLUMN subscription_due TEXT NOT NULL DEFAULT ''");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS unique_subscription_due ON entries(subscription_id,subscription_due) WHERE subscription_id IS NOT NULL");
    }
    public static class Repayment {public long id,entryId,paise;public String from,date,remarks,vendor,paymentDate;}
    private void createSplits(SQLiteDatabase db){
        Set<String> columns=new HashSet<>();try(Cursor c=db.rawQuery("PRAGMA table_info(entries)",null)){while(c.moveToNext())columns.add(c.getString(c.getColumnIndexOrThrow("name")));}
        if(!columns.contains("own_paise"))db.execSQL("ALTER TABLE entries ADD COLUMN own_paise INTEGER NOT NULL DEFAULT -1 CHECK(own_paise>=-1 AND own_paise<=paise)");
        db.execSQL("CREATE TABLE IF NOT EXISTS repayments (id INTEGER PRIMARY KEY,entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,paise INTEGER NOT NULL CHECK(paise>0),sender TEXT NOT NULL,day TEXT NOT NULL,remarks TEXT NOT NULL)");
    }
    public List<Repayment> repayments(){List<Repayment> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT r.id,r.entry_id,r.paise,r.sender,r.day,r.remarks,v.name,e.day FROM repayments r JOIN entries e ON e.id=r.entry_id JOIN vendors v ON v.id=e.vendor_id ORDER BY r.day DESC,r.id DESC",null)){while(c.moveToNext()){Repayment r=new Repayment();r.id=c.getLong(0);r.entryId=c.getLong(1);r.paise=c.getLong(2);r.from=c.getString(3);r.date=c.getString(4);r.remarks=c.getString(5);r.vendor=c.getString(6);r.paymentDate=c.getString(7);out.add(r);}}return out;}
    public long received(long entryId){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(paise),0) FROM repayments WHERE entry_id=?",new String[]{""+entryId})){c.moveToFirst();return c.getLong(0);}}
    public void addRepayment(Repayment r){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{Entry entry=null;for(Entry e:entries())if(e.id==r.entryId)entry=e;
        if(entry==null||entry.ownPaise<0)throw new IllegalArgumentException("Choose a saved split payment.");
        r.from=r.from.trim();java.time.LocalDate date=java.time.LocalDate.parse(r.date);
        if(r.from.isEmpty()||r.from.length()>100||r.remarks.length()>2000||r.paise<=0||r.paise>entry.paise-entry.spent()-received(r.entryId))throw new IllegalArgumentException("Check the name and amount. Repayments cannot exceed your friends' share.");
        if(date.isBefore(java.time.LocalDate.parse(entry.date))||date.isAfter(java.time.LocalDate.now()))throw new IllegalArgumentException("Repayment date must be between the payment date and today.");
        ContentValues v=new ContentValues();v.put("entry_id",r.entryId);v.put("paise",r.paise);v.put("sender",r.from);v.put("day",r.date);v.put("remarks",r.remarks);r.id=db.insertOrThrow("repayments",null,v);db.setTransactionSuccessful();
        }finally{db.endTransaction();}}
    public void deleteRepayment(long id){getWritableDatabase().delete("repayments","id=?",new String[]{""+id});}
    public static class Budget {public String month,category;public long paise;}
    private void createBudgets(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS budgets (month TEXT NOT NULL,category TEXT NOT NULL REFERENCES categories(name),paise INTEGER NOT NULL CHECK(paise>0 AND paise<=100000000000),PRIMARY KEY(month,category))");}
    public List<Budget> budgets(){List<Budget> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT month,category,paise FROM budgets ORDER BY month,category",null)){while(c.moveToNext()){Budget b=new Budget();b.month=c.getString(0);b.category=c.getString(1);b.paise=c.getLong(2);out.add(b);}}return out;}
    public void saveBudgets(List<Budget> budgets){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(Budget b:budgets)saveBudget(b.month,b.category,b.paise);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public void saveBudget(String month,String category,long paise){java.time.YearMonth.parse(month);if(!hasCategory(category)||paise<0||paise>100000000000L)throw new IllegalArgumentException("Choose a category and a valid budget.");if(paise==0){getWritableDatabase().delete("budgets","month=? AND category=?",new String[]{month,category});return;}ContentValues v=new ContentValues();v.put("month",month);v.put("category",category);v.put("paise",paise);getWritableDatabase().insertWithOnConflict("budgets",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    private void createCategories(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE categories (name TEXT PRIMARY KEY COLLATE NOCASE NOT NULL)");
        for(String name:CATEGORIES){ContentValues v=new ContentValues();v.put("name",name);db.insertOrThrow("categories",null,v);}
    }
    private void retainUsedCategories(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery("SELECT category FROM vendors UNION SELECT category FROM entries",null)) {
            while(c.moveToNext()){String name=c.getString(0);validateCategoryName(name);ContentValues v=new ContentValues();v.put("name",name);db.insertWithOnConflict("categories",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
        }
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if(oldV==1){createCategories(db);retainUsedCategories(db);}
        if(oldV<3)createSubscriptions(db);if(oldV<4)createBudgets(db);if(oldV<5)createSplits(db);
    }
    private void validateCategoryName(String name){if(name==null||name.trim().isEmpty()||name.length()>60||!name.equals(name.trim())||name.equals("__create__"))throw new IllegalArgumentException("Use a category name with 1–60 characters.");}
    public List<String> categories(){List<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM categories ORDER BY CASE WHEN name='Uncategorized' THEN 0 ELSE 1 END,name COLLATE NOCASE",null)){while(c.moveToNext())out.add(c.getString(0));}return out;}
    public boolean hasCategory(String name){try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM categories WHERE name=?",new String[]{name})){return c.moveToFirst();}}
    public void addCategory(String value){String name=value.trim();validateCategoryName(name);if(hasCategory(name))throw new IllegalArgumentException("That category already exists.");ContentValues v=new ContentValues();v.put("name",name);getWritableDatabase().insertOrThrow("categories",null,v);}
    public long addVendor(String value,String category){String name=value.trim();if(name.isEmpty()||name.length()>200||!hasCategory(category))throw new IllegalArgumentException("Enter a vendor name and choose a category.");for(Vendor v:vendors())if(v.name.equalsIgnoreCase(name))throw new IllegalArgumentException("That vendor already exists.");return vendor(name,category);}
    public List<Vendor> vendors() {
        List<Vendor> out = new ArrayList<>();
        try(Cursor c = getReadableDatabase().rawQuery("SELECT id,name,category FROM vendors ORDER BY name COLLATE NOCASE", null)) {
            while(c.moveToNext()) { Vendor v = new Vendor(); v.id=c.getLong(0); v.name=c.getString(1); v.category=c.getString(2); out.add(v); }
        } return out;
    }
    public Vendor resolve(String name) {
        for(Vendor v : vendors()) if(v.name.equalsIgnoreCase(name.trim())) return v;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT vendor_id FROM aliases WHERE alias=?", new String[]{name.trim()})) {
            if(c.moveToFirst()) { long id=c.getLong(0); for(Vendor v:vendors()) if(v.id==id) return v; }
        } return null;
    }
    private long vendor(String name, String category) {
        for(Vendor v:vendors()) if(v.name.equalsIgnoreCase(name.trim())) return v.id;
        ContentValues cv=new ContentValues(); cv.put("name",name.trim()); cv.put("category",category);
        return getWritableDatabase().insertOrThrow("vendors",null,cv);
    }
    public void updateVendor(long id,String name,String category) {
        if(name.trim().isEmpty()||name.length()>200||!hasCategory(category))throw new IllegalArgumentException("Check the vendor name and category.");
        ContentValues v=new ContentValues(); v.put("name",name.trim());v.put("category",category);
        getWritableDatabase().update("vendors",v,"id=?",new String[]{""+id});
    }
    public List<Entry> entries() {
        List<Entry> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.id,e.vendor_id,v.name,e.paise,e.category,e.day,e.time,e.remarks,e.reference,e.source,e.own_paise FROM entries e JOIN vendors v ON e.vendor_id=v.id ORDER BY e.day DESC,e.time DESC,e.id DESC",null)) {
            while(c.moveToNext()) { Entry e=new Entry();e.id=c.getLong(0);e.vendorId=c.getLong(1);e.vendor=c.getString(2);e.paise=c.getLong(3);e.category=c.getString(4);e.date=c.getString(5);e.time=c.getString(6);e.remarks=c.getString(7);e.reference=c.getString(8);e.source=c.getString(9);e.ownPaise=c.getLong(10);out.add(e); }
        } return out;
    }
    public boolean duplicate(String ref,long excluding) {
        if(ref.trim().isEmpty()) return false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM entries WHERE reference=? AND id<>?",new String[]{ref.trim(),""+excluding})) { return c.moveToFirst(); }
    }
    public void save(Entry e, boolean setDefault, String alias) {
        save(e,setDefault,alias,0,"");
    }
    public void save(Entry e,boolean setDefault,String alias,long subscriptionId,String expectedDue){
        if(!hasCategory(e.category))throw new IllegalArgumentException("Choose an existing category or create one first.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Subscription subscription=null;
            if(subscriptionId>0){for(Subscription s:subscriptions())if(s.id==subscriptionId)subscription=s;
                if(e.id!=0||subscription==null||!subscription.active||!subscription.nextDue.equals(expectedDue))throw new IllegalArgumentException("This renewal changed or was already recorded. Reopen the subscription.");}
            if(e.paise<=0||e.paise>100000000000L||e.ownPaise< -1||e.ownPaise>e.paise)throw new IllegalArgumentException("Your share must be between zero and the full payment.");
            if(e.id>0){long received=received(e.id);if(received>e.paise-e.spent())throw new IllegalArgumentException("Your friends have already repaid more than this split allows. Correct the repayments first.");for(Repayment r:repayments())if(r.entryId==e.id&&r.date.compareTo(e.date)<0)throw new IllegalArgumentException("Payment date cannot be after a recorded repayment.");}
            e.vendorId=vendor(e.vendor,e.category);
            if(setDefault) updateVendor(e.vendorId,e.vendor,e.category);
            if(!alias.trim().isEmpty()&&!alias.trim().equalsIgnoreCase(e.vendor.trim())) {
                ContentValues a=new ContentValues();a.put("alias",alias.trim());a.put("vendor_id",e.vendorId);db.insertWithOnConflict("aliases",null,a,SQLiteDatabase.CONFLICT_REPLACE);
            }
            ContentValues v=new ContentValues();v.put("vendor_id",e.vendorId);v.put("paise",e.paise);v.put("own_paise",e.ownPaise);v.put("category",e.category);v.put("day",e.date);v.put("time",e.time);v.put("remarks",e.remarks);v.put("reference",e.reference.trim());v.put("source",e.source);
            if(subscription!=null){v.put("subscription_id",subscription.id);v.put("subscription_due",expectedDue);}
            if(e.id==0) e.id=db.insertOrThrow("entries",null,v); else if(db.update("entries",v,"id=?",new String[]{""+e.id})!=1)throw new IllegalArgumentException("Payment not found.");
            if(subscription!=null){ContentValues next=new ContentValues();next.put("next_due",SubscriptionDates.nextDue(expectedDue,subscription.renewalDay));db.update("subscriptions",next,"id=?",new String[]{""+subscription.id});}
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public List<Subscription> subscriptions(){List<Subscription> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT s.id,s.vendor_id,v.name,s.name,s.paise,s.category,s.next_due,s.renewal_day,s.active,s.remarks FROM subscriptions s JOIN vendors v ON v.id=s.vendor_id ORDER BY s.active DESC,s.next_due,s.name COLLATE NOCASE",null)){while(c.moveToNext()){Subscription s=new Subscription();s.id=c.getLong(0);s.vendorId=c.getLong(1);s.vendor=c.getString(2);s.name=c.getString(3);s.paise=c.getLong(4);s.category=c.getString(5);s.nextDue=c.getString(6);s.renewalDay=c.getInt(7);s.active=c.getInt(8)==1;s.remarks=c.getString(9);rows.add(s);}}return rows;}
    public void saveSubscription(Subscription s){
        s.name=s.name.trim();s.vendor=s.name;s.category="Subscription";
        if(s.name.trim().isEmpty()||s.name.length()>200||s.vendor.trim().isEmpty()||s.vendor.length()>200||s.paise<=0||s.paise>100000000000L||!hasCategory(s.category)||s.renewalDay<1||s.renewalDay>31||s.remarks.length()>2000)throw new IllegalArgumentException("Check the subscription name, vendor, amount and category.");
        java.time.LocalDate date=java.time.LocalDate.parse(s.nextDue);if(date.getDayOfMonth()!=Math.min(s.renewalDay,date.lengthOfMonth()))throw new IllegalArgumentException("Next renewal must match the renewal day (or the last day of a shorter month).");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            for(Subscription prior:subscriptions())if(prior.id!=s.id&&prior.name.equalsIgnoreCase(s.name.trim())&&prior.vendor.equalsIgnoreCase(s.vendor.trim()))throw new IllegalArgumentException("This subscription already exists for that vendor.");
            long vendorId=vendor(s.vendor,s.category);updateVendor(vendorId,s.vendor,"Subscription");ContentValues v=new ContentValues();v.put("name",s.name.trim());v.put("vendor_id",vendorId);v.put("paise",s.paise);v.put("category",s.category);v.put("next_due",s.nextDue);v.put("renewal_day",s.renewalDay);v.put("active",s.active?1:0);v.put("remarks",s.remarks);
            if(s.id==0)s.id=db.insertOrThrow("subscriptions",null,v);else if(db.update("subscriptions",v,"id=?",new String[]{""+s.id})!=1)throw new IllegalArgumentException("Subscription not found.");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void deleteSubscription(long id){getWritableDatabase().delete("subscriptions","id=?",new String[]{""+id});}
    public void cancelSubscription(long id){ContentValues v=new ContentValues();v.put("active",0);getWritableDatabase().update("subscriptions",v,"id=?",new String[]{""+id});}
    public void postDueSubscriptions(String today){
        java.time.LocalDate cutoff=java.time.LocalDate.parse(today);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            for(Subscription s:subscriptions()){
                int cycles=0;
                while(s.active&&!java.time.LocalDate.parse(s.nextDue).isAfter(cutoff)){
                    if(++cycles>1200)throw new IllegalArgumentException("Subscription history exceeds 100 years.");
                    boolean recorded;try(Cursor c=db.rawQuery("SELECT id FROM entries WHERE subscription_id=? AND subscription_due=?",new String[]{""+s.id,s.nextDue})){recorded=c.moveToFirst();}
                    if(!recorded){Entry e=new Entry();e.vendor=s.name;e.paise=s.paise;e.category="Subscription";e.date=s.nextDue;e.time="00:00";e.remarks="Automatically recorded monthly subscription"+(s.remarks.isEmpty()?"":". "+s.remarks);e.reference="";e.source="Subscription";save(e,false,"",s.id,s.nextDue);}
                    else {ContentValues next=new ContentValues();next.put("next_due",SubscriptionDates.nextDue(s.nextDue,s.renewalDay));db.update("subscriptions",next,"id=?",new String[]{""+s.id});}
                    s.nextDue=SubscriptionDates.nextDue(s.nextDue,s.renewalDay);
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void delete(long id) {getWritableDatabase().delete("entries","id=?",new String[]{""+id});}
    public JSONObject backup() throws JSONException {
        JSONObject root=new JSONObject();root.put("version",5);
        for(String table:new String[]{"categories","vendors","aliases","subscriptions","entries","budgets","repayments"}) {
            JSONArray rows=new JSONArray();
            try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM "+table,null)) { while(c.moveToNext()) {JSONObject row=new JSONObject();for(int i=0;i<c.getColumnCount();i++) row.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));rows.put(row);} }
            root.put(table,rows);
        } return root;
    }
    public void restore(JSONObject root) throws JSONException {
        int version=root.getInt("version");if(version<1||version>5) throw new JSONException("Unsupported backup version");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("repayments",null,null);db.delete("budgets",null,null);db.delete("entries",null,null);db.delete("aliases",null,null);db.delete("subscriptions",null,null);db.delete("vendors",null,null);
            db.delete("categories",null,null);
            if(version==1)for(String name:CATEGORIES){ContentValues v=new ContentValues();v.put("name",name);db.insertOrThrow("categories",null,v);}
            for(String table:version==5?new String[]{"categories","vendors","aliases","subscriptions","entries","budgets","repayments"}:version==4?new String[]{"categories","vendors","aliases","subscriptions","entries","budgets"}:version==3?new String[]{"categories","vendors","aliases","subscriptions","entries"}:version==2?new String[]{"categories","vendors","aliases","entries"}:new String[]{"vendors","aliases","entries"}) {
                JSONArray rows=root.getJSONArray(table);
                for(int i=0;i<rows.length();i++) {JSONObject row=rows.getJSONObject(i);ContentValues cv=new ContentValues();Iterator<String> keys=row.keys();while(keys.hasNext()) {String key=keys.next();Object value=row.get(key);if(value==JSONObject.NULL)cv.putNull(key);else if(value instanceof Number) cv.put(key,((Number)value).longValue());else cv.put(key,value.toString());}db.insertOrThrow(table,null,cv);}
            }
            if(version==1)retainUsedCategories(db);
            if(!hasCategory("Subscription")){ContentValues cv=new ContentValues();cv.put("name","Subscription");db.insertOrThrow("categories",null,cv);}
            for(String name:categories())validateCategoryName(name);
            if(!hasCategory("Uncategorized"))throw new JSONException("Missing default category");
            for(Entry e:entries()) {java.time.LocalDate.parse(e.date);java.time.LocalTime.parse(e.time);if(e.paise<=0||e.paise>100000000000L||e.ownPaise< -1||e.ownPaise>e.paise||received(e.id)>e.paise-e.spent() || !hasCategory(e.category)) throw new JSONException("Invalid transaction");}
            for(Repayment r:repayments()){java.time.LocalDate.parse(r.date);Entry entry=null;for(Entry e:entries())if(e.id==r.entryId)entry=e;if(entry==null||entry.ownPaise<0||r.paise<=0||r.from.trim().isEmpty()||r.from.length()>100||r.remarks.length()>2000||r.date.compareTo(entry.date)<0||r.date.compareTo(java.time.LocalDate.now().toString())>0)throw new JSONException("Invalid repayment");}
            for(Vendor v:vendors()) if(v.name.trim().isEmpty()||!hasCategory(v.category)) throw new JSONException("Invalid vendor");
            for(Subscription sub:subscriptions()){
                java.time.LocalDate date=java.time.LocalDate.parse(sub.nextDue);
                if(sub.name.trim().isEmpty()||sub.name.length()>200||!sub.category.equals("Subscription")||!sub.vendor.equals(sub.name)||sub.paise<=0||sub.renewalDay<1||sub.renewalDay>31||date.getDayOfMonth()!=Math.min(sub.renewalDay,date.lengthOfMonth()))throw new JSONException("Invalid subscription");
            }
            try(Cursor c=db.rawQuery("SELECT subscription_due FROM entries WHERE subscription_id IS NOT NULL",null)){while(c.moveToNext())java.time.LocalDate.parse(c.getString(0));}
            for(Budget b:budgets()){java.time.YearMonth.parse(b.month);if(!hasCategory(b.category)||b.paise<=0||b.paise>100000000000L)throw new JSONException("Invalid budget");}
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}
