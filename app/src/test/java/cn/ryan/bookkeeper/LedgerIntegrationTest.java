package cn.ryan.bookkeeper;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class LedgerIntegrationTest {
    LedgerDb db;
    @Before public void setup() { RuntimeEnvironment.getApplication().deleteDatabase("ledger.db");db=new LedgerDb(RuntimeEnvironment.getApplication()); }
    @After public void teardown() { db.close(); }
    private void add(String type,long fen,String date) { db.save(null,type,fen,date,"测试",""); }
    @Test public void investmentsDoNotInflateLivingExpenses() throws Exception {
        add("income",100000,"2026-11-01");add("expense",29,"2026-11-02");add("invest",50000,"2026-11-02");add("redeem",10000,"2026-11-03");add("expense",200,"2026-10-01");
        JSONObject s=db.summary("2026-11");assertEquals(29,s.getLong("expense_fen"));assertEquals(99971,s.getLong("balance_fen"));assertEquals(40000,s.getLong("net_invested_fen"));assertEquals(4,db.entries("2026-11").length());
    }
    @Test public void editsPersistAndDeleteRemovesOnlySelectedRow() throws Exception {
        add("expense",20,"2026-11-01");String id=db.entries(null).getJSONObject(0).getString("id");
        db.save(id,"income",30,"2026-10-02","工资","更正");db.close();db=new LedgerDb(RuntimeEnvironment.getApplication());
        assertEquals(0,db.entries("2026-11").length());assertEquals(30,db.summary("2026-10").getLong("income_fen"));db.delete(id);assertEquals(0,db.entries(null).length());
    }
    @Test public void backupIsIdempotentAndMalformedImportRollsBackEverything() throws Exception {
        add("expense",29,"2026-11-01");JSONObject backup=db.backup();db.restore(backup);db.restore(backup);assertEquals(1,db.entries(null).length());
        JSONArray rows=backup.getJSONArray("entries");JSONObject original=new JSONObject(rows.getJSONObject(0).toString());original.put("amount_fen",100);rows.put(0,original);
        JSONObject invalid=new JSONObject(original.toString()).put("id",java.util.UUID.randomUUID().toString()).put("date","2026-02-30");rows.put(invalid);
        try {db.restore(backup);fail("must reject malformed import");}catch(RuntimeException expected){}
        assertEquals(29,db.entries(null).getJSONObject(0).getLong("amount_fen"));assertEquals(1,db.entries(null).length());
    }
    private String request(String method,String path,String auth) throws Exception {
        try(Socket s=new Socket("127.0.0.1",18765)) { s.setSoTimeout(5000);s.getOutputStream().write((method+" "+path+" HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer "+auth+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));return new String(s.getInputStream().readAllBytes(),StandardCharsets.UTF_8); }
    }
    @Test public void apiRequiresTokenAndNeverWrites() throws Exception {
        add("expense",29,"2026-11-01");try(LocalApi api=new LocalApi(db,"test-token")) {
            assertTrue(request("GET","/v1/summary?month=2026-11","wrong").startsWith("HTTP/1.1 401"));
            String response=request("GET","/v1/summary?month=2026-11","test-token");assertTrue(response.startsWith("HTTP/1.1 200"));assertTrue(response.contains("\"expense_fen\":29"));
            assertTrue(request("GET","/v1/entries?month=2026-13","test-token").startsWith("HTTP/1.1 400"));
            assertTrue(request("POST","/v1/entries","test-token").startsWith("HTTP/1.1 405"));
            assertEquals(1,db.entries(null).length());
        }
    }
    @Test public void allThreeScreensCanRender() {
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()) {
            android.view.View root=activity.get().findViewById(android.R.id.content);
            assertNotNull(find(root,"日常流水"));find(root,"统计").performClick();
            root=activity.get().findViewById(android.R.id.content);assertNotNull(find(root,"钱花在哪里"));activity.get().findViewById(R.id.nav_settings).performClick();
            assertNotNull(find(activity.get().findViewById(android.R.id.content),"数据由你掌握"));
        }
    }
    @Test public void entryFormSavesExactCents() throws Exception {
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()) {
            find(activity.get().findViewById(android.R.id.content),"＋  记一笔").performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
            android.view.View sheet=dialog.getWindow().getDecorView();
            for(String key:new String[]{"0",".","2","9"})find(sheet,key).performClick();
            find(sheet,"交通").performClick();
            dialog.findViewById(R.id.entry_save).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(dialog.isShowing());assertEquals(29,db.entries(null).getJSONObject(0).getLong("amount_fen"));
            assertEquals("交通",db.entries(null).getJSONObject(0).getString("category"));
        }
    }
    @Test public void editorKeepsDraftOnInvalidAmountAndLimitsDecimalPlaces() throws Exception {
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()) {
            find(activity.get().findViewById(android.R.id.content),"＋  记一笔").performClick();
            android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();android.view.View sheet=dialog.getWindow().getDecorView();
            dialog.findViewById(R.id.entry_save).performClick();assertTrue(dialog.isShowing());assertEquals(0,db.entries(null).length());
            for(String key:new String[]{"1",".","2","3","4"})find(sheet,key).performClick();
            assertEquals("1.23",((android.widget.EditText)dialog.findViewById(R.id.entry_amount)).getText().toString());
            find(sheet,"收入").performClick();find(sheet,"奖金").performClick();dialog.findViewById(R.id.entry_save).performClick();
            JSONObject row=db.entries(null).getJSONObject(0);assertEquals("income",row.getString("type"));assertEquals("奖金",row.getString("category"));assertEquals(123,row.getLong("amount_fen"));
        }
    }
    @Test public void editingOnlyChangesAmountAndCancelDoesNotSave() throws Exception {
        add("expense",1800,"2026-11-24");JSONObject original=db.entries(null).getJSONObject(0);original.put("category","咖啡").put("note","早餐");db.restore(new JSONObject().put("schema_version",1).put("currency","CNY").put("entries",new JSONArray().put(original)));
        try(var activity=Robolectric.buildActivity(MainActivity.class).setup()) {
            final String id=original.getString("id");
            EntryEditor editor=new EntryEditor(activity.get(),original,db,(type,fen,date,category,note)->db.save(id,type,fen,date,category,note),()->db.delete(id));editor.show();
            android.view.View sheet=editor.getWindow().getDecorView();find(sheet,"2").performClick();find(sheet,"5").performClick();editor.findViewById(R.id.entry_save).performClick();
            JSONObject saved=db.entries(null).getJSONObject(0);assertEquals(2500,saved.getLong("amount_fen"));assertEquals("咖啡",saved.getString("category"));assertEquals("早餐",saved.getString("note"));assertEquals("2026-11-24",saved.getString("date"));
            EntryEditor canceled=new EntryEditor(activity.get(),saved,db,(type,fen,date,category,note)->fail("cancel must not save"),()->fail("cancel must not delete"));canceled.show();canceled.dismiss();assertEquals(1,db.entries(null).length());
        }
    }
    private android.view.View find(android.view.View v,String label) {
        if(v instanceof android.widget.TextView && label.contentEquals(((android.widget.TextView)v).getText()))return v;
        if(v instanceof android.view.ViewGroup) {android.view.ViewGroup group=(android.view.ViewGroup)v;for(int i=0;i<group.getChildCount();i++){android.view.View found=find(group.getChildAt(i),label);if(found!=null)return found;}}
        return null;
    }
}
