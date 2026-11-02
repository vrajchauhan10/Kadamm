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
    private static final String APP_VERSION="8.1";private int dailyGoal(){return getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("stepGoal",10000);}
    private int bg,panel,raised,lime,violet,accentOn,ink,muted,line,focus,positive,warning,critical; private boolean darkTheme=true,previousMoving=false;
    private SharedPreferences prefs,historyPrefs; private FrameLayout root,drawerLayer; private LinearLayout shell,content,nav,drawer,navToday,navHistory,navProfile; private View drawerScrim; private ScrollView scroller; private TextView timer,distance,pace,steps,status,start,todayCount,todayDistance,dailyStepValue,dailyGoalMessage,accuracyLabel,activityModeLabel,todayActiveTime,todayWalkDistance,todayRunDistance,todayCaloriesValue;private ProgressBar dailyGoalProgress,dailyDistanceProgress; private TextView dailyDistanceValue;private View dailyGoalCard; private RouteView route; private Handler handler=new Handler(Looper.getMainLooper()); private String page="Today",activityType="Walk",activityRange="7D"; private Calendar shownMonth=Calendar.getInstance(),selectedDate=Calendar.getInstance(); private JSONObject lastSession; private float gestureDownX,gestureDownY;private long gestureDownAt;
    private static final int STEP_PERMISSION=101;private ProgressRingView ring;private TextView heroPercent,heroRemaining,heroDistance,heroSource,heroStart,heroNote;private View trackCard;private LinearLayout trackStats,trackEnd;private TextView heroSessions;private TextView liveSpeed,liveElev,liveKcal;private Map<String,Long> dayTotals;private double idleLat,idleLon;private float idleAcc;private boolean hasIdle,stepListening,stepSetupShown,resumed,syncing;private long lastHealthRefresh;
    private final SensorEventListener stepListener=new SensorEventListener(){@Override public void onSensorChanged(SensorEvent e){DaySteps.ingest(MainActivity.this,e.values[0]);}@Override public void onAccuracyChanged(Sensor sensor,int accuracy){}};
    private final Runnable refresh=()->{updateLive();handler.postDelayed(this.refresh,1000);};
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("stride_state",MODE_PRIVATE);historyPrefs=getSharedPreferences("kadamm_history",MODE_PRIVATE);if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.os.StrictMode.setThreadPolicy(new android.os.StrictMode.ThreadPolicy.Builder().detectNetwork().detectCustomSlowCalls().penaltyLog().build());RecorderService.recover(this);applyTheme();buildShell();if(getIntent()!=null&&getIntent().getStringExtra("page")!=null)page=getIntent().getStringExtra("page");if(getIntent()!=null&&getIntent().getBooleanExtra("startWalk",false)&&!prefs.getBoolean("active",false)&&LocalAccount.loggedIn(this)){activityType="Walk";root.post(this::onRecord);}AutoTrack.sync(this);renderPage();handler.post(refresh);if(!LocalAccount.loggedIn(this)&&!LocalAccount.guest(this))showAuth(LocalAccount.exists(this));else{playIntro(null);firstRunPermissions();}if(prefs.getBoolean("active",false))root.post(this::openLive);}
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
        celebrateLayer.setAlpha(0f);celebrateLayer.animate().alpha(1f).setDuration(200).start();
        card.setScaleX(.6f);card.setScaleY(.6f);card.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
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
    private LinearLayout navItem(String title,String icon){LinearLayout item=col();item.setGravity(Gravity.CENTER);item.setFocusable(true);item.setContentDescription(title);item.setOnClickListener(v->navigate(title));android.widget.ImageView symbol=icon(iconRes(title),muted,2);item.addView(symbol,lp(-1,28));TextView label=tx(title,11,muted,true);label.setGravity(Gravity.CENTER);item.addView(label,lp(-1,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(3),0,dp(3),0);nav.addView(item,p);return item;}
    private void navigate(String destination){page=destination;scroller.scrollTo(0,0);closeDrawer();renderPage();}
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
        drawerAction("Today","◷","Today",null);drawerAction("History","▦","History",sessions.length()+(sessions.length()==1?" session":" sessions"));drawerAction("Profile","◉","Profile",null);int nr=Insights.routes(this).length();drawerAction("Routes","↗","Routes",nr==0?null:nr+(nr==1?" route":" routes"));drawerAction("Places","place","Places",null);drawerAction("Reports","≈","Reports",null);int earned=0;for(Progress.Badge bd:Progress.badges(history(),DaySteps.allDayTotals(this),dailyGoal()))if(bd.earned())earned++;drawerAction("Achievements","★","Achievements",earned+" badges");
        drawerSection("SETTINGS");
        drawerAction("Goals & targets","◎","Goals",String.format(Locale.US,"%,d steps",goal));drawerAction("Notifications","bell","Notifications",notificationsAllowed()?"On":"Off");drawerAction("Auto","run","Auto",AutoTrack.enabled(this)?"On":"Off");drawerAction("Permissions","gear","Permissions",null);drawerAction("Storage","storage","Storage",null);drawerAction("Share Kadamm","share","ShareApp","Send the app");
        drawerAction("Step sources & health","＋","Health",getSharedPreferences("kadamm_health",MODE_PRIVATE).getBoolean("healthConnected",false)?"Connected":"Set up");
        drawerSection("APPEARANCE");
        LinearLayout modes=row();drawerList.addView(modes,lp(-1,40));String selectedMode=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("themeMode","system");
        for(String m:new String[]{"Light","Dark","System"}){boolean on=m.equalsIgnoreCase(selectedMode);Button b=button(m,on?lime:raised,on?accentOn:ink);b.setTextSize(12);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(modes.getChildCount()>0)bp.leftMargin=dp(6);modes.addView(b,bp);
            b.setOnClickListener(v->{getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putString("themeMode",m.toLowerCase(Locale.US)).apply();applyTheme();drawer.setBackgroundColor(panel);buildDrawerContents();renderPageNow();});}
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
        content.animate().translationX(-dir*w*.5f).alpha(0f).setDuration(110).withEndAction(()->{scroller.scrollTo(0,0);renderPageNow();content.setTranslationX(dir*w*.35f);content.setAlpha(0f);content.animate().translationX(0).alpha(1f).setDuration(210).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}).start();
    }
    private void renderPage(){int from=tabIndex(renderedPage),to=tabIndex(page);if(from>=0&&to>=0&&from!=to&&content.getChildCount()>0){slideTo(to>from?1:-1);return;}content.setTranslationX(0);float motion=1f;try{motion=android.provider.Settings.Global.getFloat(getContentResolver(),"animator_duration_scale",1f);}catch(Exception ignored){}if(content.getChildCount()==0||motion<=0f){renderPageNow();return;}content.animate().cancel();content.animate().alpha(0f).setDuration(75).withEndAction(()->{renderPageNow();content.setAlpha(0f);content.animate().alpha(1f).setDuration(140).start();}).start();}
    private void renderPageNow(){if(replayAnim!=null){replayAnim.cancel();replayAnim=null;}if(!page.equals(renderedPage)&&scroller!=null)scroller.scrollTo(0,0);renderedPage=page;content.removeAllViews();styleNavItem(navToday,page.equals("Today"));styleNavItem(navHistory,page.equals("History"));styleNavItem(navProfile,page.equals("Profile"));if(page.equals("Today"))renderToday();else if(page.equals("History"))renderHistory();else if(page.equals("Profile"))renderProfile();else if(page.equals("Goals"))renderGoals();else if(page.equals("Health"))renderHealth();else if(page.equals("Routes"))renderRoutes();else if(page.equals("Notifications"))renderNotifications();else if(page.equals("Reports"))renderReports();else if(page.equals("Achievements"))renderAchievements();else if(page.equals("Permissions"))renderPermissions();else if(page.equals("Auto"))renderAutoTracking();else if(page.equals("Storage"))renderStorage();else if(page.equals("Places"))renderPlaces();else renderDetail(lastSession);if(content.getChildCount()>0)signatureAtEnd();}
    private void styleNavItem(LinearLayout item,boolean selected){int color=selected?lime:muted;item.setBackground(rippleOn(selected?raised:Color.TRANSPARENT,14));((android.widget.ImageView)item.getChildAt(0)).setImageTintList(android.content.res.ColorStateList.valueOf(color));((TextView)item.getChildAt(1)).setTextColor(selected?ink:muted);}
    private void brand(String eyebrow,String headline,String sub){LinearLayout brand=row();content.addView(brand,lp(-1,48));LinearLayout.LayoutParams bbp=lp(-2,48);bbp.leftMargin=-dp(8);brand.addView(brandButton(),bbp);TextView e=tx(eyebrow.toUpperCase(Locale.US),10,muted,true);LinearLayout.LayoutParams ep=lp(-1,25);ep.topMargin=dp(8);content.addView(e,ep);TextView h=tx(headline,29,ink,true);content.addView(h);if(sub!=null){TextView s=tx(sub,13,muted,false);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(5);sp.bottomMargin=dp(16);content.addView(s,sp);}}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(panelShape(16));LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(12);content.addView(c,p);return c;}
    private GradientDrawable panelShape(int radius){GradientDrawable d=shape(panel,radius);d.setStroke(dp(1),line);return d;}
    private void renderToday(){JSONArray hist=history();dayTotals=DaySteps.allDayTotals(this);loadIdleLocation();renderHeader();renderStepSetup();renderBadgeBanner(hist);renderHero(hist);renderCoach();renderBento(hist);renderDistanceGoal(hist);
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
        renderDailyInsights(hist);renderReportsLink();renderTrackingHealth();TextView recentLabel=tx("RECENT ACTIVITY",11,muted,true);LinearLayout.LayoutParams rl=lp(-1,34);rl.topMargin=dp(20);content.addView(recentLabel,rl);JSONArray sorted=history();if(sorted.length()==0){LinearLayout empty=card();empty.addView(tx("Your sessions will show up here.",13,muted,false));}else for(int i=sorted.length()-1;i>=Math.max(0,sorted.length()-3);i--){final JSONObject session=sorted.optJSONObject(i);sessionRow(session,()->{lastSession=session;page="Detail";renderPage();});}
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
            LinearLayout.LayoutParams lvp=lp(-1,150);lvp.topMargin=dp(6);land.addView(new LandscapeView(this,chartTheme(),mv,goal,lastDay),lvp);}
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
        TextView dayLabel=tx(count==0?"No sessions on this day":count==1?"1 session":count+" sessions",11,muted,true);LinearLayout.LayoutParams dlp=lp(-1,-2);dlp.topMargin=dp(16);content.addView(dayLabel,dlp);
        for(int i=h.length()-1;i>=0;i--){JSONObject o=h.optJSONObject(i);if(o!=null&&dateKey(o).equals(sk))sessionRow(o,()->{lastSession=o;page="Detail";renderPage();});}
    }
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
        String sources=hp.getString("healthSources","").replace("com.sec.android.app.shealth","Samsung Health").replace("com.google.android.apps.fitness","Google Fit").replace(",",", ");
        LinearLayout c=card();c.addView(tx("Health Connect",16,ink,true));
        TextView hs=tx(connected?"●  Connected":"○  Not connected",14,connected?positive:muted,true);LinearLayout.LayoutParams hsp=lp(-1,-2);hsp.topMargin=dp(6);c.addView(hs,hsp);
        if(connected){
            c.addView(tx(imported>0?String.format(Locale.US,"%,d steps shared today",imported)+(sources.isEmpty()?"":" · from "+sources):"No steps shared today yet",12,muted,false));
            c.addView(tx("Last sync: "+sync+" · refreshes each time you open Kadamm",12,muted,false));
            if(imported<=0)c.addView(tx("Samsung Health keeps its count private until you allow sharing: Samsung Health > Settings > Health Connect > Steps.",11,warning,false));
        }else c.addView(tx("Adds steps counted by Samsung Health and other apps, and fills in up to 30 past days on your calendar.",12,muted,false));
        Button open=button(connected?"Refresh or manage":"Connect Health Connect",connected?raised:lime,connected?ink:accentOn);LinearLayout.LayoutParams bp=lp(-1,46);bp.topMargin=dp(12);c.addView(open,bp);
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
        goalCard("Move minutes","Time spent in Kadamm sessions. The inner ring.","◷",green(),"activeGoalMin",30,10,180,5,new int[]{20,30,45,60},v->v+" min",v->v+" min");
        goalCard("Indoor stride length","Turns treadmill steps into distance. Walk 10 m, count your steps, and divide.","⇅",teal(),"strideLengthCm",72,40,130,1,new int[]{65,72,80},v->String.format(Locale.US,"%.2f m per step",v/100f),v->v+" cm");
        goalCard("Body weight","Used to estimate the calories of your sessions. Stays on this phone.","◉p",indigo(),"weightKg",70,35,180,1,new int[]{55,65,75,85},v->v+" kg",v->v+" kg");
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
        set[0]=v->{v=Math.max(min,Math.min(max,v));if(step>1)v=Math.round(v/(float)step)*step;st.edit().putInt(key,v).apply();value.setText(fmt.apply(v));seek.setProgress(v-min);paint.run();};
        for(int i=0;i<presets.length;i++){int pv=presets[i];Button b=button(chip.apply(pv),raised,ink);b.setTextSize(12);b.setPadding(0,0,0,0);buttons[i]=b;LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)bp.leftMargin=dp(6);chips.addView(b,bp);b.setOnClickListener(v->set[0].accept(pv));}
        minus.setOnClickListener(v->set[0].accept(st.getInt(key,def)-step));plus.setOnClickListener(v->set[0].accept(st.getInt(key,def)+step));paint.run();
    }
    private void showHealthConnectInfo(){navigate("Health");}
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
        Insights.Coverage cv=Insights.coverage(h);int earned=0;for(Progress.Badge bd:Progress.badges(h,totals,dailyGoal()))if(bd.earned())earned++;
        heroChip(chips,"🔥 "+st[0]+(st[0]==1?" day":" days"),flame());heroChip(chips,"★ "+earned+" badges",lime);heroChip(chips,String.format(Locale.US,"◎ %.1f km explored",cv.uniqueKm),teal());
        if(me==null){Button in=button(LocalAccount.exists(this)?"Log in":"Create account",lime,accentOn);in.setTextSize(15);LinearLayout.LayoutParams ip=lp(-1,50);ip.topMargin=dp(16);hero.addView(in,ip);in.setOnClickListener(v->showAuth(LocalAccount.exists(this)));}
        // Lifetime totals.
        long steps=0;for(Long v:totals.values())steps+=v;double km=distanceAll(h);long ms=0;for(int i=0;i<h.length();i++){JSONObject o=h.optJSONObject(i);if(o!=null)ms+=o.optLong("durationMs",0);}
        TextView lh=tx("ALL TIME",10,muted,true);lh.setLetterSpacing(.08f);LinearLayout.LayoutParams lhp=lp(-1,-2);lhp.topMargin=dp(20);content.addView(lh,lhp);
        LinearLayout r1=glanceRow();glance(r1,"STEPS",steps>=100000?String.format(Locale.US,"%.0fk",steps/1000d):String.format(Locale.US,"%,d",steps),"",lime,"▶",null,totals.size()+" days counted");glance(r1,"SESSIONS",String.valueOf(h.length()),"",purple(),"◷",null,String.format(Locale.US,"%.1f km · %dh %02dm",km,ms/3600000,(ms/60000)%60));
        renderRecords();
        // Shortcuts.
        LinearLayout links=card();links.setPadding(dp(6),dp(6),dp(6),dp(6));
        profileLink(links,"Places you walk","Your street map and explorer level",R.drawable.ic_my_location,teal(),()->navigate("Places"));
        profileLink(links,"Achievements",earned+" badges earned",R.drawable.ic_trophy,flame(),()->navigate("Achievements"));
        profileLink(links,"Goals and targets",String.format(Locale.US,"%,d steps a day",dailyGoal()),R.drawable.ic_flag,green(),()->navigate("Goals"));
        profileLink(links,"Activity calendar","Your days and sessions",R.drawable.ic_calendar,purple(),()->navigate("History"));
        profileLink(links,"Share Kadamm","Send the app to a friend",R.drawable.ic_share,lime,this::shareApp);
        renderAccount();
        LinearLayout appearance=card();appearance.addView(tx("Appearance",16,ink,true));appearance.addView(tx("Choose how Kadamm looks on this device.",12,muted,false));String selected=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("themeMode","system");LinearLayout choices=row();LinearLayout.LayoutParams chp=lp(-1,42);chp.topMargin=dp(12);appearance.addView(choices,chp);for(String mode:new String[]{"Light","Dark","System"}){boolean active=mode.equalsIgnoreCase(selected);Button b=button(mode,active?lime:raised,active?accentOn:ink);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(choices.getChildCount()>0)bp.leftMargin=dp(7);choices.addView(b,bp);b.setOnClickListener(v->{getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putString("themeMode",mode.toLowerCase(Locale.US)).apply();applyTheme();renderPage();});}
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
    private View avatarView(LocalAccount.Account a,int sizeDp){
        java.io.File f=a!=null&&a.photo!=null?new java.io.File(a.photo):null;
        if(f!=null&&f.exists()){android.widget.ImageView iv=new android.widget.ImageView(this);iv.setImageBitmap(android.graphics.BitmapFactory.decodeFile(f.getPath()));iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
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
    private void renderDetail(JSONObject o){if(o==null){page="History";renderPage();return;}TextView back=tx("←  HISTORY",11,lime,true);content.addView(back,lp(-1,28));back.setOnClickListener(v->{page="History";renderPage();});String dt=o.optString("date","");brand(dt,sessionTitle(o),"Session summary · "+o.optString("time",""));RouteView rv=new RouteView(this);rv.setInteractive(true);rv.setTheme(bg,raised,lime,darkTheme);rv.setRoute(o.optString("route","[]"));rv.setClipToOutline(true);rv.setBackground(shape(panel,16));FrameLayout mapBox=new FrameLayout(this);LinearLayout.LayoutParams rp=lp(-1,320);content.addView(mapBox,rp);mapBox.addView(rv,new FrameLayout.LayoutParams(-1,-1));android.widget.ImageView fit=icon(R.drawable.ic_my_location,ink,10);fit.setBackground(rippleOn(panel,12));fit.setContentDescription("Show the whole route");fit.setOnClickListener(v->rv.resetView());TextView mstyle=tx("Map: "+mapStyleName(),11,ink,true);mstyle.setPadding(dp(10),dp(6),dp(10),dp(6));mstyle.setBackground(rippleOn(panel,14));FrameLayout.LayoutParams msp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);msp.setMargins(dp(10),dp(10),0,0);mapBox.post(()->mapBox.addView(mstyle,msp));mstyle.setOnClickListener(v->{cycleMapStyle();rv.setTheme(bg,raised,lime,darkTheme);mstyle.setText("Map: "+mapStyleName());});FrameLayout.LayoutParams fitp=new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.TOP|Gravity.START);fitp.setMargins(dp(10),dp(52),0,0);mapBox.addView(fit,fitp);rv.setLegend(true);if(o.optString("route","[]").contains("\"s\"")&&o.optDouble("distanceKm",0)>=.1)replayControls(mapBox,rv,o);if(o.optDouble("runDistanceKm",0)>0||o.optString("route","").contains("\"t\":\"r\"")){LinearLayout key=row();LinearLayout.LayoutParams kp=lp(-1,28);kp.topMargin=dp(6);content.addView(key,kp);key.addView(tx("●",14,lime,true));key.addView(tx(String.format(Locale.US,"  Walk %.2f km      ",o.optDouble("walkDistanceKm",0)),12,ink,true));key.addView(tx("●",14,Color.rgb(234,67,53),true));key.addView(tx(String.format(Locale.US,"  Run %.2f km",o.optDouble("runDistanceKm",0)),12,ink,true));}TextView attribution=tx("© OpenStreetMap contributors · drag, pinch or double-tap to explore",10,muted,false);LinearLayout.LayoutParams ap=lp(-1,28);ap.topMargin=dp(5);content.addView(attribution,ap);LinearLayout c=card();long ms=o.optLong("durationMs",0);double km=o.optDouble("distanceKm",0);double speed=ms>0?km/(ms/3600000d):0;detailRow(c,"DISTANCE",String.format(Locale.US,"%.2f km",km),"DURATION",formatDuration(ms));detailRow(c,"AVG. SPEED",String.format(Locale.US,"%.1f km/h",speed),"AVG. PACE",km>0?String.format(Locale.US,"%d:%02d /km",Math.round(ms/1000d/km)/60,Math.round(ms/1000d/km)%60):"—");detailRow(c,"STEPS",String.format(Locale.US,"%,d",o.optInt("steps")),"EST. CALORIES",String.format(Locale.US,"~%d kcal",o.optInt("calories",(int)Math.round(km*65))));detailRow(c,"MOVING TIME",formatDuration(o.optLong("movingMs",ms)),"ELEV. GAIN",o.has("elevGainM")?String.format(Locale.US,"%.0f m",o.optDouble("elevGainM")):"—");if(o.has("walkDistanceKm")||o.has("runDistanceKm")){long wms=o.optLong("walkDurationMs"),rms=o.optLong("runDurationMs");double wkm=o.optDouble("walkDistanceKm"),rkm=o.optDouble("runDistanceKm");detailRow(c,"WALK",String.format(Locale.US,"%.2f km · %s",wkm,formatDuration(wms)),"RUN",String.format(Locale.US,"%.2f km · %s",rkm,formatDuration(rms)));detailRow(c,"WALK PACE",wkm>0?String.format(Locale.US,"%d:%02d /km",Math.round(wms/1000d/wkm)/60,Math.round(wms/1000d/wkm)%60):"—","RUN PACE",rkm>0?String.format(Locale.US,"%d:%02d /km",Math.round(rms/1000d/rkm)/60,Math.round(rms/1000d/rkm)%60):"—");}if(o.optBoolean("distanceEstimated",false)){TextView estimate=tx("Indoor distance is estimated from your configured stride length.",11,warning,false);c.addView(estimate);TextView correction=tx("Correct with treadmill distance",13,lime,true);c.addView(correction,lp(-1,42));correction.setOnClickListener(v->correctDistance(o));}TextView note=tx(o.optString("activityLabel","").isEmpty()?"Session saved on this device":o.optString("activityLabel")+" · saved on this device",11,muted,false);LinearLayout.LayoutParams np=lp(-1,26);np.topMargin=dp(9);c.addView(note,np);if(o.has("hcSteps")){TextView hc=tx(String.format(Locale.US,"Health Connect during this session: %,d steps",o.optLong("hcSteps"))+(o.has("hcActiveKcal")?String.format(Locale.US," · %d active kcal",o.optLong("hcActiveKcal")):""),11,positive,true);LinearLayout.LayoutParams hcp=lp(-1,-2);hcp.topMargin=dp(4);c.addView(hc,hcp);}renderSessionInsights(o);sessionActions(o);}
    private void correctDistance(JSONObject session){android.widget.EditText input=new android.widget.EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setText(String.format(Locale.US,"%.2f",session.optDouble("distanceKm")));dialog().setTitle("Correct distance").setMessage("Enter the distance shown on the treadmill, in kilometres.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{try{double km=Double.parseDouble(input.getText().toString().trim());if(km<0||km>500){toast("Enter a distance from 0 to 500 km");return;}double ratio=session.optDouble("distanceKm",0)>0?km/session.optDouble("distanceKm",0):1;session.put("walkDistanceKm",session.optDouble("walkDistanceKm",km)*ratio);session.put("runDistanceKm",session.optDouble("runDistanceKm",0)*ratio);session.put("distanceKm",km);session.put("calories",Calories.estimate(this,session.optDouble("walkDistanceKm",km),session.optLong("walkDurationMs",session.optLong("durationMs")),session.optDouble("runDistanceKm",0),session.optLong("runDurationMs",0),session.optLong("steps",0)));session.put("distanceEstimated",false);session.put("distanceCorrected",true);saveSession(historyPrefs,session);lastSession=session;renderPage();}catch(Exception e){toast("Enter a valid distance");}}).show();}
    private void detailRow(LinearLayout c,String a,String av,String b,String bv){LinearLayout r=row();LinearLayout.LayoutParams p=lp(-1,54);c.addView(r,p);LinearLayout x=col(),y=col();r.addView(x,new LinearLayout.LayoutParams(0,-1,1));r.addView(y,new LinearLayout.LayoutParams(0,-1,1));x.addView(tx(a,9,muted,true));x.addView(tx(av,17,ink,true));y.addView(tx(b,9,muted,true));y.addView(tx(bv,17,ink,true));}
    private void sessionRow(JSONObject o,Runnable click){if(o==null)return;LinearLayout r=row();r.setPadding(dp(14),dp(12),dp(12),dp(12));r.setBackground(panelShape(14));LinearLayout.LayoutParams p=lp(-1,82);p.topMargin=dp(8);content.addView(r,p);LinearLayout desc=col();r.addView(desc,new LinearLayout.LayoutParams(0,-1,1));desc.addView(tx((o.optBoolean("auto",false)?"Auto · ":"")+sessionLabel(o)+"  ·  "+formatDuration(o.optLong("durationMs")),14,ink,true));double km=o.optDouble("distanceKm",0);long ms=o.optLong("durationMs",0);long sec=km>0?Math.round(ms/1000d/km):0;desc.addView(tx(String.format(Locale.US,"%.2f km  ·  %s  ·  %,d steps",km,o.optString("time",""),o.optInt("steps")),10,muted,false));desc.addView(tx(String.format(Locale.US,"%d:%02d /km  ·  ~%d kcal",sec/60,sec%60,o.optInt("calories",(int)Math.round(km*65))),10,muted,false));String savedRoute=o.optString("route", "[]");if(!"[]".equals(savedRoute)){RouteView preview=new RouteView(this);preview.setTheme(bg,raised,lime,darkTheme);preview.setMarkers(false);preview.setRoute(savedRoute);LinearLayout.LayoutParams pv=lp(58,58);pv.setMargins(dp(5),0,dp(5),0);r.addView(preview,pv);}r.addView(tx("›",25,muted,false));r.setOnClickListener(v->click.run());}
    private void finishActivity(){if(!prefs.getBoolean("active",false)){toast("No activity to finish");return;}dialog().setTitle("Finish activity?").setMessage("Save this route and its session statistics to History?").setNegativeButton("Keep tracking",null).setPositiveButton("Finish",(d,w)->{closeLive();command(RecorderService.ACTION_STOP);new Handler(Looper.getMainLooper()).postDelayed(()->{JSONArray h=history();lastSession=h.optJSONObject(h.length()-1);if(lastSession!=null){page="Detail";renderPage();}else renderPage();},500);}).show();}
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
    private void updateLive(){if(prefs==null)return;if(resumed)refreshHealth(false);maybeCelebrate();updateMiniBar();updateLiveOverlay();boolean active=prefs.getBoolean("active",false),paused=prefs.getBoolean("paused",false);JSONArray all=history();if(timer!=null){long ms=prefs.getLong("elapsed",0);timer.setText(formatDuration(ms));double km=prefs.getFloat("distance",0);distance.setText(String.format(Locale.US,"%.2f km",km));long ps=km>0?Math.round(ms/1000d/km):0;pace.setText(km>0?String.format(Locale.US,"%d:%02d /km",ps/60,ps%60):"— /km");steps.setText(String.format(Locale.US,"%,d",prefs.getInt("steps",0)));if(liveSpeed!=null){float kmh=active&&!paused?prefs.getFloat("speedKmh",0):0;liveSpeed.setText(kmh>0?String.format(Locale.US,"%.1f km/h",kmh):"— km/h");liveElev.setText(active&&prefs.getBoolean("hasElevation",false)?String.format(Locale.US,"%.0f m",prefs.getFloat("elevGainM",0)):"— m");liveKcal.setText(String.format(Locale.US,"~%d kcal",Calories.live(this)));}boolean moving=prefs.getBoolean("moving",false);long fixAge=android.os.SystemClock.elapsedRealtime()-prefs.getLong("lastFixElapsed",0);boolean indoor="indoor".equals(prefs.getString("activityMode","outdoor"));boolean gps=indoor||(prefs.getBoolean("gpsFix",false)&&fixAge<12000);boolean indoorSel="indoor".equals(prefs.getString("activityMode","outdoor"));start.setText(active?(paused?"▶  Resume":"Ⅱ  Pause"):"▶  Start activity");if(trackStats!=null){trackStats.setVisibility(active?View.VISIBLE:View.GONE);trackEnd.setVisibility(active?View.VISIBLE:View.GONE);}String type=prefs.getString("currentType",activityType);status.setText(active?(paused?"● PAUSED":!indoor&&!gps?"● GPS SIGNAL WEAK":!moving?(indoor?"● WAITING FOR STEPS":"● YOU’RE NOT WALKING"):"● YOU’RE "+(type.equals("Run")?"RUNNING":"WALKING")):"● READY");status.setTextColor(active&&paused?muted:active&&!gps&&!indoor?warning:active&&!moving?warning:active?positive:lime);if(active&&!paused&&moving!=previousMoving){toast(moving?"You're moving · Kadamm is recording your activity":"You're not moving · pause or start moving to continue");}previousMoving=moving;route.setRoute(prefs.getString("route","[]"));route.setEmptyMessage(indoor?"Indoor session · distance comes from your steps":"Your route draws here once GPS locks on");if(gps&&!indoor)route.setCurrentLocation(prefs.getFloat("latitude",0),prefs.getFloat("longitude",0),prefs.getFloat("accuracy",0));else if(!active&&!indoor&&hasIdle)route.setCurrentLocation(idleLat,idleLon,idleAcc);else route.clearCurrentLocation();if(accuracyLabel!=null)accuracyLabel.setText(indoor?"Indoor mode · distance estimated from phone steps":gps?String.format(Locale.US,"GPS accuracy · ±%.0f m",prefs.getFloat("accuracy",0)):!active?(hasIdle?"Last known location · GPS locks on when you start":"Location appears here when you start"):"GPS signal weak or unavailable · last route point kept");if(activityModeLabel!=null)activityModeLabel.setText(!active?"Outdoor or indoor · walk or run detected":prefs.getBoolean("manualType",false)?(indoor?"Indoor · ":"Outdoor · ")+type+" (set by you)":(indoor?"Indoor · detected ":"Outdoor · detected ")+type.toLowerCase(Locale.US)+(" · tap to correct"));}updateHero(all);if(dailyDistanceProgress!=null){int target=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("distanceGoalM",5000);double meters=totalDistanceOn(all,todayKey())*1000;dailyDistanceProgress.setMax(target);dailyDistanceProgress.setProgress((int)Math.min(target,meters));dailyDistanceProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(meters>=target?positive:violet));dailyDistanceValue.setText(String.format(Locale.US,"%.2f / %.1f km",meters/1000f,target/1000f));}if(todayCount!=null){todayCount.setText(String.valueOf(countOn(all,todayKey(),"Walk")+(active&&"Walk".equals(prefs.getString("currentType","Walk"))?1:0)));todayDistance.setText(String.format(Locale.US,"%.2f",totalDistanceOn(all,todayKey())));}}
    private JSONArray history(){try{return new JSONArray(historyPrefs.getString("sessions","[]"));}catch(Exception e){return new JSONArray();}}
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
        TextView e=tx(new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(new Date()).toUpperCase(Locale.getDefault()),10,muted,true);e.setLetterSpacing(.08f);LinearLayout.LayoutParams ep=lp(-1,-2);ep.topMargin=dp(6);content.addView(e,ep);
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
        LinearLayout head=row();head.addView(tx("DAILY STEP GOAL",10,muted,true),new LinearLayout.LayoutParams(0,-2,1));heroSource=tx("",10,positive,true);head.addView(heroSource);head.addView(shareIcon("today",()->shareSheet("Share today",todayShareText(),null,null)),lp(40,40));c.addView(head);
        LinearLayout body=row();LinearLayout.LayoutParams bp=lp(-1,-2);bp.topMargin=dp(10);c.addView(body,bp);
        ring=new ProgressRingView(this,chartTheme());body.addView(ring,lp(152,152));
        LinearLayout side=col();LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=dp(16);body.addView(side,sp);
        heroPercent=tx("—",30,ink,true);side.addView(heroPercent);side.addView(tx("●  of step goal",11,lime,true));
        heroRemaining=tx("",13,ink,true);LinearLayout.LayoutParams rp=lp(-1,-2);rp.topMargin=dp(10);side.addView(heroRemaining,rp);
        heroDistance=tx("",11,violet,true);LinearLayout.LayoutParams dp2=lp(-1,-2);dp2.topMargin=dp(3);side.addView(heroDistance,dp2);heroSessions=tx("",11,muted,false);LinearLayout.LayoutParams hsp=lp(-1,-2);hsp.topMargin=dp(6);side.addView(heroSessions,hsp);
        dailyGoalMessage=tx("",12,muted,false);LinearLayout.LayoutParams mp=lp(-1,-2);mp.topMargin=dp(12);c.addView(dailyGoalMessage,mp);
        heroStart=button("▶  Start activity",lime,accentOn);heroStart.setTextSize(15);LinearLayout.LayoutParams hp=lp(-1,50);hp.topMargin=dp(12);c.addView(heroStart,hp);
        heroStart.setOnClickListener(v->{if(prefs.getBoolean("active",false))openLive();else onRecord();});
        updateHero(hist);
    }
    private void updateHero(JSONArray all){
        if(ring==null||heroPercent==null)return;
        int goal=dailyGoal();long n=DaySteps.todayTotal(this);boolean data=DaySteps.hasTodayData(this),active=prefs.getBoolean("active",false);double pct=goal>0?n*100d/goal:0;
        ring.setProgress(n,goal,data);
        heroPercent.setText(data?String.format(Locale.US,pct>=100||pct==Math.floor(pct)?"%.0f%%":"%.1f%%",pct):"—");heroPercent.setTextColor(data?(pct>=100?positive:ink):muted);
        heroRemaining.setText(!data?"Steps not counted yet":n>=goal?(n>goal?String.format(Locale.US,"+%,d beyond goal",n-goal):"Goal reached"):String.format(Locale.US,"%,d steps to go",goal-n));
        long moveMin=todayDuration(all)/60000;SharedPreferences hpm=getSharedPreferences("kadamm_health",MODE_PRIVATE);if(todayKey().equals(hpm.getString("healthDate",""))&&hpm.getBoolean("healthConnected",false))moveMin=Math.max(moveMin,hpm.getInt("healthActiveMin",0));int moveGoal=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("activeGoalMin",30);ring.setInner(moveMin/(float)Math.max(1,moveGoal));heroDistance.setText(String.format(Locale.US,"●  %d of %d move min",moveMin,moveGoal));
        String source=DaySteps.todaySource(this);heroSource.setText("phone".equals(source)?"PHONE SENSOR":"health".equals(source)?"HEALTH CONNECT":"sessions".equals(source)?"SESSIONS ONLY":"");
        heroStart.setText(active?"View live session":"▶  Start activity");if(heroSessions!=null)heroSessions.setText(String.format(Locale.US,"%.2f km · %,d active kcal",DaySteps.distanceToday(this)[0],DaySteps.activeKcalToday(this)));
        if(heroNote!=null)heroNote.setText(!data?"Let's get your steps counting.":pct>=100?"Goal done. Anything from here is a bonus.":pct>=80?"Nearly there. One short walk finishes it.":pct>=50?"Over halfway. Keep the rhythm going.":pct>=10?"Good start. Keep the momentum.":"Every step counts. Let's get moving.");
        if(n>=goal&&data){
            if(!todayKey().equals(prefs.getString("goalShownDay",""))){prefs.edit().putString("goalShownDay",todayKey()).apply();if(!reducedMotion())dailyGoalCard.animate().scaleX(1.015f).scaleY(1.015f).setDuration(180).withEndAction(()->dailyGoalCard.animate().scaleX(1f).scaleY(1f).setDuration(180).start()).start();}
            dailyGoalMessage.setText("Daily goal achieved! Great work, you've reached your step goal today.");dailyGoalMessage.setTextColor(positive);
        }else if(data&&n>=Math.ceil(goal*.6)){
            dailyGoalMessage.setText(String.format(Locale.US,"You're making progress! %,d steps done, just %,d more to reach your goal.",n,goal-n));dailyGoalMessage.setTextColor(ink);
        }else{
            dailyGoalMessage.setText(data?String.format(Locale.US,"%,d of %,d steps so far today.",n,goal):DaySteps.sensorPresent(this)&&!DaySteps.permitted(this)?"Allow step counting above to see today's steps here.":"Start an activity or connect a step source to see today's steps.");dailyGoalMessage.setTextColor(muted);
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
        super.onResume();resumed=true;startStepCounting();refreshHealth(true);
        if(content!=null&&(("Today".equals(page)&&stepSetupShown&&DaySteps.permitted(this))||"Health".equals(page)||"Notifications".equals(page)||"Permissions".equals(page)||"Auto".equals(page)||"Storage".equals(page)))renderPageNow();
    }
    @Override protected void onPause(){
        resumed=false;if(stepListening){((SensorManager)getSystemService(SENSOR_SERVICE)).unregisterListener(stepListener);stepListening=false;}
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
            replayAnim=android.animation.ValueAnimator.ofFloat(from,1f);replayAnim.setDuration((long)(9000*(1-from)/sp[0]));replayAnim.setInterpolator(new android.view.animation.LinearInterpolator());
            replayAnim.addUpdateListener(a->{float f=(float)a.getAnimatedValue();rv.setReplay(f);seek.setProgress(Math.round(f*1000));caption.run();});
            replayAnim.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(replayAnim==a){replayAnim=null;play.setText("↺");play.setContentDescription("Replay again");}}});
            play.setText("Ⅱ");play.setContentDescription("Pause replay");replayAnim.start();
        });
        speed.setOnClickListener(v->{sp[0]=sp[0]==1?2:sp[0]==2?4:1;speed.setText((int)sp[0]+"×");if(replayAnim!=null){float f=rv.replay();stop.run();rv.setReplay(f);play.performClick();}});
    }

    /** Asks for dark or light, encodes the replay video with a progress bar, then opens the share sheet. */
    private void shareReplay(JSONObject o){
        dialog().setTitle("Share replay video").setMessage("A 10-second video of this route being walked, ready for WhatsApp or your story.").setNegativeButton("Light",(d,w)->makeReplay(o,false)).setPositiveButton("Dark",(d,w)->makeReplay(o,true)).setNeutralButton("Cancel",null).show();
    }
    private void makeReplay(JSONObject o,boolean dark){
        LinearLayout box=col();box.setPadding(dp(22),dp(14),dp(22),dp(6));TextView msg=tx("Drawing your route… 0%",14,ink,false);box.addView(msg);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(lime));LinearLayout.LayoutParams bp=lp(-1,10);bp.topMargin=dp(12);box.addView(bar,bp);
        AlertDialog wait=dialog().setTitle(dark?"Dark replay video":"Light replay video").setView(box).setCancelable(false).create();wait.show();
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
    private boolean installed(String pkg){try{getPackageManager().getPackageInfo(pkg,0);return true;}catch(Exception e){return false;}}
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
    private FrameLayout liveLayer;private RouteView liveMap;private TextView lvTitle,lvStatus,lvTime,lvPace,lvDist,lvSteps,lvKcal,lvSpeed;private Button lvMain,lvFinish;private long liveOpenedAt;
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
        lvTime.setText(formatDuration(ms));lvPace.setText(ps>0?String.format(Locale.US,"%d:%02d",ps/60,ps%60):"—:—");lvDist.setText(String.format(Locale.US,"%.2f",km));
        lvSteps.setText(String.format(Locale.US,"%,d",prefs.getInt("steps",0)));lvKcal.setText(String.valueOf(Calories.live(this)));float kmh=paused?0:prefs.getFloat("speedKmh",0);lvSpeed.setText(kmh>0?String.format(Locale.US,"%.1f",kmh):"—");
        lvMain.setText(paused?"Resume":"Pause");lvFinish.setVisibility(paused?View.VISIBLE:View.GONE);
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
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent!=null&&intent.getStringExtra("page")!=null){navigate(intent.getStringExtra("page"));if(intent.getBooleanExtra("startWalk",false)&&!prefs.getBoolean("active",false)){activityType="Walk";onRecord();}return;}if(intent!=null&&intent.getBooleanExtra("openLive",false)&&prefs.getBoolean("active",false))openLive();}
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
        java.util.List<double[]> track=Insights.track(o.optString("route","[]"));
        java.util.List<double[]> pace=Insights.pace(track),elev=Insights.elevation(track);
        // Charts need some distance to say anything; a session that barely moved gets none.
        boolean longEnough=!track.isEmpty()&&track.get(track.size()-1)[0]>=.1;if(!longEnough){pace=new java.util.ArrayList<>();elev=new java.util.ArrayList<>();}
        if(!longEnough&&!"indoor".equals(o.optString("activityMode"))){
            LinearLayout n=card();n.addView(tx("Too short for a route and charts",14,ink,true));
            n.addView(tx(String.format(Locale.US,"This session covered %.0f m. The blue walking / red running line and the pace and elevation charts appear once you've moved 100 m or more.",o.optDouble("distanceKm",0)*1000),12,muted,false));
        }
        if(pace.size()>=2){
            double best=Double.MAX_VALUE,sum=0;for(double[] q:pace){best=Math.min(best,q[1]);sum+=q[1];}
            LinearLayout pc=card();LinearLayout ph=row();pc.addView(ph);ph.addView(tx("Pace",16,ink,true),new LinearLayout.LayoutParams(0,-2,1));
            ph.addView(tx("●",12,lime,true));ph.addView(tx(" walk   ",11,muted,false));ph.addView(tx("●",12,SessionChartView.RUN,true));ph.addView(tx(" run",11,muted,false));
            LinearLayout ps=row();LinearLayout.LayoutParams psp=lp(-1,-2);psp.topMargin=dp(8);pc.addView(ps,psp);
            chartStat(ps,"AVERAGE",Insights.clock(sum/pace.size())+" /km");chartStat(ps,"FASTEST",Insights.clock(best)+" /km");chartStat(ps,"DISTANCE",String.format(Locale.US,"%.2f km",pace.get(pace.size()-1)[0]));
            LinearLayout.LayoutParams pcp=lp(-1,190);pcp.topMargin=dp(8);pc.addView(new SessionChartView(this,chartTheme(),pace,true),pcp);
            pc.addView(tx("Higher on the chart is faster. Dashed line: your average.",10,muted,false));
        }
        if(elev.size()>=2){
            LinearLayout ec=card();LinearLayout eh=row();ec.addView(eh);eh.addView(tx("Elevation",15,ink,true),new LinearLayout.LayoutParams(0,-2,1));
            double low=Double.MAX_VALUE,high=-Double.MAX_VALUE;for(double[] q:elev){low=Math.min(low,q[1]);high=Math.max(high,q[1]);}
            LinearLayout es=row();LinearLayout.LayoutParams esp=lp(-1,-2);esp.topMargin=dp(8);ec.addView(es,esp);
            chartStat(es,"GAIN",o.has("elevGainM")?String.format(Locale.US,"+%.0f m",o.optDouble("elevGainM")):"—");chartStat(es,"LOWEST",String.format(Locale.US,"%.0f m",low));chartStat(es,"HIGHEST",String.format(Locale.US,"%.0f m",high));
            LinearLayout.LayoutParams ecp=lp(-1,170);ecp.topMargin=dp(8);ec.addView(new SessionChartView(this,chartTheme(),elev,false),ecp);
        }
        if(track.isEmpty()&&Insights.points(o.optString("route","[]")).size()>=2){TextView n=tx("Pace and elevation charts appear for sessions recorded with Kadamm 6.0 or later.",11,muted,false);LinearLayout.LayoutParams np=lp(-1,-2);np.topMargin=dp(8);content.addView(n,np);}
    }
    private void chartStat(LinearLayout parent,String label,String value){LinearLayout c=col();parent.addView(c,new LinearLayout.LayoutParams(0,-2,1));c.addView(tx(label,9,muted,true));c.addView(tx(value,15,ink,true));}
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
            b.setOnClickListener(x->{getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putInt(key,v).apply();renderPageNow();});}
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
        f.addView(tx("Any session, automatic or started by you, finishes after this long without steps or movement. Paused sessions are left alone.",11,muted,false));
        choiceChips(f,"After no movement for",new int[]{0,3,5,10},AutoTrack.stopAfterMin(this),"autoStopMin",v->v==0?"Off":v+" min");
        LinearLayout i=card();i.addView(tx("Good to know",14,ink,true));
        i.addView(tx("• Automatic sessions shorter than 3 minutes, 200 m and 300 steps are dropped, so short walks around the house don't fill History.\n• Automatic sessions are marked \"Auto\" in History.\n• Keep unrestricted battery on (Permissions) so Android lets Kadamm start sessions in the background.",12,muted,false));
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
        java.util.List<Progress.Badge> fresh=Progress.unseen(this,Progress.badges(hist,dayTotals,dailyGoal()));if(fresh.isEmpty())return;
        LinearLayout c=card();GradientDrawable g=shape(blend(panel,flame(),darkTheme?.18f:.10f),16);g.setStroke(dp(1),blend(line,flame(),.4f));c.setBackground(g);
        LinearLayout r=row();c.addView(r);android.widget.ImageView ic=icon(R.drawable.ic_trophy,darkTheme?Color.rgb(11,11,13):Color.WHITE,8);ic.setBackground(shape(flame(),12));r.addView(ic,lp(40,40));
        LinearLayout t=col();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(12);r.addView(t,tp);
        t.addView(tx(fresh.size()==1?"New badge: "+fresh.get(0).name:fresh.size()+" new badges",15,ink,true));t.addView(tx("Tap to see your achievements",11,muted,false));
        c.setFocusable(true);c.setOnClickListener(v->navigate("Achievements"));
    }
    private void renderAchievements(){
        brand("YOUR PROGRESS","Achievements","Badges you earn and challenges you set.");
        JSONArray h=history();java.util.Map<String,Long> totals=DaySteps.allDayTotals(this);int goal=dailyGoal();
        java.util.List<Progress.Badge> all=Progress.badges(h,totals,goal);boolean celebrate=!Progress.unseen(this,all).isEmpty();
        TextView chT=tx("CHALLENGES",10,muted,true);LinearLayout.LayoutParams ctp=lp(-1,-2);ctp.topMargin=dp(6);content.addView(chT,ctp);
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
        int earned=0;for(Progress.Badge b:all)if(b.earned())earned++;
        TextView bT=tx("BADGES · "+earned+" OF "+all.size(),10,muted,true);LinearLayout.LayoutParams btp=lp(-1,-2);btp.topMargin=dp(18);content.addView(bT,btp);
        LinearLayout grid=null;
        for(int i=0;i<all.size();i++){
            if(i%3==0){grid=row();grid.setGravity(Gravity.TOP);LinearLayout.LayoutParams gp=lp(-1,-2);gp.topMargin=dp(10);content.addView(grid,gp);}
            Progress.Badge b=all.get(i);boolean on=b.earned();int color=on?flame():line;
            LinearLayout cell=col();cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setPadding(dp(8),dp(12),dp(8),dp(12));GradientDrawable cg=shape(on?blend(panel,flame(),darkTheme?.15f:.08f):panel,16);cg.setStroke(dp(1),on?blend(line,flame(),.35f):line);cell.setBackground(cg);
            LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(150),1);if(i%3>0)clp.leftMargin=dp(8);grid.addView(cell,clp);
            android.widget.ImageView ic=icon(b.icon,on?(darkTheme?Color.rgb(11,11,13):Color.WHITE):muted,10);ic.setBackground(shape(on?color:raised,24));cell.addView(ic,lp(46,46));
            TextView n=tx(b.name,12,on?ink:muted,true);n.setGravity(Gravity.CENTER);LinearLayout.LayoutParams nlp=lp(-1,-2);nlp.topMargin=dp(8);cell.addView(n,nlp);
            TextView d=tx(on?b.desc:b.progressText(),10,muted,false);d.setGravity(Gravity.CENTER);cell.addView(d,lp(-1,-2));
            if(!on&&b.target>1&&!"".equals(b.unit)){ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(1000);pb.setProgress((int)Math.min(1000,b.value*1000/b.target));pb.setProgressTintList(android.content.res.ColorStateList.valueOf(lime));pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(raised));LinearLayout.LayoutParams pp=lp(-1,5);pp.topMargin=dp(6);cell.addView(pb,pp);}
            cell.setContentDescription(b.name+", "+(on?"earned":"not yet earned, "+b.progressText())+". "+b.desc);
        }
        // Keep the last row in the 3-column grid instead of stretching a lone badge across it.
        if(grid!=null)for(int k=all.size()%3;k>0&&k<3;k++){LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(150),1);fp.leftMargin=dp(8);grid.addView(new View(this),fp);}
        if(celebrate&&!reducedMotion()){ConfettiView confetti=new ConfettiView(this);root.addView(confetti,new FrameLayout.LayoutParams(-1,-1));confetti.play();}
        Progress.markSeen(this,all);
    }
    @Override public void onBackPressed(){
        if(authLayer!=null){super.onBackPressed();return;}
        if(mapLayer!=null&&mapLayer.getVisibility()==View.VISIBLE){closeMapView();return;}
        if(liveLayer!=null&&liveLayer.getVisibility()==View.VISIBLE){closeLive();return;}
        if(drawerLayer!=null&&drawerLayer.getVisibility()==View.VISIBLE){closeDrawer();return;}
        if("Detail".equals(page)){navigate("History");return;}
        if(!"Today".equals(page)){navigate("Today");return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){handler.removeCallbacks(refresh);super.onDestroy();}
}
