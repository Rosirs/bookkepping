package cn.ryan.bookkeeper;

import android.content.*;

public final class ListeningBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent!=null&&(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())))ListeningKeepAliveService.sync(context);
    }
}
