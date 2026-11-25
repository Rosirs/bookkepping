package cn.ryan.bookkeeper;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

/** User-visible foreground lifetime, independent of MainActivity. No polling or wake locks. */
public final class ListeningKeepAliveService extends Service {
    static final String CHANNEL="payment_listening";
    static final int NOTIFICATION_ID=4101;
    static final String STOP="cn.ryan.bookkeeper.STOP_LISTENING";
    static volatile boolean running;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(prefs,key)->{
        if("enabled".equals(key)&&!prefs.getBoolean("enabled",false))stopListening();
    };
    static boolean wanted(Context context) {
        return PaymentNotificationService.prefs(context).getBoolean("enabled",false)&&PaymentNotificationService.hasAccess(context);
    }
    // Called only from a visible Activity or a permitted boot/package-replaced broadcast.
    static void sync(Context context) {
        if(!wanted(context)){context.stopService(new Intent(context,ListeningKeepAliveService.class));return;}
        try {
            context.startForegroundService(new Intent(context,ListeningKeepAliveService.class));
            PaymentNotificationService.prefs(context).edit().remove("foreground_error").apply();
        } catch(RuntimeException e) {
            PaymentNotificationService.prefs(context).edit().putBoolean("foreground_error",true).apply();
        }
    }
    @Override public void onCreate(){super.onCreate();PaymentNotificationService.prefs(this).registerOnSharedPreferenceChangeListener(changes);}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&STOP.equals(intent.getAction())) {
            PaymentNotificationService.prefs(this).edit().putBoolean("enabled",false).apply();stopListening();return START_NOT_STICKY;
        }
        if(!wanted(this)){stopListening();return START_NOT_STICKY;}
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"动账提醒后台监听",NotificationManager.IMPORTANCE_LOW));
        try {
            if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION_ID,notification(this),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION_ID,notification(this));
        } catch(RuntimeException e) {
            PaymentNotificationService.prefs(this).edit().putBoolean("foreground_error",true).apply();
            stopListening();return START_NOT_STICKY;
        }
        running=true;
        PaymentNotificationService.requestReconnect(this);
        return START_STICKY;
    }
    private static Notification notification(Context context) {
        PendingIntent open=PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(context,1,new Intent(context,ListeningKeepAliveService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_ledger)
            .setContentTitle("有钱日记 · 动账提醒监听")
            .setContentText(PaymentNotificationService.connected?"通知服务已连接，退出界面后继续监听":"常驻服务运行中，等待通知监听连接")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .addAction(new Notification.Action.Builder(null,"停止监听",stop).build()).build();
    }
    static void connectionChanged(Context context) {
        if(!wanted(context)){context.stopService(new Intent(context,ListeningKeepAliveService.class));return;}
        if(running)context.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification(context));
    }
    private void stopListening(){running=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){running=false;PaymentNotificationService.prefs(this).unregisterOnSharedPreferenceChangeListener(changes);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
