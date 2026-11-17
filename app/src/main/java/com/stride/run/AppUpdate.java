package com.stride.run;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Updates for everyone, from Kadamm's GitHub releases (the same approach as Aalap). Kadamm asks GitHub for the
 * latest release at most every 6 hours, and only when the app is used or the 15-minute step job runs, so nothing
 * extra runs in the background. A newer version is offered on opening (once a day), on Profile, and as a
 * notification every 3 days until installed.
 *
 * Safe by construction: only HTTPS links into this repository's releases are opened, and the downloaded APK is
 * checked before Android's installer sees it: same app id, a higher version code, and signed with exactly the key
 * the installed Kadamm is signed with. Android checks the signature again and updates in place, so every session,
 * setting and account stays.
 */
final class AppUpdate {
    private AppUpdate() {}
    static final String REPO="vrajchauhan10/Kadamm";
    private static final String API="https://api.github.com/repos/"+REPO+"/releases/latest",PREF="app_update",CHANNEL="kadamm_updates";
    private static final long CHECK_EVERY=6*3600000L,ASK_EVERY=24*3600000L,REMIND_EVERY=3*24*3600000L,MAX_BYTES=100L*1024*1024;

    static final class Release { String version,apkUrl,apiUrl,page,notes; }
    interface Checked { void done(Release newer,boolean reached); }
    interface Progress { void at(int percent); }
    interface Downloaded { void done(File apk,String problem); }

