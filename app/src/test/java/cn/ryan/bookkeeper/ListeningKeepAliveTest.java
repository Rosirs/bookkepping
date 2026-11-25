package cn.ryan.bookkeeper;

import android.app.*;
import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ListeningKeepAliveTest {
    private android.app.Application app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();PaymentNotificationService.prefs(app).edit().clear().commit();ListeningKeepAliveService.running=false;PaymentNotificationService.connected=false;access(false);}
    private void access(boolean enabled){Shadows.shadowOf(app.getSystemService(NotificationManager.class)).setNotificationListenerAccessGranted(new ComponentName(app,PaymentNotificationService.class),enabled);}
    private void enable(){access(true);PaymentNotificationService.prefs(app).edit().putBoolean("enabled",true).commit();}
    @Test public void disabledOrUnauthorizedNeverStartsForeground() {
        var controller=Robolectric.buildService(ListeningKeepAliveService.class).create();
        try {
            var service=controller.get();assertEquals(Service.START_NOT_STICKY,service.onStartCommand(null,0,1));assertFalse(ListeningKeepAliveService.running);
            PaymentNotificationService.prefs(app).edit().putBoolean("enabled",true).commit();assertEquals(Service.START_NOT_STICKY,service.onStartCommand(null,0,2));
            assertEquals(0,Shadows.shadowOf(service).getLastForegroundNotificationId());
        } finally {controller.destroy();}
    }
    @Test public void persistsAfterActivityFinishesAndTaskIsRemovedUntilExplicitStop() throws Exception {
        enable();var controller=Robolectric.buildService(ListeningKeepAliveService.class).create();
        try {
            var service=controller.get();assertEquals(Service.START_STICKY,service.onStartCommand(null,0,1));
            try(var activity=Robolectric.buildActivity(MainActivity.class).setup()){activity.get().finish();}
            service.onTaskRemoved(new Intent());assertTrue(ListeningKeepAliveService.running);assertFalse(Shadows.shadowOf(service).isStoppedBySelf());
            Notification n=Shadows.shadowOf(service).getLastForegroundNotification();assertTrue((n.flags&Notification.FLAG_ONGOING_EVENT)!=0);assertNotNull(n.contentIntent);
            n.actions[0].actionIntent.send(); // Stop action is routed to the service; exercise its command below.
            service.onStartCommand(new Intent(app,ListeningKeepAliveService.class).setAction(ListeningKeepAliveService.STOP),0,2);
            assertFalse(ListeningKeepAliveService.running);assertFalse(PaymentNotificationService.prefs(app).getBoolean("enabled",true));assertTrue(Shadows.shadowOf(service).isForegroundStopped());
        } finally {controller.destroy();}
    }
    @Test public void disablingSwitchStopsRunningForegroundImmediately() {
        enable();var controller=Robolectric.buildService(ListeningKeepAliveService.class).create();
        try {var service=controller.get();service.onStartCommand(null,0,1);PaymentNotificationService.prefs(app).edit().putBoolean("enabled",false).commit();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertFalse(ListeningKeepAliveService.running);assertTrue(Shadows.shadowOf(service).isStoppedBySelf());}finally{controller.destroy();}
    }
    @Test public void bootRestoresOnlyExplicitlyEnabledAuthorizedService() {
        var receiver=new ListeningBootReceiver();receiver.onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));assertNull(Shadows.shadowOf(app).getNextStartedService());
        enable();receiver.onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));assertEquals(ListeningKeepAliveService.class.getName(),Shadows.shadowOf(app).getNextStartedService().getComponent().getClassName());
        receiver.onReceive(app,new Intent("unrelated"));assertNull(Shadows.shadowOf(app).getNextStartedService());
    }
    @Test @Config(sdk=34) public void androidFourteenDeclaresSpecialUseAndFailureIsRecoverable() {
        enable();var controller=Robolectric.buildService(ListeningKeepAliveService.class).create();
        try {
            var service=controller.get();assertEquals(Service.START_STICKY,service.onStartCommand(null,0,1));assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,service.getForegroundServiceType());
        }finally{controller.destroy();}
        controller=Robolectric.buildService(ListeningKeepAliveService.class).create();
        try {
            var service=controller.get();Shadows.shadowOf(service).setThrowInStartForeground(new SecurityException("denied"));assertEquals(Service.START_NOT_STICKY,service.onStartCommand(null,0,2));assertFalse(ListeningKeepAliveService.running);assertTrue(PaymentNotificationService.prefs(app).getBoolean("foreground_error",false));
        }finally{controller.destroy();}
    }
    @Test public void disconnectRequestsRebindAndRevocationStopsKeepAlive() {
        enable();var controller=Robolectric.buildService(PaymentNotificationService.class).create();
        try {var service=controller.get();service.onListenerConnected();assertTrue(PaymentNotificationService.connected);service.onListenerDisconnected();assertFalse(PaymentNotificationService.connected);access(false);service.onListenerDisconnected();assertFalse(ListeningKeepAliveService.wanted(app));assertEquals(ListeningKeepAliveService.class.getName(),Shadows.shadowOf(app).getNextStoppedService().getComponent().getClassName());}finally{controller.destroy();}
    }
}
