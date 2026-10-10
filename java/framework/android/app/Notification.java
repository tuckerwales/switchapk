package android.app;

import android.content.Context;
import android.content.LocusId;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Port of AOSP Notification's data model: the Builder and the styles write the
 * same extras keys Android does, so code that reads a notification back
 * (NotificationCompat, recoverBuilder, tests) sees what it expects. There is no
 * shade on the Switch; NotificationManager logs what an app posts. Custom
 * RemoteViews content is not supported.
 */
public class Notification implements Parcelable {
    public static final int BADGE_ICON_LARGE = 2;
    public static final int BADGE_ICON_NONE = 0;
    public static final int BADGE_ICON_SMALL = 1;
    public static final String CATEGORY_ALARM = "alarm";
    public static final String CATEGORY_CALL = "call";
    public static final String CATEGORY_EMAIL = "email";
    public static final String CATEGORY_ERROR = "err";
    public static final String CATEGORY_EVENT = "event";
    public static final String CATEGORY_LOCATION_SHARING = "location_sharing";
    public static final String CATEGORY_MESSAGE = "msg";
    public static final String CATEGORY_MISSED_CALL = "missed_call";
    public static final String CATEGORY_NAVIGATION = "navigation";
    public static final String CATEGORY_PROGRESS = "progress";
    public static final String CATEGORY_PROMO = "promo";
    public static final String CATEGORY_RECOMMENDATION = "recommendation";
    public static final String CATEGORY_REMINDER = "reminder";
    public static final String CATEGORY_SERVICE = "service";
    public static final String CATEGORY_SOCIAL = "social";
    public static final String CATEGORY_STATUS = "status";
    public static final String CATEGORY_STOPWATCH = "stopwatch";
    public static final String CATEGORY_SYSTEM = "sys";
    public static final String CATEGORY_TRANSPORT = "transport";
    public static final String CATEGORY_VOICEMAIL = "voicemail";
    public static final String CATEGORY_WORKOUT = "workout";
    public static final int COLOR_DEFAULT = 0;
    public static final int DEFAULT_ALL = -1;
    public static final int DEFAULT_LIGHTS = 4;
    public static final int DEFAULT_SOUND = 1;
    public static final int DEFAULT_VIBRATE = 2;
    public static final String EXTRA_ANSWER_COLOR = "android.answerColor";
    public static final String EXTRA_ANSWER_INTENT = "android.answerIntent";
    public static final String EXTRA_AUDIO_CONTENTS_URI = "android.audioContents";
    public static final String EXTRA_BACKGROUND_IMAGE_URI = "android.backgroundImageUri";
    public static final String EXTRA_BIG_TEXT = "android.bigText";
    public static final String EXTRA_CALL_IS_VIDEO = "android.callIsVideo";
    public static final String EXTRA_CALL_PERSON = "android.callPerson";
    public static final String EXTRA_CALL_TYPE = "android.callType";
    public static final String EXTRA_CHANNEL_GROUP_ID = "android.intent.extra.CHANNEL_GROUP_ID";
    public static final String EXTRA_CHANNEL_ID = "android.intent.extra.CHANNEL_ID";
    public static final String EXTRA_CHRONOMETER_COUNT_DOWN = "android.chronometerCountDown";
    public static final String EXTRA_COLORIZED = "android.colorized";
    public static final String EXTRA_COMPACT_ACTIONS = "android.compactActions";
    public static final String EXTRA_CONVERSATION_TITLE = "android.conversationTitle";
    public static final String EXTRA_DECLINE_COLOR = "android.declineColor";
    public static final String EXTRA_DECLINE_INTENT = "android.declineIntent";
    public static final String EXTRA_HANG_UP_INTENT = "android.hangUpIntent";
    public static final String EXTRA_HISTORIC_MESSAGES = "android.messages.historic";
    public static final String EXTRA_INFO_TEXT = "android.infoText";
    public static final String EXTRA_IS_GROUP_CONVERSATION = "android.isGroupConversation";
    public static final String EXTRA_LARGE_ICON = "android.largeIcon";
    public static final String EXTRA_LARGE_ICON_BIG = "android.largeIcon.big";
    public static final String EXTRA_MEDIA_SESSION = "android.mediaSession";
    public static final String EXTRA_MESSAGES = "android.messages";
    public static final String EXTRA_MESSAGING_PERSON = "android.messagingUser";
    public static final String EXTRA_NOTIFICATION_ID = "android.intent.extra.NOTIFICATION_ID";
    public static final String EXTRA_NOTIFICATION_TAG = "android.intent.extra.NOTIFICATION_TAG";
    public static final String EXTRA_PEOPLE = "android.people";
    public static final String EXTRA_PEOPLE_LIST = "android.people.list";
    public static final String EXTRA_PICTURE = "android.picture";
    public static final String EXTRA_PICTURE_CONTENT_DESCRIPTION = "android.pictureContentDescription";
    public static final String EXTRA_PICTURE_ICON = "android.pictureIcon";
    public static final String EXTRA_PROGRESS = "android.progress";
    public static final String EXTRA_PROGRESS_INDETERMINATE = "android.progressIndeterminate";
    public static final String EXTRA_PROGRESS_MAX = "android.progressMax";
    public static final String EXTRA_REMOTE_INPUT_DRAFT = "android.remoteInputDraft";
    public static final String EXTRA_REMOTE_INPUT_HISTORY = "android.remoteInputHistory";
    public static final String EXTRA_SELF_DISPLAY_NAME = "android.selfDisplayName";
    public static final String EXTRA_SHOW_BIG_PICTURE_WHEN_COLLAPSED = "android.showBigPictureWhenCollapsed";
    public static final String EXTRA_SHOW_CHRONOMETER = "android.showChronometer";
    public static final String EXTRA_SHOW_WHEN = "android.showWhen";
    public static final String EXTRA_SMALL_ICON = "android.icon";
    public static final String EXTRA_SUB_TEXT = "android.subText";
    public static final String EXTRA_SUMMARY_TEXT = "android.summaryText";
    public static final String EXTRA_TEMPLATE = "android.template";
    public static final String EXTRA_TEXT = "android.text";
    public static final String EXTRA_TEXT_LINES = "android.textLines";
    public static final String EXTRA_TITLE = "android.title";
    public static final String EXTRA_TITLE_BIG = "android.title.big";
    public static final String EXTRA_VERIFICATION_ICON = "android.verificationIcon";
    public static final String EXTRA_VERIFICATION_TEXT = "android.verificationText";
    public static final int FLAG_AUTO_CANCEL = 16;
    public static final int FLAG_BUBBLE = 4096;
    public static final int FLAG_FOREGROUND_SERVICE = 64;
    public static final int FLAG_GROUP_SUMMARY = 512;
    public static final int FLAG_HIGH_PRIORITY = 128;
    public static final int FLAG_INSISTENT = 4;
    public static final int FLAG_LOCAL_ONLY = 256;
    public static final int FLAG_NO_CLEAR = 32;
    public static final int FLAG_ONGOING_EVENT = 2;
    public static final int FLAG_ONLY_ALERT_ONCE = 8;
    public static final int FLAG_SHOW_LIGHTS = 1;
    public static final int FOREGROUND_SERVICE_DEFAULT = 0;
    public static final int FOREGROUND_SERVICE_DEFERRED = 2;
    public static final int FOREGROUND_SERVICE_IMMEDIATE = 1;
    public static final int GROUP_ALERT_ALL = 0;
    public static final int GROUP_ALERT_CHILDREN = 2;
    public static final int GROUP_ALERT_SUMMARY = 1;
    public static final String INTENT_CATEGORY_NOTIFICATION_PREFERENCES = "android.intent.category.NOTIFICATION_PREFERENCES";
    public static final int PRIORITY_DEFAULT = 0;
    public static final int PRIORITY_HIGH = 1;
    public static final int PRIORITY_LOW = -1;
    public static final int PRIORITY_MAX = 2;
    public static final int PRIORITY_MIN = -2;
    public static final int STREAM_DEFAULT = -1;
    public static final int VISIBILITY_PRIVATE = 0;
    public static final int VISIBILITY_PUBLIC = 1;
    public static final int VISIBILITY_SECRET = -1;

