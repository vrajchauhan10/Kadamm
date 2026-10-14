package com.stride.run;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/** Shared semantic palette for the native Kadamm UI. */
final class KadammTheme {
    final boolean dark;
    final int background, surface, surfaceRaised, accent, accentSecondary, accentOn, text, textMuted, border, focus, positive, warning, critical;

    private KadammTheme(boolean dark) {
        this.dark=dark;
        // The logo's palette: black, white and one blue, with a lighter blue as the second accent.
        // Green is kept for status only: live, connected, goal reached.
        if(dark){
            background=Color.rgb(11,11,13);surface=Color.rgb(22,22,25);surfaceRaised=Color.rgb(36,36,40);
            accent=Color.rgb(77,144,254);accentSecondary=Color.rgb(168,199,250);accentOn=Color.WHITE;
            text=Color.rgb(245,245,247);textMuted=Color.rgb(154,160,166);border=Color.rgb(44,44,48);focus=accent;
            positive=Color.rgb(129,201,149);warning=Color.rgb(242,153,0);critical=Color.rgb(242,139,130);
        }else{
            background=Color.rgb(247,247,248);surface=Color.WHITE;surfaceRaised=Color.rgb(239,239,241);
            accent=Color.rgb(26,115,232);accentSecondary=Color.rgb(94,151,246);accentOn=Color.WHITE;
            text=Color.rgb(11,11,13);textMuted=Color.rgb(95,99,104);border=Color.rgb(227,227,230);focus=accent;
            positive=Color.rgb(30,142,62);warning=Color.rgb(227,116,0);critical=Color.rgb(217,48,37);
        }
    }
    static KadammTheme resolve(Context context,String mode){
        boolean dark="dark".equals(mode);
        if("system".equals(mode)||mode==null){int ui=context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK;dark=ui==Configuration.UI_MODE_NIGHT_YES;}
        return new KadammTheme(dark);
    }
}
