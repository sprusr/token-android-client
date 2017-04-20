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

import android.view.View;
import android.widget.Toast;

import com.tokenbrowser.R;
import com.tokenbrowser.crypto.util.TypeConverter;
import com.tokenbrowser.model.local.User;
import com.tokenbrowser.model.sofa.Payment;
import com.tokenbrowser.util.EthUtil;
import com.tokenbrowser.util.ImageUtil;
import com.tokenbrowser.util.PaymentType;
import com.tokenbrowser.view.BaseApplication;
import com.tokenbrowser.view.fragment.DialogFragment.PaymentConfirmationDialog;

import java.math.BigDecimal;
import java.math.BigInteger;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public class PaymentRequestConfirmPresenter implements Presenter<PaymentConfirmationDialog> {

    private PaymentConfirmationDialog view;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttached = true;

    private String encodedEthAmount;
    private String tokenId;
    private String paymentAddress;
    private String memo;
    private @PaymentType.Type int paymentType;

    @Override
    public void onViewAttached(PaymentConfirmationDialog view) {
        this.view = view;

        if (this.firstTimeAttached) {
            this.firstTimeAttached = false;
            initLongLivingObjects();
        }

        initShortLivingObjects();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initShortLivingObjects() {
        initClickListeners();
        processBundleData();

        if (this.paymentAddress != null) {
            updateView(null);
        } else {
            getUserByTokenId();
        }
    }

    private void initClickListeners() {
        this.view.getBinding().approve.setOnClickListener(this::handleApprovedClicked);
        this.view.getBinding().reject.setOnClickListener(this::handleRejectClicked);
    }

    private void handleApprovedClicked(final View v) {
        final Payment payment = new Payment()
                .setValue(this.encodedEthAmount);

        if (this.paymentAddress != null) {
            payment.setToAddress(this.paymentAddress);
            this.view.getPaymentConfirmationListener()
                    .onExternalPaymentApproved(payment);
        } else {
            this.view.getPaymentConfirmationListener()
                    .onTokenPaymentApproved(this.tokenId, payment);
        }
        this.view.dismiss();
    }

    private void handleRejectClicked(final View v) {
        this.view.getPaymentConfirmationListener().onPaymentRejected();
        this.view.dismiss();
    }

    @SuppressWarnings("WrongConstant")
    private void processBundleData() {
        this.tokenId = this.view.getArguments().getString(PaymentConfirmationDialog.TOKEN_ID);
        this.paymentAddress = this.view.getArguments().getString(PaymentConfirmationDialog.PAYMENT_ADDRESS);
        this.encodedEthAmount = this.view.getArguments().getString(PaymentConfirmationDialog.ETH_AMOUNT);
        this.memo = this.view.getArguments().getString(PaymentConfirmationDialog.MEMO);
        this.paymentType = this.view.getArguments().getInt(PaymentConfirmationDialog.PAYMENT_TYPE);
    }

    private void setPaymentAddress() {
        this.view.getBinding().paymentAddress.setVisibility(View.VISIBLE);
        final String paymentAddress = this.view.getString(R.string.payment_address, this.paymentAddress);
        this.view.getBinding().paymentAddress.setText(paymentAddress);
    }

    private void getUserByTokenId() {
        final Subscription sub =
                BaseApplication
                .get()
                .getTokenManager()
                .getUserManager()
                .getUserFromAddress(this.tokenId)
                .toSingle()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::updateView,
                        this::handleUserError
                );

        this.subscriptions.add(sub);
    }

    private void handleUserError(final Throwable throwable) {
        if (this.view == null) return;
        Toast.makeText(
                this.view.getContext(),
                this.view.getString(R.string.invalid_payment),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void updateView(final User user) {
        if (user != null) {
            setUserInfo(user);
        } else {
            setPaymentAddress();
        }
        setTitle();
        setMemo();
        setLocalCurrency();
    }

    private void setTitle() {
        final String title = this.paymentType == PaymentType.TYPE_SEND
                ? this.view.getString(R.string.confirmation_dialog_title_payment)
                : this.view.getString(R.string.confirmation_dialog_title_request);
        this.view.getBinding().title.setText(title);
    }

    private void setUserInfo(final User user) {
        this.view.getBinding().userInfoWrapper.setVisibility(View.VISIBLE);
        ImageUtil.loadFromNetwork(user.getAvatar(), this.view.getBinding().avatar);
        this.view.getBinding().displayName.setText(user.getDisplayName());
        this.view.getBinding().username.setText(user.getUsername());
        final String reviewCount = BaseApplication.get().getString(R.string.parentheses, user.getReviewCount());
        this.view.getBinding().numberOfRatings.setText(reviewCount);
        this.view.getBinding().ratingView.setStars(user.getReputationScore());
    }

    private void setMemo() {
        if (this.memo == null) return;
        this.view.getBinding().memo.setVisibility(View.VISIBLE);
        this.view.getBinding().memo.setText(this.memo);
    }

    private void setLocalCurrency() {
        final BigInteger weiAmount = TypeConverter.StringHexToBigInteger(this.encodedEthAmount);
        final BigDecimal ethAmount = EthUtil.weiToEth(weiAmount);

        final Subscription sub =
                BaseApplication.get()
                .getTokenManager()
                .getBalanceManager()
                .convertEthToLocalCurrencyString(ethAmount)
                .subscribe((localCurrency) -> {
                    final String usdEth = this.view.getString(R.string.eth_usd, localCurrency, getEthValue());
                    this.view.getBinding().ethUsd.setText(usdEth);
                });

        this.subscriptions.add(sub);
    }

    private String getEthValue() {
        final BigInteger eth = TypeConverter.StringHexToBigInteger(this.encodedEthAmount);
        return EthUtil.weiAmountToUserVisibleString(eth);
    }

    @Override
    public void onViewDetached() {
        this.subscriptions.clear();
        this.view = null;
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
        this.view = null;
    }
}
