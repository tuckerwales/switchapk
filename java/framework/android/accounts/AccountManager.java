package android.accounts;

import android.content.Context;

public class AccountManager {
    public static final String KEY_ACCOUNT_NAME = "authAccount";
    public static final String KEY_ACCOUNT_TYPE = "accountType";
    public static final String KEY_AUTHTOKEN = "authtoken";
    public static final String KEY_INTENT = "intent";

    public static AccountManager get(Context context) { return new AccountManager(); }
    public Account[] getAccounts() { return new Account[0]; }
    public Account[] getAccountsByType(String type) { return new Account[0]; }
    public boolean addAccountExplicitly(Account account, String password, android.os.Bundle userdata) { return false; }
    public String getUserData(Account account, String key) { return null; }
    public void setUserData(Account account, String key, String value) {}
    public String getPassword(Account account) { return null; }
}