    public static final AudioAttributes AUDIO_ATTRIBUTES_DEFAULT = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build();

    public long when;
    public int icon;
    public int iconLevel;
    public int number;
    public PendingIntent contentIntent;
    public PendingIntent deleteIntent;
    public PendingIntent fullScreenIntent;
    public CharSequence tickerText;
    // Custom layouts are kept for apps that read them back; switchapk does not inflate them.
    @Deprecated
    public android.widget.RemoteViews contentView;
    @Deprecated
    public android.widget.RemoteViews bigContentView;
    @Deprecated
    public android.widget.RemoteViews headsUpContentView;
    @Deprecated
    public android.widget.RemoteViews tickerView;
    public Bitmap largeIcon;
    public Uri sound;
    public int audioStreamType = STREAM_DEFAULT;
    public AudioAttributes audioAttributes = AUDIO_ATTRIBUTES_DEFAULT;
    public long[] vibrate;
    public int ledARGB;
    public int ledOnMS;
    public int ledOffMS;
    public int defaults;
    public int flags;
    public int priority;
    public int color = COLOR_DEFAULT;
    public int visibility;
    public Notification publicVersion;
    public String category;
    public Bundle extras = new Bundle();
    public Action[] actions;

    String mChannelId;
    String mGroupKey;
    String mSortKey;
    long mTimeout;
    int mBadgeIcon = BADGE_ICON_NONE;
    String mShortcutId;
    LocusId mLocusId;
    CharSequence mSettingsText;
    int mGroupAlertBehavior = GROUP_ALERT_ALL;
    int mFgsDeferBehavior = FOREGROUND_SERVICE_DEFAULT;
    boolean mAllowSystemGeneratedContextualActions = true;
    BubbleMetadata mBubbleMetadata;
    Icon mSmallIcon;
    Icon mLargeIcon;

    public Notification() {
        this.when = System.currentTimeMillis();
        this.priority = PRIORITY_DEFAULT;
    }

    @Deprecated
    public Notification(int icon, CharSequence tickerText, long when) {
        this.icon = icon;
        this.tickerText = tickerText;
        this.when = when;
    }

    public Notification(Parcel parcel) {
        Notification n = (Notification) parcel.readValue(null);
        if (n != null) n.cloneInto(this);
    }

    public String getGroup() { return mGroupKey; }

    public String getSortKey() { return mSortKey; }

    public String getChannelId() { return mChannelId; }

    public long getTimeoutAfter() { return mTimeout; }

    public int getBadgeIconType() { return mBadgeIcon; }

    public String getShortcutId() { return mShortcutId; }

    public LocusId getLocusId() { return mLocusId; }

    public CharSequence getSettingsText() { return mSettingsText; }

    public int getGroupAlertBehavior() { return mGroupAlertBehavior; }

    public BubbleMetadata getBubbleMetadata() { return mBubbleMetadata; }

    public boolean getAllowSystemGeneratedContextualActions() { return mAllowSystemGeneratedContextualActions; }

    public Icon getSmallIcon() { return mSmallIcon; }

    public Icon getLargeIcon() { return mLargeIcon; }

    /** framework-internal (hidden in AOSP). */
    public void setSmallIcon(Icon icon) { mSmallIcon = icon; }

    public Pair<RemoteInput, Action> findRemoteInputActionPair(boolean requiresFreeform) {
        if (actions == null) return null;
        for (Action action : actions) {
            if (action.getRemoteInputs() == null) continue;
            RemoteInput resultRemoteInput = null;
            for (RemoteInput remoteInput : action.getRemoteInputs()) {
                if (remoteInput.getAllowFreeFormInput() || !requiresFreeform) resultRemoteInput = remoteInput;
            }
            if (resultRemoteInput != null) return Pair.create(resultRemoteInput, action);
        }
        return null;
    }

    public List<Action> getContextualActions() {
        ArrayList<Action> out = new ArrayList<Action>();
        if (actions == null) return out;
        for (Action action : actions) if (action.isContextual()) out.add(action);
        return out;
    }

    public boolean hasImage() {
        if (extras == null) return mLargeIcon != null || largeIcon != null;
        return mLargeIcon != null || largeIcon != null || extras.get(EXTRA_PICTURE) != null
                || extras.get(EXTRA_PICTURE_ICON) != null;
    }

    @Override
    public Notification clone() {
        Notification that = new Notification();
        cloneInto(that);
        return that;
    }

