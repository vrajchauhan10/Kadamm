package com.stride.run;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/** The phone asking for the latest: the watch answers with its details, queued heart rate and a fresh reading. */
public class WatchListener extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent e){
        if(!"/kadamm/ping".equals(e.getPath()))return;
        HrJob.hello(this);HrJob.send(this);if(HrJob.allowed(this)){HrJob.schedule(this);HrJob.now(this);}
    }
}
