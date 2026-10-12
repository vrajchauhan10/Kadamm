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
            background=Color.rgb(11,16,32);surface=Color.rgb(17,24,39);surfaceRaised=Color.rgb(28,38,58);
            accent=Color.rgb(34,211,238);accentSecondary=Color.rgb(129,140,248);accentOn=Color.rgb(7,23,35);
            text=Color.rgb(241,245,249);textMuted=Color.rgb(148,163,184);border=Color.rgb(43,55,77);focus=accent;
            positive=Color.rgb(74,222,128);warning=Color.rgb(251,191,36);critical=Color.rgb(248,113,113);
        }else{
            background=Color.rgb(245,247,251);surface=Color.WHITE;surfaceRaised=Color.rgb(233,238,246);
            accent=Color.rgb(8,145,178);accentSecondary=Color.rgb(79,70,229);accentOn=Color.WHITE;
            text=Color.rgb(16,24,40);textMuted=Color.rgb(102,112,133);border=Color.rgb(220,227,238);focus=accent;
            positive=Color.rgb(2,122,72);warning=Color.rgb(181,71,8);critical=Color.rgb(180,35,24);
        }
    }
    static KadammTheme resolve(Context context,String mode){
        boolean dark="dark".equals(mode);
        if("system".equals(mode)||mode==null){int ui=context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK;dark=ui==Configuration.UI_MODE_NIGHT_YES;}
        return new KadammTheme(dark);
    }
}
