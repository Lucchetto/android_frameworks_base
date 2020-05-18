package com.android.systemui.media;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaController.Callback;
import android.media.session.MediaSession.Token;
import android.media.session.PlaybackState;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.settingslib.widget.AdaptiveIcon;
import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.NotificationMediaManager.MediaListener;

import java.util.List;

public class MediaControlPanel implements MediaListener {
    protected static final int[] NOTIF_ACTION_IDS = { com.android.internal.R.id.action0,
            com.android.internal.R.id.action1, com.android.internal.R.id.action2, com.android.internal.R.id.action3,
            com.android.internal.R.id.action4 };
    private final int[] mActionIds;
    private int mBackgroundColor;
    private final BackgroundExecutor mBackgroundExecutor;
    private Context mContext;
    private MediaController mController;
    private MediaMetadata mMetadata;
    private int mForegroundColor;
    private final NotificationMediaManager mMediaManager;
    protected LinearLayout mMediaNotifView;
    protected ComponentName mRecvComponent;
    private Callback mSessionCallback = new Callback() {
        public void onSessionDestroyed() {
            Log.d("MediaControlPanel", "session destroyed");
            mController.unregisterCallback(mSessionCallback);
            clearControls();
        }
    };
    private Token mToken;
    private int mWidth;
    private int mHeight;
    private boolean mIsPlaybackActive;

    public MediaControlPanel(Context context, ViewGroup viewGroup, int i, int[] iArr) {
        mContext = context;
        mMediaNotifView = (LinearLayout) LayoutInflater.from(context).inflate(i, viewGroup, false);
        mMediaManager = Dependency.get(NotificationMediaManager.class);
        mActionIds = iArr;
        mBackgroundExecutor = BackgroundExecutor.get();
    }

    public View getView() {
        return mMediaNotifView;
    }

    public Context getContext() {
        return mContext;
    }

