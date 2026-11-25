package cn.ryan.bookkeeper;

import android.app.*;
import android.view.*;
import android.widget.*;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CategoryFeaturesTest {
    LedgerDb db;
    @Before public void setup(){RuntimeEnvironment.getApplication().deleteDatabase("ledger.db");db=new LedgerDb(RuntimeEnvironment.getApplication());}
    @After public void teardown(){db.close();}
    @Test public void unusedCategoriesPersistAndDeletedDefaultsStayDeleted() {
        db.addCategory("expense","咖啡");db.deleteCategory("expense","餐饮","咖啡");db.close();db=new LedgerDb(RuntimeEnvironment.getApplication());
        assertTrue(db.categories("expense").contains("咖啡"));assertFalse(db.categories("expense").contains("餐饮"));assertTrue(db.categories("income").contains("工资"));
        try{db.addCategory("expense"," 咖啡 ");fail();}catch(IllegalArgumentException expected){}
        try{db.addCategory("expense"," ");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void renameAndDeleteTransferHistoryOnlyWithinType() throws Exception {
        db.save(null,"expense",123,"2026-11-25","餐饮","早餐");db.save(null,"income",456,"2026-11-25","餐饮","收入");
        db.renameCategory("expense","餐饮","吃饭");assertEquals(123,db.summary(null).getJSONObject("expense_categories").getLong("吃饭"));
        assertTrue(db.categories("income").contains("餐饮"));
        try{db.renameCategory("expense","吃饭","交通");fail();}catch(IllegalArgumentException expected){}
        db.deleteCategory("expense","吃饭","其他");assertEquals(123,db.summary(null).getJSONObject("expense_categories").getLong("其他"));
        assertEquals(2,db.entries(null).length());assertEquals(456,db.summary(null).getLong("income_fen"));assertFalse(db.categories("expense").contains("吃饭"));
        for(String name:new ArrayList<>(db.categories("expense")))if(!name.equals("其他"))db.deleteCategory("expense",name,"其他");
        try{db.deleteCategory("expense","其他","其他");fail();}catch(IllegalArgumentException expected){}
        assertEquals(Collections.singletonList("其他"),db.categories("expense"));
    }
    @Test public void catalogBackupRetainsUnusedAndDeletedCategoriesAndRollsBackOnError() throws Exception {
        db.addCategory("expense","订阅");db.deleteCategory("expense","购物","其他");JSONObject backup=db.backup();
        db.close();RuntimeEnvironment.getApplication().deleteDatabase("ledger.db");db=new LedgerDb(RuntimeEnvironment.getApplication());db.restore(backup);
        assertTrue(db.categories("expense").contains("订阅"));assertFalse(db.categories("expense").contains("购物"));
        backup.getJSONArray("categories").put(new JSONObject().put("type","expense").put("name","应回滚").put("enabled",true));
        backup.getJSONArray("categories").put(new JSONObject().put("type","bad").put("name","错误").put("enabled",true));
        try{db.restore(backup);fail();}catch(IllegalArgumentException expected){}
        assertFalse(db.categories("expense").contains("应回滚"));
    }
    @Test public void versionTwoUpgradeKeepsCustomHistoryAndSeedsCatalog() throws Exception {
        db.close();RuntimeEnvironment.getApplication().deleteDatabase("ledger.db");
        try(SQLiteDatabase old=RuntimeEnvironment.getApplication().openOrCreateDatabase("ledger.db",0,null)) {
            old.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY,type TEXT,amount_fen INTEGER,date TEXT,category TEXT,note TEXT)");
            old.execSQL("CREATE TABLE budgets (month TEXT PRIMARY KEY,amount_fen INTEGER)");
            old.execSQL("INSERT INTO entries VALUES ('old','expense',123,'2026-11-25','AI月供','保留')");old.setVersion(2);
        }
        db=new LedgerDb(RuntimeEnvironment.getApplication());assertTrue(db.categories("expense").containsAll(Arrays.asList("AI月供","餐饮")));assertEquals(123,db.entries(null).getJSONObject(0).getLong("amount_fen"));
    }
    @Test public void categorySheetAddsRenamesDeletesAndEditorHandlesFewerThanSeven() {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a=controller.get();find(a.findViewById(android.R.id.content),"＋  记一笔").performClick();Dialog editor=ShadowDialog.getLatestDialog();
            find(editor.getWindow().getDecorView(),"全部分类 ›").performClick();Dialog picker=ShadowDialog.getLatestDialog();
            find(picker.getWindow().getDecorView(),"＋ 新增分类").performClick();AlertDialog prompt=(AlertDialog)ShadowDialog.getLatestDialog();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();EditText input=findInput(prompt.getWindow().getDecorView());
            input.setText("咖啡");prompt.getButton(-1).performClick();assertTrue(db.categories("expense").contains("咖啡"));
            find(picker.getWindow().getDecorView(),"管理").performClick();findDescription(picker.getWindow().getDecorView(),"改名分类：咖啡").performClick();prompt=(AlertDialog)ShadowDialog.getLatestDialog();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();findInput(prompt.getWindow().getDecorView()).setText("手冲");prompt.getButton(-1).performClick();
            assertTrue(db.categories("expense").contains("手冲"));assertFalse(db.categories("expense").contains("咖啡"));
            findDescription(picker.getWindow().getDecorView(),"删除分类：手冲").performClick();((AlertDialog)ShadowDialog.getLatestDialog()).getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertFalse(db.categories("expense").contains("手冲"));
            picker.dismiss();editor.dismiss();
            for(String name:new ArrayList<>(db.categories("expense")))if(!name.equals("其他"))db.deleteCategory("expense",name,"其他");
            find(a.findViewById(android.R.id.content),"＋  记一笔").performClick();editor=ShadowDialog.getLatestDialog();assertNotNull(find(editor.getWindow().getDecorView(),"其他"));
            find(editor.getWindow().getDecorView(),"全部分类 ›").performClick();assertTrue(ShadowDialog.getLatestDialog().isShowing());
        }
    }
    @Test public void quickCategoriesUseOneRowForUpToFourAndNeverReserveMoreTile() throws Exception {
        for(String name:new ArrayList<>(db.categories("expense")))if(!name.equals("餐饮"))db.deleteCategory("expense",name,"餐饮");
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            for(int count=1;count<=9;count++) {
                if(count>1)db.addCategory("expense","分类"+count);
                EntryEditor editor=new EntryEditor(controller.get(),null,db,(t,f,d,c,n)->{},()->{});editor.show();
                View root=editor.getWindow().getDecorView();ViewGroup grid=(ViewGroup)findDescription(root,"快捷分类");
                assertEquals("count="+count,count<=4?1:3,grid.getChildCount());
                assertEquals(count<=4?count:count<=6?3:4,((ViewGroup)grid.getChildAt(0)).getChildCount());
                assertNotNull(find(root,"全部分类 ›"));assertNull(find(root,"更多 ▾"));
                if(count<=8)for(String name:db.categories("expense"))assertNotNull(find(grid,name));
                if(count==9) {
                    find(root,"全部分类 ›").performClick();Dialog picker=ShadowDialog.getLatestDialog();
                    find(picker.getWindow().getDecorView(),"分类9").performClick();
                    assertTrue(find(grid,"分类9").isSelected());picker.dismiss();
                }
                editor.dismiss();
            }
        }
    }
    private View find(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View match=find(((ViewGroup)v).getChildAt(i),label);if(match!=null)return match;}return null;}
    private View findDescription(View v,String value){if(value.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View match=findDescription(((ViewGroup)v).getChildAt(i),value);if(match!=null)return match;}return null;}
    private EditText findInput(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText match=findInput(((ViewGroup)v).getChildAt(i));if(match!=null)return match;}return null;}
}