    private void cloneInto(Notification that) {
        that.when = when;
        that.icon = icon;
        that.iconLevel = iconLevel;
        that.number = number;
        that.contentIntent = contentIntent;
        that.deleteIntent = deleteIntent;
        that.fullScreenIntent = fullScreenIntent;
        that.tickerText = tickerText;
        that.largeIcon = largeIcon;
        that.sound = sound;
        that.audioStreamType = audioStreamType;
        that.audioAttributes = audioAttributes;
        that.vibrate = vibrate != null ? vibrate.clone() : null;
        that.ledARGB = ledARGB;
        that.ledOnMS = ledOnMS;
        that.ledOffMS = ledOffMS;
        that.defaults = defaults;
        that.flags = flags;
        that.priority = priority;
        that.color = color;
        that.visibility = visibility;
        that.publicVersion = publicVersion != null ? publicVersion.clone() : null;
        that.category = category;
        that.extras = extras != null ? new Bundle(extras) : null;
        if (actions != null) {
            that.actions = new Action[actions.length];
            for (int i = 0; i < actions.length; i++) that.actions[i] = actions[i] != null ? actions[i].clone() : null;
        }
        that.mChannelId = mChannelId;
        that.mGroupKey = mGroupKey;
        that.mSortKey = mSortKey;
        that.mTimeout = mTimeout;
        that.mBadgeIcon = mBadgeIcon;
        that.mShortcutId = mShortcutId;
        that.mLocusId = mLocusId;
        that.mSettingsText = mSettingsText;
        that.mGroupAlertBehavior = mGroupAlertBehavior;
        that.mFgsDeferBehavior = mFgsDeferBehavior;
        that.mAllowSystemGeneratedContextualActions = mAllowSystemGeneratedContextualActions;
        that.mBubbleMetadata = mBubbleMetadata;
        that.mSmallIcon = mSmallIcon;
        that.mLargeIcon = mLargeIcon;
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel parcel, int flags) { parcel.writeValue(clone()); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Notification(channel=").append(mChannelId);
        sb.append(" shortcut=").append(mShortcutId);
        sb.append(" pri=").append(priority);
        sb.append(" flags=0x").append(Integer.toHexString(flags));
        sb.append(" color=0x").append(Integer.toHexString(color));
        if (category != null) sb.append(" category=").append(category);
        if (mGroupKey != null) sb.append(" groupKey=").append(mGroupKey);
        if (extras != null && extras.getString(EXTRA_TEMPLATE) != null) {
            String t = extras.getString(EXTRA_TEMPLATE);
            sb.append(" style=").append(t.substring(t.lastIndexOf('$') + 1));
        }
        if (actions != null) sb.append(" actions=").append(actions.length);
        sb.append(" vis=").append(visibility).append(")");
        return sb.toString();
    }

    public static final Parcelable.Creator<Notification> CREATOR = new Parcelable.Creator<Notification>() {
        public Notification createFromParcel(Parcel parcel) { return new Notification(parcel); }
        public Notification[] newArray(int size) { return new Notification[size]; }
    };

    static CharSequence safeCharSequence(CharSequence cs) {
        if (cs == null) return null;
        if (cs.length() > 5 * 1024) cs = cs.subSequence(0, 5 * 1024);
        return cs;
    }

    // ---------------------------------------------------------------- Action

    public static class Action implements Parcelable {
        public static final int SEMANTIC_ACTION_NONE = 0;
        public static final int SEMANTIC_ACTION_REPLY = 1;
        public static final int SEMANTIC_ACTION_MARK_AS_READ = 2;
        public static final int SEMANTIC_ACTION_MARK_AS_UNREAD = 3;
        public static final int SEMANTIC_ACTION_DELETE = 4;
        public static final int SEMANTIC_ACTION_ARCHIVE = 5;
        public static final int SEMANTIC_ACTION_MUTE = 6;
        public static final int SEMANTIC_ACTION_UNMUTE = 7;
        public static final int SEMANTIC_ACTION_THUMBS_UP = 8;
        public static final int SEMANTIC_ACTION_THUMBS_DOWN = 9;
        public static final int SEMANTIC_ACTION_CALL = 10;

        private final Bundle mExtras;
        private Icon mIcon;
        private final RemoteInput[] mRemoteInputs;
        private final RemoteInput[] mDataOnlyRemoteInputs;
        private boolean mAllowGeneratedReplies = true;
        private final int mSemanticAction;
        private final boolean mIsContextual;
        private final boolean mAuthenticationRequired;

        @Deprecated
        public int icon;
        public CharSequence title;
        public PendingIntent actionIntent;

        @Deprecated
        public Action(int icon, CharSequence title, PendingIntent intent) {
            this(icon != 0 ? Icon.createWithResource("", icon) : null, title, intent, new Bundle(), null, null, true,
                    SEMANTIC_ACTION_NONE, false, false);
        }

        private Action(Icon icon, CharSequence title, PendingIntent intent, Bundle extras, RemoteInput[] remoteInputs,
                RemoteInput[] dataOnly, boolean allowGeneratedReplies, int semanticAction, boolean isContextual,
                boolean requireAuth) {
            mIcon = icon;
            if (icon != null && icon.getType() == Icon.TYPE_RESOURCE) this.icon = icon.getResId();
            this.title = title;
            this.actionIntent = intent;
            mExtras = extras != null ? extras : new Bundle();
            mRemoteInputs = remoteInputs;
            mDataOnlyRemoteInputs = dataOnly;
            mAllowGeneratedReplies = allowGeneratedReplies;
            mSemanticAction = semanticAction;
            mIsContextual = isContextual;
            mAuthenticationRequired = requireAuth;
        }

        public Icon getIcon() {
            if (mIcon == null && icon != 0) mIcon = Icon.createWithResource("", icon);
            return mIcon;
        }

        public Bundle getExtras() { return mExtras; }

        public boolean getAllowGeneratedReplies() { return mAllowGeneratedReplies; }

        public RemoteInput[] getRemoteInputs() { return mRemoteInputs; }

        public int getSemanticAction() { return mSemanticAction; }

        public boolean isContextual() { return mIsContextual; }

        public RemoteInput[] getDataOnlyRemoteInputs() { return mDataOnlyRemoteInputs; }

        public boolean isAuthenticationRequired() { return mAuthenticationRequired; }

        @Override
        public Action clone() {
            return new Action(getIcon(), title, actionIntent, new Bundle(mExtras), mRemoteInputs,
                    mDataOnlyRemoteInputs, mAllowGeneratedReplies, mSemanticAction, mIsContextual,
                    mAuthenticationRequired);
        }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel out, int flags) { out.writeValue(clone()); }

        public static final Parcelable.Creator<Action> CREATOR = new Parcelable.Creator<Action>() {
            public Action createFromParcel(Parcel in) { return (Action) in.readValue(null); }
            public Action[] newArray(int size) { return new Action[size]; }
        };

        public interface Extender {
            Builder extend(Builder builder);
        }

        public static final class Builder {
            private final Icon mIcon;
            private final CharSequence mTitle;
            private final PendingIntent mIntent;
            private boolean mAllowGeneratedReplies = true;
            private final Bundle mExtras;
            private ArrayList<RemoteInput> mRemoteInputs;
            private int mSemanticAction;
            private boolean mIsContextual;
            private boolean mAuthenticationRequired;

            @Deprecated
            public Builder(int icon, CharSequence title, PendingIntent intent) {
                this(icon != 0 ? Icon.createWithResource("", icon) : null, title, intent);
            }

            public Builder(Icon icon, CharSequence title, PendingIntent intent) {
                this(icon, title, intent, new Bundle(), null, true, SEMANTIC_ACTION_NONE, false);
            }

            public Builder(Action action) {
                this(action.getIcon(), action.title, action.actionIntent, new Bundle(action.mExtras),
                        action.getRemoteInputs(), action.getAllowGeneratedReplies(), action.getSemanticAction(),
                        action.isAuthenticationRequired());
                if (action.mDataOnlyRemoteInputs != null) {
                    for (RemoteInput r : action.mDataOnlyRemoteInputs) addRemoteInput(r);
                }
                mIsContextual = action.isContextual();
            }

            private Builder(Icon icon, CharSequence title, PendingIntent intent, Bundle extras,
                    RemoteInput[] remoteInputs, boolean allowGeneratedReplies, int semanticAction,
                    boolean authRequired) {
                mIcon = icon;
                mTitle = title;
                mIntent = intent;
                mExtras = extras;
                if (remoteInputs != null) {
                    mRemoteInputs = new ArrayList<RemoteInput>(remoteInputs.length);
                    mRemoteInputs.addAll(Arrays.asList(remoteInputs));
                }
                mAllowGeneratedReplies = allowGeneratedReplies;
                mSemanticAction = semanticAction;
                mAuthenticationRequired = authRequired;
            }

            public Builder addExtras(Bundle extras) {
                if (extras != null) mExtras.putAll(extras);
                return this;
            }

            public Bundle getExtras() { return mExtras; }

            public Builder addRemoteInput(RemoteInput remoteInput) {
                if (mRemoteInputs == null) mRemoteInputs = new ArrayList<RemoteInput>();
                mRemoteInputs.add(remoteInput);
                return this;
            }

            public Builder setAllowGeneratedReplies(boolean allowGeneratedReplies) {
                mAllowGeneratedReplies = allowGeneratedReplies;
                return this;
            }

            public Builder setSemanticAction(int semanticAction) {
                mSemanticAction = semanticAction;
                return this;
            }

            public Builder setContextual(boolean isContextual) {
                mIsContextual = isContextual;
                return this;
            }

            public Builder extend(Extender extender) {
                extender.extend(this);
                return this;
            }

            public Builder setAuthenticationRequired(boolean authenticationRequired) {
                mAuthenticationRequired = authenticationRequired;
                return this;
            }

            public Action build() {
                if (mIsContextual && mIntent == null) {
                    throw new NullPointerException("Contextual Actions must contain a valid PendingIntent");
                }
                ArrayList<RemoteInput> dataOnly = new ArrayList<RemoteInput>();
                ArrayList<RemoteInput> text = new ArrayList<RemoteInput>();
                if (mRemoteInputs != null) {
                    for (RemoteInput input : mRemoteInputs) {
                        if (input.isDataOnly()) dataOnly.add(input);
                        else text.add(input);
                    }
                }
                RemoteInput[] dataOnlyInputs = dataOnly.isEmpty() ? null : dataOnly.toArray(new RemoteInput[0]);
                RemoteInput[] textInputs = text.isEmpty() ? null : text.toArray(new RemoteInput[0]);
                return new Action(mIcon, mTitle, mIntent, mExtras, textInputs, dataOnlyInputs,
                        mAllowGeneratedReplies, mSemanticAction, mIsContextual, mAuthenticationRequired);
            }
        }
    }