    public void setMediaSession(Token token, Icon icon, int i, int i2, final PendingIntent pendingIntent, String str) {
        mToken = token;
        mForegroundColor = i;
        mBackgroundColor = i2;
        MediaController mediaController = new MediaController(mContext, mToken);
        mController = mediaController;
        mMetadata = mediaController.getMetadata();
        List<ResolveInfo> queryBroadcastReceiversAsUser = mContext.getPackageManager()
                .queryBroadcastReceiversAsUser(new Intent("android.intent.action.MEDIA_BUTTON"), 0, mContext.getUser());
        if (queryBroadcastReceiversAsUser != null) {
            for (ResolveInfo resolveInfo : queryBroadcastReceiversAsUser) {
                if (resolveInfo.activityInfo.packageName.equals(mController.getPackageName())) {
                    mRecvComponent = resolveInfo.getComponentInfo().getComponentName();
                }
            }
        }
        mController.registerCallback(mSessionCallback);
        if (mMetadata == null) {
            Log.e("MediaControlPanel", "Media metadata was null");
            return;
        }

        updateArtwork();
        mIsPlaybackActive = true;

        if (pendingIntent != null) {
            mMediaNotifView.setOnClickListener(new OnClickListener() {
                @Override
                public final void onClick(View view) {
                    try {
                        pendingIntent.send();
                        mContext.sendBroadcast(new Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS"));
                    } catch (CanceledException e) {
                        Log.e("MediaControlPanel", "Pending intent was canceled", e);
                    }
                }
            });
        }
        ImageView imageView2 = (ImageView) mMediaNotifView.findViewById(R.id.icon);
        Drawable loadDrawable = icon.loadDrawable(mContext);
        loadDrawable.setTint(mForegroundColor);
        imageView2.setImageDrawable(loadDrawable);
        TextView textView = (TextView) mMediaNotifView.findViewById(R.id.media_artist);
        textView.setText(mMetadata.getString("android.media.metadata.ARTIST"));
        textView.setTextColor(mForegroundColor);
        TextView textView2 = (TextView) mMediaNotifView.findViewById(R.id.app_name);
        textView2.setText(str);
        textView2.setTextColor(mForegroundColor);
        TextView textView3 = (TextView) mMediaNotifView.findViewById(R.id.media_title);
        textView3.setText(mMetadata.getString("android.media.metadata.TITLE"));
        textView3.setTextColor(mForegroundColor);
        textView3.setSelected(true);
        mMediaManager.removeCallback(this);
        mMediaManager.addCallback(this);
    }

    private void updateArtwork() {
        if (mMetadata == null) {
            return;
        }
        mBackgroundExecutor.submit(new Runnable() {
            @Override
            public final void run() {
                Bitmap bitmap = mMetadata.getBitmap("android.media.metadata.ALBUM_ART");
                float radius = mContext.getResources().getDimension(R.dimen.volume_dialog_panel_radius);
                if (bitmap == null || mWidth <= 0 || mHeight <= 0) {
                    Log.e("MediaControlPanel", "No album art available");
                    GradientDrawable gradientDrawable = new GradientDrawable();
                    gradientDrawable.setCornerRadius(radius);
                    gradientDrawable.setColor(mBackgroundColor);
                    mMediaNotifView.setBackground(gradientDrawable);
                } else {
                    bitmap = scaleBitmap(bitmap, mWidth, mHeight);
                    Canvas canvas = new Canvas(bitmap);
                    Paint paint = new Paint();
                    paint.setStyle(Style.FILL);
                    paint.setColor(mBackgroundColor);
                    paint.setAlpha(215);
                    canvas.drawRect(0.0f, 0.0f, (float) canvas.getWidth(), (float) canvas.getHeight(), paint);
                    RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(mContext.getResources(), bitmap);
                    roundedBitmapDrawable.setCornerRadius(radius);
                    mMediaNotifView.setBackground(roundedBitmapDrawable);
                }
            }
        });
    }

    public void setArtworkSize(int w, int h) {
        if (w != mWidth || h != mHeight) {
            mWidth = w;
            mHeight = h;
            updateArtwork();
        }
    }

    private Bitmap scaleBitmap(Bitmap image, int maxWidth, int maxHeight) {
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;
            if (ratioMax > ratioBitmap) {
                finalWidth = (int) ((float) maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float) maxWidth / ratioBitmap);
            }
            image = Bitmap.createScaledBitmap(image.copy(Config.ARGB_8888, true), finalWidth, finalHeight, true);
            return image;
        } else {
            return image;
        }
    }

    public Token getMediaSessionToken() {
        return mToken;
    }

    public MediaController getController() {
        return mController;
    }

    public String getMediaPlayerPackage() {
        return mController.getPackageName();
    }

    public boolean hasMediaSession() {
        MediaController mediaController = mController;
        return (mediaController == null || mediaController.getPlaybackState() == null) ? false : true;
    }

    public boolean isPlaybackActive() {
        return mIsPlaybackActive;
    }

    public void setPlaybackActive(boolean active) {
        mIsPlaybackActive = active;
    }

    public boolean isPlaying() {
        return isPlaying(mController);
    }

    protected boolean isPlaying(MediaController mediaController) {
        boolean z = false;
        if (mediaController == null) {
            return false;
        }
        PlaybackState playbackState = mediaController.getPlaybackState();
        if (playbackState == null) {
            return false;
        }
        if (playbackState.getState() == PlaybackState.STATE_PLAYING) {
            z = true;
        }
        return z;
    }

    public void clearControls() {
        int i = 0;
        while (true) {
            int[] iArr = mActionIds;
            if (i < iArr.length) {
                ImageButton imageButton = (ImageButton) mMediaNotifView.findViewById(iArr[i]);
                if (imageButton != null) {
                    imageButton.setVisibility(View.GONE);
                }
                i++;
            } else {
                ImageButton imageButton2 = (ImageButton) mMediaNotifView.findViewById(iArr[0]);
                imageButton2.setOnClickListener(new OnClickListener() {
                    @Override
                    public final void onClick(View view) {
                        String str = "MediaControlPanel";
                        Log.d(str, "Attempting to restart session");
                        if (mRecvComponent != null) {
                            Intent intent = new Intent("android.intent.action.MEDIA_BUTTON");
                            intent.setComponent(mRecvComponent);
                            intent.putExtra("android.intent.extra.KEY_EVENT", new KeyEvent(0, 126));
                            mContext.sendBroadcast(intent);
                        } else if (mController.getSessionActivity() != null) {
                            try {
                                mController.getSessionActivity().send();
                            } catch (CanceledException e) {
                                Log.e(str, "Pending intent was canceled", e);
                            }
                        } else {
                            Log.e(str, "No receiver or activity to restart");
                        }
                    }
                });
                imageButton2.setImageDrawable(mContext.getResources().getDrawable(R.drawable.lb_ic_play));
                imageButton2.setImageTintList(ColorStateList.valueOf(mForegroundColor));
                imageButton2.setVisibility(View.VISIBLE);
                return;
            }
        }
    }

    @Override
    public void onMetadataOrStateChanged(MediaMetadata mediaMetadata, @PlaybackState.State int state) {
        if (state == PlaybackState.STATE_NONE) {
            clearControls();
            mMediaManager.removeCallback(this);
        }
    }
}
