package cn.ryan.bookkeeper;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import static cn.ryan.bookkeeper.UiColors.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public final class MainActivity extends Activity {
    private LedgerDb db;
    private YearMonth month=YearMonth.now();
    private int page=0;
    private LinearLayout root,content;
    private LocalApi api;
    private final String[] typeLabels={"支出","收入","投资投入","投资收回"};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); db=new LedgerDb(this);
        if(state!=null) { month=YearMonth.parse(state.getString("month")); page=state.getInt("page"); }
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(android.os.Build.VERSION.SDK_INT>=27?BG:PRIMARY);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("month",month.toString()); out.putInt("page",page); }
    @Override protected void onStop() { super.onStop(); stopApi(); }
    @Override protected void onResume() { super.onResume(); if(db!=null) render(); }
    @Override protected void onDestroy() { stopApi(); db.close(); super.onDestroy(); }
    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable shape(int color,int radius) { GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");
        if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
    private void gap(LinearLayout l,int height) { Space s=new Space(this);l.addView(s,new LinearLayout.LayoutParams(1,dp(height))); }
    private Button button(String label,Runnable action,boolean primary) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(primary?ON_CTA:PRIMARY);
        if(primary)b.setTypeface(null,Typeface.BOLD);
        b.setBackground(shape(primary?CTA:SOFT,14));b.setStateListAnimator(null);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());return b;
    }
    private void weighted(LinearLayout parent,View view) { parent.addView(view,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout box=column();box.setPadding(dp(18),dp(18),dp(18),dp(18));box.setBackground(shape(Color.WHITE,20));parent.addView(box);return box;
    }
    private void render() {
        root=column();root.setBackgroundColor(BG);
        root.setPadding(dp(20),0,dp(20),0);
        root.setOnApplyWindowInsetsListener((v,insets)-> { v.setPadding(dp(20),insets.getSystemWindowInsetTop()+dp(12),dp(20),insets.getSystemWindowInsetBottom()+dp(8)); return insets; });
        setContentView(root); root.requestApplyInsets();
        LinearLayout head=row();weighted(head,text(getString(R.string.app_name),27,INK,true));head.addView(text("CNY · 本地",12,MUTED,false));root.addView(head);gap(root,18);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        try { if(page==2) settings(); else { monthPicker();gap(content,16); if(page==0) ledger();else statistics(); } }
        catch(Exception e) { error(e); }
        gap(root,10);
        if(page!=2) { root.addView(button("＋  记一笔",()->editor(null),true),new LinearLayout.LayoutParams(-1,dp(54)));gap(root,10); }
        LinearLayout tabs=row();String[] labels={"明细","统计","设置"};
        for(int i=0;i<labels.length;i++) { final int index=i;TextView t=text(labels[i],15,page==i?PRIMARY:MUTED,page==i);t.setId(new int[]{R.id.nav_details,R.id.nav_statistics,R.id.nav_settings}[i]);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(14),0,dp(14));t.setOnClickListener(v->{page=index;render();});weighted(tabs,t); }
        root.addView(tabs);
    }
    private void monthPicker() {
        LinearLayout line=row();Button prev=button("‹",()->{month=month.minusMonths(1);render();},false);line.addView(prev,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView label=text(month.getYear()+" 年 "+String.format(Locale.ROOT,"%02d",month.getMonthValue())+" 月",20,INK,true);label.setGravity(Gravity.CENTER);weighted(line,label);
        Button next=button("›",()->{month=month.plusMonths(1);render();},false);line.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(line);
    }
    private void metric(LinearLayout parent,String label,long fen,int color,int size) { parent.addView(text(label,12,color,false));gap(parent,6);TextView value=text(LedgerRules.money(fen),size,color,true);value.setSingleLine(true);value.setAutoSizeTextTypeUniformWithConfiguration(12,size,1,android.util.TypedValue.COMPLEX_UNIT_SP);parent.addView(value); }
    private void ledger() throws Exception {
        JSONObject sum=db.summary(month.toString());LinearLayout hero=card(content);hero.setBackground(shape(HERO,22));
        hero.addView(text(month.getMonthValue()+"月 · 生活支出",13,HERO_MUTED,false));gap(hero,8);hero.addView(text("¥ "+LedgerRules.money(sum.getLong("expense_fen")),36,HERO_AMOUNT,true));gap(hero,22);
        LinearLayout numbers=row(),today=column(),income=column(),balance=column();metric(today,"今日支出",db.expenseOn(LocalDate.now()),HERO_AMOUNT,21);metric(income,"月收入",sum.getLong("income_fen"),Color.WHITE,21);metric(balance,"生活结余",sum.getLong("balance_fen"),Color.WHITE,21);weighted(numbers,today);weighted(numbers,income);weighted(numbers,balance);hero.addView(numbers);
        gap(content,14);budgetCard(sum);
        gap(content,22);LinearLayout title=row();weighted(title,text("日常流水",18,INK,true));title.addView(text(sum.getInt("count")+" 笔",12,MUTED,false));content.addView(title);gap(content,12);
        JSONArray entries=db.entries(month.toString());
        if(entries.length()==0) { LinearLayout empty=card(content);empty.addView(text("从一笔小开销开始",21,INK,true));gap(empty,10);empty.addView(text("点击「记一笔」，记录今天的花费。\n所有账目只保存在你的手机里。",15,MUTED,false)); }
        String day="";
        for(int i=0;i<entries.length();i++) {
            JSONObject entry=entries.getJSONObject(i);String date=entry.getString("date");
            if(!date.equals(day)) {
                day=date;long expense=0;
                for(int j=i;j<entries.length() && entries.getJSONObject(j).getString("date").equals(day);j++) { JSONObject e=entries.getJSONObject(j);if(e.getString("type").equals("expense"))expense+=e.getLong("amount_fen"); }
                gap(content,12);content.addView(text(date.substring(5).replace("-"," / ")+"    支出 ¥"+LedgerRules.money(expense),12,MUTED,false));gap(content,8);
            }
            LinearLayout item=card(content),line=row();String type=entry.getString("type");int idx=LedgerRules.TYPES.indexOf(type);
            LinearLayout info=column();info.addView(text(entry.getString("category"),16,INK,true));String note=entry.getString("note");info.addView(text(typeLabels[idx]+(note.isEmpty()?"":" · "+note),12,MUTED,false));weighted(line,info);
            line.addView(text((idx==0||idx==2?"−":"+")+LedgerRules.money(entry.getLong("amount_fen")),19,idx==1||idx==3?POSITIVE:INK,true));item.addView(line);item.setOnClickListener(v->editor(entry));gap(content,6);
        }
        gap(content,20);
    }
    private void statistics() throws Exception {
        JSONObject sum=db.summary(month.toString());LinearLayout box=card(content);
        metric(box,"本月生活结余",sum.getLong("balance_fen"),PRIMARY,34);gap(box,10);box.addView(text("收入 − 日常支出；投资单独计算",12,MUTED,false));gap(content,18);
        budgetCard(sum);gap(content,18);
        LinearLayout categories=card(content);categories.addView(text("钱花在哪里",19,INK,true));gap(categories,16);
        JSONObject values=sum.getJSONObject("expense_categories");ArrayList<String> keys=new ArrayList<>();values.keys().forEachRemaining(keys::add);keys.sort((a,b)->Long.compare(values.optLong(b),values.optLong(a)));
        if(keys.isEmpty())categories.addView(text("本月还没有支出",14,MUTED,false));
        for(String key:keys) { LinearLayout line=row();weighted(line,text(key,15,INK,false));line.addView(text("¥ "+LedgerRules.money(values.getLong(key)),15,INK,true));categories.addView(line);gap(categories,8);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)(1000d*values.getLong(key)/sum.getLong("expense_fen")));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(PRIMARY));categories.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));gap(categories,16); }
        gap(content,18);LinearLayout invest=card(content);invest.addView(text("投资 · 附加记录",19,INK,true));gap(invest,14);
        metric(invest,"本月投入",sum.getLong("invest_fen"),INK,23);gap(invest,12);metric(invest,"本月收回",sum.getLong("redeem_fen"),INK,23);gap(invest,12);
        metric(invest,"累计净投入",db.summary(null).getLong("net_invested_fen"),PRIMARY,26);gap(invest,12);invest.addView(text("累计投入 − 累计收回。\n这是资金流记录，不代表持仓市值或投资盈亏。",12,MUTED,false));gap(content,20);
    }
    private void budgetCard(JSONObject sum) {
        BudgetStatus plan=new BudgetStatus(month,db.budgetFen(month.toString()),sum.optLong("expense_fen"),LocalDate.now());
        LinearLayout box=card(content),heading=row();weighted(heading,text(month.getMonthValue()+"月预算",16,INK,true));
        TextView change=text(plan.configured?"调整":"设置",14,PRIMARY,true);change.setId(R.id.budget_edit);change.setGravity(Gravity.CENTER);change.setMinHeight(dp(44));change.setMinWidth(dp(56));change.setOnClickListener(v->editBudget());heading.addView(change);box.addView(heading);
        if(!plan.configured) {
            box.addView(text("给这个月留一个消费额度",18,INK,true));gap(box,6);box.addView(text("设置后可查看剩余预算和日均可用金额。",12,MUTED,false));box.setOnClickListener(v->editBudget());return;
        }
        LinearLayout values=row(),left=column(),right=column();
        metric(left,plan.exceeded?"已超预算":"还可花",Math.abs(plan.remaining),plan.exceeded?DANGER:INK,26);
        right.addView(text("总预算 ¥"+LedgerRules.money(plan.limit),12,MUTED,false));gap(right,8);right.addView(text("已用 ¥"+LedgerRules.money(plan.spent),12,MUTED,false));weighted(values,left);weighted(values,right);box.addView(values);gap(box,12);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress(plan.progress);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(plan.exceeded?DANGER:PRIMARY));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(LINE));box.addView(progress,new LinearLayout.LayoutParams(-1,dp(5)));gap(box,10);
        String message=plan.daysLeft==0?"该月已结束":(month.equals(YearMonth.now())?"含今天还剩 ":"该月共 ")+plan.daysLeft+" 天 · 日均可用 ¥"+LedgerRules.money(plan.dailyAllowance);
        box.addView(text(message,12,MUTED,false));
    }
    private void editBudget() {
        String selectedMonth=month.toString();long current=db.budgetFen(selectedMonth);
        LinearLayout form=column();form.setPadding(dp(22),dp(12),dp(22),dp(8));form.addView(text("仅计算日常支出，投资单独记录。",13,MUTED,false));gap(form,12);
        EditText input=new EditText(this);input.setId(R.id.budget_amount);input.setTextSize(28);input.setTextColor(INK);input.setHint("输入预算金额（元）");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setSingleLine(true);if(current>0)input.setText(LedgerRules.money(current));form.addView(input);gap(form,8);form.addView(text("仅设置 "+selectedMonth+"，其他月份独立设置。",12,MUTED,false));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(month.getYear()+"年"+month.getMonthValue()+"月预算").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).setNeutralButton(current>0?"移除预算":null,null).create();
        dialog.setOnShowListener(v->{input.requestFocus();input.selectAll();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try {db.setBudget(selectedMonth,LedgerRules.parseAmount(input.getText().toString()));dialog.dismiss();render();}catch(Exception e){input.setError(e.getMessage());}});if(current>0)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->{db.removeBudget(selectedMonth);dialog.dismiss();render();});});
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);dialog.show();
    }
    private void editor(JSONObject existing) {
        try {
            new EntryEditor(this,existing,db::frequentCategories,(type,fen,date,category,note)->{
                db.save(existing==null?null:existing.getString("id"),type,fen,date,category,note);
                month=YearMonth.from(LocalDate.parse(date));
                render();
            },()->{if(existing!=null){db.delete(existing.optString("id"));render();}}).show();
        }catch(Exception e){error(e);}
    }
    private void settings() {
        LinearLayout about=card(content);about.addView(text("简单记，心里有数。",23,INK,true));gap(about,12);about.addView(text("无需账号 · 无广告 · 本地保存\n金额以人民币「分」精确存储。\n卸载前请导出备份。",14,MUTED,false));gap(content,20);
        content.addView(text("数据由你掌握",18,INK,true));gap(content,12);
        content.addView(button("导出 JSON 备份",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"own-ledger-"+LocalDate.now()+".json");startActivityForResult(i,10);},false));gap(content,10);
        content.addView(button("导入 JSON 备份",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,11);},false));gap(content,8);content.addView(text("导入按账目 ID 合并，同 ID 使用备份内容覆盖。",12,MUTED,false));gap(content,24);
        LinearLayout integration=card(content);integration.addView(text("大模型接口",19,INK,true));gap(integration,10);integration.addView(text("只读 API，仅在前台手动开启时运行。\n地址 127.0.0.1:18765，离开应用即关闭。\n桌面可通过 USB 与 adb forward 访问。",13,MUTED,false));gap(integration,12);
        integration.addView(button(api==null?"开启本机 API":"关闭本机 API",()->{if(api!=null)stopApi();else try {api=new LocalApi(db,token());}catch(Exception e){error(e);}render();},false));
        if(api!=null) { gap(integration,12);TextView credential=text("Bearer "+token(),12,INK,false);credential.setTextIsSelectable(true);integration.addView(credential);gap(integration,8);integration.addView(text("GET /v1/summary?month="+month+"\nGET /v1/entries?month="+month,12,MUTED,false)); }
        gap(content,20);content.addView(text(getString(R.string.app_name)+"  "+BuildConfig.VERSION_NAME,12,MUTED,false));
    }
    private String token() { android.content.SharedPreferences p=getSharedPreferences("api",MODE_PRIVATE);String t=p.getString("token",null);if(t==null){t=UUID.randomUUID().toString()+UUID.randomUUID().toString();p.edit().putString("token",t).apply();}return t; }
    private void stopApi() { if(api!=null){api.close();api=null;} }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        try {
            if(request==10) {try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException("无法写入文件");out.write(db.backup().toString(2).getBytes(StandardCharsets.UTF_8));}toast("备份已导出");}
            if(request==11) {ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(data.getData())) {if(in==null)throw new IOException("无法读取文件");byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(out.size()+count>4*1024*1024)throw new IOException("备份不能超过 4 MB");out.write(buffer,0,count);}}
                JSONObject backup=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
                new AlertDialog.Builder(this).setTitle("导入 "+backup.getJSONArray("entries").length()+" 笔账目？").setMessage("现有账目会保留，同 ID 账目将被覆盖。建议先导出当前备份。").setNegativeButton("取消",null).setPositiveButton("导入",(d,w)->{try{int n=db.restore(backup);toast("已导入 "+n+" 笔");render();}catch(Exception e){error(e);}}).show();
            }
        }catch(Exception e){error(e);}
    }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    private void error(Exception e) { toast(e.getMessage()==null?"操作失败，请检查输入":e.getMessage()); }
}
