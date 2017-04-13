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

import android.net.Uri;

import com.tokenbrowser.view.activity.QrCodeHandlerActivity;

public class QrCodeHandlerPresenter implements Presenter<QrCodeHandlerActivity> {

    private QrCodeHandlerActivity activity;

    @Override
    public void onViewAttached(QrCodeHandlerActivity view) {
        this.activity = view;
        processIntentData();
    }

    private void processIntentData() {
        final Uri data = this.activity.getIntent().getData();
        handleIntentUri(data);
    }

    private void handleIntentUri(final Uri uri) {
        if (uri != null && uri.toString().startsWith("ethereum")) {
            handleExternalPayment(uri.toString());
        }
    }

    private void handleExternalPayment(final String uri) {
        this.activity.getIntent().setData(null);
        //Show confirmation dialog
    }

    @Override
    public void onViewDetached() {
        this.activity = null;
    }

    @Override
    public void onDestroyed() {
        this.activity = null;
    }
}
