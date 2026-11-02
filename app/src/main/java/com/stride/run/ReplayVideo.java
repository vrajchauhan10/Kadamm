package com.stride.run;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.View;

import org.json.JSONObject;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * The route replay as a shareable 720 × 1280 MP4 (H.264, 30 fps): the map draws the route as it was walked,
 * with the moving marker, kilometre dots and legend, while the numbers below count up. Eight seconds of replay,
 * then two seconds on the finished route. Dark or light, like the share image. Encoded on the phone.
 */
final class ReplayVideo {
    interface Progress { void at(int percent); }
    interface Done { void done(File file); }
    private ReplayVideo() {}
    private static final int W=720,H=1280,MAP_H=820,FPS=30,PLAY=8*FPS,HOLD=2*FPS;

    static void export(Context ctx,JSONObject o,String title,boolean dark,Progress progress,Done done){
        Context c=ctx.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());
        new Thread(()->{
            File out=new File(new File(c.getCacheDir(),"share"),"kadamm-replay-"+(dark?"dark":"light")+".mp4");out.getParentFile().mkdirs();
            MediaCodec enc=null;MediaMuxer mux=null;Surface surface=null;boolean ok=false;
            try{
                RouteView map=new RouteView(c);int bg=dark?ShareCard.BLACK:Color.WHITE;
                map.setThemeExact(bg,dark?Color.rgb(42,42,46):Color.rgb(227,227,230),dark?ShareCard.ROUTE:ShareCard.BLUE,dark);map.setRunColor(ShareCard.RUN);map.setLineScale(1.45f);map.setFollowLocation(false);map.setLegend(true);map.setRoute(o.optString("route","[]"));
                map.measure(View.MeasureSpec.makeMeasureSpec(W,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(MAP_H,View.MeasureSpec.EXACTLY));map.layout(0,0,W,MAP_H);
                // First passes ask for any map tiles that are missing; wait for them before recording.
                Bitmap warm=Bitmap.createBitmap(W,MAP_H,Bitmap.Config.ARGB_8888);map.setReplay(1f);map.draw(new Canvas(warm));map.setReplay(-1);map.draw(new Canvas(warm));
                for(int i=0;i<50&&RouteView.pendingTiles()>0;i++)Thread.sleep(100);warm.recycle();

                MediaFormat f=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,W,H);
                f.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);f.setInteger(MediaFormat.KEY_BIT_RATE,6_000_000);f.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
                f.setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);f.setInteger(MediaFormat.KEY_LEVEL,MediaCodecInfo.CodecProfileLevel.AVCLevel31);
                enc=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                try{enc.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);}catch(Exception e){f.removeKey(MediaFormat.KEY_PROFILE);f.removeKey(MediaFormat.KEY_LEVEL);enc.reset();enc.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);}
                surface=enc.createInputSurface();enc.start();
                mux=new MediaMuxer(out.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int[] track={-1};long[] written={0};int total=PLAY+HOLD;
                for(int i=0;i<total;i++){
                    float fr=Math.min(1f,i/(float)(PLAY-1));map.setReplay(fr);
                    Canvas cv=surface.lockHardwareCanvas();try{frame(c,cv,map,o,title,dark,fr);}finally{surface.unlockCanvasAndPost(cv);}
                    drain(enc,mux,track,written,i+1-4,false);
                    if(i%10==0){int pc=i*100/total;main.post(()->progress.at(pc));}
                }
                enc.signalEndOfInputStream();drain(enc,mux,track,written,total,true);
                ok=written[0]>0;
            }catch(Exception e){ok=false;}
            finally{
                try{if(enc!=null){enc.stop();enc.release();}}catch(Exception ignored){}
                try{if(mux!=null){mux.stop();mux.release();}}catch(Exception ignored){ok=false;}
                if(surface!=null)surface.release();
            }
            File result=ok?out:null;main.post(()->done.done(result));
        },"KadammReplayVideo").start();
    }

    /** Writes encoded frames; presentation times come from the frame count, so the clip plays at 30 fps however fast it was drawn. */
    private static void drain(MediaCodec enc,MediaMuxer mux,int[] track,long[] written,int want,boolean end){
        // Takes whatever is ready; while more than four frames are waiting, blocks so the input surface never drops one.
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long deadline=System.currentTimeMillis()+(end?4000:1500);
        while(System.currentTimeMillis()<deadline){
            boolean behind=end||written[0]<want;
            int ix=enc.dequeueOutputBuffer(info,behind?10_000:0);
            if(ix==MediaCodec.INFO_TRY_AGAIN_LATER){if(!behind)return;continue;}
            if(ix==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track[0]=mux.addTrack(enc.getOutputFormat());mux.start();continue;}
            if(ix<0)continue;
            ByteBuffer buf=enc.getOutputBuffer(ix);
            if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
            if(info.size>0&&track[0]>=0&&buf!=null){info.presentationTimeUs=written[0]*1_000_000L/FPS;buf.position(info.offset);buf.limit(info.offset+info.size);mux.writeSampleData(track[0],buf,info);written[0]++;}
            enc.releaseOutputBuffer(ix,false);
            if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)return;
        }
    }

    /** One frame: map with the replay on top, then the title, counting numbers, a progress line and the footer. */
    private static void frame(Context c,Canvas cv,RouteView map,JSONObject o,String title,boolean dark,float fr){
        final int BG=dark?ShareCard.BLACK:Color.WHITE,TEXT=dark?Color.WHITE:Color.rgb(11,11,13),MUTED=dark?Color.rgb(154,160,166):Color.rgb(95,99,104),LINE=dark?Color.rgb(42,42,46):Color.rgb(227,227,230),RT=dark?ShareCard.ROUTE:ShareCard.BLUE;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);cv.drawColor(BG);
        cv.save();cv.clipRect(0,0,W,MAP_H);map.draw(cv);cv.restore();
        p.setShader(new LinearGradient(0,MAP_H-220,0,MAP_H,Color.argb(0,Color.red(BG),Color.green(BG),Color.blue(BG)),BG,Shader.TileMode.CLAMP));cv.drawRect(0,MAP_H-220,W,MAP_H,p);
        p.setShader(new LinearGradient(0,0,0,170,dark?Color.argb(190,0,0,0):Color.argb(215,255,255,255),Color.argb(0,Color.red(BG),Color.green(BG),Color.blue(BG)),Shader.TileMode.CLAMP));cv.drawRect(0,0,W,170,p);p.setShader(null);
        Drawable logo=c.getDrawable(dark?R.drawable.ic_kadamm_logo_dark:R.drawable.ic_kadamm_logo);if(logo!=null){logo.setBounds(40,46,40+64,46+64);logo.draw(cv);}
        p.setColor(TEXT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(34);p.setLetterSpacing(.16f);cv.drawText("KADAMM",120,90,p);p.setLetterSpacing(0f);
        double[] st=map.replayState();boolean done=fr>=1,run=st[1]>0&&!done;
        String chip=done?"FINISHED":run?"RUNNING":"WALKING";p.setTextSize(22);float cw=p.measureText(chip)+40;p.setColor(done?Color.rgb(52,168,83):run?ShareCard.RUN:RT);
        cv.drawRoundRect(new RectF(40,MAP_H-70,40+cw,MAP_H-28),21,21,p);p.setColor(Color.WHITE);cv.drawText(chip,60,MAP_H-41,p);
        float y=MAP_H+20;p.setColor(TEXT);p.setTextSize(50);String t=title;while(p.measureText(t)>W-80&&t.length()>4)t=t.substring(0,t.length()-2);cv.drawText(t.equals(title)?t:t+"…",40,y+40,p);
        p.setTypeface(Typeface.DEFAULT);p.setColor(MUTED);p.setTextSize(24);cv.drawText(o.optString("date","")+"  ·  "+o.optString("time",""),40,y+80,p);
        double km=done?o.optDouble("distanceKm",st[0]/1000):st[0]/1000;long sec=done?o.optLong("durationMs",0)/1000:Math.round(st[2]);long pace=km>.05?Math.round(sec/km):0;
        float hy=y+220;p.setColor(RT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(120);String dk=String.format(Locale.US,"%.2f",km);cv.drawText(dk,36,hy,p);
        float dw=p.measureText(dk);p.setColor(MUTED);p.setTextSize(30);p.setTypeface(Typeface.DEFAULT);cv.drawText("km",36+dw+10,hy,p);
        p.setTextAlign(Paint.Align.RIGHT);float rx=W-40;p.setTextSize(20);p.setLetterSpacing(.1f);cv.drawText("TIME",rx,hy-96,p);cv.drawText("AVG PACE",rx,hy-26,p);p.setLetterSpacing(0f);
        p.setColor(TEXT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(40);cv.drawText(sec>=3600?String.format(Locale.US,"%d:%02d:%02d",sec/3600,(sec/60)%60,sec%60):String.format(Locale.US,"%d:%02d",sec/60,sec%60),rx,hy-56,p);
        cv.drawText(pace>0?String.format(Locale.US,"%d:%02d /km",pace/60,pace%60):"—",rx,hy+14,p);p.setTextAlign(Paint.Align.LEFT);
        float py=hy+56;p.setColor(LINE);cv.drawRoundRect(new RectF(40,py,W-40,py+10),5,5,p);p.setColor(done?Color.rgb(52,168,83):RT);cv.drawRoundRect(new RectF(40,py,40+(W-80)*fr,py+10),5,5,p);
        p.setColor(RT);cv.drawRect(40,H-74,82,H-70,p);p.setColor(MUTED);p.setTextSize(22);p.setTypeface(Typeface.DEFAULT);cv.drawText("Tracked with Kadamm  ·  Every step counts",40,H-38,p);
        ShareCard.signature(c,cv,W-40,H-36,MUTED,.7f);
    }
}
