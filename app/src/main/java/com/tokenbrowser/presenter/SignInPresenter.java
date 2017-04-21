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

import android.content.Intent;
import android.support.annotation.StringRes;
import android.view.View;
import android.widget.Toast;

import com.tokenbrowser.R;
import com.tokenbrowser.crypto.HDWallet;
import com.tokenbrowser.manager.TokenManager;
import com.tokenbrowser.util.LogUtil;
import com.tokenbrowser.util.SharedPrefsUtil;
import com.tokenbrowser.view.BaseApplication;
import com.tokenbrowser.view.activity.MainActivity;
import com.tokenbrowser.view.activity.SignInActivity;

import rx.Single;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class SignInPresenter implements Presenter<SignInActivity> {

    private SignInActivity activity;
    private boolean firstTimeAttaching = true;
    private CompositeSubscription subscriptions;
    private boolean onGoingTask = false;

    @Override
    public void onViewAttached(SignInActivity view) {
        this.activity = view;
        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }
        initMultilineWorkaround();
        initClickListeners();
    }

    private void initMultilineWorkaround() {
        this.activity.getBinding().password.setHorizontallyScrolling(false);
        this.activity.getBinding().password.setLines(3);
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initClickListeners() {
        this.activity.getBinding().signIn.setOnClickListener(v -> handleSignInClicked());
        this.activity.getBinding().createNewAccount.setOnClickListener(v -> handleCreateNewAccountClicked());
    }

    private void handleSignInClicked() {
        final String passphraseInput = this.activity.getBinding().password.getText().toString().toLowerCase().trim();
        final String[] passphraseArray = passphraseInput.split(" ");
        if (passphraseArray.length != 12) {
            Toast.makeText(
                    this.activity,
                    this.activity.getString(R.string.sign_in_length_error_message),
                    Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        tryCreateWallet(passphraseInput);
    }

    private void tryCreateWallet(final String masterSeed) {
        if (this.onGoingTask) return;
        this.onGoingTask = true;
        this.activity.getBinding().loadingSpinner.setVisibility(View.VISIBLE);

        final Subscription sub =
                new HDWallet()
                .createFromMasterSeed(masterSeed)
                .flatMap(this::initWallet)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess(__ -> SharedPrefsUtil.setHasBackedUpPhrase())
                .subscribe(
                        __ -> handleSuccess(),
                        __ -> handleWalletError(R.string.unable_to_restore_wallet)
                );

        this.subscriptions.add(sub);
    }

    private Single<TokenManager> initWallet(final HDWallet wallet) {
        return BaseApplication
                .get()
                .getTokenManager()
                .init(wallet);
    }

    private void handleCreateNewAccountClicked() {
        if (this.onGoingTask) return;
        this.onGoingTask = true;
        this.activity.getBinding().loadingSpinner.setVisibility(View.VISIBLE);

        final Subscription sub =
                BaseApplication
                .get()
                .getTokenManager()
                .init()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        __ -> handleSuccess(),
                        throwable -> {
                            LogUtil.exception(getClass(), "Error while creating new wallet", throwable);
                            handleWalletError(R.string.unable_to_create_wallet);
                        }
                );

        this.subscriptions.add(sub);
    }

    private void handleSuccess() {
        this.onGoingTask = false;
        goToMainActivity();
    }

    private void goToMainActivity() {
        SharedPrefsUtil.setSignedIn();
        final Intent intent = new Intent(this.activity, MainActivity.class);
        this.activity.startActivity(intent);
        this.activity.finish();
    }

    private void handleWalletError(final @StringRes int stringId) {
        showToast(stringId);
        this.onGoingTask = false;
        this.activity.getBinding().loadingSpinner.setVisibility(View.GONE);
    }

    private void showToast(final @StringRes int stringId) {
        Toast.makeText(
                this.activity,
                this.activity.getString(stringId),
                Toast.LENGTH_SHORT
        ).show();
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