    // ---------------------------------------------------------------- BubbleMetadata

    public static final class BubbleMetadata implements Parcelable {
        private static final int FLAG_AUTO_EXPAND_BUBBLE = 0x1;
        private static final int FLAG_SUPPRESS_NOTIFICATION = 0x2;
        private static final int FLAG_SUPPRESSABLE_BUBBLE = 0x4;
        private static final int FLAG_SUPPRESS_BUBBLE = 0x8;

        private PendingIntent mPendingIntent;
        private PendingIntent mDeleteIntent;
        private Icon mIcon;
        private int mDesiredHeight;
        private int mDesiredHeightResId;
        private int mFlags;
        private String mShortcutId;

        BubbleMetadata() {}

        public String getShortcutId() { return mShortcutId; }

        public PendingIntent getIntent() { return mPendingIntent; }

        public PendingIntent getDeleteIntent() { return mDeleteIntent; }

        public Icon getIcon() { return mIcon; }

        public int getDesiredHeight() { return mDesiredHeight; }

        public int getDesiredHeightResId() { return mDesiredHeightResId; }

        public boolean getAutoExpandBubble() { return (mFlags & FLAG_AUTO_EXPAND_BUBBLE) != 0; }

        public boolean isNotificationSuppressed() { return (mFlags & FLAG_SUPPRESS_NOTIFICATION) != 0; }

        public boolean isBubbleSuppressable() { return (mFlags & FLAG_SUPPRESSABLE_BUBBLE) != 0; }

        public boolean isBubbleSuppressed() { return (mFlags & FLAG_SUPPRESS_BUBBLE) != 0; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }

        public static final Parcelable.Creator<BubbleMetadata> CREATOR = new Parcelable.Creator<BubbleMetadata>() {
            public BubbleMetadata createFromParcel(Parcel in) { return (BubbleMetadata) in.readValue(null); }
            public BubbleMetadata[] newArray(int size) { return new BubbleMetadata[size]; }
        };

        public static final class Builder {
            private PendingIntent mPendingIntent;
            private Icon mIcon;
            private int mDesiredHeight;
            private int mDesiredHeightResId;
            private int mFlags;
            private PendingIntent mDeleteIntent;
            private String mShortcutId;

            @Deprecated
            public Builder() {}

            public Builder(String shortcutId) {
                if (shortcutId == null || shortcutId.isEmpty()) {
                    throw new NullPointerException("Bubble requires a non-null shortcut id");
                }
                mShortcutId = shortcutId;
            }

            public Builder(PendingIntent intent, Icon icon) {
                if (intent == null) throw new NullPointerException("Bubble requires non-null pending intent");
                if (icon == null) throw new NullPointerException("Bubbles require non-null icon");
                mPendingIntent = intent;
                mIcon = icon;
            }

            public Builder setIntent(PendingIntent intent) {
                if (intent == null) throw new NullPointerException("Bubble requires non-null pending intent");
                mPendingIntent = intent;
                return this;
            }

            public Builder setIcon(Icon icon) {
                if (icon == null) throw new NullPointerException("Bubbles require non-null icon");
                mIcon = icon;
                return this;
            }

            public Builder setDesiredHeight(int height) {
                mDesiredHeight = Math.max(height, 0);
                mDesiredHeightResId = 0;
                return this;
            }

            public Builder setDesiredHeightResId(int heightResId) {
                mDesiredHeightResId = heightResId;
                mDesiredHeight = 0;
                return this;
            }

            public Builder setAutoExpandBubble(boolean shouldExpand) { return flag(FLAG_AUTO_EXPAND_BUBBLE, shouldExpand); }

            public Builder setSuppressNotification(boolean shouldSuppressNotif) {
                return flag(FLAG_SUPPRESS_NOTIFICATION, shouldSuppressNotif);
            }

            public Builder setSuppressableBubble(boolean suppressBubble) {
                return flag(FLAG_SUPPRESSABLE_BUBBLE, suppressBubble);
            }

            public Builder setDeleteIntent(PendingIntent deleteIntent) {
                mDeleteIntent = deleteIntent;
                return this;
            }

            private Builder flag(int mask, boolean value) {
                if (value) mFlags |= mask;
                else mFlags &= ~mask;
                return this;
            }

            public BubbleMetadata build() {
                if (mShortcutId == null && mPendingIntent == null) {
                    throw new NullPointerException("Must supply pending intent or shortcut to bubble");
                }
                if (mShortcutId == null && mIcon == null) {
                    throw new NullPointerException("Must supply an icon or shortcut for the bubble");
                }
                BubbleMetadata data = new BubbleMetadata();
                data.mPendingIntent = mPendingIntent;
                data.mDeleteIntent = mDeleteIntent;
                data.mIcon = mIcon;
                data.mDesiredHeight = mDesiredHeight;
                data.mDesiredHeightResId = mDesiredHeightResId;
                data.mFlags = mFlags;
                data.mShortcutId = mShortcutId;
                return data;
            }
        }
    }

    // ---------------------------------------------------------------- Builder

    public interface Extender {
        Builder extend(Builder builder);
    }

