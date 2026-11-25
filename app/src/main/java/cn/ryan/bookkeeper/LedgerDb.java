package cn.ryan.bookkeeper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;

public final class LedgerDb extends SQLiteOpenHelper {
    public LedgerDb(Context context) { super(context, "ledger.db", null, 4); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, type TEXT NOT NULL CHECK(type IN ('expense','income','invest','redeem')), amount_fen INTEGER NOT NULL CHECK(amount_fen>0 AND amount_fen<=999999999999), date TEXT NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL)");
        db.execSQL("CREATE INDEX entries_date ON entries(date)");
        createBudgets(db);
        createCategories(db);
        createPayments(db);
    }
    private void createBudgets(SQLiteDatabase db) { db.execSQL("CREATE TABLE budgets (month TEXT PRIMARY KEY, amount_fen INTEGER NOT NULL CHECK(amount_fen>0 AND amount_fen<=999999999999))"); }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { if(oldVersion<2)createBudgets(db); if(oldVersion<3)createCategories(db); if(oldVersion<4)createPayments(db); }
    private void createPayments(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE payment_candidates (id TEXT PRIMARY KEY, source TEXT NOT NULL, event_key TEXT NOT NULL, posted_at INTEGER NOT NULL, amount_fen INTEGER NOT NULL, type TEXT NOT NULL, evidence TEXT NOT NULL, reason TEXT NOT NULL, rule TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', entry_id TEXT)");
        db.execSQL("CREATE INDEX payment_event ON payment_candidates(source,event_key,posted_at)");
    }
    public synchronized void capturePayment(String source,String key,long posted,PaymentNotificationParser.Result result) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            // Only an identical event is collapsed: equal-price new payments must remain reviewable.
            try(Cursor c=db.rawQuery("SELECT id FROM payment_candidates WHERE source=? AND event_key=? AND evidence=? AND posted_at=?",new String[]{source,key,result.evidence(),Long.toString(posted)})) {if(c.moveToFirst()){db.setTransactionSuccessful();return;}}
            ContentValues v=new ContentValues();v.put("id",UUID.randomUUID().toString());v.put("source",source);v.put("event_key",key);v.put("posted_at",posted);v.put("amount_fen",result.fen());v.put("type",result.type());v.put("evidence",result.evidence());v.put("reason",result.reason());v.put("rule",PaymentNotificationParser.RULE);
            db.insertOrThrow("payment_candidates",null,v);db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public synchronized JSONArray paymentCandidates() throws Exception {
        JSONArray rows=new JSONArray();
        try(Cursor c=getReadableDatabase().query("payment_candidates",null,null,null,null,null,"posted_at DESC, rowid DESC")) {
            while(c.moveToNext()) {JSONObject row=new JSONObject();for(String key:new String[]{"id","source","event_key","type","evidence","reason","rule","status","entry_id"})row.put(key,c.isNull(c.getColumnIndexOrThrow(key))?JSONObject.NULL:c.getString(c.getColumnIndexOrThrow(key)));row.put("amount_fen",c.getLong(c.getColumnIndexOrThrow("amount_fen")));row.put("posted_at",c.getLong(c.getColumnIndexOrThrow("posted_at")));rows.put(row);}
        }
        return rows;
    }
    public synchronized void confirmPayment(String id,String type,long fen,String date,String category,String note) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            try(Cursor c=db.query("payment_candidates",new String[]{"status"},"id=?",new String[]{id},null,null,null)) {if(!c.moveToFirst()||!"pending".equals(c.getString(0)))throw new IllegalArgumentException("这条提醒已处理");}
            String entryId=UUID.randomUUID().toString();save(entryId,type,fen,date,category,note);
            ContentValues v=new ContentValues();v.put("status","confirmed");v.put("entry_id",entryId);db.update("payment_candidates",v,"id=?",new String[]{id});db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public synchronized void ignorePayment(String id) {ContentValues v=new ContentValues();v.put("status","ignored");getWritableDatabase().update("payment_candidates",v,"id=? AND status='pending'",new String[]{id});}
    public synchronized void clearPaymentHistory() {getWritableDatabase().delete("payment_candidates",null,null);}
    private void createCategories(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE categories (type TEXT NOT NULL, name TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(type,name))");
        String[][] defaults={{"餐饮","交通","购物","居家","娱乐","医疗","其他"},{"工资","奖金","兼职","红包","退款","利息","其他"},{"基金","股票","存款","黄金","债券","其他","投资"}};
        for(int i=0;i<LedgerRules.TYPES.size();i++)for(String name:defaults[i==0?0:i==1?1:2]) {
            ContentValues v=new ContentValues();v.put("type",LedgerRules.TYPES.get(i));v.put("name",name);db.insertOrThrow("categories",null,v);
        }
        db.execSQL("INSERT OR IGNORE INTO categories(type,name) SELECT DISTINCT type,category FROM entries");
    }
    private String categoryName(String type,String name) {
        if(!LedgerRules.TYPES.contains(type))throw new IllegalArgumentException("未知账目类型");
        if(name==null||name.trim().isEmpty()||name.trim().length()>40)throw new IllegalArgumentException("分类名称需为 1–40 个字");
        return name.trim();
    }
    private void putCategory(String type,String name,boolean enabled) {
        ContentValues v=new ContentValues();v.put("type",type);v.put("name",name);v.put("enabled",enabled?1:0);
        SQLiteDatabase database=getWritableDatabase();
        if(database.update("categories",v,"type=? AND name=?",new String[]{type,name})==0)database.insertOrThrow("categories",null,v);
    }
    public synchronized List<String> categories(String type) {
        categoryName(type,"检查");List<String> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT c.name FROM categories c LEFT JOIN entries e ON e.type=c.type AND e.category=c.name WHERE c.type=? AND c.enabled=1 GROUP BY c.name ORDER BY COUNT(e.id) DESC,MAX(e.date) DESC,c.rowid ASC",new String[]{type})) {while(c.moveToNext())result.add(c.getString(0));}
        return result;
    }
    public synchronized void addCategory(String type,String name) {
        name=categoryName(type,name);
        if(categories(type).contains(name))throw new IllegalArgumentException("已有同名分类");
        putCategory(type,name,true);
    }
    public synchronized void renameCategory(String type,String oldName,String newName) {
        newName=categoryName(type,newName);
        if(oldName.equals(newName))return;
        List<String> names=categories(type);
        if(!names.contains(oldName))throw new IllegalArgumentException("分类已不存在");
        if(names.contains(newName))throw new IllegalArgumentException("已有同名分类");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            putCategory(type,newName,true);
            ContentValues v=new ContentValues();v.put("category",newName);db.update("entries",v,"type=? AND category=?",new String[]{type,oldName});
            putCategory(type,oldName,false);db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public synchronized void deleteCategory(String type,String name,String replacement) {
        List<String> names=categories(type);
        if(name.equals(replacement)||!names.contains(name)||!names.contains(replacement))throw new IllegalArgumentException("请选择另一个分类接收账目");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put("category",replacement);db.update("entries",v,"type=? AND category=?",new String[]{type,name});
            putCategory(type,name,false);db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public synchronized long expenseOn(LocalDate day) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount_fen),0) FROM entries WHERE type='expense' AND date=?",new String[]{day.toString()})) { c.moveToFirst();return c.getLong(0); }
    }
    public synchronized long budgetFen(String month) {
        try(Cursor c=getReadableDatabase().query("budgets",new String[]{"amount_fen"},"month=?",new String[]{LedgerRules.month(month)},null,null,null)) {return c.moveToFirst()?c.getLong(0):0;}
    }
    public synchronized void setBudget(String month,long fen) {
        LedgerRules.month(month);
        if(fen<=0||fen>999999999999L)throw new IllegalArgumentException("请输入有效的正数预算");
        ContentValues values=new ContentValues();values.put("month",month);values.put("amount_fen",fen);
        getWritableDatabase().insertWithOnConflict("budgets",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized void removeBudget(String month) {getWritableDatabase().delete("budgets","month=?",new String[]{LedgerRules.month(month)});}
    public synchronized List<String> frequentCategories(String type) {
        if(!LedgerRules.TYPES.contains(type))throw new IllegalArgumentException("未知账目类型");
        List<String> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT category FROM entries WHERE type=? GROUP BY category ORDER BY COUNT(*) DESC,MAX(date) DESC,category ASC",new String[]{type})) {while(c.moveToNext())result.add(c.getString(0));}
        return result;
    }
    public synchronized void save(String id, String type, long fen, String date, String category, String note) {
        LedgerRules.validate(type, fen, date, category, note);
        putCategory(type,category.trim(),true);
        ContentValues values = new ContentValues();
        values.put("id", id == null ? UUID.randomUUID().toString() : id);
        values.put("type", type); values.put("amount_fen", fen); values.put("date", date);
        values.put("category", category.trim()); values.put("note", note.trim());
        getWritableDatabase().insertWithOnConflict("entries", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized void delete(String id) { getWritableDatabase().delete("entries", "id=?", new String[]{id}); }
    public synchronized JSONArray entries(String month) throws Exception {
        JSONArray result = new JSONArray();
        String selection = month == null ? null : "substr(date,1,7)=?";
        String[] args = month == null ? null : new String[]{LedgerRules.month(month)};
        try (Cursor c = getReadableDatabase().query("entries", null, selection, args, null, null, "date DESC, rowid DESC")) {
            while (c.moveToNext()) {
                JSONObject item = new JSONObject();
                for (String key : new String[]{"id","type","date","category","note"}) item.put(key, c.getString(c.getColumnIndexOrThrow(key)));
                item.put("amount_fen", c.getLong(c.getColumnIndexOrThrow("amount_fen")));
                result.put(item);
            }
        }
        return result;
    }
    public synchronized JSONObject summary(String month) throws Exception {
        long expense = 0, income = 0, invest = 0, redeem = 0;
        JSONObject categories = new JSONObject();
        JSONArray rows = entries(month);
        for (int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i); long fen=row.getLong("amount_fen");
            switch (row.getString("type")) {
                case "expense": expense=Math.addExact(expense,fen); String category=row.getString("category"); categories.put(category,Math.addExact(categories.optLong(category),fen)); break;
                case "income": income=Math.addExact(income,fen); break;
                case "invest": invest=Math.addExact(invest,fen); break;
                case "redeem": redeem=Math.addExact(redeem,fen); break;
            }
        }
        JSONObject result=new JSONObject().put("month",month==null?JSONObject.NULL:month).put("expense_fen",expense).put("income_fen",income)
            .put("balance_fen",income-expense).put("invest_fen",invest).put("redeem_fen",redeem)
            .put("net_invested_fen",invest-redeem).put("expense_categories",categories).put("count",rows.length());
        if(month!=null) {
            BudgetStatus plan=new BudgetStatus(YearMonth.parse(month),budgetFen(month),expense,LocalDate.now());
            result.put("budget",new JSONObject().put("configured",plan.configured).put("limit_fen",plan.limit)
                .put("remaining_fen",plan.configured?plan.remaining:JSONObject.NULL).put("days_left",plan.daysLeft)
                .put("daily_allowance_fen",plan.configured?plan.dailyAllowance:JSONObject.NULL));
        }
        return result;
    }
    public synchronized JSONObject backup() throws Exception {
        JSONArray budgets=new JSONArray();
        try(Cursor c=getReadableDatabase().query("budgets",null,null,null,null,null,"month ASC")) {while(c.moveToNext())budgets.put(new JSONObject().put("month",c.getString(c.getColumnIndexOrThrow("month"))).put("amount_fen",c.getLong(c.getColumnIndexOrThrow("amount_fen"))));}
        JSONArray categories=new JSONArray();
        try(Cursor c=getReadableDatabase().query("categories",null,null,null,null,null,"rowid ASC")) {while(c.moveToNext())categories.put(new JSONObject().put("type",c.getString(0)).put("name",c.getString(1)).put("enabled",c.getInt(2)==1));}
        return new JSONObject().put("schema_version",3).put("currency","CNY").put("entries",entries(null)).put("budgets",budgets).put("categories",categories);
    }
    public synchronized int restore(JSONObject backup) throws Exception {
        int version=backup.getInt("schema_version");
        if ((version<1||version>3) || !"CNY".equals(backup.getString("currency"))) throw new IllegalArgumentException("不支持的备份格式");
        JSONArray rows=backup.getJSONArray("entries");
        JSONArray budgets=version>=2?backup.getJSONArray("budgets"):new JSONArray();
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                String id=row.getString("id"); UUID.fromString(id);
                Object raw=row.get("amount_fen");
                if (!(raw instanceof Integer || raw instanceof Long)) throw new IllegalArgumentException("金额必须为整数分");
                save(id,row.getString("type"),row.getLong("amount_fen"),row.getString("date"),row.getString("category"),row.getString("note"));
            }
            for(int i=0;i<budgets.length();i++) {
                JSONObject budget=budgets.getJSONObject(i);Object raw=budget.get("amount_fen");
                if(!(raw instanceof Integer||raw instanceof Long))throw new IllegalArgumentException("预算必须为整数分");
                setBudget(budget.getString("month"),budget.getLong("amount_fen"));
            }
            if(version>=3) {
                JSONArray catalog=backup.getJSONArray("categories");
                for(int i=0;i<catalog.length();i++) {
                    JSONObject item=catalog.getJSONObject(i);String type=item.getString("type");
                    putCategory(type,categoryName(type,item.getString("name")),item.getBoolean("enabled"));
                }
                // Merged local entries must always retain an available category.
                db.execSQL("UPDATE categories SET enabled=1 WHERE EXISTS (SELECT 1 FROM entries WHERE entries.type=categories.type AND entries.category=categories.name)");
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return rows.length();
    }
}
