package org.linphone.fragments;
/*
StatusFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.call.CallActivity;
import org.linphone.core.Call;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Event;
import org.linphone.core.MediaEncryption;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.mediastream.Log;

public class StatusFragment extends Fragment {
    private Handler refreshHandler = new Handler();
    private TextView statusText;
    private ImageView statusLed, callQuality;
    private Runnable mCallQualityUpdater;
    private boolean isInCall, isAttached = false, isZrtpAsk;
    private CoreListenerStub mListener;
    private Dialog ZRTPdialog = null;
    private int mDisplayedQuality = -1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.status, container, false);

        statusText = view.findViewById(R.id.status_text);
        statusLed = view.findViewById(R.id.status_led);
        callQuality = view.findViewById(R.id.call_quality);

        mListener = new CoreListenerStub() {
            @Override
            public void onRegistrationStateChanged(final Core lc, final ProxyConfig proxy, final RegistrationState state, String smessage) {
                if (!isAttached || !LinphoneService.isReady()) {
                    return;
                }

                if (lc.getProxyConfigList() == null) {
                    statusLed.setImageResource(R.drawable.led_disconnected);
                    statusText.setText(getString(R.string.no_account));
                } else {
                    statusLed.setVisibility(View.VISIBLE);
                }

                if (lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().equals(proxy)) {
                    statusLed.setImageResource(getStatusIconResource(state, true));
                    statusText.setText(getStatusIconText(state));
                } else if (lc.getDefaultProxyConfig() == null) {
                    statusLed.setImageResource(getStatusIconResource(state, true));
                    statusText.setText(getStatusIconText(state));
                }

                try {
                    statusText.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            lc.refreshRegisters();
                        }
                    });
                } catch (IllegalStateException ise) {
                }
            }

            @Override
            public void onNotifyReceived(Core lc, Event ev, String eventName, Content content) {

                if (!content.getType().equals("application")) return;
                if (!content.getSubtype().equals("simple-message-summary")) return;

                if (content.getSize() == 0) return;

                String data = content.getStringBuffer();
                String[] voiceMail = data.split("voice-message: ");
                final String[] intToParse = voiceMail[1].split("/", 0);
            }
        };

        isAttached = true;
        Activity activity = getActivity();

        if (activity instanceof LinphoneActivity) {
            ((LinphoneActivity) activity).updateStatusFragment(this);
            isInCall = false;
        } else if (activity instanceof CallActivity) {
            ((CallActivity) activity).updateStatusFragment(this);
            isInCall = true;
        }

        return view;
    }

    public void setCoreListener() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);

            ProxyConfig lpc = lc.getDefaultProxyConfig();
            if (lpc != null) {
                mListener.onRegistrationStateChanged(lc, lpc, lpc.getState(), null);
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        isAttached = false;
    }

    public void resetAccountStatus() {
        if (LinphoneManager.getLc().getProxyConfigList().length == 0) {
            statusLed.setImageResource(R.drawable.led_disconnected);
            statusText.setText(getString(R.string.no_account));
        }
    }

    private int getStatusIconResource(RegistrationState state, boolean isDefaultAccount) {
        try {
            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            boolean defaultAccountConnected = (isDefaultAccount && lc != null && lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().getState() == RegistrationState.Ok) || !isDefaultAccount;
            if (state == RegistrationState.Ok && defaultAccountConnected) {
                return R.drawable.led_connected;
            } else if (state == RegistrationState.Progress) {
                return R.drawable.led_inprogress;
            } else if (state == RegistrationState.Failed) {
                return R.drawable.led_error;
            } else {
                return R.drawable.led_disconnected;
            }
        } catch (Exception e) {
            Log.e(e);
        }

        return R.drawable.led_disconnected;
    }

    private String getStatusIconText(RegistrationState state) {
        Context context = getActivity();
        if (!isAttached && LinphoneActivity.isInstanciated())
            context = LinphoneActivity.instance();
        else if (!isAttached && LinphoneService.isReady())
            context = LinphoneService.instance();

        try {
            if (state == RegistrationState.Ok && LinphoneManager.getLcIfManagerNotDestroyedOrNull().getDefaultProxyConfig().getState() == RegistrationState.Ok) {
                return context.getString(R.string.status_connected);
            } else if (state == RegistrationState.Progress) {
                return context.getString(R.string.status_in_progress);
            } else if (state == RegistrationState.Failed) {
                return context.getString(R.string.status_error);
            } else {
                return context.getString(R.string.status_not_connected);
            }
        } catch (Exception e) {
            Log.e(e);
        }

        return context.getString(R.string.status_not_connected);
    }

    //INCALL STATUS BAR
    private void startCallQuality() {
        callQuality.setVisibility(View.VISIBLE);
        refreshHandler.postDelayed(mCallQualityUpdater = new Runnable() {
            Call mCurrentCall = LinphoneManager.getLc()
                    .getCurrentCall();

            public void run() {
                if (mCurrentCall == null) {
                    mCallQualityUpdater = null;
                    return;
                }
                float newQuality = mCurrentCall.getCurrentQuality();
                updateQualityOfSignalIcon(newQuality);

                if (isInCall) {
                    refreshHandler.postDelayed(this, 1000);
                } else
                    mCallQualityUpdater = null;
            }
        }, 1000);
    }

    void updateQualityOfSignalIcon(float quality) {
        int iQuality = (int) quality;

        if (iQuality == mDisplayedQuality) return;
        if (quality >= 4) // Good Quality
        {
            callQuality.setImageResource(
                    R.drawable.call_quality_indicator_4);
        } else if (quality >= 3) // Average quality
        {
            callQuality.setImageResource(
                    R.drawable.call_quality_indicator_3);
        } else if (quality >= 2) // Low quality
        {
            callQuality.setImageResource(
                    R.drawable.call_quality_indicator_2);
        } else if (quality >= 1) // Very low quality
        {
            callQuality.setImageResource(
                    R.drawable.call_quality_indicator_1);
        } else // Worst quality
        {
            callQuality.setImageResource(
                    R.drawable.call_quality_indicator_0);
        }
        mDisplayedQuality = iQuality;
    }

    @Override
    public void onResume() {
        super.onResume();

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
            ProxyConfig lpc = lc.getDefaultProxyConfig();
            if (lpc != null) {
                mListener.onRegistrationStateChanged(lc, lpc, lpc.getState(), null);
            }

            Call call = lc.getCurrentCall();
            if (isInCall && (call != null || lc.getConferenceSize() > 1 || lc.getCallsNb() > 0)) {
                if (call != null) {
                    startCallQuality();
                }
                callQuality.setVisibility(View.VISIBLE);

                // We are obviously connected
                if (lc.getDefaultProxyConfig() == null) {
                    statusLed.setImageResource(R.drawable.led_disconnected);
                    statusText.setText(getString(R.string.no_account));
                } else {
                    statusLed.setImageResource(getStatusIconResource(lc.getDefaultProxyConfig().getState(), true));
                    statusText.setText(getStatusIconText(lc.getDefaultProxyConfig().getState()));
                }
            }
        } else {
            statusText.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        if (mCallQualityUpdater != null) {
            refreshHandler.removeCallbacks(mCallQualityUpdater);
            mCallQualityUpdater = null;
        }
    }

    public boolean getisZrtpAsk() {
        return isZrtpAsk;
    }

    public void setisZrtpAsk(boolean bool) {
        isZrtpAsk = bool;
    }
}
