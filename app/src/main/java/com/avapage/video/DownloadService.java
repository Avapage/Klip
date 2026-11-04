package com.avapage.video;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.*;
import android.provider.MediaStore;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.*;
import java.util.*;
import kotlin.Unit;

/** Runs a single user-initiated transfer independently of an Activity. */
public class DownloadService extends Service {
    private static final String ID="klip-download", CHANNEL="downloads";
    private static final int NOTIFICATION=101;
    private static final String ACTION_CANCEL="com.avapage.video.CANCEL_DOWNLOAD";
    private static volatile boolean running=false, cancel=false;
    private static volatile String status="";
    private static volatile int percent=0;
    private static volatile ArrayList<SavedFile> results=new ArrayList<>();
    private static volatile Listener listener;
    private static volatile java.lang.ref.WeakReference<DownloadService> instance = new java.lang.ref.WeakReference<>(null);
    private final Handler main=new Handler(Looper.getMainLooper());
    private long lastNotificationUpdate;
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final ArrayDeque<Intent> queue=new ArrayDeque<>();
    private int latestStartId;
    private volatile boolean cancelPending;
    private boolean completionPending;
    private static final int MAX_QUEUE=10;

    interface Listener { void changed(boolean active, String message, int progress, ArrayList<SavedFile> files); }
    static class SavedFile {
        final Uri uri; final String name, mime;
        SavedFile(Uri uri,String name,String mime) { this.uri=uri; this.name=name; this.mime=mime; }
    }
    static synchronized boolean isRunning() { return running; }
    static boolean isCancelRequested() { return cancel; }
    static synchronized void attach(Listener l) { listener=l; if(running || !status.isEmpty()) l.changed(running,status,percent,new ArrayList<>(results)); }
    static synchronized void detach(Listener l) { if(listener==l) listener=null; }
    private void update(String message,int progress) {
        status=message; percent=progress;
        long now=android.os.SystemClock.elapsedRealtime();
        if(running && (now-lastNotificationUpdate >= 1000 || cancel)) {
            lastNotificationUpdate=now;
            getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());
        }
        Listener l=listener;
        if(l!=null) { boolean active=running; ArrayList<SavedFile> copy=new ArrayList<>(results); main.post(() -> { if(listener==l) l.changed(active,message,progress,copy); }); }
    }
    static synchronized void cancelDownload() {
        if(!running || cancel) return;
        cancel=true;
        DownloadService service=instance.get();
        if(service!=null) service.cancelPending=true;
        if(service!=null) service.main.post(() -> { if(running && cancel) service.update(service.getString(R.string.ui_022),percent); });
        new Thread(() -> {
            try { YoutubeDL.getInstance().destroyProcessById(ID); }
            finally {
                if(service!=null) service.main.post(() -> {
                    service.cancelPending=false;
                    if(service.completionPending) { service.completionPending=false; service.finishCurrent(); }
                });
            }
        }).start();
    }
    public static final class CancelReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if(intent!=null && ACTION_CANCEL.equals(intent.getAction()) && isRunning()) cancelDownload();
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        instance=new java.lang.ref.WeakReference<>(this);
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,getString(R.string.ui_070),NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification() {
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent cancelIntent=new Intent(this,CancelReceiver.class).setAction(ACTION_CANCEL);
        PendingIntent cancelPi=PendingIntent.getBroadcast(this,0,cancelIntent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Klip").setContentText(cancel?getString(R.string.ui_022):percent>0?getString(R.string.ui_071)+percent+"%":getString(R.string.ui_055))
            .setProgress(100,percent,percent<=0).setOnlyAlertOnce(true).setOngoing(true).setContentIntent(pi);
        if(!cancel) builder.addAction(new Notification.Action.Builder(null,getString(R.string.ui_053),cancelPi).build());
        return builder.build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null) return START_NOT_STICKY;
        latestStartId=startId;
        if(isRunning()) {
            if(queue.size()>=MAX_QUEUE) update(getString(R.string.ui_072),percent);
            else { queue.addLast(new Intent(intent)); update(getString(R.string.ui_073)+queue.size()+getString(R.string.ui_074),percent); }
            return START_NOT_STICKY;
        }
        cancel=false; percent=0; status=getString(R.string.ui_055);
        try {
            if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFICATION,notification());
        } catch(RuntimeException e) { status=getString(R.string.ui_077); update(status,0); stopSelf(startId); return START_NOT_STICKY; }
        running=true; cancel=false; results=new ArrayList<>(); update(getString(R.string.ui_055),0);
        worker.execute(() -> runDownload(intent));
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId,int type) { cancelDownload(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { instance.clear(); worker.shutdown(); super.onDestroy(); }
    private String message(Exception e) { return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
    private void runDownload(Intent intent) {
        String url=intent.getStringExtra("url"), ref=intent.getStringExtra("ref"), agent=intent.getStringExtra("agent"), cookies=intent.getStringExtra("cookies");
        String page=intent.getStringExtra("page_url"), pageCookies=intent.getStringExtra("page_cookies");
        int selected=intent.getIntExtra("format",0);
        String formatSelector=intent.getStringExtra("format_selector");
        File dir=new File(getCacheDir(),"job-"+System.currentTimeMillis());
        ArrayList<SavedFile> saved=new ArrayList<>();
        final java.util.concurrent.atomic.AtomicBoolean wifiLost=new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicBoolean wifiWatchActive=new java.util.concurrent.atomic.AtomicBoolean(true);
        ConnectivityManager networks=getSystemService(ConnectivityManager.class);
        ConnectivityManager.NetworkCallback callback=null;
        boolean boundWifi=false;
        try {
            if(url==null || !dir.mkdirs()) throw new IOException(getString(R.string.ui_078));
            if(intent.getBooleanExtra("wifi_only",false)) {
                Network wifi=networks.getActiveNetwork();
                NetworkCapabilities caps=wifi==null?null:networks.getNetworkCapabilities(wifi);
                if(caps==null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) throw new IOException(getString(R.string.ui_079));
                callback=new ConnectivityManager.NetworkCallback() {
                    @Override public void onLost(Network lost) {
                        if(wifi.equals(lost) && wifiWatchActive.get() && running) { wifiLost.set(true); cancelDownload(); }
                    }
                };
                networks.registerDefaultNetworkCallback(callback);
                if(!networks.bindProcessToNetwork(wifi)) throw new IOException(getString(R.string.ui_080));
                boundWifi=true;
                NetworkCapabilities current=networks.getNetworkCapabilities(wifi);
                if(current==null || !current.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) throw new IOException(getString(R.string.ui_081));
            }
            YoutubeDLRequest request=new YoutubeDLRequest(url);
            request.addOption(intent.getBooleanExtra("playlist",false)?"--yes-playlist":"--no-playlist"); request.addOption("--no-mtime");
            request.addOption("--concurrent-fragments","8"); request.addOption("--abort-on-unavailable-fragments");
            if(ref!=null && !ref.isEmpty()) request.addOption("--referer",ref);
            if(agent!=null && !agent.isEmpty()) request.addOption("--user-agent",agent);
            if((cookies!=null && !cookies.isEmpty()) || (pageCookies!=null && !pageCookies.isEmpty())) {
                File cookieFile=new File(dir,".cookies.txt");
                writeCookies(this,cookieFile,url,cookies,page,pageCookies);
                request.addOption("--cookies",cookieFile.getAbsolutePath());
            }
            request.addOption("-o",new File(dir,intent.getBooleanExtra("playlist",false)
                    ? "%(playlist_index)03d - %(title).120B.%(ext)s" : "%(title).120B.%(ext)s").getAbsolutePath());
            request.addOption("--embed-metadata");
            if(selected<=3) { request.addOption("-S","res,vcodec:h264,acodec:aac"); request.addOption("--merge-output-format","mp4/mkv"); }
            if(formatSelector!=null && formatSelector.matches("[A-Za-z0-9._+/-]+")) request.addOption("-f",formatSelector);
            else if(selected==4) { request.addOption("-x"); request.addOption("--audio-format","mp3"); }
            else if(selected==5) request.addOption("-f","bestaudio/best");
            else if(selected>=1 && selected<=3) { int h=selected==1?1080:selected==2?720:480; request.addOption("-f","bestvideo[height<="+h+"]+bestaudio/best[height<="+h+"]/best"); }
            else request.addOption("-f","bestvideo+bestaudio/best");
            if(cancel) throw new IOException(getString(R.string.ui_082));
            YoutubeDL.getInstance().execute(request,ID,(p,eta,line) -> { update(cancel?getString(R.string.ui_022):line,Math.max(0,Math.min(100,(int)(float)p))); return Unit.INSTANCE; });
            if(cancel) throw new IOException(getString(R.string.ui_082));
            File[] files=dir.listFiles(f -> f.isFile() && !f.getName().equals(".cookies.txt") && !f.getName().endsWith(".part") && !f.getName().endsWith(".ytdl"));
            if(files==null || files.length==0) throw new IOException(getString(R.string.ui_083));
            for(File file:files) { if(cancel) throw new IOException(getString(R.string.ui_082)); saved.add(save(file)); }
            results=saved; status=getString(R.string.ui_084); percent=100;
        } catch(Exception e) {
            results=saved; status=!saved.isEmpty()?getString(R.string.ui_085)+message(e):wifiLost.get()?getString(R.string.ui_086):cancel?getString(R.string.ui_087):getString(R.string.ui_088)+message(e);
        } finally {
            wifiWatchActive.set(false);
            if(callback!=null) try { networks.unregisterNetworkCallback(callback); } catch(RuntimeException ignored) { }
            if(boundWifi) networks.bindProcessToNetwork(null);
            File[] files=dir.listFiles(); if(files!=null) for(File f:files) f.delete(); dir.delete();
            main.post(this::finishCurrent);
        }
    }
    private void finishCurrent() {
        synchronized(DownloadService.class) {
        if(cancelPending) { completionPending=true; return; }
        running=false;
        update(status,percent);
        Intent next=queue.pollFirst();
        if(next==null) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelfResult(latestStartId);
        } else {
            cancel=false; percent=0; results=new ArrayList<>(); running=true;
            update(getString(R.string.ui_075)+queue.size()+getString(R.string.ui_076),0);
            worker.execute(() -> runDownload(next));
        }
        }
    }
    static void writeCookies(Context context, File file, String mediaUrl, String mediaCookies, String pageUrl, String pageCookies) throws IOException {
        try(PrintWriter pw=new PrintWriter(new OutputStreamWriter(new FileOutputStream(file),java.nio.charset.StandardCharsets.UTF_8))) {
            pw.println("# Netscape HTTP Cookie File");
            appendCookies(pw,mediaUrl,mediaCookies);
            if(pageUrl!=null && !Objects.equals(Uri.parse(mediaUrl).getHost(),Uri.parse(pageUrl).getHost())) appendCookies(pw,pageUrl,pageCookies);
            if(pw.checkError()) throw new IOException(context.getString(R.string.ui_089));
        }
    }
    private static void appendCookies(PrintWriter pw, String url, String cookies) {
        if(url==null || cookies==null) return;
        Uri parsed=Uri.parse(url); String host=parsed.getHost(), scheme=parsed.getScheme();
        if(host==null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) return;
        for(String cookie:cookies.split(";")) {
            int split=cookie.indexOf('='); if(split<=0) continue;
            String name=cookie.substring(0,split).trim(), value=cookie.substring(split+1).trim();
            if(name.contains("\t")||name.contains("\n")||name.contains("\r")||value.contains("\t")||value.contains("\n")||value.contains("\r")) continue;
            pw.println(host+"\tFALSE\t/\t"+("https".equalsIgnoreCase(scheme)?"TRUE":"FALSE")+"\t0\t"+name+"\t"+value);
        }
    }
    private SavedFile save(File file) throws IOException {
        File external=getExternalFilesDir(null);
        if(external!=null && external.getUsableSpace()<file.length()) throw new IOException(getString(R.string.ui_090));
        ContentValues v=new ContentValues(); v.put(MediaStore.Downloads.DISPLAY_NAME,file.getName());
        int dot=file.getName().lastIndexOf('.'); String ext=dot>=0?file.getName().substring(dot+1).toLowerCase(Locale.ROOT):"";
        String mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext); if(mime==null) mime="application/octet-stream";
        v.put(MediaStore.Downloads.MIME_TYPE,mime); v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Klip"); v.put(MediaStore.Downloads.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v); if(uri==null) throw new IOException(getString(R.string.ui_091));
        try {
            try(InputStream in=new FileInputStream(file); OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null) throw new IOException(getString(R.string.ui_092)); byte[] buf=new byte[65536]; int n;
                while((n=in.read(buf))!=-1) { if(cancel) throw new IOException(getString(R.string.ui_082)); out.write(buf,0,n); }
            }
            ContentValues done=new ContentValues(); done.put(MediaStore.Downloads.IS_PENDING,0);
            if(getContentResolver().update(uri,done,null,null)!=1) throw new IOException(getString(R.string.ui_093));
        } catch(IOException|RuntimeException e) { getContentResolver().delete(uri,null,null); throw e; }
        String name=file.getName();
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{MediaStore.Downloads.DISPLAY_NAME},null,null,null)) { if(c!=null && c.moveToFirst()) name=c.getString(0); } catch(RuntimeException ignored) { }
        return new SavedFile(uri,name,mime);
    }
}
