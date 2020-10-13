/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.statusbar;

import android.annotation.ColorInt;
import android.annotation.StringRes;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.notification.row.StackScrollerDecorView;
import com.android.systemui.statusbar.notification.stack.ExpandableViewState;

public class EmptyShadeView extends StackScrollerDecorView {

    private View mEmptyContainer;
    private TextView mEmptyText;
    private Button mHistoryButton;
    private @StringRes int mText = R.string.empty_shade_text;

    public EmptyShadeView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mEmptyText.setText(mText);
        mEmptyText.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(
                R.dimen.no_notifications_text_size));
        mEmptyContainer.setPadding(0, getResources().getDimensionPixelSize(
                R.dimen.no_notifications_container_top_padding), 0, 0);
        boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        mHistoryButton.setVisibility(isLandscape ? View.GONE : View.VISIBLE);
    }

    @Override
    protected View findContentView() {
        return findViewById(R.id.no_notifications_container);
    }

    @Override
    protected View findSecondaryView() {
        return null;
    }

    public void setTint(@ColorInt int color) {
        mEmptyText.setTextColor(color);
        mHistoryButton.setTextColor(color);
        mHistoryButton.setBackground(getResources().getDrawable(
                R.drawable.btn_translucent_borderless));
    }

    public void setText(@StringRes int text) {
        mText = text;
        mEmptyText.setText(mText);
    }

    public int getTextResource() {
        return mText;
    }

    public void setShowHistory(boolean show) {
        mHistoryButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public View getEmptyTextView() {
        return mEmptyText;
    }

    public View getHistoryButton() {
        return mHistoryButton;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEmptyContainer = (View) findContentView();
        mEmptyText = findViewById(R.id.no_notifications);
        mHistoryButton = findViewById(R.id.notifications_history_btn);
    }

    @Override
    public ExpandableViewState createExpandableViewState() {
        return new EmptyShadeViewState();
    }

    public class EmptyShadeViewState extends ExpandableViewState {
        @Override
        public void applyToView(View view) {
            super.applyToView(view);
            if (view instanceof EmptyShadeView) {
                EmptyShadeView emptyShadeView = (EmptyShadeView) view;
                boolean visible = this.clipTopAmount <= mEmptyContainer.getPaddingTop() * 0.6f;
                emptyShadeView.setContentVisible(visible && emptyShadeView.isVisible());
            }
        }
    }
}
