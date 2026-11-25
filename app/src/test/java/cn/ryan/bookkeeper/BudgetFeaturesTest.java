package cn.ryan.bookkeeper;

import android.app.AlertDialog;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.database.sqlite.SQLiteDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.json.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class BudgetFeaturesTest {
    LedgerDb db;
    @Before public void setup() {RuntimeEnvironment.getApplication().deleteDatabase("ledger.db");db=new LedgerDb(RuntimeEnvironment.getApplication());}
    @After public void teardown() {db.close();}
    private void add(String type,long fen,String day,String category) {db.save(null,type,fen,day,category,"");}
    @Test public void dailyExpensesIgnoreIncomeInvestmentAndOtherDays() {
        add("expense",1800,"2026-11-24","餐饮");add("expense",29,"2026-11-24","交通");add("income",90000,"2026-11-24","工资");add("invest",50000,"2026-11-24","基金");add("expense",100,"2026-11-23","餐饮");
        assertEquals(1829,db.expenseOn(LocalDate.of(2026,11,24)));assertEquals(0,db.expenseOn(LocalDate.of(2026,12,1)));
    }
    @Test public void budgetCalendarHandlesTodayLastDayLeapYearFutureAndPast() {
        BudgetStatus current=new BudgetStatus(YearMonth.of(2026,11),10000,3000,LocalDate.of(2026,11,24));assertEquals(7,current.daysLeft);assertEquals(1000,current.dailyAllowance);assertEquals(300,current.progress);
        BudgetStatus last=new BudgetStatus(YearMonth.of(2026,11),10000,3000,LocalDate.of(2026,11,30));assertEquals(1,last.daysLeft);assertEquals(7000,last.dailyAllowance);
        BudgetStatus leap=new BudgetStatus(YearMonth.of(2028,2),2900,0,LocalDate.of(2028,2,1));assertEquals(29,leap.daysLeft);assertEquals(100,leap.dailyAllowance);
        BudgetStatus past=new BudgetStatus(YearMonth.of(2026,10),10000,3000,LocalDate.of(2026,11,24));assertEquals(0,past.daysLeft);assertEquals(0,past.dailyAllowance);
        BudgetStatus future=new BudgetStatus(YearMonth.of(2026,12),3100,0,LocalDate.of(2026,11,24));assertEquals(31,future.daysLeft);assertEquals(100,future.dailyAllowance);
        BudgetStatus over=new BudgetStatus(YearMonth.of(2026,11),100,101,LocalDate.of(2026,11,24));assertTrue(over.exceeded);assertEquals(-1,over.remaining);assertEquals(0,over.dailyAllowance);assertEquals(1000,over.progress);
        assertFalse(new BudgetStatus(YearMonth.of(2026,11),0,100,LocalDate.of(2026,11,24)).configured);
    }
    @Test public void budgetsPersistPerMonthAndOnlyExpensesConsumeThem() throws Exception {
        db.setBudget("2026-11",10000);db.setBudget("2026-12",20000);add("expense",1000,"2026-11-24","餐饮");add("invest",90000,"2026-11-24","基金");db.close();db=new LedgerDb(RuntimeEnvironment.getApplication());
        assertEquals(10000,db.budgetFen("2026-11"));assertEquals(20000,db.budgetFen("2026-12"));assertEquals(9000,db.summary("2026-11").getJSONObject("budget").getLong("remaining_fen"));
        db.removeBudget("2026-11");assertEquals(0,db.budgetFen("2026-11"));assertEquals(20000,db.budgetFen("2026-12"));
    }
    @Test public void backupRoundTripsBudgetsAndLegacyImportsKeepExistingBudget() throws Exception {
        db.setBudget("2026-11",10000);add("expense",123,"2026-11-24","餐饮");JSONObject backup=db.backup();assertEquals(3,backup.getInt("schema_version"));db.removeBudget("2026-11");db.restore(backup);assertEquals(10000,db.budgetFen("2026-11"));assertEquals(1,db.entries(null).length());
        db.restore(new JSONObject().put("schema_version",1).put("currency","CNY").put("entries",new JSONArray()));assertEquals(10000,db.budgetFen("2026-11"));
        backup.getJSONArray("entries").getJSONObject(0).put("amount_fen",456);
        backup.getJSONArray("budgets").getJSONObject(0).put("amount_fen",20000);
        backup.getJSONArray("budgets").put(new JSONObject().put("month","2026-13").put("amount_fen",10));
        try {db.restore(backup);fail("Invalid month must roll back entries and budgets");}catch(IllegalArgumentException expected){}
        assertEquals(10000,db.budgetFen("2026-11"));assertEquals(123,db.entries(null).getJSONObject(0).getLong("amount_fen"));
    }
    @Test public void upgradeFromVersionOneRetainsExistingLedger() {
        db.close();RuntimeEnvironment.getApplication().deleteDatabase("ledger.db");
        try(SQLiteDatabase old=RuntimeEnvironment.getApplication().openOrCreateDatabase("ledger.db",0,null)) {
            old.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY,type TEXT,amount_fen INTEGER,date TEXT,category TEXT,note TEXT)");
            old.execSQL("INSERT INTO entries VALUES ('old-entry','expense',1800,'2026-11-24','餐饮','晚餐')");old.setVersion(1);
        }
        db=new LedgerDb(RuntimeEnvironment.getApplication());assertEquals(1800,db.expenseOn(LocalDate.of(2026,11,24)));db.setBudget("2026-11",30000);assertEquals(30000,db.budgetFen("2026-11"));
    }
    @Test public void categoryRankingUsesLiveCountsAndSurvivesRepeatedImport() throws Exception {
        add("expense",100,"2026-11-20","交通");add("expense",100,"2026-11-21","交通");add("expense",100,"2026-11-24","咖啡");add("expense",100,"2026-11-23","餐饮");add("income",100,"2026-11-24","工资");
        assertEquals(Arrays.asList("交通","咖啡","餐饮"),db.frequentCategories("expense"));assertEquals(Collections.singletonList("工资"),db.frequentCategories("income"));
        JSONObject backup=db.backup();db.restore(backup);db.restore(backup);assertEquals(Arrays.asList("交通","咖啡","餐饮"),db.frequentCategories("expense"));
        JSONArray entries=db.entries(null);for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).getString("category").equals("交通"))db.delete(entries.getJSONObject(i).getString("id"));
        assertEquals(Arrays.asList("咖啡","餐饮"),db.frequentCategories("expense"));
    }
    @Test public void budgetFormRejectsZeroSavesAndRemoves() {
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a=activity.get();a.findViewById(R.id.budget_edit).performClick();idle();AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();EditText amount=dialog.findViewById(R.id.budget_amount);
            amount.setText("0");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(dialog.isShowing());
            amount.setText("3000.29");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertFalse(dialog.isShowing());assertEquals(300029,db.budgetFen(YearMonth.now().toString()));
            a.findViewById(R.id.budget_edit).performClick();idle();((AlertDialog)ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();assertEquals(0,db.budgetFen(YearMonth.now().toString()));
        }
    }
    @Test public void frequentCustomCategoryIsFirstAndDefaultsRemainAvailable() {
        add("expense",100,"2026-11-24","咖啡");
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()) {
            find(activity.get().findViewById(android.R.id.content),"＋  记一笔").performClick();Dialog dialog=ShadowDialog.getLatestDialog();View sheet=dialog.getWindow().getDecorView();assertTrue(find(sheet,"咖啡").isSelected());
            find(sheet,"全部分类 ›").performClick();Dialog more=ShadowDialog.getLatestDialog();assertNotNull(find(more.getWindow().getDecorView(),"其他"));more.dismiss();
            find(sheet,"1").performClick();dialog.findViewById(R.id.entry_save).performClick();assertEquals("咖啡",db.frequentCategories("expense").get(0));
        }
    }
    private void idle() {Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
    private View find(View view,String label) {
        if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=find(((ViewGroup)view).getChildAt(i),label);if(result!=null)return result;}
        return null;
    }
}
