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
    public LedgerDb(Context context) { super(context, "ledger.db", null, 2); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, type TEXT NOT NULL CHECK(type IN ('expense','income','invest','redeem')), amount_fen INTEGER NOT NULL CHECK(amount_fen>0 AND amount_fen<=999999999999), date TEXT NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL)");
        db.execSQL("CREATE INDEX entries_date ON entries(date)");
        createBudgets(db);
    }
    private void createBudgets(SQLiteDatabase db) { db.execSQL("CREATE TABLE budgets (month TEXT PRIMARY KEY, amount_fen INTEGER NOT NULL CHECK(amount_fen>0 AND amount_fen<=999999999999))"); }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { if(oldVersion<2)createBudgets(db); }
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
        return new JSONObject().put("schema_version",2).put("currency","CNY").put("entries",entries(null)).put("budgets",budgets);
    }
    public synchronized int restore(JSONObject backup) throws Exception {
        int version=backup.getInt("schema_version");
        if ((version!=1&&version!=2) || !"CNY".equals(backup.getString("currency"))) throw new IllegalArgumentException("不支持的备份格式");
        JSONArray rows=backup.getJSONArray("entries");
        JSONArray budgets=version==2?backup.getJSONArray("budgets"):new JSONArray();
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
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return rows.length();
    }
}