    static String current(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "0";}}
    /** "9.10" is newer than "9.9"; a leading "v" and a "-suffix" are ignored. */
    static boolean isNewer(String remote,String local){
        String[] r=remote.trim().replaceFirst("^v","").split("-")[0].split("\\."),l=local.trim().replaceFirst("^v","").split("-")[0].split("\\.");
        for(int i=0;i<Math.max(r.length,l.length);i++){int a=i<r.length?num(r[i]):0,b=i<l.length?num(l[i]):0;if(a!=b)return a>b;}return false;
    }
    private static int num(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
    /** Only HTTPS links into this repository's releases (or its API's release assets) are trusted. */
    static boolean trusted(String url,boolean api){
        try{URI u=new URI(url);String path=u.getPath();if(path==null||!"https".equals(u.getScheme())||u.getUserInfo()!=null||u.getPort()!=-1||path.contains(".."))return false;
            return api?"api.github.com".equals(u.getHost())&&path.startsWith("/repos/"+REPO+"/releases/assets/"):"github.com".equals(u.getHost())&&path.startsWith("/"+REPO+"/releases/");}catch(Exception e){return false;}
    }
    /** GitHub's latest release, if it carries a "Kadamm-<version>.apk" we trust. */
    static Release parse(String json){
        try{JSONObject o=new JSONObject(json);if(o.optBoolean("draft")||o.optBoolean("prerelease"))return null;
            Release r=new Release();r.version=o.optString("tag_name").replaceFirst("^v","");if(r.version.isEmpty())return null;
            JSONArray as=o.optJSONArray("assets");if(as==null)return null;
            for(int i=0;i<as.length();i++){JSONObject a=as.getJSONObject(i);if(("Kadamm-"+r.version+".apk").equalsIgnoreCase(a.optString("name"))){r.apkUrl=a.optString("browser_download_url");r.apiUrl=a.optString("url");}}
            if(r.apkUrl==null||!trusted(r.apkUrl,false))return null;if(r.apiUrl!=null&&!trusted(r.apiUrl,true))r.apiUrl=null;
            r.page=o.optString("html_url");r.notes=o.optString("body","");return r;}catch(Exception e){return null;}
    }
    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    /** The newer release last found (no network), or null. */
    static Release known(Context c){String j=p(c).getString("json",null);Release r=j==null?null:parse(j);return r!=null&&isNewer(r.version,current(c))?r:null;}
    /** Whether GitHub has answered within the last check period. */
    static boolean fresh(Context c){return System.currentTimeMillis()-p(c).getLong("at",0)<CHECK_EVERY;}

    /** Asks GitHub (unless the remembered answer is recent and not forced); the callback runs on the main thread. */
    static void check(Context ctx,boolean force,Checked cb){
        Context c=ctx.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());
        if(!force&&fresh(c)){Release k=known(c);if(cb!=null)main.post(()->cb.done(k,true));return;}
        new Thread(()->{
            String body=null;
            try{HttpURLConnection h=(HttpURLConnection)new URL(API).openConnection();h.setConnectTimeout(8000);h.setReadTimeout(8000);h.setRequestProperty("Accept","application/vnd.github+json");h.setRequestProperty("User-Agent","Kadamm/"+current(c));
                int code=h.getResponseCode();if(code==200)body=read(h.getInputStream());else if(code==404)body="{}";h.disconnect();}catch(Exception ignored){}
            if(body!=null)p(c).edit().putString("json",body).putLong("at",System.currentTimeMillis()).apply();
            Release k=known(c);boolean reached=body!=null;if(cb!=null)main.post(()->cb.done(k,reached));
        },"KadammUpdateCheck").start();
    }
    private static String read(InputStream in)throws Exception{StringBuilder b=new StringBuilder();byte[] buf=new byte[16384];int n;while((n=in.read(buf))>0)b.append(new String(buf,0,n,"UTF-8"));in.close();return b.toString();}

    /** Whether to offer {@code version} on opening the app now: new versions at once, the same one once a day. */
    static boolean offerOnLaunch(Context c,String version){
        SharedPreferences s=p(c);long now=System.currentTimeMillis();
        if(version.equals(s.getString("offered",""))&&now-s.getLong("offeredAt",0)<ASK_EVERY)return false;
        s.edit().putString("offered",version).putLong("offeredAt",now).apply();return true;
    }

    /** From the 15-minute step job: asks GitHub at most daily and posts "Kadamm X is available" every 3 days until updated. */
    static void remindIfDue(Context ctx){
        Context c=ctx.getApplicationContext();SharedPreferences s=p(c);long now=System.currentTimeMillis();
        if(now-s.getLong("askedAt",0)<ASK_EVERY)return;s.edit().putLong("askedAt",now).apply();
        check(c,true,(r,ok)->{
            if(r==null)return;if(r.version.equals(s.getString("reminded",""))&&now-s.getLong("remindedAt",0)<REMIND_EVERY)return;
            s.edit().putString("reminded",r.version).putLong("remindedAt",now).apply();
            try{NotificationManager nm=c.getSystemService(NotificationManager.class);if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"App updates",NotificationManager.IMPORTANCE_DEFAULT));
                PendingIntent open=PendingIntent.getActivity(c,12,new Intent(c,MainActivity.class).putExtra("page","Profile").putExtra("offerUpdate",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
                nm.notify(7311,b.setSmallIcon(R.drawable.ic_stat_kadamm).setContentTitle("Kadamm "+r.version+" is available").setContentText("You have "+current(c)+". Tap to update; your sessions and settings stay.").setContentIntent(open).setAutoCancel(true).build());}catch(Exception ignored){}
        });
    }

    /** Downloads and checks the release; the callback runs on the main thread with the verified file or why not. */
    static void download(Context ctx,Release r,Progress progress,Downloaded done){
        Context c=ctx.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());
        new Thread(()->{
            File dir=new File(c.getCacheDir(),"update");dir.mkdirs();File[] old=dir.listFiles();if(old!=null)for(File f:old)f.delete();
            File apk=new File(dir,"Kadamm-"+r.version+".apk");
            // GitHub's direct link sometimes fails for a while; the same file through its API still downloads.
            boolean ok=fetch(r.apkUrl,apk,false,progress,main)||(r.apiUrl!=null&&fetch(r.apiUrl,apk,true,progress,main));
            String problem=ok?verify(c,apk):"The download didn't finish. Check the connection and try again.";
            if(problem!=null)apk.delete();File result=problem==null?apk:null;String why=problem;
            main.post(()->done.done(result,why));
        },"KadammUpdateDownload").start();
    }
    private static boolean fetch(String url,File out,boolean api,Progress progress,Handler main){
        try{HttpURLConnection h=(HttpURLConnection)new URL(url).openConnection();h.setConnectTimeout(10000);h.setReadTimeout(20000);h.setInstanceFollowRedirects(true);h.setRequestProperty("User-Agent","Kadamm");if(api)h.setRequestProperty("Accept","application/octet-stream");
            if(h.getResponseCode()!=200||!"https".equals(h.getURL().getProtocol()))return false;long total=h.getContentLengthLong(),done=0;int shown=-1;
            try(InputStream in=h.getInputStream();OutputStream o=new FileOutputStream(out)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0){o.write(buf,0,n);done+=n;if(done>MAX_BYTES)return false;int pct=total>0?(int)(done*100/total):0;if(pct!=shown){shown=pct;int v=pct;main.post(()->progress.at(v));}}}
            h.disconnect();return true;}catch(Exception e){return false;}
    }
    /** Null when the file is a genuine, newer Kadamm signed with this app's key; otherwise why not. */
    static String verify(Context c,File apk){
        try{PackageManager pm=c.getPackageManager();int flag=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            PackageInfo file=pm.getPackageArchiveInfo(apk.getPath(),flag),mine=pm.getPackageInfo(c.getPackageName(),flag);
            if(file==null)return "The downloaded file isn't a working app.";
            if(!c.getPackageName().equals(file.packageName))return "The downloaded file is a different app.";
            if(code(file)<=code(mine))return "The downloaded version isn't newer than this one.";
            Set<String> a=signers(file),b=signers(mine);if(b.isEmpty()||!a.equals(b))return "The download isn't signed with Kadamm's key, so it wasn't installed.";
            return null;}catch(Exception e){return "The download couldn't be checked.";}
    }
    private static long code(PackageInfo i){return Build.VERSION.SDK_INT>=28?i.getLongVersionCode():i.versionCode;}
    private static Set<String> signers(PackageInfo i){
        Set<String> out=new HashSet<>();Signature[] s;
        if(Build.VERSION.SDK_INT>=28){if(i.signingInfo==null)return out;s=i.signingInfo.hasMultipleSigners()?i.signingInfo.getApkContentsSigners():i.signingInfo.getSigningCertificateHistory();}else s=i.signatures;
        if(s!=null)for(Signature x:s)out.add(x.toCharsString());return out;
    }
    /** Opens Android's installer for a verified file. */
    static void install(Context c,File apk){
        Uri uri=androidx.core.content.FileProvider.getUriForFile(c,c.getPackageName()+".files",apk);
        c.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    /** Android's "Install unknown apps" switch for Kadamm, needed once. */
    static boolean canInstall(Context c){return Build.VERSION.SDK_INT<26||c.getPackageManager().canRequestPackageInstalls();}
    static void askInstallPermission(Context c){c.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    static String notesPreview(Release r){if(r.notes==null)return "";String n=r.notes.trim();String[] lines=n.split("\n");StringBuilder b=new StringBuilder();for(String l:lines){if(b.length()>360)break;if(l.trim().isEmpty())continue;b.append(l.trim().replaceFirst("^[-*]\\s*","• ")).append('\n');}return b.toString().trim();}
}
