package cn.ryan.bookkeeper;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;
import static cn.ryan.bookkeeper.UiColors.*;

/** Category selection and management share one compact, scrollable bottom sheet. */
final class CategoryPicker extends Dialog {
    private final LedgerDb db;
    private final String type;
    private String selected;
    private final Consumer<String> onChange;
    private LinearLayout items;
    private TextView manage;
    private ScrollView scroll;
    private boolean managing;
    CategoryPicker(Context context,LedgerDb db,String type,String selected,Consumer<String> onChange) {
        super(context,R.style.EntrySheetTheme);this.db=db;this.type=type;this.selected=selected;this.onChange=onChange;
    }
    private int dp(int n) {return (int)(n*getContext().getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column() {LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row() {LinearLayout v=new LinearLayout(getContext());v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String name,int size,int color) {TextView v=new TextView(getContext());v.setText(name);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView action(String name,int color,Runnable run) {TextView v=text(name,14,color);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setPadding(dp(12),0,dp(12),0);v.setOnClickListener(w->run.run());v.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18876025),shape(Color.TRANSPARENT,12),null));return v;}
    @Override public void show() {
        LinearLayout surface=column();surface.setPadding(dp(20),dp(12),dp(20),dp(24));surface.setBackground(shape(BG,26));
        View handle=new View(getContext());handle.setBackground(shape(LINE,3));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(32),dp(4));hp.gravity=Gravity.CENTER;hp.bottomMargin=dp(12);surface.addView(handle,hp);
        LinearLayout heading=row();TextView title=text("选择分类",21,INK);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        manage=action("管理",PRIMARY,()->{managing=!managing;render();});heading.addView(manage);
        TextView close=action("×",MUTED,this::dismiss);close.setTextSize(26);close.setContentDescription("关闭分类面板");heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));surface.addView(heading);
        TextView hint=text(new String[]{"支出","收入","投资投入","投资收回"}[LedgerRules.TYPES.indexOf(type)]+"分类 · 点选即可使用",12,MUTED);surface.addView(hint,new LinearLayout.LayoutParams(-1,dp(32)));
        items=column();scroll=new ScrollView(getContext());scroll.setVerticalScrollBarEnabled(false);scroll.addView(items);
        int height=Math.min(dp(360),getContext().getResources().getDisplayMetrics().heightPixels/2);
        surface.addView(scroll,new LinearLayout.LayoutParams(-1,height));
        TextView add=action("＋ 新增分类",PRIMARY,()->edit(null));add.setTypeface(null,Typeface.BOLD);add.setBackground(shape(SOFT,14));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(50));ap.topMargin=dp(14);surface.addView(add,ap);
        setContentView(surface);getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);getWindow().setDimAmount(.28f);
        super.show();getWindow().setGravity(Gravity.BOTTOM);getWindow().setLayout(-1,-2);render();
    }
    private void render() {
        manage.setText(managing?"完成":"管理");items.removeAllViews();List<String> names=db.categories(type);
        int rows=managing?names.size():(names.size()+2)/3;
        scroll.getLayoutParams().height=Math.min(dp(rows*60),Math.min(dp(360),getContext().getResources().getDisplayMetrics().heightPixels/2));scroll.requestLayout();
        if(managing) {
            for(String name:names) {
                LinearLayout line=row();line.setPadding(dp(12),0,0,0);line.setBackground(shape(Color.WHITE,12));
                TextView label=text(name,15,INK);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);line.addView(label,new LinearLayout.LayoutParams(0,dp(52),1));
                TextView rename=action("改名",PRIMARY,()->edit(name));rename.setContentDescription("改名分类："+name);line.addView(rename);
                TextView delete=action("删除",DANGER,()->remove(name));delete.setContentDescription("删除分类："+name);line.addView(delete);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);items.addView(line,lp);
            }
        } else {
            for(int i=0;i<names.size();i+=3) {
                LinearLayout line=row();
                for(int j=0;j<3;j++) {
                    int index=i+j;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(52),1);if(j<2)lp.rightMargin=dp(8);
                    if(index>=names.size()){line.addView(new View(getContext()),lp);continue;}
                    String name=names.get(index);boolean active=name.equals(selected);
                    TextView tile=action(name,active?PRIMARY:INK,()->{selected=name;onChange.accept(name);dismiss();});tile.setSingleLine(true);tile.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.setSelected(active);tile.setContentDescription("分类："+name+(active?"，已选中":""));
                    GradientDrawable bg=shape(active?SOFT:Color.WHITE,14);if(active)bg.setStroke(dp(1),PRIMARY);tile.setBackground(bg);if(active)tile.setTypeface(null,Typeface.BOLD);line.addView(tile,lp);
                }
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);items.addView(line,lp);
            }
        }
    }
    private void edit(String oldName) {
        EditText input=new EditText(getContext());input.setText(oldName==null?"":oldName);input.setHint("输入分类名称");input.setTextColor(INK);input.setTextSize(17);input.setSingleLine(true);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});input.setPadding(dp(16),dp(12),dp(16),dp(12));input.setBackground(shape(BG,12));
        LinearLayout box=column();box.setPadding(dp(24),dp(12),dp(24),dp(8));box.addView(input);
        if(oldName!=null)box.addView(text("历史账目会同步使用新名称",12,MUTED));
        AlertDialog prompt=new AlertDialog.Builder(getContext()).setTitle(oldName==null?"新增分类":"分类改名").setView(box).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        prompt.setOnShowListener(v->{input.requestFocus();input.selectAll();prompt.getButton(-1).setOnClickListener(w->{
            try {
                String name=input.getText().toString().trim();
                if(oldName==null)db.addCategory(type,name);else db.renameCategory(type,oldName,name);
                if(oldName==null||oldName.equals(selected))selected=name;
                onChange.accept(selected);render();prompt.dismiss();
            } catch(IllegalArgumentException e){input.setError(e.getMessage());}
        });});prompt.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);prompt.show();
    }
    private void remove(String name) {
        ArrayList<String> targets=new ArrayList<>(db.categories(type));targets.remove(name);
        if(targets.isEmpty()){new AlertDialog.Builder(getContext()).setTitle("请保留一个分类").setMessage("可先新增分类，再删除这个分类。").setPositiveButton("知道了",null).show();return;}
        final int[] choice={0};
        new AlertDialog.Builder(getContext()).setTitle("删除「"+name+"」\n历史账目移入所选分类").setSingleChoiceItems(targets.toArray(new String[0]),0,(d,i)->choice[0]=i)
            .setNegativeButton("取消",null).setPositiveButton("转移并删除",(d,w)->{
                String replacement=targets.get(choice[0]);db.deleteCategory(type,name,replacement);
                if(name.equals(selected))selected=replacement;
                onChange.accept(selected);render();
            }).show();
    }
}
