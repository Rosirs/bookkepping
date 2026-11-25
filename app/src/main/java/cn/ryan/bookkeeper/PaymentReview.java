package cn.ryan.bookkeeper;

import android.app.*;
import android.content.*;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import static cn.ryan.bookkeeper.UiColors.*;

/** Settings and review UI; only an explicit confirmation can write a ledger entry. */
final class PaymentReview {
    private final Activity activity;
    private final LedgerDb db;
    private final Runnable refresh;
    PaymentReview(Activity activity,LedgerDb db,Runnable refresh){this.activity=activity;this.db=db;this.refresh=refresh;}
    private int dp(int n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value){TextView t=new TextView(activity);t.setText(value);t.setTextColor(INK);t.setTextSize(14);t.setPadding(0,dp(6),0,dp(6));return t;}
    private Button button(String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextColor(PRIMARY);b.setOnClickListener(v->action.run());return b;}
    private boolean granted(){return PaymentNotificationService.hasAccess(activity);}
    void addSettings(LinearLayout parent) {
        SharedPreferences p=PaymentNotificationService.prefs(activity);
        TextView heading=text("动账提醒 · 微信 / 支付宝");heading.setTextSize(19);heading.setTypeface(null,android.graphics.Typeface.BOLD);parent.addView(heading);
        parent.addView(text("本地识别 → 待确认 → 入账。只处理开启后新收到的通知；不会读取聊天记录。通知隐藏金额、未发通知或格式不支持时可能漏记。"));
        Switch enabled=new Switch(activity);enabled.setText("监听动账提醒");enabled.setChecked(p.getBoolean("enabled",false));parent.addView(enabled);
        enabled.setOnCheckedChangeListener((b,on)->{p.edit().putBoolean("enabled",on).apply();if(on&&android.os.Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},41);ListeningKeepAliveService.sync(activity);refresh.run();});
        for(String pkg:new String[]{PaymentNotificationParser.WECHAT,PaymentNotificationParser.ALIPAY}) {
            Switch source=new Switch(activity);source.setText(PaymentNotificationParser.source(pkg));source.setChecked(p.getBoolean(pkg,false));parent.addView(source);source.setOnCheckedChangeListener((b,on)->p.edit().putBoolean(pkg,on).apply());
        }
        parent.addView(text(!p.getBoolean("enabled",false)?"状态：监听已关闭":!granted()?"状态：需要授权通知访问":PaymentNotificationService.connected?"状态：通知服务已连接":"状态：已授权，等待服务连接；可尝试重新连接"));
        parent.addView(text(ListeningKeepAliveService.running?"后台常驻：运行中，退出界面后继续监听":"后台常驻：未运行（需开启监听并授权通知访问）"));
        parent.addView(text("启用后显示持续通知，可在通知中停止监听。小米建议开启后台自启动，并将省电策略设为无限制。强行停止或系统清理仍可能中断服务。"));
        if(p.getBoolean("foreground_error",false))parent.addView(text("常驻服务启动失败，请重新打开应用或检查系统后台限制。"));
        if(android.os.Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)parent.addView(button("允许显示监听通知",()->activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},41)));
        parent.addView(button("刷新监听状态",refresh));
        parent.addView(button("应用后台与通知设置",()->{try{activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+activity.getPackageName())));}catch(ActivityNotFoundException e){toast("请在系统设置中打开有钱日记的应用信息");}}));
        parent.addView(button("通知访问授权",()->{try{activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(ActivityNotFoundException e){toast("系统未提供通知访问设置入口");}}));
        if(granted())parent.addView(button("重新连接监听",()->{ListeningKeepAliveService.sync(activity);PaymentNotificationService.requestReconnect(activity);toast("已请求重新连接");}));
        if(p.getBoolean("storage_error",false))parent.addView(text("提醒保存曾失败，请检查手机存储空间。"));
        parent.addView(button("待确认账单 / 识别记录",this::showRecords));
        parent.addView(text("识别记录仅在本机保存，JSON 备份只包含正式账目、预算和分类。\n授权由 Android 系统管理；应用只筛选所选来源。普通聊天不符合规则时会跳过；提醒内容仍需核对，当前不会直接自动入账。"));
    }
    void showRecords() {
        try {
            JSONArray records=db.paymentCandidates();LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),dp(12));
            box.addView(text("日期按收到通知的手机本地日期预填，请核对。相同交易可能同时收到多个提醒，确认前请对照已有账目。"));
            int pending=0;
            for(int i=0;i<records.length();i++)if("pending".equals(records.getJSONObject(i).getString("status")))pending++;
            box.addView(text("待确认 "+pending+" 条 · 共 "+records.length()+" 条记录"));
            ScrollView scroll=new ScrollView(activity);scroll.addView(box);
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("动账提醒记录").setView(scroll).setPositiveButton("关闭",null).setNeutralButton("刷新",null).setNegativeButton("清理记录",null).create();
            // Pending records come first; keep the dialog bounded for long-running installations.
            int displayed=0;
            for(String state:new String[]{"pending","confirmed","ignored"})for(int i=0;i<records.length()&&displayed<100;i++) {
                JSONObject r=records.getJSONObject(i);if(!state.equals(r.getString("status")))continue;displayed++;
                String id=r.getString("id");long fen=r.getLong("amount_fen");
                String stamp=Instant.ofEpochMilli(r.getLong("posted_at")).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T',' ');
                box.addView(text(PaymentNotificationParser.source(r.getString("source"))+" · "+stamp+"\n"+(fen>0?"¥ "+LedgerRules.money(fen):"金额待补充")+" · "+("pending".equals(state)?"待确认":"confirmed".equals(state)?"已入账":"已忽略")));
                box.addView(text(r.getString("evidence")+"\n"+r.getString("reason")));
                if("pending".equals(state)) {
                    for(int j=0;j<records.length();j++) {JSONObject other=records.getJSONObject(j);if(!id.equals(other.getString("id"))&&fen>0&&fen==other.getLong("amount_fen")&&Math.abs(r.getLong("posted_at")-other.getLong("posted_at"))<=600000) {box.addView(text("⚠ 附近有相同金额的提醒，可能重复，请先核对。"));break;}}
                    box.addView(button("核对并入账",()->{dialog.dismiss();review(r);}));
                    box.addView(button("忽略这条",()->{db.ignorePayment(id);dialog.dismiss();showRecords();}));
                }
                View line=new View(activity);line.setBackgroundColor(LINE);box.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
            }
            if(records.length()>100)box.addView(text("当前展示前 100 条，待确认优先。处理后刷新可查看后续记录。"));
            dialog.setOnShowListener(v->{dialog.getButton(-3).setOnClickListener(w->{dialog.dismiss();showRecords();});dialog.getButton(-2).setOnClickListener(w->new AlertDialog.Builder(activity).setTitle("清理全部识别记录？").setMessage("包含待确认提醒，正式账目会保留。清理后将失去这些提醒的去重依据和来源记录。建议先完成核对。").setNegativeButton("取消",null).setPositiveButton("清理",(d,n)->{db.clearPaymentHistory();PaymentNotificationService.prefs(activity).edit().remove("storage_error").apply();dialog.dismiss();refresh.run();}).show());});dialog.show();
        }catch(Exception e){toast("读取失败："+e.getMessage());}
    }
    private void review(JSONObject record) {
        String type=record.optString("type");
        if(type.isEmpty())new AlertDialog.Builder(activity).setTitle("请先核对交易类型").setMessage(record.optString("reason")+"。账户间转账、还款和提现可选择忽略，避免计入生活收支。").setItems(new String[]{"生活支出","生活收入","投资投入","投资收回"},(d,n)->edit(record,LedgerRules.TYPES.get(n))).setNegativeButton("取消",null).show();
        else edit(record,type);
    }
    private void edit(JSONObject record,String type) {
        try {
            String date=Instant.ofEpochMilli(record.getLong("posted_at")).atZone(ZoneId.systemDefault()).toLocalDate().toString();
            String category=db.categories(type).contains("其他")?"其他":db.categories(type).get(0);
            JSONObject initial=new JSONObject().put("type",type).put("amount_fen",record.getLong("amount_fen")).put("date",date).put("category",category).put("note","来源："+PaymentNotificationParser.source(record.getString("source"))+"动账提醒");
            EntryEditor editor=new EntryEditor(activity,initial,db,(t,f,d,c,n)->{db.confirmPayment(record.getString("id"),t,f,d,c,n);refresh.run();},null);
            editor.show();
        }catch(Exception e){toast("打开失败："+e.getMessage());}
    }
    private void toast(String value){Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
}
