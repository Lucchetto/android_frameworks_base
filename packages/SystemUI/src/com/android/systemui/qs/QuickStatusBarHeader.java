/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.annotation.ColorInt;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.os.UserManager;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.MathUtils;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.settingslib.drawable.UserIconDrawable;
import com.android.settingslib.Utils;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.qs.QSDetail.Callback;
import com.android.systemui.qs.carrier.QSCarrierGroup;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.MultiUserSwitch;
import com.android.systemui.statusbar.phone.SettingsButton;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarIconController.TintedIconManager;
import com.android.systemui.statusbar.phone.StatusBarWindowView;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.statusbar.policy.DateView;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserInfoController.OnUserInfoChangedListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * View that contains the top-most bits of the screen (primarily the status bar with date, time, and
 * battery) and also contains the {@link QuickQSPanel} along with some of the panel's inner
 * contents.
 */
public class QuickStatusBarHeader extends RelativeLayout implements
        View.OnClickListener,ConfigurationListener, OnUserInfoChangedListener,
        NextAlarmController.NextAlarmChangeCallback {
    private static final String TAG = "QuickStatusBarHeader";
    private static final boolean DEBUG = false;

    /** Delay for auto fading out the long press tooltip after it's fully visible (in ms). */
    private static final long AUTO_FADE_OUT_DELAY_MS = DateUtils.SECOND_IN_MILLIS * 6;
    private static final int FADE_ANIMATION_DURATION_MS = 300;
    private static final int TOOLTIP_NOT_YET_SHOWN_COUNT = 0;
    public static final int MAX_TOOLTIP_SHOWN_COUNT = 2;

    private final Handler mHandler = new Handler();
    private final NextAlarmController mAlarmController;
    private final StatusBarIconController mStatusBarIconController;
    private final ActivityStarter mActivityStarter;
    private final DeviceProvisionedController mDeviceProvisionedController;
    private final UserInfoController mUserInfoController;

    private QSPanel mQsPanel;

    private boolean mExpanded;
    private boolean mListening;
    private boolean mQsDisabled;
    private boolean mIsLandscape;

    private QSCarrierGroup mCarrierGroup;
    protected QuickQSPanel mHeaderQsPanel;
    protected QSTileHost mHost;
    private TintedIconManager mIconManager;
    private TouchAnimator mHeaderTextContainerAlphaAnimator;
    private TouchAnimator mPrivacyChipAlphaAnimator;
    private final CommandQueue mCommandQueue;

    private AlarmManager.AlarmClockInfo mNextAlarm;

    private LinearLayout mClockDateContainer, mStatusIconsContainer;

    View.OnClickListener mEditClickListener;
    View.OnClickListener mSettingsClickListener;

    private ViewGroup mHeaderTextContainerView;
    private View mAlarmContainer;
    private ViewGroup mQuickActionButtons;
    protected MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;
    private View mEdit;
    private SettingsButton mSettingsButton;

    private ViewGroup mSystemIconsView;
    private Clock mClockView;
    private ViewGroup mCollapsedDateViewContainer;
    private DateView mCollapsedDateView;
    private ViewGroup mExpandedDateViewContainer;
    private DateView mExpandedDateView;
    private BatteryMeterView mBatteryRemainingIcon;

    private ViewGroup mQuickActionButtonsLand;
    protected MultiUserSwitch mMultiUserSwitchLand;
    private ImageView mMultiUserAvatarLand;
    private View mEditLand;
    private SettingsButton mSettingsButtonLand;

    private boolean mHasTopCutout = false;
    private int mStatusBarPaddingTop = 0;
    private int mRoundedCornerPadding = 0;
    private int mContentMarginStart;
    private int mContentMarginEnd;
    private int mWaterfallTopInset;
    private int mCutOutPaddingLeft;
    private int mCutOutPaddingRight;
    private float mExpandedHeaderAlpha = 1.0f;
    private float mKeyguardExpansionFraction;

    @Inject
    public QuickStatusBarHeader(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            StatusBarIconController statusBarIconController,
            ActivityStarter activityStarter,
            CommandQueue commandQueue,
            DeviceProvisionedController deviceProvisionedController,
            UserInfoController userInfoController,
            NextAlarmController nextAlarmController) {
        super(context, attrs);
        mStatusBarIconController = statusBarIconController;
        mActivityStarter = activityStarter;
        mCommandQueue = commandQueue;
        mDeviceProvisionedController = deviceProvisionedController;
        mUserInfoController = userInfoController;
        mAlarmController = nextAlarmController;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mClockDateContainer = findViewById(R.id.clock_date_container);
        mStatusIconsContainer = findViewById(R.id.status_icons_container);
        // Make mStatusIconsContainer the same height of mClockDateContainer
        mClockDateContainer.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mClockDateContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)
                        mStatusIconsContainer.getLayoutParams();
                lp.height = mClockDateContainer.getHeight();
                mStatusIconsContainer.setLayoutParams(lp);
            }
        });

        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);
        mSystemIconsView = findViewById(R.id.quick_status_bar_system_icons);
        StatusIconContainer iconContainer = mSystemIconsView.findViewById(R.id.statusIcons);
        // Ignore privacy icons because they show in the space above QQS
        iconContainer.addIgnoredSlots(getIgnoredIconSlots());
        iconContainer.setShouldRestrictIcons(false);
        mIconManager = new TintedIconManager(iconContainer, mCommandQueue);

        mCarrierGroup = findViewById(R.id.carrier_group);

        // Views corresponding to the header info section (e.g. ringer and next alarm).
        mHeaderTextContainerView = findViewById(R.id.header_text_container);
        mAlarmContainer = findViewById(R.id.alarm_container);
        mAlarmContainer.setOnClickListener(this);
        mQuickActionButtons = mHeaderTextContainerView.findViewById(R.id.qs_header_action_buttons);
        mMultiUserSwitch = mQuickActionButtons.findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = mMultiUserSwitch.findViewById(R.id.multi_user_avatar);
        mEdit = mQuickActionButtons.findViewById(android.R.id.edit);
        mSettingsButton = mQuickActionButtons.findViewById(R.id.settings_button);
        // RenderThread is doing more harm than good when touching the header (to expand quick
        // settings), so disable it for this view
        ((RippleDrawable) mSettingsButton.getBackground()).setForceSoftware(true);

        mQuickActionButtonsLand = mSystemIconsView.findViewById(R.id.quick_status_bar_action_buttons_land);
        mMultiUserSwitchLand = mQuickActionButtonsLand.findViewById(R.id.multi_user_switch);
        mMultiUserAvatarLand = mMultiUserSwitchLand.findViewById(R.id.multi_user_avatar);
        mEditLand = mQuickActionButtonsLand.findViewById(android.R.id.edit);
        mSettingsButtonLand = mQuickActionButtonsLand.findViewById(R.id.settings_button);
        // RenderThread is doing more harm than good when touching the header (to expand quick
        // settings), so disable it for this view
        ((RippleDrawable) mSettingsButtonLand.getBackground()).setForceSoftware(true);

        mClockView = findViewById(R.id.clock);
        mClockView.setOnClickListener(this);

        mCollapsedDateViewContainer = findViewById(R.id.expanded_date_container);
        mCollapsedDateView = findViewById(R.id.collapsed_date);
        mCollapsedDateView.setOnClickListener(this);

        mExpandedDateViewContainer = findViewById(R.id.collapsed_date_container);
        mExpandedDateView = findViewById(R.id.expanded_date);
        mExpandedDateView.setOnClickListener(this);

        mBatteryRemainingIcon = findViewById(R.id.batteryRemainingIcon);
        // Don't need to worry about tuner settings for this icon
        mBatteryRemainingIcon.setIgnoreTunerUpdates(true);
        // QS will always show the estimate, and BatteryMeterView handles the case where
        // it's unavailable or charging
        mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ESTIMATE);
        mBatteryRemainingIcon.setOnClickListener(this);

        updateResources();

        updateExtendedStatusBarTint();
    }

    public QuickQSPanel getHeaderQsPanel() {
        return mHeaderQsPanel;
    }

    private List<String> getIgnoredIconSlots() {
        ArrayList<String> ignored = new ArrayList<>();
        ignored.add(mContext.getResources().getString(
                com.android.internal.R.string.status_bar_camera));
        ignored.add(mContext.getResources().getString(
                com.android.internal.R.string.status_bar_microphone));
        ignored.add(mContext.getResources().getString(
                com.android.internal.R.string.status_bar_mobile));

        return ignored;
    }

    private void updateExtendedStatusBarTint() {
        int tintColor = getContext().getColor(R.color.qs_translucent_text_primary);
        if (mIconManager != null) {
            mIconManager.setTint(tintColor);
        }
        mBatteryRemainingIcon.updateColors(tintColor, tintColor, tintColor);
        mCarrierGroup.setTint(tintColor);
        
        ColorStateList tintSecondaryStateList = ColorStateList.valueOf(
                getContext().getColor(R.color.qs_translucent_text_secondary));
        ImageView alarmIcon = mAlarmContainer.findViewById(R.id.alarm_icon);
        alarmIcon.setImageTintList(tintSecondaryStateList);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mIsLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        mQuickActionButtonsLand.setVisibility(mIsLandscape ? View.VISIBLE : View.GONE);
        mQuickActionButtons.setVisibility(mIsLandscape ? View.GONE : View.VISIBLE);
        updateResources();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    /**
     * The height of QQS should always be the status bar height + 128dp. This is normally easy, but
     * when there is a notch involved the status bar can remain a fixed pixel size.
     */
    private void updateMinimumHeight() {
        int sbHeight = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
        int qqsHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.qs_quick_header_panel_height);

        setMinimumHeight(sbHeight + qqsHeight);
    }

    private void updateResources() {
        Resources resources = mContext.getResources();
        updateMinimumHeight();

        mRoundedCornerPadding = resources.getDimensionPixelSize(
                R.dimen.rounded_corner_content_padding);
        mStatusBarPaddingTop = resources.getDimensionPixelSize(R.dimen.status_bar_padding_top);

        // Update height for a few views, especially due to landscape mode restricting space.
        mHeaderTextContainerView.getLayoutParams().height =
                resources.getDimensionPixelSize(R.dimen.qs_header_tooltip_height);
        mHeaderTextContainerView.setLayoutParams(mHeaderTextContainerView.getLayoutParams());

        mSystemIconsView.getLayoutParams().height = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.qs_status_bar_height);
        mSystemIconsView.setLayoutParams(mSystemIconsView.getLayoutParams());

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (mQsDisabled) {
            lp.height = resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.qs_status_bar_height) + resources.getDimensionPixelSize(
                            com.android.internal.R.dimen.qs_status_bar_top_padding);
        } else {
            lp.height = WRAP_CONTENT;
        }
        setLayoutParams(lp);
        updateHeaderTextContainerAlphaAnimator();
    }

    private void updateHeaderTextContainerAlphaAnimator() {
        if (mIsLandscape) {
            mHeaderTextContainerAlphaAnimator = null;
            mCollapsedDateViewContainer.setVisibility(View.GONE);
            mCollapsedDateViewContainer.setAlpha(mExpandedHeaderAlpha);
            mExpandedDateViewContainer.setVisibility(View.VISIBLE);
            mExpandedDateViewContainer.setAlpha(mExpandedHeaderAlpha);
        } else {
            mHeaderTextContainerAlphaAnimator = new TouchAnimator.Builder()
                .addFloat(mHeaderTextContainerView, "alpha", 0, 0, mExpandedHeaderAlpha)
                .addFloat(mExpandedDateViewContainer, "alpha", 0, 0, mExpandedHeaderAlpha)
                .addFloat(mCollapsedDateViewContainer, "alpha", mExpandedHeaderAlpha, 0, 0)
                .build();
            mCollapsedDateViewContainer.setVisibility(View.VISIBLE);
            mExpandedDateViewContainer.setVisibility(mExpanded ? View.VISIBLE : View.GONE);

        }
    }

    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        mHeaderQsPanel.setExpanded(expanded);
        updateEverything();
        if (!mIsLandscape) {
            mExpandedDateViewContainer.setVisibility(mExpanded ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Animates the inner contents based on the given expansion details.
     *
     * @param forceExpanded whether we should show the state expanded forcibly
     * @param expansionFraction how much the QS panel is expanded/pulled out (up to 1f)
     * @param panelTranslationY how much the panel has physically moved down vertically (required
     *                          for keyguard animations only)
     */
    public void setExpansion(boolean forceExpanded, float expansionFraction,
                             float panelTranslationY) {
        final float keyguardExpansionFraction = forceExpanded ? 1f : expansionFraction;

        if (forceExpanded) {
            // If the keyguard is showing, we want to offset the text so that it comes in at the
            // same time as the panel as it slides down.
            mHeaderTextContainerView.setTranslationY(panelTranslationY);
        } else {
            mHeaderTextContainerView.setTranslationY(0f);
        }

        if (mHeaderTextContainerAlphaAnimator != null) {
            mHeaderTextContainerAlphaAnimator.setPosition(keyguardExpansionFraction);
            if (keyguardExpansionFraction > 0) {
                mHeaderTextContainerView.setVisibility(VISIBLE);
            } else {
                mHeaderTextContainerView.setVisibility(INVISIBLE);
            }
        }

        if (expansionFraction < 1 && expansionFraction > 0.99) {
            if (mHeaderQsPanel.switchTileLayout()) {
                updateResources();
            }
        }
        mKeyguardExpansionFraction = keyguardExpansionFraction;
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        mHeaderTextContainerView.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        mQuickActionButtonsLand.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        updateResources();
        updateEverything();
        updateActionButtonsDisable();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mStatusBarIconController.addIconGroup(mIconManager);
        requestApplyInsets();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Handle padding of the clock
        DisplayCutout cutout = insets.getDisplayCutout();
        Pair<Integer, Integer> cornerCutoutPadding = StatusBarWindowView.cornerCutoutMargins(
                cutout, getDisplay());
        Pair<Integer, Integer> padding =
                StatusBarWindowView.paddingNeededForCutoutAndRoundedCorner(
                        cutout, cornerCutoutPadding, -1);
        mCutOutPaddingLeft = padding.first;
        mCutOutPaddingRight = padding.second;
        mWaterfallTopInset = cutout == null ? 0 : cutout.getWaterfallInsets().top;
        updateSystemIconsViewPadding();
        return super.onApplyWindowInsets(insets);
    }

    private void updateSystemIconsViewPadding() {
        int clockPaddingLeft = 0;
        int clockPaddingRight = 0;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        int leftMargin = lp.leftMargin;
        int rightMargin = lp.rightMargin;

        // The clock might collide with cutouts, let's shift it out of the way.
        // We only do that if the inset is bigger than our own padding, since it's nicer to
        // align with
        if (mCutOutPaddingLeft > 0) {
            // if there's a cutout, let's use at least the rounded corner inset
            int cutoutPadding = Math.max(mCutOutPaddingLeft, mRoundedCornerPadding);
            int contentMarginLeft = isLayoutRtl() ? mContentMarginEnd : mContentMarginStart;
            clockPaddingLeft = Math.max(cutoutPadding - contentMarginLeft - leftMargin, 0);
        }
        if (mCutOutPaddingRight > 0) {
            // if there's a cutout, let's use at least the rounded corner inset
            int cutoutPadding = Math.max(mCutOutPaddingRight, mRoundedCornerPadding);
            int contentMarginRight = isLayoutRtl() ? mContentMarginStart : mContentMarginEnd;
            clockPaddingRight = Math.max(cutoutPadding - contentMarginRight - rightMargin, 0);
        }

        mSystemIconsView.setPadding(clockPaddingLeft,
                mWaterfallTopInset + mStatusBarPaddingTop,
                clockPaddingRight,
                0);
    }

    @Override
    @VisibleForTesting
    public void onDetachedFromWindow() {
        setListening(false);
        mStatusBarIconController.removeIconGroup(mIconManager);
        super.onDetachedFromWindow();
    }

    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mHeaderQsPanel.setListening(listening);
        if (mHeaderQsPanel.switchTileLayout()) {
            updateResources();
        }
        mListening = listening;

        if (mListening) {
            mUserInfoController.addCallback(this);
            mAlarmController.addCallback(this);
        } else {
            mUserInfoController.removeCallback(this);
            mAlarmController.removeCallback(this);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mClockView) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    AlarmClock.ACTION_SHOW_ALARMS), 0);
        } else if (v == mCollapsedDateView || v == mExpandedDateView) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),0);
        } else if (v == mBatteryRemainingIcon) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    Intent.ACTION_POWER_USAGE_SUMMARY),0);
        } else if (v == mAlarmContainer && mAlarmContainer.isVisibleToUser()) {
            if (mNextAlarm.getShowIntent() != null) {
                mActivityStarter.postStartActivityDismissingKeyguard(
                        mNextAlarm.getShowIntent());
            } else {
                Log.d(TAG, "No PendingIntent for next alarm. Using default intent");
                mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                        AlarmClock.ACTION_SHOW_ALARMS), 0);
            }
        }
    }

    @Override
    public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
        mNextAlarm = nextAlarm;
        updateAlarmView();
    }

    private void updateAlarmView() {
        boolean isOriginalVisible = mAlarmContainer.getVisibility() == View.VISIBLE;
        TextView alarmTextView = mAlarmContainer.findViewById(R.id.alarm_text);
        CharSequence originalAlarmText = alarmTextView.getText();

        boolean alarmVisible = false;
        if (mNextAlarm != null) {
            alarmVisible = true;
            alarmTextView.setText(formatNextAlarm(mNextAlarm));
        }
        mAlarmContainer.setVisibility(alarmVisible ? View.VISIBLE : View.GONE);
    }

    public void updateEverything() {
        post(() -> {
            updateHeaderTextContainer();
            setClickable(!mExpanded);
        });
    }

    public void setQSPanel(final QSPanel qsPanel) {
        mQsPanel = qsPanel;
        mMultiUserSwitch.setQsPanel(qsPanel);
        setupHost(qsPanel.getHost());
    }

    public void setupHost(final QSTileHost host) {
        mHost = host;
        //host.setHeaderView(mExpandIndicator);
        mHeaderQsPanel.setQSPanelAndHeader(mQsPanel, this);
        mHeaderQsPanel.setHost(host, null /* No customization in header */);

        mEditClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mActivityStarter.postQSRunnableDismissingKeyguard(() ->
                        mQsPanel.showEdit(view));
            }
        };

        mSettingsClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mDeviceProvisionedController.isCurrentUserSetup()) {
                    // If user isn't setup just unlock the device and dump them back at SUW.
                    mActivityStarter.postQSRunnableDismissingKeyguard(() -> {
                    });
                    return;
                }
                MetricsLogger.action(mContext,
                        mExpanded ? MetricsProto.MetricsEvent.ACTION_QS_EXPANDED_SETTINGS_LAUNCH
                                : MetricsProto.MetricsEvent.ACTION_QS_COLLAPSED_SETTINGS_LAUNCH);
                startSettingsActivity();
            }
        };

        mEdit.setOnClickListener(mEditClickListener);
        mSettingsButton.setOnClickListener(mSettingsClickListener);
        mEditLand.setOnClickListener(mEditClickListener);
        mSettingsButtonLand.setOnClickListener(mSettingsClickListener);
    }

    public void setCallback(Callback qsPanelCallback) {
        mHeaderQsPanel.setCallback(qsPanelCallback);
    }

    private String formatNextAlarm(AlarmManager.AlarmClockInfo info) {
        if (info == null) {
            return "";
        }
        String skeleton = android.text.format.DateFormat
                .is24HourFormat(mContext, ActivityManager.getCurrentUser()) ? "EEEEHm" : "EEEEhma";
        String pattern = android.text.format.DateFormat
                .getBestDateTimePattern(Locale.getDefault(), skeleton);
        return android.text.format.DateFormat.format(pattern, info.getTriggerTime()).toString();
    }

    public static float getColorIntensity(@ColorInt int color) {
        return color == Color.WHITE ? 0 : 1;
    }

    public void setContentMargins(int marginStart, int marginEnd) {
        mContentMarginStart = marginStart;
        mContentMarginEnd = marginEnd;
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view == mHeaderQsPanel) {
                // QS panel doesn't lays out some of its content full width
                mHeaderQsPanel.setContentMargins(marginStart, marginEnd);
            } else {
                MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
                lp.setMarginStart(marginStart);
                lp.setMarginEnd(marginEnd);
                view.setLayoutParams(lp);
            }
        }
        updateSystemIconsViewPadding();
    }

    public void setExpandedScrollAmount(int scrollY) {
        // The scrolling of the expanded qs has changed. Since the header text isn't part of it,
        // but would overlap content, we're fading it out.
        float newAlpha = 1.0f;
        if (mHeaderTextContainerView.getHeight() > 0) {
            newAlpha = MathUtils.map(0, mHeaderTextContainerView.getHeight() / 2.0f, 1.0f, 0.0f,
                    scrollY);
            newAlpha = Interpolators.ALPHA_OUT.getInterpolation(newAlpha);
        }
        mHeaderTextContainerView.setScrollY(scrollY);
        if (newAlpha != mExpandedHeaderAlpha) {
            mExpandedHeaderAlpha = newAlpha;
            mHeaderTextContainerView.setAlpha(MathUtils.lerp(0.0f, mExpandedHeaderAlpha,
                    mKeyguardExpansionFraction));
            updateHeaderTextContainerAlphaAnimator();
        }
    }

    private void updateHeaderTextContainer() {
        mMultiUserSwitch.setVisibility(showUserSwitcher() ? View.VISIBLE : View.INVISIBLE);
        mMultiUserSwitch.setClickable(mMultiUserSwitch.getVisibility() == View.VISIBLE);
        mMultiUserSwitchLand.setVisibility(showUserSwitcher() ? View.VISIBLE : View.GONE);
    }

    private void updateActionButtonsDisable() {
        post(() -> {
            final boolean isDemo = UserManager.isDeviceInDemoMode(mContext);
            mEdit.setVisibility(isDemo ? View.GONE : View.VISIBLE);
            mSettingsButton.setVisibility(isDemo ? View.GONE : View.VISIBLE);
            mEditLand.setVisibility(isDemo ? View.GONE : View.VISIBLE);
            mSettingsButtonLand.setVisibility(isDemo ? View.GONE : View.VISIBLE);
        });
    }

    private void startSettingsActivity() {
        mActivityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS),
                true /* dismissShade */);
    }

    private boolean showUserSwitcher() {
        return mExpanded && mMultiUserSwitch.isMultiUserEnabled();
    }

    @Override
    public void onUserInfoChanged(String name, Drawable picture, String userAccount) {
        if (picture != null &&
                UserManager.get(mContext).isGuestUser(KeyguardUpdateMonitor.getCurrentUser()) &&
                !(picture instanceof UserIconDrawable)) {
            picture = picture.getConstantState().newDrawable(mContext.getResources()).mutate();
            picture.setColorFilter(
                    Utils.getColorAttrDefaultColor(mContext, android.R.attr.colorForeground),
                    Mode.SRC_IN);
        }
        mMultiUserAvatar.setImageDrawable(picture);
        mMultiUserAvatarLand.setImageDrawable(picture);
    }
}
