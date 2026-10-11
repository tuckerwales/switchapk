package android.os;

import android.content.Intent;
import java.util.ArrayList;
import java.util.List;

/**
 * One user, the owner, always unlocked and running, with no restrictions and no profiles (WS15). Restrictions an app
 * sets on itself are kept for the run.
 */
public class UserManager {
    public static final String ALLOW_PARENT_PROFILE_APP_LINKING = "allow_parent_profile_app_linking";
    public static final String DISALLOW_ADD_MANAGED_PROFILE = "no_add_managed_profile";
    public static final String DISALLOW_ADD_PRIVATE_PROFILE = "no_add_private_profile";
    public static final String DISALLOW_ADD_USER = "no_add_user";
    public static final String DISALLOW_ADD_WIFI_CONFIG = "no_add_wifi_config";
    public static final String DISALLOW_ADJUST_VOLUME = "no_adjust_volume";
    public static final String DISALLOW_AIRPLANE_MODE = "no_airplane_mode";
    public static final String DISALLOW_AMBIENT_DISPLAY = "no_ambient_display";
    public static final String DISALLOW_APPS_CONTROL = "no_control_apps";
    public static final String DISALLOW_ASSIST_CONTENT = "no_assist_content";
    public static final String DISALLOW_AUTOFILL = "no_autofill";
    public static final String DISALLOW_BLUETOOTH = "no_bluetooth";
    public static final String DISALLOW_BLUETOOTH_SHARING = "no_bluetooth_sharing";
    public static final String DISALLOW_CAMERA_TOGGLE = "disallow_camera_toggle";
    public static final String DISALLOW_CELLULAR_2G = "no_cellular_2g";
    public static final String DISALLOW_CHANGE_WIFI_STATE = "no_change_wifi_state";
    public static final String DISALLOW_CONFIG_BLUETOOTH = "no_config_bluetooth";
    public static final String DISALLOW_CONFIG_BRIGHTNESS = "no_config_brightness";
    public static final String DISALLOW_CONFIG_CELL_BROADCASTS = "no_config_cell_broadcasts";
    public static final String DISALLOW_CONFIG_CREDENTIALS = "no_config_credentials";
    public static final String DISALLOW_CONFIG_DATE_TIME = "no_config_date_time";
    public static final String DISALLOW_CONFIG_DEFAULT_APPS = "disallow_config_default_apps";
    public static final String DISALLOW_CONFIG_LOCALE = "no_config_locale";
    public static final String DISALLOW_CONFIG_LOCATION = "no_config_location";
    public static final String DISALLOW_CONFIG_MOBILE_NETWORKS = "no_config_mobile_networks";
    public static final String DISALLOW_CONFIG_PRIVATE_DNS = "disallow_config_private_dns";
    public static final String DISALLOW_CONFIG_SCREEN_TIMEOUT = "no_config_screen_timeout";
    public static final String DISALLOW_CONFIG_TETHERING = "no_config_tethering";
    public static final String DISALLOW_CONFIG_VPN = "no_config_vpn";
    public static final String DISALLOW_CONFIG_WIFI = "no_config_wifi";
    public static final String DISALLOW_CONTENT_CAPTURE = "no_content_capture";
    public static final String DISALLOW_CONTENT_SUGGESTIONS = "no_content_suggestions";
    public static final String DISALLOW_CREATE_WINDOWS = "no_create_windows";
    public static final String DISALLOW_CROSS_PROFILE_COPY_PASTE = "no_cross_profile_copy_paste";
    public static final String DISALLOW_DATA_ROAMING = "no_data_roaming";
    public static final String DISALLOW_DEBUGGING_FEATURES = "no_debugging_features";
    public static final String DISALLOW_FACTORY_RESET = "no_factory_reset";
    public static final String DISALLOW_FUN = "no_fun";
    public static final String DISALLOW_GRANT_ADMIN = "no_grant_admin";
    public static final String DISALLOW_INSTALL_APPS = "no_install_apps";
    public static final String DISALLOW_INSTALL_UNKNOWN_SOURCES = "no_install_unknown_sources";
    public static final String DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY = "no_install_unknown_sources_globally";
    public static final String DISALLOW_MICROPHONE_TOGGLE = "disallow_microphone_toggle";
    public static final String DISALLOW_MODIFY_ACCOUNTS = "no_modify_accounts";
    public static final String DISALLOW_MOUNT_PHYSICAL_MEDIA = "no_physical_media";
    public static final String DISALLOW_NEAR_FIELD_COMMUNICATION_RADIO = "no_near_field_communication_radio";
    public static final String DISALLOW_NETWORK_RESET = "no_network_reset";
    public static final String DISALLOW_OUTGOING_BEAM = "no_outgoing_beam";
    public static final String DISALLOW_OUTGOING_CALLS = "no_outgoing_calls";
    public static final String DISALLOW_PRINTING = "no_printing";
    public static final String DISALLOW_REMOVE_MANAGED_PROFILE = "no_remove_managed_profile";
    public static final String DISALLOW_REMOVE_USER = "no_remove_user";
    public static final String DISALLOW_SAFE_BOOT = "no_safe_boot";
    public static final String DISALLOW_SET_USER_ICON = "no_set_user_icon";
    public static final String DISALLOW_SET_WALLPAPER = "no_set_wallpaper";
    public static final String DISALLOW_SHARE_INTO_MANAGED_PROFILE = "no_sharing_into_profile";
    public static final String DISALLOW_SHARE_LOCATION = "no_share_location";
    public static final String DISALLOW_SHARING_ADMIN_CONFIGURED_WIFI = "no_sharing_admin_configured_wifi";
    public static final String DISALLOW_SIM_GLOBALLY = "no_sim_globally";
    public static final String DISALLOW_SMS = "no_sms";
    public static final String DISALLOW_SYSTEM_ERROR_DIALOGS = "no_system_error_dialogs";
    public static final String DISALLOW_ULTRA_WIDEBAND_RADIO = "no_ultra_wideband_radio";
    public static final String DISALLOW_UNIFIED_PASSWORD = "no_unified_password";
    public static final String DISALLOW_UNINSTALL_APPS = "no_uninstall_apps";
    public static final String DISALLOW_UNMUTE_MICROPHONE = "no_unmute_microphone";
    public static final String DISALLOW_USB_FILE_TRANSFER = "no_usb_file_transfer";
    public static final String DISALLOW_USER_SWITCH = "no_user_switch";
    public static final String DISALLOW_WIFI_DIRECT = "no_wifi_direct";
    public static final String DISALLOW_WIFI_TETHERING = "no_wifi_tethering";
    public static final String ENSURE_VERIFY_APPS = "ensure_verify_apps";
    public static final String KEY_RESTRICTIONS_PENDING = "restrictions_pending";
    public static final int QUIET_MODE_DISABLE_ONLY_IF_CREDENTIAL_NOT_REQUIRED = 1;
    public static final int USER_CREATION_FAILED_NOT_PERMITTED = 1;
    public static final int USER_CREATION_FAILED_NO_MORE_USERS = 2;
    public static final int USER_OPERATION_ERROR_CURRENT_USER = 4;
    public static final int USER_OPERATION_ERROR_LOW_STORAGE = 5;
    public static final int USER_OPERATION_ERROR_MANAGED_PROFILE = 2;
    public static final int USER_OPERATION_ERROR_MAX_RUNNING_USERS = 3;
    public static final int USER_OPERATION_ERROR_MAX_USERS = 6;
    public static final int USER_OPERATION_ERROR_UNKNOWN = 1;
    public static final int USER_OPERATION_SUCCESS = 0;
    public static final String USER_TYPE_PROFILE_CLONE = "android.os.usertype.profile.CLONE";
    public static final String USER_TYPE_PROFILE_MANAGED = "android.os.usertype.profile.MANAGED";
    public static final String USER_TYPE_PROFILE_PRIVATE = "android.os.usertype.profile.PRIVATE";

