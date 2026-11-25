package com.stride.run;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Spot heart rate and HRV with the fingertip over the back camera and flash (photoplethysmography): each heartbeat
 * pushes blood into the fingertip and darkens the image slightly. 60 seconds of frames are resampled to 30 Hz,
 * detrended, smoothed, and beats found as peaks at least 0.33 s apart (under 180 bpm), each placed between frames by
 * parabolic interpolation. Beat-to-beat intervals more than 25 % from their neighbours' median are rejected as motion;
 * a result needs 30+ beats with 70 %+ accepted. Heart rate = 60 / mean interval; HRV = RMSSD of consecutive accepted
 * intervals. Good for a check at rest, not for all-day tracking.
 */
public class PulseActivity extends Activity {
    private static final int SECONDS=60,SETTLE_MS=4000,REQ=71;
    private KadammTheme th;private TextView big,status,hint;private ProgressBar bar;private Wave wave;private LinearLayout root;
    private HandlerThread camThread;private Handler cam;private final Handler main=new Handler(Looper.getMainLooper());
    private CameraDevice device;private CameraCaptureSession session;private ImageReader reader;private CaptureRequest.Builder req;
    private final List<double[]> samples=Collections.synchronizedList(new ArrayList<>());
    private long startAt=-1,fingerSince=-1,lastEst;private boolean done,locked;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);th=KadammTheme.resolve(this,getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("themeMode","system"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().setStatusBarColor(th.background);getWindow().setNavigationBarColor(th.background);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(th.background);int p=dp(24);root.setPadding(p,dp(48),p,p);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,in)->{v.setPadding(p,in.getSystemWindowInsetTop()+dp(24),p,in.getSystemWindowInsetBottom()+p);return in;});
        TextView eyebrow=text("PULSE CHECK",11,th.textMuted,true);eyebrow.setLetterSpacing(.1f);root.addView(eyebrow);
        root.addView(text("Heart rate and HRV",28,th.text,true));
        hint=text("Rest your fingertip gently over the back camera and the flash, covering both. Keep still and breathe normally for 60 seconds. Sitting or lying down gives a resting reading.",14,th.textMuted,false);hint.setLineSpacing(dp(3),1f);hint.setPadding(0,dp(8),0,dp(20));root.addView(hint);
        big=text("—",64,th.text,true);big.setGravity(Gravity.CENTER);root.addView(big);
        TextView unit=text("bpm",14,th.textMuted,false);unit.setGravity(Gravity.CENTER);root.addView(unit);
        wave=new Wave(this);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(110));wp.topMargin=dp(16);root.addView(wave,wp);
        bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(SECONDS*10);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(th.accent));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(10));bp.topMargin=dp(16);root.addView(bar,bp);
        status=text("Starting the camera…",15,th.text,true);status.setGravity(Gravity.CENTER);status.setPadding(0,dp(12),0,0);root.addView(status);
        View sp=new View(this);root.addView(sp,new LinearLayout.LayoutParams(-1,0,1));
        Button cancel=new Button(this);cancel.setStateListAnimator(null);cancel.setText("Cancel");cancel.setAllCaps(false);cancel.setTextColor(th.text);GradientDrawable g=new GradientDrawable();g.setColor(th.surfaceRaised);g.setCornerRadius(dp(14));cancel.setBackground(g);cancel.setOnClickListener(v->finish());root.addView(cancel,new LinearLayout.LayoutParams(-1,dp(50)));
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},REQ);
    }
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] res){if(code==REQ&&(res.length==0||res[0]!=PackageManager.PERMISSION_GRANTED)){status.setText("Kadamm needs the camera to see your pulse in your fingertip. Nothing is recorded or saved except the result.");status.setTextColor(th.warning);}else if(code==REQ)open();}
    @Override protected void onResume(){super.onResume();if(!done&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)open();}
    @Override protected void onPause(){super.onPause();close();if(!done){samples.clear();startAt=-1;fingerSince=-1;locked=false;}}

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return t;}

    // ---- Camera ----
    private void open(){
        if(device!=null)return;camThread=new HandlerThread("KadammPulse");camThread.start();cam=new Handler(camThread.getLooper());
        try{CameraManager m=(CameraManager)getSystemService(Context.CAMERA_SERVICE);String id=null;Size size=null;
            for(String c:m.getCameraIdList()){CameraCharacteristics ch=m.getCameraCharacteristics(c);Integer f=ch.get(CameraCharacteristics.LENS_FACING);Boolean flash=ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if(f!=null&&f==CameraCharacteristics.LENS_FACING_BACK&&Boolean.TRUE.equals(flash)){id=c;StreamConfigurationMap map=ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if(map!=null)for(Size s:map.getOutputSizes(ImageFormat.YUV_420_888))if(s.getWidth()>=160&&(size==null||s.getWidth()*s.getHeight()<size.getWidth()*size.getHeight()))size=s;break;}}
            if(id==null||size==null){status.setText("This phone has no back camera with a flash, which the pulse check needs.");return;}
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,3);reader.setOnImageAvailableListener(this::frame,cam);
            m.openCamera(id,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice d){device=d;startSession();}
                @Override public void onDisconnected(CameraDevice d){d.close();device=null;}
                @Override public void onError(CameraDevice d,int e){d.close();device=null;main.post(()->status.setText("The camera is busy. Close other camera apps and try again."));}
            },cam);
        }catch(SecurityException e){status.setText("Camera permission is needed.");}catch(Exception e){status.setText("The camera couldn't be opened.");}
    }
    private void startSession(){
        try{req=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);req.addTarget(reader.getSurface());req.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_TORCH);
            req.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF);req.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,new android.util.Range<>(30,30));
            device.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession s){session=s;try{s.setRepeatingRequest(req.build(),null,cam);main.post(()->status.setText("Cover the camera and flash with your fingertip"));}catch(Exception ignored){}}
                @Override public void onConfigureFailed(CameraCaptureSession s){main.post(()->status.setText("The camera couldn't start."));}
            },cam);}catch(Exception e){main.post(()->status.setText("The camera couldn't start."));}
    }
    private void close(){try{if(session!=null)session.close();}catch(Exception ignored){}session=null;try{if(device!=null)device.close();}catch(Exception ignored){}device=null;try{if(reader!=null)reader.close();}catch(Exception ignored){}reader=null;if(camThread!=null){camThread.quitSafely();camThread=null;}}

    /** Each frame: mean brightness (Y) and redness (Cr) of the centre; a covering finger makes the frame red and dim. */
    private void frame(ImageReader r){
        Image img=null;try{img=r.acquireLatestImage();if(img==null||done)return;long t=img.getTimestamp()/1000000L;
            double y=mean(img.getPlanes()[0],img.getWidth(),img.getHeight(),1),v=mean(img.getPlanes()[2],img.getWidth()/2,img.getHeight()/2,2);
            boolean finger=v>150&&y>20&&y<250;long now=System.currentTimeMillis();
            if(!finger){if(fingerSince>=0||startAt>=0){samples.clear();startAt=-1;fingerSince=-1;unlock();}main.post(()->{status.setText("Cover the camera and flash with your fingertip");status.setTextColor(th.text);bar.setProgress(0);big.setText("—");});return;}
            if(fingerSince<0)fingerSince=now;
            if(now-fingerSince<SETTLE_MS){if(now-fingerSince>2000)lock();main.post(()->status.setText("Hold still…"));return;}
            if(startAt<0)startAt=now;samples.add(new double[]{t,y});long el=now-startAt;
            main.post(()->{bar.setProgress((int)Math.min(SECONDS*10,el/100));status.setText(String.format(Locale.US,"Measuring… %d s",Math.max(0,SECONDS-el/1000)));wave.set(snapshot());});
            if(el>=8000&&now-lastEst>=2000){lastEst=now;double[] q=analyse(snapshot(),false);if(q!=null)main.post(()->big.setText(String.valueOf(Math.round(q[0]))));}
            if(el>=SECONDS*1000L){done=true;double[] res=analyse(snapshot(),true);main.post(()->finish(res));}
        }catch(Exception ignored){}finally{if(img!=null)img.close();}
    }
    private void lock(){if(locked||session==null)return;try{req.set(CaptureRequest.CONTROL_AE_LOCK,true);req.set(CaptureRequest.CONTROL_AWB_LOCK,true);session.setRepeatingRequest(req.build(),null,cam);locked=true;}catch(Exception ignored){}}
    private void unlock(){if(!locked||session==null)return;try{req.set(CaptureRequest.CONTROL_AE_LOCK,false);req.set(CaptureRequest.CONTROL_AWB_LOCK,false);session.setRepeatingRequest(req.build(),null,cam);locked=false;}catch(Exception ignored){}}
    private static double mean(Image.Plane pl,int w,int h,int step){
        ByteBuffer b=pl.getBuffer();int rs=pl.getRowStride(),ps=pl.getPixelStride();long sum=0;int n=0;
        for(int yy=h/4;yy<h*3/4;yy+=2)for(int xx=w/4;xx<w*3/4;xx+=2){int i=yy*rs+xx*ps;if(i<b.limit()){sum+=b.get(i)&0xff;n++;}}
        return n==0?0:sum/(double)n;
    }
    private double[][] snapshot(){synchronized(samples){return samples.toArray(new double[0][]);}}

    /**
     * {heart rate, RMSSD ms, quality %, beats} or null. {@code full} applies the acceptance rules for a saved result;
     * otherwise a quick live estimate from the last 10 seconds.
     */
    static double[] analyse(double[][] raw,boolean full){
        if(raw.length<60)return null;double t0=raw[0][0],t1=raw[raw.length-1][0];if(!full)t0=Math.max(t0,t1-10000);
        int n=(int)((t1-t0)/(1000/30.0));if(n<90)return null;double[] s=new double[n];int j=0;
        for(int i=0;i<n;i++){double t=t0+i*1000/30.0;while(j+1<raw.length&&raw[j+1][0]<t)j++;if(j+1>=raw.length){s[i]=raw[raw.length-1][1];continue;}double[] a=raw[j],b=raw[j+1];double f=b[0]==a[0]?0:(t-a[0])/(b[0]-a[0]);s[i]=a[1]+(b[1]-a[1])*Math.max(0,Math.min(1,f));}
        // Detrend with a 1.5 s moving average, smooth over 5 samples, invert so a beat is a peak.
        double[] d=new double[n];for(int i=0;i<n;i++){double a=0;int c=0;for(int k=Math.max(0,i-22);k<=Math.min(n-1,i+22);k++){a+=s[k];c++;}d[i]=s[i]-a/c;}
        double[] x=new double[n];for(int i=0;i<n;i++){double a=0;int c=0;for(int k=Math.max(0,i-2);k<=Math.min(n-1,i+2);k++){a+=d[k];c++;}x[i]=-a/c;}
        List<Double> peaks=new ArrayList<>();int last=-100;
        for(int i=2;i<n-2;i++){if(!(x[i]>x[i-1]&&x[i]>=x[i+1]&&x[i]>x[i-2]&&x[i]>=x[i+2]))continue;
            double mx=0;for(int k=Math.max(0,i-45);k<=Math.min(n-1,i+45);k++)mx=Math.max(mx,x[k]);if(x[i]<.35*mx)continue;
            if(i-last<10){if(!peaks.isEmpty()&&x[i]>x[last]){peaks.remove(peaks.size()-1);}else continue;}
            double den=x[i-1]-2*x[i]+x[i+1],off=den==0?0:.5*(x[i-1]-x[i+1])/den;peaks.add((i+Math.max(-.5,Math.min(.5,off)))*1000/30.0);last=i;}
        if(peaks.size()<(full?20:5))return null;
        double[] ibi=new double[peaks.size()-1];for(int i=0;i<ibi.length;i++)ibi[i]=peaks.get(i+1)-peaks.get(i);
        boolean[] ok=new boolean[ibi.length];int good=0;double sum=0;
        for(int i=0;i<ibi.length;i++){double[] w=Arrays.copyOfRange(ibi,Math.max(0,i-2),Math.min(ibi.length,i+3));Arrays.sort(w);double med=w[w.length/2];ok[i]=ibi[i]>=333&&ibi[i]<=1500&&Math.abs(ibi[i]-med)<=.25*med;if(ok[i]){good++;sum+=ibi[i];}}
        if(good==0)return null;double hr=60000/(sum/good),q=100.0*good/ibi.length;
        double ss=0;int m=0;for(int i=1;i<ibi.length;i++)if(ok[i]&&ok[i-1]){double df=ibi[i]-ibi[i-1];ss+=df*df;m++;}
        double rmssd=m>=10?Math.sqrt(ss/m):-1;
        if(full&&(good<30||q<70||hr<35||hr>180))return new double[]{hr,rmssd,q,good,0};
        return new double[]{hr,rmssd,q,good,1};
    }
    private void finish(double[] r){
        close();bar.setProgress(SECONDS*10);
        if(r==null||r[4]<1){big.setText("—");status.setTextColor(th.warning);status.setText(r==null?"Not enough clear beats. Press a little more gently, keep the finger and phone still, and try again.":String.format(Locale.US,"Only %.0f %% of beats were clear, so nothing was saved. Keep still and try again.",r[2]));
            Button again=new Button(this);again.setStateListAnimator(null);again.setText("Try again");again.setAllCaps(false);again.setOnClickListener(v->recreate());root.addView(again,root.getChildCount()-1,new LinearLayout.LayoutParams(-1,dp(50)));return;}
        Pro.addSpot(this,r[0],r[1],(int)Math.round(r[2]));big.setText(String.valueOf(Math.round(r[0])));status.setTextColor(th.positive);
        status.setText(r[1]>0?String.format(Locale.US,"Saved · HRV %.0f ms (RMSSD) · %d beats, %.0f %% clear",r[1],Math.round(r[3]),r[2]):String.format(Locale.US,"Saved · %d beats, %.0f %% clear (too few clean pairs for HRV)",Math.round(r[3]),r[2]));
        hint.setText(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)<11?"A morning check at rest feeds your Recovery score when no watch measures HRV.":"Checks before 11 am, at rest, are the ones used for Recovery.");
    }

    /** The last 6 seconds of the pulse wave, detrended. */
    private final class Wave extends View {
        private double[][] d=new double[0][];private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Wave(Context c){super(c);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.5f));p.setStrokeCap(Paint.Cap.ROUND);}
        void set(double[][] x){d=x;invalidate();}
        @Override protected void onDraw(Canvas c){
            p.setColor(th.surfaceRaised);c.drawLine(0,getHeight()/2f,getWidth(),getHeight()/2f,p);if(d.length<10)return;
            double end=d[d.length-1][0],from=end-6000;int i0=0;while(i0<d.length&&d[i0][0]<from)i0++;if(d.length-i0<10)return;
            double lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE;double[] y=new double[d.length-i0];
            for(int i=i0;i<d.length;i++){double a=0;int n=0;for(int k=Math.max(0,i-22);k<=Math.min(d.length-1,i+22);k++){a+=d[k][1];n++;}y[i-i0]=-(d[i][1]-a/n);lo=Math.min(lo,y[i-i0]);hi=Math.max(hi,y[i-i0]);}
            if(hi-lo<1e-6)return;Path path=new Path();for(int i=0;i<y.length;i++){float px=(float)((d[i+i0][0]-from)/6000*getWidth()),py=(float)(getHeight()*(.9-.8*(y[i]-lo)/(hi-lo)));if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}
            p.setColor(th.critical);c.drawPath(path,p);
        }
    }
}