    public static class Builder {
        private final Context mContext;
        private Notification mN;
        private Bundle mUserExtras = new Bundle();
        private Style mStyle;
        private final ArrayList<Action> mActions = new ArrayList<Action>();
        private final ArrayList<Person> mPersonList = new ArrayList<Person>();

        public Builder(Context context, String channelId) { this(context, (Notification) null); mN.mChannelId = channelId; }

        @Deprecated
        public Builder(Context context) { this(context, (Notification) null); }

        /** framework-internal (hidden in AOSP): recovers a builder from a built notification. */
        Builder(Context context, Notification toAdopt) {
            mContext = context;
            if (toAdopt == null) {
                mN = new Notification();
                mN.priority = PRIORITY_DEFAULT;
                mN.visibility = VISIBILITY_PRIVATE;
                return;
            }
            mN = toAdopt;
            if (mN.actions != null) mActions.addAll(Arrays.asList(mN.actions));
            ArrayList<Person> people = mN.extras.getParcelableArrayList(EXTRA_PEOPLE_LIST);
            if (people != null) mPersonList.addAll(people);
            String templateClass = mN.extras.getString(EXTRA_TEMPLATE);
            Style style = Style.forName(templateClass);
            if (style != null) {
                style.restoreFromExtras(mN.extras);
                setStyle(style);
            }
        }

        public Builder setShortcutId(String shortcutId) { mN.mShortcutId = shortcutId; return this; }

        public Builder setLocusId(LocusId locusId) { mN.mLocusId = locusId; return this; }

        public Builder setBadgeIconType(int icon) { mN.mBadgeIcon = icon; return this; }

        public Builder setGroupAlertBehavior(int groupAlertBehavior) {
            mN.mGroupAlertBehavior = groupAlertBehavior;
            return this;
        }

        public Builder setBubbleMetadata(BubbleMetadata data) { mN.mBubbleMetadata = data; return this; }

        public Builder setChannelId(String channelId) { mN.mChannelId = channelId; return this; }

        public Builder setTimeoutAfter(long durationMs) { mN.mTimeout = durationMs; return this; }

        public Builder setWhen(long when) { mN.when = when; return this; }

        public Builder setShowWhen(boolean show) { mN.extras.putBoolean(EXTRA_SHOW_WHEN, show); return this; }

        public Builder setUsesChronometer(boolean b) { mN.extras.putBoolean(EXTRA_SHOW_CHRONOMETER, b); return this; }

        public Builder setChronometerCountDown(boolean countDown) {
            mN.extras.putBoolean(EXTRA_CHRONOMETER_COUNT_DOWN, countDown);
            return this;
        }

        public Builder setSmallIcon(int icon) {
            return setSmallIcon(icon != 0 ? Icon.createWithResource(mContext, icon) : null);
        }

        public Builder setSmallIcon(int icon, int level) {
            mN.iconLevel = level;
            return setSmallIcon(icon);
        }

        public Builder setSmallIcon(Icon icon) {
            mN.setSmallIcon(icon);
            if (icon != null && icon.getType() == Icon.TYPE_RESOURCE) mN.icon = icon.getResId();
            return this;
        }

        public Builder setContentTitle(CharSequence title) {
            mN.extras.putCharSequence(EXTRA_TITLE, safeCharSequence(title));
            return this;
        }

        public Builder setContentText(CharSequence text) {
            mN.extras.putCharSequence(EXTRA_TEXT, safeCharSequence(text));
            return this;
        }

        public Builder setSubText(CharSequence text) {
            mN.extras.putCharSequence(EXTRA_SUB_TEXT, safeCharSequence(text));
            return this;
        }

        public Builder setSettingsText(CharSequence text) { mN.mSettingsText = safeCharSequence(text); return this; }

        public Builder setRemoteInputHistory(CharSequence[] text) {
            if (text == null) mN.extras.remove(EXTRA_REMOTE_INPUT_HISTORY);
            else mN.extras.putCharSequenceArray(EXTRA_REMOTE_INPUT_HISTORY, Arrays.copyOf(text, Math.min(5, text.length)));
            return this;
        }

        public Builder setNumber(int number) { mN.number = number; return this; }

        @Deprecated
        public Builder setContentInfo(CharSequence info) {
            mN.extras.putCharSequence(EXTRA_INFO_TEXT, safeCharSequence(info));
            return this;
        }

        public Builder setProgress(int max, int progress, boolean indeterminate) {
            mN.extras.putInt(EXTRA_PROGRESS, progress);
            mN.extras.putInt(EXTRA_PROGRESS_MAX, max);
            mN.extras.putBoolean(EXTRA_PROGRESS_INDETERMINATE, indeterminate);
            return this;
        }

        public Builder setContentIntent(PendingIntent intent) { mN.contentIntent = intent; return this; }

        public Builder setDeleteIntent(PendingIntent intent) { mN.deleteIntent = intent; return this; }

        public Builder setFullScreenIntent(PendingIntent intent, boolean highPriority) {
            mN.fullScreenIntent = intent;
            setFlag(FLAG_HIGH_PRIORITY, highPriority);
            return this;
        }

        public Builder setTicker(CharSequence tickerText) { mN.tickerText = safeCharSequence(tickerText); return this; }

        @Deprecated
        public Builder setTicker(CharSequence tickerText, android.widget.RemoteViews views) {
            mN.tickerText = safeCharSequence(tickerText);
            mN.tickerView = views;
            return this;
        }

        @Deprecated
        public Builder setContent(android.widget.RemoteViews views) { return setCustomContentView(views); }

        public Builder setCustomContentView(android.widget.RemoteViews contentView) {
            mN.contentView = contentView;
            return this;
        }

        public Builder setCustomBigContentView(android.widget.RemoteViews contentView) {
            mN.bigContentView = contentView;
            return this;
        }

        public Builder setCustomHeadsUpContentView(android.widget.RemoteViews contentView) {
            mN.headsUpContentView = contentView;
            return this;
        }

        /** The custom view if one was set; switchapk builds no template RemoteViews. */
        public android.widget.RemoteViews createContentView() { return mN.contentView; }

        public android.widget.RemoteViews createBigContentView() { return mN.bigContentView; }

        public android.widget.RemoteViews createHeadsUpContentView() { return mN.headsUpContentView; }

        public Builder setLargeIcon(Bitmap b) { return setLargeIcon(b != null ? Icon.createWithBitmap(b) : null); }

        public Builder setLargeIcon(Icon icon) {
            mN.mLargeIcon = icon;
            mN.extras.putParcelable(EXTRA_LARGE_ICON, icon);
            return this;
        }

        @Deprecated
        public Builder setSound(Uri sound) {
            mN.sound = sound;
            mN.audioAttributes = AUDIO_ATTRIBUTES_DEFAULT;
            return this;
        }

        @Deprecated
        public Builder setSound(Uri sound, int streamType) {
            mN.sound = sound;
            mN.audioStreamType = streamType;
            return this;
        }

        @Deprecated
        public Builder setSound(Uri sound, AudioAttributes audioAttributes) {
            mN.sound = sound;
            mN.audioAttributes = audioAttributes;
            return this;
        }

        @Deprecated
        public Builder setVibrate(long[] pattern) { mN.vibrate = pattern; return this; }

