package cn.ryan.bookkeeper;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.*;
import static cn.ryan.bookkeeper.UiColors.*;

/** A compact entry sheet with a dedicated amount keypad and optional text editors. */
final class EntryEditor extends Dialog {
    interface CategorySource { List<String> categories(String type); }
    private final CategorySource categorySource;
    private List<String> options;
    interface OnSave { void save(String type,long fen,String date,String category,String note) throws Exception; }
    private final OnSave onSave;
    private final Runnable onDelete;
    private final boolean editing;
    private int type;
    private String category, note;
    private LocalDate date;
    private String initialAmount;
    private EditText amount;
    private TextView dateButton,noteButton,validation;
    private Button saveButton;
    private LinearLayout categories;
    private final TextView[] typeButtons=new TextView[4];
    private static final String[][] CATEGORIES={
        {"餐饮","交通","购物","居家","娱乐","医疗","其他"},
        {"工资","奖金","兼职","红包","退款","利息","其他"},
        {"基金","股票","存款","黄金","债券","其他","投资"}
    };
    EntryEditor(Context context,JSONObject existing,CategorySource categorySource,OnSave onSave,Runnable onDelete) throws Exception {
        super(context,R.style.EntrySheetTheme);
        this.onSave=onSave;this.onDelete=onDelete;this.categorySource=categorySource;editing=existing!=null;
        type=editing?LedgerRules.TYPES.indexOf(existing.getString("type")):0;
        options=categoryOptions();
        category=editing?existing.getString("category"):options.get(0);
        note=editing?existing.getString("note"):"";
        date=editing?LocalDate.parse(existing.getString("date")):LocalDate.now();
        initialAmount=editing?LedgerRules.money(existing.getLong("amount_fen")):"";
    }
    private int dp(float n) {return (int)(n*getContext().getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout column() {LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row() {LinearLayout v=new LinearLayout(getContext());v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable shape(int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String text,int size,int color,boolean bold) {
        TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(null,Typeface.BOLD);return v;
    }
    private void gap(LinearLayout parent,int height) {parent.addView(new View(getContext()),new LinearLayout.LayoutParams(1,dp(height)));}
    private TextView tap(String text,int color,Runnable action) {
        TextView v=label(text,14,color,false);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(44));v.setPadding(dp(10),0,dp(10),0);
        v.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18876025),shape(Color.TRANSPARENT,12),shape(Color.WHITE,12)));
        v.setOnClickListener(w->action.run());return v;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window=getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.28f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout sheet=column();sheet.setPadding(dp(20),dp(10),dp(20),dp(16));
        GradientDrawable surface=shape(Color.WHITE,0);surface.setCornerRadii(new float[]{dp(28),dp(28),dp(28),dp(28),0,0,0,0});sheet.setBackground(surface);
        View handle=new View(getContext());handle.setBackground(shape(LINE,3));LinearLayout.LayoutParams handleParams=new LinearLayout.LayoutParams(dp(32),dp(4));handleParams.gravity=Gravity.CENTER;sheet.addView(handle,handleParams);gap(sheet,8);
        LinearLayout heading=row();heading.addView(label(editing?"编辑这笔账":"记一笔",20,INK,true),new LinearLayout.LayoutParams(0,dp(40),1));
        if(editing){TextView delete=tap("删除",DANGER,this::confirmDelete);delete.setId(R.id.entry_delete);heading.addView(delete);}
        TextView close=tap("×",MUTED,this::dismiss);close.setTextSize(26);close.setContentDescription("关闭记账面板");heading.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));sheet.addView(heading);gap(sheet,8);

        LinearLayout segments=row();segments.setPadding(dp(4),dp(4),dp(4),dp(4));segments.setBackground(shape(BG,14));
        String[] types={"支出","收入","投资投入","投资收回"};
        for(int i=0;i<types.length;i++){final int index=i;TextView b=tap(types[i],MUTED,()->selectType(index));b.setTextSize(13);b.setPadding(0,0,0,0);typeButtons[i]=b;segments.addView(b,new LinearLayout.LayoutParams(0,dp(36),1));}
        sheet.addView(segments);gap(sheet,14);

        LinearLayout money=row();money.setBackground(shape(BG,18));money.setPadding(dp(16),dp(8),dp(14),dp(8));
        TextView currency=label("¥",27,PRIMARY,true);money.addView(currency,new LinearLayout.LayoutParams(dp(32),-1));
        amount=new EditText(getContext());amount.setId(R.id.entry_amount);amount.setContentDescription("记账金额，人民币元");amount.setTextSize(40);amount.setTextColor(INK);amount.setHintTextColor(HINT);amount.setTypeface(null,Typeface.BOLD);amount.setFontFeatureSettings("tnum");
        amount.setBackgroundColor(Color.TRANSPARENT);amount.setPadding(0,0,0,0);amount.setSingleLine(true);amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setShowSoftInputOnFocus(false);amount.setHint("0.00");amount.setText(initialAmount);amount.setSelectAllOnFocus(editing);money.addView(amount,new LinearLayout.LayoutParams(0,dp(64),1));sheet.addView(money);
        validation=label("",12,DANGER,false);validation.setVisibility(View.GONE);sheet.addView(validation);
        gap(sheet,14);
        LinearLayout categoryHeading=row();categoryHeading.addView(label("分类",12,MUTED,false));sheet.addView(categoryHeading);gap(sheet,8);
        categories=column();sheet.addView(categories);gap(sheet,12);

        LinearLayout details=row();dateButton=tap("",PRIMARY,this::pickDate);dateButton.setId(R.id.entry_date);dateButton.setBackground(shape(SOFT,12));details.addView(dateButton);
        View spacer=new View(getContext());details.addView(spacer,new LinearLayout.LayoutParams(dp(8),1));
        noteButton=tap("",MUTED,this::editNote);noteButton.setId(R.id.entry_note);noteButton.setSingleLine(true);noteButton.setEllipsize(android.text.TextUtils.TruncateAt.END);noteButton.setBackground(shape(BG,12));details.addView(noteButton,new LinearLayout.LayoutParams(0,dp(44),1));sheet.addView(details);gap(sheet,12);

        LinearLayout keypad=column();keypad.setBackground(shape(BG,18));keypad.setPadding(dp(4),dp(4),dp(4),dp(4));
        String[][] keys={{"1","2","3"},{"4","5","6"},{"7","8","9"},{".","0","⌫"}};
        for(String[] keysRow:keys){LinearLayout line=row();for(String key:keysRow){TextView b=tap(key,INK,()->key(key));b.setTextSize(24);b.setTypeface(null,Typeface.NORMAL);if(key.equals("⌫")){b.setContentDescription("删除一位");b.setOnLongClickListener(v->{amount.setText("");return true;});}line.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));}keypad.addView(line);}
        sheet.addView(keypad);gap(sheet,12);
        saveButton=new Button(getContext());saveButton.setId(R.id.entry_save);saveButton.setAllCaps(false);saveButton.setTextColor(ON_CTA);saveButton.setTextSize(16);saveButton.setTypeface(null,Typeface.BOLD);saveButton.setBackground(shape(CTA,16));saveButton.setStateListAnimator(null);saveButton.setOnClickListener(v->save());sheet.addView(saveButton,new LinearLayout.LayoutParams(-1,dp(52)));

        ScrollView scroll=new ScrollView(getContext());scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(sheet);setContentView(scroll);
        sheet.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(20),dp(10),dp(20),dp(16)+insets.getSystemWindowInsetBottom());return insets;});
        refresh();amount.requestFocus();amount.setSelection(editing?0:amount.length(),amount.length());
    }
    @Override public void show() {
        super.show();Window w=getWindow();w.setGravity(Gravity.BOTTOM);w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        w.getDecorView().requestApplyInsets();
        // Short screens can scroll; keep a visible margin above the rounded sheet.
        w.getDecorView().post(()->{int max=getContext().getResources().getDisplayMetrics().heightPixels-dp(56);if(w.getDecorView().getHeight()>max)w.setLayout(-1,max);});
    }
    private void selectType(int next) {
        if(type!=next){type=next;options=categoryOptions();category=options.get(0);refresh();}
    }
    private void refresh() {
        for(int i=0;i<typeButtons.length;i++){boolean selected=i==type;typeButtons[i].setTextColor(selected?PRIMARY:MUTED);typeButtons[i].setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);typeButtons[i].setBackground(shape(selected?Color.WHITE:Color.TRANSPARENT,10));}
        categories.removeAllViews();
        boolean hiddenSelected=!options.subList(0,7).contains(category);
        for(int r=0;r<2;r++) {
            LinearLayout line=row();
            for(int c=0;c<4;c++) {
                int index=r*4+c;boolean more=index==7;
                String name=more?(hiddenSelected?category+" ▾":"更多 ▾"):options.get(index);
                boolean selected=more?hiddenSelected:name.equals(category);
                TextView tile=tap(name,selected?PRIMARY:INK,()->{if(more)moreCategories();else{category=name;refresh();}});
                tile.setSingleLine(true);tile.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);tile.setPadding(dp(4),0,dp(4),0);
                GradientDrawable background=shape(selected?SOFT:BG,12);if(selected)background.setStroke(dp(1),PRIMARY);tile.setBackground(background);
                tile.setContentDescription(more?"更多分类，当前："+category:"分类："+name);tile.setSelected(selected);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(38),1);if(c<3)p.rightMargin=dp(8);line.addView(tile,p);
            }
            categories.addView(line);if(r==0)gap(categories,8);
        }
        dateButton.setText(date.equals(LocalDate.now())?"今天  ▾":date.getMonthValue()+"月"+date.getDayOfMonth()+"日  ▾");dateButton.setContentDescription("账目日期："+date);
        noteButton.setText(note.isEmpty()?"＋ 添加备注":note);noteButton.setContentDescription(note.isEmpty()?"添加备注":"修改备注："+note);
        saveButton.setText(new String[]{"保存支出","保存收入","保存投入","保存收回"}[type]);
    }
    private List<String> categoryOptions() {
        LinkedHashSet<String> names=new LinkedHashSet<>(categorySource.categories(LedgerRules.TYPES.get(type)));
        String[] defaults=CATEGORIES[type==0?0:type==1?1:2];
        for(String name:defaults)names.add(name);
        return new ArrayList<>(names);
    }
    private void moreCategories() {
        ArrayList<String> choices=new ArrayList<>(options);choices.add("＋ 自定义分类");
        new AlertDialog.Builder(getContext()).setTitle("选择分类").setItems(choices.toArray(new String[0]),(d,index)->{
            if(index==options.size())editCategory();else {category=options.get(index);refresh();}
        }).setNegativeButton("取消",null).show();
    }
    private void key(String key) {
        String current=amount.getText().toString();int start=Math.max(0,amount.getSelectionStart()),end=Math.max(start,amount.getSelectionEnd());
        if(key.equals("⌫")){if(start==end&&start>0)start--;amount.getText().delete(start,end);return;}
        String next=current.substring(0,start)+key+current.substring(end);
        if(next.equals("."))next="0.";
        if(!next.matches("[0-9]{1,10}(\\.[0-9]{0,2})?"))return;
        if(next.matches("0[0-9]+"))next=next.substring(1);
        amount.setText(next);amount.setSelection(Math.min(next.length(),start+key.length()+(current.isEmpty()&&key.equals(".")?1:0)));validation.setVisibility(View.GONE);
    }
    private void pickDate() {new DatePickerDialog(getContext(),(p,y,m,d)->{date=LocalDate.of(y,m+1,d);refresh();},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();}
    private void editCategory() {textEditor("自定义分类",category,40,value->{if(value.trim().isEmpty())throw new IllegalArgumentException("请输入分类名称");category=value.trim();refresh();});}
    private void editNote() {textEditor("写点备注",note,500,value->{note=value.trim();refresh();});}
    private interface OnText {void apply(String value);}
    private void textEditor(String title,String value,int limit,OnText apply) {
        EditText input=new EditText(getContext());input.setText(value);input.setTextSize(17);input.setTextColor(INK);input.setPadding(dp(14),dp(12),dp(14),dp(12));input.setBackground(shape(BG,12));input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setMaxLines(4);
        LinearLayout box=column();box.setPadding(dp(20),dp(12),dp(20),dp(8));box.addView(input);
        AlertDialog prompt=new AlertDialog.Builder(getContext()).setTitle(title).setView(box).setNegativeButton("取消",null).setPositiveButton("完成",null).create();
        prompt.setOnShowListener(v->{input.requestFocus();input.setSelection(input.length());prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{apply.apply(input.getText().toString());prompt.dismiss();}catch(IllegalArgumentException e){input.setError(e.getMessage());}});});
        prompt.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);prompt.show();
    }
    private void save() {
        try {
            String value=amount.getText().toString();if(value.endsWith("."))value+="00";
            long fen=LedgerRules.parseAmount(value);LedgerRules.validate(LedgerRules.TYPES.get(type),fen,date.toString(),category,note);
            onSave.save(LedgerRules.TYPES.get(type),fen,date.toString(),category,note);dismiss();
        }catch(Exception e){validation.setText(e.getMessage()==null?"保存失败，请重试":e.getMessage());validation.setVisibility(View.VISIBLE);}
    }
    private void confirmDelete() {new AlertDialog.Builder(getContext()).setTitle("删除这笔账目？").setMessage("删除后无法撤销。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{onDelete.run();dismiss();}).show();}
}
