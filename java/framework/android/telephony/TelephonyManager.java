package android.telephony;

import android.os.PersistableBundle;
import java.util.ArrayList;
import java.util.List;

/**
 * The console has no modem, so this answers as an Android Wi-Fi-only device does: no phone, no SIM, empty operator
 * strings (never null, which apps often upper-case), and no identifiers (WS15).
 */
public class TelephonyManager {
    public static final String ACTION_CARRIER_MESSAGING_CLIENT_SERVICE = "android.telephony.action.CARRIER_MESSAGING_CLIENT_SERVICE";
    public static final String ACTION_CARRIER_SIGNAL_DEFAULT_NETWORK_AVAILABLE = "android.telephony.action.CARRIER_SIGNAL_DEFAULT_NETWORK_AVAILABLE";
    public static final String ACTION_CARRIER_SIGNAL_PCO_VALUE = "android.telephony.action.CARRIER_SIGNAL_PCO_VALUE";
    public static final String ACTION_CARRIER_SIGNAL_REDIRECTED = "android.telephony.action.CARRIER_SIGNAL_REDIRECTED";
    public static final String ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED = "android.telephony.action.CARRIER_SIGNAL_REQUEST_NETWORK_FAILED";
    public static final String ACTION_CARRIER_SIGNAL_RESET = "android.telephony.action.CARRIER_SIGNAL_RESET";
    public static final String ACTION_CONFIGURE_VOICEMAIL = "android.telephony.action.CONFIGURE_VOICEMAIL";
    public static final String ACTION_MULTI_SIM_CONFIG_CHANGED = "android.telephony.action.MULTI_SIM_CONFIG_CHANGED";
    public static final String ACTION_NETWORK_COUNTRY_CHANGED = "android.telephony.action.NETWORK_COUNTRY_CHANGED";
    public static final String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";
    public static final String ACTION_RESET_MOBILE_NETWORK_SETTINGS = "android.telephony.action.RESET_MOBILE_NETWORK_SETTINGS";
    public static final String ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE";
    public static final String ACTION_SECRET_CODE = "android.telephony.action.SECRET_CODE";
    public static final String ACTION_SHOW_VOICEMAIL_NOTIFICATION = "android.telephony.action.SHOW_VOICEMAIL_NOTIFICATION";
    public static final String ACTION_SUBSCRIPTION_CARRIER_IDENTITY_CHANGED = "android.telephony.action.SUBSCRIPTION_CARRIER_IDENTITY_CHANGED";
    public static final String ACTION_SUBSCRIPTION_SPECIFIC_CARRIER_IDENTITY_CHANGED = "android.telephony.action.SUBSCRIPTION_SPECIFIC_CARRIER_IDENTITY_CHANGED";
    public static final int ALLOWED_NETWORK_TYPES_REASON_CARRIER = 2;
    public static final int ALLOWED_NETWORK_TYPES_REASON_USER = 0;
    public static final int APPTYPE_CSIM = 4;
    public static final int APPTYPE_ISIM = 5;
    public static final int APPTYPE_RUIM = 3;
    public static final int APPTYPE_SIM = 1;
    public static final int APPTYPE_UNKNOWN = 0;
    public static final int APPTYPE_USIM = 2;
    public static final int AUTHTYPE_EAP_AKA = 129;
    public static final int AUTHTYPE_EAP_SIM = 128;
    public static final int AUTHTYPE_GBA_BOOTSTRAP = 132;
    public static final int AUTHTYPE_GBA_NAF_KEY_EXTERNAL = 133;
    public static final int CALL_COMPOSER_STATUS_BUSINESS_ONLY = 2;
    public static final int CALL_COMPOSER_STATUS_OFF = 0;
    public static final int CALL_COMPOSER_STATUS_ON = 1;
    public static final int CALL_STATE_IDLE = 0;
    public static final int CALL_STATE_OFFHOOK = 2;
    public static final int CALL_STATE_RINGING = 1;
    public static final String CAPABILITY_SLICING_CONFIG_SUPPORTED = "CAPABILITY_SLICING_CONFIG_SUPPORTED";
    public static final int CARRIER_RESTRICTION_STATUS_NOT_RESTRICTED = 1;
    public static final int CARRIER_RESTRICTION_STATUS_RESTRICTED = 2;
    public static final int CARRIER_RESTRICTION_STATUS_RESTRICTED_TO_CALLER = 3;
    public static final int CARRIER_RESTRICTION_STATUS_UNKNOWN = 0;
    public static final int CDMA_ROAMING_MODE_AFFILIATED = 1;
    public static final int CDMA_ROAMING_MODE_ANY = 2;
    public static final int CDMA_ROAMING_MODE_HOME = 0;
    public static final int CDMA_ROAMING_MODE_RADIO_DEFAULT = -1;
    public static final int DATA_ACTIVITY_DORMANT = 4;
    public static final int DATA_ACTIVITY_IN = 1;
    public static final int DATA_ACTIVITY_INOUT = 3;
    public static final int DATA_ACTIVITY_NONE = 0;
    public static final int DATA_ACTIVITY_OUT = 2;
    public static final int DATA_CONNECTED = 2;
    public static final int DATA_CONNECTING = 1;
    public static final int DATA_DISCONNECTED = 0;
    public static final int DATA_DISCONNECTING = 4;
    public static final int DATA_ENABLED_REASON_CARRIER = 2;
    public static final int DATA_ENABLED_REASON_OVERRIDE = 4;
    public static final int DATA_ENABLED_REASON_POLICY = 1;
    public static final int DATA_ENABLED_REASON_THERMAL = 3;
    public static final int DATA_ENABLED_REASON_UNKNOWN = -1;
    public static final int DATA_ENABLED_REASON_USER = 0;
    public static final int DATA_HANDOVER_IN_PROGRESS = 5;
    public static final int DATA_SUSPENDED = 3;
    public static final int DATA_UNKNOWN = -1;
    public static final int DEFAULT_PORT_INDEX = 0;
    public static final int ERI_FLASH = 2;
    public static final int ERI_OFF = 1;
    public static final int ERI_ON = 0;
    public static final String EVENT_DISPLAY_EMERGENCY_MESSAGE = "android.telephony.event.DISPLAY_EMERGENCY_MESSAGE";
    public static final String EXTRA_ACTIVE_SIM_SUPPORTED_COUNT = "android.telephony.extra.ACTIVE_SIM_SUPPORTED_COUNT";
    public static final String EXTRA_APN_PROTOCOL = "android.telephony.extra.APN_PROTOCOL";
    public static final String EXTRA_APN_TYPE = "android.telephony.extra.APN_TYPE";
    public static final String EXTRA_CALL_VOICEMAIL_INTENT = "android.telephony.extra.CALL_VOICEMAIL_INTENT";
    public static final String EXTRA_CARRIER_ID = "android.telephony.extra.CARRIER_ID";
    public static final String EXTRA_CARRIER_NAME = "android.telephony.extra.CARRIER_NAME";
    public static final String EXTRA_DATA_FAIL_CAUSE = "android.telephony.extra.DATA_FAIL_CAUSE";
    public static final String EXTRA_DEFAULT_NETWORK_AVAILABLE = "android.telephony.extra.DEFAULT_NETWORK_AVAILABLE";
    public static final String EXTRA_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE = "android.telephony.extra.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE";
    public static final String EXTRA_EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT = "android.telephony.extra.EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT";
    public static final String EXTRA_HIDE_PUBLIC_SETTINGS = "android.telephony.extra.HIDE_PUBLIC_SETTINGS";
    public static final String EXTRA_INCOMING_NUMBER = "incoming_number";
    public static final String EXTRA_IS_REFRESH = "android.telephony.extra.IS_REFRESH";
    public static final String EXTRA_LAST_KNOWN_NETWORK_COUNTRY = "android.telephony.extra.LAST_KNOWN_NETWORK_COUNTRY";
    public static final String EXTRA_LAUNCH_VOICEMAIL_SETTINGS_INTENT = "android.telephony.extra.LAUNCH_VOICEMAIL_SETTINGS_INTENT";
    public static final String EXTRA_NETWORK_COUNTRY = "android.telephony.extra.NETWORK_COUNTRY";
    public static final String EXTRA_NOTIFICATION_COUNT = "android.telephony.extra.NOTIFICATION_COUNT";
    public static final String EXTRA_PCO_ID = "android.telephony.extra.PCO_ID";
    public static final String EXTRA_PCO_VALUE = "android.telephony.extra.PCO_VALUE";
    public static final String EXTRA_PHONE_ACCOUNT_HANDLE = "android.telephony.extra.PHONE_ACCOUNT_HANDLE";
    public static final String EXTRA_REDIRECTION_URL = "android.telephony.extra.REDIRECTION_URL";
    public static final String EXTRA_SPECIFIC_CARRIER_ID = "android.telephony.extra.SPECIFIC_CARRIER_ID";
    public static final String EXTRA_SPECIFIC_CARRIER_NAME = "android.telephony.extra.SPECIFIC_CARRIER_NAME";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_STATE_IDLE = "IDLE";
    public static final String EXTRA_STATE_OFFHOOK = "OFFHOOK";
    public static final String EXTRA_STATE_RINGING = "RINGING";
    public static final String EXTRA_SUBSCRIPTION_ID = "android.telephony.extra.SUBSCRIPTION_ID";
    public static final String EXTRA_VOICEMAIL_NUMBER = "android.telephony.extra.VOICEMAIL_NUMBER";
    public static final int INCLUDE_LOCATION_DATA_COARSE = 1;
    public static final int INCLUDE_LOCATION_DATA_FINE = 2;
    public static final int INCLUDE_LOCATION_DATA_NONE = 0;
    public static final String METADATA_HIDE_VOICEMAIL_SETTINGS_MENU = "android.telephony.HIDE_VOICEMAIL_SETTINGS_MENU";
    public static final int MULTISIM_ALLOWED = 0;
    public static final int MULTISIM_NOT_SUPPORTED_BY_CARRIER = 2;
    public static final int MULTISIM_NOT_SUPPORTED_BY_HARDWARE = 1;
    public static final int NETWORK_SELECTION_MODE_AUTO = 1;
    public static final int NETWORK_SELECTION_MODE_MANUAL = 2;
    public static final int NETWORK_SELECTION_MODE_UNKNOWN = 0;
    public static final int NETWORK_TYPE_1xRTT = 7;
    public static final long NETWORK_TYPE_BITMASK_1xRTT = 64l;
    public static final long NETWORK_TYPE_BITMASK_CDMA = 8l;
    public static final long NETWORK_TYPE_BITMASK_EDGE = 2l;
    public static final long NETWORK_TYPE_BITMASK_EHRPD = 8192l;
    public static final long NETWORK_TYPE_BITMASK_EVDO_0 = 16l;
    public static final long NETWORK_TYPE_BITMASK_EVDO_A = 32l;
    public static final long NETWORK_TYPE_BITMASK_EVDO_B = 2048l;
    public static final long NETWORK_TYPE_BITMASK_GPRS = 1l;
    public static final long NETWORK_TYPE_BITMASK_GSM = 32768l;
    public static final long NETWORK_TYPE_BITMASK_HSDPA = 128l;
    public static final long NETWORK_TYPE_BITMASK_HSPA = 512l;
    public static final long NETWORK_TYPE_BITMASK_HSPAP = 16384l;
    public static final long NETWORK_TYPE_BITMASK_HSUPA = 256l;
    public static final long NETWORK_TYPE_BITMASK_IWLAN = 131072l;
    public static final long NETWORK_TYPE_BITMASK_LTE = 4096l;
    public static final long NETWORK_TYPE_BITMASK_LTE_CA = 262144l;
    public static final long NETWORK_TYPE_BITMASK_NR = 524288l;
    public static final long NETWORK_TYPE_BITMASK_TD_SCDMA = 65536l;
    public static final long NETWORK_TYPE_BITMASK_UMTS = 4l;
    public static final long NETWORK_TYPE_BITMASK_UNKNOWN = 0l;
    public static final int NETWORK_TYPE_CDMA = 4;
    public static final int NETWORK_TYPE_EDGE = 2;
    public static final int NETWORK_TYPE_EHRPD = 14;
    public static final int NETWORK_TYPE_EVDO_0 = 5;
    public static final int NETWORK_TYPE_EVDO_A = 6;
    public static final int NETWORK_TYPE_EVDO_B = 12;
    public static final int NETWORK_TYPE_GPRS = 1;
    public static final int NETWORK_TYPE_GSM = 16;
    public static final int NETWORK_TYPE_HSDPA = 8;
    public static final int NETWORK_TYPE_HSPA = 10;
    public static final int NETWORK_TYPE_HSPAP = 15;
    public static final int NETWORK_TYPE_HSUPA = 9;
    public static final int NETWORK_TYPE_IDEN = 11;
    public static final int NETWORK_TYPE_IWLAN = 18;
    public static final int NETWORK_TYPE_LTE = 13;
    public static final int NETWORK_TYPE_NR = 20;
    public static final int NETWORK_TYPE_TD_SCDMA = 17;
    public static final int NETWORK_TYPE_UMTS = 3;
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    public static final int PHONE_TYPE_CDMA = 2;
    public static final int PHONE_TYPE_GSM = 1;
    public static final int PHONE_TYPE_NONE = 0;
    public static final int PHONE_TYPE_SIP = 3;
    public static final int PREMIUM_CAPABILITY_PRIORITIZE_LATENCY = 34;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_IN_PROGRESS = 4;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED = 3;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_DISABLED = 7;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_ERROR = 8;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_ENTITLEMENT_CHECK_FAILED = 13;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_FEATURE_NOT_SUPPORTED = 10;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_NETWORK_NOT_AVAILABLE = 12;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_DEFAULT_DATA_SUBSCRIPTION = 14;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_FOREGROUND = 5;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_PENDING_NETWORK_SETUP = 15;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_REQUEST_FAILED = 11;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_SUCCESS = 1;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED = 2;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_TIMEOUT = 9;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_CANCELED = 6;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_DISABLED = 16;
    public static final int SET_OPPORTUNISTIC_SUB_INACTIVE_SUBSCRIPTION = 2;
    public static final int SET_OPPORTUNISTIC_SUB_NO_OPPORTUNISTIC_SUB_AVAILABLE = 3;
    public static final int SET_OPPORTUNISTIC_SUB_REMOTE_SERVICE_EXCEPTION = 4;
    public static final int SET_OPPORTUNISTIC_SUB_SUCCESS = 0;
    public static final int SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED = 1;
    public static final int SIM_STATE_ABSENT = 1;
    public static final int SIM_STATE_CARD_IO_ERROR = 8;
    public static final int SIM_STATE_CARD_RESTRICTED = 9;
    public static final int SIM_STATE_NETWORK_LOCKED = 4;
    public static final int SIM_STATE_NOT_READY = 6;
    public static final int SIM_STATE_PERM_DISABLED = 7;
    public static final int SIM_STATE_PIN_REQUIRED = 2;
    public static final int SIM_STATE_PUK_REQUIRED = 3;
    public static final int SIM_STATE_READY = 5;
    public static final int SIM_STATE_UNKNOWN = 0;
    public static final int UNINITIALIZED_CARD_ID = -2;
    public static final int UNKNOWN_CARRIER_ID = -1;
    public static final int UNSUPPORTED_CARD_ID = -1;
    public static final int UPDATE_AVAILABLE_NETWORKS_ABORTED = 2;
    public static final int UPDATE_AVAILABLE_NETWORKS_DISABLE_MODEM_FAIL = 5;
    public static final int UPDATE_AVAILABLE_NETWORKS_ENABLE_MODEM_FAIL = 6;
    public static final int UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS = 3;
    public static final int UPDATE_AVAILABLE_NETWORKS_MULTIPLE_NETWORKS_NOT_SUPPORTED = 7;
    public static final int UPDATE_AVAILABLE_NETWORKS_NO_CARRIER_PRIVILEGE = 4;
    public static final int UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE = 8;
    public static final int UPDATE_AVAILABLE_NETWORKS_REMOTE_SERVICE_EXCEPTION = 9;
    public static final int UPDATE_AVAILABLE_NETWORKS_SERVICE_IS_DISABLED = 10;
    public static final int UPDATE_AVAILABLE_NETWORKS_SUCCESS = 0;
    public static final int UPDATE_AVAILABLE_NETWORKS_UNKNOWN_FAILURE = 1;
    public static final int USSD_ERROR_SERVICE_UNAVAIL = -2;
    public static final int USSD_RETURN_FAILURE = -1;
    public static final String VVM_TYPE_CVVM = "vvm_type_cvvm";
    public static final String VVM_TYPE_OMTP = "vvm_type_omtp";

