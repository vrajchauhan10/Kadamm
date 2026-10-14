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
        if(dark){
            background=Color.rgb(24,25,28);surface=Color.rgb(36,37,41);surfaceRaised=Color.rgb(54,57,62);
            accent=Color.rgb(138,180,248);accentSecondary=Color.rgb(129,201,149);accentOn=Color.rgb(24,25,28);
            text=Color.rgb(232,234,237);textMuted=Color.rgb(154,160,166);border=Color.rgb(60,64,67);focus=accent;
            positive=Color.rgb(129,201,149);warning=Color.rgb(242,153,0);critical=Color.rgb(242,139,130);
        }else{
            background=Color.rgb(248,249,250);surface=Color.WHITE;surfaceRaised=Color.rgb(236,239,243);
            accent=Color.rgb(26,115,232);accentSecondary=Color.rgb(52,168,83);accentOn=Color.WHITE;
            text=Color.rgb(32,33,36);textMuted=Color.rgb(95,99,104);border=Color.rgb(218,220,224);focus=accent;
            positive=Color.rgb(30,142,62);warning=Color.rgb(227,116,0);critical=Color.rgb(217,48,37);
        }
    }
    static KadammTheme resolve(Context context,String mode){
        boolean dark="dark".equals(mode);
        if("system".equals(mode)||mode==null){int ui=context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK;dark=ui==Configuration.UI_MODE_NIGHT_YES;}
        return new KadammTheme(dark);
    }
}
