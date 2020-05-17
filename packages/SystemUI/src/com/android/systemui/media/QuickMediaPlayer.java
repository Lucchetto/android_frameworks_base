package com.android.systemui.media;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.media.session.MediaController;
import android.media.session.MediaSession.Token;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.android.systemui.R;


public class QuickMediaPlayer extends MediaControlPanel {
    private static final int[] QQS_ACTION_IDS = { R.id.action0, R.id.action1, R.id.action2 };

    public QuickMediaPlayer(Context context, ViewGroup viewGroup) {
        super(context, viewGroup, R.layout.qqs_media_panel, QQS_ACTION_IDS);
    }

    public void setMediaSession(Token token, Icon icon, int i, int i2, View view, int[] iArr,
            PendingIntent pendingIntent, String appName) {
        Token token2 = token;
        int[] iArr2 = iArr;
        int[] iArr3 = QQS_ACTION_IDS;
        String packageName = getController() != null ? getController().getPackageName() : "";
        MediaController mediaController = new MediaController(getContext(), token);
        Token mediaSessionToken = getMediaSessionToken();
        int i3 = 0;
        boolean z = mediaSessionToken != null && mediaSessionToken.equals(token)
                && packageName.equals(mediaController.getPackageName());
        if (getController() == null || z || isPlaying(mediaController)) {
            super.setMediaSession(token, icon, i, i2, pendingIntent, appName);
            LinearLayout linearLayout = (LinearLayout) view;
            if (iArr2 != null) {
                int min = Math.min(Math.min(iArr2.length, linearLayout.getChildCount()), iArr3.length);
                int i4 = 0;
                while (i4 < min) {
                    final ImageButton imageButton = (ImageButton) mMediaNotifView.findViewById(iArr3[i4]);
                    final ImageButton imageButton2 = (ImageButton) linearLayout
                            .findViewById(MediaControlPanel.NOTIF_ACTION_IDS[iArr2[i4]]);
                    if (imageButton2 == null || imageButton2.getDrawable() == null
                            || imageButton2.getVisibility() != View.VISIBLE) {
                        imageButton.setVisibility(View.GONE);
                    } else {
                        imageButton.setImageDrawable(imageButton2.getDrawable().mutate());
                        imageButton.setVisibility(View.VISIBLE);
                        imageButton.setOnClickListener(new OnClickListener() {
                            @Override
                            public final void onClick(View view) {
                                imageButton2.performClick();
                            }
                        });
                    }
                    i4++;
                }
                i3 = i4;
            }
            while (i3 < iArr3.length) {
                ((ImageButton) mMediaNotifView.findViewById(iArr3[i3])).setVisibility(View.GONE);
                i3++;
            }
        }
    }
}
