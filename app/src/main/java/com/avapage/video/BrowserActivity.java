package com.avapage.video;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.*;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import android.webkit.CookieManager;

/** Browsing is optional. Only HTTP(S) media URLs observed in this WebView are offered. */
public class BrowserActivity extends Activity {
    private WebView web;
    private EditText address;
    private Button media;
    private final LinkedHashSet<String> links = new LinkedHashSet<>();
    private final HashMap<String,String> referers = new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(13,17,31));
        LinearLayout bar=new LinearLayout(this); root.addView(bar);
        address=new EditText(this); address.setSingleLine(); address.setTextColor(Color.WHITE); address.setHintTextColor(Color.LTGRAY); address.setHint("https://alamat-situs.com");
        bar.addView(address,new LinearLayout.LayoutParams(0,-2,1));
        Button go=new Button(this); go.setText("Buka"); bar.addView(go);
        TextView hint=new TextView(this); hint.setTextColor(Color.LTGRAY); hint.setPadding(16,8,16,8); hint.setText("Putar video, lalu periksa media yang terdeteksi. Tidak semua situs menampilkan tautan langsung."); root.addView(hint);
        web=new WebView(this); root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        media=new Button(this); media.setText("Media terdeteksi: 0"); root.addView(media); setContentView(root);
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) { address.setText(url); synchronized(links) { links.clear(); referers.clear(); } media.setText("Media terdeteksi: 0"); }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req) { String s=req.getUrl().getScheme(); return !("https".equalsIgnoreCase(s) || "http".equalsIgnoreCase(s)); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req) { observe(req.getUrl(), req.getRequestHeaders().get("Referer")); return super.shouldInterceptRequest(view,req); }
        });
        web.setDownloadListener((url,userAgent,contentDisposition,mime,contentLength) -> {
            Uri u=Uri.parse(url); String m=mime==null?"":mime.toLowerCase(Locale.ROOT);
            if(m.startsWith("video/") || m.startsWith("audio/") || m.contains("mpegurl") || m.contains("dash+xml")) addCandidate(u,null); else observe(u,null);
        });
        go.setOnClickListener(v -> open(address.getText().toString().trim()));
        media.setOnClickListener(v -> scanPage());
        String initial=getIntent().getStringExtra("url"); if(initial!=null) open(initial);
    }
    private void open(String value) {
        if(!value.contains("://")) value="https://"+value;
        Uri uri=Uri.parse(value); String scheme=uri.getScheme();
        if(uri.getHost()==null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) { address.setError("Alamat tidak valid"); return; }
        synchronized(links) { links.clear(); referers.clear(); } media.setText("Media terdeteksi: 0"); web.loadUrl(uri.toString());
    }
    private void observe(Uri uri, String referer) {
        if(uri==null || uri.getScheme()==null || !(uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http"))) return;
        String path=uri.getPath(); if(path==null) return;
        String p=path.toLowerCase(Locale.ROOT);
        if(!(p.endsWith(".mp4") || p.endsWith(".webm") || p.endsWith(".mov") || p.endsWith(".m4v") || p.endsWith(".m3u8") || p.endsWith(".mpd") || p.endsWith(".mp3") || p.endsWith(".m4a"))) return;
        addCandidate(uri,referer);
    }
    private void addCandidate(Uri uri, String referer) {
        if(uri==null || uri.getScheme()==null || !(uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http"))) return;
        int count; synchronized(links) { links.add(uri.toString()); if(referer!=null && !referer.isEmpty()) referers.put(uri.toString(),referer); count=links.size(); }
        runOnUiThread(() -> media.setText("Media terdeteksi: "+count));
    }
    private void scanPage() {
        String js="(function(){let a=[];function add(u){if(typeof u==='string'&&/^https?:/i.test(u))a.push(u)};"
            +"document.querySelectorAll('video,video source,audio,audio source').forEach(e=>{add(e.currentSrc);add(e.src)});"
            +"try{if(typeof window.jwplayer==='function'){let p=window.jwplayer().getPlaylistItem();if(p){add(p.file);(p.sources||[]).forEach(s=>add(s.file))}}}catch(e){};"
            +"return JSON.stringify([...new Set(a)]);})()";
        web.evaluateJavascript(js, result -> {
            try { Object raw=new org.json.JSONTokener(result).nextValue(); if(raw instanceof String) { org.json.JSONArray a=new org.json.JSONArray((String)raw); for(int i=0;i<a.length();i++) addCandidate(Uri.parse(a.optString(i)),web.getUrl()); } }
            catch(Exception ignored) { }
            showMedia();
        });
    }
    private static int mediaPriority(String url) {
        String path=Uri.parse(url).getPath(); String name=path==null?"":path.toLowerCase(Locale.ROOT);
        if(name.endsWith("/master.m3u8")) return 0;
        if(name.endsWith(".m3u8")) return 1;
        return 2;
    }
    private void showMedia() {
        ArrayList<String> results; synchronized(links) { results=new ArrayList<>(links); }
        if(results.isEmpty()) { Toast.makeText(this,"Belum ada media terdeteksi. Putar videonya dahulu.",Toast.LENGTH_LONG).show(); return; }
        java.util.Collections.sort(results,(a,b) -> Integer.compare(mediaPriority(a),mediaPriority(b)));
        if(results.size()>20) results=new ArrayList<>(results.subList(0,20));
        final ArrayList<String> choices=results;
        CharSequence[] labels=new CharSequence[choices.size()];
        for(int i=0;i<choices.size();i++) {
            Uri u=Uri.parse(choices.get(i)); String p=u.getLastPathSegment();
            String prefix=mediaPriority(choices.get(i))==0 ? "★ Disarankan · " : mediaPriority(choices.get(i))==1 ? "Stream · " : "";
            labels[i]=prefix+(p==null?"Media":p)+"  ·  "+u.getHost();
        }
        new AlertDialog.Builder(this).setTitle("Pilih sumber video")
            .setItems(labels,(d,n) -> sendSelected(choices,n))
            .setNegativeButton("Tutup",null).show();
    }
    private void sendSelected(ArrayList<String> choices, int selectedIndex) {
        String selected=choices.get(selectedIndex); String ref; synchronized(links) { ref=referers.get(selected); }
        if(ref==null || ref.isEmpty()) ref=web.getUrl();
        ArrayList<String> refs=new ArrayList<>(), cookies=new ArrayList<>();
        for(String url:choices) {
            String itemRef; synchronized(links) { itemRef=referers.get(url); }
            refs.add(itemRef==null || itemRef.isEmpty() ? web.getUrl() : itemRef);
            String cookie=CookieManager.getInstance().getCookie(url);
            cookies.add(cookie==null?"":cookie);
        }
        Intent i=new Intent(this,MainActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP); i.putExtra("media_url",selected);
        i.putExtra("media_referer",ref); i.putExtra("media_user_agent",web.getSettings().getUserAgentString());
        i.putExtra("media_cookies",cookies.get(selectedIndex));
        i.putStringArrayListExtra("media_choices",choices); i.putStringArrayListExtra("media_refs",refs); i.putStringArrayListExtra("media_choice_cookies",cookies);
        startActivity(i); finish();
    }
    @Override public void onBackPressed() { if(web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
