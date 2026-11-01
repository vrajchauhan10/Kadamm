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
    private static final android.graphics.ColorMatrixColorFilter DARK_MAP=new android.graphics.ColorMatrixColorFilter(new float[]{-.19f,-.62f,-.06f,0,232, -.19f,-.62f,-.06f,0,234, -.19f,-.62f,-.06f,0,244, 0,0,0,1,0});
    /** Light map: colours toned down so the blue and red route lines carry the picture. */
    private static final android.graphics.ColorMatrixColorFilter LIGHT_MAP;
    static{android.graphics.ColorMatrix m=new android.graphics.ColorMatrix();m.setSaturation(.3f);android.graphics.ColorMatrix lift=new android.graphics.ColorMatrix(new float[]{.94f,0,0,0,14, 0,.94f,0,0,14, 0,0,.94f,0,16, 0,0,0,1,0});m.postConcat(lift);LIGHT_MAP=new android.graphics.ColorMatrixColorFilter(m);}
    private static final ConcurrentHashMap<String,Boolean> pending=new ConcurrentHashMap<>();
    private static volatile boolean cacheInstalled=false;
    private String route=""; private int mapBackground=Color.rgb(31,37,34), routeColor=Color.rgb(181,234,86), routeGrid=Color.rgb(50,62,54); private boolean darkMode=true,hasLocation=false; private double currentLat,currentLon,targetLat,targetLon; private float currentAccuracy; private boolean followLocation=true;private android.animation.ValueAnimator locationAnimator;private String emptyMessage="Your route draws here once GPS locks on";
    public RouteView(Context c){super(c);installCache(c);} public RouteView(Context c,AttributeSet a){super(c,a);installCache(c);}
    private static synchronized void installCache(Context c){if(cacheInstalled)return;try{HttpResponseCache.install(new File(c.getCacheDir(),"osm-tile-cache"),25L*1024*1024);cacheInstalled=true;}catch(Exception ignored){}}
    public void setRoute(String r){if(r!=null&&!r.equals(route)){route=r;invalidate();}}
    public void setCurrentLocation(double lat,double lon,float accuracy){if(hasLocation&&lat==targetLat&&lon==targetLon&&accuracy==currentAccuracy)return;targetLat=lat;targetLon=lon;if(locationAnimator!=null)locationAnimator.cancel();double startLat=currentLat,startLon=currentLon;boolean animate=hasLocation&&Math.abs(lat-startLat)+Math.abs(lon-startLon)>0.00003;currentAccuracy=accuracy;hasLocation=true;float scale=1;try{scale=android.provider.Settings.Global.getFloat(getContext().getContentResolver(),"animator_duration_scale",1f);}catch(Exception ignored){}if(!animate||scale<=0){currentLat=lat;currentLon=lon;invalidate();return;}locationAnimator=android.animation.ValueAnimator.ofFloat(0,1);locationAnimator.setDuration(500);locationAnimator.addUpdateListener(v->{float f=(float)v.getAnimatedValue();currentLat=startLat+(lat-startLat)*f;currentLon=startLon+(lon-startLon)*f;invalidate();});locationAnimator.start();}
    static int pendingTiles(){return pending.size();}
    /** True while a finger is on an interactive map, so the page swipe and scroll leave it alone. */
    static volatile boolean gestureActive=false;
    private boolean interactive=false,manual=false,moved=false;private float downX,downY;private double mX,mY,mZoom,lastX=.5,lastY=.5,lastZf=15;
    private android.view.GestureDetector gestures;private android.view.ScaleGestureDetector pinch;
    private int runColor=Color.rgb(234,67,53);
    void setRunColor(int color){runColor=color;invalidate();}
    private float lineScale=1f;private boolean markers=true;
    /** Whole-kilometre milestones; off for tiny previews. */
    void setMarkers(boolean on){markers=on;invalidate();}
    /**
     * One small milestone at each whole kilometre of the recorded distance: a ringed dot on the line, and on large or
     * interactive maps a "1 km" pill beside it. No direction arrows and no sub-kilometre labels, so the line stays clean.
     */
    private void drawMarkers(Canvas c,int z,double centerX,double centerY,double scale,float w,float h){
        // Measured on the raw track: the drawn line is simplified, which drops repeated laps along one street.
        java.util.List<double[]> pts=new ArrayList<>();
        try{JSONArray a=new JSONArray(route);for(int i=0;i<a.length();i++){JSONObject q=a.optJSONObject(i);if(q!=null)pts.add(new double[]{q.optDouble("lat"),q.optDouble("lon"),q.optBoolean("break",false)?1:0,"r".equals(q.optString("t"))?1:0});}}catch(Exception ignored){}
        if(pts.size()<2)return;
        boolean labels=interactive||lineScale>1.2f;double run=0,nextMark=1000;java.util.List<RectF> placed=new ArrayList<>();
        android.graphics.Paint tp=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);tp.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));tp.setTextSize(dp(10)*lineScale);
        for(int i=1;i<pts.size();i++){
            double[] a=pts.get(i-1),b=pts.get(i);if(b[2]>0)continue;
            double seg=Insights.metres(a[0],a[1],b[0],b[1]);
            while(run+seg>=nextMark){
                double f=(nextMark-run)/Math.max(.001,seg);
                float ax=(float)((tileX(a[1],z)*TILE-centerX)*scale+w/2),ay=(float)((tileY(a[0],z)*TILE-centerY)*scale+h/2),bx=(float)((tileX(b[1],z)*TILE-centerX)*scale+w/2),by=(float)((tileY(b[0],z)*TILE-centerY)*scale+h/2);
                float mx=(float)(ax+(bx-ax)*f),my=(float)(ay+(by-ay)*f);int col=b[3]>0?runColor:routeColor;
                paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawCircle(mx,my,dp(5)*lineScale,paint);paint.setColor(col);c.drawCircle(mx,my,dp(3)*lineScale,paint);
                if(labels){
                    String label=(int)(nextMark/1000)+" km";float tw=tp.measureText(label),ph=dp(18)*lineScale,pw=tw+dp(14)*lineScale,lx=mx+dp(8)*lineScale,ly=my-ph-dp(4)*lineScale;
                    // Laps put several kilometres on one spot: try the other side, else keep just the dot.
                    RectF box=new RectF(lx,ly,lx+pw,ly+ph);if(overlaps(box,placed)){lx=mx-dp(8)*lineScale-pw;box=new RectF(lx,ly+ph+dp(8)*lineScale,lx+pw,ly+ph*2+dp(8)*lineScale);ly=box.top;}
                    if(!overlaps(box,placed)){placed.add(box);
                    paint.setColor(darkMode?Color.argb(235,22,22,25):Color.argb(240,255,255,255));c.drawRoundRect(new RectF(lx,ly,lx+pw,ly+ph),ph/2,ph/2,paint);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(Color.argb(160,Color.red(col),Color.green(col),Color.blue(col)));c.drawRoundRect(new RectF(lx,ly,lx+pw,ly+ph),ph/2,ph/2,paint);paint.setStyle(Paint.Style.FILL);
                    tp.setColor(darkMode?Color.rgb(245,245,247):Color.rgb(11,11,13));c.drawText(label,lx+dp(7)*lineScale,ly+ph/2+tp.getTextSize()*.35f,tp);}
                }
                nextMark+=1000;
            }
            run+=seg;
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private boolean overlaps(RectF r,java.util.List<RectF> others){for(RectF o:others)if(RectF.intersects(r,o))return true;return false;}
    /** Thicker lines and markers, for large renders such as the share image. */
    void setLineScale(float scale){lineScale=scale;invalidate();}
    /** Lets the user drag, pinch and double-tap to explore. Small previews stay static. */
    void setInteractive(boolean on){
        interactive=on;if(!on||gestures!=null)return;
        gestures=new android.view.GestureDetector(getContext(),new android.view.GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(android.view.MotionEvent e){return true;}
            @Override public boolean onScroll(android.view.MotionEvent a,android.view.MotionEvent b,float dx,float dy){enterManual();double world=TILE*Math.pow(2,mZoom);mX=clamp(mX+dx/world);mY=clamp(mY+dy/world);invalidate();return true;}
            @Override public boolean onDoubleTap(android.view.MotionEvent e){zoomAround(e.getX(),e.getY(),1);return true;}
        });
        pinch=new android.view.ScaleGestureDetector(getContext(),new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(android.view.ScaleGestureDetector d){zoomAround(d.getFocusX(),d.getFocusY(),Math.log(d.getScaleFactor())/Math.log(2));return true;}
        });
    }
    private static double clamp(double v){return Math.max(0,Math.min(1,v));}
    private void enterManual(){if(!manual){manual=true;mX=lastX;mY=lastY;mZoom=lastZf;followLocation=false;}}
    private void zoomAround(float fx,float fy,double dz){
        enterManual();double w0=TILE*Math.pow(2,mZoom),px=mX+(fx-getWidth()/2f)/w0,py=mY+(fy-getHeight()/2f)/w0;
        mZoom=Math.max(3,Math.min(19.8,mZoom+dz));double w1=TILE*Math.pow(2,mZoom);
        mX=clamp(px-(fx-getWidth()/2f)/w1);mY=clamp(py-(fy-getHeight()/2f)/w1);invalidate();
    }
    /** Back to fitting the whole route (or following the user when live). */
    void resetView(){manual=false;invalidate();}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(!interactive){
            int act=e.getActionMasked();
            if(act==android.view.MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();moved=false;}
            else if(act==android.view.MotionEvent.ACTION_MOVE&&Math.hypot(e.getX()-downX,e.getY()-downY)>android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop())moved=true;
            if(moved&&(act==android.view.MotionEvent.ACTION_UP)){setPressed(false);return true;}
            return super.onTouchEvent(e);
        }
        int a=e.getActionMasked();
        if(a==android.view.MotionEvent.ACTION_DOWN){gestureActive=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
        pinch.onTouchEvent(e);if(!pinch.isInProgress())gestures.onTouchEvent(e);
        if(a==android.view.MotionEvent.ACTION_UP||a==android.view.MotionEvent.ACTION_CANCEL)gestureActive=false;
        return true;
    }
    private String smoothedFor=null;private java.util.List<double[]> smoothedPoints=new ArrayList<>();
    /**
     * The route as drawn: each stretch between GPS gaps is averaged over five fixes and then simplified to
     * 2 m, so GPS wobble on a path walked many times collapses into one line. Walking and running stretches are
     * kept apart so each can be drawn in its own colour.
     */
    private java.util.List<double[]> smoothed(){
        if(route.equals(smoothedFor))return smoothedPoints;
        java.util.List<double[]> out=new ArrayList<>();java.util.List<double[]> run=new ArrayList<>();int runType=0;
        try{
            JSONArray a=new JSONArray(route);
            for(int i=0;i<=a.length();i++){
                JSONObject p=i<a.length()?a.optJSONObject(i):null;
                int type=p!=null&&"r".equals(p.optString("t"))?1:0;
                boolean gap=p!=null&&p.optBoolean("break",false);
                if(!run.isEmpty()&&(p==null||gap||type!=runType)){
                    double[] tail=run.get(run.size()-1);
                    flush(run,runType,out);run.clear();
                    // A walk/run switch is not a gap: the next colour starts where the last one ended.
                    if(p!=null&&!gap)run.add(tail);
                }
                if(p!=null){runType=type;run.add(new double[]{p.optDouble("lat"),p.optDouble("lon")});}
            }
        }catch(Exception ignored){}
        smoothedFor=route;smoothedPoints=out;return out;
    }
    /** Averages a stretch over five fixes, simplifies it to 2 m, and appends {lat, lon, newStretch, isRun}. */
    private static void flush(java.util.List<double[]> run,int type,java.util.List<double[]> out){
        java.util.List<double[]> avg=new ArrayList<>();
        for(int k=0;k<run.size();k++){double la=0,lo=0;int n=0;for(int j=Math.max(0,k-2);j<=Math.min(run.size()-1,k+2);j++){la+=run.get(j)[0];lo+=run.get(j)[1];n++;}avg.add(new double[]{la/n,lo/n});}
        if(!avg.isEmpty()){avg.set(0,run.get(0));avg.set(avg.size()-1,run.get(run.size()-1));}
        java.util.List<double[]> kept=simplify(avg,2.0);
        for(int k=0;k<kept.size();k++)out.add(new double[]{kept.get(k)[0],kept.get(k)[1],k==0?1:0,type});
    }
    /** Douglas-Peucker in metres (equirectangular is plenty at walking scale). */
    private static java.util.List<double[]> simplify(java.util.List<double[]> pts,double eps){
        if(pts.size()<3)return pts;
        boolean[] keep=new boolean[pts.size()];keep[0]=keep[pts.size()-1]=true;
        java.util.ArrayDeque<int[]> stack=new java.util.ArrayDeque<>();stack.push(new int[]{0,pts.size()-1});
        double mLat=111320,mLon=111320*Math.cos(Math.toRadians(pts.get(0)[0]));
        while(!stack.isEmpty()){
            int[] r=stack.pop();double[] a=pts.get(r[0]),b=pts.get(r[1]);double ax=a[1]*mLon,ay=a[0]*mLat,bx=b[1]*mLon,by=b[0]*mLat,dx=bx-ax,dy=by-ay,len=Math.max(1e-9,dx*dx+dy*dy);
            int best=-1;double far=0;
            for(int i=r[0]+1;i<r[1];i++){double px=pts.get(i)[1]*mLon,py=pts.get(i)[0]*mLat;double t=Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/len));double ex=ax+t*dx-px,ey=ay+t*dy-py,d=Math.sqrt(ex*ex+ey*ey);if(d>far){far=d;best=i;}}
            if(best>0&&far>eps){keep[best]=true;stack.push(new int[]{r[0],best});stack.push(new int[]{best,r[1]});}
        }
        java.util.List<double[]> out=new ArrayList<>();for(int i=0;i<pts.size();i++)if(keep[i])out.add(pts.get(i));return out;
    }
    public void clearCurrentLocation(){if(!hasLocation)return;hasLocation=false;invalidate();}
    public void setEmptyMessage(String message){if(message!=null&&!message.equals(emptyMessage)){emptyMessage=message;invalidate();}}
    public void setFollowLocation(boolean follow){followLocation=follow;if(follow)manual=false;invalidate();}
    public void setTheme(int background,int grid,int track,boolean dark){mapBackground=background;routeGrid=grid;routeColor=track;
        String style=getContext().getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getString("mapStyle","auto");
        darkMode="dark".equals(style)?true:"light".equals(style)?false:dark;if("light".equals(style)&&dark)mapBackground=Color.rgb(236,238,240);if("dark".equals(style)&&!dark)mapBackground=Color.rgb(11,11,13);invalidate();}
    /** For the share image: the card decides light or dark, not the app setting. */
    void setThemeExact(int background,int grid,int track,boolean dark){mapBackground=background;routeGrid=grid;routeColor=track;darkMode=dark;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();c.drawColor(mapBackground);JSONArray points;try{points=new JSONArray(route);}catch(Exception e){points=new JSONArray();}if(points.length()==0&&!hasLocation){drawEmpty(c,w,h);return;}
        double minLat=90,maxLat=-90,minLon=180,maxLon=-180;for(int i=0;i<points.length();i++){JSONObject p=points.optJSONObject(i);if(p==null)continue;double lat=p.optDouble("lat"),lon=p.optDouble("lon");minLat=Math.min(minLat,lat);maxLat=Math.max(maxLat,lat);minLon=Math.min(minLon,lon);maxLon=Math.max(maxLon,lon);}if(hasLocation){minLat=Math.min(minLat,currentLat);maxLat=Math.max(maxLat,currentLat);minLon=Math.min(minLon,currentLon);maxLon=Math.max(maxLon,currentLon);}double centerLat=followLocation&&hasLocation?currentLat:(minLat+maxLat)/2,centerLon=followLocation&&hasLocation?currentLon:(minLon+maxLon)/2;int z=followLocation&&hasLocation?17:zoomFor(minLat,maxLat,minLon,maxLon,w,h);double world=Math.scalb((double)TILE,z);double centerX=tileX(centerLon,z)*TILE,centerY=tileY(centerLat,z)*TILE;double spanX=Math.max(2,(tileX(maxLon,z)-tileX(minLon,z))*TILE),spanY=Math.max(2,(tileY(minLat,z)-tileY(maxLat,z))*TILE);double scale=followLocation&&hasLocation?1.0:Math.min(2.5,Math.min(w*.78/spanX,h*.78/spanY));if(manual){z=Math.max(3,Math.min(19,(int)Math.floor(mZoom)));scale=Math.pow(2,mZoom-z);centerX=mX*TILE*Math.scalb(1.0,z);centerY=mY*TILE*Math.scalb(1.0,z);}lastZf=z+Math.log(scale)/Math.log(2);lastX=centerX/(TILE*Math.scalb(1.0,z));lastY=centerY/(TILE*Math.scalb(1.0,z));double left=w/2-centerX*scale,top=h/2-centerY*scale,tileScreen=TILE*scale;
        int x0=(int)Math.floor((-left)/tileScreen),x1=(int)Math.floor((w-left)/tileScreen),y0=(int)Math.floor((-top)/tileScreen),y1=(int)Math.floor((h-top)/tileScreen);int limit=(1<<z);for(int ty=y0;ty<=y1;ty++)for(int tx=x0;tx<=x1;tx++){if(tx<0||tx>=limit||ty<0||ty>=limit)continue;String key=z+"/"+tx+"/"+ty;Bitmap b=tiles.get(key);if(b==null){load(key);continue;}float x=(float)(left+tx*tileScreen),y=(float)(top+ty*tileScreen);paint.setColorFilter(darkMode?DARK_MAP:LIGHT_MAP);c.drawBitmap(b,null,new RectF(x,y,x+(float)tileScreen,y+(float)tileScreen),paint);paint.setColorFilter(null);}
        Path line=new Path(),walkLine=new Path(),runLine=new Path(),gaps=new Path();float px=Float.NaN,py=Float.NaN;for(double[] p:smoothed()){float x=(float)((tileX(p[1],z)*TILE-centerX)*scale+w/2),y=(float)((tileY(p[0],z)*TILE-centerY)*scale+h/2);Path typed=p[3]>0?runLine:walkLine;if(p[2]>0){if(!Float.isNaN(px)&&(x!=px||y!=py)){gaps.moveTo(px,py);gaps.lineTo(x,y);}line.moveTo(x,y);typed.moveTo(x,y);}else{line.lineTo(x,y);typed.lineTo(x,y);}px=x;py=y;}paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(dp(2.5f)*lineScale);paint.setColor(darkMode?Color.argb(200,200,200,205):Color.argb(200,95,99,104));paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(4)*lineScale,dp(4)*lineScale},0));c.drawPath(gaps,paint);paint.setPathEffect(null);if(darkMode){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(11f)*lineScale);paint.setColor(Color.argb(60,Color.red(routeColor),Color.green(routeColor),Color.blue(routeColor)));c.drawPath(walkLine,paint);paint.setColor(Color.argb(60,Color.red(runColor),Color.green(runColor),Color.blue(runColor)));c.drawPath(runLine,paint);}paint.setColor(darkMode?Color.argb(190,0,0,0):Color.argb(235,255,255,255));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(7.5f)*lineScale);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);c.drawPath(line,paint);paint.setStrokeWidth(dp(4.5f)*lineScale);paint.setColor(routeColor);{JSONObject f0=points.optJSONObject(0),f1=points.optJSONObject(points.length()-1);if(f0!=null&&f1!=null&&points.length()>1){float sx=(float)((tileX(f0.optDouble("lon"),z)*TILE-centerX)*scale+w/2),sy=(float)((tileY(f0.optDouble("lat"),z)*TILE-centerY)*scale+h/2),ex=(float)((tileX(f1.optDouble("lon"),z)*TILE-centerX)*scale+w/2),ey=(float)((tileY(f1.optDouble("lat"),z)*TILE-centerY)*scale+h/2);if(Math.hypot(ex-sx,ey-sy)>dp(8))paint.setShader(new android.graphics.LinearGradient(sx,sy,ex,ey,blendTo(routeColor,Color.WHITE,darkMode?.45f:.35f),routeColor,android.graphics.Shader.TileMode.CLAMP));}}c.drawPath(walkLine,paint);paint.setShader(null);paint.setColor(runColor);c.drawPath(runLine,paint);if(markers)drawMarkers(c,z,centerX,centerY,scale,w,h);paint.setStyle(Paint.Style.FILL);JSONObject first=points.optJSONObject(0),last=points.optJSONObject(points.length()-1);if(first!=null)dot(c,(float)((tileX(first.optDouble("lon"),z)*TILE-centerX)*scale+w/2),(float)((tileY(first.optDouble("lat"),z)*TILE-centerY)*scale+h/2),Color.rgb(52,168,83));if(last!=null&&points.length()>1)dot(c,(float)((tileX(last.optDouble("lon"),z)*TILE-centerX)*scale+w/2),(float)((tileY(last.optDouble("lat"),z)*TILE-centerY)*scale+h/2),routeColor);if(hasLocation){float x=(float)((tileX(currentLon,z)*TILE-centerX)*scale+w/2),y=(float)((tileY(currentLat,z)*TILE-centerY)*scale+h/2);double metersPerPixel=156543.03392*Math.cos(Math.toRadians(currentLat))/Math.scalb(1.0,z);float radius=(float)(currentAccuracy/Math.max(.1,metersPerPixel)*scale);paint.setColor(Color.argb(45,33,150,243));c.drawCircle(x,y,Math.max(dp(12),radius),paint);paint.setColor(Color.WHITE);c.drawCircle(x,y,dp(9),paint);paint.setColor(Color.rgb(30,136,229));c.drawCircle(x,y,dp(6),paint);}
    }
    private void load(String key){if(pending.putIfAbsent(key,Boolean.TRUE)!=null)return;new Thread(()->{HttpURLConnection con=null;try{URL url=new URL("https://tile.openstreetmap.org/"+key+".png");con=(HttpURLConnection)url.openConnection();con.setConnectTimeout(5000);con.setReadTimeout(7000);con.setRequestProperty("User-Agent",UA);con.setUseCaches(true);try(InputStream in=con.getInputStream()){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null)tiles.put(key,b);}}catch(Exception ignored){}finally{if(con!=null)con.disconnect();pending.remove(key);postInvalidate();}} ,"KadammMapTile").start();}
    private int zoomFor(double minLat,double maxLat,double minLon,double maxLon,float w,float h){double baseX=(tileX(maxLon,16)-tileX(minLon,16))*TILE,baseY=(tileY(minLat,16)-tileY(maxLat,16))*TILE;double ratio=Math.max((w*.70)/Math.max(1,baseX),(h*.70)/Math.max(1,baseY));int z=16+(int)Math.floor(Math.log(Math.max(.00001,ratio))/Math.log(2));return Math.max(3,Math.min(18,z));}
    private static double tileX(double lon,int z){return (lon+180)/360*Math.scalb(1.0,z);}private static double tileY(double lat,int z){double rad=Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,lat)));return (1-Math.log(Math.tan(rad)+1/Math.cos(rad))/Math.PI)/2*Math.scalb(1.0,z);}
    private void drawEmpty(Canvas c,float w,float h){
        paint.setStyle(Paint.Style.FILL);paint.setColor(mapBackground);c.drawRect(0,0,w,h,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(routeGrid);paint.setStrokeWidth(dp(1));
        float cx=w/2,cy=h/2-dp(12);for(int i=1;i<=3;i++)c.drawCircle(cx,cy,dp(16)*i,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(routeColor);c.drawCircle(cx,cy,dp(5),paint);
        paint.setColor(darkMode?Color.rgb(148,163,184):Color.rgb(80,91,105));paint.setTextSize(dp(12));paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(emptyMessage,cx,h-dp(18),paint);paint.setTextAlign(Paint.Align.LEFT);
    }
    private void dot(Canvas c,float x,float y,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawCircle(x,y,dp(7f)*lineScale,paint);paint.setColor(color);c.drawCircle(x,y,dp(4.5f)*lineScale,paint);}
    private static int blendTo(int a,int b,float t){return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
}
