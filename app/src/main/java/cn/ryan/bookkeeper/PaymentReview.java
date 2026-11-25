package cn.ryan.bookkeeper;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.regex.*;
import static cn.ryan.bookkeeper.UiColors.*;

/** Settings and review UI; only an explicit confirmation can write a ledger entry. */
final class PaymentReview {
    private static final DateTimeFormatter TIME_FMT=DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final Pattern FOLDED=Pattern.compile("^\\[([0-9]+条)]\\s*");
    private final Activity activity;
    private final LedgerDb db;
    private final Runnable refresh;
    PaymentReview(Activity activity,LedgerDb db,Runnable refresh){this.activity=activity;this.db=db;this.refresh=refresh;}
    private int dp(int n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private TextView text(String value){return label(value,14,INK,false);}
    private TextView label(String value,int size,int color,boolean bold){
        TextView t=new TextView(activity);t.setText(value);t.setTextColor(color);t.setTextSize(size);t.setFontFeatureSettings("tnum");
        if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
    private Button button(String text,Runnable action){return actionButton(text,action,SOFT,PRIMARY,false);}
    private Button actionButton(String text,Runnable action,int bg,int fg,boolean bold){
        Button b=new Button(activity);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(fg);
        if(bold)b.setTypeface(null,Typeface.BOLD);
        b.setBackground(shape(bg,12));b.setStateListAnimator(null);b.setMinHeight(dp(42));b.setMinimumHeight(dp(42));
        b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setOnClickListener(v->action.run());return b;
    }
    private boolean granted(){return PaymentNotificationService.hasAccess(activity);}
    void addSettings(LinearLayout parent) {
        SharedPreferences p=PaymentNotificationService.prefs(activity);
        TextView heading=text("动账提醒 · 微信 / 支付宝");heading.setTextSize(19);heading.setTypeface(null,Typeface.BOLD);heading.setPadding(0,dp(6),0,dp(6));parent.addView(heading);
        TextView intro=text("本地识别 → 待确认 → 入账。只处理开启后新收到的通知；不会读取聊天记录。通知隐藏金额、未发通知或格式不支持时可能漏记。");intro.setTextColor(MUTED);intro.setPadding(0,0,0,dp(6));parent.addView(intro);
        Switch enabled=new Switch(activity);enabled.setText("监听动账提醒");enabled.setChecked(p.getBoolean("enabled",false));parent.addView(enabled);
        enabled.setOnCheckedChangeListener((b,on)->{p.edit().putBoolean("enabled",on).apply();if(on&&android.os.Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},41);ListeningKeepAliveService.sync(activity);refresh.run();});
        for(String pkg:new String[]{PaymentNotificationParser.WECHAT,PaymentNotificationParser.ALIPAY}) {
            Switch source=new Switch(activity);source.setText(PaymentNotificationParser.source(pkg));source.setChecked(p.getBoolean(pkg,false));parent.addView(source);source.setOnCheckedChangeListener((b,on)->p.edit().putBoolean(pkg,on).apply());
        }
        TextView stateText=text(!p.getBoolean("enabled",false)?"状态：监听已关闭":!granted()?"状态：需要授权通知访问":PaymentNotificationService.connected?"状态：通知服务已连接":"状态：已授权，等待服务连接；可尝试重新连接");stateText.setPadding(0,dp(6),0,dp(2));parent.addView(stateText);
        TextView bgText=text(ListeningKeepAliveService.running?"后台常驻：运行中，退出界面后继续监听":"后台常驻：未运行（需开启监听并授权通知访问）");bgText.setPadding(0,dp(2),0,dp(6));parent.addView(bgText);
        TextView tip=text("启用后显示持续通知，可在通知中停止监听。小米建议开启后台自启动、将省电策略设为无限制，并在系统通知管理中将微信／支付宝支付通知设为「重要」（避免被静默折叠）。");tip.setTextColor(MUTED);tip.setTextSize(13);tip.setPadding(0,0,0,dp(8));parent.addView(tip);
        if(p.getBoolean("foreground_error",false))parent.addView(text("常驻服务启动失败，请重新打开应用或检查系统后台限制。"));
        if(android.os.Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)addGapButton(parent,"允许显示监听通知",()->activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},41));
        addGapButton(parent,"刷新监听状态",refresh);
        addGapButton(parent,"应用后台与通知设置",()->{try{activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+activity.getPackageName())));}catch(ActivityNotFoundException e){toast("请在系统设置中打开有钱日记的应用信息");}});
        addGapButton(parent,"通知访问授权",()->{try{activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(ActivityNotFoundException e){toast("系统未提供通知访问设置入口");}});
        if(granted())addGapButton(parent,"重新连接监听",()->{ListeningKeepAliveService.sync(activity);PaymentNotificationService.requestReconnect(activity);toast("已请求重新连接");});
        if(p.getBoolean("storage_error",false))parent.addView(text("提醒保存曾失败，请检查手机存储空间。"));
        addGapButton(parent,"待确认账单 / 识别记录",this::showRecords);
        TextView foot=text("识别记录仅在本机保存，JSON 备份只包含正式账目、预算和分类。\n授权由 Android 系统管理；应用只筛选所选来源。普通聊天不符合规则时会跳过；提醒内容仍需核对，当前不会直接自动入账。");foot.setTextColor(MUTED);foot.setTextSize(12);foot.setPadding(0,dp(6),0,0);parent.addView(foot);
    }
    private void addGapButton(LinearLayout parent,String label,Runnable action){
        parent.addView(button(label,action),new LinearLayout.LayoutParams(-1,-2));
        parent.addView(new Space(activity),new LinearLayout.LayoutParams(1,dp(8)));
    }
    void showRecords() {
        try {
            JSONArray records=db.paymentCandidates();
            int pending=0;
            for(int i=0;i<records.length();i++)if("pending".equals(records.getJSONObject(i).getString("status")))pending++;
            final int pendingCount=pending;

            Dialog dialog=new Dialog(activity,R.style.EntrySheetTheme);
            Window window=dialog.getWindow();
            if(window!=null)window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            LinearLayout sheet=new LinearLayout(activity);sheet.setOrientation(LinearLayout.VERTICAL);
            sheet.setBackground(shape(BG,24));sheet.setPadding(dp(18),dp(18),dp(18),dp(16));

            LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout titleCol=new LinearLayout(activity);titleCol.setOrientation(LinearLayout.VERTICAL);
            titleCol.addView(label(pending>0?"待入账清单":"动账提醒记录",20,INK,true));
            titleCol.addView(label(pending>0?"待确认 "+pending+" 笔 · 共 "+records.length()+" 条":"暂无待确认 · 共 "+records.length()+" 条历史记录",12,MUTED,false));
            header.addView(titleCol,new LinearLayout.LayoutParams(0,-2,1));
            TextView closeBtn=label("关闭",14,PRIMARY,true);closeBtn.setPadding(dp(12),dp(8),dp(4),dp(8));closeBtn.setOnClickListener(v->dialog.dismiss());
            header.addView(closeBtn);
            sheet.addView(header);
            sheet.addView(new Space(activity),new LinearLayout.LayoutParams(1,dp(12)));

            ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);
            LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list);

            if(records.length()==0) {
                LinearLayout empty=new LinearLayout(activity);empty.setOrientation(LinearLayout.VERTICAL);empty.setBackground(shape(Color.WHITE,18));empty.setPadding(dp(18),dp(22),dp(18),dp(22));
                empty.addView(label("还没有动账记录",16,INK,true));
                empty.addView(new Space(activity),new LinearLayout.LayoutParams(1,dp(6)));
                empty.addView(label("开启微信／支付宝监听并收到支付通知后，会自动出现在这里。",13,MUTED,false));
                list.addView(empty);
            } else {
                int displayed=0;
                for(String state:new String[]{"pending","confirmed","ignored"})for(int i=0;i<records.length()&&displayed<100;i++) {
                    JSONObject r=records.getJSONObject(i);if(!state.equals(r.getString("status")))continue;displayed++;
                    list.addView(buildCard(dialog,r,records,state));
                    list.addView(new Space(activity),new LinearLayout.LayoutParams(1,dp(10)));
                }
                if(records.length()>100) {
                    TextView more=label("仅展示前 100 条（待确认优先），处理后可刷新查看后续。",12,MUTED,false);more.setPadding(dp(4),dp(4),dp(4),dp(8));list.addView(more);
                }
            }
            LinearLayout.LayoutParams scrollLp=new LinearLayout.LayoutParams(-1,0,1);
            int maxHeight=(int)(activity.getResources().getDisplayMetrics().heightPixels*0.62f);
            scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,records.length()>2?maxHeight:ViewGroup.LayoutParams.WRAP_CONTENT));
            if(records.length()>2)sheet.addView(scroll,new LinearLayout.LayoutParams(-1,maxHeight));
            else sheet.addView(scroll,scrollLp);

            sheet.addView(new Space(activity),new LinearLayout.LayoutParams(1,dp(12)));
            LinearLayout footer=new LinearLayout(activity);footer.setGravity(Gravity.CENTER_VERTICAL);
            if(pendingCount>1) {
                Button ignoreAll=actionButton("全部忽略",()->new AlertDialog.Builder(activity).setTitle("忽略全部 "+pendingCount+" 笔待确认？").setMessage("不会记入正式账目，之后可在记录中查看。").setNegativeButton("取消",null).setPositiveButton("全部忽略",(d,w)->{db.ignoreAllPendingPayments();dialog.dismiss();refresh.run();showRecords();}).show(),SOFT,PRIMARY,false);
                footer.addView(ignoreAll);
                footer.addView(new Space(activity),new LinearLayout.LayoutParams(dp(8),1));
            }
            if(records.length()>0) {
                Button clear=actionButton("清理记录",()->new AlertDialog.Builder(activity).setTitle("清理全部识别记录？").setMessage("包含待确认提醒，正式账目会保留。清理后将失去去重依据和来源记录。").setNegativeButton("取消",null).setPositiveButton("清理",(d,w)->{db.clearPaymentHistory();PaymentNotificationService.prefs(activity).edit().remove("storage_error").apply();dialog.dismiss();refresh.run();}).show(),Color.WHITE,MUTED,false);
                footer.addView(clear);
            }
            footer.addView(new Space(activity),new LinearLayout.LayoutParams(0,1,1));
            Button refreshBtn=actionButton("刷新",()->{dialog.dismiss();showRecords();},Color.WHITE,PRIMARY,false);
            footer.addView(refreshBtn);
            sheet.addView(footer);

            dialog.setContentView(sheet);
            if(window!=null) {
                window.setGravity(Gravity.BOTTOM);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            dialog.show();
        }catch(Exception e){toast("读取失败："+e.getMessage());}
    }
    private LinearLayout buildCard(Dialog dialog,JSONObject r,JSONArray records,String state) throws Exception {
        String id=r.getString("id");long fen=r.getLong("amount_fen");
        String type=r.optString("type");
        String stamp=Instant.ofEpochMilli(r.getLong("posted_at")).atZone(ZoneId.systemDefault()).toLocalDateTime().format(TIME_FMT);
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shape(Color.WHITE,18));card.setPadding(dp(16),dp(14),dp(16),dp(14));

        LinearLayout top=new LinearLayout(activity);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge=label(PaymentNotificationParser.source(r.getString("source")),11,PRIMARY,true);
        badge.setBackground(shape(SOFT,8));badge.setPadding(dp(8),dp(3),dp(8),dp(3));
        top.addView(badge);
        top.addView(new Space(activity),new LinearLayout.LayoutParams(dp(8),1));
        TextView timeView=label(stamp,12,MUTED,false);
        top.addView(timeView,new LinearLayout.LayoutParams(0,-2,1));
        String amountText=fen>0?"¥ "+LedgerRules.money(fen):"金额待补充";
        top.addView(label(amountText,19,fen>0?INK:DANGER,true));
        card.addView(top);
        card.addView(new Space(activity),new LinearLayout.LayoutParams(1,dp(8)));

        String rawEvidence=r.getString("evidence").replace('\n',' ').trim();
        Matcher foldedMatcher=FOLDED.matcher(rawEvidence);
        String foldedCount=foldedMatcher.find()?foldedMatcher.group(1):null;
        String cleanEvidence=foldedCount!=null?foldedMatcher.replaceFirst(""):rawEvidence;
        TextView evidenceView=label(cleanEvidence,14,INK,false);
        card.addView(evidenceView);

        String statusLabel="pending".equals(state)?r.getString("reason"):"confirmed".equals(state)?"已入账":"已忽略";
        if(foldedCount!=null&&"pending".equals(state))statusLabel="微信折叠了"+foldedCount+"，请核对是否有遗漏 · "+statusLabel;
        TextView reasonView=label(statusLabel,12,"confirmed".equals(state)?POSITIVE:MUTED,false);
        reasonView.setPadding(0,dp(4),0,0);
        card.addView(reasonView);

        if("pending".equals(state)) {
            for(int j=0;j<records.length();j++) {
                JSONObject other=records.getJSONObject(j);
                if(!id.equals(other.getString("id"))&&fen>0&&fen==other.getLong("amount_fen")&&Math.abs(r.getLong("posted_at")-other.getLong("posted_at"))<=600000) {
                    TextView dup=label("⚠ 10 分钟内有同金额提醒，请核对是否重复",12,DANGER,false);
                    dup.setPadding(0,dp(4),0,0);card.addView(dup);break;
                }
            }
            card.addView(new Space(activity),new LinearLayout.LayoutParams(1,dp(12)));
            LinearLayout actions=new LinearLayout(activity);actions.setGravity(Gravity.CENTER_VERTICAL);
            Button ignoreBtn=actionButton("忽略",()->{db.ignorePayment(id);dialog.dismiss();refresh.run();showRecords();},BG,MUTED,false);
            actions.addView(ignoreBtn);
            actions.addView(new Space(activity),new LinearLayout.LayoutParams(dp(8),1));
            boolean clearPayment=fen>0&&!type.isEmpty();
            Button reviewBtn=actionButton("核对并入账",()->{dialog.dismiss();review(r);},clearPayment?SOFT:CTA,clearPayment?PRIMARY:ON_CTA,!clearPayment);
            actions.addView(reviewBtn,new LinearLayout.LayoutParams(0,-2,1));
            if(clearPayment) {
                actions.addView(new Space(activity),new LinearLayout.LayoutParams(dp(8),1));
                Button quickBtn=actionButton("直接入账",()->quickConfirm(dialog,r,type),CTA,ON_CTA,true);
                actions.addView(quickBtn,new LinearLayout.LayoutParams(0,-2,1));
            }
            card.addView(actions);
        }
        return card;
    }
    private void quickConfirm(Dialog dialog,JSONObject record,String type) {
        try {
            String date=Instant.ofEpochMilli(record.getLong("posted_at")).atZone(ZoneId.systemDefault()).toLocalDate().toString();
            String category=db.categories(type).contains("其他")?"其他":db.categories(type).get(0);
            String note="来源："+PaymentNotificationParser.source(record.getString("source"))+"动账提醒";
            db.confirmPayment(record.getString("id"),type,record.getLong("amount_fen"),date,category,note);
            dialog.dismiss();refresh.run();toast("已入账（"+category+"）");
            if(db.pendingPaymentSummary()[0]>0)showRecords();
        }catch(Exception e){toast("入账失败："+e.getMessage());}
    }
    private void review(JSONObject record) {
        String type=record.optString("type");
        if(type.isEmpty()) {
            toast("请在顶部确认交易类型（支出 / 收入等）");
            edit(record,"expense");
        } else edit(record,type);
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
