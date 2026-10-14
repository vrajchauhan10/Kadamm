package com.stride.run;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.http.HttpResponseCache;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Draws GPS coordinates over cached, on-demand OpenStreetMap tiles. */
public class RouteView extends View {
    private static final int TILE=256; private static final String UA="Kadamm/5.0 (Android)";
    private static final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private static final ConcurrentHashMap<String,Bitmap> tiles=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String,Boolean> pending=new ConcurrentHashMap<>();
    private static volatile boolean cacheInstalled=false;
    private String route=""; private int mapBackground=Color.rgb(31,37,34), routeColor=Color.rgb(181,234,86), routeGrid=Color.rgb(50,62,54); private boolean darkMode=true,hasLocation=false; private double currentLat,currentLon,targetLat,targetLon; private float currentAccuracy; private boolean followLocation=true;private android.animation.ValueAnimator locationAnimator;private String emptyMessage="Your route draws here once GPS locks on";
    public RouteView(Context c){super(c);installCache(c);} public RouteView(Context c,AttributeSet a){super(c,a);installCache(c);}
    private static synchronized void installCache(Context c){if(cacheInstalled)return;try{HttpResponseCache.install(new File(c.getCacheDir(),"osm-tile-cache"),25L*1024*1024);cacheInstalled=true;}catch(Exception ignored){}}
    public void setRoute(String r){if(r!=null&&!r.equals(route)){route=r;invalidate();}}
    public void setCurrentLocation(double lat,double lon,float accuracy){if(hasLocation&&lat==targetLat&&lon==targetLon&&accuracy==currentAccuracy)return;targetLat=lat;targetLon=lon;if(locationAnimator!=null)locationAnimator.cancel();double startLat=currentLat,startLon=currentLon;boolean animate=hasLocation&&Math.abs(lat-startLat)+Math.abs(lon-startLon)>0.00003;currentAccuracy=accuracy;hasLocation=true;float scale=1;try{scale=android.provider.Settings.Global.getFloat(getContext().getContentResolver(),"animator_duration_scale",1f);}catch(Exception ignored){}if(!animate||scale<=0){currentLat=lat;currentLon=lon;invalidate();return;}locationAnimator=android.animation.ValueAnimator.ofFloat(0,1);locationAnimator.setDuration(500);locationAnimator.addUpdateListener(v->{float f=(float)v.getAnimatedValue();currentLat=startLat+(lat-startLat)*f;currentLon=startLon+(lon-startLon)*f;invalidate();});locationAnimator.start();}
    static int pendingTiles(){return pending.size();}
    public void clearCurrentLocation(){if(!hasLocation)return;hasLocation=false;invalidate();}
    public void setEmptyMessage(String message){if(message!=null&&!message.equals(emptyMessage)){emptyMessage=message;invalidate();}}
    public void setFollowLocation(boolean follow){followLocation=follow;invalidate();}
    public void setTheme(int background,int grid,int track,boolean dark){mapBackground=background;routeGrid=grid;routeColor=track;darkMode=dark;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();c.drawColor(mapBackground);JSONArray points;try{points=new JSONArray(route);}catch(Exception e){points=new JSONArray();}if(points.length()==0&&!hasLocation){drawEmpty(c,w,h);return;}
        double minLat=90,maxLat=-90,minLon=180,maxLon=-180;for(int i=0;i<points.length();i++){JSONObject p=points.optJSONObject(i);if(p==null)continue;double lat=p.optDouble("lat"),lon=p.optDouble("lon");minLat=Math.min(minLat,lat);maxLat=Math.max(maxLat,lat);minLon=Math.min(minLon,lon);maxLon=Math.max(maxLon,lon);}if(hasLocation){minLat=Math.min(minLat,currentLat);maxLat=Math.max(maxLat,currentLat);minLon=Math.min(minLon,currentLon);maxLon=Math.max(maxLon,currentLon);}double centerLat=followLocation&&hasLocation?currentLat:(minLat+maxLat)/2,centerLon=followLocation&&hasLocation?currentLon:(minLon+maxLon)/2;int z=followLocation&&hasLocation?17:zoomFor(minLat,maxLat,minLon,maxLon,w,h);double world=Math.scalb((double)TILE,z);double centerX=tileX(centerLon,z)*TILE,centerY=tileY(centerLat,z)*TILE;double spanX=Math.max(2,(tileX(maxLon,z)-tileX(minLon,z))*TILE),spanY=Math.max(2,(tileY(minLat,z)-tileY(maxLat,z))*TILE);double scale=followLocation&&hasLocation?1.0:Math.min(2.5,Math.min(w*.78/spanX,h*.78/spanY));double left=w/2-centerX*scale,top=h/2-centerY*scale,tileScreen=TILE*scale;
        int x0=(int)Math.floor((-left)/tileScreen),x1=(int)Math.floor((w-left)/tileScreen),y0=(int)Math.floor((-top)/tileScreen),y1=(int)Math.floor((h-top)/tileScreen);int limit=(1<<z);for(int ty=y0;ty<=y1;ty++)for(int tx=x0;tx<=x1;tx++){if(tx<0||tx>=limit||ty<0||ty>=limit)continue;String key=z+"/"+tx+"/"+ty;Bitmap b=tiles.get(key);if(b==null){load(key);continue;}float x=(float)(left+tx*tileScreen),y=(float)(top+ty*tileScreen);if(darkMode)paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(new android.graphics.ColorMatrix(new float[]{.095f,.32f,.032f,0,4, .095f,.32f,.032f,0,4, .095f,.32f,.032f,0,6, 0,0,0,1,0})));c.drawBitmap(b,null,new RectF(x,y,x+(float)tileScreen,y+(float)tileScreen),paint);paint.setColorFilter(null);}
        Path line=new Path();for(int i=0;i<points.length();i++){JSONObject p=points.optJSONObject(i);if(p==null)continue;double x=(tileX(p.optDouble("lon"),z)*TILE-centerX)*scale+w/2,y=(tileY(p.optDouble("lat"),z)*TILE-centerY)*scale+h/2;if(i==0||p.optBoolean("break",false))line.moveTo((float)x,(float)y);else line.lineTo((float)x,(float)y);}paint.setColor(Color.argb(90,0,0,0));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(5.5f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);c.drawPath(line,paint);paint.setColor(routeColor);paint.setStrokeWidth(dp(3.5f));c.drawPath(line,paint);paint.setStyle(Paint.Style.FILL);JSONObject first=points.optJSONObject(0),last=points.optJSONObject(points.length()-1);if(first!=null)dot(c,(float)((tileX(first.optDouble("lon"),z)*TILE-centerX)*scale+w/2),(float)((tileY(first.optDouble("lat"),z)*TILE-centerY)*scale+h/2),Color.WHITE);if(last!=null&&points.length()>1)dot(c,(float)((tileX(last.optDouble("lon"),z)*TILE-centerX)*scale+w/2),(float)((tileY(last.optDouble("lat"),z)*TILE-centerY)*scale+h/2),routeColor);if(hasLocation){float x=(float)((tileX(currentLon,z)*TILE-centerX)*scale+w/2),y=(float)((tileY(currentLat,z)*TILE-centerY)*scale+h/2);double metersPerPixel=156543.03392*Math.cos(Math.toRadians(currentLat))/Math.scalb(1.0,z);float radius=(float)(currentAccuracy/Math.max(.1,metersPerPixel)*scale);paint.setColor(Color.argb(45,33,150,243));c.drawCircle(x,y,Math.max(dp(12),radius),paint);paint.setColor(Color.WHITE);c.drawCircle(x,y,dp(9),paint);paint.setColor(Color.rgb(30,136,229));c.drawCircle(x,y,dp(6),paint);}
    }
    private void load(String key){if(pending.putIfAbsent(key,Boolean.TRUE)!=null)return;new Thread(()->{HttpURLConnection con=null;try{URL url=new URL("https://tile.openstreetmap.org/"+key+".png");con=(HttpURLConnection)url.openConnection();con.setConnectTimeout(5000);con.setReadTimeout(7000);con.setRequestProperty("User-Agent",UA);con.setUseCaches(true);try(InputStream in=con.getInputStream()){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null)tiles.put(key,b);}}catch(Exception ignored){}finally{if(con!=null)con.disconnect();pending.remove(key);postInvalidate();}} ,"KadammMapTile").start();}
    private int zoomFor(double minLat,double maxLat,double minLon,double maxLon,float w,float h){double baseX=(tileX(maxLon,16)-tileX(minLon,16))*TILE,baseY=(tileY(minLat,16)-tileY(maxLat,16))*TILE;double ratio=Math.max((w*.70)/Math.max(1,baseX),(h*.70)/Math.max(1,baseY));int z=16+(int)Math.floor(Math.log(Math.max(.00001,ratio))/Math.log(2));return Math.max(3,Math.min(19,z));}
    private static double tileX(double lon,int z){return (lon+180)/360*Math.scalb(1.0,z);}private static double tileY(double lat,int z){double rad=Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,lat)));return (1-Math.log(Math.tan(rad)+1/Math.cos(rad))/Math.PI)/2*Math.scalb(1.0,z);}
    private void drawEmpty(Canvas c,float w,float h){
        paint.setStyle(Paint.Style.FILL);paint.setColor(mapBackground);c.drawRect(0,0,w,h,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(routeGrid);paint.setStrokeWidth(dp(1));
        float cx=w/2,cy=h/2-dp(12);for(int i=1;i<=3;i++)c.drawCircle(cx,cy,dp(16)*i,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(routeColor);c.drawCircle(cx,cy,dp(5),paint);
        paint.setColor(darkMode?Color.rgb(148,163,184):Color.rgb(80,91,105));paint.setTextSize(dp(12));paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(emptyMessage,cx,h-dp(18),paint);paint.setTextAlign(Paint.Align.LEFT);
    }
    private void dot(Canvas c,float x,float y,int color){paint.setColor(Color.WHITE);c.drawCircle(x,y,dp(5.5f),paint);paint.setColor(color);c.drawCircle(x,y,dp(3.5f),paint);}
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
}