        @Deprecated
        public Builder setLights(int argb, int onMs, int offMs) {
            mN.ledARGB = argb;
            mN.ledOnMS = onMs;
            mN.ledOffMS = offMs;
            if (argb != 0 || onMs != 0 || offMs != 0) mN.flags |= FLAG_SHOW_LIGHTS;
            return this;
        }

        public Builder setOngoing(boolean ongoing) { return setFlag(FLAG_ONGOING_EVENT, ongoing); }

        public Builder setColorized(boolean colorize) { mN.extras.putBoolean(EXTRA_COLORIZED, colorize); return this; }

        public Builder setOnlyAlertOnce(boolean onlyAlertOnce) { return setFlag(FLAG_ONLY_ALERT_ONCE, onlyAlertOnce); }

        public Builder setForegroundServiceBehavior(int behavior) { mN.mFgsDeferBehavior = behavior; return this; }

        public Builder setAutoCancel(boolean autoCancel) { return setFlag(FLAG_AUTO_CANCEL, autoCancel); }

        public Builder setLocalOnly(boolean localOnly) { return setFlag(FLAG_LOCAL_ONLY, localOnly); }

        @Deprecated
        public Builder setDefaults(int defaults) { mN.defaults = defaults; return this; }

        @Deprecated
        public Builder setPriority(int pri) { mN.priority = pri; return this; }

        public Builder setCategory(String category) { mN.category = category; return this; }

        @Deprecated
        public Builder addPerson(String uri) { return addPerson(new Person.Builder().setUri(uri).build()); }

        public Builder addPerson(Person person) { mPersonList.add(person); return this; }

        public Builder setGroup(String groupKey) { mN.mGroupKey = groupKey; return this; }

        public Builder setGroupSummary(boolean isGroupSummary) { return setFlag(FLAG_GROUP_SUMMARY, isGroupSummary); }

        public Builder setSortKey(String sortKey) { mN.mSortKey = sortKey; return this; }

        public Builder addExtras(Bundle extras) {
            if (extras != null) mUserExtras.putAll(extras);
            return this;
        }

        public Builder setExtras(Bundle extras) {
            if (extras != null) mUserExtras = extras;
            return this;
        }

        public Bundle getExtras() { return mUserExtras; }

        @Deprecated
        public Builder addAction(int icon, CharSequence title, PendingIntent intent) {
            mActions.add(new Action(icon, safeCharSequence(title), intent));
            return this;
        }

        public Builder addAction(Action action) {
            if (action != null) mActions.add(action);
            return this;
        }

        public Builder setActions(Action... actions) {
            mActions.clear();
            if (actions != null) for (Action a : actions) if (a != null) mActions.add(a);
            return this;
        }

        public Builder setStyle(Style style) {
            if (mStyle != style) {
                mStyle = style;
                if (mStyle != null) {
                    mStyle.setBuilder(this);
                    mN.extras.putString(EXTRA_TEMPLATE, style.getClass().getName());
                } else {
                    mN.extras.remove(EXTRA_TEMPLATE);
                }
            }
            return this;
        }

        public Style getStyle() { return mStyle; }

        public Builder setVisibility(int visibility) { mN.visibility = visibility; return this; }

        public Builder setPublicVersion(Notification n) {
            mN.publicVersion = n != null ? n.clone() : null;
            return this;
        }

        public Builder extend(Extender extender) {
            extender.extend(this);
            return this;
        }

        public Builder setFlag(int mask, boolean value) {
            if (value) mN.flags |= mask;
            else mN.flags &= ~mask;
            return this;
        }

        public Builder setColor(int argb) { mN.color = argb; return this; }

        public static Builder recoverBuilder(Context context, Notification n) {
            return new Builder(context, n);
        }

        public Builder setAllowSystemGeneratedContextualActions(boolean allowed) {
            mN.mAllowSystemGeneratedContextualActions = allowed;
            return this;
        }

        @Deprecated
        public Notification getNotification() { return build(); }

        public Notification build() {
            if (mUserExtras != null) {
                Bundle all = new Bundle(mUserExtras);
                all.putAll(mN.extras);
                mN.extras = all;
            }
            if (!mPersonList.isEmpty()) {
                mN.extras.putParcelableArrayList(EXTRA_PEOPLE_LIST, new ArrayList<Person>(mPersonList));
            }
            if (!mActions.isEmpty()) mN.actions = mActions.toArray(new Action[0]);
            if (mStyle != null) {
                mStyle.validate(mContext);
                mStyle.buildStyled(mN);
            }
            if (mN.mLargeIcon != null && mN.mLargeIcon.getType() == Icon.TYPE_BITMAP) {
                mN.largeIcon = mN.mLargeIcon.getBitmap();
            }
            return mN;
        }
    }

    // ---------------------------------------------------------------- styles

    public static abstract class Style {
        private CharSequence mBigContentTitle;
        CharSequence mSummaryText;
        boolean mSummaryTextSet;
        protected Builder mBuilder;

        public Style() {}

        static Style forName(String templateClass) {
            if (templateClass == null || templateClass.isEmpty()) return null;
            if (BigTextStyle.class.getName().equals(templateClass)) return new BigTextStyle();
            if (BigPictureStyle.class.getName().equals(templateClass)) return new BigPictureStyle();
            if (InboxStyle.class.getName().equals(templateClass)) return new InboxStyle();
            if (MessagingStyle.class.getName().equals(templateClass)) return new MessagingStyle("");
            if (MediaStyle.class.getName().equals(templateClass)) return new MediaStyle();
            if (DecoratedCustomViewStyle.class.getName().equals(templateClass)) return new DecoratedCustomViewStyle();
            return null;
        }

        protected void internalSetBigContentTitle(CharSequence title) { mBigContentTitle = title; }

        protected void internalSetSummaryText(CharSequence cs) {
            mSummaryText = cs;
            mSummaryTextSet = true;
        }

        public void setBuilder(Builder builder) {
            if (mBuilder != builder) {
                mBuilder = builder;
                if (mBuilder != null) mBuilder.setStyle(this);
            }
        }

        protected void checkBuilder() {
            if (mBuilder == null) throw new IllegalArgumentException("Style requires a valid Builder object");
        }

        public Notification build() {
            checkBuilder();
            return mBuilder.build();
        }

        /** framework-internal. */
        void addExtras(Bundle extras) {
            if (mSummaryTextSet) extras.putCharSequence(EXTRA_SUMMARY_TEXT, mSummaryText);
            if (mBigContentTitle != null) extras.putCharSequence(EXTRA_TITLE_BIG, mBigContentTitle);
            extras.putString(EXTRA_TEMPLATE, getClass().getName());
        }

        /** framework-internal. */
        void restoreFromExtras(Bundle extras) {
            if (extras.containsKey(EXTRA_SUMMARY_TEXT)) {
                mSummaryText = extras.getCharSequence(EXTRA_SUMMARY_TEXT);
                mSummaryTextSet = true;
            }
            if (extras.containsKey(EXTRA_TITLE_BIG)) mBigContentTitle = extras.getCharSequence(EXTRA_TITLE_BIG);
        }

        void validate(Context context) {}

        Notification buildStyled(Notification wip) {
            addExtras(wip.extras);
            return wip;
        }
    }

