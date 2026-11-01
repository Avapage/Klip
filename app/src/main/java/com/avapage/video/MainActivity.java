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
    private volatile boolean uiBusy = true;
    private volatile boolean downloading = false;
    private volatile boolean cancelRequested = false;
    private String browserUrl, browserReferer, browserUserAgent, browserCookies, browserPageUrl, browserPageCookies;
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
                cleanOldJobs();
                YoutubeDL.getInstance().init(getApplicationContext());
                FFmpeg.getInstance().init(getApplicationContext());
                ready = true;
                if (!DownloadService.isRunning()) show("Siap. Tempel tautan video untuk mulai.");
            } catch (Exception e) { show("Gagal menyiapkan mesin: " + message(e)); }
            runOnUiThread(() -> {
                setBusy(false);
                if (ready) getWindow().getDecorView().postDelayed(this::checkAutoUpdateWhenIdle, 30000);
            });
        });
    }
    private void checkAutoUpdateWhenIdle() {
        android.content.SharedPreferences prefs=getSharedPreferences("engine", MODE_PRIVATE);
        if (!isFinishing() && ready && !uiBusy && !DownloadService.isRunning()
                && System.currentTimeMillis()-prefs.getLong("last_check",0L)>24L*60*60*1000) {
            updateEngine(prefs.getBoolean("nightly",false) ? YoutubeDL.UpdateChannel._NIGHTLY : YoutubeDL.UpdateChannel._STABLE);
        }
    }
    private void cleanOldJobs() {
        if (DownloadService.isRunning()) return;
        File[] jobs=getCacheDir().listFiles(f -> f.isDirectory() && f.getName().startsWith("job-")
                && System.currentTimeMillis()-f.lastModified()>24L*60*60*1000);
        if(jobs!=null) for(File job:jobs) { File[] parts=job.listFiles(); if(parts!=null) for(File part:parts) if(part.isFile()) part.delete(); job.delete(); }
        File[] info=getCacheDir().listFiles(f -> f.isFile() && f.getName().startsWith("klip-info-") && f.getName().endsWith(".cookies")
                && System.currentTimeMillis()-f.lastModified()>24L*60*60*1000);
        if(info!=null) for(File file:info) file.delete();
    }
    private final DownloadService.Listener downloadListener=(active, message, value, files) -> {
        downloading=active; cancelRequested=false; status.setText(message); progress.setProgress(value);
        setBusy(active); if(!files.isEmpty()) displayResults(files);
    };
    @Override protected void onStart() { super.onStart(); DownloadService.attach(downloadListener); }
    @Override protected void onStop() { DownloadService.detach(downloadListener); super.onStop(); }
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
            browserPageUrl=data.getStringExtra("page_url"); browserPageCookies=data.getStringExtra("page_cookies");
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
        Button browser=new Button(this); browser.setText("Buka browser video"); root.addView(browser); browser.setOnClickListener(v -> { Intent i=new Intent(this, BrowserActivity.class); String u=validUrl(); if(u!=null && u.equals(browserUrl) && browserPageUrl!=null) u=browserPageUrl; if(u!=null) i.putExtra("url",u); else link.setError(null); startActivityForResult(i, 100); });
        preview = text("Info video akan muncul di sini setelah diperiksa.", 14, muted); preview.setPadding(dp(16),dp(14),dp(16),dp(14)); preview.setBackground(rounded(Color.rgb(25,34,49),14)); root.addView(preview);
        TextView fLabel = text("FORMAT", 12, muted); fLabel.setPadding(0,20,0,4); root.addView(fLabel);
        format = new Spinner(this); String[] formats={"Video · kualitas terbaik", "Video · maksimal 1080p", "Video · maksimal 720p", "Video · maksimal 480p", "Audio · MP3", "Audio · format asli"}; ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,formats); format.setAdapter(adapter); root.addView(format);
        start = new Button(this); start.setText("Mulai download"); root.addView(start); start.setOnClickListener(v -> download());
        cancel = new Button(this); cancel.setText("Batalkan unduhan"); cancel.setVisibility(View.GONE); root.addView(cancel); cancel.setOnClickListener(v -> { cancelRequested=true; cancel.setEnabled(false); show("Membatalkan unduhan..."); DownloadService.cancelDownload(); });
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); root.addView(progress);
        status = text("Menyiapkan aplikasi...", 14, muted); status.setTextIsSelectable(true); status.setPadding(0,16,0,0); root.addView(status);
        resultList=new LinearLayout(this); resultList.setOrientation(LinearLayout.VERTICAL); resultList.setVisibility(View.GONE);
        resultList.setPadding(dp(16),dp(14),dp(16),dp(14)); resultList.setBackground(rounded(Color.rgb(25,34,49),14)); root.addView(resultList);
        update = new Button(this); update.setText("Pembaruan mesin"); root.addView(update); update.setOnClickListener(v -> chooseUpdate());
        TextView note = text("Hasil disimpan di Downloads / Klip. Unduhan dapat berlanjut di latar belakang.", 13, muted); note.setPadding(0,dp(24),0,0); root.addView(note);
    }
    private void setBusy(boolean busy) { busy=busy || downloading || DownloadService.isRunning(); uiBusy=busy; start.setEnabled(!busy && ready); update.setEnabled(!busy && ready); inspect.setEnabled(!busy && ready); cancel.setVisibility(downloading ? View.VISIBLE : View.GONE); cancel.setEnabled(downloading && !cancelRequested); }
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
        worker.execute(() -> { try { Object result=YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(), channel); getSharedPreferences("engine",MODE_PRIVATE).edit().putLong("last_check",System.currentTimeMillis()).apply(); show("Pembaruan selesai: " + result + "."); } catch(Exception e) { show("Update gagal: " + message(e)); } finally { runOnUiThread(() -> setBusy(false)); } });
    }
    private String validUrl() {
        String url=link.getText().toString().trim(); Uri parsed=Uri.parse(url);
        if (!("https".equalsIgnoreCase(parsed.getScheme()) || "http".equalsIgnoreCase(parsed.getScheme())) || parsed.getHost()==null) { link.setError("Masukkan tautan http atau https yang valid"); return null; }
        return url;
    }
    private void inspectVideo() {
        String url=validUrl(); if(url==null) return;
        setBusy(true); show("Membaca informasi video...");
        final String ref=url.equals(browserUrl)?browserReferer:null, agent=url.equals(browserUrl)?browserUserAgent:null;
        final String cookies=url.equals(browserUrl)?browserCookies:null, page=url.equals(browserUrl)?browserPageUrl:null, pageCookies=url.equals(browserUrl)?browserPageCookies:null;
        worker.execute(() -> { File cookieFile=null; try {
            YoutubeDLRequest request=new YoutubeDLRequest(url); request.addOption("--no-playlist");
            if(ref!=null && !ref.isEmpty()) request.addOption("--referer",ref);
            if(agent!=null && !agent.isEmpty()) request.addOption("--user-agent",agent);
            if((cookies!=null && !cookies.isEmpty()) || (pageCookies!=null && !pageCookies.isEmpty())) {
                cookieFile=File.createTempFile("klip-info-", ".cookies", getCacheDir());
                DownloadService.writeCookies(cookieFile,url,cookies,page,pageCookies); request.addOption("--cookies",cookieFile.getAbsolutePath());
            }
            VideoInfo info=YoutubeDL.getInstance().getInfo(request);
            String name=info.getTitle()==null ? "Video" : info.getTitle();
            int seconds=info.getDuration(); String duration=seconds>0 ? String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60) : "Durasi tidak tersedia";
            String uploader=info.getUploader()==null ? "" : "\n"+info.getUploader();
            runOnUiThread(() -> preview.setText(name+uploader+"\n"+duration)); show("Info video siap. Pilih format dan mulai download.");
        } catch(Exception e) { show(message(e).contains("Unsupported URL") ? "Situs ini belum dikenali mesin. Tekan Buka browser video, putar videonya, lalu tekan Media terdeteksi." : "Info belum tersedia: " + message(e) + "\nAnda masih bisa mencoba download langsung."); } finally { if(cookieFile!=null) cookieFile.delete(); runOnUiThread(() -> setBusy(false)); } });
    }
    private void displayResults(java.util.List<DownloadService.SavedFile> files) {
        runOnUiThread(() -> {
            resultList.removeAllViews();
            TextView heading=text("HASIL DOWNLOAD",12,Color.rgb(125,231,204)); resultList.addView(heading);
            for(DownloadService.SavedFile file:files) {
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
        String url=validUrl(); if(url==null || DownloadService.isRunning()) return;
        Intent intent=new Intent(this,DownloadService.class);
        intent.putExtra("url",url); intent.putExtra("format",format.getSelectedItemPosition());
        if(url.equals(browserUrl)) { intent.putExtra("ref",browserReferer); intent.putExtra("agent",browserUserAgent); intent.putExtra("cookies",browserCookies); intent.putExtra("page_url",browserPageUrl); intent.putExtra("page_cookies",browserPageCookies); }
        downloading=true; cancelRequested=false; resultList.removeAllViews(); resultList.setVisibility(View.GONE);
        setBusy(true); progress.setProgress(0); show("Menyiapkan unduhan...");
        try { startForegroundService(intent); }
        catch(RuntimeException e) { downloading=false; show("Tidak bisa memulai unduhan: "+message(e)); setBusy(false); }
    }
}
