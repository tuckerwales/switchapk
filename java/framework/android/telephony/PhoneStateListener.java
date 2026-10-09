package android.telephony;

import java.util.List;
import java.util.concurrent.Executor;

/** Telephony state callbacks. TelephonyManager has no modem and never calls them (WS15). */
public class PhoneStateListener {
    public static final int LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE = 4194304;
    public static final int LISTEN_BARRING_INFO = -2147483648;
    public static final int LISTEN_CALL_DISCONNECT_CAUSES = 33554432;
    public static final int LISTEN_CALL_FORWARDING_INDICATOR = 8;
    public static final int LISTEN_CALL_STATE = 32;
    public static final int LISTEN_CELL_INFO = 1024;
    public static final int LISTEN_CELL_LOCATION = 16;
    public static final int LISTEN_DATA_ACTIVITY = 128;
    public static final int LISTEN_DATA_CONNECTION_STATE = 64;
    public static final int LISTEN_DISPLAY_INFO_CHANGED = 1048576;
    public static final int LISTEN_EMERGENCY_NUMBER_LIST = 16777216;
    public static final int LISTEN_IMS_CALL_DISCONNECT_CAUSES = 134217728;
    public static final int LISTEN_MESSAGE_WAITING_INDICATOR = 4;
    public static final int LISTEN_NONE = 0;
    public static final int LISTEN_PRECISE_DATA_CONNECTION_STATE = 4096;
    public static final int LISTEN_REGISTRATION_FAILURE = 1073741824;
    public static final int LISTEN_SERVICE_STATE = 1;
    public static final int LISTEN_SIGNAL_STRENGTH = 2;
    public static final int LISTEN_SIGNAL_STRENGTHS = 256;
    public static final int LISTEN_USER_MOBILE_DATA_STATE = 524288;

    public PhoneStateListener() {}

    public PhoneStateListener(Executor executor) {}

    public void onSignalStrengthChanged(int asu) {}
    public void onMessageWaitingIndicatorChanged(boolean mwi) {}
    public void onCallForwardingIndicatorChanged(boolean cfi) {}
    public void onCallStateChanged(int state, String phoneNumber) {}
    public void onDataConnectionStateChanged(int state) {}
    public void onDataConnectionStateChanged(int state, int networkType) {}
    public void onDataActivity(int direction) {}
    public void onCellInfoChanged(List<CellInfo> cellInfo) {}
    public void onCallDisconnectCauseChanged(int disconnectCause, int preciseDisconnectCause) {}
    public void onUserMobileDataStateChanged(boolean enabled) {}
    public void onActiveDataSubscriptionIdChanged(int subId) {}
}
