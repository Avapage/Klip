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
import java.util.ArrayList;
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
    private CheckBox playlist, wifiOnly;
    private Button appUpdate;
    private volatile boolean ready = false;
    private volatile boolean uiBusy = true;
    private volatile boolean downloading = false;
    private volatile boolean cancelRequested = false;
    private String browserUrl, browserReferer, browserUserAgent, browserCookies, browserPageUrl, browserPageCookies;
    private String estimatedUrl;
    private long estimatedBytes;
    private String formatsForUrl;
    private final ArrayList<String> discoveredFormats=new ArrayList<>();
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
                if (!DownloadService.isRunning()) show(getString(R.string.ui_000));
            } catch (Exception e) { show(getString(R.string.ui_001) + message(e)); }
            runOnUiThread(() -> {
                setBusy(false);
                if (ready) {
                    getWindow().getDecorView().postDelayed(this::checkAutoUpdateWhenIdle, 30000);
                    showCachedAppUpdate();
                    worker.execute(this::checkForAppUpdates);
                }
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
        downloading=active; if(!active) cancelRequested=false; else if(DownloadService.isCancelRequested()) cancelRequested=true; status.setText(message); progress.setProgress(value);
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
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP); root.addView(header);
        TextView badge = text("KLIP  /  "+version, 12, Color.rgb(125,231,204)); badge.setLetterSpacing(.12f);
        header.addView(badge, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView quote = text(Quotes.next(this), 11, Color.rgb(165,177,199));
        quote.setTypeface(getResources().getFont(R.font.tinos_regular)); quote.setGravity(Gravity.END | Gravity.TOP);
        quote.setMaxLines(4); quote.setEllipsize(android.text.TextUtils.TruncateAt.END);
        quote.setContentDescription(quote.getText());
        header.addView(quote, new LinearLayout.LayoutParams(0, -2, 1.15f));
        TextView title = text(getString(R.string.ui_headline), 36, white); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setPadding(0,dp(16),0,dp(12)); root.addView(title);
        TextView intro = text(getString(R.string.ui_003), 16, muted); intro.setPadding(0,0,0,dp(28)); root.addView(intro);
        TextView label = text(getString(R.string.ui_004), 12, muted); root.addView(label);
        link = new EditText(this); link.setSingleLine(false); link.setMinLines(2); link.setTextColor(white); link.setHintTextColor(muted); link.setHint(getString(R.string.ui_005)); link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); link.setBackground(rounded(Color.rgb(30,39,57),14)); link.setPadding(dp(16),dp(12),dp(16),dp(12)); root.addView(link);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.END); root.addView(row);
        Button paste = new Button(this); paste.setText(getString(R.string.ui_006)); row.addView(paste); paste.setOnClickListener(v -> { android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE); if(cb.hasPrimaryClip() && cb.getPrimaryClip()!=null) link.setText(cb.getPrimaryClip().getItemAt(0).coerceToText(this)); });
        inspect = new Button(this); inspect.setText(getString(R.string.ui_007)); row.addView(inspect); inspect.setOnClickListener(v -> inspectVideo());
        Button browser=new Button(this); browser.setText(getString(R.string.ui_008)); root.addView(browser); browser.setOnClickListener(v -> { Intent i=new Intent(this, BrowserActivity.class); String u=validUrl(); if(u!=null && u.equals(browserUrl) && browserPageUrl!=null) u=browserPageUrl; if(u!=null) i.putExtra("url",u); else link.setError(null); startActivityForResult(i, 100); });
        preview = text(getString(R.string.ui_009), 14, muted); preview.setPadding(dp(16),dp(14),dp(16),dp(14)); preview.setBackground(rounded(Color.rgb(25,34,49),14)); root.addView(preview);
        TextView fLabel = text(getString(R.string.ui_010), 12, muted); fLabel.setPadding(0,20,0,4); root.addView(fLabel);
        format = new Spinner(this); String[] formats={getString(R.string.ui_011), getString(R.string.ui_012), getString(R.string.ui_013), getString(R.string.ui_014), getString(R.string.ui_015), getString(R.string.ui_016)}; ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,formats); format.setAdapter(adapter); root.addView(format);
        link.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                if(formatsForUrl!=null && !formatsForUrl.equals(s.toString().trim())) {
                    formatsForUrl=null; discoveredFormats.clear(); format.setAdapter(adapter);
                }
            }
            public void afterTextChanged(android.text.Editable s) { }
        });
        playlist=new CheckBox(this); playlist.setText(getString(R.string.ui_017)); playlist.setTextColor(white); root.addView(playlist);
        wifiOnly=new CheckBox(this); wifiOnly.setText(getString(R.string.ui_018)); wifiOnly.setTextColor(white);
        wifiOnly.setChecked(getSharedPreferences("download_options",MODE_PRIVATE).getBoolean("wifi_only",false));
        wifiOnly.setOnCheckedChangeListener((button,checked) -> getSharedPreferences("download_options",MODE_PRIVATE).edit().putBoolean("wifi_only",checked).apply()); root.addView(wifiOnly);
        start = new Button(this); start.setText(getString(R.string.ui_019)); root.addView(start); start.setOnClickListener(v -> download());
        cancel = new Button(this); cancel.setText(getString(R.string.ui_021)); cancel.setVisibility(View.GONE); root.addView(cancel); cancel.setOnClickListener(v -> { cancelRequested=true; cancel.setEnabled(false); show(getString(R.string.ui_022)); DownloadService.cancelDownload(); });
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); root.addView(progress);
        status = text(getString(R.string.ui_023), 14, muted); status.setTextIsSelectable(true); status.setPadding(0,16,0,0); root.addView(status);
        resultList=new LinearLayout(this); resultList.setOrientation(LinearLayout.VERTICAL); resultList.setVisibility(View.GONE);
        resultList.setPadding(dp(16),dp(14),dp(16),dp(14)); resultList.setBackground(rounded(Color.rgb(25,34,49),14)); root.addView(resultList);
        Button history=new Button(this); history.setText(getString(R.string.ui_024)); root.addView(history); history.setOnClickListener(v -> showHistory());
        update = new Button(this); update.setText(getString(R.string.ui_025)); root.addView(update); update.setOnClickListener(v -> chooseUpdate());
        appUpdate=new Button(this); appUpdate.setText(getString(R.string.ui_026)); appUpdate.setVisibility(View.GONE); root.addView(appUpdate);
        TextView note = text(getString(R.string.ui_027), 13, muted); note.setPadding(0,dp(24),0,0); root.addView(note);
    }
    private void setBusy(boolean busy) { busy=busy || downloading || DownloadService.isRunning(); uiBusy=busy; start.setEnabled(ready && (!busy || downloading)); start.setText(downloading ? getString(R.string.ui_020) : getString(R.string.ui_019)); update.setEnabled(!busy && ready); inspect.setEnabled(!busy && ready); cancel.setVisibility(downloading ? View.VISIBLE : View.GONE); cancel.setEnabled(downloading && !cancelRequested); }
    private void show(String s) { runOnUiThread(() -> status.setText(s)); }
    private static int[] versionParts(String value) {
        if(value==null) return null;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(\\d+)\\.(\\d+)\\.(\\d+)(?:[\\s-]*r(\\d+))?").matcher(value);
        if(!m.find()) return null;
        try { return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),m.group(4)==null?0:Integer.parseInt(m.group(4))}; }
        catch(NumberFormatException e) { return null; }
    }
    private void showCachedAppUpdate() {
        String url=getSharedPreferences("app_updates",MODE_PRIVATE).getString("url","");
        String prefix="https://github.com/Avapage/Klip/releases/tag/";
        boolean newer=false;
        try {
            int[] latest=versionParts(url.startsWith(prefix) ? url.substring(prefix.length()) : "");
            int[] current=versionParts(getPackageManager().getPackageInfo(getPackageName(),0).versionName);
            if(latest!=null && current!=null) for(int i=0;i<4;i++) {
                if(latest[i]!=current[i]) { newer=latest[i]>current[i]; break; }
            }
        } catch(Exception ignored) { }
        if(newer) {
            appUpdate.setVisibility(View.VISIBLE);
            appUpdate.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
                catch(android.content.ActivityNotFoundException e) { Toast.makeText(this,getString(R.string.ui_028),Toast.LENGTH_LONG).show(); }
            });
        }
    }
    private void checkForAppUpdates() {
        android.content.SharedPreferences prefs=getSharedPreferences("app_updates",MODE_PRIVATE);
        if(System.currentTimeMillis()-prefs.getLong("last_check",0L)<24L*60*60*1000) return;
        java.net.HttpURLConnection connection=null;
        try {
            connection=(java.net.HttpURLConnection)new java.net.URL("https://api.github.com/repos/Avapage/Klip/releases/latest").openConnection();
            connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept","application/vnd.github+json");
            connection.setRequestProperty("User-Agent","Klip-Android");
            if(connection.getResponseCode()!=200) return;
            StringBuilder response=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))) {
                String line; while((line=reader.readLine())!=null && response.length()<200000) response.append(line);
            }
            org.json.JSONObject json=new org.json.JSONObject(response.toString());
            String url=json.optString("html_url",""); int[] latest=versionParts(json.optString("tag_name",""));
            int[] current=versionParts(getPackageManager().getPackageInfo(getPackageName(),0).versionName);
            if(latest==null || current==null || !url.startsWith("https://github.com/Avapage/Klip/releases/tag/")) return;
            boolean newer=false; for(int i=0;i<4;i++) { if(latest[i]!=current[i]) { newer=latest[i]>current[i]; break; } }
            prefs.edit().putLong("last_check",System.currentTimeMillis()).putString("url",newer?url:"").apply();
            runOnUiThread(() -> { if(!isFinishing()) { appUpdate.setVisibility(View.GONE); showCachedAppUpdate(); } });
        } catch(Exception ignored) { /* Retry on the next launch. */ }
        finally { if(connection!=null) connection.disconnect(); }
    }
    private String message(Exception e) { return e.getMessage()==null ? e.getClass().getSimpleName() : e.getMessage(); }
    private void chooseUpdate() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_025))
            .setItems(new String[]{getString(R.string.ui_029), getString(R.string.ui_030)}, (d, which) -> updateEngine(which==0 ? YoutubeDL.UpdateChannel._STABLE : YoutubeDL.UpdateChannel._NIGHTLY)).show();
    }
    private void updateEngine(YoutubeDL.UpdateChannel channel) {
        if (!ready) return;
        getSharedPreferences("engine",MODE_PRIVATE).edit().putBoolean("nightly",channel==YoutubeDL.UpdateChannel._NIGHTLY).apply();
        setBusy(true); show(getString(R.string.ui_031));
        worker.execute(() -> { try { Object result=YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(), channel); getSharedPreferences("engine",MODE_PRIVATE).edit().putLong("last_check",System.currentTimeMillis()).apply(); show(getString(R.string.ui_032) + result + "."); } catch(Exception e) { show(getString(R.string.ui_033) + message(e)); } finally { runOnUiThread(() -> setBusy(false)); } });
    }
    private String validUrl() {
        String url=link.getText().toString().trim(); Uri parsed=Uri.parse(url);
        if (!("https".equalsIgnoreCase(parsed.getScheme()) || "http".equalsIgnoreCase(parsed.getScheme())) || parsed.getHost()==null) { link.setError(getString(R.string.ui_034)); return null; }
        return url;
    }
    private void inspectVideo() {
        String url=validUrl(); if(url==null) return;
        setBusy(true); show(getString(R.string.ui_035));
        final String ref=url.equals(browserUrl)?browserReferer:null, agent=url.equals(browserUrl)?browserUserAgent:null;
        final String cookies=url.equals(browserUrl)?browserCookies:null, page=url.equals(browserUrl)?browserPageUrl:null, pageCookies=url.equals(browserUrl)?browserPageCookies:null;
        worker.execute(() -> { File cookieFile=null; try {
            YoutubeDLRequest request=new YoutubeDLRequest(url); request.addOption("--no-playlist");
            if(ref!=null && !ref.isEmpty()) request.addOption("--referer",ref);
            if(agent!=null && !agent.isEmpty()) request.addOption("--user-agent",agent);
            if((cookies!=null && !cookies.isEmpty()) || (pageCookies!=null && !pageCookies.isEmpty())) {
                cookieFile=File.createTempFile("klip-info-", ".cookies", getCacheDir());
                DownloadService.writeCookies(this,cookieFile,url,cookies,page,pageCookies); request.addOption("--cookies",cookieFile.getAbsolutePath());
            }
            VideoInfo info=YoutubeDL.getInstance().getInfo(request);
            final ArrayList<String> labels=new ArrayList<>(), selectors=new ArrayList<>();
            labels.add(getString(R.string.ui_011)); labels.add(getString(R.string.ui_012)); labels.add(getString(R.string.ui_013));
            labels.add(getString(R.string.ui_014)); labels.add(getString(R.string.ui_015)); labels.add(getString(R.string.ui_016));
            if(info.getFormats()!=null) for(com.yausername.youtubedl_android.mapper.VideoFormat f:info.getFormats()) {
                String id=f.getFormatId(); if(id==null || !id.matches("[A-Za-z0-9._-]+")) continue;
                String vc=f.getVcodec(), ac=f.getAcodec();
                boolean video=vc!=null && !vc.equals("none"), audio=ac!=null && !ac.equals("none");
                if(!video && !audio) continue;
                String selector=video && !audio ? id+"+bestaudio/"+id : id;
                String quality=f.getHeight()>0 ? f.getHeight()+"p" : video?"video":"audio";
                String ext=f.getExt()==null?"":f.getExt();
                labels.add(quality+" · "+ext+" · "+id+(video&&!audio?" + audio":""));
                selectors.add(selector);
            }
            runOnUiThread(() -> {
                if(!url.equals(link.getText().toString().trim())) return;
                discoveredFormats.clear(); discoveredFormats.addAll(selectors); formatsForUrl=url;
                format.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
            });
            long size=Math.max(info.getFileSize(), info.getFileSizeApproximate());
            runOnUiThread(() -> { estimatedUrl=url; estimatedBytes=size; });
            String name=info.getTitle()==null ? "Video" : info.getTitle();
            int seconds=info.getDuration(); String duration=seconds>0 ? String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60) : getString(R.string.ui_036);
            String uploader=info.getUploader()==null ? "" : "\n"+info.getUploader();
            runOnUiThread(() -> preview.setText(name+uploader+"\n"+duration)); show(getString(R.string.ui_037));
        } catch(Exception e) { show(message(e).contains("Unsupported URL") ? getString(R.string.ui_038) : getString(R.string.ui_039) + message(e) + getString(R.string.ui_try_direct)); } finally { if(cookieFile!=null) cookieFile.delete(); runOnUiThread(() -> setBusy(false)); } });
    }
    private void displayResults(java.util.List<DownloadService.SavedFile> files) {
        runOnUiThread(() -> {
            resultList.removeAllViews();
            TextView heading=text(getString(R.string.ui_041),12,Color.rgb(125,231,204)); resultList.addView(heading);
            for(DownloadService.SavedFile file:files) {
                TextView filename=text(file.name,15,Color.rgb(240,243,255)); filename.setPadding(0,dp(10),0,dp(4)); resultList.addView(filename);
                Button open=new Button(this); open.setText(file.mime.startsWith("video/")?getString(R.string.ui_042):file.mime.startsWith("audio/")?getString(R.string.ui_043):getString(R.string.ui_044));
                open.setOnClickListener(v -> {
                    Intent intent=new Intent(Intent.ACTION_VIEW); intent.setDataAndType(file.uri,file.mime);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(intent); }
                    catch(android.content.ActivityNotFoundException e) { Toast.makeText(this,getString(R.string.ui_045),Toast.LENGTH_LONG).show(); }
                    catch(SecurityException e) { Toast.makeText(this,getString(R.string.ui_046),Toast.LENGTH_LONG).show(); }
                });
                resultList.addView(open);
            }
            TextView location=text(getString(R.string.ui_047),12,Color.rgb(165,177,199)); location.setPadding(0,dp(6),0,0); resultList.addView(location);
            resultList.setVisibility(View.VISIBLE);
            screen.post(() -> screen.smoothScrollTo(0,resultList.getTop()));
        });
    }
    private void showHistory() {
        ArrayList<DownloadService.SavedFile> files=new ArrayList<>();
        Uri collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] columns={MediaStore.Downloads._ID,MediaStore.Downloads.DISPLAY_NAME,MediaStore.Downloads.MIME_TYPE};
        try (android.database.Cursor c=getContentResolver().query(collection,columns,
                MediaStore.Downloads.RELATIVE_PATH+"=? AND "+MediaStore.Downloads.OWNER_PACKAGE_NAME+"=? AND "+MediaStore.Downloads.IS_PENDING+"=0",
                new String[]{Environment.DIRECTORY_DOWNLOADS+"/Klip/",getPackageName()},
                MediaStore.Downloads.DATE_ADDED+" DESC, "+MediaStore.Downloads._ID+" DESC")) {
            if(c!=null) while(c.moveToNext() && files.size()<20) {
                Uri uri=android.content.ContentUris.withAppendedId(collection,c.getLong(0));
                String name=c.getString(1), mime=c.getString(2);
                files.add(new DownloadService.SavedFile(uri,name==null?"File":name,mime==null?"application/octet-stream":mime));
            }
        } catch (RuntimeException e) {
            Toast.makeText(this,getString(R.string.ui_048),Toast.LENGTH_LONG).show(); return;
        }
        if(files.isEmpty()) { new AlertDialog.Builder(this).setTitle(getString(R.string.ui_024)).setMessage(getString(R.string.ui_049)).setPositiveButton(getString(R.string.ui_050),null).show(); return; }
        String[] names=new String[files.size()]; for(int i=0;i<files.size();i++) names[i]=files.get(i).name;
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_024)).setItems(names,(dialog,which) -> {
            DownloadService.SavedFile file=files.get(which);
            Intent open=new Intent(Intent.ACTION_VIEW).setDataAndType(file.uri,file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(open); }
            catch(android.content.ActivityNotFoundException|SecurityException e) { Toast.makeText(this,getString(R.string.ui_046),Toast.LENGTH_LONG).show(); }
        }).setNegativeButton(getString(R.string.ui_050),null).show();
    }
    private void download() {
        String url=validUrl(); if(url==null) return;
        File target=getExternalFilesDir(null);
        long free=Math.min(getCacheDir().getUsableSpace(), target==null?Long.MAX_VALUE:target.getUsableSpace());
        if(url.equals(estimatedUrl) && estimatedBytes>0 && estimatedBytes>free/2) {
            new AlertDialog.Builder(this).setTitle(getString(R.string.ui_051))
                .setMessage(getString(R.string.ui_052))
                .setNegativeButton(getString(R.string.ui_053),null).setPositiveButton(getString(R.string.ui_054),(dialog,which) -> startDownload(url)).show();
            return;
        }
        startDownload(url);
    }
    private void startDownload(String url) {
        boolean enqueue=DownloadService.isRunning();
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
        Intent intent=new Intent(this,DownloadService.class);
        intent.putExtra("url",url); intent.putExtra("format",format.getSelectedItemPosition());
        intent.putExtra("playlist",playlist.isChecked()); intent.putExtra("wifi_only",wifiOnly.isChecked());
        int choice=format.getSelectedItemPosition()-6;
        if(url.equals(formatsForUrl) && choice>=0 && choice<discoveredFormats.size()) intent.putExtra("format_selector",discoveredFormats.get(choice));
        if(url.equals(browserUrl)) { intent.putExtra("ref",browserReferer); intent.putExtra("agent",browserUserAgent); intent.putExtra("cookies",browserCookies); intent.putExtra("page_url",browserPageUrl); intent.putExtra("page_cookies",browserPageCookies); }
        if(!enqueue) {
            downloading=true; cancelRequested=false; resultList.removeAllViews(); resultList.setVisibility(View.GONE);
            setBusy(true); progress.setProgress(0); show(getString(R.string.ui_055));
        }
        try { startForegroundService(intent); }
        catch(RuntimeException e) { if(!enqueue) downloading=false; show(getString(R.string.ui_056)+message(e)); setBusy(false); }
    }
}