    /** framework-internal: the instance behind Context.TELEPHONY_SERVICE. */
    public TelephonyManager() {}

    public int getPhoneCount() { return 0; }
    public int getActiveModemCount() { return 0; }
    public int getSupportedModemCount() { return 0; }
    public TelephonyManager createForSubscriptionId(int subId) { return this; }
    public String getDeviceSoftwareVersion() { return null; }
    public String getDeviceId() { return null; }
    public String getDeviceId(int slotIndex) { return null; }
    public String getImei() { return null; }
    public String getImei(int slotIndex) { return null; }
    public String getTypeAllocationCode() { return null; }
    public String getTypeAllocationCode(int slotIndex) { return null; }
    public String getMeid() { return null; }
    public String getMeid(int slotIndex) { return null; }
    public String getManufacturerCode() { return null; }
    public String getManufacturerCode(int slotIndex) { return null; }
    public int getPhoneType() { return PHONE_TYPE_NONE; }
    public String getNetworkOperatorName() { return ""; }
    public String getNetworkOperator() { return ""; }
    public String getNetworkSpecifier() { return String.valueOf(getSubscriptionId()); }
    public PersistableBundle getCarrierConfig() { return new PersistableBundle(); }
    public boolean isNetworkRoaming() { return false; }
    public String getNetworkCountryIso() { return ""; }
    public String getNetworkCountryIso(int slotIndex) { return ""; }
    public int getNetworkType() { return NETWORK_TYPE_UNKNOWN; }
    public int getDataNetworkType() { return NETWORK_TYPE_UNKNOWN; }
    public int getVoiceNetworkType() { return NETWORK_TYPE_UNKNOWN; }
    public boolean hasIccCard() { return false; }
    public int getSimState() { return SIM_STATE_ABSENT; }
    public int getSimState(int slotIndex) { return SIM_STATE_ABSENT; }
    public String getSimOperator() { return ""; }
    public String getSimOperatorName() { return ""; }
    public String getSimCountryIso() { return ""; }
    public String getSimSerialNumber() { return null; }
    public String getSubscriberId() { return null; }
    public String getGroupIdLevel1() { return null; }
    public String getLine1Number() { return null; }
    public String getVoiceMailNumber() { return null; }
    public String getVisualVoicemailPackageName() { return null; }
    public String getVoiceMailAlphaTag() { return null; }
    public int getCallState() { return CALL_STATE_IDLE; }
    public int getCallStateForSubscription() { return CALL_STATE_IDLE; }
    public int getDataActivity() { return DATA_ACTIVITY_NONE; }
    public int getDataState() { return DATA_DISCONNECTED; }

