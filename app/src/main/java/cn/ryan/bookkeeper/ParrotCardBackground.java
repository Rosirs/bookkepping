package cn.ryan.bookkeeper;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;

/** Decorative artwork follows the content size, never its source pixel dimensions. */
final class ParrotCardBackground extends BitmapDrawable {
    ParrotCardBackground(Resources resources) {
        super(resources,BitmapFactory.decodeResource(resources,R.drawable.bg_parrot_card));
        setGravity(Gravity.FILL);setFilterBitmap(true);
    }
    @Override public int getMinimumHeight(){return 0;}
    @Override public int getMinimumWidth(){return 0;}
}
