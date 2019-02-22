package org.linphone.activities;

/*
 LinphoneActivity.java
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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneManager.AddressType;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.call.CallActivity;
import org.linphone.call.CallIncomingActivity;
import org.linphone.call.CallOutgoingActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListener;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallLog;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import org.linphone.core.Reason;
import org.linphone.core.RegistrationState;
import org.linphone.core.TransportType;
import org.linphone.fragments.StatusFragment;
import org.linphone.mediastream.Log;
import org.linphone.ui.AddressText;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

public class LinphoneActivity extends LinphoneGenericActivity implements OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback, AccountCreatorListener{
    private static final int SETTINGS_ACTIVITY = 123;
    private static final int CALL_ACTIVITY = 19;
    private static final int PERMISSIONS_REQUEST_OVERLAY = 206;
    private static final int PERMISSIONS_REQUEST_SYNC = 207;
    private static final int PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER = 209;
    private static final int PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE = 210;
    private static final int PERMISSIONS_RECORD_AUDIO_ECHO_TESTER = 211;

    private static LinphoneActivity instance;

    private StatusFragment statusFragment;
    private boolean newProxyConfig;
    private boolean isTrialAccount = false;
    private OrientationEventListener mOrientationHelper;
    private CoreListenerStub mListener;

    private boolean callTransfer = false;
    private boolean isOnBackground = false;

    public String mAddressWaitingToBeCalled;

    static public final boolean isInstanciated() {
        return instance != null;
    }

    public static final LinphoneActivity instance() {
        if (instance != null)
            return instance;
        throw new RuntimeException("LinphoneActivity not instantiated yet");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //This must be done before calling super.onCreate().
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        instance = this;

        mListener = new CoreListenerStub() {
            @Override
            public void onRegistrationStateChanged(Core lc, ProxyConfig proxy, RegistrationState state, String smessage) {
                AuthInfo authInfo = lc.findAuthInfo(proxy.getRealm(), proxy.getIdentityAddress().getUsername(), proxy.getDomain());

                if (getResources().getBoolean(R.bool.use_phone_number_validation)
                        && authInfo != null && authInfo.getDomain().equals(getString(R.string.default_domain))) {
                    if (state.equals(RegistrationState.Ok)) {
                        LinphoneManager.getInstance().isAccountWithAlias();
                    }
                }

                if (state.equals(RegistrationState.Failed) && newProxyConfig) {
                    newProxyConfig = false;
                    if (proxy.getError() == Reason.Forbidden) {
                        //displayCustomToast(getString(R.string.error_bad_credentials), Toast.LENGTH_LONG);
                    }
                    if (proxy.getError() == Reason.Unauthorized) {
                        displayCustomToast(getString(R.string.error_unauthorized), Toast.LENGTH_LONG);
                    }
                    if (proxy.getError() == Reason.IOError) {
                        displayCustomToast(getString(R.string.error_io_error), Toast.LENGTH_LONG);
                    }
                }
            }

            @Override
            public void onCallStateChanged(Core lc, Call call, Call.State state, String message) {
                if (state == State.IncomingReceived) {
                    startActivity(new Intent(LinphoneActivity.instance(), CallIncomingActivity.class));
                } else if (state == State.OutgoingInit || state == State.OutgoingProgress) {
                    startActivity(new Intent(LinphoneActivity.instance(), CallOutgoingActivity.class));
                } else if (state == State.End || state == State.Error || state == State.Released) {
                    resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
                }

                int missedCalls = LinphoneManager.getLc().getMissedCallsCount();
            }
        };

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            int missedCalls = lc.getMissedCallsCount();
        }

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                rotation = 0;
                break;
            case Surface.ROTATION_90:
                rotation = 90;
                break;
            case Surface.ROTATION_180:
                rotation = 180;
                break;
            case Surface.ROTATION_270:
                rotation = 270;
                break;
        }

        mAlwaysChangingPhoneAngle = rotation;
        if (LinphoneManager.isInstanciated()) {
            LinphoneManager.getLc().setDeviceRotation(rotation);
            onNewIntent(getIntent());
        }

        linphoneLogIn();
    }

    @SuppressLint("SimpleDateFormat")
    private String secondsToDisplayableString(int secs) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        cal.set(0, 0, 0, 0, 0, secs);
        return dateFormat.format(cal.getTime());
    }

    @Override
    public void onClick(View v) {
        EditText editText = findViewById(R.id.sipAccountToCall);
        LinphoneManager.getInstance().newOutgoingCall(editText.getText().toString(), null);
    }

    public void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

        TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
        toastText.setText(message);

        final Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    public void displayChatRoomError() {
        final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.chat_room_creation_failed));
        Button delete = dialog.findViewById(R.id.delete_button);
        Button cancel = dialog.findViewById(R.id.cancel);
        delete.setVisibility(View.GONE);
        cancel.setText(getString(R.string.ok));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    public Dialog displayDialog(String text) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.colorC));
        d.setAlpha(200);
        dialog.setContentView(R.layout.dialog);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawable(d);

        TextView customText = (TextView) dialog.findViewById(R.id.customText);
        customText.setText(text);
        return dialog;
    }

    public void startIncallActivity(Call currentCall) {
        Intent intent = new Intent(this, CallActivity.class);
        startOrientationSensor();
        startActivityForResult(intent, CALL_ACTIVITY);
    }

    /**
     * Register a sensor to track phoneOrientation changes
     */
    private synchronized void startOrientationSensor() {
        if (mOrientationHelper == null) {
            mOrientationHelper = new LocalOrientationEventListener(this);
        }
        mOrientationHelper.enable();
    }

    private int mAlwaysChangingPhoneAngle = -1;

    public void linphoneLogIn() {
        ProxyConfig proxy = LinphoneManager.getLc().getDefaultProxyConfig();
        if (proxy != null && proxy.getState() == RegistrationState.Ok)
            return;

        AccountCreator accountCreator = LinphoneManager.getLc().createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
        accountCreator.setUsername(getApplicationContext().getString(R.string.account_login));
        accountCreator.setPassword(getApplicationContext().getString(R.string.account_password));
        accountCreator.setDomain(getApplicationContext().getString(R.string.server_domain));
        TransportType transportType = TransportType
                .valueOf(getApplicationContext().getString(R.string.server_transport));
        accountCreator.setTransport(transportType);

        LinphoneManager.getLc().getConfig().loadFromXmlFile(LinphoneManager.getInstance().getmDynamicConfigFile());
        configureProxyConfig(accountCreator);
    }

    public void configureProxyConfig(AccountCreator accountCreator) {
        Core lc = LinphoneManager.getLc();
        ProxyConfig proxyConfig = lc.createProxyConfig();
        AuthInfo authInfo;

        String identity = proxyConfig.getIdentityAddress().asStringUriOnly();
        if (identity == null || accountCreator.getUsername() == null) {
            LinphoneUtils.displayErrorAlert(getString(R.string.error), this);
            return;
        }
        identity = identity.replace("?", accountCreator.getUsername());
        Address addr = Factory.instance().createAddress(identity);
        addr.setDisplayName(accountCreator.getUsername());
        proxyConfig.edit();

        proxyConfig.setIdentityAddress(addr);

        if (accountCreator.getPhoneNumber() != null && accountCreator.getPhoneNumber().length() > 0)
            proxyConfig.setDialPrefix(org.linphone.core.Utils.getPrefixFromE164(accountCreator.getPhoneNumber()));

        proxyConfig.done();

        authInfo = Factory.instance().createAuthInfo(
                accountCreator.getUsername(),
                null,
                accountCreator.getPassword(),
                accountCreator.getHa1(),
                proxyConfig.getRealm(),
                proxyConfig.getDomain());

        lc.addProxyConfig(proxyConfig);
        lc.addAuthInfo(authInfo);
        lc.setDefaultProxyConfig(proxyConfig);

        if (LinphonePreferences.instance() != null)
            LinphonePreferences.instance().setPushNotificationEnabled(true);


        LinphoneManager.getInstance().subscribeFriendList(getResources().getBoolean(R.bool.use_friendlist_subscription));
    }

    @Override
    public void onActivateAccount(AccountCreator creator, AccountCreator.Status status, String resp) {
    }

    @Override
    public void onActivateAlias(AccountCreator creator, AccountCreator.Status status, String resp) {
    }

    @Override
    public void onIsAccountLinked(AccountCreator creator, AccountCreator.Status status, String resp) {
    }

    @Override
    public void onLinkAccount(AccountCreator creator, AccountCreator.Status status, String resp) {
    }

    @Override
    public void onIsAliasUsed(AccountCreator creator, AccountCreator.Status status, String resp) {
    }

    @Override
    public void onIsAccountActivated(AccountCreator creator, AccountCreator.Status status, String resp) {
    }

    @Override
    public void onIsAccountExist(AccountCreator creator, AccountCreator.Status status, String resp) {
    }

    @Override
    public void onUpdateAccount(AccountCreator creator, AccountCreator.Status status, String resp) {
    }

    @Override
    public void onRecoverAccount(AccountCreator creator, AccountCreator.Status status, String resp) {
    }

    @Override
    public void onCreateAccount(AccountCreator creator, AccountCreator.Status status, String resp) {
    }

    private class LocalOrientationEventListener extends OrientationEventListener {
        public LocalOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(final int o) {
            if (o == OrientationEventListener.ORIENTATION_UNKNOWN) {
                return;
            }

            int degrees = 270;
            if (o < 45 || o > 315)
                degrees = 0;
            else if (o < 135)
                degrees = 90;
            else if (o < 225)
                degrees = 180;

            if (mAlwaysChangingPhoneAngle == degrees) {
                return;
            }
            mAlwaysChangingPhoneAngle = degrees;

            Log.d("Phone orientation changed to ", degrees);
            int rotation = (360 - degrees) % 360;
            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (lc != null) {
                lc.setDeviceRotation(rotation);
                Call currentCall = lc.getCurrentCall();
                if (currentCall != null && currentCall.cameraEnabled() && currentCall.getCurrentParams().videoEnabled()) {
                    lc.updateCall(currentCall, null);
                }
            }
        }
    }

    public Boolean isCallTransfer() {
        return callTransfer;
    }

    public void resetClassicMenuLayoutAndGoBackToCallIfStillRunning() {
      if (LinphoneManager.isInstanciated() && LinphoneManager.getLc().getCallsNb() > 0) {
            Call call = LinphoneManager.getLc().getCalls()[0];
            if (call.getState() == Call.State.IncomingReceived) {
                startActivity(new Intent(LinphoneActivity.this, CallIncomingActivity.class));
            } else {
                startIncallActivity(call);
            }
        }
    }

    public void quit() {
        finish();
        stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(getString(R.string.sync_account_type));
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (getIntent() != null && getIntent().getExtras() != null) {
            newProxyConfig = getIntent().getExtras().getBoolean("isNewProxyConfig");
        }

        if (resultCode == Activity.RESULT_FIRST_USER && requestCode == SETTINGS_ACTIVITY) {
            if (data.getExtras().getBoolean("Exit", false))
                quit();
        } else if (resultCode == Activity.RESULT_FIRST_USER && requestCode == CALL_ACTIVITY) {
            getIntent().putExtra("PreviousActivity", CALL_ACTIVITY);
            callTransfer = data != null && data.getBooleanExtra("Transfer", false);
            resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
        } else if (requestCode == PERMISSIONS_REQUEST_OVERLAY) {
            if (Compatibility.canDrawOverlays(this)) {
                LinphonePreferences.instance().enableOverlay(true);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onPause() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        callTransfer = false;
        isOnBackground = true;

        super.onPause();
    }

    public boolean checkAndRequestOverlayPermission() {
        Log.i("[Permission] Draw overlays permission is " + (Compatibility.canDrawOverlays(this) ? "granted" : "denied"));
        if (!Compatibility.canDrawOverlays(this)) {
            Log.i("[Permission] Asking for overlay");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, PERMISSIONS_REQUEST_OVERLAY);
            return false;
        }
        return true;
    }

    public void checkAndRequestExternalStoragePermission() {
        checkAndRequestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 0);
    }

    public void checkAndRequestCameraPermission() {
        checkAndRequestPermission(Manifest.permission.CAMERA, 0);
    }

    public void checkAndRequestWriteContactsPermission() {
        checkAndRequestPermission(Manifest.permission.WRITE_CONTACTS, 0);
    }

    public void checkAndRequestRecordAudioPermissionForEchoCanceller() {
        checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER);
    }

    public void checkAndRequestRecordAudioPermissionsForEchoTester() {
        checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_RECORD_AUDIO_ECHO_TESTER);
    }

    public void checkAndRequestReadExternalStoragePermissionForDeviceRingtone() {
        checkAndRequestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE);
    }

    public void checkAndRequestPermissionsToSendImage() {
        ArrayList<String> permissionsList = new ArrayList<>();

        int readExternalStorage = getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, getPackageName());
        Log.i("[Permission] Read external storage permission is " + (readExternalStorage == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
        Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (readExternalStorage != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            Log.i("[Permission] Asking for read external storage");
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (camera != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA);
            Log.i("[Permission] Asking for camera");
            permissionsList.add(Manifest.permission.CAMERA);
        }
        if (permissionsList.size() > 0) {
            String[] permissions = new String[permissionsList.size()];
            permissions = permissionsList.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, 0);
        }
    }

    private void checkSyncPermission() {
        checkAndRequestPermission(Manifest.permission.WRITE_SYNC_SETTINGS, PERMISSIONS_REQUEST_SYNC);
    }

    public void checkAndRequestPermission(String permission, int result) {
        int permissionGranted = getPackageManager().checkPermission(permission, getPackageName());
        Log.i("[Permission] " + permission + " is " + (permissionGranted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
            Log.i("[Permission] Asking for " + permission);
            ActivityCompat.requestPermissions(this, new String[]{permission}, result);
        }
    }

    public void updateStatusFragment(StatusFragment fragment) {
        statusFragment = fragment;
    }

    public StatusFragment getStatusFragment() {
        return statusFragment;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (permissions.length <= 0)
            return;

        int readContactsI = -1;
        for (int i = 0; i < permissions.length; i++) {
            Log.i("[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
            if (permissions[i].compareTo(Manifest.permission.READ_CONTACTS) == 0 ||
                    permissions[i].compareTo(Manifest.permission.WRITE_CONTACTS) == 0)
                readContactsI = i;
        }

        switch (requestCode) {
            case PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE:
                if (permissions[0].compareTo(Manifest.permission.READ_EXTERNAL_STORAGE) != 0)
                    break;
                boolean enableRingtone = (grantResults[0] == PackageManager.PERMISSION_GRANTED);
                LinphonePreferences.instance().enableDeviceRingtone(enableRingtone);
                LinphoneManager.getInstance().enableDeviceRingtone(enableRingtone);
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ArrayList<String> permissionsList = new ArrayList<>();

        int contacts = getPackageManager().checkPermission(Manifest.permission.READ_CONTACTS, getPackageName());
        Log.i("[Permission] Contacts permission is " + (contacts == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        int readPhone = getPackageManager().checkPermission(Manifest.permission.READ_PHONE_STATE, getPackageName());
        Log.i("[Permission] Read phone state permission is " + (readPhone == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        int ringtone = getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, getPackageName());
        Log.i("[Permission] Read external storage for ring tone permission is " + (ringtone == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (ringtone != PackageManager.PERMISSION_GRANTED) {
            if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.READ_EXTERNAL_STORAGE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Log.i("[Permission] Asking for read external storage for ring tone");
                permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (readPhone != PackageManager.PERMISSION_GRANTED) {
            if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.READ_PHONE_STATE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE)) {
                Log.i("[Permission] Asking for read phone state");
                permissionsList.add(Manifest.permission.READ_PHONE_STATE);
            }
        }
        // This one is to allow floating notifications
        permissionsList.add(Manifest.permission.SYSTEM_ALERT_WINDOW);

        if (permissionsList.size() > 0) {
            String[] permissions = new String[permissionsList.size()];
            permissions = permissionsList.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!LinphoneService.isReady()) {
            startService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        }

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
            if (!LinphoneService.instance().displayServiceNotification()) {
                lc.refreshRegisters();
            }
        }

        LinphoneManager.getInstance().changeStatusToOnline();

        if (!getIntent().getBooleanExtra("DoNotGoToCallActivity", false)) {
            if (LinphoneManager.getLc().getCalls().length > 0) {
                Call call = LinphoneManager.getLc().getCalls()[0];
                Call.State onCallStateChanged = call.getState();

                if (onCallStateChanged == State.IncomingReceived) {
                    startActivity(new Intent(this, CallIncomingActivity.class));
                } else if (onCallStateChanged == State.OutgoingInit || onCallStateChanged == State.OutgoingProgress || onCallStateChanged == State.OutgoingRinging) {
                    startActivity(new Intent(this, CallOutgoingActivity.class));
                } else {
                    startIncallActivity(call);
                }
            }
        }

        Intent intent = getIntent();

        if (intent.getStringExtra("msgShared") != null) {
            intent.putExtra("msgShared", "");
        }
        if (intent.getStringExtra("fileShared") != null && intent.getStringExtra("fileShared") != "") {
            intent.putExtra("fileShared", "");
        }
        isOnBackground = false;

        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null && extras.containsKey("SipUriOrNumber")) {
                mAddressWaitingToBeCalled = extras.getString("SipUriOrNumber");
                intent.removeExtra("SipUriOrNumber");
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (mOrientationHelper != null) {
            mOrientationHelper.disable();
            mOrientationHelper = null;
        }

        instance = null;
        super.onDestroy();

        unbindDrawables(findViewById(R.id.topLayout));
        System.gc();
    }

    private void unbindDrawables(View view) {
        if (view != null && view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }
        if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        Bundle extras = intent.getExtras();
        if (extras != null && extras.getBoolean("GoToHistory", false)) {
            intent.putExtra("DoNotGoToCallActivity", true);
        } else if (extras != null && extras.getBoolean("Notification", false)) {
            if (LinphoneManager.getLc().getCallsNb() > 0) {
                Call call = LinphoneManager.getLc().getCalls()[0];
                startIncallActivity(call);
            }
        } else if (extras != null && extras.getBoolean("StartCall", false)) {
            if (CallActivity.isInstanciated()) {
                CallActivity.instance().startIncomingCallActivity();
            } else {
                mAddressWaitingToBeCalled = extras.getString("NumberToCall");
                //startActivity(new Intent(this, CallIncomingActivity.class));
            }
        } else if (extras != null && extras.getBoolean("Transfer", false)) {
            intent.putExtra("DoNotGoToCallActivity", true);
        } else {
            if (LinphoneManager.getLc().getCalls().length > 0) {
                // If a call is ringing, start incomingcallactivity
                Collection<Call.State> incoming = new ArrayList<>();
                incoming.add(Call.State.IncomingReceived);
                if (LinphoneUtils.getCallsInState(LinphoneManager.getLc(), incoming).size() > 0) {
                    if (CallActivity.isInstanciated()) {
                        CallActivity.instance().startIncomingCallActivity();
                    } else {
                        startActivity(new Intent(this, CallIncomingActivity.class));
                    }
                }
            }
        }
        setIntent(intent);
    }

    public boolean isOnBackground() {
        return isOnBackground;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    private int getStatusIconResource(RegistrationState state) {
        try {
            if (state == RegistrationState.Ok) {
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

    public void displayInappNotification(String date) {
        Timestamp now = new Timestamp(new Date().getTime());
        if (LinphonePreferences.instance().getInappPopupTime() != null && Long.parseLong(LinphonePreferences.instance().getInappPopupTime()) > now.getTime()) {
            return;
        } else {
            long newDate = now.getTime() + getResources().getInteger(R.integer.time_between_inapp_notification);
            LinphonePreferences.instance().setInappPopupTime(String.valueOf(newDate));
        }
        if (isTrialAccount) {
            LinphoneService.instance().displayInappNotification(String.format(getString(R.string.inapp_notification_trial_expire), date));
        } else {
            LinphoneService.instance().displayInappNotification(String.format(getString(R.string.inapp_notification_account_expire), date));
        }

    }

    private String timestampToHumanDate(Calendar cal) {
        SimpleDateFormat dateFormat;
        dateFormat = new SimpleDateFormat(getResources().getString(R.string.inapp_popup_date_format));
        return dateFormat.format(cal.getTime());
    }

    private int getDiffDays(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            return -1;
        }
        if (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)) {
            return cal1.get(Calendar.DAY_OF_YEAR) - cal2.get(Calendar.DAY_OF_YEAR);
        }
        return -1;
    }
}
