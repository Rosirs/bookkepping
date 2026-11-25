package cn.ryan.bookkeeper;

import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.service.notification.StatusBarNotification;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class PaymentNotificationsTest {
    private LedgerDb db;
    private final String wx=PaymentNotificationParser.WECHAT;
    @Before public void setup(){RuntimeEnvironment.getApplication().deleteDatabase("ledger.db");db=new LedgerDb(RuntimeEnvironment.getApplication());PaymentNotificationService.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit();}
    @After public void close(){db.close();}
    private PaymentNotificationParser.Result parse(String title,String body){return PaymentNotificationParser.parse(wx,title,body);}
    @Test public void rejectsChatOtpAndUnsupportedSources() {
        assertNull(parse("朋友","今天支付成功12元"));assertNull(parse("微信支付","验证码123456，支付12元"));assertNull(PaymentNotificationParser.parse("other","微信支付","支付成功12元"));
    }
    @Test public void parsesIntegerFenButNeverGuessesAmbiguousAmounts() {
        var out=parse("微信支付","支付成功￥12.34");assertEquals(1234,out.fen());assertEquals("expense",out.type());
        assertEquals("income",parse("微信支付","收款到账100元").type());
        assertEquals(0,parse("支付宝交易提醒","消费12元，余额100元").fen());
        assertEquals(0,parse("微信支付","支付成功，金额已隐藏").fen());
        assertEquals(0,parse("微信支付","支付成功0元").fen());
        assertEquals(0,parse("微信支付","支付成功12.345元").fen());
    }
    @Test public void specialTransactionsRequireExplicitTypeAndSensitiveNumbersAreMasked() {
        for(String body:new String[]{"退款到账12元","转账成功12元","还款成功12元","提现到账12元","支付失败12元","待支付12元","支付成功12元，余额30元"})assertEquals("",parse("微信支付",body).type());
        assertFalse(parse("微信支付","支付成功12元，订单1234567890123456").evidence().contains("1234567890123456"));
    }
    @Test public void repeatedEventsAreDeduplicatedButSeparateOrUpdatedEventsRemainReviewable() throws Exception {
        var r=parse("微信支付","支付成功12元");db.capturePayment(wx,"one",1000000,r);db.capturePayment(wx,"one",1000000,r);assertEquals(1,db.paymentCandidates().length());
        db.capturePayment(wx,"two",1000100,r);db.capturePayment(wx,"one",1000200,parse("微信支付","支付成功13元"));db.capturePayment(PaymentNotificationParser.ALIPAY,"one",1000100,r);assertEquals(4,db.paymentCandidates().length());assertEquals(0,db.entries(null).length());
    }
    @Test public void confirmIsAtomicAndCannotBeRepeatedOrConfirmedAfterIgnoring() throws Exception {
        db.capturePayment(wx,"one",1000,parse("微信支付","支付成功12元"));String id=db.paymentCandidates().getJSONObject(0).getString("id");
        try{db.confirmPayment(id,"bad",1200,"2026-11-25","其他","");fail();}catch(IllegalArgumentException expected){}
        assertEquals("pending",db.paymentCandidates().getJSONObject(0).getString("status"));assertEquals(0,db.entries(null).length());
        db.confirmPayment(id,"expense",1200,"2026-11-25","餐饮","已核对");
        try{db.confirmPayment(id,"expense",1200,"2026-11-25","餐饮","");fail();}catch(IllegalArgumentException expected){}
        assertEquals(1,db.entries(null).length());assertEquals(db.entries(null).getJSONObject(0).getString("id"),db.paymentCandidates().getJSONObject(0).getString("entry_id"));
        db.capturePayment(wx,"two",2000,parse("微信支付","收款到账10元"));String ignored=db.paymentCandidates().getJSONObject(0).getString("id");db.ignorePayment(ignored);
        try{db.confirmPayment(ignored,"income",1000,"2026-11-25","其他","");fail();}catch(IllegalArgumentException expected){}
        db.clearPaymentHistory();assertEquals(1,db.entries(null).length());
    }
    @Test public void versionThreeUpgradePreservesLedgerAndAuditSurvivesReopen() throws Exception {
        db.save(null,"expense",1234,"2026-11-25","餐饮","原账目");db.setBudget("2026-11",50000);
        db.getWritableDatabase().execSQL("DROP TABLE payment_candidates");db.getWritableDatabase().setVersion(3);db.close();db=new LedgerDb(RuntimeEnvironment.getApplication());
        assertEquals(1,db.entries(null).length());assertEquals(50000,db.budgetFen("2026-11"));assertTrue(db.categories("expense").contains("餐饮"));
        db.capturePayment(wx,"one",1000,parse("微信支付","支付成功12元"));db.close();db=new LedgerDb(RuntimeEnvironment.getApplication());assertEquals(1,db.paymentCandidates().length());
        // Existing backup schema stays compatible; confirmed entries are included, audit is device-local.
        assertEquals(3,db.backup().getInt("schema_version"));assertEquals(1,db.backup().getJSONArray("entries").length());
    }
    @Test public void serviceDefaultsOffAndRequiresExplicitSource() throws Exception {
        var controller=Robolectric.buildService(PaymentNotificationService.class).create();
        try {
            PaymentNotificationService service=controller.get();Notification n=new Notification();n.extras=new Bundle();n.extras.putCharSequence(Notification.EXTRA_TITLE,"微信支付");n.extras.putCharSequence(Notification.EXTRA_TEXT,"支付成功12元");
            StatusBarNotification event=new StatusBarNotification(wx,wx,1,null,1000,0,0,n,android.os.Process.myUserHandle(),1000);
            service.onNotificationPosted(event);assertEquals(0,db.paymentCandidates().length());
            PaymentNotificationService.prefs(service).edit().putBoolean("enabled",true).commit();service.onNotificationPosted(event);assertEquals(0,db.paymentCandidates().length());
            PaymentNotificationService.prefs(service).edit().putBoolean(wx,true).commit();service.onNotificationPosted(event);
            var field=PaymentNotificationService.class.getDeclaredField("worker");field.setAccessible(true);((java.util.concurrent.ExecutorService)field.get(service)).submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1,db.paymentCandidates().length());assertEquals(0,db.entries(null).length());
        } finally {controller.destroy();}
    }
    @Test public void reviewUiRequiresValidAmountBeforeConfirmingAndSupportsIgnoring() throws Exception {
        db.capturePayment(wx,"one",System.currentTimeMillis(),parse("微信支付","支付成功，金额已隐藏"));
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a=controller.get();new PaymentReview(a,db,()->{}).showRecords();
            Dialog records=org.robolectric.shadows.ShadowDialog.getLatestDialog();
            find(records.getWindow().getDecorView(),"核对并入账").performClick();
            Dialog editor=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNull(editor.findViewById(R.id.entry_delete));
            ((android.widget.TextView)editor.findViewById(R.id.entry_save)).performClick();assertEquals(0,db.entries(null).length());
            ((android.widget.EditText)editor.findViewById(R.id.entry_amount)).setText("12.34");editor.findViewById(R.id.entry_save).performClick();
            assertEquals(1234,db.entries(null).getJSONObject(0).getLong("amount_fen"));
            db.capturePayment(wx,"two",System.currentTimeMillis(),parse("微信支付","退款到账10元"));
            new PaymentReview(a,db,()->{}).showRecords();records=org.robolectric.shadows.ShadowDialog.getLatestDialog();
            find(records.getWindow().getDecorView(),"忽略这条").performClick();assertEquals(1,db.entries(null).length());
        }
    }
    @Test @Config(sdk=26) public void settingsWorksOnAndroidEightAndOpensSystemAuthorization() {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a=controller.get();android.widget.LinearLayout parent=new android.widget.LinearLayout(a);
            new PaymentReview(a,db,()->{}).addSettings(parent);find(parent,"通知访问授权").performClick();
            assertEquals(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,Shadows.shadowOf(a).getNextStartedActivity().getAction());
        }
    }
    private android.view.View find(android.view.View v,String label){
        if(v instanceof android.widget.TextView&&label.contentEquals(((android.widget.TextView)v).getText()))return v;
        if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){var match=find(((android.view.ViewGroup)v).getChildAt(i),label);if(match!=null)return match;}
        return null;
    }

}
