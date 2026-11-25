package cn.ryan.bookkeeper;

import android.app.Notification;
import android.content.*;
import android.os.Bundle;
import android.service.notification.*;
import java.util.concurrent.*;

public final class PaymentNotificationService extends NotificationListenerService {
    static volatile boolean connected;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    static SharedPreferences prefs(Context c) {return c.getSharedPreferences("payment_notifications",Context.MODE_PRIVATE);}
    static boolean hasAccess(Context context) {
        ComponentName component=new ComponentName(context,PaymentNotificationService.class);
        if(android.os.Build.VERSION.SDK_INT>=27)return context.getSystemService(android.app.NotificationManager.class).isNotificationListenerAccessGranted(component);
        String listeners=android.provider.Settings.Secure.getString(context.getContentResolver(),"enabled_notification_listeners");
        if(listeners!=null)for(String item:listeners.split(":"))if(component.equals(ComponentName.unflattenFromString(item)))return true;
        return false;
    }
    static void requestReconnect(Context context) {
        if(!connected&&ListeningKeepAliveService.wanted(context))try {requestRebind(new ComponentName(context,PaymentNotificationService.class));}catch(SecurityException ignored){}
    }
    @Override public void onListenerConnected(){connected=true;ListeningKeepAliveService.connectionChanged(this);}
    @Override public void onListenerDisconnected(){connected=false;ListeningKeepAliveService.connectionChanged(this);requestReconnect(this);}
    @Override public void onDestroy(){connected=false;worker.shutdown();super.onDestroy();}
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if(sbn==null)return;
        String pkg=sbn.getPackageName();SharedPreferences p=prefs(this);
        if(!p.getBoolean("enabled",false)||!p.getBoolean(pkg,false))return;
        if(!PaymentNotificationParser.WECHAT.equals(pkg)&&!PaymentNotificationParser.ALIPAY.equals(pkg))return;
        Notification n=sbn.getNotification();
        if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return;
        Bundle e=n.extras;if(e==null)return;
        CharSequence title=e.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence body=e.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if(body==null||body.length()==0)body=e.getCharSequence(Notification.EXTRA_TEXT);
        PaymentNotificationParser.Result result=PaymentNotificationParser.parse(pkg,title==null?"":title.toString(),body==null?"":body.toString());
        if(result==null)return;
        worker.execute(()->{
            SharedPreferences current=prefs(this);
            if(!current.getBoolean("enabled",false)||!current.getBoolean(pkg,false))return;
            try(LedgerDb db=new LedgerDb(this)){db.capturePayment(pkg,java.util.UUID.nameUUIDFromBytes(sbn.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),sbn.getPostTime(),result);}
            catch(RuntimeException ignored){current.edit().putBoolean("storage_error",true).apply();}
        });
    }
}