    private final Bundle mRestrictions = new Bundle();

    /** framework-internal: the instance behind Context.USER_SERVICE. */
    public UserManager() {}

    public static boolean supportsMultipleUsers() { return false; }
    public static boolean isHeadlessSystemUserMode() { return false; }
    public String getUserName() { return "Owner"; }
    public boolean isUserAGoat() { return false; }
    public boolean isSystemUser() { return true; }
    public boolean isAdminUser() { return true; }
    public boolean isDemoUser() { return false; }
    public boolean isProfile() { return false; }
    public boolean isManagedProfile() { return false; }
    public boolean isUserRunning(UserHandle user) { return isOwner(user); }
    public boolean isUserRunningOrStopping(UserHandle user) { return isOwner(user); }
    public boolean isUserForeground() { return true; }
    public boolean isUserUnlocked() { return true; }
    public boolean isUserUnlocked(UserHandle user) { return isOwner(user); }
    public Bundle getUserRestrictions() { return new Bundle(mRestrictions); }
    public Bundle getUserRestrictions(UserHandle userHandle) { return new Bundle(mRestrictions); }
    @Deprecated public void setUserRestrictions(Bundle restrictions) { setUserRestrictions(restrictions, null); }
    @Deprecated
    public void setUserRestrictions(Bundle restrictions, UserHandle userHandle) {
        mRestrictions.clear();
        mRestrictions.putAll(restrictions);
    }
    @Deprecated public void setUserRestriction(String key, boolean value) { mRestrictions.putBoolean(key, value); }
    public boolean hasUserRestriction(String restrictionKey) { return mRestrictions.getBoolean(restrictionKey, false); }
    public long getSerialNumberForUser(UserHandle user) { return isOwner(user) ? 0 : -1; }
    public UserHandle getUserForSerialNumber(long serialNumber) { return serialNumber == 0 ? UserHandle.SYSTEM : null; }

    public static Intent createUserCreationIntent(String userName, String accountName, String accountType,
            PersistableBundle accountOptions) {
        return null;
    }

    public int getUserCount() { return 1; }

    public List<UserHandle> getUserProfiles() {
        List<UserHandle> out = new ArrayList<UserHandle>();
        out.add(UserHandle.SYSTEM);
        return out;
    }

    public boolean requestQuietModeEnabled(boolean enableQuietMode, UserHandle userHandle) { return false; }
    public boolean requestQuietModeEnabled(boolean enableQuietMode, UserHandle userHandle, int flags) { return false; }
    public boolean isQuietModeEnabled(UserHandle userHandle) { return false; }
    public Bundle getApplicationRestrictions(String packageName) { return new Bundle(); }
    @Deprecated public boolean setRestrictionsChallenge(String newPin) { return false; }
    public long getUserCreationTime(UserHandle userHandle) { return 0; }

    private static boolean isOwner(UserHandle user) { return user == null || user.getIdentifier() == 0; }
}
