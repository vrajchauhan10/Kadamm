package com.stride.run;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int PERMISSIONS=100;
    private static final String APP_VERSION="9.6";private int dailyGoal(){return getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("stepGoal",10000);}
    private int bg,panel,raised,lime,violet,accentOn,ink,muted,line,focus,positive,warning,critical; private boolean darkTheme=true,previousMoving=false;
    private SharedPreferences prefs,historyPrefs; private FrameLayout root,drawerLayer; private LinearLayout shell,content,nav,drawer,navToday,navHistory,navProfile; private View drawerScrim; private ScrollView scroller; private TextView timer,distance,pace,steps,status,start,todayCount,todayDistance,dailyStepValue,dailyGoalMessage,accuracyLabel,activityModeLabel,todayActiveTime,todayWalkDistance,todayRunDistance,todayCaloriesValue;private ProgressBar dailyGoalProgress,dailyDistanceProgress; private TextView dailyDistanceValue;private View dailyGoalCard; private RouteView route; private Handler handler=new Handler(Looper.getMainLooper()); private String page="Today",activityType="Walk",activityRange="7D"; private Calendar shownMonth=Calendar.getInstance(),selectedDate=Calendar.getInstance(); private JSONObject lastSession; private float gestureDownX,gestureDownY;private long gestureDownAt;
    private static final int STEP_PERMISSION=101;private ProgressRingView ring;private TextView heroPercent,heroRemaining,heroDistance,heroSource,heroStart,heroNote;private View trackCard;private LinearLayout trackStats,trackEnd;private TextView heroSessions;private TextView liveSpeed,liveElev,liveKcal;private Map<String,Long> dayTotals;private double idleLat,idleLon;private float idleAcc;private boolean hasIdle,stepListening,stepSetupShown,resumed,syncing;private long lastHealthRefresh;
    private final SensorEventListener stepListener=new SensorEventListener(){@Override public void onSensorChanged(SensorEvent e){DaySteps.ingest(MainActivity.this,e.values[0]);}@Override public void onAccuracyChanged(Sensor sensor,int accuracy){}};
    private final Runnable refresh=()->{updateLive();handler.postDelayed(this.refresh,1000);};
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("stride_state",MODE_PRIVATE);historyPrefs=getSharedPreferences("kadamm_history",MODE_PRIVATE);if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.os.StrictMode.setThreadPolicy(new android.os.StrictMode.ThreadPolicy.Builder().detectNetwork().detectCustomSlowCalls().penaltyLog().build());RecorderService.recover(this);applyTheme();buildShell();if(getIntent()!=null&&getIntent().getStringExtra("page")!=null)page=getIntent().getStringExtra("page");handleDayIntent(getIntent());if(getIntent()!=null&&getIntent().getBooleanExtra("startWalk",false)&&!prefs.getBoolean("active",false)&&LocalAccount.loggedIn(this)){activityType="Walk";root.post(this::onRecord);}AutoTrack.sync(this);renderPage();handler.post(refresh);if(!LocalAccount.loggedIn(this)&&!LocalAccount.guest(this))showAuth(LocalAccount.exists(this));else{playIntro(null);firstRunPermissions();}if(prefs.getBoolean("active",false))root.post(this::openLive);}
    private void applyTheme(){String mode=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("themeMode","system");KadammTheme t=KadammTheme.resolve(this,mode);darkTheme=t.dark;bg=t.background;panel=t.surface;raised=t.surfaceRaised;lime=t.accent;violet=t.accentSecondary;accentOn=t.accentOn;ink=t.text;muted=t.textMuted;line=t.border;focus=t.focus;positive=t.positive;warning=t.warning;critical=t.critical;getWindow().setBackgroundDrawable(new ColorDrawable(bg));getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(panel);getWindow().getDecorView().setSystemUiVisibility(darkTheme?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);if(root!=null)root.setBackgroundColor(bg);if(nav!=null)nav.setBackgroundColor(panel);if(route!=null)route.setTheme(bg,raised,lime,darkTheme);}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);} private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;} private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;} private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private TextView tx(String s,int sz,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sz);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",0));return t;} private GradientDrawable shape(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private void buildShell(){root=new FrameLayout(this);root.setBackgroundColor(bg);setContentView(root);shell=col();FrameLayout.LayoutParams shellParams=new FrameLayout.LayoutParams(-1,-1);root.addView(shell,shellParams);scroller=new ScrollView(this);scroller.setFillViewport(true);scroller.setClipToPadding(false);content=col();content.setPadding(dp(20),dp(18),dp(20),dp(20));scroller.addView(content);shell.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));buildMiniBar();nav=row();nav.setPadding(dp(10),dp(6),dp(10),dp(6));nav.setBackgroundColor(panel);shell.addView(nav,lp(-1,72));navToday=navItem("Today","◷");navHistory=navItem("History","▦");navProfile=navItem("Profile","◉");
        drawerLayer=new FrameLayout(this);drawerLayer.setVisibility(View.GONE);root.addView(drawerLayer,new FrameLayout.LayoutParams(-1,-1));drawerScrim=new View(this);drawerScrim.setBackgroundColor(Color.argb(155,0,0,0));drawerLayer.addView(drawerScrim,new FrameLayout.LayoutParams(-1,-1));drawerScrim.setOnClickListener(v->closeDrawer());drawer=col();drawer.setPadding(dp(20),dp(24),dp(16),dp(18));drawer.setBackground(panelShape(0));drawer.setElevation(dp(18));FrameLayout.LayoutParams drawerParams=new FrameLayout.LayoutParams(Math.min(dp(340),getResources().getDisplayMetrics().widthPixels-dp(48)),-1,Gravity.START);drawerLayer.addView(drawer,drawerParams);
        root.setOnApplyWindowInsetsListener((v,insets)->{int l,t,r,b;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());l=safe.left;t=safe.top;r=safe.right;b=safe.bottom;}else{l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();}root.setPadding(l,t,r,b);return insets;});root.requestApplyInsets();}
    @Override public boolean dispatchTouchEvent(MotionEvent ev){
        int a=ev.getActionMasked();
        if(a==MotionEvent.ACTION_DOWN){gestureDownX=ev.getX();gestureDownY=ev.getY();gestureDownAt=System.currentTimeMillis();dragging=false;beginPull(ev);}
        if(pull.handle(ev,c->super.dispatchTouchEvent(c)))return true;
        boolean pager=!RouteView.gestureActive&&tabIndex(page)>=0&&drawerLayer!=null&&drawerLayer.getVisibility()!=View.VISIBLE&&(liveLayer==null||liveLayer.getVisibility()!=View.VISIBLE)&&(mapLayer==null||mapLayer.getVisibility()!=View.VISIBLE)&&ev.getPointerCount()==1;
        float dx=ev.getX()-gestureDownX,dy=ev.getY()-gestureDownY;
        if(pager&&a==MotionEvent.ACTION_MOVE){
            if(!dragging&&Math.abs(dx)>dp(20)&&Math.abs(dx)>Math.abs(dy)*1.7f){
                dragging=true;MotionEvent cancel=MotionEvent.obtain(ev);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
                content.animate().cancel();if(!page.equals(renderedPage))renderPageNow();
            }
            if(dragging){int i=tabIndex(page);boolean edge=(dx>0&&i==0)||(dx<0&&i==2);content.setTranslationX(edge?dx*.18f:dx);content.setAlpha(edge?1f:Math.max(.3f,1f-Math.abs(dx)/Math.max(1,content.getWidth())));return true;}
        }
        if(dragging&&(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL)){
            dragging=false;int i=tabIndex(page),next=dx<0?i+1:i-1;long dt=System.currentTimeMillis()-gestureDownAt;
            boolean commit=a==MotionEvent.ACTION_UP&&i>=0&&next>=0&&next<=2&&(Math.abs(dx)>content.getWidth()*.28f||(dt<320&&Math.abs(dx)>dp(64)));
            if(commit){page=TABS[next];slideTo(dx<0?1:-1);}else content.animate().translationX(0).alpha(1f).setDuration(170).start();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }
    // ---- Goal celebration ----
    private FrameLayout celebrateLayer;
    /** Once a day, the first time Kadamm is on screen after the step goal is reached: confetti and a trophy card. */
    private void maybeCelebrate(){
        if(!resumed||authLayer!=null||celebrateLayer!=null)return;int goal=dailyGoal();long n=DaySteps.todayTotal(this);
        SharedPreferences st=getSharedPreferences("kadamm_settings",MODE_PRIVATE);
        if(n<goal||!DaySteps.hasTodayData(this)||todayKey().equals(st.getString("celebratedDay","")))return;
        st.edit().putString("celebratedDay",todayKey()).apply();showCelebration(n,goal);
    }
    private void showCelebration(long n,int goal){
        int[] streak=DaySteps.streaks(DaySteps.allDayTotals(this),goal);
        celebrateLayer=new FrameLayout(this);celebrateLayer.setBackgroundColor(Color.argb(170,0,0,0));celebrateLayer.setClickable(true);root.addView(celebrateLayer,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout card=col();card.setGravity(Gravity.CENTER_HORIZONTAL);card.setPadding(dp(24),dp(28),dp(24),dp(20));
        card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{blend(panel,positive,darkTheme?.30f:.18f),panel}){{setCornerRadius(dp(28));setStroke(dp(1),blend(line,positive,.4f));}});
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);cp.setMargins(dp(28),0,dp(28),0);celebrateLayer.addView(card,cp);
        android.widget.ImageView trophy=icon(R.drawable.ic_trophy,Color.WHITE,22);GradientDrawable tg=new GradientDrawable();tg.setShape(GradientDrawable.OVAL);tg.setColor(positive);tg.setStroke(dp(5),blend(positive,Color.WHITE,.5f));trophy.setBackground(tg);card.addView(trophy,lp(104,104));
        TextView big=tx(String.format(Locale.US,"%,d steps!",goal),30,ink,true);big.setGravity(Gravity.CENTER);LinearLayout.LayoutParams bp=lp(-1,-2);bp.topMargin=dp(16);card.addView(big,bp);
        TextView sub=tx(n>goal?String.format(Locale.US,"Daily goal done, and %,d more on top. Brilliant.",n-goal):"Daily goal done. Brilliant.",15,ink,false);sub.setGravity(Gravity.CENTER);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(6);card.addView(sub,sp);
        TextView st=tx(streak[0]>1?String.format(Locale.US,"🔥 %d-day streak%s",streak[0],streak[0]>=streak[1]?" · your best ever":""):"🔥 Day one of a new streak",13,flame(),true);st.setGravity(Gravity.CENTER);LinearLayout.LayoutParams stp=lp(-1,-2);stp.topMargin=dp(10);card.addView(st,stp);
        LinearLayout btns=row();LinearLayout.LayoutParams btp=lp(-1,50);btp.topMargin=dp(20);card.addView(btns,btp);
        Button share=button("Share",raised,ink),ok=button("Keep going",positive,darkTheme?Color.rgb(11,11,13):Color.WHITE);ok.setTextSize(15);share.setTextSize(15);
        btns.addView(share,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams okp=new LinearLayout.LayoutParams(0,-1,1);okp.leftMargin=dp(10);btns.addView(ok,okp);
        Runnable close=()->{FrameLayout l=celebrateLayer;celebrateLayer=null;if(l!=null)l.animate().alpha(0f).setDuration(200).withEndAction(()->root.removeView(l)).start();};
        ok.setOnClickListener(v->close.run());celebrateLayer.setOnClickListener(v->close.run());share.setOnClickListener(v->{close.run();shareSheet("Share your goal",todayShareText(),null,null);});
        ConfettiView shower=ConfettiView.shower(this);celebrateLayer.addView(shower,new FrameLayout.LayoutParams(-1,-1));
        celebrateLayer.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        if(reducedMotion()){shower.setVisibility(View.GONE);return;}
        celebrateLayer.setAlpha(0f);celebrateLayer.animate().alpha(1f).setDuration(220).withLayer().start();
        card.setScaleX(.6f);card.setScaleY(.6f);card.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).withLayer().start();
        trophy.setRotation(-25);trophy.animate().rotation(0).setStartDelay(200).setDuration(600).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
        shower.play();celebrateLayer.postDelayed(()->{if(celebrateLayer!=null&&!reducedMotion()){ConfettiView again=ConfettiView.shower(this);celebrateLayer.addView(again,celebrateLayer.getChildCount()-1,new FrameLayout.LayoutParams(-1,-1));again.play();}},1400);
    }
    // ---- Mini bar: the live session, minimised above the tabs ----
    private LinearLayout miniBar;private TextView mbTitle,mbSub,mbToggle;private ProgressBar mbProgress;private android.widget.ImageView mbIcon;private View mbDot;
    private static final android.view.animation.Interpolator EASE=new android.view.animation.PathInterpolator(.2f,0f,0f,1f);
    private void buildMiniBar(){
        miniBar=col();miniBar.setVisibility(View.GONE);miniBar.setClickable(true);miniBar.setFocusable(true);miniBar.setContentDescription("Live session. Open the map");
        LinearLayout.LayoutParams mp=lp(-1,-2);mp.setMargins(dp(10),dp(4),dp(10),dp(4));shell.addView(miniBar,1,mp);
        LinearLayout r=row();r.setPadding(dp(10),dp(9),dp(8),dp(7));miniBar.addView(r,lp(-1,-2));
        FrameLayout ic=new FrameLayout(this);r.addView(ic,lp(42,42));mbIcon=icon(R.drawable.ic_walk,Color.WHITE,9);GradientDrawable ig=new GradientDrawable();ig.setShape(GradientDrawable.OVAL);ig.setColor(ShareCard.BLUE);mbIcon.setBackground(ig);ic.addView(mbIcon,new FrameLayout.LayoutParams(-1,-1));
        mbDot=new View(this);GradientDrawable dg=new GradientDrawable();dg.setShape(GradientDrawable.OVAL);dg.setColor(positive);dg.setStroke(dp(2),panel);mbDot.setBackground(dg);ic.addView(mbDot,new FrameLayout.LayoutParams(dp(13),dp(13),Gravity.TOP|Gravity.END));
        LinearLayout t=col();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(12);r.addView(t,tp);
        mbTitle=tx("Walking",15,ink,true);mbTitle.setTypeface(Typeface.create("sans-serif-medium",0));t.addView(mbTitle);mbSub=tx("",11,muted,false);t.addView(mbSub);
        mbToggle=tx("Ⅱ",17,ink,true);mbToggle.setGravity(Gravity.CENTER);GradientDrawable tg=new GradientDrawable();tg.setShape(GradientDrawable.OVAL);tg.setColor(raised);mbToggle.setBackground(ripple(tg));mbToggle.setContentDescription("Pause");r.addView(mbToggle,lp(42,42));
        mbToggle.setOnClickListener(v->{command(RecorderService.ACTION_TOGGLE);v.animate().scaleX(.85f).scaleY(.85f).setDuration(80).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(120).start()).start();});
        TextView up=tx("⌃",20,muted,true);up.setGravity(Gravity.CENTER);r.addView(up,lp(30,42));
        mbProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);mbProgress.setMax(1000);mbProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(lime));mbProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(raised));
        LinearLayout.LayoutParams pp=lp(-1,4);pp.setMargins(dp(12),0,dp(12),dp(6));miniBar.addView(mbProgress,pp);
        miniBar.setOnClickListener(v->openLive());
    }
    /** Shows the bar while a session runs with the live screen closed; only the essentials: type, time, distance, steps to go. */
    private void updateMiniBar(){
        if(miniBar==null)return;boolean active=prefs.getBoolean("active",false),liveOpen=liveLayer!=null&&liveLayer.getVisibility()==View.VISIBLE&&!liveClosing;
        boolean show=active&&!liveOpen&&authLayer==null;
        if(!show){if(miniBar.getVisibility()==View.VISIBLE&&!liveOpen)miniBar.setVisibility(View.GONE);return;}
        if(miniBar.getVisibility()!=View.VISIBLE)revealMiniBar();
        boolean paused=prefs.getBoolean("paused",false),run="Run".equals(prefs.getString("currentType","Walk")),indoor="indoor".equals(prefs.getString("activityMode","outdoor"));
        int goal=dailyGoal();long today=DaySteps.todayTotal(this),left=Math.max(0,goal-today);
        GradientDrawable bgd=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{blend(panel,run?pink():lime,darkTheme?.24f:.13f),panel});bgd.setCornerRadius(dp(20));bgd.setStroke(dp(1),blend(line,run?pink():lime,.35f));miniBar.setBackground(bgd);
        mbIcon.setImageResource(run?R.drawable.ic_run:R.drawable.ic_walk);((GradientDrawable)mbIcon.getBackground()).setColor(run?pink():ShareCard.BLUE);
        mbTitle.setText((paused?"Paused":(indoor?"Indoor ":"")+(run?"Running":"Walking"))+"  ·  "+formatDuration(prefs.getLong("elapsed",0)));
        mbSub.setText(String.format(Locale.US,"%.2f km  ·  ",prefs.getFloat("distance",0))+(left>0?String.format(Locale.US,"%,d steps to your goal",left):"Goal reached ✓"));
        mbProgress.setProgress((int)Math.min(1000,today*1000/Math.max(1,goal)));mbProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(left>0?lime:positive));
        mbToggle.setText(paused?"▶":"Ⅱ");mbToggle.setContentDescription(paused?"Resume":"Pause");
        mbDot.setAlpha(paused?.3f:(System.currentTimeMillis()/1000%2==0?1f:.35f));
    }
    private boolean liveClosing;
    private void revealMiniBar(){
        miniBar.setVisibility(View.VISIBLE);if(reducedMotion())return;
        miniBar.setAlpha(0f);miniBar.setTranslationY(dp(40));miniBar.setScaleX(.94f);
        miniBar.animate().alpha(1f).translationY(0).scaleX(1f).setDuration(320).setInterpolator(EASE).start();
    }
    private PullDown pull;
    /**
     * Swipe down to close: the live screen slides away, and any page other than the three tabs goes back
     * when pulled down from its top. Maps and charts keep their own drags.
     */
    private void beginPull(MotionEvent ev){
        if(pull==null)pull=new PullDown(getResources().getDisplayMetrics().density,true);
        View target=null;Runnable close=null;
        if(authLayer!=null||(mapLayer!=null&&mapLayer.getVisibility()==View.VISIBLE)||(drawerLayer!=null&&drawerLayer.getVisibility()==View.VISIBLE)){}
        else if(liveLayer!=null&&liveLayer.getVisibility()==View.VISIBLE){if(!scrolledDown(liveLayer)){target=liveLayer;close=this::closeLive;}}
        else if(tabIndex(page)<0&&scroller.getScrollY()==0){target=scroller;close=()->{scroller.setTranslationY(0);scroller.setAlpha(1f);onBackPressed();};}
        pull.begin(ev,target,close);
    }
    /** True when a scrolling view inside {@code v} is not at its top, so a downward drag should scroll it instead. */
    private boolean scrolledDown(View v){if(v.canScrollVertically(-1))return true;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(g.getChildAt(i).getVisibility()==View.VISIBLE&&scrolledDown(g.getChildAt(i)))return true;}return false;}
    private String lastNavTap="";private long lastNavAt;
    /** Tapping the tab you are on (or double-tapping any tab) scrolls back to the top. */
    private LinearLayout navItem(String title,String icon){LinearLayout item=col();item.setGravity(Gravity.CENTER);item.setFocusable(true);String shown="Today".equals(title)?"Home":title;item.setContentDescription(shown);item.setOnClickListener(v->{if(title.equals(page)&&scroller!=null){scroller.smoothScrollTo(0,0);return;}long now=android.os.SystemClock.uptimeMillis();boolean dbl=title.equals(lastNavTap)&&now-lastNavAt<400;lastNavTap=title;lastNavAt=now;navigate(title);if(dbl&&scroller!=null)scroller.post(()->scroller.smoothScrollTo(0,0));});android.widget.ImageView symbol=icon(iconRes(title),muted,2);item.addView(symbol,lp(-1,28));TextView label=tx(shown,11,muted,true);label.setGravity(Gravity.CENTER);item.addView(label,lp(-1,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(3),0,dp(3),0);nav.addView(item,p);return item;}
    private void navigate(String destination){page=destination;closeDrawer();renderPage();}
    private void openDrawer(){buildDrawerContents();drawer.setBackgroundColor(panel);drawerLayer.setVisibility(View.VISIBLE);drawerLayer.setAlpha(0f);drawerLayer.animate().alpha(1f).setDuration(reducedMotion()?0:160).start();drawer.setTranslationX(-drawer.getLayoutParams().width);drawer.animate().translationX(0).setInterpolator(new android.view.animation.DecelerateInterpolator()).setDuration(reducedMotion()?0:240).start();}
    private void closeDrawer(){if(drawerLayer==null||drawerLayer.getVisibility()!=View.VISIBLE)return;drawer.animate().translationX(-drawer.getLayoutParams().width).setDuration(reducedMotion()?0:160).start();drawerLayer.animate().alpha(0f).setDuration(reducedMotion()?0:120).withEndAction(()->drawerLayer.setVisibility(View.GONE)).start();}
    private boolean reducedMotion(){try{return android.provider.Settings.Global.getFloat(getContentResolver(),"animator_duration_scale",1f)<=0f;}catch(Exception e){return false;}}
    private LinearLayout drawerList;
    private void buildDrawerContents(){
        drawer.removeAllViews();drawer.setPadding(dp(14),dp(14),dp(14),dp(14));
        LinearLayout top=row();drawer.addView(top,lp(-1,48));
        android.widget.ImageView logo=new android.widget.ImageView(this);logo.setImageResource(logoRes());LinearLayout.LayoutParams lgp=lp(38,38);lgp.leftMargin=dp(6);top.addView(logo,lgp);
        TextView word=tx("KADAMM",17,ink,true);word.setLetterSpacing(.14f);LinearLayout.LayoutParams wdp=lp(0,-2);wdp.weight=1;wdp.leftMargin=dp(10);top.addView(word,wdp);
        android.widget.ImageView close=icon(R.drawable.ic_close,muted,12);close.setContentDescription("Close menu");close.setFocusable(true);close.setBackground(rippleOn(Color.TRANSPARENT,24));top.addView(close,lp(48,48));close.setOnClickListener(v->closeDrawer());
        android.widget.ScrollView sv=new android.widget.ScrollView(this);sv.setVerticalScrollBarEnabled(false);LinearLayout.LayoutParams svp=new LinearLayout.LayoutParams(-1,0,1);svp.topMargin=dp(8);drawer.addView(sv,svp);
        drawerList=col();sv.addView(drawerList);
        LinearLayout prof=row();prof.setPadding(dp(12),dp(12),dp(12),dp(12));prof.setBackground(shape(raised,18));drawerList.addView(prof,lp(-1,-2));
        prof.addView(avatarView(LocalAccount.current(this),44),lp(44,44));
        LinearLayout who=col();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(12);prof.addView(who,wp);who.addView(tx(displayName(),16,ink,true));who.addView(tx(LocalAccount.email(this)!=null?LocalAccount.email(this):"Guest · tap to sign in",11,muted,false));prof.setFocusable(true);prof.setBackground(rippleOn(raised,18));prof.setOnClickListener(v->navigate("Profile"));
        JSONArray sessions=history();int count=countOn(sessions,todayKey(),null);boolean data=DaySteps.hasTodayData(this);long n=DaySteps.todayTotal(this);int goal=dailyGoal();
        LinearLayout today=row();today.setPadding(dp(12),dp(12),dp(12),dp(12));GradientDrawable tb=shape(panel,18);tb.setStroke(dp(1),line);today.setBackground(tb);LinearLayout.LayoutParams tp=lp(-1,-2);tp.topMargin=dp(10);drawerList.addView(today,tp);
        ProgressRingView mini=new ProgressRingView(this,chartTheme());mini.setCompact(true);mini.setProgress(n,goal,data);today.addView(mini,lp(60,60));
        LinearLayout tc=col();LinearLayout.LayoutParams tcp=new LinearLayout.LayoutParams(0,-2,1);tcp.leftMargin=dp(12);today.addView(tc,tcp);
        tc.addView(tx("TODAY",10,muted,true));tc.addView(tx(data?String.format(Locale.US,"%,d steps",n):"Steps not set up",18,ink,true));
        tc.addView(tx(String.format(Locale.US,"%.2f km · %d %s · goal %,d",DaySteps.distanceToday(this)[0],count,count==1?"session":"sessions",goal),11,muted,false));
        drawerSection("ACTIVITY");
        drawerAction("Today","◷","Today",null);drawerAction("History","▦","History",sessions.length()+(sessions.length()==1?" session":" sessions"));drawerAction("Profile","◉","Profile",null);int nr=Insights.routes(this).length();drawerAction("Routes","↗","Routes",nr==0?null:nr+(nr==1?" route":" routes"));drawerAction("Places","place","Places",null);drawerAction("Reports","≈","Reports",null);int earned=0;for(Progress.Badge bd:Progress.badges(SessionMetrics.ready()?this:null,history(),DaySteps.allDayTotals(this),dailyGoal()))if(bd.earned())earned++;drawerAction("Achievements","★","Achievements",earned+" badges");
        drawerSection("SETTINGS");
        drawerAction("Goals & targets","◎","Goals",String.format(Locale.US,"%,d steps",goal));drawerAction("Notifications","bell","Notifications",notificationsAllowed()?"On":"Off");drawerAction("Auto","run","Auto",AutoTrack.enabled(this)?"On":"Off");drawerAction("Permissions","gear","Permissions",null);drawerAction("Storage","storage","Storage",null);drawerAction("Share Kadamm","share","ShareApp","Send the app");
        drawerAction("Step sources & health","＋","Health",getSharedPreferences("kadamm_health",MODE_PRIVATE).getBoolean("healthConnected",false)?"Connected":"Set up");
        drawerSection("APPEARANCE");
        LinearLayout modes=row();drawerList.addView(modes,lp(-1,40));String selectedMode=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("themeMode","system");
        for(String m:new String[]{"Light","Dark","System"}){boolean on=m.equalsIgnoreCase(selectedMode);Button b=button(m,on?lime:raised,on?accentOn:ink);b.setTextSize(12);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(modes.getChildCount()>0)bp.leftMargin=dp(6);modes.addView(b,bp);
            b.setOnClickListener(v->confirmChange("Switch to "+m.toLowerCase(Locale.US)+" theme?","Kadamm will redraw in the "+m.toLowerCase(Locale.US)+" theme.",()->{getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putString("themeMode",m.toLowerCase(Locale.US)).apply();applyTheme();drawer.setBackgroundColor(panel);buildDrawerContents();renderPageNow();},null));}
        boolean active=prefs.getBoolean("active",false);
        Button start=button(active?"View live session":"▶  Start activity",lime,accentOn);start.setTextSize(15);LinearLayout.LayoutParams sp=lp(-1,50);sp.topMargin=dp(10);drawer.addView(start,sp);
        start.setOnClickListener(v->{closeDrawer();if(prefs.getBoolean("active",false))openLive();else{page="Today";renderPageNow();onRecord();}});
        LinearLayout foot=row();LinearLayout.LayoutParams fp=lp(-1,-2);fp.topMargin=dp(8);drawer.addView(foot,fp);TextView footer=tx("Kadamm v"+APP_VERSION+" · Every step counts",10,muted,false);foot.addView(footer,new LinearLayout.LayoutParams(0,-2,1));foot.addView(signature(15));
    }
    private static android.graphics.Typeface vibes,parisienne;
    /** The maker's mark, as in Aalap: "crafted by" in Great Vibes and the "V" in Parisienne, small, muted, bottom right. */
    private LinearLayout signature(float size){
        if(vibes==null){try{vibes=getResources().getFont(R.font.great_vibes);parisienne=getResources().getFont(R.font.parisienne);}catch(Exception e){vibes=parisienne=Typeface.SERIF;}}
        int ink60=Color.argb(153,Color.red(muted),Color.green(muted),Color.blue(muted));
        LinearLayout r=row();r.setGravity(Gravity.END|Gravity.BOTTOM);r.setBaselineAligned(true);r.setContentDescription("crafted by V");
        TextView a=new TextView(this);a.setText("crafted by ");a.setTypeface(vibes);a.setTextSize(size);a.setTextColor(ink60);r.addView(a);
        TextView v=new TextView(this);v.setText("V");v.setTypeface(parisienne);v.setTextSize(size+1);v.setTextColor(ink60);r.addView(v);
        return r;
    }
    private void signatureAtEnd(){LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(22);p.rightMargin=dp(6);content.addView(signature(17),p);}
    private void drawerSection(String title){TextView t=tx(title,10,muted,true);t.setLetterSpacing(.1f);t.setPadding(dp(8),0,0,0);LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(18);p.bottomMargin=dp(4);drawerList.addView(t,p);}
    /** Full-width 52dp row: the whole row is the touch target. Selected rows get a tinted background and a filled icon tile. */
    private void drawerAction(String label,String icon,String target,String badge){if(target.equals("Auto"))label="Auto-tracking";else if(target.equals("Storage"))label="Storage & backup";
        if(target.equals("ShareApp"))label="Share Kadamm";if(target.equals("Places"))label="Places you walk";boolean selected=target.equals(page);LinearLayout item=row();item.setPadding(dp(6),0,dp(10),0);item.setBackground(rippleOn(selected?blend(panel,lime,darkTheme?.18f:.10f):Color.TRANSPARENT,14));LinearLayout.LayoutParams p=lp(-1,52);p.topMargin=dp(2);drawerList.addView(item,p);
        android.widget.ImageView glyph=icon(iconRes(target),selected?accentOn:muted,8);glyph.setBackground(shape(selected?lime:raised,11));item.addView(glyph,lp(36,36));
        TextView l=tx(label,15,selected?lime:ink,true);LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,-2,1);llp.leftMargin=dp(12);item.addView(l,llp);
        if(badge!=null)item.addView(tx(badge,11,muted,false));
        item.setFocusable(true);item.setClickable(true);item.setContentDescription(label+(selected?", selected":""));item.setOnClickListener(v->{if(target.equals("ShareApp")){closeDrawer();shareApp();}else navigate(target);});
    }
    private android.graphics.drawable.Drawable ripple(android.graphics.drawable.Drawable content){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(darkTheme?56:38,Color.red(ink),Color.green(ink),Color.blue(ink))),content,content);}
    private android.graphics.drawable.Drawable rippleOn(int color,int radius){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(darkTheme?56:38,Color.red(ink),Color.green(ink),Color.blue(ink))),shape(color,radius),shape(Color.WHITE,radius));}
    private AlertDialog.Builder dialog(){return new AlertDialog.Builder(this,darkTheme?android.R.style.Theme_DeviceDefault_Dialog_Alert:android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);}
    /** The Kadamm wordmark as a real button: 48dp tall, whole area opens the menu. */
    /** The logo follows the app theme: blue badge in light, black badge with a blue dot in dark. */
    private int logoRes(){return darkTheme?R.drawable.ic_kadamm_logo_dark:R.drawable.ic_kadamm_logo;}
    private LinearLayout brandButton(){
        LinearLayout b=row();b.setPadding(dp(8),0,dp(10),0);b.setBackground(rippleOn(Color.TRANSPARENT,14));b.setFocusable(true);b.setClickable(true);b.setContentDescription("Kadamm menu");b.setOnClickListener(v->openDrawer());
        android.widget.ImageView menu=new android.widget.ImageView(this);menu.setImageResource(logoRes());b.addView(menu,lp(36,36));TextView name=tx("KADAMM",16,ink,true);name.setLetterSpacing(.14f);LinearLayout.LayoutParams np=lp(-2,-2);np.leftMargin=dp(10);b.addView(name,np);
        TextView version=tx("v"+APP_VERSION,9,lime,true);version.setPadding(dp(7),dp(2),dp(7),dp(2));version.setBackground(shape(blend(panel,lime,darkTheme?.22f:.12f),10));LinearLayout.LayoutParams vp=lp(-2,-2);vp.leftMargin=dp(8);b.addView(version,vp);return b;
    }
    private static final String[] TABS={"Today","History","Profile"};private String renderedPage="";private boolean dragging=false;
    private int tabIndex(String p){return "Today".equals(p)?0:"History".equals(p)?1:"Profile".equals(p)?2:-1;}
    /** Slides the outgoing tab off and the incoming one in from the side it lives on. */
    private void slideTo(int dir){
        float w=Math.max(1,content.getWidth());content.animate().cancel();
        if(reducedMotion()){content.setTranslationX(0);content.setAlpha(1f);scroller.scrollTo(0,0);renderPageNow();return;}
        // Hardware layers make the fade and slide cheap however big the page is; the new page animates in only after
        // its first layout, so building it never eats into the animation's frames.
        content.animate().translationX(-dir*w*.5f).alpha(0f).setDuration(110).withLayer().withEndAction(()->{scroller.scrollTo(0,0);renderPageNow();content.setTranslationX(dir*w*.35f);content.setAlpha(0f);content.post(()->content.animate().translationX(0).alpha(1f).setDuration(220).setInterpolator(EASE).withLayer().start());}).start();
    }
    private final java.util.HashMap<String,Integer> scrollMemory=new java.util.HashMap<>();private int restoreScroll=-1;
    private void rememberScroll(){if(renderedPage!=null&&scroller!=null)scrollMemory.put(renderedPage,scroller.getScrollY());}
    private void renderPage(){rememberScroll();int from=tabIndex(renderedPage),to=tabIndex(page);if(from>=0&&to>=0&&from!=to&&content.getChildCount()>0){slideTo(to>from?1:-1);return;}content.setTranslationX(0);float motion=1f;try{motion=android.provider.Settings.Global.getFloat(getContentResolver(),"animator_duration_scale",1f);}catch(Exception ignored){}if(content.getChildCount()==0||motion<=0f){renderPageNow();return;}content.animate().cancel();content.animate().alpha(0f).setDuration(75).withLayer().withEndAction(()->{renderPageNow();content.setAlpha(0f);content.setTranslationY(dp(10));content.post(()->content.animate().alpha(1f).translationY(0).setDuration(200).setInterpolator(EASE).withLayer().start());}).start();}
    private void renderPageNow(){long perfStart=android.os.SystemClock.uptimeMillis();renderPageInner();if(restoreScroll>=0){int y=restoreScroll;restoreScroll=-1;scroller.post(()->scroller.scrollTo(0,y));}android.util.Log.d("KadammPerf",page+" built in "+(android.os.SystemClock.uptimeMillis()-perfStart)+" ms");}
    /**
     * Where Back goes: sub-pages remember the page they were opened from (Profile → Achievements → a session),
     * so Back and swipe-down return there instead of jumping to Today. Switching tabs starts a fresh trail.
     */
    private final java.util.ArrayDeque<String> backStack=new java.util.ArrayDeque<>();private boolean goingBack;
    private void trackBack(){
        if(page.equals(renderedPage)||renderedPage==null)return;
        if(tabIndex(page)>=0)backStack.clear();
        else if(!goingBack){backStack.remove(page);backStack.push(renderedPage);}
        goingBack=false;
    }
    private void goBack(){String to=backStack.isEmpty()?("Detail".equals(page)?"History":"Today"):backStack.pop();Integer y=scrollMemory.get(to);restoreScroll=y==null?-1:y;goingBack=true;page=to;closeDrawer();renderPage();}
    private String backLabel(){String to=backStack.isEmpty()?("Detail".equals(page)?"History":"Today"):backStack.peek();return "DaySummary".equals(to)?"Day summary":"Import".equals(to)?"Import":"Detail".equals(to)?"Session":"Auto".equals(to)?"Auto tracking":"Today".equals(to)?"Home":to;}
    private void renderPageInner(){trackBack();if(replayAnim!=null){replayAnim.cancel();replayAnim=null;}if(!page.equals(renderedPage)&&scroller!=null)scroller.scrollTo(0,0);renderedPage=page;content.removeAllViews();styleNavItem(navToday,page.equals("Today"));styleNavItem(navHistory,page.equals("History"));styleNavItem(navProfile,page.equals("Profile"));if(page.equals("Today"))renderToday();else if(page.equals("History"))renderHistory();else if(page.equals("Profile"))renderProfile();else if(page.equals("Goals"))renderGoals();else if(page.equals("Health"))renderHealth();else if(page.equals("Routes"))renderRoutes();else if(page.equals("Notifications"))renderNotifications();else if(page.equals("Reports"))renderReports();else if(page.equals("Achievements"))renderAchievements();else if(page.equals("Permissions"))renderPermissions();else if(page.equals("Auto"))renderAutoTracking();else if(page.equals("Storage"))renderStorage();else if(page.equals("Places"))renderPlaces();else if(page.equals("Sessions"))renderSessions();else if(page.equals("DaySummary"))renderDaySummary();else if(page.equals("Trainer"))renderTrainer();else if(page.equals("Import"))renderImport();else renderDetail(lastSession);if(content.getChildCount()>0)signatureAtEnd();}
    private void styleNavItem(LinearLayout item,boolean selected){int color=selected?lime:muted;item.setBackground(rippleOn(selected?raised:Color.TRANSPARENT,14));((android.widget.ImageView)item.getChildAt(0)).setImageTintList(android.content.res.ColorStateList.valueOf(color));((TextView)item.getChildAt(1)).setTextColor(selected?ink:muted);}
    private void brand(String eyebrow,String headline,String sub){LinearLayout brand=row();content.addView(brand,lp(-1,48));LinearLayout.LayoutParams bbp=lp(-2,48);bbp.leftMargin=-dp(8);brand.addView(brandButton(),bbp);if(tabIndex(page)<0){brand.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));TextView bk=tx("‹  "+backLabel(),13,ink,true);bk.setGravity(Gravity.CENTER);bk.setPadding(dp(14),0,dp(16),0);bk.setBackground(rippleOn(raised,18));bk.setContentDescription("Back to "+backLabel());bk.setOnClickListener(v->goBack());brand.addView(bk,lp(-2,36));}TextView e=tx(eyebrow.toUpperCase(Locale.US),10,muted,true);LinearLayout.LayoutParams ep=lp(-1,25);ep.topMargin=dp(8);content.addView(e,ep);TextView h=tx(headline,29,ink,true);content.addView(h);if(sub!=null){TextView s=tx(sub,13,muted,false);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(5);sp.bottomMargin=dp(16);content.addView(s,sp);}}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(panelShape(16));LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(12);content.addView(c,p);return c;}
    private GradientDrawable panelShape(int radius){GradientDrawable d=shape(panel,radius);d.setStroke(dp(1),line);return d;}
    private void renderToday(){JSONArray hist=history();dayTotals=DaySteps.allDayTotals(this);loadIdleLocation();renderHeader();renderStepSetup();renderBadgeBanner(hist);renderHero(hist);renderCoach();renderStepsByHour();renderTrainerCard(hist);renderPlan(hist);renderMovementQuality();renderSittingBreaks();renderBento(hist);renderDistanceGoal(hist);
        LinearLayout mapCard=card();trackCard=mapCard;LinearLayout head=row();TextView title=tx("Live route",16,ink,true);head.addView(title,new LinearLayout.LayoutParams(0,dp(26),1));status=tx("● READY",10,lime,true);head.addView(status);mapCard.addView(head);FrameLayout mapFrame=new FrameLayout(this);LinearLayout.LayoutParams mfp=lp(-1,190);mfp.topMargin=dp(8);mapCard.addView(mapFrame,mfp);route=new RouteView(this);route.setTheme(bg,raised,lime,darkTheme);route.setContentDescription("Map. Tap to open full screen");route.setOnClickListener(v->openMapView());mapFrame.addView(route,new FrameLayout.LayoutParams(-1,-1));android.widget.ImageView follow=icon(R.drawable.ic_my_location,ink,10);follow.setBackground(rippleOn(raised,12));FrameLayout.LayoutParams flp=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.RIGHT|Gravity.TOP);flp.setMargins(0,dp(10),dp(10),0);mapFrame.addView(follow,flp);follow.setContentDescription("Follow my location");follow.setOnClickListener(v->{route.setFollowLocation(true);if(prefs.getBoolean("gpsFix",false))route.setCurrentLocation(prefs.getFloat("latitude",0),prefs.getFloat("longitude",0),prefs.getFloat("accuracy",0));else toast("Waiting for a reliable GPS fix");});LinearLayout mapMeta=row();mapCard.addView(mapMeta,lp(-1,26));accuracyLabel=tx("GPS accuracy · waiting for signal",10,muted,false);mapMeta.addView(accuracyLabel,new LinearLayout.LayoutParams(0,dp(26),1));TextView standard=tx("Tap map to explore",10,lime,true);mapMeta.addView(standard);TextView mapHint=tx("© OpenStreetMap contributors · satellite tiles are not available from this map source",9,muted,false);mapCard.addView(mapHint);
        // Track an activity: four clear choices, one Start button; live numbers only while a session runs.
        boolean running=prefs.getBoolean("active",false);LinearLayout box=card();
        LinearLayout th=row();box.addView(th);LinearLayout tt=col();th.addView(tt,new LinearLayout.LayoutParams(0,-2,1));
        tt.addView(tx(running?"Session in progress":"Start an activity",17,ink,true));
        activityModeLabel=tx(running?"Detected automatically · tap to correct":"Outdoor or indoor · walk or run detected",11,muted,false);tt.addView(activityModeLabel);
        if(running){LinearLayout g1=row();LinearLayout.LayoutParams g1p=lp(-1,-2);g1p.topMargin=dp(12);box.addView(g1,g1p);
            activityChoice(g1,"Walking","Correct the type",R.drawable.ic_walk,prefs.getString("activityMode","outdoor"),"Walk");activityChoice(g1,"Running","Correct the type",R.drawable.ic_run,prefs.getString("activityMode","outdoor"),"Run");}
        else{LinearLayout how=row();how.setPadding(dp(12),dp(10),dp(12),dp(10));how.setBackground(shape(blend(panel,lime,darkTheme?.12f:.07f),14));LinearLayout.LayoutParams hwp=lp(-1,-2);hwp.topMargin=dp(12);box.addView(how,hwp);
            android.widget.ImageView hi=icon(R.drawable.ic_bolt,lime,4);how.addView(hi,lp(24,24));TextView ht=tx("Pick outdoor or indoor when you start. Kadamm tells walking from running by itself and colours the route to match.",11,ink,false);LinearLayout.LayoutParams htp2=new LinearLayout.LayoutParams(0,-2,1);htp2.leftMargin=dp(10);how.addView(ht,htp2);}
        TextView autoRow=tx((AutoTrack.enabled(this)?"●  Auto-record walks is on":"○  Auto-record walks and runs")+"  ›",12,AutoTrack.enabled(this)?positive:lime,true);autoRow.setGravity(Gravity.CENTER_VERTICAL);autoRow.setFocusable(true);autoRow.setBackground(rippleOn(Color.TRANSPARENT,10));LinearLayout.LayoutParams arp=lp(-1,40);arp.topMargin=dp(6);box.addView(autoRow,arp);autoRow.setOnClickListener(v->navigate("Auto"));
        trackStats=col();LinearLayout.LayoutParams tsp=lp(-1,-2);tsp.topMargin=dp(12);box.addView(trackStats,tsp);trackStats.setVisibility(running?View.VISIBLE:View.GONE);
        timer=tx("00:00",38,ink,false);timer.setTypeface(Typeface.create("monospace",0));trackStats.addView(timer,lp(-1,55));trackStats.addView(tx("ELAPSED TIME",10,muted,true));
        LinearLayout stats=row();LinearLayout.LayoutParams stp=lp(-1,72);stp.topMargin=dp(10);trackStats.addView(stats,stp);distance=value(stats,"DISTANCE","0.00 km");pace=value(stats,"AVG. PACE","— /km");steps=value(stats,"STEPS","0");
        LinearLayout stats2=row();trackStats.addView(stats2,lp(-1,66));liveSpeed=value(stats2,"SPEED","— km/h");liveElev=value(stats2,"ELEV. GAIN","— m");liveKcal=value(stats2,"CALORIES","0 kcal");
        start=button("▶  Start",lime,accentOn);start.setTextSize(16);LinearLayout.LayoutParams sbp=lp(-1,54);sbp.topMargin=dp(14);box.addView(start,sbp);start.setOnClickListener(v->onRecord());
        trackEnd=row();LinearLayout.LayoutParams tep=lp(-1,46);tep.topMargin=dp(8);box.addView(trackEnd,tep);trackEnd.setVisibility(running?View.VISIBLE:View.GONE);
        Button openLive=button("Live screen",raised,ink),finish=button("Finish",raised,ink),discard=button("Discard",raised,critical);
        trackEnd.addView(openLive,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-1,1);fp.leftMargin=dp(8);trackEnd.addView(finish,fp);LinearLayout.LayoutParams dsp=new LinearLayout.LayoutParams(0,-1,1);dsp.leftMargin=dp(8);trackEnd.addView(discard,dsp);
        openLive.setOnClickListener(v->openLive());finish.setOnClickListener(v->finishActivity());discard.setOnClickListener(v->discardActivity());
        renderDailyInsights(hist);renderReportsLink();renderTrackingHealth();
    }
    private void renderActivityInsights(JSONArray all){LinearLayout c=card();LinearLayout hd=row();hd.addView(tx("Activity overview",16,ink,true),new LinearLayout.LayoutParams(0,dp(28),1));c.addView(hd);c.addView(tx("Completed sessions · local workout data",11,muted,false));LinearLayout ranges=row();LinearLayout.LayoutParams rlp=lp(-1,38);rlp.topMargin=dp(10);c.addView(ranges,rlp);for(String range:new String[]{"7D","30D","12M","ALL"}){boolean selected=range.equals(activityRange);Button b=button(range,selected?lime:raised,selected?accentOn:ink);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(ranges.getChildCount()>0)bp.leftMargin=dp(5);ranges.addView(b,bp);b.setOnClickListener(v->{activityRange=range;renderPage();});}
        JSONArray visible=sessionsInRange(all,activityRange);double km=distanceAll(visible);long duration=0,totalSteps=0;for(int i=0;i<visible.length();i++){JSONObject o=visible.optJSONObject(i);if(o!=null){duration+=o.optLong("durationMs");if(o.optBoolean("stepsAvailable",false))totalSteps+=o.optInt("steps");}}LinearLayout row1=row();LinearLayout.LayoutParams st=lp(-1,58);st.topMargin=dp(15);c.addView(row1,st);summaryValue(row1,"SESSIONS",String.valueOf(visible.length()));summaryValue(row1,"DISTANCE",String.format(Locale.US,"%.2f km",km));LinearLayout row2=row();c.addView(row2,lp(-1,52));summaryValue(row2,"TIME",formatDuration(duration));summaryValue(row2,"VERIFIED STEPS",String.format(Locale.US,"%,d",totalSteps));TextView chartTitle=tx("Distance",10,muted,true);LinearLayout.LayoutParams ct=lp(-1,20);ct.topMargin=dp(9);c.addView(chartTitle,ct);ActivityChartView chart=new ActivityChartView(this,visible,activityRange,chartTheme());LinearLayout.LayoutParams ch=lp(-1,128);ch.topMargin=dp(3);c.addView(chart,ch);}
    private void renderDailyInsights(JSONArray all){
        Map<String,Long> totals=dayTotals==null?DaySteps.allDayTotals(this):dayTotals;int goal=dailyGoal();long sum=0;int hit=0;
        for(int i=0;i<7;i++){Calendar d=Calendar.getInstance();d.add(Calendar.DAY_OF_YEAR,-i);Long v=totals.get(dateKey(d));if(v!=null){sum+=v;if(v>=goal)hit++;}}
        LinearLayout weekly=card();LinearLayout wh=row();wh.addView(tx("This week",16,ink,true),new LinearLayout.LayoutParams(0,dp(26),1));wh.addView(tx(hit+" of 7 goals",11,hit>0?positive:muted,true));weekly.addView(wh);
        weekly.addView(tx(String.format(Locale.US,"%,d steps in the last 7 days · dashed line is your %,d goal",sum,goal),11,muted,false));
        WeeklyActivityView chart=new WeeklyActivityView(this,totals,goal,chartTheme());LinearLayout.LayoutParams cp=lp(-1,150);cp.topMargin=dp(8);weekly.addView(chart,cp);
        renderStreakCard(totals,goal);
        renderStepSources();
    }
    /** Goal streak: the current run as a linked chain of the last 14 days, best and total, and the next milestone. */
    private void renderStreakCard(Map<String,Long> totals,int goal){
        int[] st=DaySteps.streaks(totals,goal);long todayN=DaySteps.todayTotal(this);boolean todayDone=todayN>=goal;
        LinearLayout c=card();c.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,flame(),darkTheme?.16f:.09f),panel}){{setCornerRadius(dp(16));setStroke(dp(1),blend(line,flame(),.25f));}});
        LinearLayout head=row();c.addView(head);
        android.widget.ImageView ic=icon(R.drawable.ic_flame,darkTheme?Color.rgb(11,11,13):Color.WHITE,8);ic.setBackground(shape(flame(),14));head.addView(ic,lp(44,44));
        LinearLayout ht=col();LinearLayout.LayoutParams htp=new LinearLayout.LayoutParams(0,-2,1);htp.leftMargin=dp(12);head.addView(ht,htp);
        ht.addView(tx("GOAL STREAK",10,muted,true));
        LinearLayout big=row();big.setGravity(Gravity.BOTTOM);ht.addView(big);big.addView(tx(String.valueOf(st[0]),32,st[0]>0?ink:muted,true));TextView unit=tx(st[0]==1?" day":" days",14,muted,true);unit.setPadding(0,0,0,dp(6));big.addView(unit);
        TextView status=tx(todayDone?"● Today counts":"○ Today open",11,todayDone?positive:flame(),true);status.setPadding(dp(10),dp(5),dp(10),dp(5));status.setBackground(shape(blend(panel,todayDone?positive:flame(),.16f),12));head.addView(status);head.addView(shareIcon("streak",()->shareSheet("Share your streak",st[0]>0?String.format(Locale.US,"I'm on a %d-day goal streak on Kadamm, %,d steps a day.%s",st[0],goal,st[1]>st[0]?String.format(Locale.US," Best so far: %d days.",st[1]):" That's my best ever."):String.format(Locale.US,"Starting a new step streak on Kadamm: %,d steps a day. Longest so far: %d days.",goal,st[1]),null,null)),lp(40,40));
        String[] letters=new String[14];boolean[] hit=new boolean[14];
        for(int i=0;i<14;i++){Calendar d=Calendar.getInstance();d.add(Calendar.DAY_OF_YEAR,i-13);Long v=totals.get(dateKey(d));hit[i]=v!=null&&v>=goal;letters[i]=new SimpleDateFormat("EEEEE",Locale.getDefault()).format(d.getTime());}
        LinearLayout.LayoutParams chp=lp(-1,52);chp.topMargin=dp(14);c.addView(new MiniChartView(this,chartTheme(),MiniChartView.CHAIN,flame(),0).chain(hit,letters,13),chp);
        int[] marks={3,7,14,21,30,50,75,100,150,200,365};int next=0;for(int m:marks)if(m>st[0]){next=m;break;}
        LinearLayout stats=row();LinearLayout.LayoutParams stp=lp(-1,-2);stp.topMargin=dp(10);c.addView(stats,stp);
        streakStat(stats,"LONGEST",st[1]+(st[1]==1?" day":" days"));streakStat(stats,"GOAL DAYS",String.valueOf(st[2]));streakStat(stats,"NEXT BADGE",next==0?"—":next+" days");
        String line=todayDone?String.format(Locale.US,"Today's goal is done. Come back tomorrow to make it %d.",st[0]+1):st[0]>0?String.format(Locale.US,"%,d more steps today keeps your %d-day streak going.",goal-todayN,st[0]):String.format(Locale.US,"Reach %,d steps today to start a new streak.",goal);
        TextView note=tx(line,12,ink,false);LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(10);c.addView(note,np);
    }
    private void streakStat(LinearLayout p,String label,String value){LinearLayout c=col();c.setPadding(dp(10),dp(8),dp(8),dp(8));c.setBackground(shape(darkTheme?Color.argb(40,255,255,255):Color.argb(160,255,255,255),12));LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1);if(p.getChildCount()>0)lp2.leftMargin=dp(8);p.addView(c,lp2);c.addView(tx(label,9,muted,true));c.addView(tx(value,15,ink,true));}
    private void renderStepSources(){
        LinearLayout c=card();c.addView(tx("Where today's steps come from",16,ink,true));
        boolean present=DaySteps.sensorPresent(this),allowed=DaySteps.permitted(this);long imported=DaySteps.healthImportedToday(this);
        healthRow(c,"PHONE SENSOR",!present?"Not on this phone":!allowed?"Permission needed":String.format(Locale.US,"%,d steps",DaySteps.phoneOn(this,todayKey())),present&&allowed?positive:warning);
        healthRow(c,"HEALTH CONNECT",imported<0?"Not connected":imported==0?"Connected · none shared":String.format(Locale.US,"%,d steps",imported),imported>0?positive:imported==0?warning:muted);
        healthRow(c,"KADAMM SESSIONS",String.format(Locale.US,"%,d steps",DaySteps.sessionOn(this,todayKey())),muted);
        c.addView(tx("Kadamm shows the most complete count, never the sum of sources, so no step is counted twice.",10,muted,false));
        SharedPreferences hs=getSharedPreferences("kadamm_health",MODE_PRIVATE);long at=hs.getLong("healthSyncedAt",0);
        TextView syncRow=tx(HealthSync.isRunning()?"Syncing Health Connect…":imported<0?"Health Connect not connected":"Last synced "+(at>0?new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date(at)):"never")+"  ·  Sync now",12,imported<0?muted:lime,true);syncRow.setGravity(Gravity.CENTER_VERTICAL);syncRow.setFocusable(true);syncRow.setBackground(rippleOn(Color.TRANSPARENT,10));c.addView(syncRow,lp(-1,48));
        syncRow.setOnClickListener(v->{if(!hs.getBoolean("healthConnected",false)){navigate("Health");return;}syncRow.setText("Syncing Health Connect…");syncing=true;refreshHealth(true);});
        TextView manage=tx("Manage step sources  →",13,lime,true);manage.setGravity(Gravity.CENTER_VERTICAL);manage.setFocusable(true);c.addView(manage,lp(-1,40));manage.setOnClickListener(v->navigate("Health"));
    }
    private int todaySteps(JSONArray all){return (int)Math.min(Integer.MAX_VALUE,DaySteps.todayTotal(this));}
    private String stepDisplay(JSONArray all,String day){if(day.equals(todayKey()))return hasStepData(all,day)?String.format(Locale.US,"%,d",todaySteps(all)):"—";Long n=dayTotals==null?null:dayTotals.get(day);return n==null?"—":String.format(Locale.US,"%,d",n);}
    private boolean hasStepData(JSONArray all,String day){if(day.equals(todayKey()))return DaySteps.hasTodayData(this);return dayTotals!=null&&dayTotals.containsKey(day);}
    private int todayStepsByType(JSONArray all,String type){long n=0;for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&dateKey(s).equals(todayKey())&&s.optBoolean("stepsAvailable",false)){String t=s.optString("type","Walk");if(type.equals(t)||("Mixed".equals(t)&&s.optLong(type.toLowerCase(Locale.US)+"DurationMs",0)>0))n+=s.optLong("steps",0);}}if(prefs.getBoolean("active",false)&&type.equals(prefs.getString("currentType","Walk")))n+=prefs.getInt("steps",0);return (int)n;}
    private long todayStepsFor(JSONArray all,String day){long n=0;for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&dateKey(s).equals(day)&&s.optBoolean("stepsAvailable",false))n+=s.optLong("steps",0);}return n;}
    private int todayCalories(JSONArray all){int n=0;for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&dateKey(s).equals(todayKey()))n+=s.optInt("calories",(int)Math.round(s.optDouble("distanceKm")*65));}n+=Calories.live(this);return n;}
    private long todayDuration(JSONArray all){long n=0;for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&dateKey(s).equals(todayKey()))n+=s.optLong("durationMs",0);}if(prefs.getBoolean("active",false))n+=prefs.getLong("elapsed",0);return n;}
    private double distanceByType(JSONArray all,String day,String type){double n=0;for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&dateKey(s).equals(day)){String key=type.toLowerCase(Locale.US)+"DistanceKm";if(s.has(key))n+=s.optDouble(key);else if(type.equals(s.optString("type","Walk")))n+=s.optDouble("distanceKm");}}if(day.equals(todayKey())&&prefs.getBoolean("active",false))n+=prefs.getFloat(type.toLowerCase(Locale.US)+"DistanceKm",0);return n;}
    private TextView summaryValue(LinearLayout parent,String label,String value){LinearLayout box=col();parent.addView(box,new LinearLayout.LayoutParams(0,-1,1));box.addView(tx(label,9,muted,true));TextView v=tx(value,16,ink,true);box.addView(v);return v;}
    private JSONArray sessionsInRange(JSONArray all,String range){if("ALL".equals(range))return all;Calendar now=Calendar.getInstance();long end=now.getTimeInMillis();Calendar start=(Calendar)now.clone();if("30D".equals(range))start.add(Calendar.DAY_OF_YEAR,-29);else if("12M".equals(range))start.add(Calendar.MONTH,-11);else start.add(Calendar.DAY_OF_YEAR,-6);start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);JSONArray out=new JSONArray();for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s==null)continue;try{Date d=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(s.optString("day",""));if(d!=null&&d.getTime()>=start.getTimeInMillis()&&d.getTime()<=end)out.put(s);}catch(Exception ignored){}}return out;}
    private KadammTheme chartTheme(){return KadammTheme.resolve(this,getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("themeMode","system"));}
    /** Tracking readiness in one compact row of status pills; tap for the Permissions page. */
    private void renderTrackingHealth(){
        boolean location=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        boolean stepsOk=DaySteps.sensorPresent(this)&&DaySteps.permitted(this),notify=notificationsAllowed(),health=getSharedPreferences("kadamm_health",MODE_PRIVATE).getBoolean("healthConnected",false);
        android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);boolean battery=pm!=null&&pm.isIgnoringBatteryOptimizations(getPackageName());
        int missing=(location?0:1)+(stepsOk?0:1)+(notify?0:1)+(battery?0:1);
        LinearLayout c=card();c.setPadding(dp(14),dp(12),dp(14),dp(12));GradientDrawable g=shape(panel,16);g.setStroke(dp(1),line);c.setBackground(ripple(g));c.setFocusable(true);c.setOnClickListener(v->navigate("Permissions"));
        LinearLayout h=row();c.addView(h);h.addView(tx(missing==0?"Ready to track":missing+(missing==1?" thing to fix":" things to fix"),13,missing==0?positive:warning,true),new LinearLayout.LayoutParams(0,-2,1));h.addView(tx("›",20,muted,false));
        LinearLayout pills=row();LinearLayout.LayoutParams pp=lp(-1,-2);pp.topMargin=dp(8);c.addView(pills,pp);
        String[] names={"GPS","Steps","Alerts","Battery","Health"};boolean[] ok={location,stepsOk,notify,battery,health};
        for(int i=0;i<names.length;i++){int col=ok[i]?positive:i==4?muted:warning;TextView t=tx("● "+names[i],10,col,true);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(4),0,dp(4));t.setBackground(shape(blend(panel,col,.12f),10));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)tp.leftMargin=dp(5);pills.addView(t,tp);}
    }
    private void healthRow(LinearLayout c,String label,String value,int state){LinearLayout r=row();LinearLayout.LayoutParams p=lp(-1,34);c.addView(r,p);r.addView(tx(label,10,muted,true),new LinearLayout.LayoutParams(0,-2,1));r.addView(tx("●  "+value,11,state,true));}
    private TextView metric(LinearLayout parent,String label,String value,String unit){LinearLayout c=col();c.setPadding(dp(12),dp(13),dp(8),dp(9));c.setBackground(panelShape(14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(94),1);if(parent.getChildCount()>0)p.leftMargin=dp(8);parent.addView(c,p);c.addView(tx(label,9,muted,true));TextView v=tx(value,24,ink,true);LinearLayout.LayoutParams vp=lp(-1,34);vp.topMargin=dp(4);c.addView(v,vp);c.addView(tx(unit,10,muted,false));if(label.startsWith("TODAY"))todayCount=v;else if(label.equals("DISTANCE"))todayDistance=v;else if(label.equals("ACTIVE TIME"))todayActiveTime=v;else if(label.equals("WALKING"))todayWalkDistance=v;else if(label.equals("RUNNING"))todayRunDistance=v;return v;}
    private TextView value(LinearLayout parent,String label,String val){LinearLayout c=col();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);parent.addView(c,p);c.addView(tx(label,9,muted,true));TextView v=tx(val,17,ink,false);v.setTypeface(Typeface.create("monospace",0));LinearLayout.LayoutParams vp=lp(-1,34);vp.topMargin=dp(5);c.addView(v,vp);return v;}
    private Button button(String s,int color,int textColor){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(textColor);GradientDrawable base=shape(color,11);base.setStroke(dp(1),line);b.setBackground(ripple(base));b.setOnFocusChangeListener((v,has)->{GradientDrawable d=shape(color,11);d.setStroke(dp(has?2:1),has?focus:line);b.setBackground(ripple(d));});return b;}private Button smallChoice(String s,boolean selected){int selectedColor="Run".equals(s)?violet:lime;Button b=button(s,selected?selectedColor:raised,selected?accentOn:ink);return b;}
    private void renderHistory(){
        dayTotals=DaySteps.allDayTotals(this);brand("YOUR ACTIVITY","History","Tap a day to see its steps and sessions.");
        JSONArray h=history();int goal=dailyGoal();Calendar now=Calendar.getInstance();
        LinearLayout month=row();content.addView(month,lp(-1,52));
        TextView prev=tx("‹",22,ink,false),next=tx("›",22,ink,false);
        for(TextView b:new TextView[]{prev,next}){b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setBackground(rippleOn(raised,22));}
        prev.setContentDescription("Previous month");next.setContentDescription("Next month");
        month.addView(prev,lp(44,44));TextView title=tx(monthName(),18,ink,true);title.setGravity(Gravity.CENTER);month.addView(title,new LinearLayout.LayoutParams(0,-2,1));month.addView(next,lp(44,44));
        prev.setOnClickListener(v->{shownMonth.add(Calendar.MONTH,-1);renderPage();});next.setOnClickListener(v->{shownMonth.add(Calendar.MONTH,1);renderPage();});
        Calendar first=(Calendar)shownMonth.clone();first.set(Calendar.DAY_OF_MONTH,1);int days=first.getActualMaximum(Calendar.DAY_OF_MONTH);
        long monthSteps=0;int goalDays=0,activeDays=0;double monthKm=0;
        for(int d=1;d<=days;d++){Calendar x=(Calendar)first.clone();x.set(Calendar.DAY_OF_MONTH,d);String k=dateKey(x);Long v=dayTotals.get(k);if(v!=null&&v>0){monthSteps+=v;activeDays++;if(v>=goal)goalDays++;}monthKm+=Math.max(distanceOnDay(h,k),v==null?0:DaySteps.stepKm(this,v));}
        LinearLayout stats=row();LinearLayout.LayoutParams stp=lp(-1,-2);stp.topMargin=dp(6);content.addView(stats,stp);
        monthStat(stats,"STEPS",monthSteps>=10000?String.format(Locale.US,"%.0fk",monthSteps/1000d):String.format(Locale.US,"%,d",monthSteps));
        monthStat(stats,"GOAL DAYS",goalDays+" / "+activeDays);monthStat(stats,"DISTANCE",String.format(Locale.US,"%.1f km",monthKm));
        // The month as a landscape: peaks above the goal horizon are goal days.
        long[] mv=new long[days];int lastDay=-1;for(int d=1;d<=days;d++){Calendar x=(Calendar)first.clone();x.set(Calendar.DAY_OF_MONTH,d);if(x.after(now)&&!isSameDate(x,now))break;lastDay=d-1;Long v=dayTotals.get(dateKey(x));mv[d-1]=v==null?0:v;}
        if(lastDay>=2){LinearLayout land=card();LinearLayout lh=row();land.addView(lh);lh.addView(tx("Month at a glance",15,ink,true),new LinearLayout.LayoutParams(0,-2,1));lh.addView(tx("touch to read a day",10,muted,false));
            LinearLayout.LayoutParams lvp=lp(-1,190);lvp.topMargin=dp(8);land.addView(new LandscapeView(this,chartTheme(),mv,goal,lastDay),lvp);
            long best=0,sum=0;int counted=0;for(int i=0;i<=lastDay;i++){sum+=mv[i];if(mv[i]>0)counted++;best=Math.max(best,mv[i]);}
            LinearLayout ls=row();LinearLayout.LayoutParams lsp=lp(-1,-2);lsp.topMargin=dp(10);land.addView(ls,lsp);
            chartStat(ls,"DAILY AVERAGE",String.format(Locale.US,"%,d",counted>0?sum/counted:0));chartStat(ls,"GOAL DAYS",goalDays+" of "+(lastDay+1));chartStat(ls,"BEST DAY",String.format(Locale.US,"%,d",best));}
        LinearLayout cal=card();LinearLayout dr=row();cal.addView(dr);
        for(String d:new String[]{"M","T","W","T","F","S","S"}){TextView t=tx(d,11,muted,true);t.setGravity(Gravity.CENTER);dr.addView(t,new LinearLayout.LayoutParams(0,dp(28),1));}
        int offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7,weeks=(offset+days+6)/7;KadammTheme theme=chartTheme();
        for(int wk=0;wk<weeks;wk++){
            LinearLayout rr=row();cal.addView(rr,lp(-1,50));
            for(int col=0;col<7;col++){
                int n=wk*7+col-offset+1;
                if(n<1||n>days){rr.addView(new View(this),new LinearLayout.LayoutParams(0,-1,1));continue;}
                Calendar date=(Calendar)shownMonth.clone();date.set(Calendar.DAY_OF_MONTH,n);String key=dateKey(date);Long v=dayTotals.get(key);
                int sessions=countOn(h,key,null);boolean future=date.after(now)&&!isSameDate(date,now);
                // Goal days next to goal days in the same week row join into one chain.
                Calendar pd=(Calendar)date.clone();pd.add(Calendar.DAY_OF_YEAR,-1);Calendar nd=(Calendar)date.clone();nd.add(Calendar.DAY_OF_YEAR,1);Long pv=dayTotals.get(dateKey(pd)),nv=dayTotals.get(dateKey(nd));
                boolean hit=v!=null&&v>=goal,linkL=hit&&col>0&&n>1&&pv!=null&&pv>=goal,linkR=hit&&col<6&&n<days&&nv!=null&&nv>=goal&&!nd.after(now);
                DayCellView cell=new DayCellView(this,theme,n,v==null?-1f:v/(float)Math.max(1,goal),isSameDate(date,now),isSameDate(date,selectedDate),sessions>0,future,linkL,linkR);
                cell.setFocusable(true);cell.setContentDescription(new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(date.getTime())+(v==null?", no step data":String.format(Locale.US,", %,d steps",v))+", "+sessions+(sessions==1?" session":" sessions"));
                cell.setOnClickListener(x->{selectedDate=(Calendar)date.clone();renderPageNow();});
                cell.setOnLongClickListener(x->{showSessionCountPopup(cell,date,sessions);return true;});
                rr.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
            }
        }
        LinearLayout legend=row();LinearLayout.LayoutParams lgp=lp(-1,-2);lgp.topMargin=dp(10);cal.addView(legend,lgp);
        int chain=0,run2=0;for(int d=0;d<=lastDay;d++){if(mv[d]>=goal){run2++;chain=Math.max(chain,run2);}else run2=0;}
        for(String[] lg:new String[][]{{"▬","Goal day · joined = streak"},{"▁","Progress"},{"•","Session"}}){int col=lg[1].startsWith("Goal")?positive:lg[1].startsWith("Progress")?lime:muted;TextView a=tx(lg[0]+" ",12,col,true);legend.addView(a);TextView b2=tx(lg[1],10,muted,false);LinearLayout.LayoutParams bp2=lp(-2,-2);bp2.rightMargin=dp(12);legend.addView(b2,bp2);}
        int[] st=DaySteps.streaks(dayTotals,goal);
        TextView streak=tx(String.format(Locale.US,"Streak %d %s  ·  best %d  ·  month's longest chain %d",st[0],st[0]==1?"day":"days",st[1],chain),12,lime,true);LinearLayout.LayoutParams srp=lp(-1,-2);srp.topMargin=dp(6);cal.addView(streak,srp);
        String sk=dateKey(selectedDate);Long sv=dayTotals.get(sk);long sSteps=sk.equals(todayKey())?DaySteps.todayTotal(this):(sv==null?-1:sv);
        LinearLayout sel=card();LinearLayout sh=row();sel.addView(sh);
        sh.addView(tx(new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(selectedDate.getTime()),15,ink,true),new LinearLayout.LayoutParams(0,-2,1));
        if(isSameDate(selectedDate,now)){TextView badge=tx("TODAY",9,lime,true);badge.setPadding(dp(8),dp(3),dp(8),dp(3));badge.setBackground(shape(blend(panel,lime,darkTheme?.22f:.12f),10));sh.addView(badge);}
        LinearLayout big=row();LinearLayout.LayoutParams bgp=lp(-1,-2);bgp.topMargin=dp(10);sel.addView(big,bgp);
        big.addView(tx(sSteps<0?"—":String.format(Locale.US,"%,d",sSteps),30,ink,true));
        TextView of=tx(sSteps<0?"  no step data":String.format(Locale.US,"  of %,d steps · %d%%",goal,Math.round(sSteps*100f/Math.max(1,goal))),12,muted,false);big.addView(of);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,goal));bar.setProgress((int)Math.max(0,Math.min(goal,sSteps)));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(lime));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(raised));
        LinearLayout.LayoutParams bp=lp(-1,8);bp.topMargin=dp(8);bp.bottomMargin=dp(6);sel.addView(bar,bp);
        double km=Math.max(totalDistanceOn(h,sk),sk.equals(todayKey())?DaySteps.distanceToday(this)[0]:DaySteps.stepKm(this,Math.max(0,sSteps)));long ms=durationOn(h,sk);int count=countOn(h,sk,null);
        detailRow(sel,"DISTANCE",String.format(Locale.US,"%.2f km",km),"ACTIVE TIME",formatDuration(ms));
        long dayKcal=0;for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o!=null&&dateKey(o).equals(sk))dayKcal+=o.optLong("calories",Math.round(o.optDouble("distanceKm",0)*65));}detailRow(sel,"SESSION CALORIES",dayKcal+" kcal active","SESSIONS",String.valueOf(count));
        // Sessions live behind one stacked card; this day's are opened there.
        renderSessionsEntry(h);
    }
    /**
     * Sessions saved before 8.6 that today's rules would not keep (under 100 steps, or a ride). Offered for
     * removal; nothing is deleted until you confirm.
     */
    private void renderTidyUp(JSONArray h){
        java.util.List<Long> drop=new java.util.ArrayList<>();
        for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o==null)continue;boolean few=o.optBoolean("stepsAvailable",false)&&o.optLong("steps",0)<SessionRules.MIN_STEPS;if(few||Insights.vehicle(o))drop.add(o.optLong("id"));}
        if(drop.isEmpty())return;
        LinearLayout c=card();GradientDrawable g=shape(blend(panel,warning,darkTheme?.12f:.07f),16);g.setStroke(dp(1),blend(line,warning,.35f));c.setBackground(g);
        c.addView(tx("Tidy up "+drop.size()+(drop.size()==1?" session":" sessions"),15,ink,true));
        c.addView(tx("These were saved before Kadamm's smarter rules: each has under "+SessionRules.MIN_STEPS+" steps or was a ride. Their steps still count toward their days either way.",12,muted,false));
        Button b=button("Review and remove",raised,ink);LinearLayout.LayoutParams bp=lp(-1,44);bp.topMargin=dp(10);c.addView(b,bp);
        b.setOnClickListener(v->dialog().setTitle("Remove "+drop.size()+(drop.size()==1?" session?":" sessions?")).setMessage("They'll be deleted from History and can't be brought back. Daily step totals don't change.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{
            try{JSONArray kept=new JSONArray();for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o!=null&&!drop.contains(o.optLong("id")))kept.put(o);}historyPrefs.edit().putString("sessions",kept.toString()).apply();toast(drop.size()+" removed");renderPageNow();}catch(Exception ignored){}
        }).show());
    }
    private boolean scrollToSessions;
    private double distanceOnDay(JSONArray h,String day){double n=0;for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o!=null&&dateKey(o).equals(day))n+=o.optDouble("distanceKm",0);}return n;}
    private void monthStat(LinearLayout parent,String label,String value){
        LinearLayout c=col();c.setPadding(dp(12),dp(10),dp(10),dp(10));GradientDrawable g=shape(panel,16);g.setStroke(dp(1),line);c.setBackground(g);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(parent.getChildCount()>0)p.leftMargin=dp(8);parent.addView(c,p);
        c.addView(tx(label,9,muted,true));c.addView(tx(value,17,ink,true));
    }
    private int tint(int color){return Color.argb(darkTheme?105:75,Color.red(color),Color.green(color),Color.blue(color));}
    private int calendarColor(JSONArray h,Calendar d){String key=dateKey(d);Long v=dayTotals==null?null:dayTotals.get(key);long count=v==null?0:v;int goal=dailyGoal();if(count>=goal)return lime;if(count>=goal/2)return Color.argb(darkTheme?150:120,Color.red(lime),Color.green(lime),Color.blue(lime));if(count>0)return Color.argb(darkTheme?70:45,Color.red(lime),Color.green(lime),Color.blue(lime));return countOn(h,key,null)>0?raised:Color.TRANSPARENT;}
    private double runDistanceOn(JSONArray h,String day){return distanceByType(h,day,"Run");}
    private long durationByType(JSONArray h,String day,String type){long n=0;String key=type.toLowerCase(Locale.US)+"DurationMs";for(int i=0;i<h.length();i++){JSONObject s=h.optJSONObject(i);if(s!=null&&dateKey(s).equals(day)){if(s.has(key))n+=s.optLong(key);else if(type.equals(s.optString("type","Walk")))n+=s.optLong("durationMs");}}if(day.equals(todayKey())&&prefs.getBoolean("active",false))n+=prefs.getLong(key,0);return n;}
    private long durationOn(JSONArray h,String day){long n=0;for(int i=0;i<h.length();i++){JSONObject s=h.optJSONObject(i);if(s!=null&&dateKey(s).equals(day))n+=s.optLong("durationMs",0);}return n;}
    private void showSessionCountPopup(View anchor,Calendar date,int count){LinearLayout bubble=col();bubble.setPadding(dp(14),dp(10),dp(14),dp(10));bubble.setBackground(panelShape(14));String label=isSameDate(date,Calendar.getInstance())?"TODAY'S SESSIONS":new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(date.getTime()).toUpperCase(Locale.getDefault());bubble.addView(tx(label,9,muted,true));TextView amount=tx(count+" "+(count==1?"session":"sessions"),16,ink,true);LinearLayout.LayoutParams ap=lp(-1,26);ap.topMargin=dp(2);bubble.addView(amount,ap);PopupWindow popup=new PopupWindow(bubble,dp(160),dp(62),true);popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));popup.setElevation(dp(10));popup.setOutsideTouchable(true);popup.showAsDropDown(anchor,0,-dp(70),Gravity.CENTER_HORIZONTAL);}
    private void renderHealth(){
        brand("STEP SOURCES","Your daily steps","Kadamm counts with this phone on its own. Health Connect is an optional extra.");
        boolean present=DaySteps.sensorPresent(this),allowed=DaySteps.permitted(this);long since=DaySteps.startedAt(this);
        LinearLayout ph=card();ph.addView(tx("Phone step sensor",16,ink,true));
        TextView state=tx(!present?"○  Not available on this phone":allowed?"●  Counting all day":"○  Permission needed",14,present&&allowed?positive:warning,true);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(6);ph.addView(state,sp);
        if(present&&allowed){
            String from=since<=0?"":" · counting since "+new SimpleDateFormat(DaySteps.key(since).equals(todayKey())?"h:mm a":"d MMM",Locale.getDefault()).format(new Date(since));
            ph.addView(tx(String.format(Locale.US,"%,d steps today",DaySteps.phoneOn(this,todayKey()))+from,12,muted,false));
            ph.addView(tx("Uses the motion sensor built into your phone, so it works without a watch or any other app and keeps counting while Kadamm is closed. Steps you took before Kadamm started counting are not included.",11,muted,false));
        }else if(present){
            ph.addView(tx("Android needs your permission for Physical activity before Kadamm can read the step sensor.",12,muted,false));
            Button allow=button("Allow step counting",lime,accentOn);LinearLayout.LayoutParams ap=lp(-1,46);ap.topMargin=dp(12);ph.addView(allow,ap);allow.setOnClickListener(v->requestStepPermission());
        }else ph.addView(tx("This phone has no hardware step counter. Connect Health Connect below, or record sessions to count steps.",12,muted,false));

        SharedPreferences hp=getSharedPreferences("kadamm_health",MODE_PRIVATE);boolean connected=hp.getBoolean("healthConnected",false);long imported=DaySteps.healthImportedToday(this);
        String sync=hp.getLong("healthSyncedAt",0)>0?new SimpleDateFormat("d MMM, h:mm a",Locale.getDefault()).format(new Date(hp.getLong("healthSyncedAt",0))):"Never";
        StringBuilder srcB=new StringBuilder();for(String pkg:hp.getString("healthSources","").split(",")){if(pkg.trim().isEmpty())continue;String l=HealthSync.label(pkg.trim());if(srcB.indexOf(l)<0){if(srcB.length()>0)srcB.append(", ");srcB.append(l);}}String sources=srcB.toString();
        LinearLayout c=card();c.addView(tx("Health Connect",16,ink,true));
        TextView hs=tx(connected?"●  Connected":"○  Not connected",14,connected?positive:muted,true);LinearLayout.LayoutParams hsp=lp(-1,-2);hsp.topMargin=dp(6);c.addView(hs,hsp);
        if(connected){
            c.addView(tx(imported>0?String.format(Locale.US,"%,d steps shared today",imported)+(sources.isEmpty()?"":" · from "+sources):"No steps shared today yet",12,muted,false));
            c.addView(tx("Last sync: "+sync+" · refreshes each time you open Kadamm",12,muted,false));
            if(imported<=0)c.addView(tx("Samsung Health keeps its count private until you allow sharing: Samsung Health > Settings > Health Connect > Steps.",11,warning,false));
        }else c.addView(tx("Adds steps counted by Samsung Health and other apps, and fills in up to 30 past days on your calendar.",12,muted,false));
        settingSwitch(c,"Use Health Connect steps","Adds steps from Samsung Health and other apps. Turn off to count only with this phone and your Kadamm sessions; useful when Samsung Health counts steps while you ride.","hcSteps",true,
            "Stop using Health Connect steps?","Kadamm will count only with this phone's step sensor and your sessions. Steps from Samsung Health, including any counted in a vehicle, won't be added, and minute-by-minute charts will use Kadamm's own data. You can turn it back on any time.",
            "Use Health Connect steps?","Kadamm will add steps from Samsung Health and other apps, taking the most complete count each day (never adding sources together).",()->refreshHealth(true));
        settingSwitch(c,"Heart rate from a watch","Reads heart rate a watch writes to Health Connect for each session: zones and a more exact effort.","hcHeart",true,
            "Stop reading heart rate?","New sessions won't get heart rate from your watch. Heart rate already saved stays.",
            "Read heart rate?","Kadamm will read heart rate for your sessions from Health Connect, about 30 minutes after each one ends.",null);
        Button open=button(connected?"Health Connect permissions":"Connect Health Connect",connected?raised:lime,connected?ink:accentOn);LinearLayout.LayoutParams bp=lp(-1,46);bp.topMargin=dp(12);c.addView(open,bp);
        Button imp=button("Import past walks and steps",raised,ink);LinearLayout.LayoutParams ip=lp(-1,46);ip.topMargin=dp(8);c.addView(imp,ip);imp.setOnClickListener(v->navigate("Import"));
        open.setOnClickListener(v->{try{startActivity(new Intent(this,HealthConnectActivity.class));}catch(Exception e){toast("Health Connect could not be opened");}});

        LinearLayout n=card();n.addView(tx("How the total is worked out",15,ink,true));
        n.addView(tx("Your phone sensor and Health Connect both measure the whole day, so Kadamm shows whichever is more complete instead of adding them together. Health Connect steps are read only from outside your Kadamm sessions; the session's own steps fill those windows.",12,muted,false));
    }
    private void renderGoals(){
        brand("PERSONALISE","Goals & targets","Shape what a good day looks like. Past days keep the results they earned.");
        SharedPreferences st=getSharedPreferences("kadamm_settings",MODE_PRIVATE);JSONArray h=history();
        LinearLayout sum=card();sum.addView(tx("TODAY AGAINST YOUR GOALS",10,muted,true));
        goalProgress(sum,"Steps",DaySteps.todayTotal(this),st.getInt("stepGoal",10000),lime,String.format(Locale.US,"%,d / %,d",DaySteps.todayTotal(this),st.getInt("stepGoal",10000)));
        goalProgress(sum,"Distance",Math.round(DaySteps.distanceToday(this)[0]*1000),st.getInt("distanceGoalM",5000),orange(),String.format(Locale.US,"%.2f / %.1f km",totalDistanceOn(h,todayKey()),st.getInt("distanceGoalM",5000)/1000f));
        goalProgress(sum,"Move minutes",todayDuration(h)/60000,st.getInt("activeGoalMin",30),violet,String.format(Locale.US,"%d / %d min",todayDuration(h)/60000,st.getInt("activeGoalMin",30)));
        goalCard("Daily steps","Your main goal. The outer ring fills toward it.","▶",lime,"stepGoal",10000,1000,50000,500,new int[]{6000,8000,10000,12000,15000},v->String.format(Locale.US,"%,d steps",v),v->(v/1000)+"k");
        goalCard("Daily distance","From GPS sessions and indoor estimates.","↗",orange(),"distanceGoalM",5000,500,30000,500,new int[]{3000,5000,8000,10000},v->String.format(Locale.US,"%.1f km",v/1000f),v->(v/1000)+" km");
        goalCard("Brisk minutes","Minutes at 100 or more steps a minute, a moderate walk. The middle (green) ring on Home.","◷",green(),"briskGoalMin",30,10,120,5,new int[]{20,30,45,60},v->v+" min a day",v->v+" min");
        goalCard("Indoor stride length","Turns treadmill steps into distance. Walk 10 m, count your steps, and divide.","⇅",teal(),"strideLengthCm",72,40,130,1,new int[]{65,72,80},v->String.format(Locale.US,"%.2f m per step",v/100f),v->v+" cm");
        goalCard("Body weight","Used to estimate the calories of your sessions. Stays on this phone.","◉p",indigo(),"weightKg",70,35,180,1,new int[]{55,65,75,85},v->v+" kg",v->v+" kg");
        goalCard("Age","Sets your maximum heart rate (208 − 0.7 × age) for heart-rate zones when a watch adds heart rate. Stays on this phone.","♥",critical,"ageYears",30,12,90,1,new int[]{25,30,40,50},v->v+" years",v->v+" years");
        Button health=button("Connect health data  →",raised,ink);LinearLayout.LayoutParams hp=lp(-1,50);hp.topMargin=dp(16);content.addView(health,hp);health.setOnClickListener(v->navigate("Health"));
    }
    private void goalProgress(LinearLayout c,String label,long value,int goal,int color,String text){
        LinearLayout r=row();LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(12);c.addView(r,rp);
        r.addView(tx(label,13,ink,true),new LinearLayout.LayoutParams(0,-2,1));r.addView(tx(text,12,value>=goal?positive:muted,true));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,goal));bar.setProgress((int)Math.min(goal,value));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(value>=goal?positive:color));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(raised));
        LinearLayout.LayoutParams bp=lp(-1,8);bp.topMargin=dp(6);c.addView(bar,bp);
    }
    /** One goal: colour badge, big value with − / + steppers, a tinted slider and quick presets. Saves as it changes. */
    private void goalCard(String title,String desc,String icon,int color,String key,int def,int min,int max,int step,int[] presets,java.util.function.IntFunction<String> fmt,java.util.function.IntFunction<String> chip){
        SharedPreferences st=getSharedPreferences("kadamm_settings",MODE_PRIVATE);
        LinearLayout c=card();LinearLayout head=row();c.addView(head);
        android.widget.ImageView badge=icon(iconRes(icon),darkTheme?Color.rgb(11,11,13):Color.WHITE,9);badge.setBackground(shape(color,14));head.addView(badge,lp(40,40));
        LinearLayout words=col();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(12);head.addView(words,wp);words.addView(tx(title,16,ink,true));words.addView(tx(desc,11,muted,false));
        LinearLayout vr=row();LinearLayout.LayoutParams vrp=lp(-1,52);vrp.topMargin=dp(12);c.addView(vr,vrp);
        Button minus=button("−",raised,ink);minus.setTextSize(20);vr.addView(minus,lp(52,48));
        TextView value=tx(fmt.apply(st.getInt(key,def)),24,color,true);value.setGravity(Gravity.CENTER);vr.addView(value,new LinearLayout.LayoutParams(0,-2,1));
        Button plus=button("+",raised,ink);plus.setTextSize(20);vr.addView(plus,lp(52,48));
        java.util.function.IntConsumer[] set=new java.util.function.IntConsumer[1];
        android.widget.SeekBar seek=SeekBarControl.add(this,c,st.getInt(key,def),min,max,v->set[0].accept(v));
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(color));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(color));seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(raised));
        LinearLayout chips=row();LinearLayout.LayoutParams cp=lp(-1,36);cp.topMargin=dp(4);c.addView(chips,cp);Button[] buttons=new Button[presets.length];
        Runnable paint=()->{int now=st.getInt(key,def);for(int i=0;i<presets.length;i++){boolean on=presets[i]==now;buttons[i].setTextColor(on?(darkTheme?Color.rgb(24,25,28):Color.WHITE):ink);GradientDrawable g=shape(on?color:raised,18);buttons[i].setBackground(ripple(g));}};
        // Changes are pending until saved: Save asks to confirm, Cancel puts the old value back.
        int[] pending={st.getInt(key,def)};LinearLayout act=row();act.setVisibility(View.GONE);LinearLayout.LayoutParams ap=lp(-1,44);ap.topMargin=dp(10);
        Button cancel=button("Cancel",raised,ink),save=button("Save",color,darkTheme?Color.rgb(24,25,28):Color.WHITE);act.addView(cancel,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams svp=new LinearLayout.LayoutParams(0,-1,1);svp.leftMargin=dp(8);act.addView(save,svp);
        set[0]=v->{v=Math.max(min,Math.min(max,v));if(step>1)v=Math.round(v/(float)step)*step;pending[0]=v;value.setText(fmt.apply(v));if(seek.getProgress()!=v-min)seek.setProgress(v-min);act.setVisibility(v==st.getInt(key,def)?View.GONE:View.VISIBLE);paintPending(buttons,presets,v,color);};
        cancel.setOnClickListener(v->set[0].accept(st.getInt(key,def)));
        save.setOnClickListener(v->confirmChange("Change "+title.toLowerCase(Locale.getDefault())+"?","From "+fmt.apply(st.getInt(key,def))+" to "+fmt.apply(pending[0])+".",()->{st.edit().putInt(key,pending[0]).apply();act.setVisibility(View.GONE);paint.run();},null));
        for(int i=0;i<presets.length;i++){int pv=presets[i];Button b=button(chip.apply(pv),raised,ink);b.setTextSize(12);b.setPadding(0,0,0,0);buttons[i]=b;LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)bp.leftMargin=dp(6);chips.addView(b,bp);b.setOnClickListener(v->set[0].accept(pv));}
        minus.setOnClickListener(v->set[0].accept(pending[0]-step));plus.setOnClickListener(v->set[0].accept(pending[0]+step));paint.run();c.addView(act,ap);
    }
    private void paintPending(Button[] buttons,int[] presets,int now,int color){for(int i=0;i<presets.length;i++){if(buttons[i]==null)continue;boolean on=presets[i]==now;buttons[i].setTextColor(on?(darkTheme?Color.rgb(24,25,28):Color.WHITE):ink);buttons[i].setBackground(ripple(shape(on?color:raised,18)));}}
    /** A labelled switch for an on/off setting; flipping it asks to confirm and puts it back if you cancel. */
    private void settingSwitch(LinearLayout parent,String title,String sub,String key,boolean def,String offTitle,String offMsg,String onTitle,String onMsg,Runnable after){
        SharedPreferences st=getSharedPreferences("kadamm_settings",MODE_PRIVATE);
        View div=new View(this);div.setBackgroundColor(line);LinearLayout.LayoutParams dp1=lp(-1,1);dp1.topMargin=dp(12);parent.addView(div,dp1);
        LinearLayout r=row();r.setPadding(0,dp(10),0,0);parent.addView(r,lp(-1,-2));LinearLayout l=col();r.addView(l,new LinearLayout.LayoutParams(0,-2,1));
        l.addView(tx(title,14,ink,true));l.addView(tx(sub,11,muted,false));
        android.widget.Switch sw=new android.widget.Switch(this);sw.setChecked(st.getBoolean(key,def));sw.setThumbTintList(switchTint());LinearLayout.LayoutParams swp=lp(-2,-2);swp.leftMargin=dp(10);r.addView(sw,swp);
        boolean[] quiet={false};
        sw.setOnCheckedChangeListener((b,v)->{if(quiet[0])return;
            confirmChange(v?onTitle:offTitle,v?onMsg:offMsg,()->{st.edit().putBoolean(key,v).apply();if(after!=null)after.run();renderPageNow();},()->{quiet[0]=true;sw.setChecked(!v);quiet[0]=false;});});
    }
    private void showHealthConnectInfo(){navigate("Health");}
    /** Every settings change is confirmed first, the way system settings do; Cancel leaves everything as it was. */
    private void confirmChange(String title,String message,Runnable apply,Runnable cancel){
        dialog().setTitle(title).setMessage(message).setNegativeButton("Cancel",(d,w)->{if(cancel!=null)cancel.run();}).setOnCancelListener(d->{if(cancel!=null)cancel.run();}).setPositiveButton("Confirm",(d,w)->{apply.run();toast("Saved");}).show();
    }
    /** Profile: you first (photo, name, email, level chips), then lifetime totals, records, shortcuts, account and appearance. */
    private void renderProfile(){
        JSONArray h=history();LocalAccount.Account me=LocalAccount.current(this);Map<String,Long> totals=DaySteps.allDayTotals(this);int[] st=DaySteps.streaks(totals,dailyGoal());
        LinearLayout top=row();content.addView(top,lp(-1,48));LinearLayout.LayoutParams bbp=lp(-2,48);bbp.leftMargin=-dp(8);top.addView(brandButton(),bbp);
        // Hero: photo with a camera badge, name with an edit pencil, email and member-since.
        LinearLayout hero=col();hero.setGravity(Gravity.CENTER_HORIZONTAL);hero.setPadding(dp(18),dp(22),dp(18),dp(18));
        hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{blend(panel,lime,darkTheme?.26f:.14f),panel}){{setCornerRadius(dp(24));setStroke(dp(1),blend(line,lime,.3f));}});
        LinearLayout.LayoutParams hp=lp(-1,-2);hp.topMargin=dp(10);content.addView(hero,hp);
        FrameLayout ph=new FrameLayout(this);hero.addView(ph,lp(104,104));
        View av=avatarView(me,96);FrameLayout.LayoutParams avp=new FrameLayout.LayoutParams(dp(96),dp(96),Gravity.CENTER);ph.addView(av,avp);
        GradientDrawable ring=new GradientDrawable();ring.setShape(GradientDrawable.OVAL);ring.setStroke(dp(3),lime);View rv=new View(this);rv.setBackground(ring);ph.addView(rv,new FrameLayout.LayoutParams(dp(104),dp(104),Gravity.CENTER));
        if(me!=null){android.widget.ImageView cam=icon(R.drawable.ic_my_location,accentOn,7);GradientDrawable cb=new GradientDrawable();cb.setShape(GradientDrawable.OVAL);cb.setColor(lime);cb.setStroke(dp(3),panel);cam.setBackground(cb);cam.setImageResource(android.R.drawable.ic_menu_camera);cam.setContentDescription("Change profile photo");ph.addView(cam,new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.BOTTOM|Gravity.END));
            View.OnClickListener pick=v->choosePhoto();cam.setOnClickListener(pick);av.setOnClickListener(pick);}
        LinearLayout nr=row();nr.setGravity(Gravity.CENTER);LinearLayout.LayoutParams nrp=lp(-2,-2);nrp.topMargin=dp(14);hero.addView(nr,nrp);
        nr.addView(tx(displayName(),24,ink,true));
        if(me!=null){android.widget.ImageView pen=icon(R.drawable.ic_edit,muted,9);pen.setContentDescription("Edit name");pen.setBackground(rippleOn(Color.TRANSPARENT,18));pen.setOnClickListener(v->editName());nr.addView(pen,lp(36,36));}
        TextView em=tx(me!=null?me.email:"Guest · sign in to put your name on your activity",12,muted,false);em.setGravity(Gravity.CENTER);hero.addView(em);
        if(me!=null){TextView since=tx("Member since "+new SimpleDateFormat("MMMM yyyy",Locale.getDefault()).format(new Date(me.created)),11,muted,false);since.setGravity(Gravity.CENTER);LinearLayout.LayoutParams sp=lp(-2,-2);sp.topMargin=dp(2);hero.addView(since,sp);}
        LinearLayout chips=row();chips.setGravity(Gravity.CENTER);LinearLayout.LayoutParams cp=lp(-2,-2);cp.topMargin=dp(14);hero.addView(chips,cp);
        Insights.Coverage cv=Insights.coverage(h);int earned=0;for(Progress.Badge bd:Progress.badges(this,h,totals,dailyGoal()))if(bd.earned())earned++;
        heroChip(chips,"🔥 "+st[0]+(st[0]==1?" day":" days"),flame());heroChip(chips,"★ "+earned+" badges",lime);heroChip(chips,String.format(Locale.US,"◎ %.1f km explored",cv.uniqueKm),teal());
        if(me==null){Button in=button(LocalAccount.exists(this)?"Log in":"Create account",lime,accentOn);in.setTextSize(15);LinearLayout.LayoutParams ip=lp(-1,50);ip.topMargin=dp(16);hero.addView(in,ip);in.setOnClickListener(v->showAuth(LocalAccount.exists(this)));}
        // Lifetime totals.
        long steps=0;for(Long v:totals.values())steps+=v;double km=distanceAll(h);long ms=0;for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o!=null)ms+=o.optLong("durationMs",0);}
        TextView lh=tx("ALL TIME",10,muted,true);lh.setLetterSpacing(.08f);LinearLayout.LayoutParams lhp=lp(-1,-2);lhp.topMargin=dp(20);content.addView(lh,lhp);
        LinearLayout r1=glanceRow();glance(r1,"STEPS",steps>=100000?String.format(Locale.US,"%.0fk",steps/1000d):String.format(Locale.US,"%,d",steps),"",lime,"▶",null,totals.size()+" days counted");glance(r1,"SESSIONS",String.valueOf(h.length()),"",purple(),"◷",null,String.format(Locale.US,"%.1f km · %dh %02dm",km,ms/3600000,(ms/60000)%60));
        renderFitnessSnapshot(h);
        renderRecords();
        // Shortcuts.
        LinearLayout links=card();links.setPadding(dp(6),dp(6),dp(6),dp(6));
        profileLink(links,"Places you walk","Your street map and explorer level",R.drawable.ic_my_location,teal(),()->navigate("Places"));
        profileLink(links,"Achievements",earned+" badges earned",R.drawable.ic_trophy,flame(),()->navigate("Achievements"));
        profileLink(links,"Goals and targets",String.format(Locale.US,"%,d steps a day",dailyGoal()),R.drawable.ic_flag,green(),()->navigate("Goals"));
        profileLink(links,"Kadamm Trainer","Your stage, today's workout and the week ahead",R.drawable.ic_bolt,teal(),()->navigate("Trainer"));profileLink(links,"Import history","Walks, runs and steps from Samsung Health, Zepp, Google Fit",R.drawable.ic_download,indigo(),()->navigate("Import"));profileLink(links,"Activity calendar","Your days and sessions",R.drawable.ic_calendar,purple(),()->navigate("History"));
        profileLink(links,"Share Kadamm","Send the app to a friend",R.drawable.ic_share,lime,this::shareApp);
        renderAccount();
        renderAppVersion();
        LinearLayout appearance=card();appearance.addView(tx("Appearance",16,ink,true));appearance.addView(tx("Choose how Kadamm looks on this device.",12,muted,false));String selected=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("themeMode","system");LinearLayout choices=row();LinearLayout.LayoutParams chp=lp(-1,42);chp.topMargin=dp(12);appearance.addView(choices,chp);for(String mode:new String[]{"Light","Dark","System"}){boolean active=mode.equalsIgnoreCase(selected);Button b=button(mode,active?lime:raised,active?accentOn:ink);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(choices.getChildCount()>0)bp.leftMargin=dp(7);choices.addView(b,bp);b.setOnClickListener(v->{if(mode.toLowerCase(Locale.US).equals(selected))return;confirmChange("Switch to "+mode.toLowerCase(Locale.US)+" theme?","Kadamm will redraw in the "+mode.toLowerCase(Locale.US)+" theme.",()->{getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putString("themeMode",mode.toLowerCase(Locale.US)).apply();applyTheme();renderPage();},null);});}
    }
    private void changePassword(){
        android.widget.EditText cur=field("Current password",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD),nw=field("New password (8+ characters)",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD),cf=field("Confirm new password",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box=col();box.setPadding(dp(20),dp(8),dp(20),0);for(android.widget.EditText e:new android.widget.EditText[]{cur,nw,cf}){LinearLayout.LayoutParams p=lp(-1,54);p.topMargin=dp(8);box.addView(e,p);}
        AlertDialog d=dialog().setTitle("Change password").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String err=LocalAccount.changePassword(this,cur.getText().toString(),nw.getText().toString(),cf.getText().toString());if(err!=null){(err.contains("current")?cur:nw).setError(err);return;}d.dismiss();toast("Password changed");}));d.show();
    }
    private void heroChip(LinearLayout p,String text,int color){TextView t=tx(text,11,darkTheme?blend(color,Color.WHITE,.2f):blend(color,Color.BLACK,.25f),true);t.setPadding(dp(10),dp(6),dp(10),dp(6));t.setBackground(shape(blend(panel,color,darkTheme?.22f:.14f),14));LinearLayout.LayoutParams lp2=lp(-2,-2);if(p.getChildCount()>0)lp2.leftMargin=dp(6);p.addView(t,lp2);}
    private void profileLink(LinearLayout parent,String title,String sub,int res,int color,Runnable go){
        LinearLayout r=row();r.setPadding(dp(10),dp(10),dp(10),dp(10));r.setBackground(rippleOn(Color.TRANSPARENT,14));r.setFocusable(true);r.setContentDescription(title);parent.addView(r,lp(-1,-2));
        android.widget.ImageView ic=icon(res,darkTheme?Color.rgb(11,11,13):Color.WHITE,8);ic.setBackground(shape(color,12));r.addView(ic,lp(38,38));
        LinearLayout t=col();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(12);r.addView(t,tp);t.addView(tx(title,15,ink,true));t.addView(tx(sub,11,muted,false));r.addView(tx("›",22,muted,false));r.setOnClickListener(v->go.run());
    }
    /** A round avatar: the account's photo, or its initials on the accent colour. */
    private static String avatarKey;private static android.graphics.Bitmap avatarCache;
    /** The profile photo, decoded once at a sensible size and reused until the file changes. */
    private android.graphics.Bitmap avatarBitmap(java.io.File f){
        String key=f.getPath()+"/"+f.lastModified();if(key.equals(avatarKey)&&avatarCache!=null)return avatarCache;
        android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(f.getPath(),o);
        int want=dp(120),sample=1;while(Math.min(o.outWidth,o.outHeight)/(sample*2)>=want)sample*=2;
        o=new android.graphics.BitmapFactory.Options();o.inSampleSize=sample;avatarCache=android.graphics.BitmapFactory.decodeFile(f.getPath(),o);avatarKey=key;return avatarCache;
    }
    private View avatarView(LocalAccount.Account a,int sizeDp){
        java.io.File f=a!=null&&a.photo!=null?new java.io.File(a.photo):null;
        if(f!=null&&f.exists()){android.widget.ImageView iv=new android.widget.ImageView(this);iv.setImageBitmap(avatarBitmap(f));iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            iv.setOutlineProvider(new android.view.ViewOutlineProvider(){@Override public void getOutline(View v,android.graphics.Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});iv.setClipToOutline(true);iv.setContentDescription("Profile photo");return iv;}
        String n=a!=null?a.name:null;String ini="KA";if(n!=null&&!n.trim().isEmpty()){String[] p=n.trim().split("\\s+");ini=(p[0].substring(0,1)+(p.length>1?p[p.length-1].substring(0,1):"")).toUpperCase(Locale.getDefault());}
        TextView t=tx(ini,Math.max(12,sizeDp*2/5),accentOn,true);t.setGravity(Gravity.CENTER);GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(lime);t.setBackground(g);t.setContentDescription("Profile picture, "+ini);return t;
    }
    private static final int PHOTO_REQUEST=203;
    private void choosePhoto(){
        java.util.List<String> opts=new ArrayList<>(java.util.Arrays.asList("Choose a photo"));if(LocalAccount.photo(this)!=null)opts.add("Remove photo");
        dialog().setTitle("Profile photo").setItems(opts.toArray(new String[0]),(d,w)->{
            if(w==1){LocalAccount.setPhoto(this,null);renderPageNow();return;}
            try{Intent i=Build.VERSION.SDK_INT>=33?new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES).setType("image/*"):new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PHOTO_REQUEST);}catch(Exception e){toast("No photo picker available");}
        }).show();
    }
    /** Crops the picked image to a centred 512 px square and keeps it in app storage. */
    private void savePhoto(Uri uri){
        try{
            android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inJustDecodeBounds=true;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){android.graphics.BitmapFactory.decodeStream(in,null,o);}
            int scale=1;while(Math.min(o.outWidth,o.outHeight)/(scale*2)>=512)scale*=2;o=new android.graphics.BitmapFactory.Options();o.inSampleSize=scale;
            android.graphics.Bitmap src;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){src=android.graphics.BitmapFactory.decodeStream(in,null,o);}
            if(src==null)throw new IllegalStateException();
            int side=Math.min(src.getWidth(),src.getHeight());android.graphics.Bitmap sq=android.graphics.Bitmap.createBitmap(src,(src.getWidth()-side)/2,(src.getHeight()-side)/2,side,side);
            android.graphics.Bitmap out=android.graphics.Bitmap.createScaledBitmap(sq,512,512,true);
            java.io.File f=new java.io.File(getFilesDir(),"avatar-"+System.currentTimeMillis()+".jpg");try(java.io.FileOutputStream fo=new java.io.FileOutputStream(f)){out.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,fo);}
            LocalAccount.setPhoto(this,f);toast("Profile photo updated");renderPageNow();
        }catch(Exception e){toast("Couldn't use that photo");}
    }
    private void editName(){
        android.widget.EditText in=field("Your name",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);in.setText(displayName());in.setSelection(in.length());
        FrameLayout box=new FrameLayout(this);box.setPadding(dp(20),dp(8),dp(20),0);box.addView(in,new FrameLayout.LayoutParams(-1,dp(54)));
        AlertDialog d=dialog().setTitle("Edit name").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String err=LocalAccount.rename(this,in.getText().toString());if(err!=null){in.setError(err);return;}d.dismiss();toast("Name updated");renderPageNow();}));d.show();
    }
    private void profileStat(LinearLayout p,String l,String val){LinearLayout c=col();p.addView(c,new LinearLayout.LayoutParams(0,-1,1));c.addView(tx(l,9,muted,true));c.addView(tx(val,16,ink,true));}
    private void renderDetail(JSONObject o){if(o==null){page="History";renderPage();return;}String dt=o.optString("date","");brand(dt,sessionTitle(o),"Session summary · "+o.optString("time",""));RouteView rv=new RouteView(this);rv.setInteractive(true);rv.setTheme(bg,raised,lime,darkTheme);rv.setRoute(o.optString("route","[]"));rv.setClipToOutline(true);rv.setBackground(shape(panel,16));FrameLayout mapBox=new FrameLayout(this);LinearLayout.LayoutParams rp=lp(-1,320);content.addView(mapBox,rp);mapBox.addView(rv,new FrameLayout.LayoutParams(-1,-1));android.widget.ImageView fit=icon(R.drawable.ic_my_location,ink,10);fit.setBackground(rippleOn(panel,12));fit.setContentDescription("Show the whole route");fit.setOnClickListener(v->rv.resetView());TextView mstyle=tx("Map: "+mapStyleName(),11,ink,true);mstyle.setPadding(dp(10),dp(6),dp(10),dp(6));mstyle.setBackground(rippleOn(panel,14));FrameLayout.LayoutParams msp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);msp.setMargins(dp(10),dp(10),0,0);mapBox.post(()->mapBox.addView(mstyle,msp));mstyle.setOnClickListener(v->{cycleMapStyle();rv.setTheme(bg,raised,lime,darkTheme);mstyle.setText("Map: "+mapStyleName());});FrameLayout.LayoutParams fitp=new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.TOP|Gravity.START);fitp.setMargins(dp(10),dp(52),0,0);mapBox.addView(fit,fitp);rv.setLegend(true);mapModes(mapBox,rv,o);if(o.optString("route","[]").contains("\"s\"")&&o.optDouble("distanceKm",0)>=.1)replayControls(mapBox,rv,o);if(o.optDouble("runDistanceKm",0)>0||o.optString("route","").contains("\"t\":\"r\"")){LinearLayout key=row();LinearLayout.LayoutParams kp=lp(-1,28);kp.topMargin=dp(6);content.addView(key,kp);key.addView(tx("●",14,lime,true));key.addView(tx(String.format(Locale.US,"  Walk %.2f km      ",o.optDouble("walkDistanceKm",0)),12,ink,true));key.addView(tx("●",14,Color.rgb(234,67,53),true));key.addView(tx(String.format(Locale.US,"  Run %.2f km",o.optDouble("runDistanceKm",0)),12,ink,true));}TextView attribution=tx("© OpenStreetMap contributors · drag, pinch or double-tap to explore",10,muted,false);LinearLayout.LayoutParams ap=lp(-1,28);ap.topMargin=dp(5);content.addView(attribution,ap);renderSessionOverview(o);renderSessionInsights(o);sessionActions(o);}
    private void correctDistance(JSONObject session){android.widget.EditText input=new android.widget.EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setText(String.format(Locale.US,"%.2f",session.optDouble("distanceKm")));dialog().setTitle("Correct distance").setMessage("Enter the distance shown on the treadmill, in kilometres.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{try{double km=Double.parseDouble(input.getText().toString().trim());if(km<0||km>500){toast("Enter a distance from 0 to 500 km");return;}double ratio=session.optDouble("distanceKm",0)>0?km/session.optDouble("distanceKm",0):1;session.put("walkDistanceKm",session.optDouble("walkDistanceKm",km)*ratio);session.put("runDistanceKm",session.optDouble("runDistanceKm",0)*ratio);session.put("distanceKm",km);session.put("calories",Calories.estimate(this,session.optDouble("walkDistanceKm",km),session.optLong("walkDurationMs",session.optLong("durationMs")),session.optDouble("runDistanceKm",0),session.optLong("runDurationMs",0),session.optLong("steps",0)));session.put("distanceEstimated",false);session.put("distanceCorrected",true);saveSession(historyPrefs,session);lastSession=session;renderPage();}catch(Exception e){toast("Enter a valid distance");}}).show();}
    private void detailRow(LinearLayout c,String a,String av,String b,String bv){LinearLayout r=row();LinearLayout.LayoutParams p=lp(-1,54);c.addView(r,p);LinearLayout x=col(),y=col();r.addView(x,new LinearLayout.LayoutParams(0,-1,1));r.addView(y,new LinearLayout.LayoutParams(0,-1,1));x.addView(tx(a,9,muted,true));x.addView(tx(av,17,ink,true));y.addView(tx(b,9,muted,true));y.addView(tx(bv,17,ink,true));}
    // ---- Recent activity as day stacks ----
    private int recentDays=7;private final java.util.HashSet<String> openDays=new java.util.HashSet<>();
    /**
     * Recent activity grouped by day, newest first: each day is a stack of cards (one layer per extra session)
     * with its totals and how many sessions were automatic or started by you. Tap a day to fan the stack out
     * into its sessions; tap a session for its details. "Show more days" adds three days at a time.
     */
    private void renderRecentDays(JSONArray all){
        java.util.LinkedHashMap<String,java.util.List<JSONObject>> days=new java.util.LinkedHashMap<>();
        for(int i=all.length()-1;i>=0;i--){JSONObject o=all.optJSONObject(i);if(o==null)continue;String d=o.optString("day","");java.util.List<JSONObject> l=days.get(d);if(l==null){l=new java.util.ArrayList<>();days.put(d,l);}l.add(o);}
        LinearLayout head=row();LinearLayout.LayoutParams hp=lp(-1,30);hp.topMargin=dp(4);content.addView(head,hp);
        head.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        if(days.isEmpty()){LinearLayout empty=card();empty.addView(tx("Your sessions will show up here, grouped by day.",13,muted,false));return;}
        head.addView(tx(days.size()>recentDays?"Last "+recentDays+" active days":days.size()+(days.size()==1?" active day":" active days"),11,muted,false));
        int shown=0;
        for(java.util.Map.Entry<String,java.util.List<JSONObject>> e:days.entrySet()){if(shown++>=recentDays)break;dayStack(e.getKey(),e.getValue());}
        if(days.size()>7){
            LinearLayout more=row();more.setGravity(Gravity.CENTER);LinearLayout.LayoutParams mp=lp(-1,44);mp.topMargin=dp(12);content.addView(more,mp);
            boolean all2=recentDays>=days.size();
            if(!all2){TextView m=tx("Show 7 more days  ▾",13,lime,true);m.setGravity(Gravity.CENTER);m.setBackground(rippleOn(raised,22));m.setPadding(dp(18),0,dp(18),0);more.addView(m,lp(-2,40));m.setOnClickListener(v->{recentDays+=7;int y=scroller.getScrollY();renderPageNow();scroller.post(()->scroller.scrollTo(0,y));});}
            if(recentDays>7){TextView f=tx("Show fewer",13,muted,true);f.setGravity(Gravity.CENTER);f.setPadding(dp(18),0,dp(18),0);f.setBackground(rippleOn(raised,22));LinearLayout.LayoutParams fp=lp(-2,40);if(!all2)fp.leftMargin=dp(8);more.addView(f,fp);f.setOnClickListener(v->{recentDays=7;int y=scroller.getScrollY();renderPageNow();scroller.post(()->scroller.scrollTo(0,y));});}
        }
    }
    /** A card peeking out under a stack: neutral, a little closer to the page colour with each layer, with a hairline edge. */
    private GradientDrawable stackLayer(int k,int radius){GradientDrawable g=shape(blend(panel,bg,darkTheme?.30f*k:.38f*k),radius);g.setStroke(dp(1),blend(line,bg,.15f*k));return g;}
    // ---- Kadamm Trainer ----
    private Trainer.State trainerState(JSONArray h){return Trainer.build(this,h);}
    /** Home: today's workout from the trainer, one tap to the full plan. */
    private void renderTrainerCard(JSONArray hist){
        if(!SessionMetrics.ready())return;TrainerCoach.Day cd=TrainerCoach.today(this,hist);
        LinearLayout c=card();c.setPadding(dp(18),dp(18),dp(18),dp(18));coachBackground(c,cd.allDone);
        coachHeader(c,cd,false);
        TextView m=tx(cd.message,14,ink,false);m.setLineSpacing(dp(3),1f);m.setMaxLines(4);m.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams mp=lp(-1,-2);mp.topMargin=dp(12);c.addView(m,mp);
        LinearLayout r=row();LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(14);c.addView(r,rp);
        for(int i=0;i<cd.list.size();i++){TrainerCoach.Challenge ch=cd.list.get(i);LinearLayout b=col();b.setPadding(dp(10),dp(10),dp(10),dp(10));b.setBackground(shape(ch.done?blend(panel,positive,darkTheme?.22f:.14f):raised,12));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)bp.leftMargin=dp(8);r.addView(b,bp);
            b.addView(tx(ch.done?"✓":coachIcon(ch.id),16,ch.done?positive:coachColor(i),true));TextView t=tx(ch.title,11,ink,true);t.setMaxLines(2);LinearLayout.LayoutParams tp=lp(-1,-2);tp.topMargin=dp(4);b.addView(t,tp);
            b.addView(coachBar(ch,i),coachBarLp());}
        TextView open=tx("Open your trainer  ›",13,GoalCapsule.COLORS[0],true);LinearLayout.LayoutParams op=lp(-1,-2);op.topMargin=dp(14);c.addView(open,op);c.setOnClickListener(v->navigate("Trainer"));
        maybeCoachCelebrate(cd);
    }
    private static final int[] COACH_COLORS={0xFF00BFA6,0xFFFF6B6B,0xFF7C6CF2};
    private int coachColor(int i){return COACH_COLORS[i%3];}
    private String coachIcon(String id){switch(id){case "steps":return "👟";case "brisk":return "⚡";case "sit":return "🪑";case "long":return "⏱";case "km":return "📍";case "beat":return "🏆";case "evening":return "🌙";case "twice":return "✌";case "hours":return "⏰";case "cadence":return "🎵";default:return "🌅";}}
    private void coachBackground(LinearLayout c,boolean done){int a=done?positive:violet;GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,a,darkTheme?.26f:.14f),blend(panel,GoalCapsule.COLORS[0],darkTheme?.10f:.06f),panel});g.setCornerRadius(dp(20));g.setStroke(dp(1),blend(line,a,.35f));c.setBackground(g);}
    /** The coach's face and name: a gradient avatar, "Coach Kadamm", and what it does. */
    private void coachHeader(LinearLayout c,TrainerCoach.Day cd,boolean big){
        LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);c.addView(r,lp(-1,-2));
        TextView av=tx("K",big?26:20,Color.WHITE,true);av.setGravity(Gravity.CENTER);GradientDrawable ag=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{GoalCapsule.COLORS[0],violet});ag.setShape(GradientDrawable.OVAL);ag.setStroke(dp(3),blend(panel,Color.WHITE,.25f));av.setBackground(ag);r.addView(av,lp(big?60:48,big?60:48));
        LinearLayout l=col();LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,-2,1);llp.leftMargin=dp(12);r.addView(l,llp);
        TextView e=tx(cd.allDone?"ALL DONE TODAY":"YOUR TRAINER",9,cd.allDone?positive:violet,true);e.setLetterSpacing(.08f);l.addView(e);
        l.addView(tx("Coach Kadamm",big?20:17,ink,true));l.addView(tx(cd.greeting,12,muted,false));
        TextView cnt=tx(cd.done+"/"+cd.list.size(),big?20:16,cd.allDone?positive:ink,true);cnt.setGravity(Gravity.CENTER);cnt.setPadding(dp(10),dp(4),dp(10),dp(4));cnt.setBackground(shape(cd.allDone?blend(panel,positive,.25f):raised,14));r.addView(cnt,lp(-2,-2));
    }
    private View coachBar(TrainerCoach.Challenge ch,int i){
        FrameLayout f=new FrameLayout(this);f.setBackground(shape(blend(raised,line,.5f),3));View in=new View(this);in.setBackground(shape(ch.done?positive:coachColor(i),3));
        f.addView(in,new FrameLayout.LayoutParams(0,-1));f.post(()->{FrameLayout.LayoutParams q=(FrameLayout.LayoutParams)in.getLayoutParams();q.width=(int)(f.getWidth()*Math.max(.03f,ch.fraction()));in.setLayoutParams(q);});return f;}
    private LinearLayout.LayoutParams coachBarLp(){LinearLayout.LayoutParams p=lp(-1,6);p.topMargin=dp(8);return p;}
    /** Once a day, when all three challenges are done: the coach's congratulation with confetti. */
    private void maybeCoachCelebrate(TrainerCoach.Day cd){
        if(!cd.allDone||!resumed||authLayer!=null||celebrateLayer!=null)return;SharedPreferences st=getSharedPreferences("kadamm_coach",MODE_PRIVATE);if(todayKey().equals(st.getString("celebrated","")))return;
        st.edit().putString("celebrated",todayKey()).apply();
        handler.postDelayed(()->{if(!resumed||celebrateLayer!=null)return;
            celebrateLayer=new FrameLayout(this);celebrateLayer.setBackgroundColor(Color.argb(175,0,0,0));celebrateLayer.setClickable(true);root.addView(celebrateLayer,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout card=col();card.setGravity(Gravity.CENTER_HORIZONTAL);card.setPadding(dp(24),dp(28),dp(24),dp(20));
            card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{blend(panel,violet,darkTheme?.32f:.18f),panel}){{setCornerRadius(dp(28));setStroke(dp(1),blend(line,violet,.4f));}});
            FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);cp.setMargins(dp(26),0,dp(26),0);celebrateLayer.addView(card,cp);
            TextView av=tx("K",36,Color.WHITE,true);av.setGravity(Gravity.CENTER);GradientDrawable ag=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{GoalCapsule.COLORS[0],violet});ag.setShape(GradientDrawable.OVAL);ag.setStroke(dp(5),blend(violet,Color.WHITE,.5f));av.setBackground(ag);card.addView(av,lp(96,96));
            TextView h=tx("Coach Kadamm",13,violet,true);h.setGravity(Gravity.CENTER);LinearLayout.LayoutParams hp=lp(-1,-2);hp.topMargin=dp(12);card.addView(h,hp);
            TextView big=tx("All 3 challenges done!",26,ink,true);big.setGravity(Gravity.CENTER);LinearLayout.LayoutParams bp=lp(-1,-2);bp.topMargin=dp(4);card.addView(big,bp);
            TextView sub=tx(cd.message,15,ink,false);sub.setGravity(Gravity.CENTER);sub.setLineSpacing(dp(3),1f);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(10);card.addView(sub,sp);
            TextView so=tx(cd.signoff,13,muted,false);so.setGravity(Gravity.CENTER);LinearLayout.LayoutParams sop=lp(-1,-2);sop.topMargin=dp(8);card.addView(so,sop);
            Button ok=button("Thanks, coach",violet,Color.WHITE);ok.setTextSize(15);LinearLayout.LayoutParams okp=lp(-1,50);okp.topMargin=dp(20);card.addView(ok,okp);
            Runnable close=()->{FrameLayout l=celebrateLayer;celebrateLayer=null;if(l!=null)l.animate().alpha(0f).setDuration(200).withEndAction(()->root.removeView(l)).start();};
            ok.setOnClickListener(v->close.run());celebrateLayer.setOnClickListener(v->close.run());
            ConfettiView shower=ConfettiView.shower(this);celebrateLayer.addView(shower,new FrameLayout.LayoutParams(-1,-1));},900);
    }
    // ---- Trainer: weekly challenges, the weekly review, friends, watch and fitness, records ----
    private void challengeRow(LinearLayout parent,TrainerCoach.Challenge ch,int i){
        LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(12),dp(12),dp(12),dp(12));r.setBackground(shape(ch.done?blend(panel,positive,darkTheme?.20f:.12f):raised,14));LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(8);parent.addView(r,rp);
        TextView ic=tx(ch.done?"✓":String.valueOf(i+1),15,ch.done?Color.WHITE:coachColor(i),true);ic.setGravity(Gravity.CENTER);GradientDrawable ig=new GradientDrawable();ig.setShape(GradientDrawable.OVAL);ig.setColor(ch.done?positive:blend(panel,coachColor(i),.22f));ic.setBackground(ig);r.addView(ic,lp(36,36));
        LinearLayout l=col();LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,-2,1);llp.leftMargin=dp(12);r.addView(l,llp);l.addView(tx(ch.title,14,ink,true));l.addView(tx(ch.progress(),12,ch.done?positive:muted,ch.done));l.addView(coachBar(ch,i),coachBarLp());
    }
    private void renderWeekly(JSONArray h){
        TrainerCoach.Week w=TrainerCoach.week(this,h);
        LinearLayout c=metricCard("THIS WEEK'S CHALLENGES",w.done+"/"+w.list.size(),"Three challenges for Monday to Sunday, picked fresh each week and fitted to your usual week (the four before). Win all three for a weekly trophy.");
        c.addView(tx(w.allDone?"🏆 Weekly challenge won. Brilliant week.":w.daysLeft==1?"Last day of the week: finish strong.":w.daysLeft+" days left this week",13,w.allDone?positive:ink,true));
        for(int i=0;i<w.list.size();i++)challengeRow(c,w.list.get(i),i);
        if(w.wins>0){TextView t=tx("🏆 × "+w.wins+(w.wins==1?" weekly trophy":" weekly trophies"),12,muted,true);t.setPadding(0,dp(10),0,0);c.addView(t);}
        // The weekly review: this week from Sunday 6 pm, last week on Monday and Tuesday.
        Calendar now=Calendar.getInstance();int dow=now.get(Calendar.DAY_OF_WEEK);int back=dow==Calendar.SUNDAY&&now.get(Calendar.HOUR_OF_DAY)>=18?0:(dow==Calendar.MONDAY||dow==Calendar.TUESDAY)?1:-1;
        if(back>=0){TrainerCoach.Review r=TrainerCoach.review(this,h,back);LinearLayout rc=card();coachBackground(rc,false);
            TextView e=tx("WEEKLY REVIEW · COACH KADAMM",10,violet,true);e.setLetterSpacing(.08f);rc.addView(e);rc.addView(tx(r.title,20,ink,true));
            for(String line:r.lines){TextView t=tx("•  "+line,14,ink,false);t.setPadding(0,dp(6),0,0);rc.addView(t);}
            TextView f=tx(r.focus,13,violet,true);f.setPadding(0,dp(12),0,0);f.setLineSpacing(dp(2),1f);rc.addView(f);
            Button sh=button("Share my week",raised,ink);LinearLayout.LayoutParams sp=lp(-1,44);sp.topMargin=dp(12);rc.addView(sh,sp);
            sh.setOnClickListener(v->{StringBuilder b=new StringBuilder("Kadamm · "+r.title+"\n");for(String line:r.lines)b.append("• ").append(line).append('\n');shareSheet("Share your week",b.toString().trim(),null,null);});}
    }
    private void renderFriends(JSONArray h){
        LinearLayout c=metricCard("CHALLENGE A FRIEND","","Make a challenge code and send it any way you like. Your friend enters it in their Kadamm and the clock starts for them that day. Codes hold only the challenge and your first name; nothing goes to a server.");
        java.util.List<TrainerCoach.Friend> fs=TrainerCoach.friends(this,h);
        if(fs.isEmpty())c.addView(tx("Walk together, apart: send a code like KDM-S70000-7 (70,000 steps in 7 days) or enter one from a friend.",13,muted,false));
        for(TrainerCoach.Friend f:fs){LinearLayout r=col();r.setPadding(dp(12),dp(12),dp(12),dp(12));r.setBackground(shape(f.done?blend(panel,positive,darkTheme?.20f:.12f):raised,14));LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(8);c.addView(r,rp);
            String unit=TrainerCoach.kindUnit(f.kind);String tgt="D".equals(f.kind)?String.format(Locale.US,"%d km",f.target):String.format(Locale.US,"%,d %s",f.target,unit);
            r.addView(tx(f.name+"'s challenge: "+tgt,14,ink,true));
            String val="D".equals(f.kind)?String.format(Locale.US,"≈%.1f km",f.value):String.format(Locale.US,"%,d %s",Math.round(f.value),unit);
            r.addView(tx(f.done?"Done ✓ "+val:f.over?"Ended · "+val:val+" · until "+prettyDay(f.to),12,f.done?positive:muted,f.done));
            TrainerCoach.Challenge pr=new TrainerCoach.Challenge("f","",f.target,f.value,"",f.done);r.addView(coachBar(pr,0),coachBarLp());
            LinearLayout acts=row();LinearLayout.LayoutParams ap=lp(-1,-2);ap.topMargin=dp(8);r.addView(acts,ap);
            TextView share=tx("Send my result  ›",12,lime,true);acts.addView(share,new LinearLayout.LayoutParams(0,-2,1));TextView rm=tx("Remove",12,muted,true);acts.addView(rm);
            share.setOnClickListener(v->shareSheet("Send your result","Kadamm challenge "+f.code+": I "+(f.done?"finished it with ":"have ")+val+" of "+tgt+".",null,null));
            rm.setOnClickListener(v->{TrainerCoach.removeFriend(this,f.code);renderPageNow();});}
        LinearLayout btns=row();LinearLayout.LayoutParams bp=lp(-1,46);bp.topMargin=dp(12);c.addView(btns,bp);
        Button make=button("Create a code",lime,darkTheme?Color.rgb(11,11,13):Color.WHITE),enter=button("Enter a code",raised,ink);
        btns.addView(make,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,-1,1);ep.leftMargin=dp(8);btns.addView(enter,ep);
        make.setOnClickListener(v->{String[] opts={"70,000 steps in 7 days","15,000 steps in 1 day","150 brisk minutes in 7 days","40 km in 7 days","300,000 steps in 30 days"};String[] codes={"S70000-7","S15000-1","B150-7","D40-7","S300000-30"};
            dialog().setTitle("Challenge a friend").setItems(opts,(d,which)->{String[] q=codes[which].split("-");String code=TrainerCoach.makeCode(q[0].substring(0,1),Long.parseLong(q[0].substring(1)),Integer.parseInt(q[1]),LocalAccount.name(this));
                TrainerCoach.join(this,code);renderPageNow();shareSheet("Send your challenge","I challenge you on Kadamm: "+opts[which]+". Enter this code in Kadamm › Trainer › Challenge a friend: "+code,null,null);}).show();});
        enter.setOnClickListener(v->{android.widget.EditText in=new android.widget.EditText(this);in.setHint("KDM-S70000-7-Name");in.setSingleLine(true);FrameLayout box=new FrameLayout(this);box.setPadding(dp(20),dp(8),dp(20),0);box.addView(in);
            dialog().setTitle("Enter a challenge code").setView(box).setPositiveButton("Join",(d,w)->{String err=TrainerCoach.join(this,in.getText().toString());if(err!=null)toast(err);else{toast("Challenge joined: it starts today");renderPageNow();}}).setNegativeButton("Cancel",null).show();});
    }
    private String prettyDay(String key){try{return new java.text.SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(key));}catch(Exception e){return key;}}
    private void renderWatchFitness(JSONArray h){
        JSONObject v=Vitals.all(this);
        LinearLayout c=metricCard("YOUR WATCH","","Kadamm works with any Wear OS watch (Galaxy Watch, Pixel Watch and others): install Kadamm on the watch to start and finish walks from your wrist, see today's challenges, and add your watch's heart rate to sessions. Sleep, resting heart rate and VO2max come from any watch that shares into Health Connect, including Samsung Health, Fitbit, Zepp and Garmin.");
        TextView status=tx("Looking for your watch…",13,muted,true);c.addView(status);
        try{com.google.android.gms.wearable.Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes->{if(nodes.isEmpty()){status.setText("No Wear OS watch connected");status.setTextColor(muted);}else{status.setText("⌚  Connected: "+nodes.get(0).getDisplayName());status.setTextColor(positive);}})
            .addOnFailureListener(e->status.setText("No Wear OS watch paired with this phone"));}catch(Throwable t){status.setText("Wear OS isn't available on this phone");}
        java.util.TreeMap<String,Double> sleep=Vitals.series(v,"sleep"),rhr=Vitals.series(v,"rhr"),vo2=Vitals.series(v,"vo2");
        if(sleep.isEmpty()&&rhr.isEmpty()&&vo2.isEmpty()){TextView t=tx(DaySteps.hcSteps(this)?"No sleep, resting heart rate or VO2max shared yet. Wear your watch to sleep and allow these in Health Connect.":"Connect Health Connect to bring in your watch's sleep, resting heart rate and VO2max.",13,ink,false);t.setPadding(0,dp(8),0,0);c.addView(t);
            TextView go=tx("Health Connect permissions  ›",13,lime,true);go.setPadding(0,dp(10),0,0);go.setOnClickListener(x->startActivity(new Intent(this,HealthConnectActivity.class)));c.addView(go);}
        else{java.util.List<String[]> cells=new java.util.ArrayList<>();
            if(!sleep.isEmpty()){double last=sleep.lastEntry().getValue();double avg=0;for(double x:sleep.values())avg+=x;avg/=sleep.size();cells.add(new String[]{"Last night",String.format(Locale.US,"%dh %02dm",Math.round(last)/60,Math.round(last)%60),String.format(Locale.US,"avg %dh %02dm",Math.round(avg)/60,Math.round(avg)%60)});}
            if(!rhr.isEmpty()){double now=rhr.lastEntry().getValue();java.util.List<Double> vals=new java.util.ArrayList<>(rhr.values());double first=Vitals.median(vals.subList(0,Math.max(1,vals.size()/2)));cells.add(new String[]{"Resting HR",Math.round(now)+"",vals.size()>=6?String.format(Locale.US,"bpm (%+.0f)",now-first):"bpm"});}
            if(!vo2.isEmpty())cells.add(new String[]{"VO₂max",String.format(Locale.US,"%.1f",vo2.lastEntry().getValue()),"ml/kg/min"});
            statGrid(c,cells.toArray(new String[0][]),10);
            String src=Vitals.sources(v);if(!src.isEmpty()){TextView t=tx("From "+src+" via Health Connect",11,muted,false);t.setPadding(0,dp(8),0,0);c.addView(t);}
            double[] sv=Vitals.sleepVsSteps(this);if(sv!=null){double diff=sv[0]-sv[1];TextView t=tx(String.format(Locale.US,Math.abs(diff)<5?"Your sleep is about the same after active and quiet days (%,d+ steps or fewer).":diff>0?"After days of %,d+ steps you sleep %d minutes longer than after quieter days.":"After days of %,d+ steps you sleep %d minutes less than after quieter days.",Math.round(sv[2]),Math.round(Math.abs(diff))),13,ink,true);t.setPadding(0,dp(10),0,0);c.addView(t);
                TextView n=tx("Based on "+Math.round(sv[3])+" nights.",11,muted,false);c.addView(n);}}
        // Fitness age
        Vitals.FitnessAge fa=Vitals.fitnessAge(this,h);
        LinearLayout fc=metricCard("FITNESS AGE","","The age whose average VO2max matches yours (from the HUNT fitness study). VO2max comes from your watch when it measures it; otherwise Kadamm estimates it from walks with heart rate: the oxygen cost of your walking speed, scaled by how hard your heart worked against your measured resting heart rate. Estimates are labelled.");
        if(fa.need!=null){String msg="age".equals(fa.need)?"Add your age in Profile to see your fitness age.":"rest".equals(fa.need)?"Needs a resting heart rate from your watch (through Health Connect), or VO2max from your watch.":"Needs 2 or more walks of 10+ minutes with heart rate from a watch in the last 60 days ("+fa.sessions+" so far).";fc.addView(tx(msg,13,ink,false));}
        else{LinearLayout r=row();r.setGravity(Gravity.BOTTOM);fc.addView(r);r.addView(tx(String.valueOf(fa.fitAge),40,fa.fitAge<=fa.age?positive:warning,true));TextView yr=tx(" years",14,muted,false);yr.setPadding(0,0,0,dp(8));r.addView(yr);
            fc.addView(tx(fa.fitAge<fa.age?(fa.age-fa.fitAge)+" years younger than your age ("+fa.age+")":fa.fitAge==fa.age?"The same as your age":(fa.fitAge-fa.age)+" years older than your age ("+fa.age+"): brisk walks bring it down",13,ink,true));
            fc.addView(tx(String.format(Locale.US,"VO₂max %.1f ml/kg/min · %s",fa.vo2,fa.fromWatch?"measured by your watch":"estimate from "+fa.sessions+" walks"),12,muted,false));}
        String sex=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("sex","");
        TextView sx=tx(("m".equals(sex)?"Compared with men":"f".equals(sex)?"Compared with women":"Compared with everyone")+" · change  ›",12,lime,true);sx.setPadding(0,dp(10),0,0);fc.addView(sx);
        sx.setOnClickListener(x->dialog().setTitle("Compare with").setItems(new String[]{"Men","Women","Everyone"},(d,w)->{getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putString("sex",w==0?"m":w==1?"f":"").apply();renderPageNow();}).show());
    }
    private void renderRecordsTrend(JSONArray h){
        java.util.List<Vitals.Month> ms=Vitals.records(this,h);boolean any=false;for(Vitals.Month m:ms)if(m.bestDay>0||m.sessions>0)any=true;if(!any)return;
        LinearLayout c=metricCard("RECORDS OVER TIME","6 months","Each month's biggest day, longest walk and fastest pace (walks of 1 km or more with GPS distance). Green marks an all-time best in these six months.");
        long topDay=0;double topKm=0,topPace=-1;for(Vitals.Month m:ms){topDay=Math.max(topDay,m.bestDay);topKm=Math.max(topKm,m.longestKm);if(m.bestPaceS>0&&(topPace<0||m.bestPaceS<topPace))topPace=m.bestPaceS;}
        LinearLayout hd=row();c.addView(hd,lp(-1,-2));String[] heads={"","Best day","Longest","Fastest"};float[] ws={.8f,1.3f,1f,1f};
        for(int i=0;i<4;i++){TextView t=tx(heads[i],11,muted,true);t.setGravity(i==0?Gravity.START:Gravity.END);hd.addView(t,new LinearLayout.LayoutParams(0,-2,ws[i]));}
        for(int k=ms.size()-1;k>=0;k--){Vitals.Month m=ms.get(k);LinearLayout r=row();r.setPadding(0,dp(9),0,dp(9));c.addView(r,lp(-1,-2));
            String[] vals={m.label,m.bestDay>0?String.format(Locale.US,"%,d",m.bestDay):"—",m.longestKm>0?String.format(Locale.US,"%.2f km",m.longestKm):"—",m.bestPaceS>0?paceText(m.bestPaceS):"—"};
            boolean[] top={false,m.bestDay>0&&m.bestDay==topDay,m.longestKm>0&&m.longestKm==topKm,m.bestPaceS>0&&m.bestPaceS==topPace};
            for(int i=0;i<4;i++){TextView t=tx(vals[i],13,top[i]?positive:ink,i==0||top[i]);t.setGravity(i==0?Gravity.START:Gravity.END);r.addView(t,new LinearLayout.LayoutParams(0,-2,ws[i]));}}
    }
    /** Trainer page: the coach's message, today's challenges in full, and the report on your last four weeks. */
    private void renderCoachSection(JSONArray h){
        TrainerCoach.Day cd=TrainerCoach.today(this,h);
        LinearLayout c=card();c.setPadding(dp(20),dp(20),dp(20),dp(20));coachBackground(c,cd.allDone);coachHeader(c,cd,true);
        LinearLayout bub=col();bub.setPadding(dp(16),dp(14),dp(16),dp(14));GradientDrawable bg2=shape(darkTheme?blend(panel,Color.BLACK,.25f):blend(panel,Color.WHITE,.6f),18);bg2.setCornerRadii(new float[]{dp(4),dp(4),dp(18),dp(18),dp(18),dp(18),dp(18),dp(18)});bub.setBackground(bg2);
        LinearLayout.LayoutParams bup=lp(-1,-2);bup.topMargin=dp(16);c.addView(bub,bup);
        TextView m=tx(cd.message,15,ink,false);m.setLineSpacing(dp(4),1f);bub.addView(m);TextView so=tx(cd.signoff,13,cd.allDone?positive:violet,true);so.setLineSpacing(dp(3),1f);so.setPadding(0,dp(10),0,0);bub.addView(so);
        TextView ht=tx("TODAY'S CHALLENGES",10,muted,true);ht.setLetterSpacing(.08f);LinearLayout.LayoutParams htp=lp(-1,-2);htp.topMargin=dp(22);c.addView(ht,htp);
        for(int i=0;i<cd.list.size();i++){TrainerCoach.Challenge ch=cd.list.get(i);LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(14),dp(14),dp(14),dp(14));r.setBackground(shape(ch.done?blend(panel,positive,darkTheme?.20f:.12f):raised,16));
            LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(10);c.addView(r,rp);
            TextView ic=tx(ch.done?"✓":coachIcon(ch.id),18,ch.done?Color.WHITE:coachColor(i),true);ic.setGravity(Gravity.CENTER);GradientDrawable ig=new GradientDrawable();ig.setShape(GradientDrawable.OVAL);ig.setColor(ch.done?positive:blend(panel,coachColor(i),.22f));ic.setBackground(ig);r.addView(ic,lp(44,44));
            LinearLayout l=col();LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,-2,1);llp.leftMargin=dp(14);r.addView(l,llp);
            l.addView(tx(ch.title,15,ink,true));l.addView(tx(ch.progress(),12,ch.done?positive:muted,ch.done));l.addView(coachBar(ch,i),coachBarLp());}
        TextView how=tx("A new set every day, picked from 11 kinds so none repeats from yesterday. Targets come from your own last four weeks: a good day for you, not your best. Finish all three and tomorrow's rise 5 %; miss most and they ease.",11,muted,false);how.setLineSpacing(dp(2),1f);LinearLayout.LayoutParams hwp=lp(-1,-2);hwp.topMargin=dp(14);c.addView(how,hwp);
        maybeCoachCelebrate(cd);
        // Report
        java.util.List<TrainerCoach.Finding> rep=TrainerCoach.report(this,h);
        LinearLayout rc=metricCard("COACH'S REPORT","last 4 weeks","What your last 28 days of steps, sessions, pace, cadence and challenges show, compared with the weeks before. Green is going well, amber is worth a look.");
        if(rep.isEmpty())rc.addView(tx("A week of days is enough for the first report. Keep walking and check back.",13,muted,false));
        for(int i=0;i<rep.size();i++){TrainerCoach.Finding f=rep.get(i);LinearLayout r=row();r.setPadding(0,dp(12),0,dp(12));if(i>0){View sep=new View(this);sep.setBackgroundColor(line);rc.addView(sep,lp(-1,1));}rc.addView(r,lp(-1,-2));
            View dot=new View(this);GradientDrawable dg=new GradientDrawable();dg.setShape(GradientDrawable.OVAL);dg.setColor(f.tone>0?positive:f.tone<0?warning:muted);dot.setBackground(dg);LinearLayout.LayoutParams dlp=lp(10,10);dlp.topMargin=dp(5);dlp.rightMargin=dp(12);r.addView(dot,dlp);
            LinearLayout l=col();r.addView(l,new LinearLayout.LayoutParams(0,-2,1));LinearLayout top=row();l.addView(top,lp(-1,-2));
            top.addView(tx(f.title,13,muted,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(tx(f.value,14,ink,true));
            TextView v=tx(f.verdict,13,ink,false);v.setLineSpacing(dp(2),1f);v.setPadding(0,dp(4),0,0);l.addView(v);}
    }
    private void renderTrainer(){
        JSONArray h=history();brand("KADAMM TRAINER","Your training","A plan built from your own walks, steps and pace.");
        if(!SessionMetrics.ready()){LinearLayout w=card();w.addView(tx("Analysing your sessions…",13,muted,false));handler.postDelayed(()->{if("Trainer".equals(page))renderPageNow();},800);return;}
        renderCoachSection(h);renderWeekly(h);renderFriends(h);renderWatchFitness(h);renderRecordsTrend(h);
        Trainer.State st=trainerState(h);KadammTheme th=chartTheme();int teal=GoalCapsule.COLORS[0];
        // Stage ladder
        LinearLayout sc=card();TextView se=tx("STAGE "+(st.stage+1)+" OF 6",10,teal,true);se.setLetterSpacing(.08f);sc.addView(se);sc.addView(tx(Trainer.STAGES[st.stage],24,ink,true));
        LinearLayout bar=row();LinearLayout.LayoutParams bp=lp(-1,8);bp.topMargin=dp(10);sc.addView(bar,bp);
        for(int i=0;i<6;i++){View v=new View(this);v.setBackground(shape(i<=st.stage?teal:raised,4));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)vp.leftMargin=dp(4);bar.addView(v,vp);}
        LinearLayout names=row();LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(4);sc.addView(names,np);
        for(int i=0;i<6;i++){TextView t=tx(Trainer.STAGES[i].replace(" walker","").replace(" runner",""),9,i==st.stage?teal:muted,i==st.stage);t.setGravity(Gravity.CENTER);names.addView(t,new LinearLayout.LayoutParams(0,-2,1));}
        // Today
        Trainer.Workout w=st.today;
        if(w!=null){LinearLayout tc=metricCard("TODAY'S WORKOUT","","Today's part of your weekly plan. Pace ranges come from your pace zones around your threshold pace ("+(st.threshold>0?paceText(st.threshold)+" /km":"not known yet")+"). The plan eases hard days when your form is below −20 or your load ratio above 1.5, and adds a little when you're fresh and under-loaded.");
            tc.addView(tx((w.done?"✓ ":"")+w.title,19,w.done?positive:ink,true));tc.addView(tx(w.detail,13,muted,false));
            TextView why=tx(w.why,12,ink,false);LinearLayout.LayoutParams wp=lp(-1,-2);wp.topMargin=dp(8);tc.addView(why,wp);
            if(!w.rest&&!w.done){Button go=button("▶  Start this workout",teal,Color.WHITE);go.setTextSize(15);LinearLayout.LayoutParams gp=lp(-1,48);gp.topMargin=dp(12);tc.addView(go,gp);
                go.setOnClickListener(v->{if(prefs.getBoolean("active",false)){openLive();return;}activityType=w.run?"Run":"Walk";prefs.edit().putString("activityMode","outdoor").apply();toast("Target: "+w.detail);beginRecord();});}}
        // Next stage
        if(!st.next.isEmpty()){LinearLayout nc=metricCard("NEXT STAGE · "+Trainer.STAGES[st.stage+1].toUpperCase(Locale.US),"","Every check must be met at once, measured over the last three weeks (and 60 days for distance and running). Stages move up automatically.");
            for(Trainer.Check ch:st.next){TextView t=tx((ch.done?"✓  ":"○  ")+ch.text,13,ch.done?positive:ink,false);t.setPadding(0,dp(6),0,0);nc.addView(t);}}
        // The week
        LinearLayout wc=metricCard("THIS WEEK","","Your stage's weekly plan. A day is ticked when that day's sessions add up to 70 % of its minutes; rest and steps days tick once the day has passed or you reach your step goal.");
        for(Trainer.Workout d:st.week){
            LinearLayout r=row();r.setPadding(dp(8),dp(8),dp(8),dp(8));if(d.today)r.setBackground(shape(blend(panel,teal,darkTheme?.16f:.10f),10));LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(4);wc.addView(r,rp);
            r.addView(tx(d.dayName,13,d.today?teal:ink,true),lp(40,-2));LinearLayout l=col();r.addView(l,new LinearLayout.LayoutParams(0,-2,1));l.addView(tx(d.title,13,ink,d.today));l.addView(tx(d.detail,11,muted,false));
            r.addView(tx(d.done?"✓":d.today?"today":d.past?"missed":"○",12,d.done?positive:d.today?teal:d.past?warning:muted,true));
        }
        // Forecast
        if(st.history!=null&&st.history.length>=2){LinearLayout fc=metricCard("FORECAST","4 weeks","Fitness (the 42-day average of your daily effort, as in Reports) for the last four weeks, then run forward four weeks: teal if you follow this plan, coral if you stop.");
            double pEnd=st.plan[st.plan.length-1],sEnd=st.stop[st.stop.length-1];
            fc.addView(tx(String.format(Locale.US,"Fitness %.0f → %.0f with the plan, %.0f if you stop",st.fitness,pEnd,sEnd),13,ink,true));
            LinearLayout.LayoutParams fp=lp(-1,150);fp.topMargin=dp(8);fc.addView(new MetricViews.Forecast(this,th,st.history,st.plan,st.stop),fp);
            statGrid(fc,new String[][]{{"Week steps so far",String.format(Locale.US,"%,d",st.weekStepsSoFar),""},{"On track for",String.format(Locale.US,"%,d",st.weekStepsProjected),"this week"},{"Month challenge",Math.round(Math.min(1,st.monthOnTrack)*100)+"","% on track"}},10);}
        // Tips
        if(!st.tips.isEmpty()){LinearLayout tc2=card();GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,violet,darkTheme?.20f:.10f),panel});g.setCornerRadius(dp(16));tc2.setBackground(g);
            TextView h2=tx("✦  COACH'S NOTES",10,violet,true);h2.setLetterSpacing(.08f);tc2.addView(h2);for(String t:st.tips){TextView x=tx("• "+t,13,ink,false);x.setPadding(0,dp(8),0,0);tc2.addView(x);}}
        TextView imp=tx("Bring in older walks from Samsung Health, Zepp or Google Fit  ›",12,lime,true);imp.setPadding(0,dp(14),0,0);imp.setOnClickListener(v->navigate("Import"));content.addView(imp);
    }
    // ---- Import ----
    private int importDays=180;
    private boolean installed(String pkg){try{getPackageManager().getPackageInfo(pkg,0);return true;}catch(Exception e){return false;}}
    private void renderImport(){
        brand("YOUR DATA","Import history","Walks, runs and daily steps from Samsung Health, Zepp, Google Fit and other apps, through Health Connect.");
        SharedPreferences hp=getSharedPreferences("kadamm_health",MODE_PRIVATE);boolean hc=hp.getBoolean("healthConnected",false);
        LinearLayout src=metricCard("SOURCES","","Kadamm reads what these apps share into Health Connect. Google is closing Google Fit's own data access in 2026; Google Fit, Samsung Health and Zepp all share into Health Connect instead, so that's the one path that keeps working.");
        sourceRow(src,"Samsung Health",installed("com.sec.android.app.shealth")?(hc?"Connected":"Installed · connect Health Connect"):"Not installed",hc&&installed("com.sec.android.app.shealth"),"Steps by day, walks and runs, heart rate");
        sourceRow(src,"Zepp (Amazfit)",installed("com.huami.watch.hmwatchmanager")?"Installed":"Not installed",false,"In Zepp: Profile → Add accounts → Health Connect, then allow Steps and Exercise");
        sourceRow(src,"Google Fit",installed("com.google.android.apps.fitness")?"Installed":"Not installed",false,"In Google Fit: Profile → Settings → Manage connected apps → Health Connect");
        LinearLayout rc=card();rc.addView(tx("How far back",15,ink,true));rc.addView(tx("Past 30 days need no extra permission; further back asks Health Connect for \"past data\".",11,muted,false));
        LinearLayout chips=row();LinearLayout.LayoutParams cp=lp(-1,38);cp.topMargin=dp(10);rc.addView(chips,cp);int[] opts={30,180,365,730};String[] lab={"30 days","6 months","1 year","2 years"};
        for(int i=0;i<4;i++){int v=opts[i];boolean on=v==importDays;Button b=button(lab[i],on?lime:raised,on?accentOn:ink);b.setTextSize(12);b.setPadding(0,0,0,0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(i>0)p.leftMargin=dp(6);chips.addView(b,p);b.setOnClickListener(x->{importDays=v;renderPageNow();});}
        Button go=button("Import "+lab[java.util.Arrays.binarySearch(opts,importDays)],lime,accentOn);go.setTextSize(15);LinearLayout.LayoutParams gp=lp(-1,50);gp.topMargin=dp(14);rc.addView(go,gp);
        go.setOnClickListener(v->confirmChange("Import "+lab[java.util.Arrays.binarySearch(opts,importDays)].toLowerCase(Locale.US)+"?","Kadamm will add walks and runs from Health Connect as sessions marked with their source, skipping any that overlap your Kadamm sessions, and fill in daily steps for those days (keeping the larger figure). You can remove imported sessions later.",this::runImport,null));
        long at=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getLong("importedAt",0);
        if(at>0){TextView last=tx("Last import: "+new SimpleDateFormat("d MMM, h:mm a",Locale.getDefault()).format(new Date(at)),11,muted,false);LinearLayout.LayoutParams lp2=lp(-1,-2);lp2.topMargin=dp(8);rc.addView(last,lp2);}
        int imported=0;JSONArray h=history();for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o!=null&&o.optBoolean("imported",false))imported++;}
        if(imported>0){LinearLayout ic=card();ic.addView(tx(imported+" imported sessions in History",14,ink,true));Button rm=button("Remove imported sessions",raised,critical);LinearLayout.LayoutParams rp=lp(-1,44);rp.topMargin=dp(10);ic.addView(rm,rp);final int cnt=imported;
            rm.setOnClickListener(v->dialog().setTitle("Remove "+cnt+" imported sessions?").setMessage("They'll be deleted from Kadamm (not from the other apps). Your own sessions stay.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,x)->{JSONArray all=history(),kept=new JSONArray();for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o!=null&&!o.optBoolean("imported",false))kept.put(o);}historyPrefs.edit().putString("sessions",kept.toString()).apply();toast(cnt+" removed");renderPageNow();}).show());}
        Button perms=button("Health Connect permissions",raised,ink);LinearLayout.LayoutParams pp=lp(-1,46);pp.topMargin=dp(12);content.addView(perms,pp);perms.setOnClickListener(v->startActivity(new Intent(this,HealthConnectActivity.class)));
    }
    private void sourceRow(LinearLayout parent,String name,String state,boolean ok,String how){
        LinearLayout r=col();r.setPadding(0,dp(10),0,dp(2));parent.addView(r);LinearLayout top=row();r.addView(top,lp(-1,-2));
        top.addView(tx(name,14,ink,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(tx(state,11,ok?positive:muted,true));r.addView(tx(how,11,muted,false));
    }
    private void runImport(){
        LinearLayout box=col();box.setPadding(dp(22),dp(14),dp(22),dp(6));TextView msg=tx("Starting…",14,ink,false);box.addView(msg);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setIndeterminate(true);LinearLayout.LayoutParams bp=lp(-1,10);bp.topMargin=dp(12);box.addView(bar,bp);
        AlertDialog wait=dialog().setTitle("Importing").setView(box).setCancelable(false).create();wait.show();
        HealthImport.run(this,importDays,new HealthImport.Listener(){
            @Override public void progress(String m){msg.setText(m);}
            @Override public void done(int sessions,int days,String note){wait.dismiss();
                String body=(sessions==0?"No new walks or runs to add.":sessions+(sessions==1?" walk or run added.":" walks and runs added."))+(days>0?String.format(Locale.US,"\nDaily steps filled in for %d days.",days):"")+(note!=null?"\n\n"+note:"");
                AlertDialog.Builder b=dialog().setTitle(sessions>0||days>0?"Import finished":"Nothing imported").setMessage(body).setPositiveButton("OK",null);
                if(note!=null&&note.contains("Allow"))b.setNeutralButton("Open permissions",(d,w)->startActivity(new Intent(MainActivity.this,HealthConnectActivity.class)));
                b.show();SessionMetrics.warm(MainActivity.this,history());renderPageNow();}
        });
    }
    // ---- App updates (from Kadamm's GitHub releases, like Aalap) ----
    private static boolean updateChecked;
    /** Once per app start: offers a newer Kadamm on opening, at most once a day for the same version. */
    private void checkForUpdateOnce(){
        if(updateChecked)return;updateChecked=true;
        AppUpdate.check(this,false,(r,ok)->{if(r!=null&&AppUpdate.offerOnLaunch(this,r.version)&&resumed&&authLayer==null)showUpdate(r);if("Profile".equals(page))renderPageNow();});
    }
    private void showUpdate(AppUpdate.Release r){
        String notes=AppUpdate.notesPreview(r);
        dialog().setTitle("Kadamm "+r.version+" is available").setMessage("You have "+AppUpdate.current(this)+". Updating keeps all your sessions, steps and settings."+(notes.isEmpty()?"":"\n\nWhat's new\n"+notes)).setNegativeButton("Later",null).setPositiveButton("Update now",(d,w)->startUpdate(r)).show();
    }
    private void startUpdate(AppUpdate.Release r){
        if(!AppUpdate.canInstall(this)){dialog().setTitle("Allow Kadamm to install updates").setMessage("Android asks once: turn on \"Allow from this source\" for Kadamm on the next screen, then come back and tap Update now again.").setNegativeButton("Cancel",null).setPositiveButton("Open setting",(d,w)->AppUpdate.askInstallPermission(this)).show();return;}
        LinearLayout box=col();box.setPadding(dp(22),dp(14),dp(22),dp(6));TextView msg=tx("Downloading… 0%",14,ink,false);box.addView(msg);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(lime));LinearLayout.LayoutParams bp=lp(-1,10);bp.topMargin=dp(12);box.addView(bar,bp);
        AlertDialog wait=dialog().setTitle("Updating to "+r.version).setView(box).setCancelable(false).create();wait.show();
        AppUpdate.download(this,r,pc->{bar.setProgress(pc);msg.setText(pc>=100?"Checking the download…":"Downloading… "+pc+"%");},(apk,problem)->{
            wait.dismiss();if(problem!=null){dialog().setTitle("Update not installed").setMessage(problem).setPositiveButton("OK",null).show();return;}
            try{AppUpdate.install(this,apk);}catch(Exception e){toast("Couldn't open the installer");}
        });
    }
    /** Profile: this version, and an update when there is one. */
    private void renderAppVersion(){
        AppUpdate.Release r=AppUpdate.known(this);LinearLayout c=card();
        LinearLayout row=row();c.addView(row,lp(-1,-2));android.widget.ImageView ic=icon(R.drawable.ic_download,r!=null?accentOn:muted,9);ic.setBackground(shape(r!=null?lime:raised,12));row.addView(ic,lp(40,40));
        LinearLayout l=col();LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1);lp2.leftMargin=dp(12);row.addView(l,lp2);
        l.addView(tx(r!=null?"Kadamm "+r.version+" is available":"Kadamm "+AppUpdate.current(this),15,ink,true));
        l.addView(tx(r!=null?"You have "+AppUpdate.current(this)+" · your data stays":AppUpdate.fresh(this)?"Up to date":"Checking for updates…",11,r!=null?lime:muted,false));
        if(r!=null){Button b=button("Update now",lime,accentOn);LinearLayout.LayoutParams bp=lp(-1,46);bp.topMargin=dp(12);c.addView(b,bp);b.setOnClickListener(v->startUpdate(r));}
        else{TextView chk=tx("Check now",12,lime,true);chk.setPadding(dp(10),dp(6),0,dp(6));row.addView(chk);chk.setOnClickListener(v->{toast("Checking…");AppUpdate.check(this,true,(x,ok)->{if(!ok)toast("Couldn't reach GitHub");else if(x==null)toast("You're on the latest version");renderPageNow();if(x!=null)showUpdate(x);});});}
    }
    private String summaryDay;
    /** All of a day's routes as one route, each session starting after a gap. */
    private String mergedRoute(java.util.List<JSONObject> list){
        JSONArray out=new JSONArray();
        // Sessions that barely moved (under 50 m) add a stray point, not a route, and would zoom the map out.
        for(JSONObject o:list){if(o.optDouble("distanceKm",0)<.05)continue;try{JSONArray r=new JSONArray(o.optString("route","[]"));for(int i=0;i<r.length();i++){JSONObject p=new JSONObject(r.getJSONObject(i).toString());if(i==0&&out.length()>0)p.put("break",true);out.put(p);}}catch(Exception ignored){}}
        return out.toString();
    }
    /** Which session did best on a measure: {value, session}; higher is better unless {@code low}. */
    private Object[] bestOf(java.util.List<JSONObject> list,java.util.function.ToDoubleFunction<JSONObject> f,boolean low){
        JSONObject best=null;double bv=low?Double.MAX_VALUE:-Double.MAX_VALUE;for(JSONObject o:list){double v=f.applyAsDouble(o);if(v<=0||Double.isNaN(v))continue;if(low?v<bv:v>bv){bv=v;best=o;}}return best==null?null:new Object[]{bv,best};
    }
    /**
     * A day's sessions as one: the routes together on a map, totals and averages, relative effort against your
     * recent days, intensity and pace zones summed, and which session was best at each thing.
     */
    private void renderDaySummary(){
        String day=summaryDay==null?todayKey():summaryDay;JSONArray h=history();KadammTheme th=chartTheme();
        java.util.List<JSONObject> list=new java.util.ArrayList<>();for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o!=null&&day.equals(o.optString("day","")))list.add(o);}
        DaySummary d=DaySummary.of(this,h,day);
        brand(dayTitle(day).toUpperCase(Locale.getDefault()),"Day summary",d.sessions+(d.sessions==1?" session":" sessions")+(d.auto>0?" · "+d.auto+" auto":"")+" · everything added up");
        if(list.isEmpty()){LinearLayout e=card();e.addView(tx("No sessions on this day.",13,muted,false));return;}
        // Map of every route
        String merged=mergedRoute(list);
        if(merged.contains("lat")){RouteView rv=new RouteView(this);rv.setInteractive(true);rv.setTheme(bg,raised,lime,darkTheme);rv.setOverview(true);rv.setRoute(merged);rv.setClipToOutline(true);rv.setBackground(shape(panel,16));content.addView(rv,lp(-1,280));
            content.addView(tx("Every route you walked this day · © OpenStreetMap contributors",10,muted,false));}
        int sesAt=Reminders.get(this,"sesAt",23*60);Calendar nowC=Calendar.getInstance();boolean final_=!day.equals(todayKey())||nowC.get(Calendar.HOUR_OF_DAY)*60+nowC.get(Calendar.MINUTE)>=sesAt;
        list.removeIf(o->!DaySummary.counts(o));
        LinearLayout ov=metricCard(final_?"FINAL SUMMARY":"SO FAR TODAY","",(final_?"The day is complete: everything below is final.":"The summary is final at "+clock12(sesAt)+", when it's also sent as a notification; until then it grows with each session.")+" Totals add every session together; averages are worked out over the whole day (pace = total moving time ÷ total distance, cadence weighted by each session's moving time)."+(d.skipped>0?"\n\n"+d.skipped+(d.skipped==1?" short recording":" short recordings")+" under 100 steps (or rides) are left out.":""));
        // The whole day first (the same total as Home), then what the sessions added up to.
        ov.addView(valueUnit(String.format(Locale.US,"%,d",d.daySteps),"steps today",34,ink));
        if(d.daySteps>0){int pct=(int)Math.round(d.steps*100d/d.daySteps);
            LinearLayout split=row();split.setClipToOutline(true);split.setBackground(shape(raised,5));LinearLayout.LayoutParams spp=lp(-1,10);spp.topMargin=dp(8);ov.addView(split,spp);
            View a1=new View(this);a1.setBackgroundColor(GoalCapsule.COLORS[0]);split.addView(a1,new LinearLayout.LayoutParams(0,-1,Math.max(1,pct)));View a2=new View(this);split.addView(a2,new LinearLayout.LayoutParams(0,-1,Math.max(1,100-pct)));
            ov.addView(tx(String.format(Locale.US,"%,d in %d sessions (%d%%) · %,d from everyday walking",d.steps,d.sessions,pct,d.outsideSteps),12,muted,false));}
        TextView wl=tx("WHOLE DAY",10,muted,true);wl.setLetterSpacing(.08f);LinearLayout.LayoutParams wlp=lp(-1,-2);wlp.topMargin=dp(12);ov.addView(wl,wlp);
        statGrid(ov,new String[][]{{"Steps",String.format(Locale.US,"%,d",d.daySteps),""},{"Distance",String.format(Locale.US,"≈%.1f",d.dayKm),"km"},{"Active",String.valueOf(Math.round(d.dayKcal)),"kcal"}},6);
        double gain=0;for(JSONObject o:list){SessionMetrics m=SessionMetrics.of(this,o);if(m.gain>0)gain+=m.gain;}
        TextView tl=tx("IN SESSIONS · TOTALS",10,muted,true);tl.setLetterSpacing(.08f);LinearLayout.LayoutParams tlp=lp(-1,-2);tlp.topMargin=dp(12);ov.addView(tl,tlp);
        statGrid(ov,new String[][]{{"Total time",DaySummary.clock(d.elapsedS),""},{"Moving time",DaySummary.clock(d.movingS),""},{"Sessions",String.valueOf(d.sessions),""},
            {"Steps",d.steps>0?String.format(Locale.US,"%,d",d.steps):"—",""},{"Active",String.valueOf(Math.round(d.kcal)),"kcal"},{"Climb",gain>0?String.format(Locale.US,"%.0f",gain):"—",gain>0?"m":""},
            {"Effort",String.valueOf(Math.round(d.effort)),""},{"Intensity",String.valueOf(Math.round(d.intensity)),"min"},{"Distance",String.format(Locale.US,"%.2f",d.km),"km"}},6);
        TextView al=tx("AVERAGES",10,muted,true);al.setLetterSpacing(.08f);LinearLayout.LayoutParams alp=lp(-1,-2);alp.topMargin=dp(16);ov.addView(al,alp);
        statGrid(ov,new String[][]{{"Pace",paceText(d.pace()),"/km"},{"Cadence",d.cadence>0?String.valueOf(Math.round(d.cadence)):"—",d.cadence>0?"spm":""},{"Per session",DaySummary.clock(d.avgTime()),""},
            {"Distance each",String.format(Locale.US,"%.2f",d.avgKm()),"km"},{"Steps each",d.steps>0?String.format(Locale.US,"%,d",d.avgSteps()):"—",""},{"Effort each",d.sessions>0?String.valueOf(Math.round(d.effort/d.sessions)):"—",""}},6);
        if(d.skipped>0){TextView sk=tx(d.skipped+(d.skipped==1?" short recording":" short recordings")+" under 100 steps left out",11,muted,false);LinearLayout.LayoutParams skp=lp(-1,-2);skp.topMargin=dp(10);ov.addView(sk,skp);}
        // Session by session: every session side by side, the top one in each column highlighted.
        if(list.size()>=2){
            LinearLayout sb=metricCard("SESSION BY SESSION","","Each of the day's sessions side by side. The highest steps, distance and effort, and the fastest pace, are highlighted, so you can see which session did the most. The bottom line adds them up.");
            long maxSteps=0;double maxKm=0,maxEff=0,bestPace=Double.MAX_VALUE;
            for(JSONObject o:list){SessionMetrics x=SessionMetrics.of(this,o);maxSteps=Math.max(maxSteps,o.optLong("steps",0));maxKm=Math.max(maxKm,o.optDouble("distanceKm",0));maxEff=Math.max(maxEff,x.effort);if(x.paceMoving>0&&x.paceMoving<1800)bestPace=Math.min(bestPace,x.paceMoving);}
            LinearLayout hd=row();LinearLayout.LayoutParams hdp=lp(-1,-2);hdp.topMargin=dp(10);sb.addView(hd,hdp);float[] wts={1.25f,1f,.85f,.8f,1f};String[] hs={"Session","Steps","km","Effort","Pace"};
            for(int k=0;k<5;k++){TextView t=tx(hs[k],10,muted,false);if(k>0)t.setGravity(Gravity.END);hd.addView(t,new LinearLayout.LayoutParams(0,-2,wts[k]));}
            for(JSONObject o:list){SessionMetrics x=SessionMetrics.of(this,o);LinearLayout r=row();r.setPadding(0,dp(9),0,dp(9));r.setBackground(rippleOn(Color.TRANSPARENT,8));sb.addView(r,lp(-1,-2));
                r.addView(tx(o.optString("time",""),13,ink,true),new LinearLayout.LayoutParams(0,-2,wts[0]));
                long st=o.optLong("steps",0);double kmv=o.optDouble("distanceKm",0);boolean bp=x.paceMoving>0&&Math.abs(x.paceMoving-bestPace)<.5;
                cellMax(r,String.format(Locale.US,"%,d",st),st==maxSteps,wts[1]);cellMax(r,String.format(Locale.US,"%.2f",kmv),kmv==maxKm,wts[2]);cellMax(r,String.valueOf(Math.round(x.effort)),x.effort==maxEff,wts[3]);cellMax(r,paceText(x.paceMoving),bp,wts[4]);
                r.setOnClickListener(v->{lastSession=o;page="Detail";renderPage();});}
            View dv=new View(this);dv.setBackgroundColor(line);sb.addView(dv,lp(-1,1));
            LinearLayout tot=row();tot.setPadding(0,dp(9),0,0);sb.addView(tot,lp(-1,-2));tot.addView(tx("Total",13,ink,true),new LinearLayout.LayoutParams(0,-2,wts[0]));
            String[] tv={String.format(Locale.US,"%,d",d.steps),String.format(Locale.US,"%.2f",d.km),String.valueOf(Math.round(d.effort)),paceText(d.pace())};for(int k=0;k<4;k++){TextView t=tx(tv[k],13,ink,true);t.setGravity(Gravity.END);tot.addView(t,new LinearLayout.LayoutParams(0,-2,wts[k+1]));}
            sb.addView(tx("Highlighted = the day's top session for that measure. Tap a row to open it.",10,muted,false));
        }
        // Relative effort: this day against your recent active days
        java.util.Map<String,Double> byDay=new java.util.HashMap<>();String from=DaySteps.key(dayMillis(day)-21L*86400000L);
        for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o==null)continue;String k=o.optString("day","");if(k.compareTo(from)>=0&&k.compareTo(day)<0&&!Insights.vehicle(o)){Double v=byDay.get(k);byDay.put(k,(v==null?0:v)+SessionMetrics.of(this,o).effort);}}
        LinearLayout ef=metricCard("RELATIVE EFFORT","day","The day's effort is all its sessions' effort added together. The bands compare it with your average day with sessions over the 21 days before.");
        Object[] topE=bestOf(list,o->SessionMetrics.of(this,o).effort,false);
        LinearLayout er=row();LinearLayout.LayoutParams erp=lp(-1,-2);erp.topMargin=dp(8);ef.addView(er,erp);er.addView(valueUnit(String.valueOf(Math.round(d.effort)),"effort",30,ink),new LinearLayout.LayoutParams(0,-2,1));
        if(topE!=null)er.addView(tx("Biggest: "+((JSONObject)topE[1]).optString("time"),12,muted,false));
        if(byDay.size()>=3){double avg=0;for(double v:byDay.values())avg+=v;avg/=byDay.size();LinearLayout.LayoutParams bp=lp(-1,126);bp.topMargin=dp(12);MetricViews.Bands bands=new MetricViews.Bands(this,th,d.effort/avg,violet);bands.setClipToOutline(true);bands.setBackground(shape(panel,12));ef.addView(bands,bp);
            TextView cap=tx(String.format(Locale.US,"Your average active day is %.0f, from %d days.",avg,byDay.size()),11,muted,false);LinearLayout.LayoutParams cp=lp(-1,-2);cp.topMargin=dp(8);ef.addView(cap,cp);}
        // Intensity zones summed
        long[] z=new long[5];for(JSONObject o:list){SessionMetrics m=SessionMetrics.of(this,o);long[] q=m.hrZoneS!=null?m.hrZoneS:m.zoneS;for(int i=0;i<5;i++)z[i]+=q[i];}long zt=0;for(long v:z)zt+=v;
        if(zt>=60){LinearLayout zc=metricCard("INTENSITY ZONES","all sessions","Time in each intensity zone, added up across the day's sessions (from cadence, GPS speed or heart rate, as each session allows).");
            zoneBars(zc,z,zt,MetricViews.ZONE_COLORS,SessionMetrics.ZONES);
            Object[] mi=bestOf(list,o->{SessionMetrics m=SessionMetrics.of(this,o);return m.intensityMin;},false);
            if(mi!=null){TextView t=tx(String.format(Locale.US,"%.0f intensity minutes in total · most from the %s session (%.0f min)",d.intensity,((JSONObject)mi[1]).optString("time"),(double)mi[0]),11,muted,false);LinearLayout.LayoutParams tp=lp(-1,-2);tp.topMargin=dp(10);zc.addView(t,tp);}}
        // Pace zones summed
        double thr=SessionMetrics.thresholdPace(this,h);long[] pz=new long[6];boolean anyPz=false;
        for(JSONObject o:list){long[] q=SessionMetrics.of(this,o).paceZones(thr);if(q!=null){anyPz=true;for(int i=0;i<6;i++)pz[i]+=q[i];}}
        long pt=0;for(long v:pz)pt+=v;
        if(anyPz&&pt>=60){LinearLayout pc=metricCard("PACE ZONES","all sessions","Time in each pace zone across the day's outdoor sessions, around your threshold pace ("+paceText(thr)+" /km): your best 30-minute pace.");
            String[] names=new String[6];for(int i=0;i<6;i++)names[i]="Z"+(i+1)+" "+SessionMetrics.PACE_ZONES[i];
            int[] blues={Color.rgb(181,212,244),Color.rgb(133,183,235),Color.rgb(55,138,221),Color.rgb(24,95,165),Color.rgb(12,68,124),Color.rgb(4,44,83)};zoneBars(pc,pz,pt,blues,names);}
        // Best of the day
        LinearLayout bc=metricCard("BEST OF THE DAY","","Which of the day's sessions did best at each measure. Tap one to open it.");
        bestRow(bc,"Fastest pace",bestOf(list,o->SessionMetrics.of(this,o).paceMoving,true),v->paceText(v)+" /km");
        bestRow(bc,"Longest distance",bestOf(list,o->o.optDouble("distanceKm",0),false),v->String.format(Locale.US,"%.2f km",v));
        bestRow(bc,"Most steps",bestOf(list,o->o.optBoolean("stepsAvailable",false)?o.optLong("steps",0):0,false),v->String.format(Locale.US,"%,d",Math.round(v)));
        bestRow(bc,"Highest cadence",bestOf(list,o->SessionMetrics.of(this,o).cadenceAvg,false),v->Math.round(v)+" spm");
        bestRow(bc,"Biggest effort",bestOf(list,o->SessionMetrics.of(this,o).effort,false),v->String.valueOf(Math.round(v)));
        bestRow(bc,"Most climbing",bestOf(list,o->SessionMetrics.of(this,o).gain,false),v->String.format(Locale.US,"+%.0f m",v));
        bestRow(bc,"Fastest 1 km",bestOf(list,o->{Double t=SessionMetrics.of(this,o).best.get(1000);return t==null?0:t;},true),MetricViews::mmss);
        // The sessions
        TextView sl=tx("SESSIONS",11,muted,true);sl.setLetterSpacing(.06f);LinearLayout.LayoutParams slp=lp(-1,-2);slp.topMargin=dp(18);content.addView(sl,slp);
        for(JSONObject o:list)sessionRow(o,()->{lastSession=o;page="Detail";renderPage();});
        Button share=button("Share this day",lime,accentOn);share.setTextSize(15);LinearLayout.LayoutParams shp=lp(-1,50);shp.topMargin=dp(14);content.addView(share,shp);share.setOnClickListener(v->shareDay(day));
        TextView when=tx("The day's summary also arrives as a notification at "+clock12(Reminders.get(this,"sesAt",23*60))+" · change in Notifications  ›",12,lime,true);when.setPadding(0,dp(12),0,0);when.setOnClickListener(v->navigate("Notifications"));content.addView(when);
    }
    private void cellMax(LinearLayout r,String text,boolean top,float w){TextView t=tx(text,13,top?GoalCapsule.COLORS[0]:ink,top);t.setGravity(Gravity.END);if(top){t.setBackground(shape(blend(panel,GoalCapsule.COLORS[0],darkTheme?.20f:.12f),6));t.setPadding(dp(4),dp(1),dp(4),dp(1));}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,w);p.leftMargin=dp(3);r.addView(t,p);}
    private long dayMillis(String day){try{return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(day).getTime();}catch(Exception e){return System.currentTimeMillis();}}
    private void zoneBars(LinearLayout parent,long[] z,long total,int[] colors,String[] names){
        for(int i=z.length-1;i>=0;i--){
            LinearLayout zr=col();LinearLayout.LayoutParams zp=lp(-1,-2);zp.topMargin=dp(i==z.length-1?10:10);parent.addView(zr,zp);
            LinearLayout l1=row();zr.addView(l1,lp(-1,-2));l1.addView(tx(names[i],13,ink,true),new LinearLayout.LayoutParams(0,-2,1));
            l1.addView(tx(String.format(Locale.US,"%d%%   ",Math.round(z[i]*100d/total)),12,ink,true));l1.addView(tx(MetricViews.mmss(z[i]),12,muted,false));
            FrameLayout track=new FrameLayout(this);track.setBackground(shape(raised,4));LinearLayout.LayoutParams tp=lp(-1,7);tp.topMargin=dp(5);zr.addView(track,tp);
            View fill=new View(this);fill.setBackground(shape(colors[i],4));track.addView(fill,new FrameLayout.LayoutParams(0,-1));final float f=(float)z[i]/total;track.post(()->{FrameLayout.LayoutParams fl=(FrameLayout.LayoutParams)fill.getLayoutParams();fl.width=f>0?Math.max(dp(5),(int)(track.getWidth()*f)):0;fill.setLayoutParams(fl);});
        }
    }
    private void bestRow(LinearLayout parent,String label,Object[] best,java.util.function.DoubleFunction<String> fmt){
        if(best==null)return;JSONObject o=(JSONObject)best[1];
        LinearLayout r=row();r.setPadding(0,dp(10),0,dp(4));r.setBackground(rippleOn(Color.TRANSPARENT,8));parent.addView(r,lp(-1,-2));
        LinearLayout l=col();r.addView(l,new LinearLayout.LayoutParams(0,-2,1));l.addView(tx(label,13,ink,true));l.addView(tx((o.optBoolean("auto",false)?"Auto · ":"")+sessionLabel(o)+" at "+o.optString("time",""),11,muted,false));
        r.addView(tx(fmt.apply((double)best[0]),15,lime,true));r.addView(tx("  ›",16,muted,false));
        r.setOnClickListener(v->{lastSession=o;page="Detail";renderPage();});
    }
    /** The Sessions page: every walk and run, grouped by day as stacks you can open. */
    private void renderSessions(){
        JSONArray all=history();brand("YOUR ACTIVITY","Sessions",all.length()+(all.length()==1?" walk or run":" walks and runs")+", grouped by day. Tap a day to open it.");
        renderTidyUp(all);
        renderRecentDays(all);
    }
    /**
     * Today's only link to past sessions: one stacked card with the latest day's summary and totals.
     * Opens the Sessions page, where every day is listed.
     */
    private void renderSessionsEntry(JSONArray all){
        TextView label=tx("YOUR SESSIONS",11,muted,true);label.setLetterSpacing(.06f);LinearLayout.LayoutParams rl=lp(-1,34);rl.topMargin=dp(20);content.addView(label,rl);
        if(all.length()==0){LinearLayout empty=card();empty.addView(tx("Your sessions will show up here.",13,muted,false));return;}
        JSONObject last=all.optJSONObject(all.length()-1);String day=last==null?"":last.optString("day","");
        int n=0,auto=0;double km=0;java.util.HashSet<String> dayset=new java.util.HashSet<>();
        for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o==null)continue;dayset.add(o.optString("day",""));if(day.equals(o.optString("day",""))){n++;if(o.optBoolean("auto",false))auto++;km+=o.optDouble("distanceKm",0);}}
        int layers=Math.min(2,dayset.size()-1);
        FrameLayout stack=new FrameLayout(this);stack.setClipChildren(false);LinearLayout.LayoutParams sp=lp(-1,-2);content.addView(stack,sp);
        for(int k=layers;k>=1;k--){View u=new View(this);u.setBackground(stackLayer(k,18));
            FrameLayout.LayoutParams up=new FrameLayout.LayoutParams(-1,-1);up.setMargins(dp(14*k),dp(20),dp(14*k),dp(7*(layers-k)));stack.addView(u,up);}
        LinearLayout front=row();front.setPadding(dp(14),dp(14),dp(12),dp(14));
        GradientDrawable fg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,lime,darkTheme?.12f:.06f),panel});fg.setCornerRadius(dp(18));fg.setStroke(dp(1),line);front.setBackground(ripple(fg));
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,-2);fp.bottomMargin=dp(7*layers);stack.addView(front,fp);
        android.widget.ImageView ic=icon(R.drawable.ic_layers,accentOn,10);ic.setBackground(shape(lime,14));front.addView(ic,lp(46,46));
        LinearLayout mid=col();LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-2,1);mp.leftMargin=dp(12);front.addView(mid,mp);
        mid.addView(tx("Your sessions",16,ink,true));
        String kinds=n+(n==1?" session":" sessions")+(auto==0?"":auto==n?" · auto":" · "+auto+" auto");
        mid.addView(tx(dayTitle(day)+" · "+kinds+String.format(Locale.US," · %.2f km",km),12,muted,false));
        mid.addView(tx(all.length()+" in total across "+dayset.size()+" days",12,lime,true));
        front.addView(tx("›",26,muted,false));
        front.setContentDescription("Your sessions. Latest: "+dayTitle(day)+", "+kinds+". Opens every session by day.");
        front.setOnClickListener(v->navigate("Sessions"));
    }
    private String dayTitle(String day){
        Calendar c=Calendar.getInstance();String today=DaySteps.key(c.getTimeInMillis());c.add(Calendar.DAY_OF_YEAR,-1);String yest=DaySteps.key(c.getTimeInMillis());
        if(day.equals(today))return "Today";if(day.equals(yest))return "Yesterday";
        try{java.util.Date d=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(day);long ago=(System.currentTimeMillis()-d.getTime())/86400000L;
            return new java.text.SimpleDateFormat(ago<7?"EEEE":"EEE, d MMM",Locale.getDefault()).format(d);}catch(Exception e){return day;}
    }
    private void dayStack(String day,java.util.List<JSONObject> list){
        int n=list.size(),auto=0;double km=0;long ms=0,steps=0;for(JSONObject o:list){if(o.optBoolean("auto",false))auto++;km+=o.optDouble("distanceKm",0);ms+=o.optLong("durationMs",0);if(o.optBoolean("stepsAvailable",false))steps+=o.optLong("steps",0);}
        boolean open=openDays.contains(day);int layers=Math.min(2,n-1);
        FrameLayout stack=new FrameLayout(this);stack.setClipChildren(false);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(10);content.addView(stack,sp);
        // Cards peeking out underneath, one per extra session (up to two).
        View[] under=new View[layers];
        for(int k=layers;k>=1;k--){View u=new View(this);u.setBackground(stackLayer(k,16));u.setElevation(0);
            FrameLayout.LayoutParams up=new FrameLayout.LayoutParams(-1,-1);up.setMargins(dp(12*k),dp(20),dp(12*k),dp(7*(layers-k)));stack.addView(u,up);under[k-1]=u;if(open){u.setAlpha(0f);u.setTranslationY(-dp(7*k));}}
        LinearLayout front=col();front.setPadding(dp(14),dp(14),dp(14),dp(14));
        GradientDrawable fg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,lime,darkTheme?.10f:.05f),panel});fg.setCornerRadius(dp(16));fg.setStroke(dp(1),line);front.setBackground(fg);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,-2);fp.bottomMargin=open?0:dp(7*layers);stack.addView(front,fp);
        LinearLayout top=row();front.addView(top,lp(-1,-2));
        // Date tile
        LinearLayout date=col();date.setGravity(Gravity.CENTER);date.setBackground(shape(blend(panel,lime,darkTheme?.22f:.12f),14));top.addView(date,lp(48,54));
        String dn=day.length()>=10?String.valueOf(Integer.parseInt(day.substring(8,10))):"";String wd="";try{wd=new java.text.SimpleDateFormat("EEE",Locale.getDefault()).format(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(day)).toUpperCase(Locale.getDefault());}catch(Exception ignored){}
        date.addView(tx(dn,20,ink,true));TextView w=tx(wd,9,lime,true);w.setLetterSpacing(.08f);date.addView(w);
        LinearLayout mid=col();LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-2,1);mp.leftMargin=dp(12);top.addView(mid,mp);
        mid.addView(tx(dayTitle(day),17,ink,true));
        StringBuilder kinds=new StringBuilder(n+(n==1?" session":" sessions"));
        if(n==1)kinds.append(auto==1?" · auto":" · started by you");else if(auto==0)kinds.append(" · ").append(n).append(" manual");else if(auto==n)kinds.append(" · all auto");else kinds.append(" · ").append(auto).append(" auto · ").append(n-auto).append(" manual");
        mid.addView(tx(kinds.toString(),12,muted,false));
        TextView tot=tx(String.format(Locale.US,"%.2f km · %s",km,ms>=3600000?String.format(Locale.US,"%dh %02dm",ms/3600000,(ms/60000)%60):formatDuration(ms))+(steps>0?" · "+(steps>=1000?String.format(Locale.US,"%.1fk",steps/1000d):String.valueOf(steps))+" steps":""),12,ink,false);tot.setMaxLines(1);tot.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams totp=lp(-1,-2);totp.topMargin=dp(2);mid.addView(tot,totp);
        // Route thumbnail of the longest outdoor session, with a count badge.
        JSONObject best=null;for(JSONObject o:list)if(!"[]".equals(o.optString("route","[]"))&&(best==null||o.optDouble("distanceKm",0)>best.optDouble("distanceKm",0)))best=o;
        FrameLayout thumb=new FrameLayout(this);LinearLayout.LayoutParams tp=lp(54,54);tp.leftMargin=dp(6);top.addView(thumb,tp);
        if(best!=null){RouteView pv=new RouteView(this);pv.setTheme(bg,raised,lime,darkTheme);pv.setMarkers(false);pv.setRoute(best.optString("route"));pv.setClipToOutline(true);pv.setBackground(shape(raised,12));thumb.addView(pv,new FrameLayout.LayoutParams(-1,-1));}
        else{android.widget.ImageView ic=icon(R.drawable.ic_walk,muted,16);ic.setBackground(shape(raised,12));thumb.addView(ic,new FrameLayout.LayoutParams(-1,-1));}
        if(n>1){TextView badge=tx(String.valueOf(n),11,accentOn,true);badge.setGravity(Gravity.CENTER);GradientDrawable bgd=new GradientDrawable();bgd.setShape(GradientDrawable.OVAL);bgd.setColor(lime);bgd.setStroke(dp(2),panel);badge.setBackground(bgd);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.TOP|Gravity.END);bp.setMargins(0,-dp(6),-dp(6),0);thumb.setClipChildren(false);top.setClipChildren(false);thumb.addView(badge,bp);}
        TextView chev=tx("›",24,muted,false);chev.setGravity(Gravity.CENTER);chev.setRotation(open?-90:90);LinearLayout.LayoutParams cp=lp(22,40);cp.leftMargin=dp(2);top.addView(chev,cp);
        // Sessions, shown when the stack is open.
        LinearLayout body=col();body.setVisibility(open?View.VISIBLE:View.GONE);front.addView(body,lp(-1,-2));
        View div=new View(this);div.setBackgroundColor(line);LinearLayout.LayoutParams dvp=lp(-1,1);dvp.topMargin=dp(12);dvp.bottomMargin=dp(2);body.addView(div,dvp);
        if(n>=2){
            // The day's summary, first in the list, as its own entry that opens a full page.
            DaySummary ds=DaySummary.of(this,history(),day);
            LinearLayout sr=row();sr.setPadding(dp(12),dp(12),dp(12),dp(12));GradientDrawable sg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(raised,lime,darkTheme?.22f:.12f),blend(raised,violet,darkTheme?.16f:.08f)});sg.setCornerRadius(dp(14));sr.setBackground(ripple(sg));
            LinearLayout.LayoutParams srp=lp(-1,-2);srp.topMargin=dp(8);body.addView(sr,srp);
            LinearLayout sd=col();sr.addView(sd,new LinearLayout.LayoutParams(0,-2,1));
            TextView chip=tx("DAY SUMMARY",9,lime,true);chip.setLetterSpacing(.08f);chip.setPadding(dp(7),dp(2),dp(7),dp(2));chip.setBackground(shape(blend(raised,lime,.2f),8));LinearLayout.LayoutParams chp=lp(-2,-2);chp.bottomMargin=dp(3);sd.addView(chip,chp);
            sd.addView(tx("All "+n+" sessions together",14,ink,true));
            sd.addView(tx(String.format(Locale.US,"%.2f km · %s · %,d steps",ds.km,DaySummary.clock(ds.movingS),ds.steps),11,muted,false));
            sd.addView(tx("Effort "+Math.round(ds.effort)+" · "+Math.round(ds.intensity)+" intensity min · tap for everything",11,muted,false));
            RouteView pv=new RouteView(this);pv.setTheme(bg,raised,lime,darkTheme);pv.setMarkers(false);pv.setOverview(true);pv.setRoute(mergedRoute(list));pv.setClipToOutline(true);pv.setBackground(shape(raised,10));LinearLayout.LayoutParams pvp=lp(58,58);pvp.setMargins(dp(5),0,dp(5),0);sr.addView(pv,pvp);
            sr.addView(tx("›",25,muted,false));sr.setOnClickListener(v->{summaryDay=day;navigate("DaySummary");});
        }
        for(JSONObject o:list){
            LinearLayout wrap=col();body.addView(wrap,lp(-1,-2));
            sessionRow(wrap,o,()->{lastSession=o;page="Detail";renderPage();},true);
            LinearLayout r=(LinearLayout)wrap.getChildAt(0);LinearLayout desc=(LinearLayout)r.getChildAt(0);
            boolean a=o.optBoolean("auto",false);TextView chip=tx(a?"AUTO":"MANUAL",9,a?violet:lime,true);chip.setLetterSpacing(.08f);chip.setPadding(dp(7),dp(2),dp(7),dp(2));chip.setBackground(shape(blend(raised,a?violet:lime,.18f),8));
            LinearLayout.LayoutParams chp=lp(-2,-2);chp.bottomMargin=dp(3);desc.addView(chip,0,chp);r.getLayoutParams().height=-2;desc.getLayoutParams().height=-2;
        }
        front.setFocusable(true);front.setContentDescription(dayTitle(day)+", "+kinds+". "+(open?"Tap to fold.":"Tap to see the sessions."));
        top.setOnClickListener(v->{
            android.transition.TransitionManager.beginDelayedTransition(content,new android.transition.ChangeBounds().setDuration(240).setInterpolator(EASE));
            boolean now=!openDays.contains(day);if(now)openDays.add(day);else openDays.remove(day);
            chev.animate().rotation(now?-90:90).setDuration(220).setInterpolator(EASE).start();
            for(int k=0;k<under.length;k++)under[k].animate().alpha(now?0f:1f).translationY(now?-dp(7*(k+1)):0).setDuration(200).setInterpolator(EASE).start();
            FrameLayout.LayoutParams lpf=(FrameLayout.LayoutParams)front.getLayoutParams();lpf.bottomMargin=now?0:dp(7*layers);front.setLayoutParams(lpf);
            if(now){body.setVisibility(View.VISIBLE);body.setAlpha(0f);body.setTranslationY(-dp(8));body.animate().alpha(1f).translationY(0).setDuration(240).setInterpolator(EASE).start();}
            else body.setVisibility(View.GONE);
        });
    }
    private void sessionRow(JSONObject o,Runnable click){sessionRow(content,o,click,false);}
    private void sessionRow(LinearLayout parent,JSONObject o,Runnable click,boolean nested){if(o==null)return;LinearLayout r=row();r.setPadding(dp(nested?12:14),dp(12),dp(12),dp(12));r.setBackground(nested?ripple(shape(raised,14)):panelShape(14));LinearLayout.LayoutParams p=lp(-1,82);p.topMargin=dp(8);parent.addView(r,p);LinearLayout desc=col();r.addView(desc,new LinearLayout.LayoutParams(0,-1,1));desc.addView(tx((o.optBoolean("auto",false)?"Auto · ":"")+sessionLabel(o)+"  ·  "+formatDuration(o.optLong("durationMs")),14,ink,true));double km=o.optDouble("distanceKm",0);long ms=o.optLong("durationMs",0);long sec=km>0?Math.round(ms/1000d/km):0;desc.addView(tx(String.format(Locale.US,"%.2f km  ·  %s  ·  %,d steps",km,o.optString("time",""),o.optInt("steps")),10,muted,false));desc.addView(tx((sec>0&&sec<1800&&km>=.05?String.format(Locale.US,"%d:%02d /km",sec/60,sec%60):"— /km")+String.format(Locale.US,"  ·  ~%d kcal",o.optInt("calories",(int)Math.round(km*65))),10,muted,false));String savedRoute=o.optString("route", "[]");if(!"[]".equals(savedRoute)){RouteView preview=new RouteView(this);preview.setTheme(bg,raised,lime,darkTheme);preview.setMarkers(false);preview.setRoute(savedRoute);LinearLayout.LayoutParams pv=lp(58,58);pv.setMargins(dp(5),0,dp(5),0);r.addView(preview,pv);}r.addView(tx("›",25,muted,false));r.setOnClickListener(v->click.run());}
    private void finishActivity(){if(!prefs.getBoolean("active",false)){toast("No activity to finish");return;}dialog().setTitle("Finish activity?").setMessage("Save this route and its session statistics to History?").setNegativeButton("Keep tracking",null).setPositiveButton("Finish",(d,w)->{int before=history().length();closeLive();command(RecorderService.ACTION_STOP);new Handler(Looper.getMainLooper()).postDelayed(()->{JSONArray h=history();if(h.length()==before){dialog().setTitle("Not saved as a session").setMessage("It had fewer than "+SessionRules.MIN_STEPS+" steps (or was a ride), so it isn't kept as a session. Any steps still count toward today's total.").setPositiveButton("OK",null).show();renderPage();return;}lastSession=h.optJSONObject(h.length()-1);if(lastSession!=null){page="Detail";renderPage();}else renderPage();},700);}).show();}
    private void hideMiniBar(){if(miniBar!=null)miniBar.setVisibility(View.GONE);}
    private void discardActivity(){if(!prefs.getBoolean("active",false)){toast("No activity to discard");return;}dialog().setTitle("Discard activity?").setMessage("This session and its route will be deleted without saving.").setNegativeButton("Keep tracking",null).setPositiveButton("Discard",(d,w)->{command(RecorderService.ACTION_DISCARD);toast("Activity discarded");renderPage();}).show();}
    private void setManualType(String type){Intent i=new Intent(this,RecorderService.class);i.setAction(RecorderService.ACTION_SET_TYPE);i.putExtra("type",type);startService(i);toast("Activity type set to "+type);renderPage();}
    private void onRecord(){if(prefs.getBoolean("active",false)){command(RecorderService.ACTION_TOGGLE);return;}showStartChooser();}
    /** Every new session asks where it happens; walking or running is then detected by the recorder. */
    private void showStartChooser(){
        LinearLayout sheet=col();PullDown sheetPull=new PullDown(getResources().getDisplayMetrics().density,true);
        android.app.Dialog d=new android.app.Dialog(this){@Override public boolean dispatchTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)sheetPull.begin(e,sheet,this::dismiss);return sheetPull.handle(e,c->super.dispatchTouchEvent(c))||super.dispatchTouchEvent(e);}};d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
sheet.setPadding(dp(20),dp(10),dp(20),dp(24));GradientDrawable sb=new GradientDrawable();sb.setColor(panel);sb.setCornerRadii(new float[]{dp(26),dp(26),dp(26),dp(26),0,0,0,0});sheet.setBackground(sb);
        View grip=new View(this);grip.setBackground(shape(line,3));LinearLayout.LayoutParams gp=lp(40,5);gp.gravity=Gravity.CENTER_HORIZONTAL;gp.bottomMargin=dp(14);sheet.addView(grip,gp);
        sheet.addView(tx("Where are you moving?",21,ink,true));
        TextView sub=tx("Walking or running is detected automatically from your pace and cadence.",12,muted,false);LinearLayout.LayoutParams subp=lp(-1,-2);subp.topMargin=dp(4);subp.bottomMargin=dp(14);sheet.addView(sub,subp);
        startOption(sheet,d,"Outdoor","GPS route on the map · pace, splits, elevation",R.drawable.ic_place,lime,"outdoor");
        startOption(sheet,d,"Indoor","Treadmill or indoors · steps and stride distance",R.drawable.ic_walk,violet,"indoor");
        TextView cancel=tx("Not now",14,muted,true);cancel.setGravity(Gravity.CENTER);cancel.setBackground(rippleOn(Color.TRANSPARENT,14));LinearLayout.LayoutParams cp=lp(-1,48);cp.topMargin=dp(6);sheet.addView(cancel,cp);cancel.setOnClickListener(v->d.dismiss());
        d.setContentView(sheet);android.view.Window w=d.getWindow();
        if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);w.setWindowAnimations(android.R.style.Animation_InputMethod);w.setDimAmount(.55f);}
        d.show();
    }
    private void startOption(LinearLayout sheet,android.app.Dialog d,String title,String sub,int res,int color,String mode){
        LinearLayout o=row();o.setPadding(dp(14),dp(16),dp(14),dp(16));GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{blend(panel,color,darkTheme?.22f:.12f),blend(panel,color,darkTheme?.06f:.03f)});g.setCornerRadius(dp(20));g.setStroke(dp(1),blend(line,color,.35f));o.setBackground(ripple(g));
        LinearLayout.LayoutParams op=lp(-1,-2);op.topMargin=dp(10);sheet.addView(o,op);
        android.widget.ImageView ic=icon(res,darkTheme?Color.rgb(11,11,13):Color.WHITE,10);ic.setBackground(shape(color,16));o.addView(ic,lp(52,52));
        LinearLayout t=col();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(14);o.addView(t,tp);t.addView(tx(title,18,ink,true));t.addView(tx(sub,11,muted,false));
        o.addView(tx("›",26,color,false));o.setFocusable(true);o.setContentDescription("Start "+title.toLowerCase(Locale.US)+" activity");
        o.setOnClickListener(v->{d.dismiss();prefs.edit().putString("activityMode",mode).apply();activityType="Walk";beginRecord();});
    }
    private void beginRecord(){prefs.edit().putString("activityType",activityType).apply();ArrayList<String> r=new ArrayList<>();boolean indoor="indoor".equals(prefs.getString("activityMode","outdoor"));if(!indoor){boolean gpsOn=false;try{gpsOn=((LocationManager)getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}if(!gpsOn){dialog().setTitle("Turn on location").setMessage("Outdoor tracking needs GPS to draw your route. Turn on Location, then start again, or switch to Indoor / treadmill.").setNegativeButton("Not now",null).setPositiveButton("Open settings",(d,w)->{try{startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(Exception ignored){}}).show();return;}}if(!indoor&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)r.add(Manifest.permission.ACCESS_FINE_LOCATION);if(!indoor&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)r.add(Manifest.permission.ACCESS_COARSE_LOCATION);if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)r.add(Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)r.add(Manifest.permission.POST_NOTIFICATIONS);if(!r.isEmpty()){requestPermissions(r.toArray(new String[0]),PERMISSIONS);return;}command(RecorderService.ACTION_START);openLive();askBackground();}
    @Override public void onRequestPermissionsResult(int q,String[] ps,int[] rs){super.onRequestPermissionsResult(q,ps,rs);if(q==NOTIFY_PERMISSION||q==LOCATION_PERMISSION||q==BG_LOCATION_PERMISSION){renderPageNow();return;}if(q==STEP_PERMISSION){if(DaySteps.permitted(this)){startStepCounting();toast("Step counting is on");}else if(Build.VERSION.SDK_INT>=29)getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putBoolean("stepPermDenied",true).apply();renderPage();return;}startStepCounting();if(q==PERMISSIONS&&"indoor".equals(prefs.getString("activityMode","outdoor"))&&!DaySteps.permitted(this)){toast("Indoor tracking needs Physical activity access to count steps");return;}if(q==PERMISSIONS&&("indoor".equals(prefs.getString("activityMode","outdoor"))||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)){command(RecorderService.ACTION_START);openLive();askBackground();}}
    private void command(String action){Intent i=new Intent(this,RecorderService.class);i.setAction(action);if(ACTION_START.equals(action)){if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}else startService(i);}
    private static final String ACTION_START=RecorderService.ACTION_START;
    private void updateLive(){if(prefs==null)return;StepsWidget.refresh(this);if(resumed)refreshHealth(false);maybeCelebrate();updateMiniBar();updateLiveOverlay();boolean active=prefs.getBoolean("active",false),paused=prefs.getBoolean("paused",false);JSONArray all=history();if(timer!=null){long ms=prefs.getLong("elapsed",0);timer.setText(formatDuration(ms));double km=prefs.getFloat("distance",0);distance.setText(String.format(Locale.US,"%.2f km",km));long ps=km>0?Math.round(ms/1000d/km):0;pace.setText(km>0?String.format(Locale.US,"%d:%02d /km",ps/60,ps%60):"— /km");steps.setText(String.format(Locale.US,"%,d",prefs.getInt("steps",0)));if(liveSpeed!=null){float kmh=active&&!paused?prefs.getFloat("speedKmh",0):0;liveSpeed.setText(kmh>0?String.format(Locale.US,"%.1f km/h",kmh):"— km/h");liveElev.setText(active&&prefs.getBoolean("hasElevation",false)?String.format(Locale.US,"%.0f m",prefs.getFloat("elevGainM",0)):"— m");liveKcal.setText(String.format(Locale.US,"~%d kcal",Calories.live(this)));}boolean moving=prefs.getBoolean("moving",false);long fixAge=android.os.SystemClock.elapsedRealtime()-prefs.getLong("lastFixElapsed",0);boolean indoor="indoor".equals(prefs.getString("activityMode","outdoor"));boolean gps=indoor||(prefs.getBoolean("gpsFix",false)&&fixAge<12000);boolean indoorSel="indoor".equals(prefs.getString("activityMode","outdoor"));start.setText(active?(paused?"▶  Resume":"Ⅱ  Pause"):"▶  Start activity");if(trackStats!=null){trackStats.setVisibility(active?View.VISIBLE:View.GONE);trackEnd.setVisibility(active?View.VISIBLE:View.GONE);}String type=prefs.getString("currentType",activityType);status.setText(active?(paused?"● PAUSED":!indoor&&!gps?"● GPS SIGNAL WEAK":!moving?(indoor?"● WAITING FOR STEPS":"● YOU’RE NOT WALKING"):"● YOU’RE "+(type.equals("Run")?"RUNNING":"WALKING")):"● READY");status.setTextColor(active&&paused?muted:active&&!gps&&!indoor?warning:active&&!moving?warning:active?positive:lime);if(active&&!paused&&moving!=previousMoving){toast(moving?"You're moving · Kadamm is recording your activity":"You're not moving · pause or start moving to continue");}previousMoving=moving;route.setRoute(prefs.getString("route","[]"));route.setEmptyMessage(indoor?"Indoor session · distance comes from your steps":"Your route draws here once GPS locks on");if(gps&&!indoor)route.setCurrentLocation(prefs.getFloat("latitude",0),prefs.getFloat("longitude",0),prefs.getFloat("accuracy",0));else if(!active&&!indoor&&hasIdle)route.setCurrentLocation(idleLat,idleLon,idleAcc);else route.clearCurrentLocation();if(accuracyLabel!=null)accuracyLabel.setText(indoor?"Indoor mode · distance estimated from phone steps":gps?String.format(Locale.US,"GPS accuracy · ±%.0f m",prefs.getFloat("accuracy",0)):!active?(hasIdle?"Last known location · GPS locks on when you start":"Location appears here when you start"):"GPS signal weak or unavailable · last route point kept");if(activityModeLabel!=null)activityModeLabel.setText(!active?"Outdoor or indoor · walk or run detected":prefs.getBoolean("manualType",false)?(indoor?"Indoor · ":"Outdoor · ")+type+" (set by you)":(indoor?"Indoor · detected ":"Outdoor · detected ")+type.toLowerCase(Locale.US)+(" · tap to correct"));}updateHero(all);if(dailyDistanceProgress!=null){int target=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("distanceGoalM",5000);double meters=totalDistanceOn(all,todayKey())*1000;dailyDistanceProgress.setMax(target);dailyDistanceProgress.setProgress((int)Math.min(target,meters));dailyDistanceProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(meters>=target?positive:violet));dailyDistanceValue.setText(String.format(Locale.US,"%.2f / %.1f km",meters/1000f,target/1000f));}if(todayCount!=null){todayCount.setText(String.valueOf(countOn(all,todayKey(),"Walk")+(active&&"Walk".equals(prefs.getString("currentType","Walk"))?1:0)));todayDistance.setText(String.format(Locale.US,"%.2f",totalDistanceOn(all,todayKey())));}}
    private JSONArray history(){return DaySteps.sessions(this);}
    static void saveSession(SharedPreferences hp,JSONObject s){try{JSONArray old=new JSONArray(hp.getString("sessions","[]"));JSONArray a=new JSONArray();boolean replaced=false;for(int i=0;i<old.length();i++){JSONObject row=old.optJSONObject(i);if(row!=null&&row.optLong("id",-1)==s.optLong("id",-2)){a.put(s);replaced=true;}else a.put(row);}if(!replaced)a.put(s);hp.edit().putString("sessions",a.toString()).apply();}catch(Exception ignored){}}
    private String todayKey(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}private String dateKey(Calendar c){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime());}private String dateKey(JSONObject o){return o.optString("day",o.optString("date",""));}
    private int countOn(JSONArray a,String d,String type){int n=0;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&dateKey(o).equals(d)&&(type==null||type.equals(o.optString("type","Walk"))))n++;}return n;}
    private double totalDistanceOn(JSONArray a,String d){double n=0;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&dateKey(o).equals(d))n+=o.optDouble("distanceKm");}if(d.equals(todayKey())&&prefs.getBoolean("active",false))n+=prefs.getFloat("distance",0);return n;}
    private double distanceOn(JSONArray a,String d){double n=0;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&dateKey(o).equals(d)&&"Walk".equals(o.optString("type","Walk")))n+=o.optDouble("distanceKm");}return n;}
    private double distanceAll(JSONArray a){double n=0;for(int i=0;i<a.length();i++)n+=a.optJSONObject(i).optDouble("distanceKm");return n;}private long stepsAll(JSONArray a){long n=0;for(int i=0;i<a.length();i++){JSONObject s=a.optJSONObject(i);if(s!=null&&s.optBoolean("stepsAvailable",false))n+=s.optInt("steps");}return n;}
    private String monthName(){return new SimpleDateFormat("MMMM yyyy",Locale.getDefault()).format(shownMonth.getTime());}private boolean isSameDate(Calendar a,Calendar b){return a.get(Calendar.YEAR)==b.get(Calendar.YEAR)&&a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR);}private String formatDuration(long ms){long s=ms/1000;return s>=3600?String.format(Locale.US,"%d:%02d:%02d",s/3600,(s/60)%60,s%60):String.format(Locale.US,"%02d:%02d",s/60,s%60);}private void toast(String s){android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show();}
    // ---- Today: header, hero ring and bento metrics ----
    private void renderHeader(){
        LinearLayout top=row();content.addView(top,lp(-1,48));LinearLayout.LayoutParams bbp=lp(-2,48);bbp.leftMargin=-dp(8);top.addView(brandButton(),bbp);
        top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        String source=DaySteps.todaySource(this);boolean live="phone".equals(source)||"health".equals(source);
        TextView chip=tx(live?"●  Steps live":"○  Set up steps",10,live?positive:warning,true);chip.setPadding(dp(11),0,dp(11),0);chip.setGravity(Gravity.CENTER);chip.setBackground(rippleOn(raised,14));chip.setFocusable(true);chip.setContentDescription("Step sources");chip.setOnClickListener(v->navigate("Health"));top.addView(chip,lp(-2,28));
        android.widget.ImageView gear=icon(R.drawable.ic_settings,muted,12);gear.setContentDescription("Goals and targets");gear.setFocusable(true);gear.setBackground(rippleOn(Color.TRANSPARENT,24));gear.setOnClickListener(v->navigate("Goals"));LinearLayout.LayoutParams gp=lp(48,48);gp.leftMargin=dp(4);top.addView(gear,gp);View me=avatarView(LocalAccount.current(this),34);me.setFocusable(true);me.setContentDescription("Your profile");me.setOnClickListener(v->navigate("Profile"));LinearLayout.LayoutParams mep=lp(34,34);mep.leftMargin=dp(2);top.addView(me,mep);
        int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        TextView e=tx(("Today's insights  ·  "+new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(new Date())).toUpperCase(Locale.getDefault()),10,muted,true);e.setLetterSpacing(.08f);LinearLayout.LayoutParams ep=lp(-1,-2);ep.topMargin=dp(6);content.addView(e,ep);
        content.addView(tx(hour<12?"Good morning":hour<17?"Good afternoon":"Good evening",28,ink,true));
        heroNote=tx("",13,muted,false);LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(3);np.bottomMargin=dp(4);content.addView(heroNote,np);
    }
    private void renderStepSetup(){
        stepSetupShown=false;if(!DaySteps.sensorPresent(this)||DaySteps.permitted(this))return;stepSetupShown=true;
        LinearLayout c=card();GradientDrawable outline=shape(panel,16);outline.setStroke(dp(1),warning);c.setBackground(outline);
        c.addView(tx("Turn on all-day step counting",16,ink,true));
        c.addView(tx("Kadamm can count every step with this phone's own motion sensor. No watch and no other app needed. Android asks for Physical activity access first.",12,muted,false));
        Button allow=button("Allow step counting",lime,accentOn);LinearLayout.LayoutParams ap=lp(-1,46);ap.topMargin=dp(12);c.addView(allow,ap);allow.setOnClickListener(v->requestStepPermission());
    }
    private void renderHero(JSONArray hist){
        LinearLayout c=card();dailyGoalCard=c;c.setBackground(panelShape(20));
        LinearLayout head=row();head.addView(tx("DAILY GOALS",10,muted,true),new LinearLayout.LayoutParams(0,-2,1));heroSource=tx("",10,positive,true);head.addView(heroSource);head.addView(shareIcon("today",()->shareSheet("Share today",todayShareText(),null,null)),lp(40,40));c.addView(head);
        LinearLayout body=row();LinearLayout.LayoutParams bp=lp(-1,-2);bp.topMargin=dp(10);c.addView(body,bp);
        // The goals capsule: steps, brisk minutes and distance layered into one day's %.
        capsule=new GoalCapsule.CapsuleView(this,darkTheme);body.addView(capsule,lp(96,214));
        LinearLayout side=col();LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=dp(14);body.addView(side,sp);
        ringRows=new TextView[3];
        // Steps first and biggest: the day's main number, then brisk minutes and distance under it.
        stepsBig=tx("—",34,GoalCapsule.COLORS[0],true);side.addView(stepsBig);
        for(int i=0;i<3;i++){
            int col=GoalCapsule.COLORS[i];LinearLayout r=col();LinearLayout.LayoutParams rp2=lp(-1,-2);if(i>0)rp2.topMargin=dp(10);side.addView(r,rp2);
            LinearLayout top=row();r.addView(top,lp(-1,-2));
            View sw=new View(this);sw.setBackground(shape(col,3));top.addView(sw,lp(9,9));
            TextView name=tx(GoalCapsule.NAMES[i],13,ink,true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,-2);np.leftMargin=dp(7);top.addView(name,np);
            ringRows[i]=tx("",12,muted,false);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(0,-2,1);vp.leftMargin=dp(6);top.addView(ringRows[i],vp);
            ringPct[i]=tx("",13,col,true);top.addView(ringPct[i]);
            FrameLayout track=new FrameLayout(this);track.setBackground(shape(raised,3));LinearLayout.LayoutParams tp=lp(-1,6);tp.topMargin=dp(5);r.addView(track,tp);
            View fill=new View(this);fill.setBackground(shape(col,3));track.addView(fill,new FrameLayout.LayoutParams(0,-1));goalFills[i]=fill;
            ringSubs[i]=tx("",11,muted,false);LinearLayout.LayoutParams sp2=lp(-1,-2);sp2.topMargin=dp(3);r.addView(ringSubs[i],sp2);
        }
        heroPercent=tx("",1,ink,false);heroRemaining=tx("",13,ink,true);heroDistance=tx("",11,violet,true);
        heroSessions=tx("",11,muted,false);LinearLayout.LayoutParams hsp=lp(-1,-2);hsp.topMargin=dp(10);c.addView(heroSessions,hsp);
        dailyGoalMessage=tx("",12,muted,false);LinearLayout.LayoutParams mp=lp(-1,-2);mp.topMargin=dp(12);c.addView(dailyGoalMessage,mp);
        heroStart=button("▶  Start activity",lime,accentOn);heroStart.setTextSize(15);LinearLayout.LayoutParams hp=lp(-1,50);hp.topMargin=dp(12);c.addView(heroStart,hp);
        heroStart.setOnClickListener(v->{if(prefs.getBoolean("active",false))openLive();else onRecord();});
        updateHero(hist);
    }
    private int briskGoal(){return getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("briskGoalMin",30);}
    private GoalCapsule.CapsuleView capsule;private TextView stepsBig;private final View[] goalFills=new View[3];private MetricViews.Rings rings;private TextView[] ringRows;private final TextView[] ringSubs=new TextView[3],ringPct=new TextView[3];
    /** Brisk minutes today: minutes at 100+ steps a minute from Health Connect, or from today's sessions; the larger. */
    private double briskToday(JSONArray all){
        JSONObject d=DaySteps.minuteDays(this).optJSONObject(todayKey());double hc=d!=null?d.optDouble("brisk",0):0,ses=0;
        if(SessionMetrics.ready())for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(o!=null&&todayKey().equals(o.optString("day",""))&&!Insights.vehicle(o)){SessionMetrics m=SessionMetrics.of(this,o);ses+=(m.zoneS[2]+m.zoneS[3]+m.zoneS[4])/60d;}}
        return Math.max(hc,ses);
    }
    private void updateHero(JSONArray all){
        if(capsule==null||heroPercent==null)return;
        int goal=dailyGoal();long n=DaySteps.todayTotal(this);boolean data=DaySteps.hasTodayData(this),active=prefs.getBoolean("active",false);double pct=goal>0?n*100d/goal:0;
        GoalCapsule.Day day=GoalCapsule.today(this);capsule.set(day);
        if(stepsBig!=null){android.text.SpannableStringBuilder sb=new android.text.SpannableStringBuilder(data?String.format(Locale.US,"%,d",n):"—");stepsBig.setText(sb);}
        for(int i=0;i<3;i++){
            ringRows[i].setText(i==0?String.format(Locale.US,"goal %,d",day.stepGoal):day.value(i)+"  "+day.goal(i));ringPct[i].setText("+"+Math.round(day.share[i])+"%");
            ringSubs[i].setText(day.pending(i));ringSubs[i].setTextColor(day.done[i]>=1?positive:muted);
            View f=goalFills[i];double fr=day.done[i];f.post(()->{View tr=(View)f.getParent();FrameLayout.LayoutParams fl=(FrameLayout.LayoutParams)f.getLayoutParams();int w=(int)(tr.getWidth()*fr);if(fl.width!=w){fl.width=w;f.setLayoutParams(fl);}});
        }
        int closed=(day.done[0]>=1?1:0)+(day.done[1]>=1?1:0)+(day.done[2]>=1?1:0);int total=day.total();
        heroPercent.setText(data?String.format(Locale.US,pct>=100||pct==Math.floor(pct)?"%.0f%%":"%.1f%%",pct):"—");heroPercent.setTextColor(data?(pct>=100?positive:ink):muted);
        heroRemaining.setText(!data?"Steps not counted yet":n>=goal?(n>goal?String.format(Locale.US,"+%,d beyond goal",n-goal):"Goal reached"):String.format(Locale.US,"%,d steps to go",goal-n));
        String source=DaySteps.todaySource(this);heroSource.setText("phone".equals(source)?"PHONE SENSOR":"health".equals(source)?"HEALTH CONNECT":"sessions".equals(source)?"SESSIONS ONLY":"");
        heroStart.setText(active?"View live session":"▶  Start activity");if(heroSessions!=null){heroSessions.setText(closed==3?"All three goals met: a perfect day ✦":total+"% = "+Math.round(day.share[0])+"% steps + "+Math.round(day.share[1])+"% brisk + "+Math.round(day.share[2])+"% distance · "+closed+" of 3 goals met");heroSessions.setTextColor(closed==3?positive:muted);}
        if(heroNote!=null)heroNote.setText(!data?"Let's get your steps counting.":pct>=100?"Goal done. Anything from here is a bonus.":pct>=80?"Nearly there. One short walk finishes it.":pct>=50?"Over halfway. Keep the rhythm going.":pct>=10?"Good start. Keep the momentum.":"Every step counts. Let's get moving.");
        if(n>=goal&&data){
            if(!todayKey().equals(prefs.getString("goalShownDay",""))){prefs.edit().putString("goalShownDay",todayKey()).apply();if(!reducedMotion())dailyGoalCard.animate().scaleX(1.015f).scaleY(1.015f).setDuration(180).withEndAction(()->dailyGoalCard.animate().scaleX(1f).scaleY(1f).setDuration(180).start()).start();}
            dailyGoalMessage.setText("");dailyGoalMessage.setVisibility(View.GONE);
        }else if(data&&n>=Math.ceil(goal*.6)){
            dailyGoalMessage.setVisibility(View.VISIBLE);dailyGoalMessage.setText(String.format(Locale.US,"You're making progress! %,d steps done, just %,d more to reach your goal.",n,goal-n));dailyGoalMessage.setTextColor(ink);
        }else{
            dailyGoalMessage.setVisibility(View.VISIBLE);dailyGoalMessage.setText(data?String.format(Locale.US,"%,d of %,d steps so far today.",n,goal):DaySteps.sensorPresent(this)&&!DaySteps.permitted(this)?"Allow step counting above to see today's steps here.":"Start an activity or connect a step source to see today's steps.");dailyGoalMessage.setTextColor(muted);
        }
        if(todayDistance!=null)todayDistance.setText(String.format(Locale.US,"%.2f",DaySteps.distanceToday(this)[0]));
        if(todayCaloriesValue!=null)todayCaloriesValue.setText(String.format(Locale.US,"%,d",DaySteps.activeKcalToday(this)));
    }
    private int hue(int light,int dark){return darkTheme?dark:light;}
    private android.widget.ImageView icon(int res,int tint,int padDp){android.widget.ImageView v=new android.widget.ImageView(this);v.setImageResource(res);v.setImageTintList(android.content.res.ColorStateList.valueOf(tint));v.setPadding(dp(padDp),dp(padDp),dp(padDp),dp(padDp));v.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);return v;}
    /** Vector icon for each symbol used across the app. */
    private int iconRes(String g){
        switch(g){
            case "Goals":return R.drawable.ic_flag;case "Routes":return R.drawable.ic_place;case "Notifications":return R.drawable.ic_bell;case "Permissions":return R.drawable.ic_settings;case "Auto":return R.drawable.ic_run;case "Storage":return R.drawable.ic_storage;case "Reports":return R.drawable.ic_chart;case "Achievements":return R.drawable.ic_trophy;case "Health":return R.drawable.ic_heart;case "ShareApp":return R.drawable.ic_share;case "Places":return R.drawable.ic_my_location;
            case "Today":case "◷home":return R.drawable.ic_home;case "History":case "▦":return R.drawable.ic_calendar;case "Profile":case "◉p":return R.drawable.ic_person;
            case "↗":return R.drawable.ic_place;case "⚡\uFE0E":return R.drawable.ic_flame;case "◷":return R.drawable.ic_timer;case "▶":return R.drawable.ic_walk;case "◉":return R.drawable.ic_walk;
            case "»":return R.drawable.ic_run;case "★":return R.drawable.ic_bolt;case "≈":return R.drawable.ic_chart;case "◎":return R.drawable.ic_flag;case "＋":return R.drawable.ic_heart;case "⇅":return R.drawable.ic_ruler;
            default:return R.drawable.ic_home;
        }
    }
    private int blend(int a,int b,float t){return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    // Tile colours stay inside the logo palette: blue, light blue, and black/white.
    // Glance tile colours: each metric keeps its own soft hue.
    private int purple(){return hue(Color.rgb(147,52,230),Color.rgb(197,138,249));}
    private int orange(){return hue(Color.rgb(232,113,10),Color.rgb(252,173,112));}
    private int teal(){return hue(Color.rgb(18,158,175),Color.rgb(120,217,236));}
    private int flame(){return hue(Color.rgb(242,153,0),Color.rgb(253,214,99));}
    private int green(){return hue(Color.rgb(30,142,62),Color.rgb(129,201,149));}
    private int pink(){return hue(Color.rgb(208,24,132),Color.rgb(255,139,203));}
    private int indigo(){return hue(Color.rgb(63,81,181),Color.rgb(159,168,218));}
    private LinearLayout bentoRow(){LinearLayout r=row();LinearLayout.LayoutParams p=lp(-1,118);p.topMargin=dp(10);content.addView(r,p);return r;}
    /** A dashboard tile with its own colour: tinted gradient surface, icon badge, value and unit. */
    private TextView tile(LinearLayout parent,String label,String value,String unit,int color,String icon){
        LinearLayout c=col();c.setPadding(dp(14),dp(14),dp(12),dp(12));
        GradientDrawable g=shape(blend(panel,color,darkTheme?.15f:.08f),20);g.setStroke(dp(1),blend(line,color,darkTheme?.28f:.22f));c.setBackground(g);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=dp(10);parent.addView(c,p);
        LinearLayout head=row();c.addView(head);
        android.widget.ImageView ic=icon(iconRes(icon),darkTheme?Color.rgb(11,11,13):Color.WHITE,6);ic.setBackground(shape(color,10));head.addView(ic,lp(30,30));
        TextView l=tx(label,10,muted,true);l.setLetterSpacing(.06f);LinearLayout.LayoutParams lpp=lp(-2,-2);lpp.leftMargin=dp(8);head.addView(l,lpp);
        TextView v=tx(value,24,ink,true);LinearLayout.LayoutParams vp=lp(-1,-2);vp.topMargin=dp(10);c.addView(v,vp);
        c.addView(tx(unit,10,muted,false));return v;
    }
    /** A glance tile: tinted surface, icon badge, value, a small drawn visual and a one-line explanation. */
    private TextView glance(LinearLayout parent,String label,String value,String unit,int color,String icon,View viz,String foot){
        LinearLayout c=col();c.setPadding(dp(14),dp(14),dp(14),dp(12));
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,color,darkTheme?.20f:.11f),blend(panel,color,darkTheme?.06f:.03f)});g.setCornerRadius(dp(20));g.setStroke(dp(1),blend(line,color,darkTheme?.30f:.22f));c.setBackground(g);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=dp(10);parent.addView(c,p);
        LinearLayout head=row();c.addView(head);
        android.widget.ImageView ic=icon(iconRes(icon),darkTheme?Color.rgb(11,11,13):Color.WHITE,6);ic.setBackground(shape(color,10));head.addView(ic,lp(28,28));
        TextView l=tx(label,10,muted,true);l.setLetterSpacing(.06f);LinearLayout.LayoutParams lpp=lp(-2,-2);lpp.leftMargin=dp(8);head.addView(l,lpp);
        LinearLayout vr=row();vr.setGravity(Gravity.BOTTOM);LinearLayout.LayoutParams vrp=lp(-1,-2);vrp.topMargin=dp(10);c.addView(vr,vrp);
        TextView v=tx(value,26,ink,true);vr.addView(v);TextView u=tx(" "+unit,11,muted,true);u.setPadding(0,0,0,dp(4));vr.addView(u);
        if(viz!=null){LinearLayout.LayoutParams zp=lp(-1,viz instanceof MiniChartView?26:-2);zp.topMargin=dp(8);c.addView(viz,zp);}
        if(foot!=null){TextView f=tx(foot,10,muted,false);LinearLayout.LayoutParams fp=lp(-1,-2);fp.topMargin=dp(6);c.addView(f,fp);}
        return v;
    }
    private LinearLayout glanceRow(){LinearLayout r=row();r.setGravity(Gravity.TOP);LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(10);content.addView(r,p);return r;}
    private String minutes(long min){return min>=60?String.format(Locale.US,"%dh %02dm",min/60,min%60):min+" min";}
    private void renderBento(JSONArray hist){
        String today=todayKey();int walks=countOn(hist,today,"Walk"),runs=countOn(hist,today,"Run"),sessions=countOn(hist,today,null);KadammTheme th=chartTheme();
        SharedPreferences hp=getSharedPreferences("kadamm_health",MODE_PRIVATE),st=getSharedPreferences("kadamm_settings",MODE_PRIVATE);boolean hc=today.equals(hp.getString("healthDate",""))&&hp.getBoolean("healthConnected",false);
        LinearLayout sh=row();LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(22);content.addView(sh,sp);
        TextView sec=tx("TODAY AT A GLANCE",10,muted,true);sec.setLetterSpacing(.08f);sh.addView(sec,new LinearLayout.LayoutParams(0,-2,1));
        sh.addView(tx(hc?"● Health Connect + phone":"● Phone sensor + sessions",10,positive,true));
        dailyDistanceValue=null;dailyDistanceProgress=null;todayWalkDistance=null;todayRunDistance=null;
        // Distance and active calories: whole day, never the resting burn.
        double[] dist=DaySteps.distanceToday(this);int distTarget=st.getInt("distanceGoalM",5000);
        long kcal=DaySteps.activeKcalToday(this);long goalKcal=Math.round(DaySteps.stepKm(this,dailyGoal())*Calories.weightKg(this)*.5);
        LinearLayout r1=glanceRow();
        todayDistance=glance(r1,"DISTANCE",String.format(Locale.US,"%.2f",dist[0]),"km",lime,"↗",new MiniChartView(this,th,MiniChartView.PROGRESS,lime,0).progress((float)(dist[0]*1000/Math.max(1,distTarget))),String.format(Locale.US,"%.0f%% of %.1f km%s",dist[0]*100000/Math.max(1,distTarget),distTarget/1000f,dist[1]>0?" · est. from steps":""));
        todayCaloriesValue=glance(r1,"CALORIES",String.format(Locale.US,"%,d",kcal),"kcal",orange(),"⚡\uFE0E",new MiniChartView(this,th,MiniChartView.PROGRESS,orange(),0).progress(kcal/(float)Math.max(1,goalKcal)),String.format(Locale.US,"active only · ~%,d at goal",goalKcal));
        // Active time against the move goal; sessions with this week's session minutes.
        long moveMin=todayDuration(hist)/60000;if(hc)moveMin=Math.max(moveMin,hp.getInt("healthActiveMin",0));int moveGoal=st.getInt("activeGoalMin",30);
        long[] weekMin=new long[7];for(int i=0;i<7;i++){Calendar d=Calendar.getInstance();d.add(Calendar.DAY_OF_YEAR,i-6);weekMin[i]=durationOn(hist,dateKey(d))/60000;}weekMin[6]=todayDuration(hist)/60000;
        LinearLayout r2=glanceRow();
        todayActiveTime=glance(r2,"ACTIVE TIME",moveMin>=60?String.format(Locale.US,"%dh %02d",moveMin/60,moveMin%60):String.valueOf(moveMin),moveMin>=60?"":"min",green(),"◷",new MiniChartView(this,th,MiniChartView.PROGRESS,green(),0).progress(moveMin/(float)Math.max(1,moveGoal)),moveMin>=moveGoal?"move goal done":String.format(Locale.US,"%d min to your %d min goal",moveGoal-moveMin,moveGoal));
        glance(r2,"SESSIONS",String.valueOf(sessions+(prefs.getBoolean("active",false)?1:0)),"today",purple(),"▶",new MiniChartView(this,th,MiniChartView.BARS,purple(),0).bars(weekMin,0),walks+" walk · "+runs+" run · minutes, 7 days");
        // Walking vs running: everyday steps count as walking; running is what sessions recorded as running.
        double runKm=distanceByType(hist,today,"Run"),walkKm=Math.max(0,dist[0]-runKm);
        LinearLayout r3=glanceRow();
        LinearLayout wr=col();wr.setPadding(dp(14),dp(14),dp(14),dp(12));GradientDrawable wg=shape(panel,20);wg.setStroke(dp(1),line);wr.setBackground(wg);r3.addView(wr,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout wh=row();wr.addView(wh);
        LinearLayout wl=col();wh.addView(wl,new LinearLayout.LayoutParams(0,-2,1));wl.addView(tx("● WALKING",10,teal(),true));wl.addView(tx(String.format(Locale.US,"%.2f km",walkKm),20,ink,true));wl.addView(tx(formatDuration(durationByType(hist,today,"Walk"))+" in sessions",10,muted,false));
        LinearLayout rl=col();rl.setGravity(Gravity.END);wh.addView(rl,new LinearLayout.LayoutParams(0,-2,1));TextView rlt=tx("RUNNING ●",10,pink(),true);rlt.setGravity(Gravity.END);rl.addView(rlt);TextView rlv=tx(String.format(Locale.US,"%.2f km",runKm),20,ink,true);rlv.setGravity(Gravity.END);rl.addView(rlv);TextView rld=tx(formatDuration(durationByType(hist,today,"Run"))+" in sessions",10,muted,false);rld.setGravity(Gravity.END);rl.addView(rld);
        LinearLayout.LayoutParams sbp=lp(-1,14);sbp.topMargin=dp(10);wr.addView(new MiniChartView(this,th,MiniChartView.SPLIT,teal(),pink()).split((float)walkKm,(float)runKm),sbp);
        // Streak and the average of the last seven finished days (today is still counting, so it is left out).
        int[] st2=DaySteps.streaks(dayTotals,dailyGoal());long sum=0;int days=0;long[] last7=new long[7];
        for(int i=0;i<7;i++){Calendar d=Calendar.getInstance();d.add(Calendar.DAY_OF_YEAR,i-6);Long v=dayTotals.get(dateKey(d));last7[i]=v==null?0:v;}
        for(int i=1;i<=7;i++){Calendar d=Calendar.getInstance();d.add(Calendar.DAY_OF_YEAR,-i);Long v=dayTotals.get(dateKey(d));if(v!=null&&v>0){sum+=v;days++;}}
        long avg=days==0?0:sum/days,now=DaySteps.todayTotal(this);
        LinearLayout r4=glanceRow();
        glance(r4,"STREAK",String.valueOf(st2[0]),st2[0]==1?"day":"days",flame(),"★",new MiniChartView(this,th,MiniChartView.PROGRESS,flame(),0).progress(st2[1]==0?0:st2[0]/(float)st2[1]),st2[0]>=st2[1]&&st2[0]>0?"your best ever":String.format(Locale.US,"best %d · %d to beat it",st2[1],st2[1]-st2[0]+1));
        glance(r4,"DAILY AVG",days==0?"—":String.format(Locale.US,"%,d",avg),"",indigo(),"≈",new MiniChartView(this,th,MiniChartView.BARS,indigo(),0).bars(last7,dailyGoal()),days==0?"no finished days yet":String.format(Locale.US,"past 7 days · today %d%% of it",Math.round(now*100f/Math.max(1,avg))));
    }
    private void renderDistanceGoal(JSONArray hist){}
    private void loadIdleLocation(){
        hasIdle=false;
        try{
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location best=null;
            for(String provider:lm.getProviders(true)){Location l=lm.getLastKnownLocation(provider);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}
            if(best!=null){idleLat=best.getLatitude();idleLon=best.getLongitude();idleAcc=best.hasAccuracy()?best.getAccuracy():0;hasIdle=true;}
        }catch(Exception ignored){}
    }

    // ---- Step counting and permissions ----
    private void firstRunPermissions(){
        SharedPreferences s=getSharedPreferences("kadamm_settings",MODE_PRIVATE);if(s.getBoolean("stepPermAsked",false))return;s.edit().putBoolean("stepPermAsked",true).apply();
        ArrayList<String> r=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=29&&DaySteps.sensorPresent(this)&&!DaySteps.permitted(this))r.add(Manifest.permission.ACTIVITY_RECOGNITION);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)r.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!r.isEmpty())requestPermissions(r.toArray(new String[0]),STEP_PERMISSION);
    }
    private void requestStepPermission(){
        if(Build.VERSION.SDK_INT<29)return;SharedPreferences s=getSharedPreferences("kadamm_settings",MODE_PRIVATE);
        // After a firm "Don't allow", Android stops showing the dialog, so go to the app's settings page instead.
        if(s.getBoolean("stepPermDenied",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)){
            toast("Open Permissions and allow Physical activity");
            try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)));}catch(Exception ignored){}
            return;
        }
        requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},STEP_PERMISSION);
    }
    private void startStepCounting(){
        if(!DaySteps.ready(this))return;StepSyncJob.schedule(this);if(stepListening)return;
        SensorManager sm=(SensorManager)getSystemService(SENSOR_SERVICE);Sensor counter=sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if(counter!=null)stepListening=sm.registerListener(stepListener,counter,SensorManager.SENSOR_DELAY_UI);
    }
    private void refreshHealth(boolean force){
        long now=android.os.SystemClock.elapsedRealtime();if(!force&&now-lastHealthRefresh<120000)return;lastHealthRefresh=now;
        try{HealthSync.refresh(this,()->{StepsWidget.refresh(this);if(resumed&&!dragging&&("Health".equals(page)||"Today".equals(page))){int y=scroller.getScrollY();renderPageNow();scroller.post(()->scroller.scrollTo(0,y));}syncing=false;});}catch(Throwable ignored){}
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;checkForUpdateOnce();WidgetTick.schedule(this);handler.removeCallbacks(refresh);handler.post(refresh);startStepCounting();refreshHealth(true);handler.postDelayed(()->SessionMetrics.warm(this,history()),1500);handler.postDelayed(()->{WearBridge.pushToday(this);WearBridge.pushLive(this);},4000);
        if(content!=null&&(("Today".equals(page)&&stepSetupShown&&DaySteps.permitted(this))||"Health".equals(page)||"Notifications".equals(page)||"Permissions".equals(page)||"Auto".equals(page)||"Storage".equals(page)))renderPageNow();
    }
    @Override protected void onPause(){
        resumed=false;handler.removeCallbacks(refresh);if(stepListening){((SensorManager)getSystemService(SENSOR_SERVICE)).unregisterListener(stepListener);stepListening=false;}
        super.onPause();
    }

    // ---- Saved sessions: name, share, delete ----
    private String sessionLabel(JSONObject o){String name=o.optString("name","").trim();return name.isEmpty()?o.optString("type","Walk"):name;}
    private String sessionTitle(JSONObject o){String name=o.optString("name","").trim();return name.isEmpty()?o.optString("type","Walk")+" complete":name;}
    // ---- Route replay ----
    private android.animation.ValueAnimator replayAnim;
    /** Play, scrub and speed controls under the session map, and a live caption on the map while it plays. */
    private void replayControls(FrameLayout mapBox,RouteView rv,JSONObject o){
        TextView hud=tx("",12,Color.WHITE,true);hud.setPadding(dp(10),dp(6),dp(10),dp(6));hud.setBackground(shape(Color.argb(225,22,22,26),12));hud.setVisibility(View.GONE);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);hp.setMargins(dp(10),0,0,dp(10));mapBox.addView(hud,hp);
        LinearLayout bar=row();bar.setPadding(dp(6),dp(6),dp(10),dp(6));bar.setBackground(panelShape(16));LinearLayout.LayoutParams bp=lp(-1,-2);bp.topMargin=dp(8);content.addView(bar,bp);
        TextView play=tx("▶",16,accentOn,true);play.setGravity(Gravity.CENTER);GradientDrawable pg=new GradientDrawable();pg.setShape(GradientDrawable.OVAL);pg.setColor(lime);play.setBackground(ripple(pg));play.setContentDescription("Replay this route");bar.addView(play,lp(44,44));
        android.widget.SeekBar seek=new android.widget.SeekBar(this);seek.setMax(1000);seek.setProgress(1000);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(lime));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(lime));seek.setContentDescription("Replay position");bar.addView(seek,new LinearLayout.LayoutParams(0,-2,1));
        TextView speed=tx("1×",13,ink,true);speed.setGravity(Gravity.CENTER);speed.setBackground(rippleOn(raised,12));speed.setContentDescription("Replay speed");bar.addView(speed,lp(44,34));
        float[] sp={1f};boolean[] user={false};
        Runnable caption=()->{double[] st=rv.replayState();long sec=Math.round(st[2]);hud.setText((rv.replay()>=1?"Finished":st[1]>0?"Running":"Walking")+String.format(Locale.US,"  ·  %.2f km  ·  %d:%02d",st[0]/1000,sec/60,sec%60));};
        Runnable stop=()->{if(replayAnim!=null){replayAnim.cancel();replayAnim=null;}play.setText("▶");play.setContentDescription("Replay this route");};
        seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(android.widget.SeekBar b,int v,boolean fromUser){if(!fromUser)return;stop.run();rv.setReplay(v/1000f);hud.setVisibility(View.VISIBLE);caption.run();}
            public void onStartTrackingTouch(android.widget.SeekBar b){RouteView.gestureActive=true;}
            public void onStopTrackingTouch(android.widget.SeekBar b){RouteView.gestureActive=false;}});
        play.setOnClickListener(v->{
            if(replayAnim!=null){stop.run();return;}
            float from=rv.replay()<0||rv.replay()>=1?0:rv.replay();rv.resetView();hud.setVisibility(View.VISIBLE);
            replayAnim=android.animation.ValueAnimator.ofFloat(from,1f);replayAnim.setDuration((long)(13500*(1-from)/sp[0]));replayAnim.setInterpolator(new android.view.animation.LinearInterpolator());
            replayAnim.addUpdateListener(a->{float f=(float)a.getAnimatedValue();rv.setReplay(f);seek.setProgress(Math.round(f*1000));caption.run();});
            replayAnim.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(replayAnim==a){replayAnim=null;play.setText("↺");play.setContentDescription("Replay again");}}});
            play.setText("Ⅱ");play.setContentDescription("Pause replay");replayAnim.start();
        });
        speed.setOnClickListener(v->{sp[0]=sp[0]==1?2:sp[0]==2?4:1;speed.setText((int)sp[0]+"×");if(replayAnim!=null){float f=rv.replay();stop.run();rv.setReplay(f);play.performClick();}});
    }

    /** Asks for dark or light, encodes the replay video with a progress bar, then opens the share sheet. */
    private void shareReplay(JSONObject o){
        makeReplay(o,darkTheme);   // the video follows the app's theme
    }
    private void makeReplay(JSONObject o,boolean dark){
        LinearLayout box=col();box.setPadding(dp(22),dp(14),dp(22),dp(6));TextView msg=tx("Drawing your route… 0%",14,ink,false);box.addView(msg);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(lime));LinearLayout.LayoutParams bp=lp(-1,10);bp.topMargin=dp(12);box.addView(bar,bp);
        AlertDialog wait=dialog().setTitle("Replay video").setView(box).setCancelable(false).create();wait.show();
        ReplayVideo.export(this,o,sessionTitle(o),dark,pc->{bar.setProgress(pc);msg.setText("Drawing your route… "+pc+"%");},file->{
            wait.dismiss();if(file==null){toast("Couldn't make the video on this phone");return;}
            Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",file);
            shareSheet("Share replay video",String.format(Locale.US,"%s · %.2f km in %s · tracked with Kadamm",sessionTitle(o),o.optDouble("distanceKm",0),formatDuration(o.optLong("durationMs",0))),uri,"video/mp4");
        });
    }
    private void sessionActions(JSONObject o){
        LinearLayout r=row();LinearLayout.LayoutParams p=lp(-1,46);p.topMargin=dp(12);content.addView(r,p);
        Button share=button("Share",lime,accentOn),rename=button("Rename",raised,ink),delete=button("Delete",raised,critical);
        r.addView(share,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=dp(8);r.addView(rename,rp);LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,-1,1);dlp.leftMargin=dp(8);r.addView(delete,dlp);
        share.setOnClickListener(v->shareSession(o));
        if(o.optString("route","[]").contains("\"s\"")&&o.optDouble("distanceKm",0)>=.1){Button vid=button("▶  Share replay video",raised,ink);LinearLayout.LayoutParams vp=lp(-1,46);vp.topMargin=dp(8);content.addView(vid,vp);vid.setOnClickListener(v->shareReplay(o));}
        if(Insights.points(o.optString("route","[]")).size()>=5&&o.optDouble("distanceKm",0)>=.1){
            boolean saved=false;JSONArray rs=Insights.routes(this);for(int i=0;i<rs.length();i++)if(rs.optJSONObject(i).optLong("fromSession")==o.optLong("id"))saved=true;
            if(!saved){Button save=button("Save as route",raised,ink);LinearLayout.LayoutParams svp=lp(-1,46);svp.topMargin=dp(8);content.addView(save,svp);
                save.setOnClickListener(v->{android.widget.EditText input=new android.widget.EditText(this);input.setSingleLine(true);input.setHint("Morning lap");
                    dialog().setTitle("Save as route").setMessage("Kadamm will recognise this route when you walk or run it again and compare your times.").setView(input).setNegativeButton("Cancel",null)
                        .setPositiveButton("Save",(d,w)->{String n=input.getText().toString().trim();Insights.saveRoute(this,n.isEmpty()?"Route "+(Insights.routes(this).length()+1):(n.length()>40?n.substring(0,40):n),o);toast("Route saved");renderPageNow();}).show();});}
        }
        rename.setOnClickListener(v->{
            android.widget.EditText input=new android.widget.EditText(this);input.setSingleLine(true);input.setText(o.optString("name",""));input.setHint("Morning walk");
            dialog().setTitle("Name this activity").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{String name=input.getText().toString().trim();if(name.length()>40)name=name.substring(0,40);o.put("name",name);saveSession(historyPrefs,o);lastSession=o;renderPage();}catch(Exception ignored){}}).show();
        });
        delete.setOnClickListener(v->dialog().setTitle("Delete this activity?").setMessage("Its route and statistics will be removed from this phone. This cannot be undone.").setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{
            try{JSONArray old=history(),kept=new JSONArray();for(int i=0;i<old.length();i++){JSONObject s=old.optJSONObject(i);if(s!=null&&s.optLong("id",-1)!=o.optLong("id",-2))kept.put(s);}historyPrefs.edit().putString("sessions",kept.toString()).apply();}catch(Exception ignored){}
            lastSession=null;page="History";toast("Activity deleted");renderPage();}).show());
    }
    private String shareText(JSONObject o){
        double km=o.optDouble("distanceKm",0);long ms=o.optLong("durationMs",0),sec=km>0?Math.round(ms/1000d/km):0;
        StringBuilder s=new StringBuilder(sessionLabel(o)).append(" · ").append(o.optString("date","")).append('\n').append(String.format(Locale.US,"%.2f km in %s",km,formatDuration(ms)));
        if(km>0)s.append(String.format(Locale.US," · %d:%02d /km",sec/60,sec%60));
        if(o.optBoolean("stepsAvailable",false))s.append(String.format(Locale.US," · %,d steps",o.optInt("steps")));
        return s.append(String.format(Locale.US," · ~%d kcal",o.optInt("calories",(int)Math.round(km*65)))).append("\nTracked with Kadamm").toString();
    }
    /** Renders the route-and-stats image off the main thread, previews it with a Light/Dark choice, then shares the PNG. */
    private void shareSession(JSONObject o){
        toast("Preparing your activity image…");String text=shareText(o),title=sessionTitle(o);
        new Thread(()->{
            try{
                ShareCard.render(this,o,title,true);ShareCard.render(this,o,title,false);   // first passes ask for missing map tiles
                for(int i=0;i<40&&RouteView.pendingTiles()>0;i++)Thread.sleep(100);
                android.graphics.Bitmap[] img={ShareCard.render(this,o,title,true),ShareCard.render(this,o,title,false)};
                runOnUiThread(()->showSharePreview(img,text));
            }catch(Exception e){
                runOnUiThread(()->{try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Share activity"));}catch(Exception ignored){}});
            }
        },"KadammShare").start();
    }
    private void showSharePreview(android.graphics.Bitmap[] img,String text){
        int[] pick={darkTheme?0:1};
        LinearLayout box=col();box.setPadding(dp(16),dp(14),dp(16),0);
        LinearLayout seg=row();box.addView(seg,lp(-1,40));
        android.widget.ImageView preview=new android.widget.ImageView(this);preview.setAdjustViewBounds(true);preview.setMaxHeight((int)(getResources().getDisplayMetrics().heightPixels*.56f));preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams pp=lp(-1,-2);pp.topMargin=dp(12);box.addView(preview,pp);
        Button[] modes={button("Dark",raised,ink),button("Light",raised,ink)};
        Runnable paint=()->{preview.setImageBitmap(img[pick[0]]);for(int i=0;i<2;i++){boolean on=i==pick[0];modes[i].setTextColor(on?accentOn:ink);modes[i].setBackground(ripple(shape(on?lime:raised,20)));}};
        for(int i=0;i<2;i++){int m=i;LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)bp.leftMargin=dp(8);seg.addView(modes[i],bp);modes[i].setOnClickListener(v->{pick[0]=m;paint.run();});}
        paint.run();
        // Share targets: WhatsApp straight away, a copy in Photos, or any other app.
        TextView to=tx("SHARE TO",10,muted,true);to.setLetterSpacing(.08f);LinearLayout.LayoutParams top=lp(-1,-2);top.topMargin=dp(14);box.addView(to,top);
        LinearLayout targets=row();targets.setGravity(Gravity.TOP);LinearLayout.LayoutParams tgp=lp(-1,-2);tgp.topMargin=dp(8);tgp.bottomMargin=dp(6);box.addView(targets,tgp);
        AlertDialog ad=dialog().setView(box).setNegativeButton("Close",null).create();
        int wa=Color.rgb(37,211,102);
        if(installed("com.whatsapp"))shareTarget(targets,"WhatsApp",R.drawable.ic_chat,wa,()->{ad.dismiss();shareImage(img[pick[0]],text,"com.whatsapp");});
        if(installed("com.whatsapp.w4b"))shareTarget(targets,"WA Business",R.drawable.ic_chat,blend(wa,Color.rgb(7,94,84),.5f),()->{ad.dismiss();shareImage(img[pick[0]],text,"com.whatsapp.w4b");});
        if(Build.VERSION.SDK_INT>=29)shareTarget(targets,"Save to Photos",R.drawable.ic_download,violet,()->{ad.dismiss();saveToPhotos(img[pick[0]]);});
        shareTarget(targets,"More apps",R.drawable.ic_share,lime,()->{ad.dismiss();shareImage(img[pick[0]],text,null);});
        ad.show();
    }
    /**
     * One share sheet for the whole app: WhatsApp (and WhatsApp Business) straight away, any other app, or copy.
     * {@code file} is optional; with one the text goes along as its caption.
     */
    private void shareSheet(String title,String text,Uri file,String mime){
        LinearLayout sheet=col();PullDown sheetPull=new PullDown(getResources().getDisplayMetrics().density,true);
        android.app.Dialog d=new android.app.Dialog(this){@Override public boolean dispatchTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)sheetPull.begin(e,sheet,this::dismiss);return sheetPull.handle(e,c->super.dispatchTouchEvent(c))||super.dispatchTouchEvent(e);}};d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        sheet.setPadding(dp(20),dp(10),dp(20),dp(20));GradientDrawable sb=new GradientDrawable();sb.setColor(panel);sb.setCornerRadii(new float[]{dp(26),dp(26),dp(26),dp(26),0,0,0,0});sheet.setBackground(sb);
        View grip=new View(this);grip.setBackground(shape(line,3));LinearLayout.LayoutParams gp=lp(40,5);gp.gravity=Gravity.CENTER_HORIZONTAL;gp.bottomMargin=dp(14);sheet.addView(grip,gp);
        sheet.addView(tx(title,20,ink,true));
        TextView msg=tx(text,13,ink,false);msg.setPadding(dp(14),dp(12),dp(14),dp(12));msg.setBackground(shape(raised,14));msg.setMaxLines(9);msg.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams mp=lp(-1,-2);mp.topMargin=dp(10);sheet.addView(msg,mp);
        LinearLayout targets=row();targets.setGravity(Gravity.TOP);LinearLayout.LayoutParams tp=lp(-1,-2);tp.topMargin=dp(16);sheet.addView(targets,tp);
        int wa=Color.rgb(37,211,102);
        if(installed("com.whatsapp"))shareTarget(targets,"WhatsApp",R.drawable.ic_chat,wa,()->{d.dismiss();send(text,file,mime,"com.whatsapp");});
        if(installed("com.whatsapp.w4b"))shareTarget(targets,"WA Business",R.drawable.ic_chat,blend(wa,Color.rgb(7,94,84),.5f),()->{d.dismiss();send(text,file,mime,"com.whatsapp.w4b");});
        if(file!=null&&mime!=null&&(mime.startsWith("video/")||mime.startsWith("image/"))&&Build.VERSION.SDK_INT>=29)shareTarget(targets,"Save",R.drawable.ic_download,violet,()->{d.dismiss();saveToGallery(file,mime);});
        shareTarget(targets,"More apps",R.drawable.ic_share,lime,()->{d.dismiss();send(text,file,mime,null);});
        if(file==null)shareTarget(targets,"Copy",R.drawable.ic_storage,violet,()->{d.dismiss();android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText("Kadamm",text));if(Build.VERSION.SDK_INT<33)toast("Copied");});
        d.setContentView(sheet);android.view.Window w=d.getWindow();
        if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);w.setWindowAnimations(android.R.style.Animation_InputMethod);w.setDimAmount(.55f);}
        d.show();
    }
    /** Copies a shared file into Movies/Kadamm or Pictures/Kadamm (Android 10+ needs no storage permission). */
    private void saveToGallery(Uri src,String mime){
        boolean video=mime.startsWith("video/");
        try{
            android.content.ContentValues v=new android.content.ContentValues();v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"kadamm-"+System.currentTimeMillis()+(video?".mp4":".png"));v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,mime);v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,(video?"Movies":"Pictures")+"/Kadamm");
            Uri dst=getContentResolver().insert(video?android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI:android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(dst==null)throw new IllegalStateException();
            try(java.io.InputStream in=getContentResolver().openInputStream(src);java.io.OutputStream out=getContentResolver().openOutputStream(dst)){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}
            toast(video?"Saved to Movies/Kadamm":"Saved to Pictures/Kadamm");
        }catch(Exception e){toast("Couldn't save it");}
    }
    private void send(String text,Uri file,String mime,String pkg){
        Intent i=new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT,text);
        if(file!=null)i.setType(mime).putExtra(Intent.EXTRA_STREAM,file).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);else i.setType("text/plain");
        try{if(pkg!=null)startActivity(i.setPackage(pkg));else startActivity(Intent.createChooser(i,"Share"));}catch(Exception e){toast(pkg!=null?"Couldn't open that app":"No app available to share with");}
    }
    /** Shares Kadamm itself: this installed APK, so a friend can install it from the chat. */
    private void shareApp(){
        try{
            java.io.File dir=new java.io.File(getCacheDir(),"share");dir.mkdirs();java.io.File apk=new java.io.File(dir,"Kadamm-v"+APP_VERSION+".apk");
            if(!apk.exists()){java.io.File[] old=dir.listFiles();if(old!=null)for(java.io.File f:old)if(f.getName().endsWith(".apk"))f.delete();
                try(java.io.InputStream in=new java.io.FileInputStream(getApplicationInfo().sourceDir);java.io.OutputStream out=new java.io.FileOutputStream(apk)){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}}
            Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",apk);
            shareSheet("Share Kadamm","I track my walks and runs with Kadamm: all-day steps, GPS routes, streaks and reports, all kept on the phone. Here's the app (v"+APP_VERSION+") — open the file to install it on Android.",uri,"application/vnd.android.package-archive");
        }catch(Exception e){toast("Couldn't prepare the app file");}
    }
    private String todayShareText(){
        int goal=dailyGoal();long n=DaySteps.todayTotal(this);int[] st=DaySteps.streaks(DaySteps.allDayTotals(this),goal);double[] dist=DaySteps.distanceToday(this);
        StringBuilder b=new StringBuilder(String.format(Locale.US,"My day on Kadamm: %,d steps (%d%% of my %,d goal)",n,Math.round(n*100f/Math.max(1,goal)),goal));
        b.append(String.format(Locale.US,"\n%.2f km · %,d active kcal · %d move min",dist[0],DaySteps.activeKcalToday(this),DaySteps.moveMinutesToday(this)));
        if(st[0]>0)b.append(String.format(Locale.US,"\n%d-day goal streak",st[0]));
        return b.append(n>=goal?"\nGoal done for today ✓":"").toString();
    }
    private android.widget.ImageView shareIcon(String what,Runnable go){android.widget.ImageView s=icon(R.drawable.ic_share,muted,10);s.setContentDescription("Share "+what);s.setFocusable(true);s.setBackground(rippleOn(Color.TRANSPARENT,20));s.setOnClickListener(v->go.run());return s;}
    private void shareTarget(LinearLayout parent,String label,int res,int color,Runnable go){
        LinearLayout t=col();t.setGravity(Gravity.CENTER_HORIZONTAL);t.setPadding(0,dp(6),0,dp(6));t.setBackground(rippleOn(Color.TRANSPARENT,14));t.setFocusable(true);t.setContentDescription("Share to "+label);
        android.widget.ImageView ic=icon(res,Color.WHITE,13);GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(color);ic.setBackground(g);t.addView(ic,lp(52,52));
        TextView l=tx(label,11,ink,true);l.setGravity(Gravity.CENTER);LinearLayout.LayoutParams lpp=lp(-1,-2);lpp.topMargin=dp(6);t.addView(l,lpp);
        parent.addView(t,new LinearLayout.LayoutParams(0,-2,1));t.setOnClickListener(v->go.run());
    }
    /** Shares the image and caption; {@code pkg} sends it straight to that app (WhatsApp opens its chat picker), null opens Android's share sheet. */
    private void shareImage(android.graphics.Bitmap bmp,String text,String pkg){
        try{
            java.io.File dir=new java.io.File(getCacheDir(),"share");dir.mkdirs();java.io.File file=new java.io.File(dir,"kadamm-activity.png");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",file);
            Intent send=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_TEXT,text).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(pkg!=null)startActivity(send.setPackage(pkg));else startActivity(Intent.createChooser(send,"Share activity"));
        }catch(Exception e){toast(pkg!=null?"Couldn't open that app":"No app available to share with");}
    }
    /** Saves the image to Pictures/Kadamm (Android 10+ needs no storage permission for this). */
    private void saveToPhotos(android.graphics.Bitmap bmp){
        try{
            android.content.ContentValues v=new android.content.ContentValues();v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"kadamm-"+System.currentTimeMillis()+".png");v.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Kadamm");
            Uri uri=getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IllegalStateException();
            try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            toast("Saved to Pictures/Kadamm");
        }catch(Exception e){toast("Couldn't save the image");}
    }

    // ---- Live session screen ----
    private FrameLayout liveLayer;private RouteView liveMap;private TextView lvClose,lvTitle,lvStatus,lvTime,lvPace,lvDist,lvSteps,lvKcal,lvSpeed;private Button lvMain,lvFinish;private long liveOpenedAt;
    private void openLive(){
        if(liveLayer==null)buildLive();
        if(liveLayer.getVisibility()==View.VISIBLE)return;
        if(Build.VERSION.SDK_INT>=27)setShowWhenLocked(true);
        liveOpenedAt=android.os.SystemClock.elapsedRealtime();liveClosing=false;liveLayer.setVisibility(View.VISIBLE);liveLayer.animate().cancel();
        boolean fromBar=miniBar!=null&&miniBar.getVisibility()==View.VISIBLE;
        if(reducedMotion()){liveLayer.setAlpha(1f);liveLayer.setTranslationY(0);liveLayer.setScaleX(1f);if(miniBar!=null)miniBar.setVisibility(View.GONE);}
        else{
            // Grows up out of the mini bar: starts low and slightly narrow, eases to full screen.
            float h=Math.max(1,root.getHeight());liveLayer.setPivotX(root.getWidth()/2f);liveLayer.setPivotY(h);
            liveLayer.setTranslationY(fromBar?h*.82f:dp(48));liveLayer.setScaleX(fromBar?.94f:1f);liveLayer.setAlpha(fromBar?.35f:0f);
            liveLayer.animate().translationY(0).scaleX(1f).alpha(1f).setDuration(fromBar?380:260).setInterpolator(EASE).start();
            if(fromBar)miniBar.animate().alpha(0f).translationY(-dp(16)).setDuration(180).withEndAction(()->{miniBar.setVisibility(View.GONE);miniBar.setAlpha(1f);miniBar.setTranslationY(0);}).start();
        }
        getWindow().setStatusBarColor(ShareCard.BLACK);root.setBackgroundColor(ShareCard.BLACK);getWindow().getDecorView().setSystemUiVisibility(0);updateLiveOverlay();
    }
    private void closeLive(){
        if(liveLayer==null||liveLayer.getVisibility()!=View.VISIBLE)return;
        if(Build.VERSION.SDK_INT>=27)setShowWhenLocked(false);
        boolean toBar=prefs.getBoolean("active",false);float h=Math.max(1,root.getHeight());liveLayer.setPivotX(root.getWidth()/2f);liveLayer.setPivotY(h);
        // Slides down and narrows into the mini bar, which rises into place as the screen leaves.
        liveLayer.animate().cancel();
        liveLayer.animate().translationY(toBar?h*.86f:dp(48)).scaleX(toBar?.94f:1f).alpha(0f).setDuration(reducedMotion()?0:(toBar?360:180)).setInterpolator(EASE)
            .withEndAction(()->{liveLayer.setVisibility(View.GONE);liveLayer.setTranslationY(0);liveLayer.setScaleX(1f);liveClosing=false;updateMiniBar();}).start();
        applyTheme();if(toBar){liveClosing=true;updateMiniBar();}
    }
    private void buildLive(){
        int dark=ShareCard.BLACK,card=Color.argb(245,22,22,26),sub=Color.rgb(154,160,166),white=Color.rgb(245,245,247);
        liveLayer=new FrameLayout(this);liveLayer.setBackgroundColor(dark);liveLayer.setClickable(true);liveLayer.setVisibility(View.GONE);root.addView(liveLayer,new FrameLayout.LayoutParams(-1,-1));
        liveMap=new RouteView(this);liveMap.setInteractive(true);liveMap.setTheme(dark,Color.rgb(38,38,42),ShareCard.ROUTE,true);liveLayer.addView(liveMap,new FrameLayout.LayoutParams(-1,-1));
        TextView down=tx("‹",30,white,true);down.setRotation(-90);down.setGravity(Gravity.CENTER);down.setBackground(ripple(shape(Color.argb(235,22,22,26),24)));down.setContentDescription("Minimise live session");down.setOnClickListener(v->closeLive());
        FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.START);dlp.setMargins(dp(16),dp(16),0,0);liveLayer.addView(down,dlp);
        android.widget.ImageView follow=icon(R.drawable.ic_my_location,white,12);follow.setBackground(ripple(shape(Color.argb(235,22,22,26),24)));follow.setContentDescription("Follow my location");follow.setOnClickListener(v->liveMap.setFollowLocation(true));
        FrameLayout.LayoutParams flp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);flp.setMargins(0,dp(16),dp(16),0);liveLayer.addView(follow,flp);
        LinearLayout panel=col();liveLayer.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        LinearLayout sc=col();sc.setPadding(dp(18),dp(14),dp(18),dp(16));sc.setBackground(shape(card,22));LinearLayout.LayoutParams scp=lp(-1,-2);scp.setMargins(dp(14),0,dp(14),dp(12));panel.addView(sc,scp);
        LinearLayout th=row();lvTitle=tx("Walk",17,white,true);th.addView(lvTitle,new LinearLayout.LayoutParams(0,-2,1));lvStatus=tx("● GPS",11,positive,true);th.addView(lvStatus);sc.addView(th);
        LinearLayout r1=row();LinearLayout.LayoutParams r1p=lp(-1,-2);r1p.topMargin=dp(12);sc.addView(r1,r1p);
        lvTime=liveStat(r1,"Time",sub,white,22,1f);lvPace=liveStat(r1,"Avg pace (/km)",sub,white,40,1.5f);lvDist=liveStat(r1,"Distance (km)",sub,white,22,1f);
        View rule=new View(this);rule.setBackgroundColor(Color.rgb(42,42,46));LinearLayout.LayoutParams rp=lp(-1,1);rp.setMargins(0,dp(12),0,dp(12));sc.addView(rule,rp);
        LinearLayout r2=row();sc.addView(r2);lvSteps=liveStat(r2,"Steps",sub,white,19,1f);lvKcal=liveStat(r2,"Calories",sub,white,19,1f);lvSpeed=liveStat(r2,"Speed (km/h)",sub,white,19,1f);
        // Auto-close: tap to cycle how long the session waits, idle, before it finishes by itself.
        lvClose=tx("",12,white,true);lvClose.setGravity(Gravity.CENTER);lvClose.setPadding(dp(14),dp(8),dp(14),dp(8));lvClose.setBackground(ripple(shape(Color.rgb(36,36,40),16)));
        LinearLayout.LayoutParams lcp=lp(-2,-2);lcp.gravity=Gravity.CENTER_HORIZONTAL;lcp.topMargin=dp(12);sc.addView(lvClose,lcp);
        lvClose.setOnClickListener(v->{int[] opts={2,3,5,10,0};int cur=AutoTrack.stopAfterMin(this),k=0;for(int i=0;i<opts.length;i++)if(opts[i]==cur)k=(i+1)%opts.length;AutoTrack.s(this).edit().putInt("autoStopMin",opts[k]).apply();updateLiveOverlay();});
        LinearLayout ctl=row();ctl.setPadding(dp(14),dp(14),dp(14),dp(16));GradientDrawable sheet=new GradientDrawable();sheet.setColor(card);sheet.setCornerRadii(new float[]{dp(24),dp(24),dp(24),dp(24),0,0,0,0});ctl.setBackground(sheet);panel.addView(ctl,lp(-1,-2));
        lvMain=pill("Pause",ShareCard.BLUE,Color.WHITE);ctl.addView(lvMain,new LinearLayout.LayoutParams(0,dp(58),1));lvMain.setOnClickListener(v->command(RecorderService.ACTION_TOGGLE));
        lvFinish=pill("Finish",white,dark);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(58),1);fp.leftMargin=dp(12);ctl.addView(lvFinish,fp);lvFinish.setOnClickListener(v->finishActivity());
    }
    private TextView liveStat(LinearLayout row,String label,int sub,int white,int size,float weight){
        LinearLayout c=col();c.setGravity(Gravity.CENTER_HORIZONTAL);row.addView(c,new LinearLayout.LayoutParams(0,-2,weight));
        TextView v=tx("—",size,white,true);v.setGravity(Gravity.CENTER);c.addView(v);TextView l=tx(label,11,sub,false);l.setGravity(Gravity.CENTER);c.addView(l);return v;
    }
    private Button pill(String text,int color,int textColor){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(17);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setTextColor(textColor);b.setBackground(ripple(shape(color,29)));b.setStateListAnimator(null);return b;
    }
    private void updateLiveOverlay(){
        if(liveLayer==null||liveLayer.getVisibility()!=View.VISIBLE)return;
        // The recorder may not have written "active" yet right after Start; only close once it has had time to.
        if(!prefs.getBoolean("active",false)){if(android.os.SystemClock.elapsedRealtime()-liveOpenedAt>3000)closeLive();return;}
        boolean paused=prefs.getBoolean("paused",false),indoor="indoor".equals(prefs.getString("activityMode","outdoor"));
        long ms=prefs.getLong("elapsed",0);float km=prefs.getFloat("distance",0);long ps=km>=.05f?Math.round(ms/1000d/km):0;if(ps>3600)ps=0;
        long fixAge=android.os.SystemClock.elapsedRealtime()-prefs.getLong("lastFixElapsed",0);boolean gps=prefs.getBoolean("gpsFix",false)&&fixAge<12000;
        lvTitle.setText((indoor?"Indoor ":"")+("Run".equals(prefs.getString("currentType","Walk"))?"Run":"Walk")+(paused?"  ·  Paused":""));
        lvStatus.setText(paused?"●  Paused":indoor?"●  Counting steps":gps?String.format(Locale.US,"●  GPS ±%.0f m",prefs.getFloat("accuracy",0)):"●  Finding GPS");lvStatus.setTextColor(paused?Color.rgb(154,160,166):indoor||gps?positive:warning);
        int liveSteps=prefs.getInt("steps",0);if(!paused&&prefs.getBoolean("stepsAvailable",false)&&liveSteps<SessionRules.MIN_STEPS){lvStatus.setText("●  "+liveSteps+" of "+SessionRules.MIN_STEPS+" steps to count");lvStatus.setTextColor(Color.rgb(251,188,4));}
        lvTime.setText(formatDuration(ms));lvPace.setText(ps>0?String.format(Locale.US,"%d:%02d",ps/60,ps%60):"—:—");lvDist.setText(String.format(Locale.US,"%.2f",km));
        lvSteps.setText(String.format(Locale.US,"%,d",prefs.getInt("steps",0)));lvKcal.setText(String.valueOf(Calories.live(this)));float kmh=paused?0:prefs.getFloat("speedKmh",0);lvSpeed.setText(kmh>0?String.format(Locale.US,"%.1f",kmh):"—");
        lvMain.setText(paused?"Resume":"Pause");lvFinish.setVisibility(View.VISIBLE);
        int closeMin=AutoTrack.stopAfterMin(this);long closesIn=prefs.getLong("closesInS",-1);
        lvClose.setText(closeMin<=0?"Auto-close: off  ·  tap to change":closesIn>=0?String.format(Locale.US,"No movement · closes in %d:%02d  ·  tap to change",closesIn/60,closesIn%60):"Auto-close after "+closeMin+" min idle  ·  tap to change");
        lvClose.setTextColor(closesIn>=0&&closeMin>0?Color.rgb(251,188,4):Color.WHITE);
        liveMap.setEmptyMessage(indoor?"Indoor session · distance comes from your steps":"Finding GPS…");liveMap.setRoute(prefs.getString("route","[]"));
        if(!indoor&&gps)liveMap.setCurrentLocation(prefs.getFloat("latitude",0),prefs.getFloat("longitude",0),prefs.getFloat("accuracy",0));else if(!indoor&&hasIdle)liveMap.setCurrentLocation(idleLat,idleLon,idleAcc);
    }
    private void askBackground(){
        try{
            android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);SharedPreferences st=getSharedPreferences("kadamm_settings",MODE_PRIVATE);
            if(pm==null||pm.isIgnoringBatteryOptimizations(getPackageName())||st.getBoolean("batteryAsked",false))return;
            st.edit().putBoolean("batteryAsked",true).apply();
            dialog().setTitle("Keep tracking in the background").setMessage("So Android and Samsung's battery saver never pause your session when you switch apps, allow Kadamm to run without battery restrictions.").setNegativeButton("Not now",null)
                .setPositiveButton("Allow",(d,w)->{try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}}).show();
        }catch(Exception ignored){}
    }
    // ---- Full-screen map (from the dashboard) ----
    private FrameLayout mapLayer;private RouteView bigMap;
    /** The dashboard map stays still so the page scrolls; this view is the one to drag, pinch and explore. */
    private void openMapView(){
        if(mapLayer==null){
            mapLayer=new FrameLayout(this);mapLayer.setClickable(true);root.addView(mapLayer,new FrameLayout.LayoutParams(-1,-1));
            bigMap=new RouteView(this);bigMap.setInteractive(true);mapLayer.addView(bigMap,new FrameLayout.LayoutParams(-1,-1));
            android.widget.ImageView close=icon(R.drawable.ic_close,ink,12);close.setBackground(rippleOn(panel,24));close.setContentDescription("Close map");close.setOnClickListener(v->closeMapView());
            FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.START);cp.setMargins(dp(16),dp(16),0,0);mapLayer.addView(close,cp);
            android.widget.ImageView me=icon(R.drawable.ic_my_location,ink,12);me.setBackground(rippleOn(panel,24));me.setContentDescription("Centre on my location");me.setOnClickListener(v->bigMap.setFollowLocation(true));
            FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);mp.setMargins(0,dp(16),dp(16),0);mapLayer.addView(me,mp);
            TextView style=tx("",12,ink,true);style.setPadding(dp(14),dp(8),dp(14),dp(8));style.setBackground(rippleOn(panel,16));FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);sp.topMargin=dp(22);mapLayer.addView(style,sp);
            Runnable showStyle=()->style.setText("Map: "+mapStyleName());showStyle.run();
            style.setOnClickListener(v->{cycleMapStyle();bigMap.setTheme(bg,raised,lime,darkTheme);showStyle.run();});
            TextView hint=tx("Drag to move · pinch or double-tap to zoom",12,ink,true);hint.setPadding(dp(14),dp(8),dp(14),dp(8));hint.setBackground(shape(panel,16));
            FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hp.bottomMargin=dp(24);mapLayer.addView(hint,hp);
        }
        bigMap.setTheme(bg,raised,lime,darkTheme);bigMap.setRoute(prefs.getBoolean("active",false)?prefs.getString("route","[]"):"[]");
        if(prefs.getBoolean("active",false)&&prefs.getBoolean("gpsFix",false))bigMap.setCurrentLocation(prefs.getFloat("latitude",0),prefs.getFloat("longitude",0),prefs.getFloat("accuracy",0));else if(hasIdle)bigMap.setCurrentLocation(idleLat,idleLon,idleAcc);
        bigMap.setFollowLocation(true);mapLayer.setTranslationY(0);mapLayer.setVisibility(View.VISIBLE);mapLayer.setAlpha(0f);mapLayer.animate().alpha(1f).setDuration(reducedMotion()?0:180).start();
    }
    private void closeMapView(){if(mapLayer!=null&&mapLayer.getVisibility()==View.VISIBLE)mapLayer.animate().alpha(0f).setDuration(reducedMotion()?0:150).withEndAction(()->mapLayer.setVisibility(View.GONE)).start();}
    // ---- Account ----
    private String displayName(){String n=LocalAccount.name(this);return n!=null?n:"Kadamm athlete";}
    private String initials(){String n=LocalAccount.name(this);if(n==null)return "KA";String[] p=n.trim().split("\\s+");String out=p[0].substring(0,1)+(p.length>1?p[p.length-1].substring(0,1):"");return out.toUpperCase(Locale.getDefault());}
    private void renderAccount(){
        LinearLayout c=card();c.addView(tx("Account",16,ink,true));String email=LocalAccount.email(this);
        if(email!=null){
            c.addView(tx("Signed in as "+email+" · stored in this phone's database",12,muted,false));
            LinearLayout ar=row();LinearLayout.LayoutParams arp=lp(-1,46);arp.topMargin=dp(12);c.addView(ar,arp);
            Button pwb=button("Change password",raised,ink),sw=button(LocalAccount.all(this).size()>1?"Switch account":"Add account",raised,ink);ar.addView(pwb,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams swp=new LinearLayout.LayoutParams(0,-1,1);swp.leftMargin=dp(8);ar.addView(sw,swp);
            pwb.setOnClickListener(v->changePassword());sw.setOnClickListener(v->{LocalAccount.logOut(this);closeDrawer();showAuth(true);});
            Button out=button("Log out",raised,critical);LinearLayout.LayoutParams op=lp(-1,46);op.topMargin=dp(8);c.addView(out,op);
            out.setOnClickListener(v->dialog().setTitle("Log out?").setMessage("Your activity stays on this phone. Log back in with your email and password.").setNegativeButton("Cancel",null).setPositiveButton("Log out",(d,w)->{LocalAccount.logOut(this);closeDrawer();showAuth(true);}).show());
        }else{
            c.addView(tx("Create a Kadamm account to put your name on your activity. Everything stays on this phone.",12,muted,false));
            Button in=button(LocalAccount.exists(this)?"Log in":"Sign up",lime,accentOn);in.setTextSize(15);LinearLayout.LayoutParams ip=lp(-1,50);ip.topMargin=dp(12);c.addView(in,ip);
            in.setOnClickListener(v->showAuth(LocalAccount.exists(this)));
        }
    }

    // ---- Sign up / log in (local account) ----
    private FrameLayout authLayer;private Runnable authModeRun=()->{};
    private void showAuth(boolean loginFirst){
        if(authLayer!=null)root.removeView(authLayer);
        authLayer=new FrameLayout(this);authLayer.setBackgroundColor(bg);authLayer.setClickable(true);root.addView(authLayer,new FrameLayout.LayoutParams(-1,-1));
        android.widget.ScrollView sv=new android.widget.ScrollView(this);sv.setFillViewport(true);authLayer.addView(sv,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout f=col();f.setGravity(Gravity.CENTER_HORIZONTAL);f.setPadding(dp(24),dp(40),dp(24),dp(28));sv.addView(f);
        android.widget.ImageView logo=new android.widget.ImageView(this);logo.setImageResource(logoRes());f.addView(logo,lp(76,76));
        TextView title=tx("Welcome to Kadamm",26,ink,true);title.setGravity(Gravity.CENTER);LinearLayout.LayoutParams tp=lp(-1,-2);tp.topMargin=dp(18);f.addView(title,tp);
        TextView sub=tx("Every walk and run, on your phone.",14,muted,false);sub.setGravity(Gravity.CENTER);f.addView(sub,lp(-1,-2));
        boolean[] login={LocalAccount.exists(this)};
        LinearLayout seg=row();seg.setPadding(dp(4),dp(4),dp(4),dp(4));seg.setBackground(shape(raised,14));LinearLayout.LayoutParams sp=lp(-1,52);sp.topMargin=dp(28);f.addView(seg,sp);
        TextView signTab=tx("Sign up",14,ink,true),logTab=tx("Log in",14,ink,true);
        for(TextView t:new TextView[]{signTab,logTab}){t.setGravity(Gravity.CENTER);t.setFocusable(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);seg.addView(t,p);}
        android.widget.EditText name=field("Your name",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        android.widget.EditText email=field("Email",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        android.widget.EditText pass=field("Password (8+ characters)",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        android.widget.EditText confirm=field("Confirm password",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        email.setText(LocalAccount.savedEmail(this));
        // Accounts already on this phone: tap one to fill its email.
        java.util.List<LocalAccount.Account> known=LocalAccount.all(this);LinearLayout knownBox=col();LinearLayout.LayoutParams kbp=lp(-1,-2);kbp.topMargin=dp(16);f.addView(knownBox,kbp);
        if(!known.isEmpty()){TextView kh=tx("ON THIS PHONE",10,muted,true);kh.setLetterSpacing(.08f);knownBox.addView(kh);
            for(LocalAccount.Account a:known){LinearLayout r=row();r.setPadding(dp(12),dp(10),dp(12),dp(10));GradientDrawable rg=shape(panel,16);rg.setStroke(dp(1),line);r.setBackground(ripple(rg));LinearLayout.LayoutParams rlp=lp(-1,-2);rlp.topMargin=dp(8);knownBox.addView(r,rlp);
                r.addView(avatarView(a,40),lp(40,40));LinearLayout t=col();LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,-2,1);tlp.leftMargin=dp(12);r.addView(t,tlp);t.addView(tx(a.name,15,ink,true));t.addView(tx(a.email,11,muted,false));r.addView(tx("›",22,muted,false));
                r.setFocusable(true);r.setContentDescription("Log in as "+a.name);r.setOnClickListener(v->{login[0]=true;email.setText(a.email);pass.requestFocus();authModeRun.run();});}}
        for(android.widget.EditText e:new android.widget.EditText[]{name,email}){LinearLayout.LayoutParams p=lp(-1,54);p.topMargin=dp(12);f.addView(e,p);}
        // Password with a show/hide toggle, then a strength meter while signing up.
        FrameLayout pw=new FrameLayout(this);LinearLayout.LayoutParams pwp=lp(-1,54);pwp.topMargin=dp(12);f.addView(pw,pwp);pw.addView(pass,new FrameLayout.LayoutParams(-1,-1));pass.setPadding(dp(16),0,dp(64),0);
        TextView eye=tx("Show",13,lime,true);eye.setGravity(Gravity.CENTER);eye.setBackground(rippleOn(Color.TRANSPARENT,12));eye.setFocusable(true);eye.setContentDescription("Show password");pw.addView(eye,new FrameLayout.LayoutParams(dp(60),-1,Gravity.END|Gravity.CENTER_VERTICAL));
        eye.setOnClickListener(v->{boolean shown=(pass.getInputType()&android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)!=0;int t=android.text.InputType.TYPE_CLASS_TEXT|(shown?android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);pass.setInputType(t);confirm.setInputType(t);pass.setSelection(pass.length());eye.setText(shown?"Show":"Hide");eye.setContentDescription(shown?"Show password":"Hide password");});
        LinearLayout meter=row();LinearLayout.LayoutParams mlp=lp(-1,-2);mlp.topMargin=dp(8);f.addView(meter,mlp);View[] segs=new View[4];for(int i=0;i<4;i++){segs[i]=new View(this);segs[i].setBackground(shape(raised,3));LinearLayout.LayoutParams sgp=new LinearLayout.LayoutParams(0,dp(5),1);if(i>0)sgp.leftMargin=dp(5);meter.addView(segs[i],sgp);}
        TextView meterText=tx("",11,muted,true);LinearLayout.LayoutParams mtp=lp(-2,-2);mtp.leftMargin=dp(10);meter.addView(meterText,mtp);
        pass.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence a,int b,int c2,int d){}public void onTextChanged(CharSequence a,int b,int c2,int d){}public void afterTextChanged(android.text.Editable e){int st=LocalAccount.strength(e.toString());int col=st<=1?critical:st==2?warning:positive;for(int i=0;i<4;i++)segs[i].setBackground(shape(i<st?col:raised,3));meterText.setText(e.length()==0?"":new String[]{"","Too short","Okay","Good","Strong"}[st]);meterText.setTextColor(col);}});
        {LinearLayout.LayoutParams p=lp(-1,54);p.topMargin=dp(12);f.addView(confirm,p);}
        TextView error=tx("",13,critical,false);LinearLayout.LayoutParams ep=lp(-1,-2);ep.topMargin=dp(10);f.addView(error,ep);
        Button go=button("Create account",lime,accentOn);go.setTextSize(16);LinearLayout.LayoutParams gp=lp(-1,54);gp.topMargin=dp(14);f.addView(go,gp);
        TextView note=tx("Accounts live in a private database on this phone. Passwords are stored only as a salted hash.",11,muted,false);note.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(14);f.addView(note,np);
        TextView guest=tx("Continue as guest",14,lime,true);guest.setGravity(Gravity.CENTER);guest.setFocusable(true);guest.setBackground(rippleOn(Color.TRANSPARENT,12));LinearLayout.LayoutParams gup=lp(-1,48);gup.topMargin=dp(6);f.addView(guest,gup);
        Runnable mode=()->{
            boolean l=login[0];name.setVisibility(l?View.GONE:View.VISIBLE);confirm.setVisibility(l?View.GONE:View.VISIBLE);meter.setVisibility(l?View.GONE:View.VISIBLE);knownBox.setVisibility(l&&!known.isEmpty()?View.VISIBLE:View.GONE);go.setText(l?"Log in":"Create account");error.setText("");
            signTab.setBackground(l?null:shape(panel,11));logTab.setBackground(l?shape(panel,11):null);signTab.setTextColor(l?muted:ink);logTab.setTextColor(l?ink:muted);
        };
        authModeRun=mode;signTab.setOnClickListener(v->{login[0]=false;mode.run();});logTab.setOnClickListener(v->{login[0]=true;mode.run();});mode.run();
        android.text.TextWatcher clear=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence a,int b,int c2,int d){}public void onTextChanged(CharSequence a,int b,int c2,int d){error.setText("");}public void afterTextChanged(android.text.Editable e){}};
        for(android.widget.EditText e:new android.widget.EditText[]{name,email,pass,confirm})e.addTextChangedListener(clear);
        go.setOnClickListener(v->{
            String err=login[0]?LocalAccount.logIn(this,email.getText().toString(),pass.getText().toString()):LocalAccount.signUp(this,name.getText().toString(),email.getText().toString(),pass.getText().toString(),confirm.getText().toString());
            if(err!=null){error.setText(err);if(!reducedMotion())error.animate().translationX(dp(6)).setDuration(60).withEndAction(()->error.animate().translationX(0).setDuration(60).start()).start();return;}
            pass.setText("");confirm.setText("");finishAuth();
        });
        guest.setOnClickListener(v->{LocalAccount.continueAsGuest(this);finishAuth();});
        authLayer.setAlpha(0f);authLayer.animate().alpha(1f).setDuration(reducedMotion()?0:200).start();
    }
    private android.widget.EditText field(String hint,int type){
        android.widget.EditText e=new android.widget.EditText(this);e.setHint(hint);e.setInputType(type);e.setSingleLine(true);e.setTextSize(15);e.setTextColor(ink);e.setHintTextColor(muted);
        e.setPadding(dp(16),0,dp(16),0);GradientDrawable g=shape(panel,14);g.setStroke(dp(1),line);e.setBackground(g);
        e.setOnFocusChangeListener((v,has)->{GradientDrawable d=shape(panel,14);d.setStroke(dp(has?2:1),has?lime:line);e.setBackground(d);});return e;
    }
    private void finishAuth(){
        android.view.inputmethod.InputMethodManager im=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(im!=null&&authLayer!=null)im.hideSoftInputFromWindow(authLayer.getWindowToken(),0);
        FrameLayout gone=authLayer;authLayer=null;page="Today";renderPageNow();
        playIntro(()->{if(gone!=null)root.removeView(gone);});
    }

    // ---- Opening animation: the logo appears, then a circle grows out of it and fills the screen ----
    private void playIntro(Runnable covered){
        if(reducedMotion()){if(covered!=null)covered.run();return;}
        // Start from exactly what the system splash showed (it follows the phone's dark mode), so the hand-off is invisible.
        boolean night=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        FrameLayout layer=new FrameLayout(this);layer.setBackgroundColor(night?Color.rgb(11,11,13):Color.rgb(247,247,248));layer.setClickable(true);root.addView(layer,new FrameLayout.LayoutParams(-1,-1));
        root.setBackgroundColor(night?Color.rgb(11,11,13):Color.rgb(247,247,248));
        View circle=new View(this);circle.setBackground(shape(Color.rgb(26,115,232),1000));int size=dp(112);layer.addView(circle,new FrameLayout.LayoutParams(size,size,Gravity.CENTER));
        android.widget.ImageView logo=new android.widget.ImageView(this);logo.setImageResource(night?R.drawable.ic_kadamm_logo_dark:R.drawable.ic_kadamm_logo);logo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        logo.setOutlineProvider(new android.view.ViewOutlineProvider(){@Override public void getOutline(View v,android.graphics.Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});logo.setClipToOutline(true);
        layer.addView(logo,new FrameLayout.LayoutParams(size,size,Gravity.CENTER));
        circle.setScaleX(.2f);circle.setScaleY(.2f);circle.setAlpha(0f);logo.setScaleX(.6f);logo.setScaleY(.6f);logo.setAlpha(0f);
        float cover=(float)Math.hypot(getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels)/size*1.05f;
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).withEndAction(()->{
            circle.setAlpha(1f);
            circle.animate().scaleX(cover).scaleY(cover).setStartDelay(120).setDuration(520).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).withEndAction(()->{
                if(covered!=null)covered.run();root.setBackgroundColor(bg);
                layer.animate().alpha(0f).setDuration(260).withEndAction(()->root.removeView(layer)).start();
            }).start();
            logo.animate().alpha(0f).scaleX(1.15f).scaleY(1.15f).setStartDelay(260).setDuration(260).start();
        }).start();
    }

    /** Tapping the live notification (also from the lock screen) brings up the live session screen. */
    /** From the session-summary notification: open that day in History, and share it when asked. */
    private void handleDayIntent(Intent intent){
        if(intent==null)return;if(intent.getBooleanExtra("offerUpdate",false)){intent.removeExtra("offerUpdate");handler.postDelayed(()->{AppUpdate.Release r=AppUpdate.known(this);if(r!=null)showUpdate(r);},700);}String day=intent.getStringExtra("openDay");if(day==null)return;
        openDays.add(day);summaryDay=day;page="DaySummary";String share=intent.getStringExtra("shareDay");intent.removeExtra("openDay");intent.removeExtra("shareDay");
        if(share!=null)handler.postDelayed(()->shareDay(share),600);
    }
    private void shareDay(String day){DaySummary d=DaySummary.of(this,history(),day);if(d.sessions==0)return;shareSheet("Share your day",d.text(dayTitle(day)+" in Kadamm")+"\nTracked with Kadamm",null,null);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleDayIntent(intent);if(intent!=null&&intent.getStringExtra("page")!=null){navigate(intent.getStringExtra("page"));if(intent.getBooleanExtra("startWalk",false)&&!prefs.getBoolean("active",false)){activityType="Walk";onRecord();}return;}if(intent!=null&&intent.getBooleanExtra("openLive",false)&&prefs.getBoolean("active",false))openLive();}
    // ---- Session summary: records, route attempts, charts ----
    private void renderSessionInsights(JSONObject o){
        double hours=o.optLong("durationMs",0)/3600000d,kmh=hours>0?o.optDouble("distanceKm",0)/hours:0;
        if(Insights.vehicle(o)){
            LinearLayout v=card();GradientDrawable vg=shape(blend(panel,warning,darkTheme?.16f:.08f),16);vg.setStroke(dp(1),blend(line,warning,.4f));v.setBackground(vg);
            v.addView(tx("Looks like a vehicle",14,warning,true));
            v.addView(tx(String.format(Locale.US,"Average %.0f km/h with %s steps. This was probably a ride, not a walk or run; GPS jumps at that speed leave gaps, shown as dashed lines on the map. You can delete it if you like.",kmh,o.optLong("steps",0)==0?"no":String.format(Locale.US,"only %,d",o.optLong("steps"))),12,ink,false));
        }
        java.util.List<Insights.Record> recs=Insights.records(history(),DaySteps.allDayTotals(this));java.util.List<String> mine=new java.util.ArrayList<>();
        for(Insights.Record r:recs)if(r.sessionId==o.optLong("id"))mine.add(r.label+" · "+r.value);
        if(!mine.isEmpty()){
            LinearLayout pr=card();GradientDrawable g=shape(blend(panel,flame(),darkTheme?.18f:.10f),16);g.setStroke(dp(1),blend(line,flame(),.4f));pr.setBackground(g);
            pr.addView(tx("PERSONAL RECORD",10,flame(),true));for(String m:mine)pr.addView(tx(m,16,ink,true));
            boolean fresh=!o.optBoolean("celebrated",false)&&System.currentTimeMillis()-o.optLong("endTimeMs",0)<7L*86400000L;
            if(fresh&&!reducedMotion()){
                try{o.put("celebrated",true);saveSession(historyPrefs,o);}catch(Exception ignored){}
                pr.setScaleX(.85f);pr.setScaleY(.85f);pr.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
                ConfettiView confetti=new ConfettiView(this);root.addView(confetti,new FrameLayout.LayoutParams(-1,-1));confetti.play();
            }
        }
        JSONArray rs=Insights.routes(this);JSONArray hist=history();
        for(int i=0;i<rs.length();i++){
            JSONObject route=rs.optJSONObject(i);if(route==null||!Insights.matches(route,o))continue;
            java.util.List<JSONObject> tries=Insights.attempts(route,hist);int index=0;long best=Long.MAX_VALUE;
            for(int k=0;k<tries.size();k++){long d=tries.get(k).optLong("durationMs");best=Math.min(best,d);if(tries.get(k).optLong("id")==o.optLong("id"))index=k+1;}
            long mineMs=o.optLong("durationMs");LinearLayout rc=card();
            rc.addView(tx("ROUTE · "+route.optString("name").toUpperCase(Locale.getDefault()),10,lime,true));
            rc.addView(tx(formatDuration(mineMs)+(mineMs==best?"  ·  best time":"  ·  "+(mineMs>best?"+":"")+formatDuration(Math.abs(mineMs-best))+" vs best"),17,mineMs==best?positive:ink,true));
            rc.addView(tx("Attempt "+index+" of "+tries.size()+" · best "+formatDuration(best),11,muted,false));
        }
        renderAnalysis(o);
    }
    // ---- Session analysis (8.2): overview, coach note, effort, zones, pace, splits, cadence, stride, heart rate, elevation, efforts ----
    /** A card with an uppercase title, an optional unit, and an ⓘ that explains how the numbers are worked out. */
    private LinearLayout metricCard(String title,String unit,String how){
        LinearLayout c=card();LinearLayout h=row();c.addView(h,lp(-1,-2));
        TextView t=tx(title,12,ink,true);t.setLetterSpacing(.06f);h.addView(t);
        if(unit!=null&&!unit.isEmpty())h.addView(tx("  "+unit,12,muted,false));
        h.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        if(how!=null){String guide=readingGuide(title);TextView i=tx("ⓘ",15,muted,false);i.setPadding(dp(10),0,0,0);i.setContentDescription("How "+title.toLowerCase(Locale.US)+" is worked out");i.setOnClickListener(v->dialog().setTitle(title.charAt(0)+title.substring(1).toLowerCase(Locale.US)).setMessage(how+(guide.isEmpty()?"":"\n\nHOW TO READ IT\n"+guide)).setPositiveButton("OK",null).show());h.addView(i);}
        return c;
    }
    /** What the units and number formats on a card mean, shown under its explanation. */
    private String readingGuide(String title){
        String t=title.toUpperCase(Locale.US);StringBuilder g=new StringBuilder();
        if(t.contains("PACE")||t.contains("PER KM")||t.contains("EFFORTS")||t.contains("MATCHED"))g.append("• 14'16\" /km means 14 minutes 16 seconds to walk one kilometre. A smaller number is faster.\n• \"—\" means too little distance to work out a pace.\n");
        if(t.contains("PER KM"))g.append("• spm is steps per minute; Elev is metres climbed (+) or descended (−) in that km; Time is the total time when you reached that km (h:mm:ss).\n");
        if(t.contains("CADENCE")||t.contains("MOVEMENT"))g.append("• spm = steps per minute. 100 spm is a moderate walk, 120+ brisk, 150–180 running.\n");
        if(t.contains("STRIDE"))g.append("• cm = length of one step in centimetres. Variation % shows how much it changes during the walk; lower is steadier.\n");
        if(t.contains("ZONES")||t.contains("INTENSITY"))g.append("• mm:ss is minutes and seconds spent in each zone; % is the share of your moving time.\n");
        if(t.contains("EFFORT")||t.contains("LOAD")||t.contains("FITNESS"))g.append("• Effort points: one point is about one minute of moderate walking; a 30-minute brisk walk scores about 45.\n• Form = fitness − fatigue. Positive means fresh, negative means you've been training hard.\n");
        if(t.contains("ELEVATION"))g.append("• m = metres. Gain +21 m means you climbed 21 m in total over the walk; Avg, Min and Max are heights above sea level.\n");
        if(t.contains("HEALTH"))g.append("• m/s = metres per second. 1.0 m/s = 3.6 km/h; 1.3 m/s = 4.7 km/h.\n• kcal = kilocalories (the \"calories\" on food labels).\n");
        if(t.contains("HEART"))g.append("• bpm = heartbeats per minute.\n");
        if(t.contains("WHEN YOU MOVE"))g.append("• Each square is one hour of one weekday; darker means more steps on average.\n");
        if(t.contains("CURVE"))g.append("• Each row is the fastest pace you've kept up for that long. Longer bars are faster.\n");
        if(g.length()==0)g.append("• Times are shown as h:mm:ss or mm:ss (hours, minutes, seconds).\n");
        return g.toString().trim();
    }
    /** Big value with a small unit after it. */
    private TextView valueUnit(String value,String unit,int size,int color){
        android.text.SpannableString s=new android.text.SpannableString(value+(unit.isEmpty()?"":" "+unit));
        s.setSpan(new android.text.style.RelativeSizeSpan(.5f),value.length(),s.length(),0);s.setSpan(new android.text.style.ForegroundColorSpan(muted),value.length(),s.length(),0);
        TextView t=tx("",size,color,true);t.setText(s);return t;
    }
    /** Cells of {label, value, unit} in rows of three. */
    private void statGrid(LinearLayout parent,String[][] cells,int topDp){
        LinearLayout r=null;
        for(int i=0;i<cells.length;i++){
            if(i%3==0){r=row();r.setGravity(Gravity.TOP);LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(i==0?topDp:14);parent.addView(r,rp);}
            LinearLayout cell=col();r.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
            cell.addView(valueUnit(cells[i][1],cells[i][2],19,ink));TextView l=tx(cells[i][0],10,muted,false);cell.addView(l);
        }
        if(r!=null)for(int k=cells.length%3;k>0&&k<3;k++)r.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
    }
    /** Pace as 14'16" (minutes and seconds per km); "—" when there is no meaningful pace (under 50 m, or slower than 30 min per km, i.e. 2 km/h). */
    private String paceText(double sec){return sec>0&&sec<1800?String.format(Locale.US,"%d'%02d\"",Math.round(sec)/60,Math.round(sec)%60):"—";}

    /**
     * Map colouring, chosen with a small segmented control on the map: the plain route, the route coloured by
     * speed with this session's records pinned (default), or by grade when the barometer recorded climbing.
     */
    private void mapModes(FrameLayout mapBox,RouteView rv,JSONObject o){
        SessionMetrics m=SessionMetrics.of(this,o);if(m.paceOverlay.isEmpty())return;JSONArray hist=history();
        java.util.List<Object[]> pins=new java.util.ArrayList<>();
        for(int i=SessionMetrics.EFFORTS.length-1;i>=0&&pins.size()<2;i--){int d=SessionMetrics.EFFORTS[i];double[] at=m.bestEnd.get(d);if(at==null)continue;int r=SessionMetrics.rank(this,hist,o,d);
            if(r>=1&&r<=3&&(pins.isEmpty()||Insights.metres((double)pins.get(0)[0],(double)pins.get(0)[1],at[0],at[1])>120))pins.add(new Object[]{at[0],at[1],(r==1?"Fastest ":r==2?"2nd fastest ":"3rd fastest ")+SessionMetrics.EFFORT_NAMES[i],r==1?Color.rgb(196,128,8):r==2?Color.rgb(96,108,124):Color.rgb(160,82,30)});}
        String[] names=m.gradeOverlay.isEmpty()?new String[]{"Route","Pace"}:new String[]{"Route","Pace","Grade"};
        LinearLayout seg=row();seg.setPadding(dp(3),dp(3),dp(3),dp(3));seg.setBackground(shape(darkTheme?Color.argb(225,22,22,26):Color.argb(235,255,255,255),16));
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,dp(34),Gravity.BOTTOM|Gravity.START);sp.setMargins(dp(10),0,0,dp(10));mapBox.addView(seg,sp);
        TextView key=tx("",10,ink,true);key.setPadding(dp(8),dp(4),dp(8),dp(4));key.setBackground(shape(darkTheme?Color.argb(225,22,22,26):Color.argb(235,255,255,255),10));
        FrameLayout.LayoutParams kp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);kp.setMargins(0,0,dp(10),dp(14));mapBox.addView(key,kp);
        TextView[] opts=new TextView[names.length];
        java.util.function.IntConsumer pick=k->{
            for(int i=0;i<opts.length;i++){boolean on=i==k;opts[i].setTextColor(on?accentOn:ink);opts[i].setBackground(on?shape(lime,13):null);}
            rv.setOverlay(k==1?m.paceOverlay:k==2?m.gradeOverlay:null);rv.setLabels(k==1?pins:null);
            android.text.SpannableStringBuilder b=new android.text.SpannableStringBuilder();
            if(k==1){append(b,"● ",MetricViews.ZONE_COLORS[2]);b.append("Slow   ");append(b,"● ",MetricViews.ZONE_COLORS[3]);b.append("Steady   ");append(b,"● ",MetricViews.ZONE_COLORS[4]);b.append("Fast");}
            else if(k==2){append(b,"● ",MetricViews.ZONE_COLORS[4]);b.append("Up   ");append(b,"● ",MetricViews.ZONE_COLORS[3]);b.append("Flat   ");append(b,"● ",MetricViews.ZONE_COLORS[1]);b.append("Down");}
            key.setText(b);key.setVisibility(k==0?View.GONE:View.VISIBLE);
        };
        for(int i=0;i<names.length;i++){int k=i;TextView t=tx(names[i],12,ink,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(12),0,dp(12),0);seg.addView(t,new LinearLayout.LayoutParams(-2,-1));opts[i]=t;t.setOnClickListener(v->pick.accept(k));}
        pick.accept(1);
    }
    private void append(android.text.SpannableStringBuilder b,String text,int color){int st=b.length();b.append(text);b.setSpan(new android.text.style.ForegroundColorSpan(color),st,b.length(),0);}

    private void renderSessionOverview(JSONObject o){
        SessionMetrics m=SessionMetrics.of(this,o);long ms=o.optLong("durationMs",0);double km=o.optDouble("distanceKm",0);
        LinearLayout c=card();
        GradientDrawable hg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{blend(panel,lime,darkTheme?.16f:.08f),panel});hg.setCornerRadius(dp(16));hg.setStroke(dp(1),blend(line,lime,.25f));c.setBackground(hg);
        LinearLayout top=row();top.setGravity(Gravity.BOTTOM);c.addView(top,lp(-1,-2));
        top.addView(valueUnit(String.format(Locale.US,"%.2f",km),"km",42,ink));top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        String kind=("indoor".equals(o.optString("activityMode"))?"INDOOR ":"OUTDOOR ")+o.optString("type","Walk").toUpperCase(Locale.US);
        TextView chip=tx(kind,10,m.run?Color.WHITE:accentOn,true);chip.setLetterSpacing(.08f);chip.setPadding(dp(10),dp(5),dp(10),dp(5));chip.setBackground(shape(m.run?SessionChartView.RUN:lime,12));LinearLayout.LayoutParams cp=lp(-2,-2);cp.bottomMargin=dp(12);top.addView(chip,cp);
        if(m.kmh>0&&!m.vehicle){
            // Where the average speed sits on a slow-to-fast scale for walking (2–7 km/h) or running (6–14 km/h).
            float f=(float)(m.run?(m.kmh-6)/8:(m.kmh-2)/5);LinearLayout.LayoutParams sp=lp(-1,40);sp.topMargin=dp(4);
            c.addView(new MetricViews.Scale(this,chartTheme(),f,"Slow","Fast"),sp);
        }
        String effort=m.zoneTotal()>=60?String.valueOf(Math.round(m.effort)):"—";
        statGrid(c,new String[][]{
            {"Workout time",formatDuration(ms),""},{"Avg pace",paceText(m.paceMoving),"/km"},{"Steps",o.optBoolean("stepsAvailable",false)?String.format(Locale.US,"%,d",o.optLong("steps")):"—",""},
            {"Active calories",String.valueOf(o.optInt("calories",0)),"kcal"},{"Avg cadence",m.cadenceAvg>0?String.valueOf(Math.round(m.cadenceAvg)):"—",m.cadenceAvg>0?"spm":""},{"Effort",effort,""},
            {"Moving time",formatDuration(m.movingS*1000),""},{"Avg speed",m.kmh>0?String.format(Locale.US,"%.1f",m.kmh):"—","km/h"},{"Elev. gain",m.gain>=0?String.format(Locale.US,"%.0f",m.gain):"—",m.gain>=0?"m":""}},10);
        if(o.optDouble("walkDistanceKm",0)>0&&o.optDouble("runDistanceKm",0)>0){
            long wms=o.optLong("walkDurationMs"),rms=o.optLong("runDurationMs");double wkm=o.optDouble("walkDistanceKm"),rkm=o.optDouble("runDistanceKm");
            statGrid(c,new String[][]{{"Walk",String.format(Locale.US,"%.2f",wkm),"km · "+formatDuration(wms)},{"Run",String.format(Locale.US,"%.2f",rkm),"km · "+formatDuration(rms)},{"Run pace",paceText(rkm>0?rms/1000d/rkm:-1),"/km"}},14);
        }
        if(o.optBoolean("distanceEstimated",false)){TextView estimate=tx("Indoor distance is estimated from your configured stride length.",11,warning,false);LinearLayout.LayoutParams ep=lp(-1,-2);ep.topMargin=dp(12);c.addView(estimate,ep);TextView correction=tx("Correct with treadmill distance",13,lime,true);c.addView(correction,lp(-1,42));correction.setOnClickListener(v->correctDistance(o));}
        TextView note=tx(o.optString("activityLabel","").isEmpty()?"Session saved on this device":o.optString("activityLabel")+" · saved on this device",11,muted,false);LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(12);c.addView(note,np);
        if(o.has("hcSteps")){TextView hc=tx(String.format(Locale.US,"Health Connect during this session: %,d steps",o.optLong("hcSteps"))+(o.has("hcActiveKcal")?String.format(Locale.US," · %d active kcal",o.optLong("hcActiveKcal")):""),11,positive,true);LinearLayout.LayoutParams hcp=lp(-1,-2);hcp.topMargin=dp(4);c.addView(hc,hcp);}
    }

    private void renderAnalysis(JSONObject o){
        SessionMetrics m=SessionMetrics.of(this,o);JSONArray hist=history();KadammTheme th=chartTheme();
        if(m.vehicle)return;
        if(m.movingS<120&&m.km<.1){
            LinearLayout n=card();n.addView(tx("Too short to analyse",14,ink,true));
            n.addView(tx("Zones, splits, cadence and the other charts appear once a session lasts two minutes or covers 100 m.",12,muted,false));return;
        }
        // Coach note
        double[] recent=SessionMetrics.recentEffort(this,hist,o);String summary=m.summary(recent);
        if(!summary.isEmpty()){
            LinearLayout ai=card();GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,violet,darkTheme?.22f:.12f),blend(panel,lime,darkTheme?.10f:.05f)});g.setCornerRadius(dp(16));g.setStroke(dp(1),blend(line,violet,.35f));ai.setBackground(g);
            TextView h=tx("✦  KADAMM INTELLIGENCE",10,violet,true);h.setLetterSpacing(.08f);ai.addView(h);
            TextView s=tx(summary,15,ink,false);s.setLineSpacing(dp(2),1f);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(8);ai.addView(s,sp);
        }
        // Effort and fitness
        if(m.zoneTotal()>=60){
            LinearLayout ef=metricCard("RELATIVE EFFORT","","Effort adds up the minutes you spent in each intensity zone, weighted by how hard the zone is (light 0.2, easy 0.5, moderate 1, brisk 1.5, vigorous 2.5 per minute). With a watch, heart-rate zones are used instead of movement.\n\nThe bands compare this session with the average of your sessions in the 21 days before it.\n\nFitness is a 42-day weighted average of your daily effort, the model behind Strava's Fitness & Freshness and TrainingPeaks (Banister). A session raises it a little; rest lets it drift down.");
            LinearLayout r=row();LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(8);ef.addView(r,rp);
            r.addView(valueUnit(String.valueOf(Math.round(m.effort)),"effort",34,ink),new LinearLayout.LayoutParams(0,-2,1));
            SessionMetrics.Fitness fit=SessionMetrics.fitness(this,hist);double gainF=SessionMetrics.fitnessGain(fit,o);
            LinearLayout fc=col();fc.setGravity(Gravity.END);r.addView(fc);
            fc.addView(tx(gainF>=.05?String.format(Locale.US,"Fitness +%.1f",gainF):"Fitness held",14,gainF>=.05?positive:muted,true));
            fc.addView(tx(String.format(Locale.US,"score %.0f · form %+.0f",fit.fitness,fit.form),11,muted,false));
            if(recent[1]>=3){
                LinearLayout.LayoutParams bp=lp(-1,126);bp.topMargin=dp(12);MetricViews.Bands bands=new MetricViews.Bands(this,th,m.effort/recent[0],violet);bands.setClipToOutline(true);bands.setBackground(shape(panel,12));ef.addView(bands,bp);
                TextView cap=tx(String.format(Locale.US,"Your 3-week average is %.0f, from %d sessions.",recent[0],(int)recent[1]),11,muted,false);LinearLayout.LayoutParams capp=lp(-1,-2);capp.topMargin=dp(8);ef.addView(cap,capp);
            }else{TextView cap=tx("After a few more sessions, each one is compared with your 3-week average here.",11,muted,false);LinearLayout.LayoutParams capp=lp(-1,-2);capp.topMargin=dp(8);ef.addView(cap,capp);}
        }
        // Intensity zones
        if(m.zoneTotal()>=60){
            boolean byHr=m.hrZoneS!=null;long[] z=byHr?m.hrZoneS:m.zoneS;long total=m.zoneTotal();
            LinearLayout zc=metricCard(byHr?"HEART RATE ZONES":"INTENSITY ZONES","",byHr?"Zones are shares of your maximum heart rate ("+m.maxHr+" bpm): light 50–60 %, easy 60–70 %, moderate 70–80 %, brisk 80–90 %, vigorous 90 % and up.":"Zones come from your steps per minute when Kadamm recorded them over time (8.2 and later), using the CADENCE-Adults research: about 100 steps a minute is moderate intensity (3 METs) and 130 is vigorous (6 METs). Older outdoor sessions use GPS speed instead, and older indoor sessions the session's average cadence.\n\nIntensity minutes follow the WHO guideline: moderate minutes plus twice the vigorous ones, aiming for 150 a week.");
            String[] ranges=byHr?new String[]{"50–60 %","60–70 %","70–80 %","80–90 %","90 %+"}:"cadence".equals(m.zoneSource)||"session average".equals(m.zoneSource)&&m.cadenceAvg>0?new String[]{"under 80 spm","80–99 spm","100–114 spm","115–129 spm","130+ spm"}:new String[]{"under 3.2 km/h","3.2–4 km/h","4–4.8 km/h","4.8–6.4 km/h","6.4+ km/h"};
            for(int i=4;i>=0;i--){
                LinearLayout zr=col();LinearLayout.LayoutParams zp=lp(-1,-2);zp.topMargin=dp(i==4?10:12);zc.addView(zr,zp);
                LinearLayout l1=row();zr.addView(l1,lp(-1,-2));l1.addView(tx(SessionMetrics.ZONES[i],14,ink,true));l1.addView(tx("  "+ranges[i],11,muted,false),new LinearLayout.LayoutParams(0,-2,1));
                l1.addView(tx(String.format(Locale.US,"%d%%   ",Math.round(z[i]*100d/total)),12,ink,true));l1.addView(tx(MetricViews.mmss(z[i]),12,muted,false));
                FrameLayout track=new FrameLayout(this);track.setBackground(shape(raised,4));LinearLayout.LayoutParams tp=lp(-1,7);tp.topMargin=dp(5);zr.addView(track,tp);
                View fill=new View(this);fill.setBackground(shape(MetricViews.ZONE_COLORS[i],4));float fr=(float)z[i]/total;track.addView(fill,new FrameLayout.LayoutParams(fr<=0?0:Math.max(dp(7),(int)((getResources().getDisplayMetrics().widthPixels-dp(64))*fr)),-1));
            }
            TextView im=tx(String.format(Locale.US,"%.0f intensity minutes toward the WHO's 150 a week · from %s",m.intensityMin,byHr?"heart rate":m.zoneSource),11,muted,false);LinearLayout.LayoutParams imp=lp(-1,-2);imp.topMargin=dp(12);zc.addView(im,imp);
        }
        // Pace zones around your threshold pace
        long[] pz=m.paceZones(SessionMetrics.thresholdPace(this,hist));
        if(pz!=null){
            long pt=0;for(long v:pz)pt+=v;
            if(pt>=60){
                double thr=SessionMetrics.thresholdPace(this,hist);
                LinearLayout pzc=metricCard("PACE ZONES","","Six zones around your threshold pace: the fastest pace you have held for 30 minutes in any session ("+paceText(thr)+" /km). It's the walking version of Strava's pace zones, which are based on your predicted 5K. Each 30-second stretch of moving is placed by its speed as a share of threshold speed: recovery under 78 %, endurance 78–88 %, tempo 88–94 %, threshold 94–101 %, VO₂ max 101–105 %, anaerobic above.");
                TextView sub=tx("Based on your threshold pace of "+paceText(thr)+" /km",11,muted,false);pzc.addView(sub);
                int[] blues={Color.rgb(181,212,244),Color.rgb(133,183,235),Color.rgb(55,138,221),Color.rgb(24,95,165),Color.rgb(12,68,124),Color.rgb(4,44,83)};
                double tk=3600/thr;long mx=0;for(long v:pz)mx=Math.max(mx,v);
                for(int i=5;i>=0;i--){
                    LinearLayout r=row();r.setPadding(0,dp(6),0,dp(2));pzc.addView(r,lp(-1,-2));
                    r.addView(tx("Z"+(i+1),12,ink,true),lp(30,-2));
                    FrameLayout bar=new FrameLayout(this);r.addView(bar,new LinearLayout.LayoutParams(0,dp(20),1));
                    View fill=new View(this);fill.setBackground(shape(blues[i],6));final float f=mx>0?(float)pz[i]/mx:0;
                    bar.addView(fill,new FrameLayout.LayoutParams(0,-1));bar.post(()->{FrameLayout.LayoutParams fl=(FrameLayout.LayoutParams)fill.getLayoutParams();fl.width=f>0?Math.max(dp(5),(int)(bar.getWidth()*f)):0;fill.setLayoutParams(fl);});
                    TextView pc=tx(Math.round(pz[i]*100d/pt)+"%",12,ink,true);pc.setGravity(Gravity.END);r.addView(pc,lp(44,-2));
                    double lo=i==0?0:SessionMetrics.PACE_FROM[i-1],hi=i==5?0:SessionMetrics.PACE_FROM[i];
                    String range=i==0?"> "+paceText(3600/(tk*hi)):i==5?"< "+paceText(3600/(tk*lo)):paceText(3600/(tk*lo))+"–"+paceText(3600/(tk*hi));
                    TextView rg=tx(range,10,muted,false);rg.setGravity(Gravity.END);r.addView(rg,lp(96,-2));
                }
                TextView names=tx("Z1 recovery · Z2 endurance · Z3 tempo · Z4 threshold · Z5 VO₂ max · Z6 anaerobic",10,muted,false);LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(8);pzc.addView(names,np);
            }
        }
        // Pace
        if(m.pace.size()>=2){
            double best=Double.MAX_VALUE;for(double[] q:m.pace)best=Math.min(best,q[1]);
            LinearLayout pc=metricCard("PACE","(min/km)","Pace is time per kilometre, each point averaged over the last 150 m. Average pace uses moving time, so stops don't count against you.\n\nGrade-adjusted pace (GAP) is the flat-ground pace with the same effort, using Minetti's energy cost of moving uphill and downhill. It needs barometer altitude, recorded from 8.2.\n\nConsistency is the spread of your pace over 250 m stretches: under 6 % is very steady."+(m.estimatedDistance?"\n\nIndoors, distance is your steps times your stride length, so pace is an estimate.":""));
            String[][] cells={{"Avg",paceText(m.paceMoving),"/km"},{"Fastest",paceText(best),"/km"},{m.gapPace>0?"Grade-adjusted":"Elapsed avg",paceText(m.gapPace>0?m.gapPace:m.paceElapsed),"/km"}};statGrid(pc,cells,10);
            if(m.consistency>=0){TextView cs=tx((m.consistency<6?"Very steady pace":m.consistency<12?"Steady pace":"Changing pace")+String.format(Locale.US," · %.0f %% variation",m.consistency),12,m.consistency<12?positive:warning,true);LinearLayout.LayoutParams csp=lp(-1,-2);csp.topMargin=dp(10);pc.addView(cs,csp);}
            LinearLayout.LayoutParams pcp=lp(-1,190);pcp.topMargin=dp(8);pc.addView(new SessionChartView(this,th,m.pace,true),pcp);
        }
        if(m.gap.size()>=10){
            LinearLayout gc=metricCard("GRADE ADJUSTED PACE","(min/km)","The pace you'd have held on flat ground with the same effort, from the barometer's gradient and Minetti's energy cost of walking or running on slopes. Climbs make your real pace slower than your GAP; descents make it faster.");
            statGrid(gc,new String[][]{{"Avg GAP",paceText(m.gapPace),"/km"},{"Avg pace",paceText(m.paceMoving),"/km"},{"Hills cost you",m.gapPace>0&&m.paceMoving>0?String.format(Locale.US,"%+d",Math.round(m.paceMoving-m.gapPace)):"—","s/km"}},10);
            LinearLayout.LayoutParams p=lp(-1,170);p.topMargin=dp(10);gc.addView(new MetricViews.Pair(this,th,thinPace(m.pace,300),thinPace(m.gap,300),lime),p);
            LinearLayout lg=row();LinearLayout.LayoutParams lgp=lp(-1,-2);lgp.topMargin=dp(6);gc.addView(lg,lgp);
            android.text.SpannableStringBuilder b=new android.text.SpannableStringBuilder();append(b,"■ ",lime);b.append("Your pace    ");append(b,"■ ",MetricViews.alpha(lime,110));b.append("Pace on flat ground (GAP)");lg.addView(tx("",11,muted,false));((TextView)lg.getChildAt(0)).setText(b);
        }
        // Splits
        if(m.splits.size()>=1){
            LinearLayout sc=metricCard("DETAILS PER KM",m.splits.size()+(m.splits.size()==1?" split":" splits"),"Each row is one kilometre: its pace, steps per minute during it, the climb or drop in metres, and the total time when you reached it. The bar is longer for faster kilometres. The fastest full kilometre is marked ▶.");
            LinearLayout hd=row();LinearLayout.LayoutParams hp=lp(-1,-2);hp.topMargin=dp(10);sc.addView(hd,hp);
            boolean anyCad=false;for(SessionMetrics.Split s:m.splits)if(s.cadence>0)anyCad=true;
            String[] heads={"km","Pace","","spm","Elev","Time"};float[] wts={.7f,1.3f,1.8f,anyCad?.9f:0f,.9f,1.3f};
            for(int i=0;i<heads.length;i++){if(wts[i]==0)continue;TextView t=tx(heads[i],10,muted,false);if(i>=3)t.setGravity(Gravity.END);hd.addView(t,new LinearLayout.LayoutParams(0,-2,wts[i]));}
            double fastest=Double.MAX_VALUE,slowest=0;for(SessionMetrics.Split s:m.splits){fastest=Math.min(fastest,s.pace);slowest=Math.max(slowest,s.pace);}
            long cum=0;
            for(SessionMetrics.Split s:m.splits){
                cum+=s.sec;LinearLayout r=row();r.setPadding(0,dp(7),0,dp(7));if(s.fastest)r.setBackground(shape(blend(panel,positive,darkTheme?.14f:.08f),8));sc.addView(r,lp(-1,-2));
                r.addView(tx((s.fastest?"▶ ":"")+(s.fraction<1?String.format(Locale.US,"%.2f",s.index-1+s.fraction):String.valueOf(s.index)),13,s.fastest?positive:ink,true),new LinearLayout.LayoutParams(0,-2,wts[0]));
                r.addView(tx(paceText(s.pace),13,ink,true),new LinearLayout.LayoutParams(0,-2,wts[1]));
                FrameLayout bar=new FrameLayout(this);bar.setBackground(shape(raised,4));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(8),wts[2]);bp.rightMargin=dp(6);r.addView(bar,bp);
                double f=slowest>fastest?.35+.65*(slowest-s.pace)/(slowest-fastest):1;View fill=new View(this);fill.setBackground(shape(s.fastest?positive:m.run?SessionChartView.RUN:lime,4));
                final double ff=f;bar.addView(fill,new FrameLayout.LayoutParams(0,-1));bar.post(()->{FrameLayout.LayoutParams fl=(FrameLayout.LayoutParams)fill.getLayoutParams();fl.width=(int)(bar.getWidth()*ff);fill.setLayoutParams(fl);});
                TextView cad=tx(s.cadence>0?String.valueOf(Math.round(s.cadence)):"—",13,ink,false);cad.setGravity(Gravity.END);if(anyCad)r.addView(cad,new LinearLayout.LayoutParams(0,-2,wts[3]));
                TextView el=tx(Double.isNaN(s.elev)?"—":Math.round(s.elev)==0?"0":String.format(Locale.US,"%+d",Math.round(s.elev)),13,ink,false);el.setGravity(Gravity.END);r.addView(el,new LinearLayout.LayoutParams(0,-2,wts[4]));
                TextView tm=tx(MetricViews.mmss(cum),13,muted,false);tm.setGravity(Gravity.END);r.addView(tm,new LinearLayout.LayoutParams(0,-2,wts[5]));
            }
            if(m.firstHalfPace>0){TextView hs=tx(String.format(Locale.US,"First half %s · second half %s /km",paceText(m.firstHalfPace),paceText(m.secondHalfPace)),11,muted,false);LinearLayout.LayoutParams hsp=lp(-1,-2);hsp.topMargin=dp(8);sc.addView(hs,hsp);}
        }
        // Cadence
        if(m.cadenceAvg>0){
            LinearLayout cc=metricCard("CADENCE","(spm)","Cadence is steps per minute, from your phone's step counter, over 30-second windows. Dot colours are the intensity zones: green from 100 spm (moderate), amber from 115 (brisk), red from 130 (vigorous).\n\nThe average counts only the time you were moving. Typical walking is 100–120 spm; running 150–180.");
            String spk=m.km>0&&m.stepsOk?String.format(Locale.US,"%,d",Math.round(m.steps/m.km)):"—";
            statGrid(cc,m.cadenceMax>0?new String[][]{{"Avg",String.valueOf(Math.round(m.cadenceAvg)),"spm"},{"Max",String.valueOf(Math.round(m.cadenceMax)),"spm"},{"Steps per km",spk,""}}:new String[][]{{"Avg",String.valueOf(Math.round(m.cadenceAvg)),"spm"},{"Steps per km",spk,""},{"Steps",String.format(Locale.US,"%,d",m.steps),""}},10);
            if(m.cadence.size()>=6){LinearLayout.LayoutParams p=lp(-1,170);p.topMargin=dp(10);cc.addView(new MetricViews.Series(this,th,m.cadence,true,lime,new double[]{80,100,115,130},m.cadenceAvg,v->String.valueOf(Math.round(v)),MetricViews::mmss),p);}
            if(m.cadence.size()>=6&&"Health Connect".equals(m.stepSource)){TextView n=tx("Minute by minute from Samsung Health, through Health Connect",10,muted,false);cc.addView(n);}
            else if(m.cadence.size()<6){TextView n=tx("A cadence chart over time appears once Health Connect has minute-level steps for this session, and for every session recorded with Kadamm 8.2 or later.",11,muted,false);LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(8);cc.addView(n,p);}
        }
        // Stride
        if(m.strideMeasured){
            LinearLayout st=metricCard("STRIDE","(cm)","Stride is the distance of one step: GPS distance divided by steps, over 30-second windows. It is measured only outdoors; indoors Kadamm uses the stride length you set. A longer stride at the same cadence means a faster pace.");
            int setting=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("strideLengthCm",72);
            statGrid(st,m.strideMaxCm>0?new String[][]{{"Avg",String.valueOf(Math.round(m.strideAvgCm)),"cm"},{"Max",String.valueOf(Math.round(m.strideMaxCm)),"cm"},{"Your setting",String.valueOf(setting),"cm"}}:new String[][]{{"Avg measured",String.valueOf(Math.round(m.strideAvgCm)),"cm"},{"Your setting",String.valueOf(setting),"cm"},{"Difference",String.format(Locale.US,"%+d",Math.round(m.strideAvgCm)-setting),"cm"}},10);
            if(m.strideCv>=0){TextView sv=tx(String.format(Locale.US,"Gait steadiness: %s · stride varies %.0f %%",m.strideCv<8?"very steady":m.strideCv<15?"steady":"uneven",m.strideCv),12,m.strideCv<15?positive:warning,true);LinearLayout.LayoutParams svp=lp(-1,-2);svp.topMargin=dp(10);st.addView(sv,svp);}
            if(m.stride.size()>=6){LinearLayout.LayoutParams p=lp(-1,160);p.topMargin=dp(10);st.addView(new MetricViews.Series(this,th,m.stride,true,teal(),null,m.strideAvgCm,v->String.valueOf(Math.round(v)),MetricViews::mmss),p);}
        }
        // Heart rate (from a watch, through Health Connect)
        if(!m.hr.isEmpty()){
            LinearLayout hc=metricCard("HEART RATE","(bpm)","Heart rate recorded by your watch during this session, read from Health Connect"+(m.hrSource!=null?" ("+m.hrSource+")":"")+" in 5-second averages.");
            statGrid(hc,new String[][]{{"Avg",String.valueOf(m.hrAvg),"bpm"},{"Max",String.valueOf(m.hrMax),"bpm"},{"Max HR used",m.maxHr>0?String.valueOf(m.maxHr):"—",m.maxHr>0?"bpm":""}},10);
            LinearLayout.LayoutParams p=lp(-1,170);p.topMargin=dp(10);hc.addView(new MetricViews.Series(this,th,m.hr,false,critical,null,m.hrAvg,v->String.valueOf(Math.round(v)),MetricViews::mmss),p);
            if(m.maxHr<=0){TextView n=tx("Add your age in Goals and targets to see heart-rate zones.",12,lime,true);n.setOnClickListener(v->navigate("Goals"));LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(8);hc.addView(n,np);}
        }else if(!o.optBoolean("hrChecked",false)||System.currentTimeMillis()-o.optLong("endTimeMs",0)<30L*86400000L){
            LinearLayout hc=card();LinearLayout hr=row();hc.addView(hr,lp(-1,-2));
            android.widget.ImageView heart=icon(R.drawable.ic_heart,critical,9);heart.setBackground(shape(blend(panel,critical,darkTheme?.18f:.10f),14));hr.addView(heart,lp(40,40));
            LinearLayout tcol=col();LinearLayout.LayoutParams tcp=new LinearLayout.LayoutParams(0,-2,1);tcp.leftMargin=dp(12);hr.addView(tcol,tcp);
            tcol.addView(tx("Heart rate",15,ink,true));tcol.addView(tx("No watch data for this session",11,muted,false));
            TextView why=tx("Wear a watch that syncs to Samsung Health or Health Connect (Galaxy Watch, Amazfit through Zepp, Fitbit and others) and Kadamm adds heart rate, heart-rate zones and a more exact effort to each session automatically.",12,muted,false);LinearLayout.LayoutParams wp=lp(-1,-2);wp.topMargin=dp(10);hc.addView(why,wp);
            TextView go=tx("Allow heart rate in Health Connect  ›",13,lime,true);go.setPadding(0,dp(10),0,dp(2));go.setOnClickListener(v->startActivity(new Intent(this,HealthConnectActivity.class)));hc.addView(go);
        }
        // Elevation
        if(m.elevation.size()>=10){
            LinearLayout ec=metricCard("ELEVATION","(m)",m.barometer?"From your phone's barometer, which senses changes of about a metre. Gradient: uphill above +2 %, downhill below −2 %, over 30 m stretches.":"From GPS altitude, which can drift by several metres, so it is smoothed and only bigger changes count. Sessions recorded with Kadamm 8.2 or later use the barometer instead, and also show the gradient.");
            double sum=0;for(double[] q:m.elevation)sum+=q[1];
            statGrid(ec,new String[][]{{"Gain",String.format(Locale.US,"+%.0f",m.gain),"m"},{"Loss",m.loss>=0?String.format(Locale.US,"−%.0f",m.loss):"—","m"},{"Avg",String.format(Locale.US,"%.0f",sum/m.elevation.size()),"m"},{"Min",String.format(Locale.US,"%.0f",m.altMin),"m"},{"Max",String.format(Locale.US,"%.0f",m.altMax),"m"},{"Range",String.format(Locale.US,"%.0f",m.altMax-m.altMin),"m"}},10);
            LinearLayout.LayoutParams ep=lp(-1,160);ep.topMargin=dp(10);
            ec.addView(new MetricViews.Series(this,th,m.elevation.size()>400?thin(m.elevation,400):withRunFlag(m.elevation),false,MetricViews.ZONE_COLORS[3],null,sum/m.elevation.size(),v->String.format(Locale.US,"%.0f",v),v->String.format(Locale.US,"%.1f km",v)),ep);
            if(!m.barometer){TextView n=tx((m.seaLevel?"GPS height above sea level":"GPS altitude")+" · less precise than the barometer",10,muted,false);ec.addView(n);}
            else if(m.seaLevel){TextView n=tx("Above sea level · barometer anchored to GPS",10,muted,false);ec.addView(n);}
            if(m.gradeS!=null&&m.gradeS[0]+m.gradeS[1]+m.gradeS[2]>=60){
                TextView gt=tx("TERRAIN",11,ink,true);gt.setLetterSpacing(.06f);LinearLayout.LayoutParams gtp=lp(-1,-2);gtp.topMargin=dp(16);ec.addView(gt,gtp);
                LinearLayout.LayoutParams tp=lp(-1,140);tp.topMargin=dp(6);ec.addView(new MetricViews.Terrain(this,th,m.gradeS[0],m.gradeS[1],m.gradeS[2]),tp);
            }
        }
        // Best efforts with all-time ranks
        if(!m.best.isEmpty()){
            LinearLayout be=metricCard("BEST EFFORTS","","Your fastest time for each distance anywhere inside this session, from the GPS track, compared with every session you've recorded. Indoor sessions don't count because their distance is estimated.");
            for(int i=0;i<SessionMetrics.EFFORTS.length;i++){
                Double t=m.best.get(SessionMetrics.EFFORTS[i]);if(t==null)continue;int rank=SessionMetrics.rank(this,hist,o,SessionMetrics.EFFORTS[i]);
                LinearLayout r=row();r.setPadding(0,dp(10),0,dp(4));be.addView(r,lp(-1,-2));
                int mc=rank==1?Color.rgb(255,196,0):rank==2?Color.rgb(176,186,198):rank==3?Color.rgb(205,127,50):raised;
                TextView medal=tx(rank>=1&&rank<=3?String.valueOf(rank):"",13,rank<=3?Color.rgb(20,20,22):muted,true);medal.setGravity(Gravity.CENTER);GradientDrawable md=new GradientDrawable();md.setShape(GradientDrawable.OVAL);md.setColor(mc);if(rank<=3)md.setStroke(dp(2),blend(mc,Color.WHITE,.4f));medal.setBackground(md);r.addView(medal,lp(32,32));
                LinearLayout tc=col();LinearLayout.LayoutParams tcp=new LinearLayout.LayoutParams(0,-2,1);tcp.leftMargin=dp(12);r.addView(tc,tcp);
                tc.addView(tx(SessionMetrics.EFFORT_NAMES[i],15,ink,true));
                tc.addView(tx(String.format(Locale.US,"%s  ·  %s /km",MetricViews.mmss(t),paceText(t/(SessionMetrics.EFFORTS[i]/1000d))),12,muted,false));
                r.addView(tx(rank==1?"Your fastest ever":rank==2?"2nd fastest ever":rank==3?"3rd fastest ever":"#"+rank+" all time",11,rank<=3?flame():muted,true));
            }
        }
        // Similar sessions
        java.util.List<double[]> match=SessionMetrics.matched(this,hist,o);
        if(match.size()>=3){
            double now=match.get(match.size()-1)[1],sum=0;for(int i=0;i<match.size()-1;i++)sum+=match.get(i)[1];double avg=sum/(match.size()-1);
            LinearLayout mc=metricCard("MATCHED SESSIONS",match.size()+" similar","Earlier "+("indoor".equals(o.optString("activityMode"))?"indoor":"outdoor")+" sessions within 20 % of this distance, compared by average moving pace. Higher on the chart is faster.");
            boolean faster=now<avg;TextView v=tx(faster?"Trending faster":"Slower than usual",17,faster?positive:warning,true);LinearLayout.LayoutParams vp=lp(-1,-2);vp.topMargin=dp(8);mc.addView(v,vp);
            mc.addView(tx(String.format(Locale.US,"This one %s /km · their average %s /km",paceText(now),paceText(avg)),12,muted,false));
            java.util.List<double[]> pts=new java.util.ArrayList<>();for(int i=0;i<match.size();i++)pts.add(new double[]{i,-match.get(i)[1],0});
            final java.util.List<double[]> fm=match;LinearLayout.LayoutParams p=lp(-1,130);p.topMargin=dp(10);
            mc.addView(new MetricViews.Series(this,th,pts,false,lime,null,-1,q->paceText(-q),q->new java.text.SimpleDateFormat("d MMM",Locale.getDefault()).format(new java.util.Date((long)fm.get((int)Math.round(Math.max(0,Math.min(fm.size()-1,q))))[0]))),p);
        }
        // Health markers
        if(!m.run&&m.walkingSpeed()>0&&!m.estimatedDistance){
            LinearLayout hm=metricCard("HEALTH MARKERS","","Walking speed (metres per second, over moving time) is called the sixth vital sign: in a study of 34,000 older adults, people who walked 1.0 m/s or faster lived longer than expected for their age (Studenski et al., JAMA 2011). 1.3 m/s and up is a brisk walk.\n\nThe moving share is moving time over the whole session; a stop is 20 seconds or more without steps or movement.");
            double v=m.walkingSpeed();
            LinearLayout r=row();LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(8);hm.addView(r,rp);
            r.addView(valueUnit(String.format(Locale.US,"%.2f",v),"m/s",28,ink),new LinearLayout.LayoutParams(0,-2,1));
            r.addView(tx(m.walkingSpeedVerdict(),12,v>=1.0?positive:warning,true));
            LinearLayout.LayoutParams sp=lp(-1,40);sp.topMargin=dp(6);hm.addView(new MetricViews.Scale(this,th,(float)((v-.6)/1.0),"0.6 m/s","1.6 m/s",true),sp);
            statGrid(hm,new String[][]{{"Moving",m.elapsedS>0?Math.round(m.movingS*100d/m.elapsedS)+"":"—","%"},{"Stops",String.valueOf(m.stops),""},{"Calories per km",m.km>0?String.valueOf(Math.round(o.optInt("calories",0)/m.km)):"—","kcal"}},12);
        }
        // Running fitness estimate
        if(m.vdot>0){
            LinearLayout vc=metricCard("RUNNING FITNESS","","An estimate of VO₂max from your best running effort of 1.5 km or more in this session, using Jack Daniels' VDOT formula. Race predictions use Riegel's formula (time grows with distance to the power 1.06). Estimates, not lab measurements.");
            double bestD=0,bestT=0;for(java.util.Map.Entry<Integer,Double> e:m.best.entrySet())if(e.getKey()>=1500&&e.getKey()>bestD){bestD=e.getKey();bestT=e.getValue();}
            statGrid(vc,new String[][]{{"Est. VO₂max",String.format(Locale.US,"%.0f",m.vdot),"ml/kg/min"},{"5 km",MetricViews.mmss(bestT*Math.pow(5000/bestD,1.06)),""},{"10 km",MetricViews.mmss(bestT*Math.pow(10000/bestD,1.06)),""}},10);
        }
    }
    private static java.util.List<double[]> thinPace(java.util.List<double[]> d,int n){if(d.size()<=n)return d;java.util.List<double[]> out=new java.util.ArrayList<>();double step=d.size()/(double)n;for(double i=0;i<d.size();i+=step)out.add(d.get((int)i));return out;}
    private static java.util.List<double[]> withRunFlag(java.util.List<double[]> d){java.util.List<double[]> out=new java.util.ArrayList<>();for(double[] q:d)out.add(new double[]{q[0],q[1],0});return out;}
    private static java.util.List<double[]> thin(java.util.List<double[]> d,int n){java.util.List<double[]> out=new java.util.ArrayList<>();double step=d.size()/(double)n;for(double i=0;i<d.size();i+=step){double[] q=d.get((int)i);out.add(new double[]{q[0],q[1],0});}return out;}
    private void chartStat(LinearLayout parent,String label,String value){LinearLayout c=col();parent.addView(c,new LinearLayout.LayoutParams(0,-2,1));c.addView(tx(label,9,muted,true));c.addView(tx(value,15,ink,true));}
    /**
     * Today's plan, like a coach's daily target: 60 % your step goal and 40 % brisk minutes (30 a day, the WHO
     * weekly 150 over five days). Each part counts up to 150 %, so a big day shows over 100. The week's
     * completion averages Monday to Sunday, with days to come as zero.
     */
    private double planFor(String day,Map<String,Long> totals,JSONObject md,JSONArray hist){
        Long st=totals.get(day);double steps=st==null?0:st;JSONObject d=md.optJSONObject(day);
        double brisk=d!=null&&d.has("brisk")?d.optDouble("brisk"):-1;
        // Without Health Connect minutes, fall back to session zones once they are analysed (never on a cold start).
        if(brisk<0){brisk=0;if(SessionMetrics.ready())for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o!=null&&day.equals(o.optString("day"))&&!Insights.vehicle(o)){SessionMetrics m=SessionMetrics.of(this,o);brisk+=(m.zoneS[2]+m.zoneS[3]+m.zoneS[4])/60d;}}}
        return 100*(.6*Math.min(1.5,steps/Math.max(1,dailyGoal()))+.4*Math.min(1.5,brisk/Math.max(1,briskGoal())));
    }
    private void renderPlan(JSONArray hist){
        Map<String,Long> totals=dayTotals!=null?dayTotals:DaySteps.allDayTotals(this);JSONObject md=DaySteps.minuteDays(this);String today=todayKey();
        double plan=planFor(today,totals,md,hist);
        Calendar mon=Calendar.getInstance();int back=(mon.get(Calendar.DAY_OF_WEEK)+5)%7;mon.add(Calendar.DAY_OF_YEAR,-back);
        double[] week=new double[7];double sum=0;for(int i=0;i<7;i++){Calendar d=(Calendar)mon.clone();d.add(Calendar.DAY_OF_YEAR,i);String k=DaySteps.key(d.getTimeInMillis());week[i]=k.compareTo(today)>0?-1:Math.min(100,planFor(k,totals,md,hist));if(week[i]>0)sum+=week[i];}
        LinearLayout c=card();
        LinearLayout hd=row();c.addView(hd,lp(-1,-2));TextView t=tx("TODAY'S PLAN",10,muted,true);t.setLetterSpacing(.08f);hd.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        TextView info=tx("ⓘ",15,muted,false);info.setOnClickListener(v->dialog().setTitle("Today's plan").setMessage("Your plan is 60 % your daily step goal and 40 % brisk minutes, 30 a day (minutes at 100 or more steps a minute, which is moderate intensity). Each part counts up to 150 %, so a big day goes past 100 %.\n\nThis week's completion is the average of Monday to Sunday, so it grows through the week.").setPositiveButton("OK",null).show());hd.addView(info);
        LinearLayout big=row();big.setGravity(Gravity.BOTTOM);LinearLayout.LayoutParams bgp=lp(-1,-2);bgp.topMargin=dp(4);c.addView(big,bgp);
        big.addView(valueUnit(String.valueOf(Math.round(plan)),"%",36,plan>=100?positive:ink));
        big.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        JSONObject d=md.optJSONObject(today);
        big.addView(tx(plan>=100?"Plan complete":d!=null&&d.has("brisk")?d.optInt("brisk")+" of "+briskGoal()+" brisk min":"Brisk minutes from sessions",12,plan>=100?positive:muted,true));
        LinearLayout.LayoutParams pp=lp(-1,30);pp.topMargin=dp(6);c.addView(new MetricViews.PlanBar(this,chartTheme(),(float)(plan/100),flame()),pp);
        LinearLayout wk=row();LinearLayout.LayoutParams wkp=lp(-1,-2);wkp.topMargin=dp(14);c.addView(wk,wkp);
        wk.addView(tx("This week",14,ink,true),new LinearLayout.LayoutParams(0,-2,1));wk.addView(valueUnit(String.valueOf(Math.round(sum/7)),"%",18,ink));
        LinearLayout.LayoutParams wp=lp(-1,96);wp.topMargin=dp(6);c.addView(new MetricViews.WeekBars(this,chartTheme(),week,new String[]{"M","T","W","T","F","S","S"},back,MetricViews.ZONE_COLORS[2]),wp);
    }
    /**
     * Movement quality today, from minute-level steps: peak-30 cadence (your 30 best minutes, a marker linked with
     * lower mortality in large studies), brisk minutes, the longest stretch without moving, and steps by hour.
     */
    /**
     * Home: sitting breaks today. Each time you got up after 30+ minutes without walking is a break; sits of an
     * hour or more are flagged, since breaking up sitting every 30–60 minutes is better for blood sugar and the heart.
     */
    private void renderSittingBreaks(){
        java.util.List<int[]> sits=DaySteps.sitsToday(this);Calendar now=Calendar.getInstance();int nowMin=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);
        long still=DaySteps.stillMinutes(this);if(sits.isEmpty()&&still<0)return;
        boolean resting=nowMin<420||nowMin>=1380,sittingNow=!resting&&still>=20;
        int longSits=0,longest=0,longIdx=-1;for(int i=0;i<sits.size();i++){int[] x=sits.get(i);if(x[1]>=60)longSits++;if(x[1]>longest){longest=x[1];longIdx=i;}}
        int breaks=sits.size();int stillNow=(int)Math.max(0,still);if(sittingNow&&stillNow>longest){longest=stillNow;longIdx=-2;}
        LinearLayout c=metricCard("SITTING BREAKS","today","A sit is 30 minutes or more without walking (fewer than 10 steps), between 7 am and 11 pm. A break is when you get up and walk after one. Long unbroken sitting raises blood sugar and heart risk even in people who exercise; standing up and walking for 2 minutes every 30–60 minutes helps.\n\n"+(DaySteps.minuteDays(this).optJSONObject(todayKey())!=null?"Times come from Samsung Health's minute steps through Health Connect.":"Times come from Kadamm's own step readings, so start and end can be a few minutes off."));
        // Right now
        String head=sittingNow?"Sitting for "+mins(stillNow):still>=0&&!resting?(still<2?"Moving now":"Last moved "+mins(stillNow)+" ago"):"Resting hours";
        int headCol=sittingNow?(stillNow>=60?critical:warning):positive;
        TextView h=tx(head,22,headCol,true);LinearLayout.LayoutParams hp=lp(-1,-2);hp.topMargin=dp(4);c.addView(h,hp);
        c.addView(tx(sittingNow?(stillNow>=60?"Over an hour: stand up and walk for 2 minutes now.":"Try to get up before it reaches an hour."):"Getting up every 30–60 minutes is the healthy rhythm.",12,muted,false));
        // Summary sentence
        String sum=breaks==0?"No long sits finished yet today.":"Today you got up "+breaks+(breaks==1?" time":" times")+" after sitting a while."+(longest>0?" Your longest sit was "+mins(longest)+(longIdx>=0?" ("+clock12(sits.get(longIdx)[0])+"–"+clock12(sits.get(longIdx)[0]+sits.get(longIdx)[1])+")":" (still going)")+".":"")+(longSits==0?" None went past an hour. Good.":" "+longSits+(longSits==1?" went":" went")+" past an hour.");
        TextView st=tx(sum,13,ink,false);st.setLineSpacing(dp(2),1f);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(12);c.addView(st,sp);
        statGrid(c,new String[][]{{"Breaks taken",String.valueOf(breaks),""},{"Sits over 1 hour",String.valueOf(longSits),""},{"Longest sit",longest>0?mins(longest):"—",""}},12);
        // Each sit, as a time range with a bar for its length
        if(!sits.isEmpty()){
            TextView lh=tx("YOUR SITS TODAY",10,muted,true);lh.setLetterSpacing(.06f);LinearLayout.LayoutParams lhp=lp(-1,-2);lhp.topMargin=dp(16);c.addView(lh,lhp);
            int maxLen=60;for(int[] x:sits)maxLen=Math.max(maxLen,x[1]);
            for(int[] x:sits){
                LinearLayout r=row();r.setPadding(0,dp(8),0,0);c.addView(r,lp(-1,-2));
                r.addView(tx(clock12(x[0])+" – "+clock12(x[0]+x[1]),12,ink,false),lp(130,-2));
                FrameLayout track=new FrameLayout(this);track.setBackground(shape(raised,4));r.addView(track,new LinearLayout.LayoutParams(0,dp(8),1));
                View fill=new View(this);fill.setBackground(shape(x[1]>=60?MetricViews.ZONE_COLORS[3]:MetricViews.alpha(muted,170),4));track.addView(fill,new FrameLayout.LayoutParams(0,-1));final float f=(float)x[1]/maxLen;track.post(()->{FrameLayout.LayoutParams fl=(FrameLayout.LayoutParams)fill.getLayoutParams();fl.width=(int)(track.getWidth()*f);fill.setLayoutParams(fl);});
                TextView len=tx(mins(x[1]),12,x[1]>=60?MetricViews.ZONE_COLORS[3]:muted,true);len.setGravity(Gravity.END);r.addView(len,lp(64,-2));
            }
        }
        boolean remind=Reminders.inactiveOn(this);int after=Reminders.get(this,"remAfter",Reminders.DEF_AFTER);
        TextView rem=tx((remind?"Reminder after "+mins(after)+" still · change":"Sitting reminder is off · turn on")+"  ›",12,lime,true);rem.setPadding(0,dp(14),0,dp(2));rem.setOnClickListener(v->navigate("Notifications"));c.addView(rem);
    }
    private String mins(long m){return m>=60?(m%60==0?(m/60)+(m/60==1?" hour":" hours"):String.format(Locale.US,"%dh %02dm",m/60,m%60)):m+" min";}
    /** Home: today's steps hour by hour as a dot matrix, from Kadamm's own step counting (Health Connect's hours when that is on and fuller). */
    private void renderStepsByHour(){
        String today=todayKey();double[] own=DaySteps.hoursOn(this,today),hc=null;
        JSONObject d=DaySteps.minuteDays(this).optJSONObject(today);if(d!=null&&d.optJSONArray("h")!=null){JSONArray a=d.optJSONArray("h");hc=new double[24];for(int i=0;i<24;i++)hc[i]=a.optDouble(i);}
        double so=0,sh=0;if(own!=null)for(double v:own)so+=v;if(hc!=null)for(double v:hc)sh+=v;
        double[] hours=own!=null&&so>=sh?own:hc!=null?hc:own;if(hours==null){return;}
        double total=0;int peak=0;for(int i=0;i<24;i++){total+=hours[i];if(hours[i]>hours[peak])peak=i;}if(total<=0)return;
        // The headline is the same day total as the top of Home; the hours show where those steps fell.
        long dayTotal=DaySteps.todayTotal(this);double placed=total;if(dayTotal>0)total=dayTotal;
        LinearLayout c=metricCard("STEPS THROUGH THE DAY","","Every step you've taken today, hour by hour, from "+(hours==own?"Kadamm's own counting with this phone's step sensor":"Health Connect")+". Each dot is a block of steps (shown under the chart); dots turn amber in busier hours. The ring marks the current hour. Touch and slide across the chart to read any hour.");
        LinearLayout r=row();LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(6);c.addView(r,rp);
        r.addView(valueUnit(String.format(Locale.US,"%,d",Math.round(total)),"steps",26,ink),new LinearLayout.LayoutParams(0,-2,1));
        r.addView(tx("Most active "+MetricViews.DotDay.hourName(peak),12,MetricViews.ZONE_COLORS[3],true));
        MetricViews.DotDay dd=new MetricViews.DotDay(this,chartTheme(),hours,Calendar.getInstance().get(Calendar.HOUR_OF_DAY),lime);
        LinearLayout.LayoutParams dp1=lp(-1,170);dp1.topMargin=dp(8);c.addView(dd,dp1);
        TextView key=tx("● = "+dd.unit()+" steps",10,muted,false);LinearLayout.LayoutParams kp=lp(-1,-2);kp.topMargin=dp(4);c.addView(key,kp);
        long gap=Math.round(total-placed);
        if(Math.abs(gap)>Math.max(50,total*.01)){TextView n=tx(gap>0?String.format(Locale.US,"%,d steps don't have an hour yet (Health Connect sends them later)",gap):String.format(Locale.US,"The hours add up to %,d; small differences come from readings around midnight",Math.round(placed)),10,muted,false);c.addView(n);}
    }
    private void renderMovementQuality(){
        JSONObject d=DaySteps.minuteDays(this).optJSONObject(todayKey());if(d==null||!d.has("p30"))return;
        LinearLayout c=metricCard("MOVEMENT QUALITY","today","Peak-30 cadence is the average of your 30 highest step-per-minute minutes today, not necessarily in a row. Research on thousands of adults (Tudor-Locke; Del Pozo Cruz, JAMA Internal Medicine 2022) links a higher peak-30 cadence with lower risk of death, heart disease and dementia, beyond total steps. Around 100 is moderate intensity; 110+ is excellent.\n\nBrisk minutes are minutes at 100+ steps. Active hours are hours of the day with at least 250 steps (about two to three minutes of walking); aim to move in most waking hours.\n\nFrom Samsung Health's minute steps through Health Connect.");
        int p30=d.optInt("p30"),brisk=d.optInt("brisk"),active=0;JSONArray hh=d.optJSONArray("h");if(hh!=null)for(int i=0;i<hh.length();i++)if(hh.optLong(i)>=250)active++;
        statGrid(c,new String[][]{{"Peak-30 cadence",String.valueOf(p30),"spm"},{"Brisk minutes",String.valueOf(brisk),"min"},{"Active hours",String.valueOf(active),"of 24"}},10);
        TextView v=tx(p30>=110?"Excellent walking intensity":p30>=100?"Moderate-intensity walking reached":p30>=80?"Mostly easy walking so far":"Little brisk walking so far",12,p30>=100?positive:warning,true);LinearLayout.LayoutParams vp=lp(-1,-2);vp.topMargin=dp(10);c.addView(v,vp);

    }
    /** Profile: fitness and form, this week's intensity minutes, and how you walk over the last 30 days. */
    private void renderFitnessSnapshot(JSONArray h){
        SessionMetrics.Fitness fit=SessionMetrics.fitness(this,h);if(fit.days.isEmpty())return;
        LinearLayout c=card();GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,MetricViews.ZONE_COLORS[2],darkTheme?.18f:.10f),blend(panel,violet,darkTheme?.14f:.07f)});g.setCornerRadius(dp(20));g.setStroke(dp(1),blend(line,MetricViews.ZONE_COLORS[2],.3f));c.setBackground(g);
        LinearLayout hd=row();c.addView(hd,lp(-1,-2));TextView t=tx("FITNESS SNAPSHOT",10,MetricViews.ZONE_COLORS[2],true);t.setLetterSpacing(.08f);hd.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=tx("Reports  ›",12,lime,true);more.setOnClickListener(v->navigate("Reports"));hd.addView(more);
        Calendar mon=Calendar.getInstance();int back=(mon.get(Calendar.DAY_OF_WEEK)+5)%7;mon.add(Calendar.DAY_OF_YEAR,-back);Calendar next=(Calendar)mon.clone();next.add(Calendar.DAY_OF_YEAR,7);
        double who=SessionMetrics.intensityMinutes(this,h,DaySteps.key(mon.getTimeInMillis()),DaySteps.key(next.getTimeInMillis()));
        LinearLayout r=row();LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(10);c.addView(r,rp);
        r.addView(new MetricViews.Ring(this,chartTheme(),(float)(who/150),MetricViews.ZONE_COLORS[2],String.valueOf(Math.round(who)),"WHO min"),lp(96,96));
        LinearLayout right=col();LinearLayout.LayoutParams rcp=new LinearLayout.LayoutParams(0,-2,1);rcp.leftMargin=dp(16);r.addView(right,rcp);
        String verdict=fit.form>5?"Fresh":fit.form>=-10?"Neutral":fit.form>=-30?"Productive":"Overreaching";
        right.addView(valueUnit(String.format(Locale.US,"%.0f",fit.fitness),"fitness",28,ink));
        right.addView(tx(String.format(Locale.US,"Form %+.0f · %s",fit.form,verdict),13,fit.form>=-30?positive:critical,true));
        right.addView(tx(who>=150?"WHO 150 met this week":Math.round(Math.max(0,150-who))+" intensity min to go this week",11,muted,false));
        // Last 30 days: walking speed and cadence, weighted by moving time.
        String from=DaySteps.key(System.currentTimeMillis()-30L*86400000L);double sp=0,spT=0,cd=0,cdT=0;
        for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o==null||o.optString("day","").compareTo(from)<0||Insights.vehicle(o))continue;SessionMetrics m=SessionMetrics.of(this,o);
            if(m.walkingSpeed()>0&&!m.estimatedDistance){sp+=m.walkingSpeed()*m.movingS;spT+=m.movingS;}if(m.cadenceAvg>0){cd+=m.cadenceAvg*m.movingS;cdT+=m.movingS;}}
        statGrid(c,new String[][]{{"Walking speed",spT>0?String.format(Locale.US,"%.2f",sp/spT):"—",spT>0?"m/s":""},{"Cadence",cdT>0?String.valueOf(Math.round(cd/cdT)):"—",cdT>0?"spm":""},{"Fatigue",String.format(Locale.US,"%.0f",fit.fatigue),""}},14);
        TextView foot=tx("Last 30 days · from your sessions",10,muted,false);LinearLayout.LayoutParams fp=lp(-1,-2);fp.topMargin=dp(8);c.addView(foot,fp);
    }
    /** Personal records as a trophy shelf: a header with the count, then medal cards two by two. Tap one to open its session. */
    private void renderRecords(){
        Map<String,Long> totals=DaySteps.allDayTotals(this);JSONArray hh=history();
        java.util.List<Insights.Record> recs=new ArrayList<>(Insights.records(hh,totals));
        int[] st=DaySteps.streaks(totals,dailyGoal());if(st[1]>0)recs.add(new Insights.Record("Longest streak",st[1]+(st[1]==1?" day":" days"),"days in a row at your goal",0));
        java.util.Map<String,Long> weeks=new java.util.HashMap<>();
        for(Map.Entry<String,Long> e:totals.entrySet()){try{Calendar d=Calendar.getInstance();d.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(e.getKey()));d.add(Calendar.DAY_OF_YEAR,-((d.get(Calendar.DAY_OF_WEEK)+5)%7));String k=dateKey(d);Long o=weeks.get(k);weeks.put(k,(o==null?0:o)+e.getValue());}catch(Exception ignored){}}
        String bw=null;long bwv=0;for(Map.Entry<String,Long> e:weeks.entrySet())if(e.getValue()>bwv){bwv=e.getValue();bw=e.getKey();}
        if(bw!=null){String lab=bw;try{lab="Week of "+new SimpleDateFormat("d MMM yyyy",Locale.getDefault()).format(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(bw));}catch(Exception ignored){}recs.add(new Insights.Record("Best week",String.format(Locale.US,"%,d",bwv),lab,0));}
        if(recs.isEmpty())return;
        LinearLayout c=card();c.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{blend(panel,flame(),darkTheme?.18f:.10f),panel}){{setCornerRadius(dp(18));setStroke(dp(1),blend(line,flame(),.25f));}});
        LinearLayout h=row();c.addView(h);
        android.widget.ImageView ic=icon(R.drawable.ic_trophy,darkTheme?Color.rgb(11,11,13):Color.WHITE,9);ic.setBackground(shape(flame(),16));h.addView(ic,lp(46,46));
        LinearLayout ht=col();LinearLayout.LayoutParams htp=new LinearLayout.LayoutParams(0,-2,1);htp.leftMargin=dp(12);h.addView(ht,htp);
        ht.addView(tx("Personal records",18,ink,true));ht.addView(tx(recs.size()+" bests so far · tap one to relive it",11,muted,false));StringBuilder rt=new StringBuilder("My personal records on Kadamm");for(Insights.Record r:recs)rt.append("\n• ").append(r.label).append(": ").append(r.value);String recText=rt.toString();h.addView(shareIcon("records",()->shareSheet("Share your records",recText,null,null)),lp(40,40));
        int[] colors={flame(),lime,purple(),teal(),pink(),green(),indigo(),orange()};
        LinearLayout line2=null;
        for(int i=0;i<recs.size();i++){
            Insights.Record r=recs.get(i);if(i%2==0){line2=row();line2.setGravity(Gravity.TOP);LinearLayout.LayoutParams lp2=lp(-1,-2);lp2.topMargin=dp(10);c.addView(line2,lp2);}
            int col=colors[i%colors.length];
            LinearLayout m=col();m.setGravity(Gravity.CENTER_HORIZONTAL);m.setPadding(dp(10),dp(14),dp(10),dp(12));
            GradientDrawable mg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,col,darkTheme?.22f:.13f),blend(panel,col,darkTheme?.05f:.03f)});mg.setCornerRadius(dp(18));mg.setStroke(dp(1),blend(line,col,.35f));m.setBackground(ripple(mg));
            LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-1,1);if(i%2==1)mp.leftMargin=dp(10);line2.addView(m,mp);
            // The medal: an icon in a coloured disc with a light rim, and a small ribbon tab under it.
            FrameLayout medal=new FrameLayout(this);m.addView(medal,lp(56,62));
            View ribbon=new View(this);ribbon.setBackground(shape(blend(col,Color.BLACK,.2f),3));FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(18),dp(16),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);medal.addView(ribbon,rp);
            android.widget.ImageView mi=icon(recordIcon(r.label),darkTheme?Color.rgb(11,11,13):Color.WHITE,12);GradientDrawable disc=new GradientDrawable();disc.setShape(GradientDrawable.OVAL);disc.setColor(col);disc.setStroke(dp(3),blend(col,Color.WHITE,.45f));mi.setBackground(disc);medal.addView(mi,new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.TOP|Gravity.CENTER_HORIZONTAL));
            TextView v=tx(r.value,20,ink,true);v.setGravity(Gravity.CENTER);LinearLayout.LayoutParams vp=lp(-1,-2);vp.topMargin=dp(8);m.addView(v,vp);
            TextView l=tx(r.label.toUpperCase(Locale.getDefault()),9,col,true);l.setGravity(Gravity.CENTER);l.setLetterSpacing(.06f);m.addView(l,lp(-1,-2));
            TextView d=tx(r.date,10,muted,false);d.setGravity(Gravity.CENTER);LinearLayout.LayoutParams dlp=lp(-1,-2);dlp.topMargin=dp(2);m.addView(d,dlp);
            m.setFocusable(true);m.setContentDescription(r.label+", "+r.value+", "+r.date);
            if(r.sessionId!=0){JSONObject target=null;for(int k=0;k<hh.length();k++)if(hh.optJSONObject(k).optLong("id")==r.sessionId)target=hh.optJSONObject(k);
                JSONObject go=target;if(go!=null)m.setOnClickListener(v2->{lastSession=go;page="Detail";renderPage();});}
            else if(r.label.startsWith("Most steps")||r.label.startsWith("Best week")||r.label.startsWith("Longest streak"))m.setOnClickListener(v2->navigate("History"));
        }
        if(recs.size()%2==1&&line2!=null){LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,1,1);sp.leftMargin=dp(10);line2.addView(new View(this),sp);}
    }
    private int recordIcon(String label){return label.startsWith("Fastest")?R.drawable.ic_bolt:label.startsWith("Longest distance")?R.drawable.ic_ruler:label.startsWith("Longest session")?R.drawable.ic_timer:label.startsWith("Most steps")?R.drawable.ic_walk:label.startsWith("Longest streak")?R.drawable.ic_flame:label.startsWith("Best week")?R.drawable.ic_calendar:R.drawable.ic_trophy;}
    // ---- Places you walk ----
    /** Every street you have covered on one map, how much of it is unique, and what is new lately. */
    private void renderPlaces(){
        brand("YOUR MAP","Places you walk","Every street you've covered. Walk somewhere new and watch the map fill in.");
        Insights.Coverage cv=Insights.coverage(history());boolean[] placesAll={false};
        if(cv.sessions==0){LinearLayout e=card();e.addView(tx("No routes yet",16,ink,true));e.addView(tx("Record an outdoor walk or run and its streets appear here. Indoor sessions have no route.",12,muted,false));return;}
        FrameLayout box=new FrameLayout(this);LinearLayout.LayoutParams bp=lp(-1,380);bp.topMargin=dp(4);content.addView(box,bp);
        RouteView map=new RouteView(this);map.setInteractive(true);map.setOverview(true);map.setTheme(bg,raised,lime,darkTheme);map.setRoute(cv.route);map.setFocus(cv.home);map.setClipToOutline(true);map.setBackground(shape(panel,18));map.setContentDescription("Map of every street you have walked");box.addView(map,new FrameLayout.LayoutParams(-1,-1));
        android.widget.ImageView fit=icon(R.drawable.ic_my_location,ink,10);fit.setBackground(rippleOn(panel,12));fit.setContentDescription("Show all your streets");fit.setOnClickListener(v->{map.setFocus(placesAll[0]?null:cv.home);map.resetView();});FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.TOP|Gravity.END);fp.setMargins(0,dp(10),dp(10),0);box.addView(fit,fp);
        TextView style=tx("Map: "+mapStyleName(),11,ink,true);style.setPadding(dp(10),dp(6),dp(10),dp(6));style.setBackground(rippleOn(panel,14));FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);sp.setMargins(dp(10),dp(10),0,0);box.addView(style,sp);style.setOnClickListener(v->{cycleMapStyle();map.setTheme(bg,raised,lime,darkTheme);style.setText("Map: "+mapStyleName());});
        if(cv.areas>1){TextView area=tx("Showing: where you walk most  ·  Show all",11,ink,true);area.setPadding(dp(12),dp(7),dp(12),dp(7));area.setBackground(rippleOn(panel,14));FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);ap.bottomMargin=dp(12);box.addView(area,ap);
            area.setOnClickListener(v->{placesAll[0]=!placesAll[0];map.setFocus(placesAll[0]?null:cv.home);area.setText(placesAll[0]?"Showing: all places  ·  Your area":"Showing: where you walk most  ·  Show all");});}
        content.addView(tx("© OpenStreetMap contributors · drag, pinch or double-tap to explore",10,muted,false));
        // Explorer level: each one needs more unique streets than the last.
        double[] levels={1,3,5,10,20,35,50,75,100,150,250,500};int lvl=0;while(lvl<levels.length&&cv.uniqueKm>=levels[lvl])lvl++;
        double from=lvl==0?0:levels[lvl-1],to=lvl<levels.length?levels[lvl]:levels[levels.length-1];
        LinearLayout lc=card();lc.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,teal(),darkTheme?.20f:.11f),panel}){{setCornerRadius(dp(18));setStroke(dp(1),blend(line,teal(),.3f));}});
        LinearLayout lh=row();lc.addView(lh);
        TextView badge=tx(String.valueOf(lvl),22,darkTheme?Color.rgb(11,11,13):Color.WHITE,true);badge.setGravity(Gravity.CENTER);GradientDrawable bd=new GradientDrawable();bd.setShape(GradientDrawable.OVAL);bd.setColor(teal());bd.setStroke(dp(3),blend(teal(),Color.WHITE,.45f));badge.setBackground(bd);lh.addView(badge,lp(52,52));
        LinearLayout lt=col();LinearLayout.LayoutParams ltp=new LinearLayout.LayoutParams(0,-2,1);ltp.leftMargin=dp(12);lh.addView(lt,ltp);
        String[] names={"Doorstep","Neighbour","Street walker","Local","Wanderer","Pathfinder","Explorer","Trailblazer","Navigator","Voyager","Cartographer","Legend","Legend"};
        lt.addView(tx("EXPLORER LEVEL",10,muted,true));lt.addView(tx(names[Math.min(lvl,names.length-1)],19,ink,true));
        lt.addView(tx(lvl<levels.length?String.format(Locale.US,"%.1f km of new streets to level %d",Math.max(0,to-cv.uniqueKm),lvl+1):"Top level reached",11,muted,false));
        LinearLayout.LayoutParams pp=lp(-1,14);pp.topMargin=dp(12);lc.addView(new MiniChartView(this,chartTheme(),MiniChartView.PROGRESS,teal(),0).progress(lvl<levels.length?(float)((cv.uniqueKm-from)/Math.max(.01,to-from)):1f),pp);
        LinearLayout r1=glanceRow();
        glance(r1,"STREETS",String.format(Locale.US,"%.1f",cv.uniqueKm),"km",lime,"↗",null,"unique · repeats count once");
        glance(r1,"WALKED",String.format(Locale.US,"%.1f",cv.totalKm),"km",purple(),"▶",new MiniChartView(this,chartTheme(),MiniChartView.PROGRESS,purple(),0).progress((float)(cv.uniqueKm/Math.max(.01,cv.totalKm))),String.format(Locale.US,"%d%% on new ground · %d routes",Math.round(cv.uniqueKm*100/Math.max(.01,cv.totalKm)),cv.sessions));
        LinearLayout r2=glanceRow();
        glance(r2,"NEW THIS WEEK",String.format(Locale.US,"%.1f",cv.weekKm),"km",green(),"◷",null,cv.weekKm<.05?"walk a new street to add some":"streets you'd never walked");
        glance(r2,"NEW THIS MONTH",String.format(Locale.US,"%.1f",cv.monthKm),"km",orange(),"≈",null,"last 30 days");
        renderStreets(cv);
        LinearLayout tip=card();tip.addView(tx("How it counts",14,ink,true));tip.addView(tx("Each route is split into 25 m squares. A square counts the first time you reach it, so the same loop twice adds nothing; take a turn you've never taken to grow the map. Vehicle rides are left out.",11,muted,false));
    }
    private int streetBatches;private boolean streetsAll;
    /** The streets you have covered, by name, longest first; names are looked up in the background a batch at a time. */
    private void renderStreets(Insights.Coverage cv){
        LinearLayout c=card();LinearLayout h=row();c.addView(h);
        android.widget.ImageView ic=icon(R.drawable.ic_place,darkTheme?Color.rgb(11,11,13):Color.WHITE,8);ic.setBackground(shape(lime,12));h.addView(ic,lp(38,38));
        LinearLayout ht=col();LinearLayout.LayoutParams htp=new LinearLayout.LayoutParams(0,-2,1);htp.leftMargin=dp(12);h.addView(ht,htp);
        java.util.List<Streets.Street> list=Streets.list(this,cv);java.util.Set<String> areas=new java.util.TreeSet<>();for(Streets.Street st:list)if(!st.area.isEmpty())areas.add(st.area);
        ht.addView(tx("Streets you've walked",16,ink,true));ht.addView(tx(list.size()+(list.size()==1?" street":" streets")+(areas.isEmpty()?"":" · "+areas.size()+(areas.size()==1?" area":" areas")),11,muted,false));
        int left=Streets.pending(this,cv);
        if(!Streets.available()){c.addView(tx("Street names need Android's geocoder, which isn't available on this phone.",11,muted,false));return;}
        if(left>0&&!Streets.running()&&streetBatches<12){streetBatches++;Streets.lookUp(this,cv,40,()->{if("Places".equals(page)&&resumed){int y=scroller.getScrollY();renderPageNow();scroller.post(()->scroller.scrollTo(0,y));}});}
        if(left>0){TextView st=tx(Streets.running()?String.format(Locale.US,"Naming streets… %d spots left",left):String.format(Locale.US,"%d spots not named yet · they'll be named when you're online",left),11,lime,true);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(8);c.addView(st,sp);}
        if(list.isEmpty())return;
        double max=list.get(0).km;int shown=streetsAll?list.size():Math.min(8,list.size());
        for(int i=0;i<shown;i++){Streets.Street st=list.get(i);
            LinearLayout r=col();r.setPadding(0,dp(10),0,0);c.addView(r,lp(-1,-2));
            LinearLayout rr=row();r.addView(rr);TextView n=tx(st.name,14,ink,true);n.setSingleLine(true);n.setEllipsize(android.text.TextUtils.TruncateAt.END);rr.addView(n,new LinearLayout.LayoutParams(0,-2,1));
            rr.addView(tx(String.format(Locale.US,"%.2f km",st.km),12,muted,true));
            if(!st.area.isEmpty())r.addView(tx(st.area,10,muted,false));
            LinearLayout.LayoutParams bp=lp(-1,10);bp.topMargin=dp(4);r.addView(new MiniChartView(this,chartTheme(),MiniChartView.PROGRESS,i==0?flame():lime,0).progress((float)(st.km/Math.max(.001,max))),bp);
        }
        if(list.size()>8){TextView more=tx(streetsAll?"Show fewer":"Show all "+list.size()+" streets",13,lime,true);more.setGravity(Gravity.CENTER_VERTICAL);more.setBackground(rippleOn(Color.TRANSPARENT,10));LinearLayout.LayoutParams mp=lp(-1,44);mp.topMargin=dp(6);c.addView(more,mp);more.setOnClickListener(v->{streetsAll=!streetsAll;int y=scroller.getScrollY();renderPageNow();scroller.post(()->scroller.scrollTo(0,y));});}
        TextView note=tx("Names come from Android's geocoder, looked up once per 100 m and kept on this phone.",10,muted,false);LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(10);c.addView(note,np);
    }
    // ---- Routes ----
    private void renderRoutes(){
        brand("YOUR ROUTES","Routes","Save a session as a route, then beat your time on it.");
        JSONArray rs=Insights.routes(this),hist=history();
        if(rs.length()==0){LinearLayout e=card();e.addView(tx("No saved routes yet",15,ink,true));e.addView(tx("Open a session with a GPS route and tap Save as route. Next time you walk or run it, Kadamm shows your time against your best.",12,muted,false));return;}
        for(int i=rs.length()-1;i>=0;i--){
            JSONObject route=rs.optJSONObject(i);if(route==null)continue;java.util.List<JSONObject> tries=Insights.attempts(route,hist);
            long best=Long.MAX_VALUE;for(JSONObject t:tries)best=Math.min(best,t.optLong("durationMs"));
            LinearLayout c=card();LinearLayout h=row();c.addView(h);
            LinearLayout l=col();h.addView(l,new LinearLayout.LayoutParams(0,-2,1));l.addView(tx(route.optString("name"),17,ink,true));
            l.addView(tx(String.format(Locale.US,"%.2f km · %d %s",route.optDouble("distanceKm"),tries.size(),tries.size()==1?"attempt":"attempts"),12,muted,false));
            if(best<Long.MAX_VALUE)h.addView(tx(formatDuration(best),18,lime,true));
            RouteView map=new RouteView(this);map.setTheme(bg,raised,lime,darkTheme);map.setFollowLocation(false);map.setRoute(route.optString("route","[]"));LinearLayout.LayoutParams mp=lp(-1,140);mp.topMargin=dp(10);c.addView(map,mp);
            for(int k=tries.size()-1;k>=0&&k>=tries.size()-5;k--){
                JSONObject t=tries.get(k);long d=t.optLong("durationMs");LinearLayout ar=row();ar.setPadding(0,dp(8),0,dp(2));ar.setBackground(rippleOn(Color.TRANSPARENT,8));c.addView(ar,lp(-1,-2));
                ar.addView(tx(t.optString("date")+" · "+t.optString("time"),12,ink,false),new LinearLayout.LayoutParams(0,-2,1));
                ar.addView(tx(formatDuration(d)+(d==best?"  best":"  +"+formatDuration(d-best)),12,d==best?positive:muted,true));
                ar.setOnClickListener(v->{lastSession=t;page="Detail";renderPage();});
            }
            LinearLayout acts=row();LinearLayout.LayoutParams ap=lp(-1,42);ap.topMargin=dp(10);c.addView(acts,ap);
            Button ren=button("Rename",raised,ink),del=button("Delete",raised,critical);acts.addView(ren,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,-1,1);dlp.leftMargin=dp(8);acts.addView(del,dlp);
            long id=route.optLong("id");
            ren.setOnClickListener(v->{android.widget.EditText in=new android.widget.EditText(this);in.setSingleLine(true);in.setText(route.optString("name"));dialog().setTitle("Rename route").setView(in).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{String n=in.getText().toString().trim();if(!n.isEmpty())Insights.renameRoute(this,id,n.length()>40?n.substring(0,40):n);renderPageNow();}).show();});
            del.setOnClickListener(v->dialog().setTitle("Delete this route?").setMessage("Your sessions stay; only the saved route and its comparisons go.").setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{Insights.deleteRoute(this,id);renderPageNow();}).show());
        }
    }
    // ---- Notification settings ----
    private static final int NOTIFY_PERMISSION=102;
    private boolean notificationsAllowed(){
        android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);
        return (Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&(nm==null||nm.areNotificationsEnabled());
    }
    private void openAppNotificationSettings(){try{startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}catch(Exception e){toast("Open Settings > Apps > Kadamm > Notifications");}}
    private void renderNotifications(){
        brand("SETTINGS","Notifications","Choose what Kadamm tells you, and when.");
        boolean on=notificationsAllowed();
        LinearLayout st=card();LinearLayout h=row();st.addView(h);
        android.widget.ImageView ic=icon(R.drawable.ic_bell,darkTheme?Color.rgb(11,11,13):Color.WHITE,9);ic.setBackground(shape(on?positive:warning,14));h.addView(ic,lp(40,40));
        LinearLayout w=col();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(12);h.addView(w,wp);
        w.addView(tx(on?"Notifications are on":"Notifications are off",16,on?positive:warning,true));
        w.addView(tx(on?"Kadamm can show your live session and the reminders you choose below.":"Without them you won't see the live session card, goal alerts or reminders. Tracking still works.",11,muted,false));
        if(!on){Button allow=button("Allow notifications",lime,accentOn);allow.setTextSize(15);LinearLayout.LayoutParams ap=lp(-1,48);ap.topMargin=dp(12);st.addView(allow,ap);
            allow.setOnClickListener(v->{SharedPreferences s0=getSharedPreferences("kadamm_settings",MODE_PRIVATE);
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&(!s0.getBoolean("notifyAsked",false)||shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))){s0.edit().putBoolean("notifyAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY_PERMISSION);}
                else openAppNotificationSettings();});}
        LinearLayout ses=card();ses.addView(tx("DURING A SESSION",10,muted,true));
        LinearLayout live=row();live.setPadding(0,dp(10),0,0);ses.addView(live,lp(-1,-2));LinearLayout ll=col();live.addView(ll,new LinearLayout.LayoutParams(0,-2,1));
        ll.addView(tx("Live session card",14,ink,true));ll.addView(tx("Time, distance, pace and Pause/Finish. Android requires it while recording.",11,muted,false));live.addView(tx("Always",11,positive,true));
        reminderSwitch(ses,"Show details on the lock screen","Off: the lock screen shows only that a session is running","notifyLockPublic");
        LinearLayout goals=card();goals.addView(tx("GOALS",10,muted,true));
        reminderSwitch(goals,"Daily goal reached","Once a day, when you hit your step goal","notifyGoal");
        reminderSwitch(goals,"You're making progress","Once a day, at 60% of your goal","notifyProgress");
        reminderSwitch(goals,"Weekly report","Monday morning: last week's steps, goal days and sessions","notifyWeekly");
        int sumAt=Reminders.get(this,"sumAt",21*60);
        reminderSwitch(goals,"Daily summary","A short summary of your day at "+clock12(sumAt),"notifySummary");
        if(getSharedPreferences("kadamm_settings",MODE_PRIVATE).getBoolean("notifySummary",true)){LinearLayout sr=row();LinearLayout.LayoutParams srp=lp(-1,40);srp.topMargin=dp(8);goals.addView(sr,srp);timeChip(sr,"At "+clock12(sumAt)+" · change","sumAt",sumAt);choiceChips(goals,"Quick pick",new int[]{19*60,20*60,21*60,22*60},sumAt,"sumAt",this::clock12);}
        int sesAt=Reminders.get(this,"sesAt",23*60);
        reminderSwitch(goals,"Session summary","Your day's sessions added up at "+clock12(sesAt)+", with Share, on days you record any","notifySessions");
        if(getSharedPreferences("kadamm_settings",MODE_PRIVATE).getBoolean("notifySessions",true)){LinearLayout sr2=row();LinearLayout.LayoutParams srp2=lp(-1,40);srp2.topMargin=dp(8);goals.addView(sr2,srp2);timeChip(sr2,"At "+clock12(sesAt)+" · change","sesAt",sesAt);choiceChips(goals,"Quick pick",new int[]{21*60,22*60,23*60,23*60+30},sesAt,"sesAt",this::clock12);}
        TextView once=tx("Each notification's wording is used at most once a day, so you never get the same message twice in a day. \"Time to move\" comes at most 3 times a day.",11,muted,false);LinearLayout.LayoutParams onp=lp(-1,-2);onp.topMargin=dp(10);goals.addView(once,onp);
        LinearLayout rem=card();rem.addView(tx("REMINDERS",10,muted,true));
        boolean auto=Reminders.automatic(this);
        LinearLayout modeRow=row();modeRow.setPadding(0,dp(10),0,dp(2));rem.addView(modeRow,lp(-1,-2));
        modeRow.addView(tx(auto?"Automatic times":"Your own times",13,auto?positive:lime,true),new LinearLayout.LayoutParams(0,-2,1));
        if(!auto){TextView reset=tx("Reset to automatic",12,lime,true);reset.setPadding(dp(8),dp(6),dp(8),dp(6));reset.setBackground(rippleOn(Color.TRANSPARENT,10));modeRow.addView(reset);reset.setOnClickListener(v->{Reminders.resetToAutomatic(this);renderPageNow();});}
        int start=Reminders.get(this,"remStart",Reminders.DEF_START),end=Reminders.get(this,"remEnd",Reminders.DEF_END),after=Reminders.get(this,"remAfter",Reminders.DEF_AFTER),evening=Reminders.get(this,"remEvening",Reminders.DEF_EVENING),left=Reminders.get(this,"remLeft",Reminders.DEF_LEFT);
        reminderSwitch(rem,"Time to move",String.format(Locale.US,"After %d min without steps, %s to %s",after,clock12(start),clock12(end)),"remindInactive");
        if(getSharedPreferences("kadamm_settings",MODE_PRIVATE).getBoolean("remindInactive",true)){
            LinearLayout hours=row();LinearLayout.LayoutParams hp=lp(-1,40);hp.topMargin=dp(8);rem.addView(hours,hp);
            timeChip(hours,"From "+clock12(start),"remStart",start);timeChip(hours,"Until "+clock12(end),"remEnd",end);
            choiceChips(rem,"Remind after",new int[]{30,45,60,90,120},after,"remAfter",v->v>=60&&v%60==0?(v/60)+" h":v+" min");
        }
        reminderSwitch(rem,"Close to your goal (evening)",String.format(Locale.US,"From %s, if you are within %,d steps of your daily goal, a nudge to finish it",clock12(evening),left),"remindEvening");
        if(getSharedPreferences("kadamm_settings",MODE_PRIVATE).getBoolean("remindEvening",true)){
            LinearLayout ev=row();LinearLayout.LayoutParams evp=lp(-1,40);evp.topMargin=dp(8);rem.addView(ev,evp);
            timeChip(ev,"At "+clock12(evening),"remEvening",evening);ev.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
            choiceChips(rem,"When steps left are",new int[]{1000,2000,3000,5000},left,"remLeft",v->String.format(Locale.US,"%,d",v));
        }
        Button preview=button("Preview notifications",raised,ink);LinearLayout.LayoutParams pvp=lp(-1,48);pvp.topMargin=dp(14);content.addView(preview,pvp);
        TextView pvn=tx("Sends one sample of each, marked \"Preview\", so you can see how they look. Swipe them away when done.",11,muted,false);content.addView(pvn,lp(-1,-2));
        preview.setOnClickListener(v->{if(!notificationsAllowed()){toast("Allow notifications first");return;}Reminders.preview(this);toast("Previews sent. Pull down the notification shade to see them.");});
        TextView more=tx("Sounds and vibration in Android settings  →",13,lime,true);more.setGravity(Gravity.CENTER_VERTICAL);more.setFocusable(true);more.setBackground(rippleOn(Color.TRANSPARENT,10));LinearLayout.LayoutParams mp=lp(-1,48);mp.topMargin=dp(10);content.addView(more,mp);more.setOnClickListener(v->openAppNotificationSettings());
    }
    private String clock12(int minutes){int h=minutes/60%24,m=minutes%60;return String.format(Locale.US,"%d:%02d %s",h%12==0?12:h%12,m,h<12?"am":"pm");}
    /** A chip that opens a time picker and saves minutes-of-day under {@code key}. */
    private void timeChip(LinearLayout parent,String label,String key,int minutes){
        Button b=button(label,raised,ink);b.setTextSize(13);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=dp(8);parent.addView(b,p);
        b.setOnClickListener(v->new android.app.TimePickerDialog(this,darkTheme?android.R.style.Theme_DeviceDefault_Dialog_Alert:android.R.style.Theme_DeviceDefault_Light_Dialog_Alert,(tp,h,m)->{getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putInt(key,h*60+m).apply();renderPageNow();},minutes/60,minutes%60,android.text.format.DateFormat.is24HourFormat(this)).show());
    }
    /** A labelled row of preset choices saved as an int under {@code key}. */
    private void choiceChips(LinearLayout parent,String label,int[] values,int current,String key,java.util.function.IntFunction<String> fmt){
        TextView l=tx(label,11,muted,false);LinearLayout.LayoutParams lpp=lp(-1,-2);lpp.topMargin=dp(8);parent.addView(l,lpp);
        LinearLayout r=row();LinearLayout.LayoutParams rp=lp(-1,36);rp.topMargin=dp(4);parent.addView(r,rp);
        for(int v:values){boolean on=v==current;Button b=button(fmt.apply(v),on?lime:raised,on?accentOn:ink);b.setTextSize(12);b.setPadding(0,0,0,0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(r.getChildCount()>0)p.leftMargin=dp(6);r.addView(b,p);
            b.setOnClickListener(x->{if(v==current)return;confirmChange("Change "+label.toLowerCase(Locale.getDefault())+"?","From "+fmt.apply(current)+" to "+fmt.apply(v)+".",()->{getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putInt(key,v).apply();renderPageNow();},null);});}
    }
    // ---- Reminders ----
    private void renderReminderSettings(){
        LinearLayout c=card();LinearLayout h=row();c.addView(h);
        android.widget.ImageView ic=icon(R.drawable.ic_timer,darkTheme?Color.rgb(11,11,13):Color.WHITE,9);ic.setBackground(shape(teal(),14));h.addView(ic,lp(40,40));
        LinearLayout w=col();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(12);h.addView(w,wp);w.addView(tx("Reminders",16,ink,true));w.addView(tx("Gentle nudges, never during a session.",11,muted,false));
        reminderSwitch(c,"Inactive for an hour","Between 8 am and 9 pm, at most every 2 hours","remindInactive");
        reminderSwitch(c,"Evening: almost there","After 7 pm when 2,000 steps or fewer are left","remindEvening");
    }
    private void reminderSwitch(LinearLayout c,String title,String sub,String key){
        LinearLayout r=row();r.setPadding(0,dp(10),0,0);c.addView(r,lp(-1,-2));LinearLayout l=col();r.addView(l,new LinearLayout.LayoutParams(0,-2,1));l.addView(tx(title,14,ink,true));l.addView(tx(sub,11,muted,false));
        android.widget.Switch sw=new android.widget.Switch(this);sw.setChecked(getSharedPreferences("kadamm_settings",MODE_PRIVATE).getBoolean(key,true));
        sw.setThumbTintList(switchTint());sw.setContentDescription(title);r.addView(sw);
        sw.setOnCheckedChangeListener((b,on)->{getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putBoolean(key,on).apply();if(key.startsWith("remind")||key.equals("notifySummary")){int y=scroller.getScrollY();renderPageNow();scroller.post(()->scroller.scrollTo(0,y));}});
    }
    /** Rotation no longer recreates the screen; only refit what depends on the width. */
    @Override public void onConfigurationChanged(android.content.res.Configuration config){
        super.onConfigurationChanged(config);
        if(drawer!=null){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)drawer.getLayoutParams();p.width=Math.min(dp(340),getResources().getDisplayMetrics().widthPixels-dp(48));drawer.setLayoutParams(p);}
        root.requestApplyInsets();
    }
    private void activityChoice(LinearLayout parent,String title,String sub,int res,String mode,String type){
        boolean running=prefs.getBoolean("active",false);String curType=running?prefs.getString("currentType",activityType):activityType;
        boolean on=mode.equals(prefs.getString("activityMode","outdoor"))&&type.equals(curType);
        LinearLayout t=row();t.setPadding(dp(10),dp(12),dp(10),dp(12));GradientDrawable g=shape(on?blend(panel,lime,darkTheme?.18f:.10f):panel,16);g.setStroke(dp(on?2:1),on?lime:line);t.setBackground(ripple(g));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(parent.getChildCount()>0)p.leftMargin=dp(8);parent.addView(t,p);
        android.widget.ImageView ic=icon(res,on?accentOn:("Run".equals(type)?pink():lime),7);ic.setBackground(shape(on?lime:blend(panel,"Run".equals(type)?pink():lime,.14f),12));t.addView(ic,lp(34,34));
        LinearLayout w=col();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(10);t.addView(w,wp);w.addView(tx(title,13,ink,true));w.addView(tx(sub,10,muted,false));
        t.setFocusable(true);t.setContentDescription(title+(on?", selected":""));
        t.setOnClickListener(v->{
            if(prefs.getBoolean("active",false)){if(!mode.equals(prefs.getString("activityMode","outdoor"))){toast("Finish this session to switch between outdoor and indoor");return;}setManualType(type);return;}
            prefs.edit().putString("activityMode",mode).apply();activityType=type;renderPage();
        });
    }
    private void renderReportsLink(){
        LinearLayout c=card();LinearLayout r=row();c.addView(r);
        android.widget.ImageView ic=icon(R.drawable.ic_chart,darkTheme?Color.rgb(11,11,13):Color.WHITE,8);ic.setBackground(shape(indigo(),12));r.addView(ic,lp(38,38));
        LinearLayout t=col();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(12);r.addView(t,tp);t.addView(tx("Reports",15,ink,true));t.addView(tx("Weekly, monthly and custom ranges",11,muted,false));
        r.addView(tx("›",24,muted,false));c.setFocusable(true);c.setBackground(rippleOn(panel,16));GradientDrawable g=(GradientDrawable)shape(panel,16);g.setStroke(dp(1),line);c.setBackground(ripple(g));c.setOnClickListener(v->navigate("Reports"));
    }
    /** Blue thumb when on, grey when off, so a switch never looks on while it is off. */
    private android.content.res.ColorStateList switchTint(){return new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{lime,darkTheme?Color.rgb(154,160,166):Color.rgb(189,193,198)});}
    private String mapStyleName(){String st=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("mapStyle","auto");return "dark".equals(st)?"Dark":"light".equals(st)?"Light":"Auto";}
    private void cycleMapStyle(){SharedPreferences st=getSharedPreferences("kadamm_settings",MODE_PRIVATE);String cur=st.getString("mapStyle","auto");st.edit().putString("mapStyle","auto".equals(cur)?"light":"light".equals(cur)?"dark":"auto").apply();}
    // ---- Auto-tracking ----
    private static final int BG_LOCATION_PERMISSION=104;
    private void renderAutoTracking(){
        brand("SETTINGS","Auto-tracking","Kadamm notices when you start walking or running and records it for you.");
        boolean on=AutoTrack.enabled(this),bgLoc=AutoTrack.backgroundLocation(this);
        LinearLayout c=card();LinearLayout r=row();c.addView(r);LinearLayout l=col();r.addView(l,new LinearLayout.LayoutParams(0,-2,1));
        l.addView(tx("Auto-record walks and runs",16,ink,true));l.addView(tx("Starts a session with the map when activity recognition sees you walking or running, even with Kadamm closed.",11,muted,false));
        android.widget.Switch sw=new android.widget.Switch(this);sw.setChecked(on);sw.setThumbTintList(switchTint());r.addView(sw);
        sw.setOnCheckedChangeListener((b,v)->{AutoTrack.s(this).edit().putBoolean("autoTrack",v).apply();AutoTrack.sync(this);
            if(v&&!AutoTrack.backgroundLocation(this))dialog().setTitle("Allow location all the time").setMessage("To draw the route of a walk Kadamm starts by itself, Android needs location set to \"Allow all the time\". On the next screen choose that option.").setNegativeButton("Not now",null).setPositiveButton("Continue",(d,w)->requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},BG_LOCATION_PERMISSION)).show();
            renderPageNow();});
        if(on&&!bgLoc){TextView warn=tx("Location is not set to \"Allow all the time\", so automatic sessions count steps and time but may miss the route.",11,warning,false);LinearLayout.LayoutParams wp=lp(-1,-2);wp.topMargin=dp(8);c.addView(warn,wp);
            Button fix=button("Allow location all the time",lime,accentOn);LinearLayout.LayoutParams fp=lp(-1,44);fp.topMargin=dp(8);c.addView(fix,fp);fix.setOnClickListener(v->requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},BG_LOCATION_PERMISSION));}
        LinearLayout f=card();f.addView(tx("Finish automatically",16,ink,true));
        f.addView(tx("Any session, automatic or started by you, closes by itself after this long without steps or movement, paused or not. You can also change it on the live screen.",11,muted,false));
        choiceChips(f,"After no movement for",new int[]{2,3,5,10,0},AutoTrack.stopAfterMin(this),"autoStopMin",v->v==0?"Off":v+" min");
        LinearLayout i=card();i.addView(tx("Good to know",14,ink,true));
        i.addView(tx("• A session needs at least "+SessionRules.MIN_STEPS+" steps to be saved. Shorter ones still add their steps to the day.\n• Rides are left out: an automatic session with no steps after two minutes is dropped, getting into a vehicle closes it, and stretches at 15 km/h or faster with almost no steps are cut from any session.\n• Stepping without going anywhere (a treadmill, a shop) is counted as indoor walking from your stride; a session that is mostly that becomes an indoor session.\n• Automatic sessions are marked \"Auto\" in History.\n• Keep unrestricted battery on (Permissions) so Android lets Kadamm start sessions in the background.",12,muted,false));
    }
    // ---- Storage and backup ----
    private static final int EXPORT_REQUEST=201,IMPORT_REQUEST=202;
    private long bytesOf(java.io.File f){if(f==null||!f.exists())return 0;if(f.isFile())return f.length();long n=0;java.io.File[] k=f.listFiles();if(k!=null)for(java.io.File x:k)n+=bytesOf(x);return n;}
    private String size(long b){return b>=1048576?String.format(Locale.US,"%.1f MB",b/1048576d):String.format(Locale.US,"%.0f KB",Math.max(1,b/1024d));}
    private void renderStorage(){
        brand("SETTINGS","Storage & backup","See what Kadamm keeps, clear what you don't need, and save a copy anywhere.");
        JSONArray h=history();long histBytes=historyPrefs.getString("sessions","[]").length(),mapBytes=bytesOf(new java.io.File(getCacheDir(),"osm-tile-cache")),shareBytes=bytesOf(new java.io.File(getCacheDir(),"share"));
        LinearLayout u=card();u.addView(tx("ON THIS PHONE",10,muted,true));
        detailRow(u,"SESSIONS",h.length()+" · "+size(histBytes),"SAVED ROUTES",String.valueOf(Insights.routes(this).length()));
        detailRow(u,"MAP CACHE",size(mapBytes),"SHARE IMAGES",size(shareBytes));
        Button clear=button("Clear map cache and share images",raised,ink);LinearLayout.LayoutParams cp=lp(-1,44);cp.topMargin=dp(8);u.addView(clear,cp);
        clear.setOnClickListener(v->{deleteTree(new java.io.File(getCacheDir(),"osm-tile-cache"));deleteTree(new java.io.File(getCacheDir(),"share"));toast("Cache cleared. Map tiles download again when needed.");renderPageNow();});
        LinearLayout b=card();b.addView(tx("Backup",16,ink,true));b.addView(tx("Save all sessions, routes, daily steps, goals and settings to a file in a place you choose (Downloads, Google Drive, a USB drive…). Restore it here later, or on a new phone.",11,muted,false));
        LinearLayout br=row();LinearLayout.LayoutParams bp=lp(-1,46);bp.topMargin=dp(10);b.addView(br,bp);
        Button exp=button("Save backup",lime,accentOn),imp=button("Restore",raised,ink);br.addView(exp,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-1,1);ip.leftMargin=dp(8);br.addView(imp,ip);
        exp.setOnClickListener(v->{try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"kadamm-backup-"+todayKey()+".json"),EXPORT_REQUEST);}catch(Exception e){toast("No file picker available");}});
        imp.setOnClickListener(v->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT_REQUEST);}catch(Exception e){toast("No file picker available");}});
        java.util.TreeMap<String,int[]> months=new java.util.TreeMap<>(java.util.Collections.reverseOrder());
        for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o==null)continue;String d=o.optString("day","");if(d.length()<7)continue;String m=d.substring(0,7);int[] x=months.get(m);if(x==null){x=new int[2];months.put(m,x);}x[0]++;x[1]+=o.toString().length();}
        TextView mt=tx("BY MONTH",10,muted,true);LinearLayout.LayoutParams mtp=lp(-1,-2);mtp.topMargin=dp(18);content.addView(mt,mtp);
        if(months.isEmpty()){LinearLayout e=card();e.addView(tx("No sessions saved yet",13,muted,false));}
        for(java.util.Map.Entry<String,int[]> m:months.entrySet()){
            String label;try{label=new SimpleDateFormat("MMMM yyyy",Locale.getDefault()).format(new SimpleDateFormat("yyyy-MM",Locale.US).parse(m.getKey()));}catch(Exception e){label=m.getKey();}
            LinearLayout r=row();r.setPadding(dp(14),dp(12),dp(10),dp(12));GradientDrawable g=shape(panel,14);g.setStroke(dp(1),line);r.setBackground(g);LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(8);content.addView(r,rp);
            LinearLayout l=col();r.addView(l,new LinearLayout.LayoutParams(0,-2,1));l.addView(tx(label,15,ink,true));l.addView(tx(m.getValue()[0]+(m.getValue()[0]==1?" session · ":" sessions · ")+size(m.getValue()[1]),11,muted,false));
            Button del=button("Delete",raised,critical);del.setTextSize(12);r.addView(del,lp(84,40));String key=m.getKey();String name=label;
            del.setOnClickListener(v->dialog().setTitle("Delete "+name+"?").setMessage("Removes the sessions recorded in "+name+" and that month's daily step history from this phone. Save a backup first if you may want them later. This can't be undone.").setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{deleteMonth(key);toast(name+" deleted");renderPageNow();}).show());
        }
    }
    private void deleteTree(java.io.File f){if(f==null||!f.exists())return;java.io.File[] k=f.listFiles();if(k!=null)for(java.io.File x:k)deleteTree(x);f.delete();}
    private void deleteMonth(String month){
        try{JSONArray h=history(),kept=new JSONArray();for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o!=null&&!o.optString("day","").startsWith(month))kept.put(o);}historyPrefs.edit().putString("sessions",kept.toString()).apply();}catch(Exception ignored){}
        for(String[] pk:new String[][]{{DaySteps.PREFS,"days"},{"kadamm_health","healthDays"}}){
            SharedPreferences p=getSharedPreferences(pk[0],MODE_PRIVATE);
            try{JSONObject d=new JSONObject(p.getString(pk[1],"{}"));java.util.List<String> drop=new java.util.ArrayList<>();for(java.util.Iterator<String> it=d.keys();it.hasNext();){String k=it.next();if(k.startsWith(month)&&!k.equals(todayKey()))drop.add(k);}for(String k:drop)d.remove(k);p.edit().putString(pk[1],d.toString()).apply();}catch(Exception ignored){}
        }
    }
    private static final String[][] BACKUP={{"kadamm_history","sessions"},{"kadamm_routes","routes"},{DaySteps.PREFS,"days"},{"kadamm_health","healthDays"}};
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==PHOTO_REQUEST){savePhoto(uri);return;}
        if(request==EXPORT_REQUEST){
            try{
                JSONObject root=new JSONObject();root.put("app","Kadamm");root.put("version",APP_VERSION);root.put("savedAt",System.currentTimeMillis());
                for(String[] b:BACKUP)root.put(b[0]+"."+b[1],getSharedPreferences(b[0],MODE_PRIVATE).getString(b[1],b[1].equals("sessions")||b[1].equals("routes")?"[]":"{}"));
                JSONObject settings=new JSONObject();for(java.util.Map.Entry<String,?> e:getSharedPreferences("kadamm_settings",MODE_PRIVATE).getAll().entrySet())settings.put(e.getKey(),e.getValue());root.put("settings",settings);
                try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){out.write(root.toString(2).getBytes("UTF-8"));}
                toast("Backup saved");
            }catch(Exception e){toast("Couldn't save the backup");}
        }else if(request==IMPORT_REQUEST){
            try{
                java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();try(java.io.InputStream in=getContentResolver().openInputStream(uri)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)buf.write(b,0,n);}
                JSONObject root=new JSONObject(buf.toString("UTF-8"));if(!"Kadamm".equals(root.optString("app")))throw new IllegalArgumentException();
                dialog().setTitle("Restore this backup?").setMessage("Sessions and routes from the file are added to what's on this phone (nothing is deleted). Daily steps keep the higher number for each day. Settings come from the file.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->{restore(root);toast("Backup restored");renderPageNow();}).show();
            }catch(Exception e){toast("That file isn't a Kadamm backup");}
        }
    }
    private void restore(JSONObject root){
        try{
            for(String[] b:BACKUP){
                SharedPreferences p=getSharedPreferences(b[0],MODE_PRIVATE);String in=root.optString(b[0]+"."+b[1],null);if(in==null)continue;
                if(b[1].equals("sessions")||b[1].equals("routes")){
                    JSONArray mine=new JSONArray(p.getString(b[1],"[]")),theirs=new JSONArray(in);java.util.Set<Long> ids=new java.util.HashSet<>();for(int i=0;i<mine.length();i++)ids.add(mine.optJSONObject(i).optLong("id"));
                    for(int i=0;i<theirs.length();i++){JSONObject o=theirs.optJSONObject(i);if(o!=null&&ids.add(o.optLong("id")))mine.put(o);}
                    p.edit().putString(b[1],mine.toString()).apply();
                }else{
                    JSONObject mine=new JSONObject(p.getString(b[1],"{}")),theirs=new JSONObject(in);for(java.util.Iterator<String> it=theirs.keys();it.hasNext();){String k=it.next();if(theirs.optLong(k)>mine.optLong(k,-1))mine.put(k,theirs.optLong(k));}
                    p.edit().putString(b[1],mine.toString()).apply();
                }
            }
            JSONObject st=root.optJSONObject("settings");if(st!=null){SharedPreferences.Editor e=getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit();for(java.util.Iterator<String> it=st.keys();it.hasNext();){String k=it.next();Object v=st.get(k);if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof String)e.putString(k,(String)v);}e.apply();}
        }catch(Exception ignored){}
    }
    // ---- Permissions ----
    private static final int LOCATION_PERMISSION=103;
    private void renderPermissions(){
        brand("SETTINGS","Permissions","What Kadamm needs to track reliably, and why.");
        boolean loc=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);boolean battery=pm!=null&&pm.isIgnoringBatteryOptimizations(getPackageName());
        boolean hc=getSharedPreferences("kadamm_health",MODE_PRIVATE).getBoolean("healthConnected",false);
        permissionRow(R.drawable.ic_bolt,"Unrestricted battery","Lets Kadamm keep recording when you switch apps or lock the phone. Without it, Samsung's battery saver can pause a session.",battery,"Allow",()->{
            try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}catch(Exception e){openAppSettings();}});
        if(!battery||true){TextView tip=tx("On Samsung, also open Battery settings > Kadamm > Unrestricted, and keep Kadamm out of \"Sleeping apps\".",11,muted,false);LinearLayout.LayoutParams tpp=lp(-1,-2);tpp.topMargin=dp(6);content.addView(tip,tpp);
            TextView open=tx("Open Kadamm's battery settings  →",13,lime,true);open.setGravity(Gravity.CENTER_VERTICAL);open.setFocusable(true);open.setBackground(rippleOn(Color.TRANSPARENT,10));content.addView(open,lp(-1,44));open.setOnClickListener(v->openAppSettings());}
        permissionRow(R.drawable.ic_place,"Precise location","Draws your route and measures outdoor distance. Only used while a session is recording.",loc,"Allow",()->{
            if(shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)||!getSharedPreferences("kadamm_settings",MODE_PRIVATE).getBoolean("locAsked",false)){getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putBoolean("locAsked",true).apply();requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION);}else openAppSettings();});
        permissionRow(R.drawable.ic_run,"Location all the time","Only needed for Auto-tracking, so a walk Kadamm starts by itself still gets its route. Choose \"Allow all the time\".",AutoTrack.backgroundLocation(this)||!AutoTrack.enabled(this),"Allow",()->requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},BG_LOCATION_PERMISSION));
        permissionRow(R.drawable.ic_walk,"Physical activity","Counts your steps all day and on the treadmill, with the phone's step sensor.",DaySteps.permitted(this),"Allow",this::requestStepPermission);
        permissionRow(R.drawable.ic_bell,"Notifications","The live session card, goal alerts, reminders and your daily summary.",notificationsAllowed(),"Allow",()->navigate("Notifications"));
        permissionRow(R.drawable.ic_heart,"Health Connect","Brings in Samsung Health's steps, distance and calories. Optional.",hc,"Connect",()->navigate("Health"));
    }
    private void openAppSettings(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)));}catch(Exception ignored){}}
    private void permissionRow(int res,String title,String why,boolean ok,String action,Runnable fix){
        LinearLayout c=card();LinearLayout h=row();c.addView(h);
        android.widget.ImageView ic=icon(res,darkTheme?Color.rgb(11,11,13):Color.WHITE,8);ic.setBackground(shape(ok?positive:warning,12));h.addView(ic,lp(38,38));
        LinearLayout t=col();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(12);h.addView(t,tp);t.addView(tx(title,15,ink,true));
        TextView pill=tx(ok?"Allowed":"Needed",10,ok?positive:warning,true);pill.setPadding(dp(8),dp(3),dp(8),dp(3));pill.setBackground(shape(blend(panel,ok?positive:warning,.15f),10));h.addView(pill);
        TextView w=tx(why,12,muted,false);LinearLayout.LayoutParams wp=lp(-1,-2);wp.topMargin=dp(8);c.addView(w,wp);
        if(!ok){Button b=button(action,lime,accentOn);LinearLayout.LayoutParams bp=lp(-1,44);bp.topMargin=dp(10);c.addView(b,bp);b.setOnClickListener(v->fix.run());}
    }
    // ---- Daily summary card ----
    private void renderCoach(){
        Coach.Summary sum=Coach.today(this);LinearLayout c=card();LinearLayout h=row();c.addView(h);
        LinearLayout t=col();h.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        t.addView(tx(sum.title.toUpperCase(Locale.getDefault()),10,muted,true));t.addView(tx(sum.headline,18,ink,true));
        TextView tag=tx("✦ Kadamm insight",9,indigo(),true);tag.setPadding(dp(8),dp(3),dp(8),dp(3));tag.setBackground(shape(blend(panel,indigo(),darkTheme?.2f:.1f),10));h.addView(tag);
        if(!sum.insight.isEmpty()){TextView ins=tx(sum.insight,15,ink,false);ins.setLineSpacing(dp(3),1f);LinearLayout.LayoutParams ip=lp(-1,-2);ip.topMargin=dp(10);c.addView(ins,ip);
            if(!reducedMotion()){ins.setAlpha(0f);ins.setTranslationY(dp(6));ins.animate().alpha(1f).translationY(0).setDuration(450).setStartDelay(150).start();}}
        for(Coach.Item it:sum.items){
            int color;int res;
            switch(it.kind){
                case "steps":color=lime;res=R.drawable.ic_walk;break;case "up":color=positive;res=R.drawable.ic_chart;break;case "down":color=orange();res=R.drawable.ic_chart;break;
                case "same":color=muted;res=R.drawable.ic_chart;break;case "sessions":color=purple();res=R.drawable.ic_place;break;case "active":color=green();res=R.drawable.ic_timer;break;
                case "left":color=teal();res=R.drawable.ic_flag;break;case "streak":color=flame();res=R.drawable.ic_bolt;break;default:color=muted;res=R.drawable.ic_heart;
            }
            LinearLayout r=row();LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(10);c.addView(r,rp);
            android.widget.ImageView ic=icon(res,color,6);ic.setBackground(shape(blend(panel,color,darkTheme?.22f:.12f),10));r.addView(ic,lp(30,30));
            TextView line=tx(it.text,14,ink,false);LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,-2,1);llp.leftMargin=dp(12);r.addView(line,llp);
        }
    }
    // ---- Reports ----
    private boolean reportMonth=false,reportCustom=false,reportPicked=false;private int reportOffset=0,reportN=2,reportUnit=Calendar.WEEK_OF_YEAR;private Calendar reportFrom,reportTo;
    /** Totals across sessions in a period: {zone seconds ×5, intensity min, effort, sessions, walk speed·s, walk s, cadence·s, cadence s}. */
    private double[] periodHealth(Progress.Period p,JSONArray h,int limitDays){
        double[] t=new double[13];Calendar end=(Calendar)p.end.clone();if(limitDays>0&&limitDays<p.days){end=(Calendar)p.start.clone();end.add(Calendar.DAY_OF_YEAR,limitDays);}
        String from=DaySteps.key(p.start.getTimeInMillis()),to=DaySteps.key(end.getTimeInMillis());
        for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o==null||Insights.vehicle(o))continue;String d=o.optString("day","");if(d.compareTo(from)<0||d.compareTo(to)>=0)continue;
            SessionMetrics m=SessionMetrics.of(this,o);long[] z=m.hrZoneS!=null?m.hrZoneS:m.zoneS;for(int k=0;k<5;k++)t[k]+=z[k];t[5]+=m.intensityMin;t[6]+=m.effort;t[7]++;
            if(m.walkingSpeed()>0&&!m.estimatedDistance){t[8]+=m.walkingSpeed()*m.movingS;t[9]+=m.movingS;}if(m.cadenceAvg>0){t[10]+=m.cadenceAvg*m.movingS;t[11]+=m.movingS;}}
        t[5]=SessionMetrics.intensityMinutes(this,h,from,to);
        return t;
    }
    /** Reports: intensity minutes against the WHO's 150 a week, fitness and form, walking quality and the zone mix. */
    private void renderReportHealth(Progress.Period per,Progress.Period prevP,JSONArray h,int elapsed){
        double[] now=periodHealth(per,h,0),before=periodHealth(prevP,h,elapsed<per.days?elapsed:0);KadammTheme th=chartTheme();
        TextView head=tx("HEALTH & FITNESS",10,muted,true);head.setLetterSpacing(.08f);LinearLayout.LayoutParams hp=lp(-1,-2);hp.topMargin=dp(20);content.addView(head,hp);
        // Intensity minutes
        double weeks=Math.max(1,elapsed)/7d,target=150*Math.max(1,weeks);
        LinearLayout im=metricCard("INTENSITY MINUTES","","Moderate minutes plus twice the vigorous ones, from your sessions' intensity zones. The World Health Organization recommends at least 150 a week for adults; 300 brings extra benefit. A month's target is 150 for every week that has passed.");
        LinearLayout r=row();LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(10);im.addView(r,rp);
        r.addView(new MetricViews.Ring(this,th,(float)(now[5]/target),MetricViews.ZONE_COLORS[2],String.valueOf(Math.round(now[5])),"of "+Math.round(target)),lp(112,112));
        LinearLayout rc=col();LinearLayout.LayoutParams rcp=new LinearLayout.LayoutParams(0,-2,1);rcp.leftMargin=dp(16);r.addView(rc,rcp);
        boolean met=now[5]>=target;rc.addView(tx(met?"WHO target met":Math.round(target-now[5])+" min to go",17,met?positive:ink,true));
        rc.addView(tx("Sessions and all-day brisk walking",12,muted,false));
        if(before[7]>0){long d=Math.round(now[5]-before[5]);rc.addView(tx((d>=0?"▲ ":"▼ ")+Math.abs(d)+(elapsed<per.days?" min vs the same days before":" min vs the period before"),12,d>=0?positive:critical,true));}
        // Zone mix
        double zt=now[0]+now[1]+now[2]+now[3]+now[4];
        if(zt>0){
            LinearLayout bar=row();bar.setClipToOutline(true);bar.setBackground(shape(raised,6));LinearLayout.LayoutParams bp=lp(-1,12);bp.topMargin=dp(14);im.addView(bar,bp);
            for(int k=0;k<5;k++)if(now[k]>0){View v=new View(this);v.setBackgroundColor(MetricViews.ZONE_COLORS[k]);bar.addView(v,new LinearLayout.LayoutParams(0,-1,(float)(now[k]/zt)));}
            LinearLayout lg=row();LinearLayout.LayoutParams lgp=lp(-1,-2);lgp.topMargin=dp(8);im.addView(lg,lgp);
            for(int k=0;k<5;k++){LinearLayout c=col();lg.addView(c,new LinearLayout.LayoutParams(0,-2,1));c.addView(tx("● "+SessionMetrics.ZONES[k],10,MetricViews.ZONE_COLORS[k],true));c.addView(tx(Math.round(now[k]*100/zt)+"%",11,muted,false));}
        }
        // Fitness and form
        SessionMetrics.Fitness fit=SessionMetrics.fitness(this,h);
        if(!fit.days.isEmpty()){
            LinearLayout fc=metricCard("FITNESS & FORM","","Fitness is a 42-day weighted average of your daily effort and fatigue a 7-day one (the Banister model used by Strava's Fitness & Freshness and TrainingPeaks). Form is fitness minus fatigue: above +5 you're fresh, −10 to +5 is neutral, −30 to −10 is productive training, and below −30 is a lot at once.");
            String verdict=fit.form>5?"Fresh":fit.form>=-10?"Neutral":fit.form>=-30?"Productive":"Overreaching";int vc=fit.form>5?positive:fit.form>=-10?lime:fit.form>=-30?flame():critical;
            statGrid(fc,new String[][]{{"Fitness",String.format(Locale.US,"%.0f",fit.fitness),""},{"Fatigue",String.format(Locale.US,"%.0f",fit.fatigue),""},{"Form",String.format(Locale.US,"%+.0f",fit.form),verdict}},10);
            java.util.List<double[]> pts=new java.util.ArrayList<>();java.util.List<String> keys=new java.util.ArrayList<>(fit.days.keySet());int startI=Math.max(0,keys.size()-60);
            for(int i=startI;i<keys.size();i++)pts.add(new double[]{i-startI,fit.days.get(keys.get(i))[0],0});
            final java.util.List<String> fk=keys.subList(startI,keys.size());
            if(pts.size()>=3){LinearLayout.LayoutParams p=lp(-1,150);p.topMargin=dp(10);fc.addView(new MetricViews.Series(this,th,pts,false,vc,null,-1,v->String.format(Locale.US,"%.0f",v),v->{String k=fk.get((int)Math.max(0,Math.min(fk.size()-1,Math.round(v))));return k.substring(8)+"/"+k.substring(5,7);}),p);}
        }
        // Training load: this week's daily effort against the last four weeks'
        if(!fit.days.isEmpty()){
            java.util.List<double[]> vals=new java.util.ArrayList<>(fit.days.values());double a7=0,a28=0;int n=vals.size();
            for(int i=Math.max(0,n-28);i<n;i++){a28+=vals.get(i)[3];if(i>=n-7)a7+=vals.get(i)[3];}a7/=7;a28/=28;
            if(a28>0){double ratio=a7/a28;
                LinearLayout lc=metricCard("TRAINING LOAD","","Acute:chronic workload ratio: your average daily effort over the last 7 days divided by the last 28. Sports science uses it to pace increases: 0.8–1.3 is the sweet spot, above 1.5 means you've ramped up quickly and injury risk rises, below 0.8 means you're doing less than you're used to.");
                String verdict=ratio>1.5?"Ramping up fast":ratio>1.3?"Building, keep an eye on it":ratio>=.8?"Sweet spot":"Lighter than usual";int vc=ratio>1.5?critical:ratio>1.3?flame():ratio>=.8?positive:lime;
                LinearLayout lr=row();LinearLayout.LayoutParams rp2=lp(-1,-2);rp2.topMargin=dp(8);lc.addView(lr,rp2);
                lr.addView(valueUnit(String.format(Locale.US,"%.2f",ratio),"ratio",30,ink),new LinearLayout.LayoutParams(0,-2,1));lr.addView(tx(verdict,13,vc,true));
                LinearLayout.LayoutParams sp2=lp(-1,40);sp2.topMargin=dp(6);lc.addView(new MetricViews.Scale(this,th,(float)(ratio/2),"0  rest","2.0  overload"),sp2);
                statGrid(lc,new String[][]{{"7-day effort",String.format(Locale.US,"%.0f",a7*7),""},{"28-day weekly avg",String.format(Locale.US,"%.0f",a28*7),""},{"Sweet spot","0.8–1.3",""}},10);
            }
        }
        // Peak-30 cadence and brisk minutes through the period (minute steps from Health Connect)
        JSONObject md=DaySteps.minuteDays(this);
        java.util.List<double[]> p30=new java.util.ArrayList<>();double briskSum=0,sitSum=0;int nd=0;double[][] heat=new double[7][24];int[] heatN=new int[7];
        Calendar dc=(Calendar)per.start.clone();
        for(int i=0;i<per.days;i++,dc.add(Calendar.DAY_OF_YEAR,1)){JSONObject x=md.optJSONObject(DaySteps.key(dc.getTimeInMillis()));if(x==null||!x.has("p30"))continue;
            p30.add(new double[]{i,x.optDouble("p30"),0});briskSum+=x.optDouble("brisk");sitSum+=x.optDouble("sit");nd++;
            int wd=(dc.get(Calendar.DAY_OF_WEEK)+5)%7;JSONArray hh=x.optJSONArray("h");if(hh!=null){for(int k=0;k<24;k++)heat[wd][k]+=hh.optDouble(k);heatN[wd]++;}}
        if(nd>=1){
            LinearLayout pc=metricCard("PEAK-30 CADENCE","(spm)","Each day's average of its 30 highest minutes of steps per minute. Studies of thousands of adults link a higher peak-30 cadence with lower risk of death, heart disease and dementia, independent of total steps. About 100 is moderate intensity; 110 or more is excellent. From Samsung Health's minute steps through Health Connect.");
            double avg=0,best=0;for(double[] q:p30){avg+=q[1];best=Math.max(best,q[1]);}avg/=p30.size();
            statGrid(pc,new String[][]{{"Average",String.format(Locale.US,"%.0f",avg),"spm"},{"Best day",String.format(Locale.US,"%.0f",best),"spm"},{"Brisk min a day",String.format(Locale.US,"%.0f",briskSum/nd),""}},10);
            if(p30.size()>=3){final Progress.Period fp=per;LinearLayout.LayoutParams p=lp(-1,140);p.topMargin=dp(10);pc.addView(new MetricViews.Series(this,th,p30,true,lime,new double[]{80,100,115,130},avg,v->String.format(Locale.US,"%.0f",v),v->{Calendar c2=(Calendar)fp.start.clone();c2.add(Calendar.DAY_OF_YEAR,(int)Math.round(v));return new java.text.SimpleDateFormat("d MMM",Locale.getDefault()).format(c2.getTime());}),p);}
            TextView sit=tx("",12,sitSum/nd>120?warning:muted,false);
            sit.setText(String.format(Locale.US,"Longest sitting stretch: %dh %02d a day on average",Math.round(sitSum/nd)/60,Math.round(sitSum/nd)%60));LinearLayout.LayoutParams sp3=lp(-1,-2);sp3.topMargin=dp(8);pc.addView(sit,sp3);
            // When you move: weekday × hour
            for(int wr=0;wr<7;wr++)if(heatN[wr]>0)for(int k=0;k<24;k++)heat[wr][k]/=heatN[wr];
            LinearLayout hm=metricCard("WHEN YOU MOVE","","Average steps in each hour, by weekday, over this period. Darker means more steps. From minute-level steps in Health Connect.");
            int bh=0,bd=0;for(int wr=0;wr<7;wr++)for(int k=0;k<24;k++)if(heat[wr][k]>heat[bd][bh]){bd=wr;bh=k;}
            hm.addView(tx(String.format(Locale.US,"Most active: %s around %s",new String[]{"Mondays","Tuesdays","Wednesdays","Thursdays","Fridays","Saturdays","Sundays"}[bd],bh==0?"midnight":bh<12?bh+" am":bh==12?"noon":(bh-12)+" pm"),12,muted,false));
            LinearLayout.LayoutParams hp2=lp(-1,150);hp2.topMargin=dp(10);hm.addView(new MetricViews.Heatmap(this,th,heat,lime),hp2);
        }
        // Pace curve: best pace held for each duration, all-time against the last 90 days
        java.util.Map<Integer,double[]> allBest=new java.util.TreeMap<>(),recentBest=new java.util.TreeMap<>();String since=DaySteps.key(System.currentTimeMillis()-90L*86400000L);
        for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o==null)continue;SessionMetrics m=SessionMetrics.of(this,o);
            for(java.util.Map.Entry<Integer,Double> e:m.bestByTime.entrySet()){double[] a=allBest.get(e.getKey());if(a==null||e.getValue()<a[0])allBest.put(e.getKey(),new double[]{e.getValue()});
                if(o.optString("day","").compareTo(since)>=0){double[] b=recentBest.get(e.getKey());if(b==null||e.getValue()<b[0])recentBest.put(e.getKey(),new double[]{e.getValue()});}}}
        if(allBest.size()>=3){
            LinearLayout cv=metricCard("PACE CURVE","","Your fastest pace held for 1, 5, 10, 20, 30 and 60 minutes, from the GPS of every outdoor session: the walking version of a power curve. The bars compare your all-time best with the last 90 days.");
            for(int dur:SessionMetrics.CURVE){double[] a=allBest.get(dur);if(a==null)continue;double[] rec=recentBest.get(dur);
                LinearLayout crow=row();crow.setPadding(0,dp(8),0,dp(2));cv.addView(crow,lp(-1,-2));
                crow.addView(tx(dur<3600?dur/60+" min":"1 hour",13,ink,true),lp(64,-2));
                LinearLayout bars=col();crow.addView(bars,new LinearLayout.LayoutParams(0,-2,1));
                double slow=1800,fast=400;float fa=(float)Math.max(.08,Math.min(1,(slow-a[0])/(slow-fast))),fr=rec==null?0:(float)Math.max(.08,Math.min(1,(slow-rec[0])/(slow-fast)));
                View ab=new View(this);ab.setBackground(shape(MetricViews.alpha(lime,120),4));LinearLayout.LayoutParams abp=new LinearLayout.LayoutParams(0,dp(7));bars.addView(ab,abp);
                View rb=new View(this);rb.setBackground(shape(lime,4));LinearLayout.LayoutParams rbp=new LinearLayout.LayoutParams(0,dp(7));rbp.topMargin=dp(3);bars.addView(rb,rbp);
                bars.post(()->{abp.width=(int)(bars.getWidth()*fa);ab.setLayoutParams(abp);rbp.width=(int)(bars.getWidth()*fr);rb.setLayoutParams(rbp);});
                LinearLayout vals=col();vals.setGravity(Gravity.END);crow.addView(vals,lp(84,-2));vals.addView(tx(paceText(a[0]),12,ink,true));vals.addView(tx(rec==null?"—":paceText(rec[0]),11,lime,true));
            }
            android.text.SpannableStringBuilder b=new android.text.SpannableStringBuilder();append(b,"■ ",MetricViews.alpha(lime,120));b.append("All time    ");append(b,"■ ",lime);b.append("Last 90 days");TextView lg=tx("",11,muted,false);lg.setText(b);LinearLayout.LayoutParams lgp=lp(-1,-2);lgp.topMargin=dp(8);cv.addView(lg,lgp);
        }
        // Walking quality
        if(now[9]>0||now[11]>0){
            LinearLayout wq=metricCard("WALKING QUALITY","","Average walking speed (outdoor walks, over moving time) and cadence across this period's sessions, each weighted by how long the session was, compared with the period before. Walking speed of 1.0 m/s or more is linked with healthier ageing.");
            String sp=now[9]>0?String.format(Locale.US,"%.2f",now[8]/now[9]):"—",cd=now[11]>0?String.valueOf(Math.round(now[10]/now[11])):"—";
            statGrid(wq,new String[][]{{"Walking speed",sp,now[9]>0?"m/s":""},{"Cadence",cd,now[11]>0?"spm":""},{"Effort per session",now[7]>0?String.valueOf(Math.round(now[6]/now[7])):"—",""}},10);
            StringBuilder cmp=new StringBuilder();
            if(now[9]>0&&before[9]>0){double d=now[8]/now[9]-before[8]/before[9];cmp.append(String.format(Locale.US,"Speed %s%.2f m/s",d>=0?"+":"−",Math.abs(d)));}
            if(now[11]>0&&before[11]>0){double d=now[10]/now[11]-before[10]/before[11];if(cmp.length()>0)cmp.append(" · ");cmp.append(String.format(Locale.US,"cadence %s%d spm",d>=0?"+":"−",Math.abs(Math.round(d))));}
            if(cmp.length()>0){TextView c=tx(cmp+" vs the period before",12,muted,false);LinearLayout.LayoutParams cp=lp(-1,-2);cp.topMargin=dp(10);wq.addView(c,cp);}
        }
    }
    private void renderReports(){
        brand("YOUR PROGRESS","Reports","How your weeks and months add up.");
        LinearLayout seg=row();seg.setPadding(dp(4),dp(4),dp(4),dp(4));seg.setBackground(shape(raised,14));content.addView(seg,lp(-1,48));
        for(String m:new String[]{"Week","Month","Custom"}){boolean on=m.equals("Custom")?reportCustom:!reportCustom&&m.equals("Month")==reportMonth;TextView t=tx(m,14,on?ink:muted,true);t.setGravity(Gravity.CENTER);t.setFocusable(true);if(on)t.setBackground(shape(panel,11));seg.addView(t,new LinearLayout.LayoutParams(0,-1,1));
            t.setOnClickListener(v->{reportCustom=m.equals("Custom");reportMonth=m.equals("Month");reportOffset=0;renderPageNow();});}
        if(reportCustom)renderReportRange();
        Progress.Period per,prevP;
        if(reportCustom&&reportPicked&&reportFrom!=null&&reportTo!=null){Calendar end=(Calendar)reportTo.clone();end.add(Calendar.DAY_OF_YEAR,1);per=Progress.between(reportFrom,end,"chosen days");prevP=Progress.before(per);}
        else if(reportCustom){per=Progress.last(reportN,reportUnit,reportOffset);prevP=Progress.before(per);}
        else{per=reportMonth?Progress.month(reportOffset):Progress.week(reportOffset);prevP=reportMonth?Progress.month(reportOffset-1):Progress.week(reportOffset-1);}
        JSONArray h=history();java.util.Map<String,Long> totals=DaySteps.allDayTotals(this);int goal=dailyGoal();
        Progress.Summary s=Progress.summarize(per,h,totals,goal),prev=Progress.summarize(prevP,h,totals,goal);
        LinearLayout nav=row();LinearLayout.LayoutParams np=lp(-1,48);np.topMargin=dp(10);content.addView(nav,np);if(reportCustom&&reportPicked)nav.setVisibility(View.GONE);
        android.widget.ImageView back=icon(R.drawable.ic_close,ink,12);TextView prevB=tx("‹",22,ink,false),nextB=tx("›",22,reportOffset<0?ink:muted,false);
        for(TextView b:new TextView[]{prevB,nextB}){b.setGravity(Gravity.CENTER);b.setBackground(rippleOn(raised,22));}
        nav.addView(prevB,lp(44,44));TextView lab=tx(per.label,14,ink,true);lab.setGravity(Gravity.CENTER);nav.addView(lab,new LinearLayout.LayoutParams(0,-2,1));nav.addView(nextB,lp(44,44));
        prevB.setOnClickListener(v->{reportOffset--;renderPageNow();});nextB.setOnClickListener(v->{if(reportOffset<0){reportOffset++;renderPageNow();}});
        LinearLayout hero=card();hero.addView(tx("STEPS",10,muted,true));
        LinearLayout big=row();hero.addView(big);big.addView(tx(String.format(Locale.US,"%,d",s.steps),32,ink,true));
        // A period still in progress is compared with the same number of days at the start of the one before.
        long prevSteps=prev.steps;if(s.future>0){prevSteps=0;for(int i=0;i<Math.min(s.elapsed,prev.daily.length);i++)prevSteps+=prev.daily[i];}
        if(prevSteps>0){long pct=Math.round((s.steps-prevSteps)*100.0/prevSteps);TextView ch=tx(String.format(Locale.US,"  %s %d%%",pct>=0?"▲":"▼",Math.abs(pct)),14,pct>=0?positive:critical,true);big.addView(ch);}
        String before=reportCustom?per.days+" days":reportMonth?"month":"week";
        hero.addView(tx(prevSteps<=0?"Nothing to compare with yet":s.future>0?String.format(Locale.US,"vs the first %d %s of the %s before (%,d) · touch a bar for its steps",s.elapsed,s.elapsed==1?"day":"days",before,prevSteps):String.format(Locale.US,"vs the %s before (%,d) · touch a bar for its steps",before,prevSteps),11,muted,false));
        long[] bars=s.daily;String[] labels=s.labels,names=s.names;int barGoal=goal,future=s.future;
        if(bars.length>62){int wk=(bars.length+6)/7;long[] x=new long[wk];String[] lb=new String[wk],nm=new String[wk];for(int i=0;i<bars.length;i++)x[i/7]+=bars[i];for(int i=0;i<wk;i++){lb[i]="W"+(i+1);nm[i]="Week of "+s.names[i*7];}bars=x;labels=lb;names=nm;barGoal=goal*7;future=s.future/7;hero.addView(tx("Weekly totals · dashed line is 7 × your daily goal",10,muted,false));}
        LinearLayout.LayoutParams cp=lp(-1,bars.length>7?180:170);cp.topMargin=dp(8);hero.addView(new BarChartView(this,chartTheme(),bars,labels,names,barGoal,future),cp);
        KadammTheme th=chartTheme();int days=Math.max(1,s.elapsed);double walkedKm=DaySteps.stepKm(this,s.steps);long avg=s.steps/days;
        LinearLayout r1=glanceRow();
        glance(r1,"DISTANCE",String.format(Locale.US,"%.1f",Math.max(walkedKm,s.km)),"km",lime,"↗",null,String.format(Locale.US,"≈ walked, from steps · %.1f km in sessions",s.km));
        glance(r1,"ACTIVE TIME",String.format(Locale.US,"%dh %02d",s.activeMs/3600000,(s.activeMs/60000)%60),"",green(),"◷",null,"recorded in sessions");
        LinearLayout r2=glanceRow();
        glance(r2,"GOAL DAYS",String.valueOf(s.goalDays),"/ "+s.elapsed,flame(),"★",new MiniChartView(this,th,MiniChartView.PROGRESS,flame(),0).progress(s.goalDays/(float)days),s.future>0?s.future+(s.future==1?" day still to come":" days still to come"):String.format(Locale.US,"%d%% of days at your goal",Math.round(s.goalDays*100f/days)));
        glance(r2,"SESSIONS",String.valueOf(s.sessions),"",purple(),"▶",null,s.sessions==0?"no walks or runs recorded":String.format(Locale.US,"%.1f km · %d min",s.km,s.activeMs/60000));
        LinearLayout r3=glanceRow();
        glance(r3,"DAILY AVG",String.format(Locale.US,"%,d",avg),"steps",indigo(),"≈",new MiniChartView(this,th,MiniChartView.PROGRESS,indigo(),0).progress(avg/(float)Math.max(1,goal)),String.format(Locale.US,"over %d %s · %d%% of goal",days,days==1?"day":"days",Math.round(avg*100f/Math.max(1,goal))));
        glance(r3,"BEST DAY",s.bestSteps==0?"—":String.format(Locale.US,"%,d",s.bestSteps),"",teal(),"◉",null,s.bestDay.isEmpty()?"no steps yet":s.bestDay);
        renderReportHealth(per,prevP,h,s.elapsed);
        Button share=button("Share this report",lime,accentOn);share.setTextSize(15);LinearLayout.LayoutParams sp=lp(-1,50);sp.topMargin=dp(14);content.addView(share,sp);
        share.setOnClickListener(v->showSharePreview(new android.graphics.Bitmap[]{ReportCard.render(this,per,s,prev,goal,true),ReportCard.render(this,per,s,prev,goal,false)},String.format(Locale.US,"%s in Kadamm: %,d steps, %.1f km, %d goal days",per.label,s.steps,s.km,s.goalDays)));
    }
    /** Custom range controls: "last N days/weeks/months", or two dates. */
    private void renderReportRange(){
        LinearLayout c=card();
        LinearLayout mode=row();c.addView(mode,lp(-1,36));
        Button lastB=button("Last…",reportPicked?raised:lime,reportPicked?ink:accentOn),pickB=button("Pick dates",reportPicked?lime:raised,reportPicked?accentOn:ink);
        lastB.setTextSize(13);pickB.setTextSize(13);mode.addView(lastB,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-1,1);pp.leftMargin=dp(8);mode.addView(pickB,pp);
        lastB.setOnClickListener(v->{reportPicked=false;reportOffset=0;renderPageNow();});
        pickB.setOnClickListener(v->{reportPicked=true;if(reportFrom==null){reportTo=Calendar.getInstance();reportFrom=Calendar.getInstance();reportFrom.add(Calendar.DAY_OF_YEAR,-13);}renderPageNow();});
        if(!reportPicked){
            LinearLayout nr=row();LinearLayout.LayoutParams nrp=lp(-1,52);nrp.topMargin=dp(12);c.addView(nr,nrp);
            Button minus=button("−",raised,ink),plus=button("+",raised,ink);minus.setTextSize(20);plus.setTextSize(20);nr.addView(minus,lp(52,48));
            String unitName=reportUnit==Calendar.MONTH?(reportN==1?"month":"months"):reportUnit==Calendar.WEEK_OF_YEAR?(reportN==1?"week":"weeks"):(reportN==1?"day":"days");
            TextView val=tx("Last "+reportN+" "+unitName,20,lime,true);val.setGravity(Gravity.CENTER);nr.addView(val,new LinearLayout.LayoutParams(0,-2,1));nr.addView(plus,lp(52,48));
            int max=reportUnit==Calendar.MONTH?12:reportUnit==Calendar.WEEK_OF_YEAR?26:90;
            minus.setOnClickListener(v->{reportN=Math.max(1,reportN-1);reportOffset=0;renderPageNow();});plus.setOnClickListener(v->{reportN=Math.min(max,reportN+1);reportOffset=0;renderPageNow();});
            LinearLayout units=row();LinearLayout.LayoutParams up=lp(-1,36);up.topMargin=dp(8);c.addView(units,up);
            int[] codes={Calendar.DAY_OF_YEAR,Calendar.WEEK_OF_YEAR,Calendar.MONTH};String[] names={"Days","Weeks","Months"};
            for(int i=0;i<3;i++){int code=codes[i];boolean on=code==reportUnit;Button b=button(names[i],on?lime:raised,on?accentOn:ink);b.setTextSize(12);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)bp.leftMargin=dp(6);units.addView(b,bp);
                b.setOnClickListener(v->{reportUnit=code;reportN=Math.min(reportN,code==Calendar.MONTH?12:code==Calendar.WEEK_OF_YEAR?26:90);reportOffset=0;renderPageNow();});}
        }else{
            LinearLayout dates=row();LinearLayout.LayoutParams dp2=lp(-1,44);dp2.topMargin=dp(12);c.addView(dates,dp2);
            SimpleDateFormat f=new SimpleDateFormat("EEE d MMM yyyy",Locale.getDefault());
            for(int i=0;i<2;i++){boolean from=i==0;Calendar cal=from?reportFrom:reportTo;Button b=button((from?"From ":"To ")+f.format(cal.getTime()),raised,ink);b.setTextSize(12);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)bp.leftMargin=dp(8);dates.addView(b,bp);
                b.setOnClickListener(v->{android.app.DatePickerDialog d=new android.app.DatePickerDialog(this,darkTheme?android.R.style.Theme_DeviceDefault_Dialog_Alert:android.R.style.Theme_DeviceDefault_Light_Dialog_Alert,(dp3,y,m,dd)->{Calendar picked=Calendar.getInstance();picked.set(y,m,dd);
                    if(from){reportFrom=picked;if(reportTo.before(reportFrom))reportTo=(Calendar)picked.clone();}else{reportTo=picked;if(reportTo.before(reportFrom))reportFrom=(Calendar)picked.clone();}renderPageNow();},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH));
                    d.getDatePicker().setMaxDate(System.currentTimeMillis());d.show();});}
        }
    }
    // ---- Achievements: badges and challenges ----
    private void renderBadgeBanner(JSONArray hist){
        java.util.List<Progress.Badge> fresh=Progress.unseen(this,Progress.badges(SessionMetrics.ready()?this:null,hist,dayTotals,dailyGoal()));if(fresh.isEmpty())return;
        LinearLayout c=card();GradientDrawable g=shape(blend(panel,flame(),darkTheme?.18f:.10f),16);g.setStroke(dp(1),blend(line,flame(),.4f));c.setBackground(g);
        LinearLayout r=row();c.addView(r);android.widget.ImageView ic=icon(R.drawable.ic_trophy,darkTheme?Color.rgb(11,11,13):Color.WHITE,8);ic.setBackground(shape(flame(),12));r.addView(ic,lp(40,40));
        LinearLayout t=col();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(12);r.addView(t,tp);
        t.addView(tx(fresh.size()==1?"New badge: "+fresh.get(0).name:fresh.size()+" new badges",15,ink,true));t.addView(tx("Tap to see your achievements",11,muted,false));
        c.setFocusable(true);c.setOnClickListener(v->navigate("Achievements"));
    }
    private void renderAchievements(){
        brand("YOUR PROGRESS","Achievements","Earn medals in four tiers, from easy to extreme.");
        JSONArray h=history();java.util.Map<String,Long> totals=DaySteps.allDayTotals(this);int goal=dailyGoal();KadammTheme th=chartTheme();
        java.util.List<Progress.Badge> all=Progress.badges(this,h,totals,goal);boolean celebrate=!Progress.unseen(this,all).isEmpty();
        int earned=0;int[] perTier=new int[4],earnedTier=new int[4];for(Progress.Badge b:all){perTier[b.tier]++;if(b.earned()){earned++;earnedTier[b.tier]++;}}
        // Hero: the highest-tier medal earned, the count, and a tier-coloured progress strip.
        Progress.Badge top=null;for(Progress.Badge b:all)if(b.earned()&&(top==null||b.tier>=top.tier))top=b;
        LinearLayout hero=card();int ht=top==null?0:top.tier;
        GradientDrawable hg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,MetricViews.TIER[ht][1],darkTheme?.55f:.25f),blend(panel,violet,darkTheme?.25f:.12f)});hg.setCornerRadius(dp(22));hg.setStroke(dp(1),blend(line,MetricViews.TIER[ht][0],.4f));hero.setBackground(hg);
        TextView eye=tx("REWARD YOUR PROGRESS",10,darkTheme?MetricViews.TIER[ht][0]:MetricViews.TIER[ht][1],true);eye.setLetterSpacing(.1f);hero.addView(eye);
        LinearLayout hr=row();LinearLayout.LayoutParams hrp=lp(-1,-2);hrp.topMargin=dp(8);hero.addView(hr,hrp);
        hr.addView(new MetricViews.Medal(this,th,ht,top==null?R.drawable.ic_trophy:top.icon,top!=null,0),lp(96,96));
        LinearLayout hc=col();LinearLayout.LayoutParams hcp=new LinearLayout.LayoutParams(0,-2,1);hcp.leftMargin=dp(16);hr.addView(hc,hcp);
        hc.addView(valueUnit(String.valueOf(earned),"/ "+all.size(),40,ink));
        hc.addView(tx(top==null?"Your first medal is one walk away":"Latest top tier: "+top.name,12,muted,false));
        Progress.Badge next=nearest(all);hc.addView(tx("Next: "+next.name+" · "+Math.round(next.fraction()*100)+"%",12,ink,true));
        LinearLayout strip=row();strip.setClipToOutline(true);strip.setBackground(shape(raised,5));LinearLayout.LayoutParams stp=lp(-1,10);stp.topMargin=dp(16);hero.addView(strip,stp);
        for(int t=0;t<4;t++){View seg=new View(this);seg.setBackgroundColor(MetricViews.TIER[t][0]);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-1,earnedTier[t]);if(earnedTier[t]>0)strip.addView(seg,sp);}
        View rest=new View(this);strip.addView(rest,new LinearLayout.LayoutParams(0,-1,all.size()-earned));
        LinearLayout legend=row();LinearLayout.LayoutParams lgp=lp(-1,-2);lgp.topMargin=dp(8);hero.addView(legend,lgp);
        for(int t=0;t<4;t++){LinearLayout cl=col();legend.addView(cl,new LinearLayout.LayoutParams(0,-2,1));cl.addView(tx(earnedTier[t]+"/"+perTier[t],14,ink,true));TextView n=tx(MetricViews.TIER_NAMES[t],10,darkTheme?MetricViews.TIER[t][0]:MetricViews.TIER[t][1],true);cl.addView(n);}
        // Current streak with a flame.
        int[] st=DaySteps.streaks(totals,goal);Long tv=totals.get(todayKey());long todaySteps=tv==null?0:tv;
        LinearLayout sc=card();LinearLayout sr=row();sc.addView(sr,lp(-1,-2));
        LinearLayout sl=col();sr.addView(sl,new LinearLayout.LayoutParams(0,-2,1));
        TextView sh=tx("CURRENT STREAK",10,flame(),true);sh.setLetterSpacing(.1f);sl.addView(sh);
        sl.addView(valueUnit(String.valueOf(st[0]),st[0]==1?"day":"days",34,ink));
        sl.addView(tx(st[0]>=st[1]&&st[0]>0?"Your longest ever":"Best "+st[1]+" days · "+Math.max(0,st[1]-st[0])+" to beat it",12,muted,false));
        sr.addView(new MetricViews.Flame(this,th,st[0]),lp(74,84));
        ProgressBar gp=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);gp.setMax(1000);gp.setProgress((int)Math.min(1000,todaySteps*1000/Math.max(1,goal)));gp.setProgressTintList(android.content.res.ColorStateList.valueOf(flame()));gp.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(raised));
        LinearLayout.LayoutParams gpp=lp(-1,8);gpp.topMargin=dp(12);sc.addView(gp,gpp);
        sc.addView(tx(todaySteps>=goal?"Today's goal done, streak extended":String.format(Locale.US,"%,d more steps today to keep it going",goal-todaySteps),11,todaySteps>=goal?positive:muted,todaySteps>=goal));
        // In progress
        java.util.List<Progress.Badge> close=new java.util.ArrayList<>();for(Progress.Badge b:all)if(!b.earned()&&b.fraction()>0)close.add(b);
        close.sort((x,y)->Float.compare(y.fraction(),x.fraction()));
        if(!close.isEmpty()){shelfTitle("IN PROGRESS",null);LinearLayout s1=shelf();for(int i=0;i<Math.min(8,close.size());i++)medalCell(s1,close.get(i),true);}
        // Personal bests as gold medals: tap one to open its session.
        java.util.List<Insights.Record> recs=Insights.records(h,totals);
        if(!recs.isEmpty()){shelfTitle("PERSONAL BESTS",null);LinearLayout s2=shelf();
            for(Insights.Record r:recs){LinearLayout cell=col();cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setPadding(dp(10),dp(14),dp(10),dp(12));cell.setBackground(panelShape(18));LinearLayout.LayoutParams clp=lp(136,-2);clp.rightMargin=dp(8);s2.addView(cell,clp);
                int icon=r.label.contains("km")||r.label.contains("Fastest")?R.drawable.ic_bolt:r.label.contains("distance")?R.drawable.ic_ruler:r.label.contains("session")?R.drawable.ic_timer:r.label.contains("streak")?R.drawable.ic_flame:R.drawable.ic_walk;
                cell.addView(new MetricViews.Medal(this,th,2,icon,true,0),lp(64,64));
                TextView v=tx(r.value,15,ink,true);v.setGravity(Gravity.CENTER);v.setMaxLines(1);LinearLayout.LayoutParams vp=lp(-1,-2);vp.topMargin=dp(8);cell.addView(v,vp);
                TextView l=tx(r.label,10,darkTheme?MetricViews.TIER[2][0]:MetricViews.TIER[2][1],true);l.setGravity(Gravity.CENTER);l.setMaxLines(2);cell.addView(l,lp(-1,-2));
                TextView d=tx(r.date,10,muted,false);d.setGravity(Gravity.CENTER);d.setMaxLines(1);cell.addView(d,lp(-1,-2));
                if(r.sessionId>0){JSONObject go=null;for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o!=null&&o.optLong("id")==r.sessionId)go=o;}final JSONObject g2=go;if(g2!=null)cell.setOnClickListener(v2->{lastSession=g2;page="Detail";renderPage();});}}
        }
        // Each tier as a shelf, earned medals first.
        for(int t=0;t<4;t++){
            shelfTitle(MetricViews.TIER_NAMES[t].toUpperCase(Locale.US)+"  ·  "+earnedTier[t]+" of "+perTier[t],MetricViews.TIER[t]);
            LinearLayout s3=shelf();java.util.List<Progress.Badge> tierList=new java.util.ArrayList<>();for(Progress.Badge b:all)if(b.tier==t)tierList.add(b);
            tierList.sort((x,y)->x.earned()==y.earned()?Float.compare(y.fraction(),x.fraction()):x.earned()?-1:1);
            for(Progress.Badge b:tierList)medalCell(s3,b,false);
        }
        TextView chT=tx("CHALLENGES",10,muted,true);chT.setLetterSpacing(.08f);LinearLayout.LayoutParams ctp=lp(-1,-2);ctp.topMargin=dp(24);content.addView(chT,ctp);
        for(Progress.Challenge ch:Progress.challenges(this,h,totals,goal)){
            LinearLayout c=card();LinearLayout hd=row();c.addView(hd);LinearLayout l=col();hd.addView(l,new LinearLayout.LayoutParams(0,-2,1));
            l.addView(tx(ch.title,16,ink,true));l.addView(tx(ch.detail,11,muted,false));
            if(ch.done()){TextView done=tx("COMPLETE",9,positive,true);done.setPadding(dp(8),dp(3),dp(8),dp(3));done.setBackground(shape(blend(panel,positive,.15f),10));hd.addView(done);}
            String val=ch.distance?String.format(Locale.US,"%.1f of %.0f km",ch.value,ch.target):String.format(Locale.US,"%,d of %,d",(long)ch.value,(long)ch.target);
            LinearLayout vr=row();LinearLayout.LayoutParams vp=lp(-1,-2);vp.topMargin=dp(10);c.addView(vr,vp);vr.addView(tx(val,18,ch.done()?positive:ink,true),new LinearLayout.LayoutParams(0,-2,1));
            vr.addView(tx(ch.done()?"Done":ch.daysLeft+(ch.daysLeft==1?" day left":" days left"),11,muted,false));
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)Math.min(1000,ch.value*1000/Math.max(1,ch.target)));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ch.done()?positive:lime));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(raised));
            LinearLayout.LayoutParams bp=lp(-1,8);bp.topMargin=dp(6);c.addView(bar,bp);
            int[] presets=ch.key.equals("chMonthSteps")?new int[]{150000,200000,250000,300000,400000}:ch.key.equals("chMonthKm")?new int[]{20,50,100,150}:new int[]{3,4,5,6,7};
            choiceChips(c,"Target",presets,(int)ch.target,ch.key,v->ch.key.equals("chMonthSteps")?(v/1000)+"k":ch.key.equals("chMonthKm")?v+" km":v+" days");
        }
        if(celebrate&&!reducedMotion()){ConfettiView confetti=new ConfettiView(this);root.addView(confetti,new FrameLayout.LayoutParams(-1,-1));confetti.play();}
        Progress.markSeen(this,all);
    }
    private void shelfTitle(String title,int[] tier){
        LinearLayout r=row();LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(22);content.addView(r,rp);
        if(tier!=null){View dot=new View(this);GradientDrawable dg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,tier);dg.setShape(GradientDrawable.OVAL);dot.setBackground(dg);LinearLayout.LayoutParams dp2=lp(12,12);dp2.rightMargin=dp(8);r.addView(dot,dp2);}
        TextView t=tx(title,11,ink,true);t.setLetterSpacing(.08f);r.addView(t);
    }
    private LinearLayout shelf(){
        android.widget.HorizontalScrollView hs=new android.widget.HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams hp=lp(-1,-2);hp.topMargin=dp(10);content.addView(hs,hp);LinearLayout s=row();s.setGravity(Gravity.TOP);hs.addView(s);return s;
    }
    /** One badge on a shelf: the medal, name, and earned or progress; tap for details. */
    private void medalCell(LinearLayout shelf,Progress.Badge b,boolean ring){
        boolean on=b.earned();LinearLayout cell=col();cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setPadding(dp(8),dp(14),dp(8),dp(12));
        GradientDrawable cg=shape(on?blend(panel,MetricViews.TIER[b.tier][0],darkTheme?.14f:.12f):panel,18);cg.setStroke(dp(1),on?blend(line,MetricViews.TIER[b.tier][0],.5f):line);cell.setBackground(cg);
        LinearLayout.LayoutParams clp=lp(112,-2);clp.rightMargin=dp(8);shelf.addView(cell,clp);
        cell.addView(new MetricViews.Medal(this,chartTheme(),b.tier,b.icon,on,b.fraction()),lp(66,66));
        TextView n=tx(b.name,12,on?ink:muted,true);n.setGravity(Gravity.CENTER);n.setMaxLines(1);n.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(8);cell.addView(n,np);
        TextView d=tx(on?"Earned":Math.round(b.fraction()*100)+"%",10,on?(darkTheme?MetricViews.TIER[b.tier][0]:MetricViews.TIER[b.tier][1]):muted,on);d.setGravity(Gravity.CENTER);cell.addView(d,lp(-1,-2));
        if(!on){ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(1000);pb.setProgress((int)(b.fraction()*1000));pb.setProgressTintList(android.content.res.ColorStateList.valueOf(MetricViews.TIER[b.tier][0]));pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(raised));LinearLayout.LayoutParams pp=lp(-1,4);pp.topMargin=dp(6);cell.addView(pb,pp);}
        cell.setContentDescription(b.name+", "+MetricViews.TIER_NAMES[b.tier]+", "+(on?"earned":"not yet earned, "+b.progressText())+". "+b.desc);
        cell.setOnClickListener(v->badgeDetail(b));
    }
    private Progress.Badge nearest(java.util.List<Progress.Badge> all){Progress.Badge best=null;for(Progress.Badge b:all)if(!b.earned()&&(best==null||b.fraction()>best.fraction()||b.fraction()==best.fraction()&&b.tier<best.tier))best=b;return best==null?all.get(0):best;}
    private void badgeDetail(Progress.Badge b){
        LinearLayout box=col();box.setGravity(Gravity.CENTER_HORIZONTAL);box.setPadding(dp(24),dp(20),dp(24),dp(4));
        box.addView(new MetricViews.Medal(this,chartTheme(),b.tier,b.icon,b.earned(),b.fraction()),lp(110,110));
        TextView tier=tx(MetricViews.TIER_NAMES[b.tier].toUpperCase(Locale.US)+" BADGE",10,darkTheme?MetricViews.TIER[b.tier][0]:MetricViews.TIER[b.tier][1],true);tier.setLetterSpacing(.1f);LinearLayout.LayoutParams tp=lp(-2,-2);tp.topMargin=dp(14);box.addView(tier,tp);
        TextView n=tx(b.name,22,ink,true);n.setGravity(Gravity.CENTER);box.addView(n);
        TextView d=tx(b.desc,14,muted,false);d.setGravity(Gravity.CENTER);box.addView(d);
        TextView p=tx(b.earned()?"Earned ✓":b.progressText(),15,b.earned()?positive:ink,true);LinearLayout.LayoutParams pp=lp(-2,-2);pp.topMargin=dp(12);box.addView(p,pp);
        dialog().setView(box).setPositiveButton("Close",null).show();
    }
    @Override public void onBackPressed(){
        if(authLayer!=null){super.onBackPressed();return;}
        if(mapLayer!=null&&mapLayer.getVisibility()==View.VISIBLE){closeMapView();return;}
        if(liveLayer!=null&&liveLayer.getVisibility()==View.VISIBLE){closeLive();return;}
        if(drawerLayer!=null&&drawerLayer.getVisibility()==View.VISIBLE){closeDrawer();return;}
        if(!"Today".equals(page)){if(tabIndex(page)>=0)navigate("Today");else goBack();return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){handler.removeCallbacks(refresh);super.onDestroy();}
}