    public static class BigTextStyle extends Style {
        private CharSequence mBigText;

        public BigTextStyle() {}

        @Deprecated
        public BigTextStyle(Builder builder) { setBuilder(builder); }

        public BigTextStyle setBigContentTitle(CharSequence title) {
            internalSetBigContentTitle(safeCharSequence(title));
            return this;
        }

        public BigTextStyle setSummaryText(CharSequence cs) {
            internalSetSummaryText(safeCharSequence(cs));
            return this;
        }

        public BigTextStyle bigText(CharSequence cs) {
            mBigText = safeCharSequence(cs);
            return this;
        }

        @Override
        void addExtras(Bundle extras) {
            super.addExtras(extras);
            extras.putCharSequence(EXTRA_BIG_TEXT, mBigText);
        }

        @Override
        void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            mBigText = extras.getCharSequence(EXTRA_BIG_TEXT);
        }
    }

    public static class BigPictureStyle extends Style {
        private Icon mPictureIcon;
        private Icon mBigLargeIcon;
        private boolean mBigLargeIconSet;
        private CharSequence mPictureContentDescription;
        private boolean mShowBigPictureWhenCollapsed;

        public BigPictureStyle() {}

        @Deprecated
        public BigPictureStyle(Builder builder) { setBuilder(builder); }

        public BigPictureStyle setBigContentTitle(CharSequence title) {
            internalSetBigContentTitle(safeCharSequence(title));
            return this;
        }

        public BigPictureStyle setSummaryText(CharSequence cs) {
            internalSetSummaryText(safeCharSequence(cs));
            return this;
        }

        public BigPictureStyle setContentDescription(CharSequence contentDescription) {
            mPictureContentDescription = contentDescription;
            return this;
        }

        public BigPictureStyle bigPicture(Bitmap b) {
            mPictureIcon = b != null ? Icon.createWithBitmap(b) : null;
            return this;
        }

        public BigPictureStyle bigPicture(Icon icon) {
            mPictureIcon = icon;
            return this;
        }

        public BigPictureStyle showBigPictureWhenCollapsed(boolean show) {
            mShowBigPictureWhenCollapsed = show;
            return this;
        }

        public BigPictureStyle bigLargeIcon(Bitmap b) { return bigLargeIcon(b != null ? Icon.createWithBitmap(b) : null); }

        public BigPictureStyle bigLargeIcon(Icon icon) {
            mBigLargeIconSet = true;
            mBigLargeIcon = icon;
            return this;
        }

        @Override
        void addExtras(Bundle extras) {
            super.addExtras(extras);
            if (mBigLargeIconSet) extras.putParcelable(EXTRA_LARGE_ICON_BIG, mBigLargeIcon);
            if (mPictureContentDescription != null) {
                extras.putCharSequence(EXTRA_PICTURE_CONTENT_DESCRIPTION, mPictureContentDescription);
            }
            extras.putBoolean(EXTRA_SHOW_BIG_PICTURE_WHEN_COLLAPSED, mShowBigPictureWhenCollapsed);
            if (mPictureIcon != null && mPictureIcon.getType() == Icon.TYPE_BITMAP) {
                extras.putParcelable(EXTRA_PICTURE, mPictureIcon.getBitmap());
                extras.putParcelable(EXTRA_PICTURE_ICON, null);
            } else {
                extras.putParcelable(EXTRA_PICTURE, null);
                extras.putParcelable(EXTRA_PICTURE_ICON, mPictureIcon);
            }
        }

        @Override
        void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            if (extras.containsKey(EXTRA_LARGE_ICON_BIG)) {
                mBigLargeIconSet = true;
                mBigLargeIcon = (Icon) extras.getParcelable(EXTRA_LARGE_ICON_BIG);
            }
            mPictureContentDescription = extras.getCharSequence(EXTRA_PICTURE_CONTENT_DESCRIPTION);
            mShowBigPictureWhenCollapsed = extras.getBoolean(EXTRA_SHOW_BIG_PICTURE_WHEN_COLLAPSED);
            Object picture = extras.getParcelable(EXTRA_PICTURE);
            if (picture instanceof Bitmap) mPictureIcon = Icon.createWithBitmap((Bitmap) picture);
            else mPictureIcon = (Icon) extras.getParcelable(EXTRA_PICTURE_ICON);
        }
    }

    public static class InboxStyle extends Style {
        private final ArrayList<CharSequence> mTexts = new ArrayList<CharSequence>(5);

        public InboxStyle() {}

        @Deprecated
        public InboxStyle(Builder builder) { setBuilder(builder); }

        public InboxStyle setBigContentTitle(CharSequence title) {
            internalSetBigContentTitle(safeCharSequence(title));
            return this;
        }

        public InboxStyle setSummaryText(CharSequence cs) {
            internalSetSummaryText(safeCharSequence(cs));
            return this;
        }

        public InboxStyle addLine(CharSequence cs) {
            mTexts.add(safeCharSequence(cs));
            return this;
        }

        @Override
        void addExtras(Bundle extras) {
            super.addExtras(extras);
            extras.putCharSequenceArray(EXTRA_TEXT_LINES, mTexts.toArray(new CharSequence[0]));
        }

        @Override
        void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            mTexts.clear();
            CharSequence[] lines = extras.getCharSequenceArray(EXTRA_TEXT_LINES);
            if (lines != null) mTexts.addAll(Arrays.asList(lines));
        }
    }

    public static class MessagingStyle extends Style {
        public static final int MAXIMUM_RETAINED_MESSAGES = 25;

        private Person mUser;
        private CharSequence mConversationTitle;
        private final List<Message> mMessages = new ArrayList<Message>();
        private final List<Message> mHistoricMessages = new ArrayList<Message>();
        private boolean mIsGroupConversation;

        @Deprecated
        public MessagingStyle(CharSequence userDisplayName) {
            this(new Person.Builder().setName(userDisplayName).build());
        }

        public MessagingStyle(Person user) { mUser = user; }

        public Person getUser() { return mUser; }

        public CharSequence getUserDisplayName() { return mUser.getName(); }

        public MessagingStyle setConversationTitle(CharSequence conversationTitle) {
            mConversationTitle = conversationTitle;
            return this;
        }

        public CharSequence getConversationTitle() { return mConversationTitle; }

        @Deprecated
        public MessagingStyle addMessage(CharSequence text, long timestamp, CharSequence sender) {
            return addMessage(text, timestamp, sender == null ? null : new Person.Builder().setName(sender).build());
        }

        public MessagingStyle addMessage(CharSequence text, long timestamp, Person sender) {
            return addMessage(new Message(text, timestamp, sender));
        }

        public MessagingStyle addMessage(Message message) {
            mMessages.add(message);
            if (mMessages.size() > MAXIMUM_RETAINED_MESSAGES) mMessages.remove(0);
            return this;
        }

        public MessagingStyle addHistoricMessage(Message message) {
            mHistoricMessages.add(message);
            if (mHistoricMessages.size() > MAXIMUM_RETAINED_MESSAGES) mHistoricMessages.remove(0);
            return this;
        }

        public List<Message> getMessages() { return mMessages; }

        public List<Message> getHistoricMessages() { return mHistoricMessages; }

        public MessagingStyle setGroupConversation(boolean isGroupConversation) {
            mIsGroupConversation = isGroupConversation;
            return this;
        }

        public boolean isGroupConversation() { return mIsGroupConversation; }

        @Override
        void validate(Context context) {
            if (mUser == null || mUser.getName() == null) {
                throw new RuntimeException("User must be valid and have a name.");
            }
        }

        @Override
        void addExtras(Bundle extras) {
            super.addExtras(extras);
            if (mUser != null) {
                extras.putCharSequence(EXTRA_SELF_DISPLAY_NAME, mUser.getName());
                extras.putParcelable(EXTRA_MESSAGING_PERSON, mUser);
            }
            if (mConversationTitle != null) extras.putCharSequence(EXTRA_CONVERSATION_TITLE, mConversationTitle);
            if (!mMessages.isEmpty()) extras.putParcelableArray(EXTRA_MESSAGES, Message.getBundleArrayForMessages(mMessages));
            if (!mHistoricMessages.isEmpty()) {
                extras.putParcelableArray(EXTRA_HISTORIC_MESSAGES, Message.getBundleArrayForMessages(mHistoricMessages));
            }
            extras.putBoolean(EXTRA_IS_GROUP_CONVERSATION, mIsGroupConversation);
        }

        @Override
        void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            Person user = (Person) extras.getParcelable(EXTRA_MESSAGING_PERSON);
            if (user == null) user = new Person.Builder().setName(extras.getCharSequence(EXTRA_SELF_DISPLAY_NAME)).build();
            mUser = user;
            mConversationTitle = extras.getCharSequence(EXTRA_CONVERSATION_TITLE);
            mMessages.clear();
            mMessages.addAll(Message.getMessagesFromBundleArray(extras.getParcelableArray(EXTRA_MESSAGES)));
            mHistoricMessages.clear();
            mHistoricMessages.addAll(Message.getMessagesFromBundleArray(extras.getParcelableArray(EXTRA_HISTORIC_MESSAGES)));
            mIsGroupConversation = extras.getBoolean(EXTRA_IS_GROUP_CONVERSATION);
        }

        public static final class Message {
            static final String KEY_TEXT = "text";
            static final String KEY_TIMESTAMP = "time";
            static final String KEY_SENDER = "sender";
            static final String KEY_SENDER_PERSON = "sender_person";
            static final String KEY_DATA_MIME_TYPE = "type";
            static final String KEY_DATA_URI = "uri";
            static final String KEY_EXTRAS_BUNDLE = "extras";

            private final CharSequence mText;
            private final long mTimestamp;
            private final Person mSender;
            private Bundle mExtras = new Bundle();
            private String mDataMimeType;
            private Uri mDataUri;

            @Deprecated
            public Message(CharSequence text, long timestamp, CharSequence sender) {
                this(text, timestamp, sender == null ? null : new Person.Builder().setName(sender).build());
            }

            public Message(CharSequence text, long timestamp, Person sender) {
                mText = safeCharSequence(text);
                mTimestamp = timestamp;
                mSender = sender;
            }

            public Message setData(String dataMimeType, Uri dataUri) {
                mDataMimeType = dataMimeType;
                mDataUri = dataUri;
                return this;
            }

            public CharSequence getText() { return mText; }

            public long getTimestamp() { return mTimestamp; }

            public Bundle getExtras() { return mExtras; }

            @Deprecated
            public CharSequence getSender() { return mSender == null ? null : mSender.getName(); }

            public Person getSenderPerson() { return mSender; }

            public String getDataMimeType() { return mDataMimeType; }

            public Uri getDataUri() { return mDataUri; }

            private Bundle toBundle() {
                Bundle bundle = new Bundle();
                if (mText != null) bundle.putCharSequence(KEY_TEXT, mText);
                bundle.putLong(KEY_TIMESTAMP, mTimestamp);
                if (mSender != null) {
                    bundle.putCharSequence(KEY_SENDER, safeCharSequence(mSender.getName()));
                    bundle.putParcelable(KEY_SENDER_PERSON, mSender);
                }
                if (mDataMimeType != null) bundle.putString(KEY_DATA_MIME_TYPE, mDataMimeType);
                if (mDataUri != null) bundle.putParcelable(KEY_DATA_URI, mDataUri);
                if (mExtras != null) bundle.putBundle(KEY_EXTRAS_BUNDLE, mExtras);
                return bundle;
            }

            static Bundle[] getBundleArrayForMessages(List<Message> messages) {
                Bundle[] bundles = new Bundle[messages.size()];
                for (int i = 0; i < bundles.length; i++) bundles[i] = messages.get(i).toBundle();
                return bundles;
            }

            public static List<Message> getMessagesFromBundleArray(Parcelable[] bundles) {
                ArrayList<Message> messages = new ArrayList<Message>();
                if (bundles == null) return messages;
                for (Parcelable p : bundles) {
                    if (!(p instanceof Bundle)) continue;
                    Message message = getMessageFromBundle((Bundle) p);
                    if (message != null) messages.add(message);
                }
                return messages;
            }

            static Message getMessageFromBundle(Bundle bundle) {
                if (!bundle.containsKey(KEY_TEXT) || !bundle.containsKey(KEY_TIMESTAMP)) return null;
                Person sender = (Person) bundle.getParcelable(KEY_SENDER_PERSON);
                if (sender == null) {
                    CharSequence senderName = bundle.getCharSequence(KEY_SENDER);
                    if (senderName != null) sender = new Person.Builder().setName(senderName).build();
                }
                Message message = new Message(bundle.getCharSequence(KEY_TEXT), bundle.getLong(KEY_TIMESTAMP), sender);
                if (bundle.containsKey(KEY_DATA_MIME_TYPE) && bundle.containsKey(KEY_DATA_URI)) {
                    message.setData(bundle.getString(KEY_DATA_MIME_TYPE), (Uri) bundle.getParcelable(KEY_DATA_URI));
                }
                if (bundle.containsKey(KEY_EXTRAS_BUNDLE)) message.mExtras.putAll(bundle.getBundle(KEY_EXTRAS_BUNDLE));
                return message;
            }
        }
    }

    /** TODO(WS4) setMediaSession needs android.media.session. */
    public static class MediaStyle extends Style {
        private int[] mActionsToShowInCompact;

        public MediaStyle() {}

        @Deprecated
        public MediaStyle(Builder builder) { setBuilder(builder); }

        public MediaStyle setShowActionsInCompactView(int... actions) {
            mActionsToShowInCompact = actions;
            return this;
        }

        @Override
        void addExtras(Bundle extras) {
            super.addExtras(extras);
            if (mActionsToShowInCompact != null) extras.putIntArray(EXTRA_COMPACT_ACTIONS, mActionsToShowInCompact);
        }

        @Override
        void restoreFromExtras(Bundle extras) {
            super.restoreFromExtras(extras);
            mActionsToShowInCompact = extras.getIntArray(EXTRA_COMPACT_ACTIONS);
        }
    }

    public static class DecoratedCustomViewStyle extends Style {
        public DecoratedCustomViewStyle() {}
    }
}