    /** Nothing ever changes, so listeners hear nothing. */
    public void listen(PhoneStateListener listener, int events) {}

    public boolean isVoiceCapable() { return false; }
    public boolean isSmsCapable() { return false; }
    public List<CellInfo> getAllCellInfo() { return new ArrayList<CellInfo>(); }
    public String getMmsUserAgent() { return null; }
    public String getMmsUAProfUrl() { return null; }
    /** SubscriptionManager.INVALID_SUBSCRIPTION_ID */
    public int getSubscriptionId() { return -1; }
    public String[] getForbiddenPlmns() { return null; }
    public int getNetworkSelectionMode() { return NETWORK_SELECTION_MODE_UNKNOWN; }
    public int getCallComposerStatus() { return CALL_COMPOSER_STATUS_OFF; }
    public boolean isConcurrentVoiceAndDataSupported() { return false; }
    public boolean isDataEnabled() { return false; }
    public boolean isDataEnabledForReason(int reason) { return false; }
    public boolean canChangeDtmfToneLength() { return false; }
    public boolean isWorldPhone() { return false; }
    public boolean isTtyModeSupported() { return false; }
    public boolean isHearingAidCompatibilitySupported() { return false; }
    public int getSimCarrierId() { return UNKNOWN_CARRIER_ID; }
    public CharSequence getSimCarrierIdName() { return null; }
    public int getSimSpecificCarrierId() { return UNKNOWN_CARRIER_ID; }
    public CharSequence getSimSpecificCarrierIdName() { return null; }
    public int getCarrierIdFromSimMccMnc() { return UNKNOWN_CARRIER_ID; }
    public long getSupportedRadioAccessFamily() { return NETWORK_TYPE_BITMASK_UNKNOWN; }
    public boolean isEmergencyNumber(String number) { return false; }
    public int isMultiSimSupported() { return MULTISIM_NOT_SUPPORTED_BY_HARDWARE; }
}
