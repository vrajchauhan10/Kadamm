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
    private static final String APP_VERSION="5.2";private int dailyGoal(){return getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("stepGoal",10000);}
    private int bg,panel,raised,lime,violet,accentOn,ink,muted,line,focus,positive,warning,critical; private boolean darkTheme=true,previousMoving=false;
    private SharedPreferences prefs,historyPrefs; private FrameLayout root,drawerLayer; private LinearLayout shell,content,nav,drawer,navToday,navHistory,navProfile; private View drawerScrim; private ScrollView scroller; private TextView timer,distance,pace,steps,status,start,todayCount,todayDistance,dailyStepValue,dailyGoalMessage,accuracyLabel,activityModeLabel,todayActiveTime,todayWalkDistance,todayRunDistance,todayCaloriesValue;private ProgressBar dailyGoalProgress,dailyDistanceProgress; private TextView dailyDistanceValue;private View dailyGoalCard; private RouteView route; private Handler handler=new Handler(Looper.getMainLooper()); private String page="Today",activityType="Walk",activityRange="7D"; private Calendar shownMonth=Calendar.getInstance(),selectedDate=Calendar.getInstance(); private JSONObject lastSession; private float gestureDownX,gestureDownY;private long gestureDownAt;
    private static final int STEP_PERMISSION=101;private ProgressRingView ring;private TextView heroPercent,heroRemaining,heroDistance,heroSource,heroStart,heroNote;private View trackCard;private TextView heroSessions;private TextView liveSpeed,liveElev,liveKcal;private Map<String,Long> dayTotals;private double idleLat,idleLon;private float idleAcc;private boolean hasIdle,stepListening,stepSetupShown,resumed,syncing;private long lastHealthRefresh;
    private final SensorEventListener stepListener=new SensorEventListener(){@Override public void onSensorChanged(SensorEvent e){DaySteps.ingest(MainActivity.this,e.values[0]);}@Override public void onAccuracyChanged(Sensor sensor,int accuracy){}};
    private final Runnable refresh=()->{updateLive();handler.postDelayed(this.refresh,1000);};
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("stride_state",MODE_PRIVATE);historyPrefs=getSharedPreferences("kadamm_history",MODE_PRIVATE);if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.os.StrictMode.setThreadPolicy(new android.os.StrictMode.ThreadPolicy.Builder().detectNetwork().detectCustomSlowCalls().penaltyLog().build());RecorderService.recover(this);applyTheme();buildShell();renderPage();handler.post(refresh);firstRunPermissions();if(prefs.getBoolean("active",false))root.post(this::openLive);}
    private void applyTheme(){String mode=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("themeMode","system");KadammTheme t=KadammTheme.resolve(this,mode);darkTheme=t.dark;bg=t.background;panel=t.surface;raised=t.surfaceRaised;lime=t.accent;violet=t.accentSecondary;accentOn=t.accentOn;ink=t.text;muted=t.textMuted;line=t.border;focus=t.focus;positive=t.positive;warning=t.warning;critical=t.critical;getWindow().setBackgroundDrawable(new ColorDrawable(bg));getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(panel);getWindow().getDecorView().setSystemUiVisibility(darkTheme?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);if(root!=null)root.setBackgroundColor(bg);if(nav!=null)nav.setBackgroundColor(panel);if(route!=null)route.setTheme(bg,raised,lime,darkTheme);}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);} private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;} private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;} private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private TextView tx(String s,int sz,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sz);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",0));return t;} private GradientDrawable shape(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private void buildShell(){root=new FrameLayout(this);root.setBackgroundColor(bg);setContentView(root);shell=col();FrameLayout.LayoutParams shellParams=new FrameLayout.LayoutParams(-1,-1);root.addView(shell,shellParams);scroller=new ScrollView(this);scroller.setFillViewport(true);scroller.setClipToPadding(false);content=col();content.setPadding(dp(20),dp(18),dp(20),dp(20));scroller.addView(content);shell.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));nav=row();nav.setPadding(dp(10),dp(6),dp(10),dp(6));nav.setBackgroundColor(panel);shell.addView(nav,lp(-1,72));navToday=navItem("Today","◷");navHistory=navItem("History","▦");navProfile=navItem("Profile","◉");
        drawerLayer=new FrameLayout(this);drawerLayer.setVisibility(View.GONE);root.addView(drawerLayer,new FrameLayout.LayoutParams(-1,-1));drawerScrim=new View(this);drawerScrim.setBackgroundColor(Color.argb(155,0,0,0));drawerLayer.addView(drawerScrim,new FrameLayout.LayoutParams(-1,-1));drawerScrim.setOnClickListener(v->closeDrawer());drawer=col();drawer.setPadding(dp(20),dp(24),dp(16),dp(18));drawer.setBackground(panelShape(0));drawer.setElevation(dp(18));FrameLayout.LayoutParams drawerParams=new FrameLayout.LayoutParams(Math.min(dp(340),getResources().getDisplayMetrics().widthPixels-dp(48)),-1,Gravity.START);drawerLayer.addView(drawer,drawerParams);
        root.setOnApplyWindowInsetsListener((v,insets)->{int l,t,r,b;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());l=safe.left;t=safe.top;r=safe.right;b=safe.bottom;}else{l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();}root.setPadding(l,t,r,b);return insets;});root.requestApplyInsets();}
    @Override public boolean dispatchTouchEvent(MotionEvent ev){
        int a=ev.getActionMasked();
        if(a==MotionEvent.ACTION_DOWN){gestureDownX=ev.getX();gestureDownY=ev.getY();gestureDownAt=System.currentTimeMillis();dragging=false;}
        boolean pager=tabIndex(page)>=0&&drawerLayer!=null&&drawerLayer.getVisibility()!=View.VISIBLE&&(liveLayer==null||liveLayer.getVisibility()!=View.VISIBLE)&&ev.getPointerCount()==1;
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
    private LinearLayout navItem(String title,String icon){LinearLayout item=col();item.setGravity(Gravity.CENTER);item.setFocusable(true);item.setContentDescription(title);item.setOnClickListener(v->navigate(title));TextView symbol=tx(icon,23,muted,true);symbol.setGravity(Gravity.CENTER);item.addView(symbol,lp(-1,30));TextView label=tx(title,11,muted,true);label.setGravity(Gravity.CENTER);item.addView(label,lp(-1,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(3),0,dp(3),0);nav.addView(item,p);return item;}
    private void navigate(String destination){page=destination;scroller.scrollTo(0,0);closeDrawer();renderPage();}
    private void openDrawer(){buildDrawerContents();drawer.setBackgroundColor(panel);drawerLayer.setVisibility(View.VISIBLE);drawerLayer.setAlpha(0f);drawerLayer.animate().alpha(1f).setDuration(reducedMotion()?0:160).start();drawer.setTranslationX(-dp(24));drawer.animate().translationX(0).setDuration(reducedMotion()?0:220).start();}
    private void closeDrawer(){if(drawerLayer==null||drawerLayer.getVisibility()!=View.VISIBLE)return;drawerLayer.animate().alpha(0f).setDuration(reducedMotion()?0:120).withEndAction(()->drawerLayer.setVisibility(View.GONE)).start();}
    private boolean reducedMotion(){try{return android.provider.Settings.Global.getFloat(getContentResolver(),"animator_duration_scale",1f)<=0f;}catch(Exception e){return false;}}
    private void buildDrawerContents(){
        drawer.removeAllViews();drawer.setPadding(dp(12),dp(14),dp(12),dp(16));
        LinearLayout head=row();head.setPadding(dp(8),0,0,0);drawer.addView(head,lp(-1,64));
        TextView avatar=tx("K",20,accentOn,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(lime,24));head.addView(avatar,lp(48,48));
        LinearLayout who=col();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(12);head.addView(who,wp);who.addView(tx("Kadamm athlete",17,ink,true));who.addView(tx("Guest profile · saved on this phone",11,muted,false));
        TextView close=tx("×",26,muted,false);close.setGravity(Gravity.CENTER);close.setContentDescription("Close menu");close.setFocusable(true);close.setBackground(rippleOn(Color.TRANSPARENT,24));head.addView(close,lp(48,48));close.setOnClickListener(v->closeDrawer());
        JSONArray sessions=history();int count=countOn(sessions,todayKey(),null);boolean data=DaySteps.hasTodayData(this);long n=DaySteps.todayTotal(this);int goal=dailyGoal();
        LinearLayout today=col();today.setPadding(dp(16),dp(14),dp(16),dp(14));today.setBackground(shape(raised,18));LinearLayout.LayoutParams tp=lp(-1,-2);tp.topMargin=dp(12);tp.bottomMargin=dp(14);drawer.addView(today,tp);
        today.addView(tx("TODAY",10,muted,true));today.addView(tx(data?String.format(Locale.US,"%,d steps",n):"Steps not set up",22,ink,true));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(goal);bar.setProgress((int)Math.min(goal,n));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(n>=goal?positive:lime));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(line));LinearLayout.LayoutParams bp=lp(-1,6);bp.topMargin=dp(8);bp.bottomMargin=dp(8);today.addView(bar,bp);
        today.addView(tx(String.format(Locale.US,"%.2f km · %d %s · goal %,d",totalDistanceOn(sessions,todayKey()),count,count==1?"session":"sessions",goal),11,muted,false));
        drawerAction("Today","◷","Today");drawerAction("History","▦","History");drawerAction("Profile","◉","Profile");
        View rule=new View(this);rule.setBackgroundColor(line);LinearLayout.LayoutParams rp=lp(-1,1);rp.setMargins(dp(8),dp(10),dp(8),dp(6));drawer.addView(rule,rp);
        drawerAction("Goals & targets","◎","Goals");drawerAction("Step sources & health","＋","Health");
        drawer.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=tx("Kadamm v"+APP_VERSION+" · Every Process. Every Signal. One View.",10,muted,false);footer.setPadding(dp(8),0,0,0);drawer.addView(footer);
    }
    /** Full-width 52dp row: the whole row is the touch target, with ripple and an active indicator. */
    private void drawerAction(String label,String icon,String target){
        boolean selected=target.equals(page);LinearLayout item=row();item.setBackground(rippleOn(selected?raised:Color.TRANSPARENT,14));LinearLayout.LayoutParams p=lp(-1,52);p.topMargin=dp(4);drawer.addView(item,p);
        View bar=new View(this);bar.setBackground(shape(selected?lime:Color.TRANSPARENT,2));LinearLayout.LayoutParams bp=lp(4,24);bp.leftMargin=dp(4);item.addView(bar,bp);
        TextView glyph=tx(icon,20,selected?lime:muted,true);glyph.setGravity(Gravity.CENTER);item.addView(glyph,lp(48,-1));
        item.addView(tx(label,15,selected?lime:ink,selected),new LinearLayout.LayoutParams(0,-2,1));
        item.setFocusable(true);item.setClickable(true);item.setContentDescription(label+(selected?", selected":""));item.setOnClickListener(v->navigate(target));
    }
    private android.graphics.drawable.Drawable ripple(android.graphics.drawable.Drawable content){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(darkTheme?56:38,Color.red(ink),Color.green(ink),Color.blue(ink))),content,content);}
    private android.graphics.drawable.Drawable rippleOn(int color,int radius){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(darkTheme?56:38,Color.red(ink),Color.green(ink),Color.blue(ink))),shape(color,radius),shape(Color.WHITE,radius));}
    private AlertDialog.Builder dialog(){return new AlertDialog.Builder(this,darkTheme?android.R.style.Theme_DeviceDefault_Dialog_Alert:android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);}
    /** The Kadamm wordmark as a real button: 48dp tall, whole area opens the menu. */
    private LinearLayout brandButton(){
        LinearLayout b=row();b.setPadding(dp(8),0,dp(10),0);b.setBackground(rippleOn(Color.TRANSPARENT,14));b.setFocusable(true);b.setClickable(true);b.setContentDescription("Kadamm menu");b.setOnClickListener(v->openDrawer());
        android.widget.ImageView menu=new android.widget.ImageView(this);menu.setImageResource(R.drawable.ic_kadamm_logo);b.addView(menu,lp(30,30));TextView name=tx("KADAMM",15,lime,true);name.setLetterSpacing(.14f);LinearLayout.LayoutParams np=lp(-2,-2);np.leftMargin=dp(10);b.addView(name,np);
        TextView version=tx("v"+APP_VERSION,9,muted,true);LinearLayout.LayoutParams vp=lp(-2,-2);vp.leftMargin=dp(7);b.addView(version,vp);return b;
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
    private void renderPageNow(){renderedPage=page;content.removeAllViews();styleNavItem(navToday,page.equals("Today"));styleNavItem(navHistory,page.equals("History"));styleNavItem(navProfile,page.equals("Profile"));if(page.equals("Today"))renderToday();else if(page.equals("History"))renderHistory();else if(page.equals("Profile"))renderProfile();else if(page.equals("Goals"))renderGoals();else if(page.equals("Health"))renderHealth();else renderDetail(lastSession);}
    private void styleNavItem(LinearLayout item,boolean selected){int color=selected?lime:muted;item.setBackground(rippleOn(selected?raised:Color.TRANSPARENT,14));((TextView)item.getChildAt(0)).setTextColor(color);((TextView)item.getChildAt(1)).setTextColor(selected?ink:muted);}
    private void brand(String eyebrow,String headline,String sub){LinearLayout brand=row();content.addView(brand,lp(-1,48));LinearLayout.LayoutParams bbp=lp(-2,48);bbp.leftMargin=-dp(8);brand.addView(brandButton(),bbp);TextView e=tx(eyebrow.toUpperCase(Locale.US),10,muted,true);LinearLayout.LayoutParams ep=lp(-1,25);ep.topMargin=dp(8);content.addView(e,ep);TextView h=tx(headline,29,ink,true);content.addView(h);if(sub!=null){TextView s=tx(sub,13,muted,false);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(5);sp.bottomMargin=dp(16);content.addView(s,sp);}}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(panelShape(16));LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(12);content.addView(c,p);return c;}
    private GradientDrawable panelShape(int radius){GradientDrawable d=shape(panel,radius);d.setStroke(dp(1),line);return d;}
    private void renderToday(){JSONArray hist=history();dayTotals=DaySteps.allDayTotals(this);loadIdleLocation();renderHeader();renderStepSetup();renderHero(hist);renderBento(hist);renderDistanceGoal(hist);
        LinearLayout mapCard=card();trackCard=mapCard;LinearLayout head=row();TextView title=tx("Live route",16,ink,true);head.addView(title,new LinearLayout.LayoutParams(0,dp(26),1));status=tx("● READY",10,lime,true);head.addView(status);mapCard.addView(head);FrameLayout mapFrame=new FrameLayout(this);LinearLayout.LayoutParams mfp=lp(-1,190);mfp.topMargin=dp(8);mapCard.addView(mapFrame,mfp);route=new RouteView(this);route.setTheme(bg,raised,lime,darkTheme);mapFrame.addView(route,new FrameLayout.LayoutParams(-1,-1));Button follow=button("◎",raised,ink);FrameLayout.LayoutParams flp=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.RIGHT|Gravity.TOP);flp.setMargins(0,dp(10),dp(10),0);mapFrame.addView(follow,flp);follow.setContentDescription("Follow my location");follow.setOnClickListener(v->{route.setFollowLocation(true);if(prefs.getBoolean("gpsFix",false))route.setCurrentLocation(prefs.getFloat("latitude",0),prefs.getFloat("longitude",0),prefs.getFloat("accuracy",0));else toast("Waiting for a reliable GPS fix");});LinearLayout mapMeta=row();mapCard.addView(mapMeta,lp(-1,26));accuracyLabel=tx("GPS accuracy · waiting for signal",10,muted,false);mapMeta.addView(accuracyLabel,new LinearLayout.LayoutParams(0,dp(26),1));TextView standard=tx("Standard map",10,muted,false);mapMeta.addView(standard);TextView mapHint=tx("© OpenStreetMap contributors · satellite tiles are not available from this map source",9,muted,false);mapCard.addView(mapHint);
        LinearLayout box=card();TextView tr=tx(prefs.getBoolean("active",false)?"Activity type":"Track an activity",16,ink,true);box.addView(tr);activityModeLabel=tx(prefs.getBoolean("active",false)?"Automatic detection · tap to correct manually":"Walk or run",11,muted,false);box.addView(activityModeLabel);LinearLayout modes=row();LinearLayout.LayoutParams modep=lp(-1,38);modep.topMargin=dp(8);box.addView(modes,modep);String savedMode=prefs.getString("activityMode","outdoor");for(String[] opt:new String[][]{{"Outdoor","outdoor"},{"Indoor / treadmill","indoor"}}){Button mb=smallChoice(opt[0],savedMode.equals(opt[1]));LinearLayout.LayoutParams xx=new LinearLayout.LayoutParams(0,-1,1);if(modes.getChildCount()>0)xx.leftMargin=dp(6);modes.addView(mb,xx);mb.setOnClickListener(v->{if(!prefs.getBoolean("active",false)){prefs.edit().putString("activityMode",opt[1]).apply();renderPage();}});}LinearLayout types=row();LinearLayout.LayoutParams typ=lp(-1,38);typ.topMargin=dp(8);box.addView(types,typ);String current=prefs.getBoolean("active",false)?prefs.getString("currentType",activityType):activityType;Button walk=smallChoice("Walk",current.equals("Walk"));Button run=smallChoice("Run",current.equals("Run"));types.addView(walk,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams rlp=lp(90,-1);rlp.leftMargin=dp(8);types.addView(run,rlp);walk.setOnClickListener(v->{if(prefs.getBoolean("active",false)){setManualType("Walk");}else{activityType="Walk";renderPage();}});run.setOnClickListener(v->{if(prefs.getBoolean("active",false)){setManualType("Run");}else{activityType="Run";renderPage();}});
        timer=tx("00:00",38,ink,false);timer.setTypeface(Typeface.create("monospace",0));LinearLayout.LayoutParams tp=lp(-1,55);tp.topMargin=dp(13);box.addView(timer,tp);box.addView(tx("ELAPSED TIME",10,muted,true));LinearLayout stats=row();LinearLayout.LayoutParams stp=lp(-1,82);stp.topMargin=dp(10);box.addView(stats,stp);distance=value(stats,"DISTANCE","0.00 km");pace=value(stats,"AVG. PACE","— /km");steps=value(stats,"STEPS","0");LinearLayout stats2=row();box.addView(stats2,lp(-1,66));liveSpeed=value(stats2,"SPEED","— km/h");liveElev=value(stats2,"ELEV. GAIN","— m");liveKcal=value(stats2,"CALORIES","0 kcal");
        LinearLayout actions=row();LinearLayout.LayoutParams ap=lp(-1,48);ap.topMargin=dp(12);box.addView(actions,ap);start=button(prefs.getBoolean("active",false)?(prefs.getBoolean("paused",false)?"▶  Resume":"Ⅱ  Pause"):"▶  Start activity",lime,accentOn);actions.addView(start,new LinearLayout.LayoutParams(0,-1,1));start.setOnClickListener(v->onRecord());Button finish=button("Finish",raised,ink);LinearLayout.LayoutParams fp=lp(72,-1);fp.leftMargin=dp(6);actions.addView(finish,fp);finish.setOnClickListener(v->finishActivity());Button discard=button("Discard",raised,critical);LinearLayout.LayoutParams dp=lp(78,-1);dp.leftMargin=dp(6);actions.addView(discard,dp);discard.setOnClickListener(v->discardActivity());
        renderDailyInsights(hist);renderActivityInsights(hist);renderTrackingHealth();TextView recentLabel=tx("RECENT ACTIVITY",11,muted,true);LinearLayout.LayoutParams rl=lp(-1,34);rl.topMargin=dp(20);content.addView(recentLabel,rl);JSONArray sorted=history();if(sorted.length()==0){LinearLayout empty=card();empty.addView(tx("Your sessions will show up here.",13,muted,false));}else for(int i=sorted.length()-1;i>=Math.max(0,sorted.length()-3);i--){final JSONObject session=sorted.optJSONObject(i);sessionRow(session,()->{lastSession=session;page="Detail";renderPage();});}
    }
    private void renderActivityInsights(JSONArray all){LinearLayout c=card();LinearLayout hd=row();hd.addView(tx("Activity overview",16,ink,true),new LinearLayout.LayoutParams(0,dp(28),1));c.addView(hd);c.addView(tx("Completed sessions · local workout data",11,muted,false));LinearLayout ranges=row();LinearLayout.LayoutParams rlp=lp(-1,38);rlp.topMargin=dp(10);c.addView(ranges,rlp);for(String range:new String[]{"7D","30D","12M","ALL"}){boolean selected=range.equals(activityRange);Button b=button(range,selected?lime:raised,selected?accentOn:ink);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(ranges.getChildCount()>0)bp.leftMargin=dp(5);ranges.addView(b,bp);b.setOnClickListener(v->{activityRange=range;renderPage();});}
        JSONArray visible=sessionsInRange(all,activityRange);double km=distanceAll(visible);long duration=0,totalSteps=0;for(int i=0;i<visible.length();i++){JSONObject o=visible.optJSONObject(i);if(o!=null){duration+=o.optLong("durationMs");if(o.optBoolean("stepsAvailable",false))totalSteps+=o.optInt("steps");}}LinearLayout row1=row();LinearLayout.LayoutParams st=lp(-1,58);st.topMargin=dp(15);c.addView(row1,st);summaryValue(row1,"SESSIONS",String.valueOf(visible.length()));summaryValue(row1,"DISTANCE",String.format(Locale.US,"%.2f km",km));LinearLayout row2=row();c.addView(row2,lp(-1,52));summaryValue(row2,"TIME",formatDuration(duration));summaryValue(row2,"VERIFIED STEPS",String.format(Locale.US,"%,d",totalSteps));TextView chartTitle=tx("Distance",10,muted,true);LinearLayout.LayoutParams ct=lp(-1,20);ct.topMargin=dp(9);c.addView(chartTitle,ct);ActivityChartView chart=new ActivityChartView(this,visible,activityRange,chartTheme());LinearLayout.LayoutParams ch=lp(-1,128);ch.topMargin=dp(3);c.addView(chart,ch);}
    private void renderDailyInsights(JSONArray all){
        Map<String,Long> totals=dayTotals==null?DaySteps.allDayTotals(this):dayTotals;int goal=dailyGoal();long sum=0;int hit=0;
        for(int i=0;i<7;i++){Calendar d=Calendar.getInstance();d.add(Calendar.DAY_OF_YEAR,-i);Long v=totals.get(dateKey(d));if(v!=null){sum+=v;if(v>=goal)hit++;}}
        LinearLayout weekly=card();LinearLayout wh=row();wh.addView(tx("This week",16,ink,true),new LinearLayout.LayoutParams(0,dp(26),1));wh.addView(tx(hit+" of 7 goals",11,hit>0?positive:muted,true));weekly.addView(wh);
        weekly.addView(tx(String.format(Locale.US,"%,d steps in the last 7 days · dashed line is your %,d goal",sum,goal),11,muted,false));
        WeeklyActivityView chart=new WeeklyActivityView(this,totals,goal,chartTheme());LinearLayout.LayoutParams cp=lp(-1,150);cp.topMargin=dp(8);weekly.addView(chart,cp);
        int[] st=DaySteps.streaks(totals,goal);LinearLayout streak=card();LinearLayout head=row();head.addView(tx("Goal streak",16,ink,true),new LinearLayout.LayoutParams(0,dp(28),1));head.addView(tx(st[0]+(st[0]==1?" day":" days"),16,st[0]>0?positive:muted,true));streak.addView(head);
        detailRow(streak,"LONGEST STREAK",st[1]+(st[1]==1?" day":" days"),"GOALS REACHED",st[2]+(st[2]==1?" day":" days"));
        streak.addView(tx("A day counts when you reach your current step goal. Today stays open until midnight, so an unfinished day never breaks a streak.",10,muted,false));
        renderStepSources();
    }
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
    private int todayCalories(JSONArray all){int n=0;for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&dateKey(s).equals(todayKey()))n+=s.optInt("calories",(int)Math.round(s.optDouble("distanceKm")*65));}if(prefs.getBoolean("active",false))n+=Math.round(prefs.getFloat("distance",0)*65);return n;}
    private long todayDuration(JSONArray all){long n=0;for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&dateKey(s).equals(todayKey()))n+=s.optLong("durationMs",0);}if(prefs.getBoolean("active",false))n+=prefs.getLong("elapsed",0);return n;}
    private double distanceByType(JSONArray all,String day,String type){double n=0;for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&dateKey(s).equals(day)){String key=type.toLowerCase(Locale.US)+"DistanceKm";if(s.has(key))n+=s.optDouble(key);else if(type.equals(s.optString("type","Walk")))n+=s.optDouble("distanceKm");}}if(day.equals(todayKey())&&prefs.getBoolean("active",false))n+=prefs.getFloat(type.toLowerCase(Locale.US)+"DistanceKm",0);return n;}
    private TextView summaryValue(LinearLayout parent,String label,String value){LinearLayout box=col();parent.addView(box,new LinearLayout.LayoutParams(0,-1,1));box.addView(tx(label,9,muted,true));TextView v=tx(value,16,ink,true);box.addView(v);return v;}
    private JSONArray sessionsInRange(JSONArray all,String range){if("ALL".equals(range))return all;Calendar now=Calendar.getInstance();long end=now.getTimeInMillis();Calendar start=(Calendar)now.clone();if("30D".equals(range))start.add(Calendar.DAY_OF_YEAR,-29);else if("12M".equals(range))start.add(Calendar.MONTH,-11);else start.add(Calendar.DAY_OF_YEAR,-6);start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);JSONArray out=new JSONArray();for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s==null)continue;try{Date d=new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(s.optString("day",""));if(d!=null&&d.getTime()>=start.getTimeInMillis()&&d.getTime()<=end)out.put(s);}catch(Exception ignored){}}return out;}
    private KadammTheme chartTheme(){return KadammTheme.resolve(this,getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("themeMode","system"));}
    private void renderTrackingHealth(){
        LinearLayout c=card();c.addView(tx("Tracking readiness",15,ink,true));
        boolean location=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        SensorManager sm=(SensorManager)getSystemService(SENSOR_SERVICE);boolean sensor=sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!=null||sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)!=null,allowed=DaySteps.permitted(this);
        boolean notify=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        boolean health=getSharedPreferences("kadamm_health",MODE_PRIVATE).getBoolean("healthConnected",false);
        healthRow(c,"GPS LOCATION",location?"Ready":"Asked when you start",location?positive:warning);
        healthRow(c,"STEP SENSOR",!sensor?"Not on this phone":allowed?"Ready":"Permission needed",sensor&&allowed?positive:warning);
        healthRow(c,"NOTIFICATIONS",notify?"Ready":"Off · no live session card",notify?positive:warning);
        healthRow(c,"HEALTH CONNECT",health?"Connected":"Optional · not connected",health?positive:muted);
        android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);boolean free=pm!=null&&pm.isIgnoringBatteryOptimizations(getPackageName());
        healthRow(c,"BACKGROUND",free?"Unrestricted":"Battery saver may pause it",free?positive:warning);
    }
    private void healthRow(LinearLayout c,String label,String value,int state){LinearLayout r=row();LinearLayout.LayoutParams p=lp(-1,34);c.addView(r,p);r.addView(tx(label,10,muted,true),new LinearLayout.LayoutParams(0,-2,1));r.addView(tx("●  "+value,11,state,true));}
    private TextView metric(LinearLayout parent,String label,String value,String unit){LinearLayout c=col();c.setPadding(dp(12),dp(13),dp(8),dp(9));c.setBackground(panelShape(14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(94),1);if(parent.getChildCount()>0)p.leftMargin=dp(8);parent.addView(c,p);c.addView(tx(label,9,muted,true));TextView v=tx(value,24,ink,true);LinearLayout.LayoutParams vp=lp(-1,34);vp.topMargin=dp(4);c.addView(v,vp);c.addView(tx(unit,10,muted,false));if(label.startsWith("TODAY"))todayCount=v;else if(label.equals("DISTANCE"))todayDistance=v;else if(label.equals("ACTIVE TIME"))todayActiveTime=v;else if(label.equals("WALKING"))todayWalkDistance=v;else if(label.equals("RUNNING"))todayRunDistance=v;return v;}
    private TextView value(LinearLayout parent,String label,String val){LinearLayout c=col();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);parent.addView(c,p);c.addView(tx(label,9,muted,true));TextView v=tx(val,17,ink,false);v.setTypeface(Typeface.create("monospace",0));LinearLayout.LayoutParams vp=lp(-1,34);vp.topMargin=dp(5);c.addView(v,vp);return v;}
    private Button button(String s,int color,int textColor){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(textColor);GradientDrawable base=shape(color,11);base.setStroke(dp(1),line);b.setBackground(ripple(base));b.setOnFocusChangeListener((v,has)->{GradientDrawable d=shape(color,11);d.setStroke(dp(has?2:1),has?focus:line);b.setBackground(ripple(d));});return b;}private Button smallChoice(String s,boolean selected){int selectedColor="Run".equals(s)?violet:lime;Button b=button(s,selected?selectedColor:raised,selected?accentOn:ink);return b;}
    private void renderHistory(){dayTotals=DaySteps.allDayTotals(this);brand("YOUR ACTIVITY","History","Browse your sessions by day.");LinearLayout month=row();Button prev=button("‹",raised,ink),next=button("›",raised,ink);month.addView(prev,lp(42,40));TextView label=tx(monthName(),15,ink,true);label.setGravity(Gravity.CENTER);month.addView(label,new LinearLayout.LayoutParams(0,dp(40),1));month.addView(next,lp(42,40));content.addView(month);prev.setOnClickListener(v->{shownMonth.add(Calendar.MONTH,-1);renderPage();});next.setOnClickListener(v->{shownMonth.add(Calendar.MONTH,1);renderPage();});
        LinearLayout cal=card();String[] days={"M","T","W","T","F","S","S"};LinearLayout dr=row();cal.addView(dr);for(String d:days){TextView t=tx(d,10,muted,true);t.setGravity(Gravity.CENTER);dr.addView(t,new LinearLayout.LayoutParams(0,dp(30),1));}Calendar first=(Calendar)shownMonth.clone();first.set(Calendar.DAY_OF_MONTH,1);int offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7;int count=first.getActualMaximum(Calendar.DAY_OF_MONTH);JSONArray h=history();for(int wk=0;wk<6;wk++){LinearLayout rr=row();cal.addView(rr,lp(-1,42));for(int col=0;col<7;col++){int n=wk*7+col-offset+1;TextView d=tx(n>0&&n<=count?String.valueOf(n):"",13,ink,false);d.setGravity(Gravity.CENTER);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,1);rr.addView(d,dp);if(n>0&&n<=count){Calendar date=(Calendar)shownMonth.clone();date.set(Calendar.DAY_OF_MONTH,n);int sessions=countOn(h,dateKey(date),null);boolean today=isSameDate(date,Calendar.getInstance()),selected=isSameDate(date,selectedDate);GradientDrawable dateBg=shape(calendarColor(h,date),12);if(today||selected)dateBg.setStroke(dp(today?2f:1.5f),lime);d.setBackground(dateBg);String spoken=new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(date.getTime());d.setContentDescription(spoken+". "+sessions+" "+(sessions==1?"session":"sessions")+". Long press to show count.");d.setOnClickListener(v->{selectedDate=(Calendar)date.clone();renderPage();});d.setOnLongClickListener(v->{showSessionCountPopup(d,date,sessions);return true;});}}}
        LinearLayout legend=card();legend.addView(tx("ACTIVITY KEY",9,muted,true));TextView key=tx("Green · goal reached    Orange · 3,000+ steps    Yellow · under 3,000    Red · run logged    Grey · session without steps",10,muted,false);legend.addView(key);String selectedKey=dateKey(selectedDate);LinearLayout dateSummary=card();dateSummary.addView(tx("SELECTED DAY",9,muted,true));long selectedSteps=todayStepsFor(h,selectedKey);double selectedKm=totalDistanceOn(h,selectedKey);long selectedMs=durationOn(h,selectedKey);detailRow(dateSummary,"STEPS",stepDisplay(h,selectedKey),"DISTANCE",String.format(Locale.US,"%.2f km",selectedKm));detailRow(dateSummary,"DURATION",formatDuration(selectedMs),"EST. CALORIES",Math.round(selectedKm*65)+" kcal");
        TextView day=tx(new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(selectedDate.getTime()),14,ink,true);LinearLayout.LayoutParams dp=lp(-1,38);dp.topMargin=dp(12);content.addView(day,dp);boolean found=false;for(int i=h.length()-1;i>=0;i--){JSONObject o=h.optJSONObject(i);if(o!=null&&dateKey(o).equals(dateKey(selectedDate))){sessionRow(o,()->{lastSession=o;page="Detail";renderPage();});found=true;}}if(!found){LinearLayout empty=card();empty.addView(tx("No sessions on this day",13,muted,false));}}
    private int tint(int color){return Color.argb(darkTheme?105:75,Color.red(color),Color.green(color),Color.blue(color));}
    private int calendarColor(JSONArray h,Calendar d){String key=dateKey(d);Long v=dayTotals==null?null:dayTotals.get(key);long count=v==null?0:v;if(count>=dailyGoal())return tint(positive);if(runDistanceOn(h,key)>0)return tint(critical);if(count>=3000)return tint(Color.rgb(249,115,22));if(count>0)return tint(Color.rgb(234,179,8));return countOn(h,key,null)>0?raised:Color.TRANSPARENT;}
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
        goalProgress(sum,"Distance",Math.round(totalDistanceOn(h,todayKey())*1000),st.getInt("distanceGoalM",5000),orange(),String.format(Locale.US,"%.2f / %.1f km",totalDistanceOn(h,todayKey()),st.getInt("distanceGoalM",5000)/1000f));
        goalProgress(sum,"Move minutes",todayDuration(h)/60000,st.getInt("activeGoalMin",30),violet,String.format(Locale.US,"%d / %d min",todayDuration(h)/60000,st.getInt("activeGoalMin",30)));
        goalCard("Daily steps","Your main goal. The outer ring fills toward it.","★",lime,"stepGoal",10000,1000,50000,500,new int[]{6000,8000,10000,12000,15000},v->String.format(Locale.US,"%,d steps",v),v->(v/1000)+"k");
        goalCard("Daily distance","From GPS sessions and indoor estimates.","↗",orange(),"distanceGoalM",5000,500,30000,500,new int[]{3000,5000,8000,10000},v->String.format(Locale.US,"%.1f km",v/1000f),v->(v/1000)+" km");
        goalCard("Move minutes","Time spent in Kadamm sessions. The inner ring.","◷",violet,"activeGoalMin",30,10,180,5,new int[]{20,30,45,60},v->v+" min",v->v+" min");
        goalCard("Indoor stride length","Turns treadmill steps into distance. Walk 10 m, count your steps, and divide.","⇅",teal(),"strideLengthCm",72,40,130,1,new int[]{65,72,80},v->String.format(Locale.US,"%.2f m per step",v/100f),v->v+" cm");
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
        TextView badge=tx(icon,16,darkTheme?Color.rgb(24,25,28):Color.WHITE,true);badge.setGravity(Gravity.CENTER);badge.setBackground(shape(color,20));head.addView(badge,lp(40,40));
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
    private void renderProfile(){JSONArray h=history();brand("YOUR SPACE","Guest runner","Your Kadamm profile is ready.");LinearLayout card=card();TextView avatar=tx("K",28,accentOn,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(lime,32));card.addView(avatar,lp(58,58));TextView name=tx("Kadamm athlete",19,ink,true);LinearLayout.LayoutParams np=lp(-1,34);np.topMargin=dp(13);card.addView(name,np);card.addView(tx("Local profile · Google sign-in coming later",12,muted,false));LinearLayout totals=row();LinearLayout.LayoutParams tp=lp(-1,82);tp.topMargin=dp(18);card.addView(totals,tp);profileStat(totals,"SESSIONS",String.valueOf(h.length()));profileStat(totals,"DISTANCE",String.format(Locale.US,"%.1f km",distanceAll(h)));profileStat(totals,"VERIFIED STEPS",stepsAll(h)>0?String.format(Locale.US,"%,d",stepsAll(h)):"Unavailable");LinearLayout history=card();history.addView(tx("Activity calendar",16,ink,true));TextView go=tx("See your days and sessions  →",13,lime,true);LinearLayout.LayoutParams gp=lp(-1,40);gp.topMargin=dp(8);history.addView(go,gp);go.setOnClickListener(v->{page="History";renderPage();});TextView goalsLink=tx("Goals & targets  →",13,lime,true);card.addView(goalsLink,lp(-1,36));goalsLink.setOnClickListener(v->navigate("Goals"));
        LinearLayout appearance=card();appearance.addView(tx("Appearance",16,ink,true));appearance.addView(tx("Choose how Kadamm looks on this device.",12,muted,false));String selected=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getString("themeMode","system");LinearLayout choices=row();LinearLayout.LayoutParams cp=lp(-1,42);cp.topMargin=dp(12);appearance.addView(choices,cp);for(String mode:new String[]{"Light","Dark","System"}){boolean active=mode.equalsIgnoreCase(selected);Button b=button(mode,active?lime:raised,active?accentOn:ink);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(choices.getChildCount()>0)bp.leftMargin=dp(7);choices.addView(b,bp);b.setOnClickListener(v->{getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putString("themeMode",mode.toLowerCase(Locale.US)).apply();applyTheme();renderPage();});}
    }
    private void profileStat(LinearLayout p,String l,String val){LinearLayout c=col();p.addView(c,new LinearLayout.LayoutParams(0,-1,1));c.addView(tx(l,9,muted,true));c.addView(tx(val,16,ink,true));}
    private void renderDetail(JSONObject o){if(o==null){page="History";renderPage();return;}TextView back=tx("←  HISTORY",11,lime,true);content.addView(back,lp(-1,28));back.setOnClickListener(v->{page="History";renderPage();});String dt=o.optString("date","");brand(dt,sessionTitle(o),"Session summary · "+o.optString("time",""));RouteView rv=new RouteView(this);rv.setTheme(bg,raised,lime,darkTheme);rv.setRoute(o.optString("route","[]"));rv.setBackground(shape(panel,16));LinearLayout.LayoutParams rp=lp(-1,280);content.addView(rv,rp);TextView attribution=tx("© OpenStreetMap contributors · GPS route stored on device",10,muted,false);LinearLayout.LayoutParams ap=lp(-1,28);ap.topMargin=dp(5);content.addView(attribution,ap);LinearLayout c=card();long ms=o.optLong("durationMs",0);double km=o.optDouble("distanceKm",0);double speed=ms>0?km/(ms/3600000d):0;detailRow(c,"DISTANCE",String.format(Locale.US,"%.2f km",km),"DURATION",formatDuration(ms));detailRow(c,"AVG. SPEED",String.format(Locale.US,"%.1f km/h",speed),"AVG. PACE",km>0?String.format(Locale.US,"%d:%02d /km",Math.round(ms/1000d/km)/60,Math.round(ms/1000d/km)%60):"—");detailRow(c,"STEPS",String.format(Locale.US,"%,d",o.optInt("steps")),"EST. CALORIES",String.format(Locale.US,"~%d kcal",o.optInt("calories",(int)Math.round(km*65))));detailRow(c,"MOVING TIME",formatDuration(o.optLong("movingMs",ms)),"ELEV. GAIN",o.has("elevGainM")?String.format(Locale.US,"%.0f m",o.optDouble("elevGainM")):"—");if(o.has("walkDistanceKm")||o.has("runDistanceKm")){long wms=o.optLong("walkDurationMs"),rms=o.optLong("runDurationMs");double wkm=o.optDouble("walkDistanceKm"),rkm=o.optDouble("runDistanceKm");detailRow(c,"WALK",String.format(Locale.US,"%.2f km · %s",wkm,formatDuration(wms)),"RUN",String.format(Locale.US,"%.2f km · %s",rkm,formatDuration(rms)));detailRow(c,"WALK PACE",wkm>0?String.format(Locale.US,"%d:%02d /km",Math.round(wms/1000d/wkm)/60,Math.round(wms/1000d/wkm)%60):"—","RUN PACE",rkm>0?String.format(Locale.US,"%d:%02d /km",Math.round(rms/1000d/rkm)/60,Math.round(rms/1000d/rkm)%60):"—");}if(o.optBoolean("distanceEstimated",false)){TextView estimate=tx("Indoor distance is estimated from your configured stride length.",11,warning,false);c.addView(estimate);TextView correction=tx("Correct with treadmill distance",13,lime,true);c.addView(correction,lp(-1,42));correction.setOnClickListener(v->correctDistance(o));}TextView note=tx(o.optString("activityLabel","").isEmpty()?"Session saved on this device":o.optString("activityLabel")+" · saved on this device",11,muted,false);LinearLayout.LayoutParams np=lp(-1,26);np.topMargin=dp(9);c.addView(note,np);sessionActions(o);}
    private void correctDistance(JSONObject session){android.widget.EditText input=new android.widget.EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setText(String.format(Locale.US,"%.2f",session.optDouble("distanceKm")));dialog().setTitle("Correct distance").setMessage("Enter the distance shown on the treadmill, in kilometres.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{try{double km=Double.parseDouble(input.getText().toString().trim());if(km<0||km>500){toast("Enter a distance from 0 to 500 km");return;}session.put("distanceKm",km);session.put("calories",Math.round(km*65));session.put("distanceEstimated",false);session.put("distanceCorrected",true);saveSession(historyPrefs,session);lastSession=session;renderPage();}catch(Exception e){toast("Enter a valid distance");}}).show();}
    private void detailRow(LinearLayout c,String a,String av,String b,String bv){LinearLayout r=row();LinearLayout.LayoutParams p=lp(-1,54);c.addView(r,p);LinearLayout x=col(),y=col();r.addView(x,new LinearLayout.LayoutParams(0,-1,1));r.addView(y,new LinearLayout.LayoutParams(0,-1,1));x.addView(tx(a,9,muted,true));x.addView(tx(av,17,ink,true));y.addView(tx(b,9,muted,true));y.addView(tx(bv,17,ink,true));}
    private void sessionRow(JSONObject o,Runnable click){if(o==null)return;LinearLayout r=row();r.setPadding(dp(14),dp(12),dp(12),dp(12));r.setBackground(panelShape(14));LinearLayout.LayoutParams p=lp(-1,82);p.topMargin=dp(8);content.addView(r,p);LinearLayout desc=col();r.addView(desc,new LinearLayout.LayoutParams(0,-1,1));desc.addView(tx(sessionLabel(o)+"  ·  "+formatDuration(o.optLong("durationMs")),14,ink,true));double km=o.optDouble("distanceKm",0);long ms=o.optLong("durationMs",0);long sec=km>0?Math.round(ms/1000d/km):0;desc.addView(tx(String.format(Locale.US,"%.2f km  ·  %s  ·  %,d steps",km,o.optString("time",""),o.optInt("steps")),10,muted,false));desc.addView(tx(String.format(Locale.US,"%d:%02d /km  ·  ~%d kcal",sec/60,sec%60,o.optInt("calories",(int)Math.round(km*65))),10,muted,false));String savedRoute=o.optString("route", "[]");if(!"[]".equals(savedRoute)){RouteView preview=new RouteView(this);preview.setTheme(bg,raised,lime,darkTheme);preview.setRoute(savedRoute);LinearLayout.LayoutParams pv=lp(58,58);pv.setMargins(dp(5),0,dp(5),0);r.addView(preview,pv);}r.addView(tx("›",25,muted,false));r.setOnClickListener(v->click.run());}
    private void finishActivity(){if(!prefs.getBoolean("active",false)){toast("No activity to finish");return;}dialog().setTitle("Finish activity?").setMessage("Save this route and its session statistics to History?").setNegativeButton("Keep tracking",null).setPositiveButton("Finish",(d,w)->{closeLive();command(RecorderService.ACTION_STOP);new Handler(Looper.getMainLooper()).postDelayed(()->{JSONArray h=history();lastSession=h.optJSONObject(h.length()-1);if(lastSession!=null){page="Detail";renderPage();}else renderPage();},500);}).show();}
    private void discardActivity(){if(!prefs.getBoolean("active",false)){toast("No activity to discard");return;}dialog().setTitle("Discard activity?").setMessage("This session and its route will be deleted without saving.").setNegativeButton("Keep tracking",null).setPositiveButton("Discard",(d,w)->{command(RecorderService.ACTION_DISCARD);toast("Activity discarded");renderPage();}).show();}
    private void setManualType(String type){Intent i=new Intent(this,RecorderService.class);i.setAction(RecorderService.ACTION_SET_TYPE);i.putExtra("type",type);startService(i);toast("Activity type set to "+type);renderPage();}
    private void onRecord(){if(prefs.getBoolean("active",false)){command(RecorderService.ACTION_TOGGLE);return;}prefs.edit().putString("activityType",activityType).apply();ArrayList<String> r=new ArrayList<>();boolean indoor="indoor".equals(prefs.getString("activityMode","outdoor"));if(!indoor){boolean gpsOn=false;try{gpsOn=((LocationManager)getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}if(!gpsOn){dialog().setTitle("Turn on location").setMessage("Outdoor tracking needs GPS to draw your route. Turn on Location, then start again, or switch to Indoor / treadmill.").setNegativeButton("Not now",null).setPositiveButton("Open settings",(d,w)->{try{startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(Exception ignored){}}).show();return;}}if(!indoor&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)r.add(Manifest.permission.ACCESS_FINE_LOCATION);if(!indoor&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)r.add(Manifest.permission.ACCESS_COARSE_LOCATION);if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)r.add(Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)r.add(Manifest.permission.POST_NOTIFICATIONS);if(!r.isEmpty()){requestPermissions(r.toArray(new String[0]),PERMISSIONS);return;}command(RecorderService.ACTION_START);openLive();askBackground();}
    @Override public void onRequestPermissionsResult(int q,String[] ps,int[] rs){super.onRequestPermissionsResult(q,ps,rs);if(q==STEP_PERMISSION){if(DaySteps.permitted(this)){startStepCounting();toast("Step counting is on");}else if(Build.VERSION.SDK_INT>=29)getSharedPreferences("kadamm_settings",MODE_PRIVATE).edit().putBoolean("stepPermDenied",true).apply();renderPage();return;}startStepCounting();if(q==PERMISSIONS&&"indoor".equals(prefs.getString("activityMode","outdoor"))&&!DaySteps.permitted(this)){toast("Indoor tracking needs Physical activity access to count steps");return;}if(q==PERMISSIONS&&("indoor".equals(prefs.getString("activityMode","outdoor"))||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)){command(RecorderService.ACTION_START);openLive();askBackground();}}
    private void command(String action){Intent i=new Intent(this,RecorderService.class);i.setAction(action);if(ACTION_START.equals(action)){if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}else startService(i);}
    private static final String ACTION_START=RecorderService.ACTION_START;
    private void updateLive(){if(prefs==null)return;if(resumed)refreshHealth(false);updateLiveOverlay();boolean active=prefs.getBoolean("active",false),paused=prefs.getBoolean("paused",false);JSONArray all=history();if(timer!=null){long ms=prefs.getLong("elapsed",0);timer.setText(formatDuration(ms));double km=prefs.getFloat("distance",0);distance.setText(String.format(Locale.US,"%.2f km",km));long ps=km>0?Math.round(ms/1000d/km):0;pace.setText(km>0?String.format(Locale.US,"%d:%02d /km",ps/60,ps%60):"— /km");steps.setText(String.format(Locale.US,"%,d",prefs.getInt("steps",0)));if(liveSpeed!=null){float kmh=active&&!paused?prefs.getFloat("speedKmh",0):0;liveSpeed.setText(kmh>0?String.format(Locale.US,"%.1f km/h",kmh):"— km/h");liveElev.setText(active&&prefs.getBoolean("hasElevation",false)?String.format(Locale.US,"%.0f m",prefs.getFloat("elevGainM",0)):"— m");liveKcal.setText(String.format(Locale.US,"~%d kcal",Math.round(km*65)));}boolean moving=prefs.getBoolean("moving",false);long fixAge=android.os.SystemClock.elapsedRealtime()-prefs.getLong("lastFixElapsed",0);boolean indoor="indoor".equals(prefs.getString("activityMode","outdoor"));boolean gps=indoor||(prefs.getBoolean("gpsFix",false)&&fixAge<12000);start.setText(active?(paused?"▶  Resume":"Ⅱ  Pause"):"▶  Start activity");String type=prefs.getString("currentType",activityType);status.setText(active?(paused?"● PAUSED":!indoor&&!gps?"● GPS SIGNAL WEAK":!moving?(indoor?"● WAITING FOR STEPS":"● YOU’RE NOT WALKING"):"● YOU’RE "+(type.equals("Run")?"RUNNING":"WALKING")):"● READY");status.setTextColor(active&&paused?muted:active&&!gps&&!indoor?warning:active&&!moving?warning:active?positive:lime);if(active&&!paused&&moving!=previousMoving){toast(moving?"You're moving · Kadamm is recording your activity":"You're not moving · pause or start moving to continue");}previousMoving=moving;route.setRoute(prefs.getString("route","[]"));route.setEmptyMessage(indoor?"Indoor session · distance comes from your steps":"Your route draws here once GPS locks on");if(gps&&!indoor)route.setCurrentLocation(prefs.getFloat("latitude",0),prefs.getFloat("longitude",0),prefs.getFloat("accuracy",0));else if(!active&&!indoor&&hasIdle)route.setCurrentLocation(idleLat,idleLon,idleAcc);else route.clearCurrentLocation();if(accuracyLabel!=null)accuracyLabel.setText(indoor?"Indoor mode · distance estimated from phone steps":gps?String.format(Locale.US,"GPS accuracy · ±%.0f m",prefs.getFloat("accuracy",0)):!active?(hasIdle?"Last known location · GPS locks on when you start":"Location appears here when you start"):"GPS signal weak or unavailable · last route point kept");if(activityModeLabel!=null)activityModeLabel.setText(prefs.getBoolean("manualType",false)?"Manual type · "+type:(indoor?"Indoor step tracking · ":"Automatic detection · currently ")+type);}updateHero(all);if(dailyDistanceProgress!=null){int target=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("distanceGoalM",5000);double meters=totalDistanceOn(all,todayKey())*1000;dailyDistanceProgress.setMax(target);dailyDistanceProgress.setProgress((int)Math.min(target,meters));dailyDistanceProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(meters>=target?positive:violet));dailyDistanceValue.setText(String.format(Locale.US,"%.2f / %.1f km",meters/1000f,target/1000f));}if(todayCount!=null){todayCount.setText(String.valueOf(countOn(all,todayKey(),"Walk")+(active&&"Walk".equals(prefs.getString("currentType","Walk"))?1:0)));todayDistance.setText(String.format(Locale.US,"%.2f",totalDistanceOn(all,todayKey())));}}
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
        TextView gear=tx("⚙︎",19,muted,false);gear.setGravity(Gravity.CENTER);gear.setContentDescription("Goals and targets");gear.setFocusable(true);gear.setBackground(rippleOn(Color.TRANSPARENT,24));gear.setOnClickListener(v->navigate("Goals"));LinearLayout.LayoutParams gp=lp(48,48);gp.leftMargin=dp(4);top.addView(gear,gp);
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
        LinearLayout head=row();head.addView(tx("DAILY STEP GOAL",10,muted,true),new LinearLayout.LayoutParams(0,dp(20),1));heroSource=tx("",10,violet,true);head.addView(heroSource);c.addView(head);
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
        long moveMin=todayDuration(all)/60000;int moveGoal=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("activeGoalMin",30);ring.setInner(moveMin/(float)Math.max(1,moveGoal));heroDistance.setText(String.format(Locale.US,"●  %d of %d move min",moveMin,moveGoal));
        String source=DaySteps.todaySource(this);heroSource.setText("phone".equals(source)?"PHONE SENSOR":"health".equals(source)?"HEALTH CONNECT":"sessions".equals(source)?"SESSIONS ONLY":"");
        heroStart.setText(active?"View live session":"▶  Start activity");if(heroSessions!=null)heroSessions.setText(String.format(Locale.US,"In Kadamm sessions: %,d steps · ~%d kcal",DaySteps.sessionOn(this,todayKey()),DaySteps.sessionCaloriesToday(this)));
        if(heroNote!=null)heroNote.setText(!data?"Let's get your steps counting.":pct>=100?"Goal done. Anything from here is a bonus.":pct>=80?"Nearly there. One short walk finishes it.":pct>=50?"Over halfway. Keep the rhythm going.":pct>=10?"Good start. Keep the momentum.":"Every step counts. Let's get moving.");
        if(n>=goal&&data){
            if(!todayKey().equals(prefs.getString("goalShownDay",""))){prefs.edit().putString("goalShownDay",todayKey()).apply();if(!reducedMotion())dailyGoalCard.animate().scaleX(1.015f).scaleY(1.015f).setDuration(180).withEndAction(()->dailyGoalCard.animate().scaleX(1f).scaleY(1f).setDuration(180).start()).start();}
            dailyGoalMessage.setText("Daily goal achieved! Great work, you've reached your step goal today.");dailyGoalMessage.setTextColor(positive);
        }else if(data&&n>=Math.ceil(goal*.6)){
            dailyGoalMessage.setText(String.format(Locale.US,"You're making progress! %,d steps done, just %,d more to reach your goal.",n,goal-n));dailyGoalMessage.setTextColor(ink);
        }else{
            dailyGoalMessage.setText(data?String.format(Locale.US,"%,d of %,d steps so far today.",n,goal):DaySteps.sensorPresent(this)&&!DaySteps.permitted(this)?"Allow step counting above to see today's steps here.":"Start an activity or connect a step source to see today's steps.");dailyGoalMessage.setTextColor(muted);
        }
        if(todayDistance!=null)todayDistance.setText(String.format(Locale.US,"%.2f",totalDistanceOn(all,todayKey())));
        if(todayActiveTime!=null)todayActiveTime.setText(formatDuration(todayDuration(all)));
        if(todayCaloriesValue!=null)todayCaloriesValue.setText("~"+todayCalories(all));
    }
    private int hue(int light,int dark){return darkTheme?dark:light;}
    private int blend(int a,int b,float t){return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    private int purple(){return hue(Color.rgb(147,52,230),Color.rgb(197,138,249));}
    private int orange(){return hue(Color.rgb(242,153,0),Color.rgb(253,186,85));}
    private int teal(){return hue(Color.rgb(18,150,170),Color.rgb(120,217,236));}
    private int flame(){return hue(Color.rgb(232,113,10),Color.rgb(252,173,112));}
    private LinearLayout bentoRow(){LinearLayout r=row();LinearLayout.LayoutParams p=lp(-1,118);p.topMargin=dp(10);content.addView(r,p);return r;}
    /** A dashboard tile with its own colour: tinted gradient surface, icon badge, value and unit. */
    private TextView tile(LinearLayout parent,String label,String value,String unit,int color,String icon){
        LinearLayout c=col();c.setPadding(dp(14),dp(14),dp(12),dp(12));
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{blend(panel,color,darkTheme?.20f:.13f),blend(panel,color,darkTheme?.06f:.03f)});g.setCornerRadius(dp(20));g.setStroke(dp(1),blend(line,color,.30f));c.setBackground(g);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=dp(10);parent.addView(c,p);
        LinearLayout head=row();c.addView(head);
        TextView ic=tx(icon,13,darkTheme?Color.rgb(24,25,28):Color.WHITE,true);ic.setGravity(Gravity.CENTER);ic.setBackground(shape(color,14));head.addView(ic,lp(28,28));
        TextView l=tx(label,10,muted,true);l.setLetterSpacing(.06f);LinearLayout.LayoutParams lpp=lp(-2,-2);lpp.leftMargin=dp(8);head.addView(l,lpp);
        TextView v=tx(value,24,ink,true);LinearLayout.LayoutParams vp=lp(-1,-2);vp.topMargin=dp(10);c.addView(v,vp);
        c.addView(tx(unit,10,muted,false));return v;
    }
    private void renderBento(JSONArray hist){
        String today=todayKey();int walks=countOn(hist,today,"Walk"),runs=countOn(hist,today,"Run"),sessions=countOn(hist,today,null);
        SharedPreferences hp=getSharedPreferences("kadamm_health",MODE_PRIVATE);boolean hc=todayKey().equals(hp.getString("healthDate",""))&&hp.getBoolean("healthConnected",false);
        TextView sec=tx("TODAY AT A GLANCE",10,muted,true);sec.setLetterSpacing(.08f);LinearLayout.LayoutParams sp=lp(-1,-2);sp.topMargin=dp(20);content.addView(sec,sp);
        LinearLayout r1=bentoRow();double sessionKm=totalDistanceOn(hist,today);
        if(hc&&hp.contains("healthDistanceM")&&hp.getFloat("healthDistanceM",0)/1000f>sessionKm){todayDistance=null;tile(r1,"DISTANCE",String.format(Locale.US,"%.2f",hp.getFloat("healthDistanceM",0)/1000f),"km today · all day",lime,"↗");}
        else todayDistance=tile(r1,"DISTANCE",String.format(Locale.US,"%.2f",sessionKm),"km in sessions",lime,"↗");
        if(hc&&hp.contains("healthActiveKcal")){todayCaloriesValue=null;tile(r1,"CALORIES",String.format(Locale.US,"%,d",Math.round(hp.getFloat("healthActiveKcal",0))),"active kcal today",orange(),"⚡\uFE0E");}
        else if(hc&&hp.contains("healthKcal")){todayCaloriesValue=null;tile(r1,"CALORIES",String.format(Locale.US,"%,d",Math.round(hp.getFloat("healthKcal",0))),"kcal burned incl. resting",orange(),"⚡\uFE0E");}
        else todayCaloriesValue=tile(r1,"CALORIES","~"+todayCalories(hist),"kcal · session estimate",orange(),"⚡\uFE0E");
        LinearLayout r2=bentoRow();todayActiveTime=tile(r2,"ACTIVE TIME",formatDuration(todayDuration(hist)),"in sessions today",violet,"◷");tile(r2,"SESSIONS",String.valueOf(sessions),walks+" walk · "+runs+" run",purple(),"▶");
        LinearLayout r3=bentoRow();tile(r3,"WALKING",String.format(Locale.US,"%.2f km",distanceByType(hist,today,"Walk")),formatDuration(durationByType(hist,today,"Walk"))+" today",teal(),"◉");tile(r3,"RUNNING",String.format(Locale.US,"%.2f km",distanceByType(hist,today,"Run")),formatDuration(durationByType(hist,today,"Run"))+" today",purple(),"»");
        int[] st=DaySteps.streaks(dayTotals,dailyGoal());long sum=0;int days=0;
        for(int i=0;i<7;i++){Calendar d=Calendar.getInstance();d.add(Calendar.DAY_OF_YEAR,-i);Long v=dayTotals.get(dateKey(d));if(v!=null&&v>0){sum+=v;days++;}}
        LinearLayout r4=bentoRow();tile(r4,"STREAK",st[0]+(st[0]==1?" day":" days"),"best "+st[1]+" · goal days "+st[2],flame(),"★");tile(r4,"DAILY AVG",days==0?"—":String.format(Locale.US,"%,d",sum/days),"steps · last 7 days",lime,"≈");
    }
    private void renderDistanceGoal(JSONArray hist){
        LinearLayout distGoal=card();distGoal.addView(tx("DAILY DISTANCE TARGET",10,muted,true));int distTarget=getSharedPreferences("kadamm_settings",MODE_PRIVATE).getInt("distanceGoalM",5000);
        dailyDistanceValue=tx(String.format(Locale.US,"%.2f / %.1f km",totalDistanceOn(hist,todayKey()),distTarget/1000f),17,ink,true);distGoal.addView(dailyDistanceValue);
        dailyDistanceProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);dailyDistanceProgress.setMax(distTarget);dailyDistanceProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(violet));dailyDistanceProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(raised));LinearLayout.LayoutParams dgp=lp(-1,7);dgp.topMargin=dp(8);distGoal.addView(dailyDistanceProgress,dgp);
    }
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
        if(content!=null&&(("Today".equals(page)&&stepSetupShown&&DaySteps.permitted(this))||"Health".equals(page)))renderPageNow();
    }
    @Override protected void onPause(){
        resumed=false;if(stepListening){((SensorManager)getSystemService(SENSOR_SERVICE)).unregisterListener(stepListener);stepListening=false;}
        super.onPause();
    }

    // ---- Saved sessions: name, share, delete ----
    private String sessionLabel(JSONObject o){String name=o.optString("name","").trim();return name.isEmpty()?o.optString("type","Walk"):name;}
    private String sessionTitle(JSONObject o){String name=o.optString("name","").trim();return name.isEmpty()?o.optString("type","Walk")+" complete":name;}
    private void sessionActions(JSONObject o){
        LinearLayout r=row();LinearLayout.LayoutParams p=lp(-1,46);p.topMargin=dp(12);content.addView(r,p);
        Button share=button("Share",lime,accentOn),rename=button("Rename",raised,ink),delete=button("Delete",raised,critical);
        r.addView(share,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=dp(8);r.addView(rename,rp);LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,-1,1);dlp.leftMargin=dp(8);r.addView(delete,dlp);
        share.setOnClickListener(v->shareSession(o));
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
    /** Renders the route-and-stats image off the main thread, previews it, then shares the PNG. */
    private void shareSession(JSONObject o){
        toast("Preparing your activity image…");String text=shareText(o),title=sessionTitle(o);
        new Thread(()->{
            try{
                ShareCard.render(this,o,title);                       // first pass asks for any missing map tiles
                for(int i=0;i<40&&RouteView.pendingTiles()>0;i++)Thread.sleep(100);
                android.graphics.Bitmap img=ShareCard.render(this,o,title);
                java.io.File dir=new java.io.File(getCacheDir(),"share");dir.mkdirs();java.io.File file=new java.io.File(dir,"kadamm-activity.png");
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){img.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
                Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",file);
                runOnUiThread(()->{
                    android.widget.ImageView preview=new android.widget.ImageView(this);preview.setImageBitmap(img);preview.setAdjustViewBounds(true);preview.setPadding(dp(16),dp(12),dp(16),0);
                    dialog().setView(preview).setNegativeButton("Close",null).setPositiveButton("Share",(d,w)->{
                        try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_TEXT,text).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share activity"));}catch(Exception e){toast("No app available to share with");}
                    }).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Share activity"));}catch(Exception ignored){}});
            }
        },"KadammShare").start();
    }

    // ---- Live session screen ----
    private FrameLayout liveLayer;private RouteView liveMap;private TextView lvTitle,lvStatus,lvTime,lvPace,lvDist,lvSteps,lvKcal,lvSpeed;private Button lvMain,lvFinish;private long liveOpenedAt;
    private void openLive(){
        if(liveLayer==null)buildLive();
        if(liveLayer.getVisibility()==View.VISIBLE)return;
        liveOpenedAt=android.os.SystemClock.elapsedRealtime();liveLayer.setVisibility(View.VISIBLE);liveLayer.setAlpha(0f);liveLayer.setTranslationY(dp(48));
        liveLayer.animate().alpha(1f).translationY(0).setDuration(reducedMotion()?0:240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        getWindow().setStatusBarColor(ShareCard.BLACK);root.setBackgroundColor(ShareCard.BLACK);getWindow().getDecorView().setSystemUiVisibility(0);updateLiveOverlay();
    }
    private void closeLive(){
        if(liveLayer==null||liveLayer.getVisibility()!=View.VISIBLE)return;
        liveLayer.animate().alpha(0f).translationY(dp(48)).setDuration(reducedMotion()?0:180).withEndAction(()->liveLayer.setVisibility(View.GONE)).start();applyTheme();
    }
    private void buildLive(){
        int dark=ShareCard.BLACK,card=Color.argb(245,22,22,26),sub=Color.rgb(154,160,166),white=Color.rgb(245,245,247);
        liveLayer=new FrameLayout(this);liveLayer.setBackgroundColor(dark);liveLayer.setClickable(true);liveLayer.setVisibility(View.GONE);root.addView(liveLayer,new FrameLayout.LayoutParams(-1,-1));
        liveMap=new RouteView(this);liveMap.setTheme(dark,Color.rgb(38,38,42),ShareCard.ROUTE,true);liveLayer.addView(liveMap,new FrameLayout.LayoutParams(-1,-1));
        TextView down=tx("‹",30,white,true);down.setRotation(-90);down.setGravity(Gravity.CENTER);down.setBackground(ripple(shape(Color.argb(235,22,22,26),24)));down.setContentDescription("Minimise live session");down.setOnClickListener(v->closeLive());
        FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.START);dlp.setMargins(dp(16),dp(16),0,0);liveLayer.addView(down,dlp);
        TextView follow=tx("◎",20,white,true);follow.setGravity(Gravity.CENTER);follow.setBackground(ripple(shape(Color.argb(235,22,22,26),24)));follow.setContentDescription("Follow my location");follow.setOnClickListener(v->liveMap.setFollowLocation(true));
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
        lvSteps.setText(String.format(Locale.US,"%,d",prefs.getInt("steps",0)));lvKcal.setText(String.valueOf(Math.round(km*65)));float kmh=paused?0:prefs.getFloat("speedKmh",0);lvSpeed.setText(kmh>0?String.format(Locale.US,"%.1f",kmh):"—");
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
    @Override public void onBackPressed(){
        if(liveLayer!=null&&liveLayer.getVisibility()==View.VISIBLE){closeLive();return;}
        if(drawerLayer!=null&&drawerLayer.getVisibility()==View.VISIBLE){closeDrawer();return;}
        if("Detail".equals(page)){navigate("History");return;}
        if(!"Today".equals(page)){navigate("Today");return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){handler.removeCallbacks(refresh);super.onDestroy();}
}
