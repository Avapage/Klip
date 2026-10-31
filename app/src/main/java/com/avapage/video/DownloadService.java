package com.avapage.video;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.Uri;
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
    private static volatile boolean running=false, cancel=false;
    private static volatile String status="";
    private static volatile int percent=0;
    private static volatile ArrayList<SavedFile> results=new ArrayList<>();
    private static volatile Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();

    interface Listener { void changed(boolean active, String message, int progress, ArrayList<SavedFile> files); }
    static class SavedFile {
        final Uri uri; final String name, mime;
        SavedFile(Uri uri,String name,String mime) { this.uri=uri; this.name=name; this.mime=mime; }
    }
    static synchronized boolean isRunning() { return running; }
    static synchronized void attach(Listener l) { listener=l; if(running || !status.isEmpty()) l.changed(running,status,percent,new ArrayList<>(results)); }
    static synchronized void detach(Listener l) { if(listener==l) listener=null; }
    private void update(String message,int progress) {
        status=message; percent=progress;
        Listener l=listener;
        if(l!=null) { ArrayList<SavedFile> copy=new ArrayList<>(results); main.post(() -> { if(listener==l) l.changed(running,message,progress,copy); }); }
    }
    static void cancelDownload() { cancel=true; new Thread(() -> YoutubeDL.getInstance().destroyProcessById(ID)).start(); }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Downloads",NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification() {
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Klip").setContentText("Download in progress").setOngoing(true).setContentIntent(pi).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || isRunning()) return START_NOT_STICKY;
        try {
            if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFICATION,notification());
        } catch(RuntimeException e) { status="Tidak bisa memulai unduhan latar belakang."; update(status,0); stopSelf(startId); return START_NOT_STICKY; }
        running=true; cancel=false; results=new ArrayList<>(); update("Menyiapkan unduhan...",0);
        worker.execute(() -> runDownload(intent,startId));
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId,int type) { cancelDownload(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }
    private String message(Exception e) { return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
    private void runDownload(Intent intent,int startId) {
        String url=intent.getStringExtra("url"), ref=intent.getStringExtra("ref"), agent=intent.getStringExtra("agent"), cookies=intent.getStringExtra("cookies");
        int selected=intent.getIntExtra("format",0);
        File dir=new File(getCacheDir(),"job-"+System.currentTimeMillis());
        ArrayList<SavedFile> saved=new ArrayList<>();
        try {
            if(url==null || !dir.mkdirs()) throw new IOException("Tidak bisa menyiapkan unduhan");
            YoutubeDLRequest request=new YoutubeDLRequest(url);
            request.addOption("--no-playlist"); request.addOption("--no-mtime");
            request.addOption("--concurrent-fragments","8"); request.addOption("--abort-on-unavailable-fragments");
            if(ref!=null && !ref.isEmpty()) request.addOption("--referer",ref);
            if(agent!=null && !agent.isEmpty()) request.addOption("--user-agent",agent);
            if(cookies!=null && !cookies.isEmpty()) {
                String host=Uri.parse(url).getHost(); File cookieFile=new File(dir,".cookies.txt");
                try(PrintWriter pw=new PrintWriter(new OutputStreamWriter(new FileOutputStream(cookieFile),java.nio.charset.StandardCharsets.UTF_8))) {
                    pw.println("# Netscape HTTP Cookie File");
                    for(String cookie:cookies.split(";")) { int split=cookie.indexOf('='); if(split<=0) continue; String name=cookie.substring(0,split).trim(), value=cookie.substring(split+1).trim(); if(name.contains("\t")||name.contains("\n")||value.contains("\t")||value.contains("\n")) continue; pw.println(host+"\tFALSE\t/\t"+(url.startsWith("https:")?"TRUE":"FALSE")+"\t0\t"+name+"\t"+value); }
                }
                request.addOption("--cookies",cookieFile.getAbsolutePath());
            }
            request.addOption("-o",new File(dir,"%(title).120B [%(id)s].%(ext)s").getAbsolutePath());
            if(selected<=3) { request.addOption("-S","res,vcodec:h264,acodec:aac"); request.addOption("--merge-output-format","mp4/mkv"); }
            if(selected==4) { request.addOption("-x"); request.addOption("--audio-format","mp3"); }
            else if(selected==5) request.addOption("-f","bestaudio/best");
            else if(selected>=1 && selected<=3) { int h=selected==1?1080:selected==2?720:480; request.addOption("-f","bestvideo[height<="+h+"]+bestaudio/best[height<="+h+"]/best"); }
            else request.addOption("-f","bestvideo+bestaudio/best");
            if(cancel) throw new IOException("Dibatalkan");
            YoutubeDL.getInstance().execute(request,ID,(p,eta,line) -> { update(cancel?"Membatalkan unduhan...":line,Math.max(0,Math.min(100,(int)(float)p))); return Unit.INSTANCE; });
            if(cancel) throw new IOException("Dibatalkan");
            File[] files=dir.listFiles(f -> f.isFile() && !f.getName().equals(".cookies.txt") && !f.getName().endsWith(".part") && !f.getName().endsWith(".ytdl"));
            if(files==null || files.length==0) throw new IOException("Tidak ada file yang dihasilkan");
            for(File file:files) { if(cancel) throw new IOException("Dibatalkan"); saved.add(save(file)); }
            results=saved; status="Selesai! File disimpan di Downloads/Klip."; percent=100;
        } catch(Exception e) {
            results=saved; status=!saved.isEmpty()?"Sebagian file disimpan; file lain gagal: "+message(e):cancel?"Unduhan dibatalkan.":"Unduhan gagal: "+message(e);
        } finally {
            File[] files=dir.listFiles(); if(files!=null) for(File f:files) f.delete(); dir.delete();
            running=false; update(status,percent); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId);
        }
    }
    private SavedFile save(File file) throws IOException {
        File external=getExternalFilesDir(null);
        if(external!=null && external.getUsableSpace()<file.length()) throw new IOException("Penyimpanan tidak cukup untuk menyimpan file.");
        ContentValues v=new ContentValues(); v.put(MediaStore.Downloads.DISPLAY_NAME,file.getName());
        int dot=file.getName().lastIndexOf('.'); String ext=dot>=0?file.getName().substring(dot+1).toLowerCase(Locale.ROOT):"";
        String mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext); if(mime==null) mime="application/octet-stream";
        v.put(MediaStore.Downloads.MIME_TYPE,mime); v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Klip"); v.put(MediaStore.Downloads.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v); if(uri==null) throw new IOException("Tidak bisa membuat file");
        try {
            try(InputStream in=new FileInputStream(file); OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null) throw new IOException("Tidak bisa membuka file"); byte[] buf=new byte[65536]; int n;
                while((n=in.read(buf))!=-1) { if(cancel) throw new IOException("Dibatalkan"); out.write(buf,0,n); }
            }
            ContentValues done=new ContentValues(); done.put(MediaStore.Downloads.IS_PENDING,0);
            if(getContentResolver().update(uri,done,null,null)!=1) throw new IOException("Tidak bisa menyelesaikan penyimpanan");
        } catch(IOException|RuntimeException e) { getContentResolver().delete(uri,null,null); throw e; }
        String name=file.getName();
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{MediaStore.Downloads.DISPLAY_NAME},null,null,null)) { if(c!=null && c.moveToFirst()) name=c.getString(0); } catch(RuntimeException ignored) { }
        return new SavedFile(uri,name,mime);
    }
}
