package com.avapage.video;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.app.AlertDialog;
import android.widget.*;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import java.io.*;
import java.util.concurrent.Executors;
import kotlin.Unit;

public class MainActivity extends Activity {
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText link;
    private TextView status, preview;
    private ProgressBar progress;
    private LinearLayout resultList;
    private ScrollView screen;
    private Spinner format;
    private Button start, update, inspect, cancel;
    private volatile boolean ready = false;
    private volatile boolean downloading = false;
    private volatile boolean cancelRequested = false;
    private String browserUrl, browserReferer, browserUserAgent, browserCookies;
    private final String processId = "avapage-download";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13,17,31));
        getWindow().setNavigationBarColor(Color.rgb(13,17,31));
        buildUi();
        acceptIntent(getIntent());
        setBusy(true);
        worker.execute(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                FFmpeg.getInstance().init(getApplicationContext());
                android.content.SharedPreferences settings=getSharedPreferences("engine",MODE_PRIVATE);
                long now=System.currentTimeMillis();
                if(now-settings.getLong("last_check",0L)>24L*60*60*1000) {
                    settings.edit().putLong("last_check",now).apply();
                    show("Memeriksa pembaruan mesin yt-dlp...");
                    try { YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(), settings.getBoolean("nightly",false) ? YoutubeDL.UpdateChannel._NIGHTLY : YoutubeDL.UpdateChannel._STABLE); }
                    catch(Exception ignored) { }
                }
                ready = true;
                show("Siap. Tempel tautan video untuk mulai.");
            } catch (Exception e) { show("Gagal menyiapkan mesin: " + message(e)); }
            runOnUiThread(() -> setBusy(false));
        });
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent); }
    private void acceptIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(text);
                link.setText(m.find() ? m.group() : text);
            }
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            browserUrl=data.getStringExtra("media_url"); browserReferer=data.getStringExtra("media_referer");
            browserUserAgent=data.getStringExtra("media_user_agent"); browserCookies=data.getStringExtra("media_cookies");
            if (browserUrl != null) link.setText(browserUrl);
        }
    }
    private TextView text(String s, int size, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable rounded(int color, int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void buildUi() {
        int white = Color.rgb(240,243,255), muted = Color.rgb(165,177,199);
        ScrollView scroll = new ScrollView(this); screen=scroll; scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(13,17,31));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22),dp(28),dp(22),dp(36)); scroll.addView(root); setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(22)+insets.getSystemWindowInsetLeft(), dp(28)+insets.getSystemWindowInsetTop(),
                    dp(22)+insets.getSystemWindowInsetRight(), dp(36)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        String version;
        try { version=getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch(android.content.pm.PackageManager.NameNotFoundException e) { version=""; }
        TextView badge = text("KLIP  /  "+version, 12, Color.rgb(125,231,204)); badge.setLetterSpacing(.12f); root.addView(badge);
        TextView title = text("Simpan yang\npenting.", 36, white); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setPadding(0,dp(16),0,dp(12)); root.addView(title);
        TextView intro = text("Klip mengambil video dan audio langsung ke perangkatmu.", 16, muted); intro.setPadding(0,0,0,dp(28)); root.addView(intro);
        TextView label = text("TAUTAN VIDEO", 12, muted); root.addView(label);
        link = new EditText(this); link.setSingleLine(false); link.setMinLines(2); link.setTextColor(white); link.setHintTextColor(muted); link.setHint("Tempel tautan di sini..."); link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); link.setBackground(rounded(Color.rgb(30,39,57),14)); link.setPadding(dp(16),dp(12),dp(16),dp(12)); root.addView(link);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.END); root.addView(row);
        Button paste = new Button(this); paste.setText("Tempel"); row.addView(paste); paste.setOnClickListener(v -> { android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE); if(cb.hasPrimaryClip() && cb.getPrimaryClip()!=null) link.setText(cb.getPrimaryClip().getItemAt(0).coerceToText(this)); });
        inspect = new Button(this); inspect.setText("Cek info video"); row.addView(inspect); inspect.setOnClickListener(v -> inspectVideo());
        Button browser=new Button(this); browser.setText("Buka browser video"); root.addView(browser); browser.setOnClickListener(v -> { Intent i=new Intent(this, BrowserActivity.class); String u=validUrl(); if(u!=null) i.putExtra("url",u); else link.setError(null); startActivityForResult(i, 100); });
        preview = text("Info video akan muncul di sini setelah diperiksa.", 14, muted); preview.setPadding(dp(16),dp(14),dp(16),dp(14)); preview.setBackground(rounded(Color.rgb(25,34,49),14)); root.addView(preview);
        TextView fLabel = text("FORMAT", 12, muted); fLabel.setPadding(0,20,0,4); root.addView(fLabel);
        format = new Spinner(this); String[] formats={"Video · kualitas terbaik", "Video · maksimal 1080p", "Video · maksimal 720p", "Video · maksimal 480p", "Audio · MP3", "Audio · format asli"}; ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,formats); format.setAdapter(adapter); root.addView(format);
        start = new Button(this); start.setText("Mulai download"); root.addView(start); start.setOnClickListener(v -> download());
        cancel = new Button(this); cancel.setText("Batalkan unduhan"); cancel.setVisibility(View.GONE); root.addView(cancel); cancel.setOnClickListener(v -> { cancelRequested=true; cancel.setEnabled(false); show("Membatalkan unduhan..."); new Thread(() -> YoutubeDL.getInstance().destroyProcessById(processId)).start(); });
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); root.addView(progress);
        status = text("Menyiapkan aplikasi...", 14, muted); status.setTextIsSelectable(true); status.setPadding(0,16,0,0); root.addView(status);
        resultList=new LinearLayout(this); resultList.setOrientation(LinearLayout.VERTICAL); resultList.setVisibility(View.GONE);
        resultList.setPadding(dp(16),dp(14),dp(16),dp(14)); resultList.setBackground(rounded(Color.rgb(25,34,49),14)); root.addView(resultList);
        update = new Button(this); update.setText("Pembaruan mesin"); root.addView(update); update.setOnClickListener(v -> chooseUpdate());
        TextView note = text("Hasil disimpan di Downloads / Klip. Biarkan aplikasi terbuka selama unduhan.", 13, muted); note.setPadding(0,dp(24),0,0); root.addView(note);
    }
    private void setBusy(boolean busy) { start.setEnabled(!busy && ready); update.setEnabled(!busy && ready); inspect.setEnabled(!busy && ready); cancel.setVisibility(downloading ? View.VISIBLE : View.GONE); cancel.setEnabled(downloading && !cancelRequested); }
    private void show(String s) { runOnUiThread(() -> status.setText(s)); }
    private String message(Exception e) { return e.getMessage()==null ? e.getClass().getSimpleName() : e.getMessage(); }
    private void chooseUpdate() {
        new AlertDialog.Builder(this).setTitle("Pembaruan mesin")
            .setItems(new String[]{"Stable · direkomendasikan", "Nightly · perbaikan terbaru"}, (d, which) -> updateEngine(which==0 ? YoutubeDL.UpdateChannel._STABLE : YoutubeDL.UpdateChannel._NIGHTLY)).show();
    }
    private void updateEngine(YoutubeDL.UpdateChannel channel) {
        if (!ready) return;
        getSharedPreferences("engine",MODE_PRIVATE).edit().putBoolean("nightly",channel==YoutubeDL.UpdateChannel._NIGHTLY).apply();
        setBusy(true); show("Memeriksa pembaruan...");
        worker.execute(() -> { try { Object result=YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(), channel); show("Pembaruan selesai: " + result + "."); } catch(Exception e) { show("Update gagal: " + message(e)); } finally { runOnUiThread(() -> setBusy(false)); } });
    }
    private String validUrl() {
        String url=link.getText().toString().trim(); Uri parsed=Uri.parse(url);
        if (!("https".equalsIgnoreCase(parsed.getScheme()) || "http".equalsIgnoreCase(parsed.getScheme())) || parsed.getHost()==null) { link.setError("Masukkan tautan http atau https yang valid"); return null; }
        return url;
    }
    private void inspectVideo() {
        String url=validUrl(); if(url==null) return;
        setBusy(true); show("Membaca informasi video...");
        worker.execute(() -> { try {
            YoutubeDLRequest request=new YoutubeDLRequest(url); request.addOption("--no-playlist");
            VideoInfo info=YoutubeDL.getInstance().getInfo(request);
            String name=info.getTitle()==null ? "Video" : info.getTitle();
            int seconds=info.getDuration(); String duration=seconds>0 ? String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60) : "Durasi tidak tersedia";
            String uploader=info.getUploader()==null ? "" : "\n"+info.getUploader();
            runOnUiThread(() -> preview.setText(name+uploader+"\n"+duration)); show("Info video siap. Pilih format dan mulai download.");
        } catch(Exception e) { show(message(e).contains("Unsupported URL") ? "Situs ini belum dikenali mesin. Tekan Buka browser video, putar videonya, lalu tekan Media terdeteksi." : "Info belum tersedia: " + message(e) + "\nAnda masih bisa mencoba download langsung."); } finally { runOnUiThread(() -> setBusy(false)); } });
    }
    private static class SavedFile {
        final Uri uri; final String name; final String mime;
        SavedFile(Uri uri,String name,String mime) { this.uri=uri; this.name=name; this.mime=mime; }
    }
    private void displayResults(java.util.List<SavedFile> files) {
        runOnUiThread(() -> {
            resultList.removeAllViews();
            TextView heading=text("HASIL DOWNLOAD",12,Color.rgb(125,231,204)); resultList.addView(heading);
            for(SavedFile file:files) {
                TextView filename=text(file.name,15,Color.rgb(240,243,255)); filename.setPadding(0,dp(10),0,dp(4)); resultList.addView(filename);
                Button open=new Button(this); open.setText(file.mime.startsWith("video/")?"Putar video":file.mime.startsWith("audio/")?"Putar audio":"Buka file");
                open.setOnClickListener(v -> {
                    Intent intent=new Intent(Intent.ACTION_VIEW); intent.setDataAndType(file.uri,file.mime);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(intent); }
                    catch(android.content.ActivityNotFoundException e) { Toast.makeText(this,"Tidak ada aplikasi yang bisa membuka format ini. File tetap ada di Downloads/Klip.",Toast.LENGTH_LONG).show(); }
                    catch(SecurityException e) { Toast.makeText(this,"File tidak bisa dibuka. Cek kembali di Downloads/Klip.",Toast.LENGTH_LONG).show(); }
                });
                resultList.addView(open);
            }
            TextView location=text("Tersimpan di Downloads / Klip",12,Color.rgb(165,177,199)); location.setPadding(0,dp(6),0,0); resultList.addView(location);
            resultList.setVisibility(View.VISIBLE);
            screen.post(() -> screen.smoothScrollTo(0,resultList.getTop()));
        });
    }
    private void download() {
        String url=validUrl(); if(url==null) return;
        final int selected=format.getSelectedItemPosition();
        final String contextReferer=url.equals(browserUrl)?browserReferer:null;
        final String contextAgent=url.equals(browserUrl)?browserUserAgent:null;
        final String contextCookies=url.equals(browserUrl)?browserCookies:null;
        downloading=true; cancelRequested=false;
        resultList.removeAllViews(); resultList.setVisibility(View.GONE);
        setBusy(true); progress.setProgress(0); show("Menyiapkan unduhan...");
        worker.execute(() -> {
            File dir=new File(getCacheDir(), "job-"+System.currentTimeMillis());
            java.util.ArrayList<SavedFile> saved=new java.util.ArrayList<>();
            try {
                if(!dir.mkdirs()) throw new IOException("Tidak bisa menyiapkan penyimpanan sementara");
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("--no-playlist"); request.addOption("--no-mtime");
                request.addOption("--concurrent-fragments","8");
                request.addOption("--abort-on-unavailable-fragments");
                if(contextReferer!=null && !contextReferer.isEmpty()) request.addOption("--referer",contextReferer);
                if(contextAgent!=null && !contextAgent.isEmpty()) request.addOption("--user-agent",contextAgent);
                if(contextCookies!=null && !contextCookies.isEmpty()) {
                    String host=Uri.parse(url).getHost(); File cookieFile=new File(dir,".cookies.txt");
                    try (java.io.PrintWriter pw=new java.io.PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(cookieFile),java.nio.charset.StandardCharsets.UTF_8))) {
                        pw.println("# Netscape HTTP Cookie File");
                        for(String cookie:contextCookies.split(";")) { int split=cookie.indexOf('='); if(split<=0) continue; String name=cookie.substring(0,split).trim(); String value=cookie.substring(split+1).trim(); if(name.contains("\t") || name.contains("\n") || value.contains("\t") || value.contains("\n")) continue; pw.println(host+"\tFALSE\t/\t"+(url.startsWith("https:")?"TRUE":"FALSE")+"\t0\t"+name+"\t"+value); }
                    }
                    request.addOption("--cookies",cookieFile.getAbsolutePath());
                }
                request.addOption("-o", new File(dir, "%(title).120B [%(id)s].%(ext)s").getAbsolutePath());
                if(selected==4) { request.addOption("-x"); request.addOption("--audio-format", "mp3"); }
                else if(selected==5) request.addOption("-f", "bestaudio/best");
                else if(selected>=1 && selected<=3) { int height=selected==1?1080:selected==2?720:480; request.addOption("-f", "bestvideo[height<="+height+"]+bestaudio/best[height<="+height+"]/best"); }
                else request.addOption("-f", "bestvideo+bestaudio/best");
                if(cancelRequested) throw new IOException("Dibatalkan");
                YoutubeDL.getInstance().execute(request, processId, (p, eta, line) -> { if(cancelRequested) new Thread(() -> YoutubeDL.getInstance().destroyProcessById(processId)).start(); runOnUiThread(() -> { progress.setProgress(Math.max(0,Math.min(100,(int)(float)p))); status.setText(cancelRequested ? "Membatalkan unduhan..." : line); }); return Unit.INSTANCE; });
                if(cancelRequested) throw new IOException("Dibatalkan");
                File[] files=dir.listFiles(f -> f.isFile() && !f.getName().equals(".cookies.txt") && !f.getName().endsWith(".part") && !f.getName().endsWith(".ytdl"));
                if(files==null || files.length==0) throw new IOException("Tidak ada file yang dihasilkan");
                for(File file:files) saved.add(saveToDownloads(file));
                show("Selesai! File disimpan di Downloads/Klip.");
                displayResults(saved);
                runOnUiThread(() -> progress.setProgress(100));
            } catch(Exception e) { if(!saved.isEmpty()) displayResults(saved); show(!saved.isEmpty() ? "Sebagian file sudah tersimpan. File lain gagal disimpan: " + message(e) : cancelRequested ? "Unduhan dibatalkan." : message(e).contains("Unsupported URL") ? "Situs ini belum dikenali mesin. Tekan Buka browser video, putar videonya, lalu tekan Media terdeteksi." : "Unduhan gagal: " + message(e) + "\nCoba update mesin atau gunakan tautan lain."); }
            finally { File[] files=dir.listFiles(); if(files!=null) for(File f:files) f.delete(); dir.delete(); downloading=false; runOnUiThread(() -> setBusy(false)); }
        });
    }
    private SavedFile saveToDownloads(File file) throws IOException {
        File external = getExternalFilesDir(null);
        if (external != null && external.getUsableSpace() < file.length())
            throw new IOException("Penyimpanan tidak cukup untuk menyimpan file. Kosongkan ruang lalu coba lagi.");
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME,file.getName());
        int dot=file.getName().lastIndexOf('.');
        String ext=dot>=0?file.getName().substring(dot+1).toLowerCase(java.util.Locale.ROOT):"";
        String mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if(mime==null) mime="application/octet-stream";
        values.put(MediaStore.Downloads.MIME_TYPE,mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Klip");
        values.put(MediaStore.Downloads.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(uri==null) throw new IOException("Tidak bisa membuat file di Downloads");
        try {
            try (InputStream in=new FileInputStream(file); OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null) throw new IOException("Tidak bisa membuka file tujuan");
                byte[] buf=new byte[65536]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
            }
            ContentValues done=new ContentValues(); done.put(MediaStore.Downloads.IS_PENDING,0);
            if(getContentResolver().update(uri,done,null,null)!=1) throw new IOException("Tidak bisa menyelesaikan penyimpanan");
        } catch (IOException | RuntimeException e) { getContentResolver().delete(uri,null,null); throw e; }
        String displayName=file.getName();
        try (android.database.Cursor cursor=getContentResolver().query(uri,new String[]{MediaStore.Downloads.DISPLAY_NAME},null,null,null)) {
            if(cursor!=null && cursor.moveToFirst()) displayName=cursor.getString(0);
        } catch(RuntimeException ignored) { }
        return new SavedFile(uri,displayName,mime);
    }
}
