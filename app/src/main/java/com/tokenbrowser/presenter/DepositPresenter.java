/*
 * 	Copyright (c) 2017. Token Browser, Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tokenbrowser.presenter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.tokenbrowser.R;
import com.tokenbrowser.model.local.User;
import com.tokenbrowser.util.ImageUtil;
import com.tokenbrowser.util.LogUtil;
import com.tokenbrowser.util.SharedPrefsUtil;
import com.tokenbrowser.view.BaseApplication;
import com.tokenbrowser.view.activity.DepositActivity;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class DepositPresenter implements Presenter<DepositActivity> {

    private DepositActivity activity;
    private User localUser;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttaching = true;

    @Override
    public void onViewAttached(DepositActivity view) {
        this.activity = view;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }

        attachListeners();
        updateView();
        initClickListeners();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initClickListeners() {
        this.activity.getBinding().closeButton.setOnClickListener(this::handleCloseButtonClicked);
        this.activity.getBinding().copyToClipboard.setOnClickListener(this::handleCopyToClipboardClicked);
    }

    private void handleCloseButtonClicked(final View v) {
        this.activity.finish();
    }

    private void handleCopyToClipboardClicked(final View v) {
        final ClipboardManager clipboard = (ClipboardManager) this.activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText(this.activity.getString(R.string.payment_address), this.localUser.getPaymentAddress());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this.activity, this.activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
    }

    private void attachListeners() {
        final Subscription sub =
                BaseApplication.get()
                .getTokenManager()
                .getUserManager()
                .getCurrentUser()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleUserCallback,
                        this::handleUserError
                );

        this.subscriptions.add(sub);
    }

    private void handleUserError(final Throwable throwable) {
        Crashlytics.logException(throwable);
        LogUtil.e(getClass(), throwable.toString());
    }

    private void handleUserCallback(final User user) {
        this.localUser = user;
        updateView();
    };

    private void updateView() {
        if (this.localUser == null || this.activity == null) return;
        this.activity.getBinding().ownerAddress.setText(this.localUser.getPaymentAddress());
        this.activity.getBinding().copyToClipboard.setEnabled(true);
        generateQrCode();
    }

    private void generateQrCode() {
        final Subscription sub =
                ImageUtil.generateQrCode(this.localUser.getTokenId())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleQrCodeGenerated,
                        this::handleQrCodeError
                );

        this.subscriptions.add(sub);
    }

    private void handleQrCodeError(final Throwable throwable) {
        Crashlytics.logException(throwable);
        LogUtil.e(getClass(), throwable.toString());
    }

    private void handleQrCodeGenerated(final Bitmap qrBitmap) {
        SharedPrefsUtil.saveQrCode(ImageUtil.compressBitmap(qrBitmap));
        renderQrCode(qrBitmap);
    }

    private void renderQrCode(final Bitmap qrCodeBitmap) {
        if (this.activity == null) return;
        this.activity.getBinding().qrCode.setAlpha(0.0f);
        this.activity.getBinding().qrCode.setImageBitmap(qrCodeBitmap);
        this.activity.getBinding().qrCode.animate().alpha(1f).setDuration(200).start();
    }

    @Override
    public void onViewDetached() {
        this.subscriptions.clear();
        this.activity = null;
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
        this.activity = null;
    }
}
