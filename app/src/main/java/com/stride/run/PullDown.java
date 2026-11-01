package com.stride.run;

import android.view.MotionEvent;
import android.view.View;

/**
 * Swipe down to close. A window feeds it every touch; once a drag is clearly downward it takes the gesture,
 * cancels it for the views underneath, slides the target down with the finger, and on release either closes
 * (far enough, or a quick flick) or springs back.
 */
final class PullDown {
    interface Cancel { void send(MotionEvent cancel); }
    private View target;private Runnable close;private float downX,downY,density;private long downAt;private boolean pulling;
    private final boolean slideOut;

    /** {@code slideOut}: the target leaves the screen before closing (sheets, overlays); otherwise it springs back as the page changes. */
    PullDown(float density,boolean slideOut){this.density=density;this.slideOut=slideOut;}

    /** Call on ACTION_DOWN with what a pull would close, or a null target when nothing here can be pulled. */
    void begin(MotionEvent e,View target,Runnable close){this.target=target;this.close=close;downX=e.getRawX();downY=e.getRawY();downAt=System.currentTimeMillis();pulling=false;}
    boolean pulling(){return pulling;}

    /** Returns true when the event belongs to the pull and must not reach the views underneath. */
    boolean handle(MotionEvent e,Cancel cancel){
        if(target==null)return false;
        int a=e.getActionMasked();float dy=e.getRawY()-downY,dx=e.getRawX()-downX;
        if(a==MotionEvent.ACTION_POINTER_DOWN){if(!pulling)target=null;return pulling;}
        if(a==MotionEvent.ACTION_MOVE){
            if(!pulling&&!RouteView.gestureActive&&dy>24*density&&dy>Math.abs(dx)*1.6f){
                pulling=true;MotionEvent c=MotionEvent.obtain(e);c.setAction(MotionEvent.ACTION_CANCEL);cancel.send(c);c.recycle();target.animate().cancel();
            }
            if(pulling){float y=Math.max(0,dy);target.setTranslationY(slideOut?y:y*.55f);target.setAlpha(Math.max(.35f,1f-y/(target.getHeight()*1.4f+1)));return true;}
        }
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
            if(!pulling){target=null;return false;}
            pulling=false;View t=target;target=null;long dt=System.currentTimeMillis()-downAt;
            boolean go=a==MotionEvent.ACTION_UP&&(dy>140*density||(dt<320&&dy>70*density));
            if(go&&slideOut)t.animate().translationY(t.getHeight()).alpha(0f).setDuration(170).withEndAction(close::run).start();
            else if(go){close.run();t.setTranslationY(0);t.setAlpha(1f);}
            else t.animate().translationY(0).alpha(1f).setDuration(200).start();
            return true;
        }
        return pulling;
    }
}
