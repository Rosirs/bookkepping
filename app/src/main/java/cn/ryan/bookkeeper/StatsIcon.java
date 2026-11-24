package cn.ryan.bookkeeper;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small native line icons, using the same weight at every screen density. */
final class StatsIcon extends View {
    private final int kind,color;
    StatsIcon(Context context,int kind,int color){super(context);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.8f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        if(kind==0){canvas.drawRoundRect(3,5,21,20,3,3,p);canvas.drawLine(6,5,6,3,p);canvas.drawLine(6,3,18,3,p);canvas.drawRoundRect(14,10,22,16,2,2,p);canvas.drawPoint(17,13,p);}
        else if(kind==1||kind==2){canvas.drawCircle(12,12,9,p);canvas.drawLine(12,7,12,17,p);Path arrow=new Path();if(kind==1){arrow.moveTo(8,11);arrow.lineTo(12,7);arrow.lineTo(16,11);}else{arrow.moveTo(8,13);arrow.lineTo(12,17);arrow.lineTo(16,13);}canvas.drawPath(arrow,p);}
        else{canvas.drawArc(3,3,21,21,0,270,false,p);canvas.drawLine(12,3,12,12,p);canvas.drawLine(12,12,21,12,p);canvas.drawArc(15,2,22,9,270,90,false,p);canvas.drawLine(15,2,15,9,p);canvas.drawLine(15,9,22,9,p);}
        canvas.restore();
    }
}
