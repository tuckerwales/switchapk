package android.provider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/** Constants and the calls apps actually make. Values match android.jar. */
public final class MediaStore {
    public static final String ACCESS_MEDIA_OWNER_PACKAGE_NAME_PERMISSION = "com.android.providers.media.permission.ACCESS_MEDIA_OWNER_PACKAGE_NAME";
    public static final String ACTION_IMAGE_CAPTURE = "android.media.action.IMAGE_CAPTURE";
    public static final String ACTION_IMAGE_CAPTURE_SECURE = "android.media.action.IMAGE_CAPTURE_SECURE";
    public static final String ACTION_PICK_IMAGES = "android.provider.action.PICK_IMAGES";
    public static final String ACTION_PICK_IMAGES_SETTINGS = "android.provider.action.PICK_IMAGES_SETTINGS";
    public static final String ACTION_REVIEW = "android.provider.action.REVIEW";
    public static final String ACTION_REVIEW_SECURE = "android.provider.action.REVIEW_SECURE";
    public static final String ACTION_VIDEO_CAPTURE = "android.media.action.VIDEO_CAPTURE";
    public static final String AUTHORITY = "media";
    public static final Uri AUTHORITY_URI = Uri.parse("content://media");
    public static final String EXTRA_ACCEPT_ORIGINAL_MEDIA_FORMAT = "android.provider.extra.ACCEPT_ORIGINAL_MEDIA_FORMAT";
    public static final String EXTRA_BRIGHTNESS = "android.provider.extra.BRIGHTNESS";
    public static final String EXTRA_DURATION_LIMIT = "android.intent.extra.durationLimit";
    public static final String EXTRA_FINISH_ON_COMPLETION = "android.intent.extra.finishOnCompletion";
    public static final String EXTRA_FULL_SCREEN = "android.intent.extra.fullScreen";
    public static final String EXTRA_MEDIA_ALBUM = "android.intent.extra.album";
    public static final String EXTRA_MEDIA_ARTIST = "android.intent.extra.artist";
    public static final String EXTRA_MEDIA_CAPABILITIES = "android.provider.extra.MEDIA_CAPABILITIES";
    public static final String EXTRA_MEDIA_CAPABILITIES_UID = "android.provider.extra.MEDIA_CAPABILITIES_UID";
    public static final String EXTRA_MEDIA_FOCUS = "android.intent.extra.focus";
    public static final String EXTRA_MEDIA_GENRE = "android.intent.extra.genre";
    public static final String EXTRA_MEDIA_PLAYLIST = "android.intent.extra.playlist";
    public static final String EXTRA_MEDIA_RADIO_CHANNEL = "android.intent.extra.radio_channel";
    public static final String EXTRA_MEDIA_TITLE = "android.intent.extra.title";
    public static final String EXTRA_OUTPUT = "output";
    public static final String EXTRA_PICK_IMAGES_ACCENT_COLOR = "android.provider.extra.PICK_IMAGES_ACCENT_COLOR";
    public static final String EXTRA_PICK_IMAGES_IN_ORDER = "android.provider.extra.PICK_IMAGES_IN_ORDER";
    public static final String EXTRA_PICK_IMAGES_LAUNCH_TAB = "android.provider.extra.PICK_IMAGES_LAUNCH_TAB";
    public static final String EXTRA_PICK_IMAGES_MAX = "android.provider.extra.PICK_IMAGES_MAX";
    public static final String EXTRA_SCREEN_ORIENTATION = "android.intent.extra.screenOrientation";
    public static final String EXTRA_SHOW_ACTION_ICONS = "android.intent.extra.showActionIcons";
    public static final String EXTRA_SIZE_LIMIT = "android.intent.extra.sizeLimit";
    public static final String EXTRA_VIDEO_QUALITY = "android.intent.extra.videoQuality";
    public static final String INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    public static final String INTENT_ACTION_MEDIA_SEARCH = "android.intent.action.MEDIA_SEARCH";
    public static final String INTENT_ACTION_MUSIC_PLAYER = "android.intent.action.MUSIC_PLAYER";
    public static final String INTENT_ACTION_STILL_IMAGE_CAMERA = "android.media.action.STILL_IMAGE_CAMERA";
    public static final String INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE = "android.media.action.STILL_IMAGE_CAMERA_SECURE";
    public static final String INTENT_ACTION_TEXT_OPEN_FROM_SEARCH = "android.media.action.TEXT_OPEN_FROM_SEARCH";
    public static final String INTENT_ACTION_VIDEO_CAMERA = "android.media.action.VIDEO_CAMERA";
    public static final String INTENT_ACTION_VIDEO_PLAY_FROM_SEARCH = "android.media.action.VIDEO_PLAY_FROM_SEARCH";
    public static final int MATCH_DEFAULT = 0;
    public static final int MATCH_EXCLUDE = 2;
    public static final int MATCH_INCLUDE = 1;
    public static final int MATCH_ONLY = 3;
    public static final String MEDIA_IGNORE_FILENAME = ".nomedia";
    public static final String MEDIA_SCANNER_VOLUME = "volume";
    public static final String META_DATA_REVIEW_GALLERY_PREWARM_SERVICE = "android.media.review_gallery_prewarm_service";
    public static final String META_DATA_STILL_IMAGE_CAMERA_PREWARM_SERVICE = "android.media.still_image_camera_preview_service";
    public static final int PICK_IMAGES_TAB_ALBUMS = 0;
    public static final int PICK_IMAGES_TAB_IMAGES = 1;
    public static final String QUERY_ARG_INCLUDE_RECENTLY_UNMOUNTED_VOLUMES = "android:query-arg-recently-unmounted-volumes";
    public static final String QUERY_ARG_LATEST_SELECTION_ONLY = "android:query-arg-latest-selection-only";
    public static final String QUERY_ARG_MATCH_FAVORITE = "android:query-arg-match-favorite";
    public static final String QUERY_ARG_MATCH_PENDING = "android:query-arg-match-pending";
    public static final String QUERY_ARG_MATCH_TRASHED = "android:query-arg-match-trashed";
    public static final String QUERY_ARG_RELATED_URI = "android:query-arg-related-uri";
    public static final String UNKNOWN_STRING = "<unknown>";
    public static final String VOLUME_EXTERNAL = "external";
    public static final String VOLUME_EXTERNAL_PRIMARY = "external_primary";
    public static final String VOLUME_INTERNAL = "internal";
    public MediaStore() {}
    public static final class Audio {
        public Audio() {}
        public interface AlbumColumns {
            public static final String ALBUM = "album";
            public static final String ALBUM_ART = "album_art";
            public static final String ALBUM_ID = "album_id";
            public static final String ALBUM_KEY = "album_key";
            public static final String ARTIST = "artist";
            public static final String ARTIST_ID = "artist_id";
            public static final String ARTIST_KEY = "artist_key";
            public static final String FIRST_YEAR = "minyear";
            public static final String LAST_YEAR = "maxyear";
            public static final String NUMBER_OF_SONGS = "numsongs";
            public static final String NUMBER_OF_SONGS_FOR_ARTIST = "numsongs_by_artist";
        }
        public static final class Albums implements BaseColumns, AlbumColumns {
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/albums";
            public static final String DEFAULT_SORT_ORDER = "album_key";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/album";
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/albums");
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/audio/albums");
            public Albums() {}
        }
        public interface ArtistColumns {
            public static final String ARTIST = "artist";
            public static final String ARTIST_KEY = "artist_key";
            public static final String NUMBER_OF_ALBUMS = "number_of_albums";
            public static final String NUMBER_OF_TRACKS = "number_of_tracks";
        }
        public static final class Artists implements BaseColumns, ArtistColumns {
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/artists";
            public static final String DEFAULT_SORT_ORDER = "artist_key";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/artist";
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/artists");
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/audio/artists");
            public Artists() {}
            public static final class Albums implements BaseColumns, AlbumColumns {
                public Albums() {}
            }
        }
        public interface AudioColumns extends MediaColumns {
            public static final String ALBUM_ID = "album_id";
            public static final String ALBUM_KEY = "album_key";
            public static final String ARTIST_ID = "artist_id";
            public static final String ARTIST_KEY = "artist_key";
            public static final String BOOKMARK = "bookmark";
            public static final String GENRE = "genre";
            public static final String GENRE_ID = "genre_id";
            public static final String GENRE_KEY = "genre_key";
            public static final String IS_ALARM = "is_alarm";
            public static final String IS_AUDIOBOOK = "is_audiobook";
            public static final String IS_MUSIC = "is_music";
            public static final String IS_NOTIFICATION = "is_notification";
            public static final String IS_PODCAST = "is_podcast";
            public static final String IS_RECORDING = "is_recording";
            public static final String IS_RINGTONE = "is_ringtone";
            public static final String TITLE_KEY = "title_key";
            public static final String TITLE_RESOURCE_URI = "title_resource_uri";
            public static final String TRACK = "track";
            public static final String YEAR = "year";
        }
        public static final class Genres implements BaseColumns, GenresColumns {
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/genre";
            public static final String DEFAULT_SORT_ORDER = "name";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/genre";
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/genres");
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/audio/genres");
            public Genres() {}
            public static final class Members implements AudioColumns {
                public static final String AUDIO_ID = "audio_id";
                public static final String CONTENT_DIRECTORY = "members";
                public static final String DEFAULT_SORT_ORDER = "title_key";
                public static final String GENRE_ID = "genre_id";
                public Members() {}
            }
        }
        public interface GenresColumns {
            public static final String NAME = "name";
        }
        public static final class Media implements AudioColumns {
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/audio";
            public static final String DEFAULT_SORT_ORDER = "title_key";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/audio";
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/media");
            public static final String EXTRA_MAX_BYTES = "android.provider.MediaStore.extra.MAX_BYTES";
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/audio/media");
            public static final String RECORD_SOUND_ACTION = "android.provider.MediaStore.RECORD_SOUND";
            public Media() {}

        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://" + MediaStore.AUTHORITY + "/" + volumeName + "/audio/media");
        }

        public static Uri getContentUri(String volumeName, long id) {
            return Uri.withAppendedPath(getContentUri(volumeName), String.valueOf(id));
        }

        public static Uri getContentUriForPath(String path) {
            String volume = (path != null && path.startsWith("/data/")) ? "internal" : "external";
            return getContentUri(volume);
        }
        }
        public static final class Playlists implements BaseColumns, PlaylistsColumns {
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/playlist";
            public static final String DEFAULT_SORT_ORDER = "name";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/playlist";
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/playlists");
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/audio/playlists");
            public Playlists() {}
            public static final class Members implements AudioColumns {
                public static final String AUDIO_ID = "audio_id";
                public static final String CONTENT_DIRECTORY = "members";
                public static final String DEFAULT_SORT_ORDER = "play_order";
                public static final String PLAYLIST_ID = "playlist_id";
                public static final String PLAY_ORDER = "play_order";
                public static final String _ID = "_id";
                public Members() {}
            }
        }
        public interface PlaylistsColumns extends MediaColumns {
            public static final String DATA = "_data";
            public static final String DATE_ADDED = "date_added";
            public static final String DATE_MODIFIED = "date_modified";
            public static final String NAME = "name";
        }
        public static final class Radio {
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/radio";
            public Radio() {}
        }
    }
    public interface DownloadColumns extends MediaColumns {
        public static final String DOWNLOAD_URI = "download_uri";
        public static final String REFERER_URI = "referer_uri";
    }
    public static final class Downloads implements DownloadColumns {
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/download";
        public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/downloads");
        public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/downloads");
        private Downloads() {}

        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://" + MediaStore.AUTHORITY + "/" + volumeName + "/downloads");
        }

        public static Uri getContentUri(String volumeName, long id) {
            return Uri.withAppendedPath(getContentUri(volumeName), String.valueOf(id));
        }
    }
    public static final class Files {
        public Files() {}

        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://" + MediaStore.AUTHORITY + "/" + volumeName + "/file");
        }

        public static Uri getContentUri(String volumeName, long id) {
            return Uri.withAppendedPath(getContentUri(volumeName), String.valueOf(id));
        }
        public interface FileColumns extends MediaColumns {
            public static final String MEDIA_TYPE = "media_type";
            public static final int MEDIA_TYPE_AUDIO = 2;
            public static final int MEDIA_TYPE_DOCUMENT = 6;
            public static final int MEDIA_TYPE_IMAGE = 1;
            public static final int MEDIA_TYPE_NONE = 0;
            public static final int MEDIA_TYPE_PLAYLIST = 4;
            public static final int MEDIA_TYPE_SUBTITLE = 5;
            public static final int MEDIA_TYPE_VIDEO = 3;
            public static final String MIME_TYPE = "mime_type";
            public static final String PARENT = "parent";
        }
    }
    public static final class Images {
        public Images() {}
        public interface ImageColumns extends MediaColumns {
            public static final String DESCRIPTION = "description";
            public static final String EXPOSURE_TIME = "exposure_time";
            public static final String F_NUMBER = "f_number";
            public static final String ISO = "iso";
            public static final String IS_PRIVATE = "isprivate";
            public static final String LATITUDE = "latitude";
            public static final String LONGITUDE = "longitude";
            public static final String MINI_THUMB_MAGIC = "mini_thumb_magic";
            public static final String PICASA_ID = "picasa_id";
            public static final String SCENE_CAPTURE_TYPE = "scene_capture_type";
        }
        public static final class Media implements ImageColumns {
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/image";
            public static final String DEFAULT_SORT_ORDER = "bucket_display_name";
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/images/media");
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/images/media");
            public Media() {}

        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://" + MediaStore.AUTHORITY + "/" + volumeName + "/images/media");
        }

        public static Uri getContentUri(String volumeName, long id) {
            return Uri.withAppendedPath(getContentUri(volumeName), String.valueOf(id));
        }

        public static Cursor query(ContentResolver cr, Uri uri, String[] projection) {
            return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
        }

        public static Cursor query(ContentResolver cr, Uri uri, String[] projection, String where, String orderBy) {
            return cr.query(uri, projection, where, null, orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
        }

        public static Cursor query(ContentResolver cr, Uri uri, String[] projection, String selection,
                String[] selectionArgs, String orderBy) {
            return cr.query(uri, projection, selection, selectionArgs, orderBy);
        }

        public static String insertImage(ContentResolver cr, String imagePath, String name, String description)
                throws java.io.FileNotFoundException {
            if (imagePath == null) throw new java.io.FileNotFoundException("null path");
            ContentValues values = new ContentValues();
            values.put(MediaColumns.DATA, imagePath);
            values.put(MediaColumns.DISPLAY_NAME, name);
            values.put(MediaColumns.MIME_TYPE, "image/jpeg");
            if (description != null) values.put(ImageColumns.DESCRIPTION, description);
            Uri uri = cr.insert(EXTERNAL_CONTENT_URI, values);
            return uri == null ? null : uri.toString();
        }

        public static String insertImage(ContentResolver cr, android.graphics.Bitmap source, String title,
                String description) {
            if (source == null) return null;
            try {
                java.io.File dir = new java.io.File("/data/local/tmp/media");
                if (!dir.exists()) dir.mkdirs();
                java.io.File out = new java.io.File(dir, "img-" + System.currentTimeMillis() + ".jpg");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                try {
                    source.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
                } finally {
                    fos.close();
                }
                return insertImage(cr, out.getPath(), title, description);
            } catch (java.io.IOException e) {
                android.util.Log.w("MediaStore", "insertImage failed");
                return null;
            }
        }

        public static android.graphics.Bitmap getBitmap(ContentResolver cr, Uri url)
                throws java.io.FileNotFoundException, java.io.IOException {
            java.io.InputStream in = cr.openInputStream(url);
            if (in == null) throw new java.io.FileNotFoundException(String.valueOf(url));
            try {
                return android.graphics.BitmapFactory.decodeStream(in);
            } finally {
                in.close();
            }
        }
        }
        public static class Thumbnails implements BaseColumns {
            public static final String DATA = "_data";
            public static final String DEFAULT_SORT_ORDER = "image_id ASC";
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/images/thumbnails");
            public static final int FULL_SCREEN_KIND = 2;
            public static final String HEIGHT = "height";
            public static final String IMAGE_ID = "image_id";
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/images/thumbnails");
            public static final String KIND = "kind";
            public static final int MICRO_KIND = 3;
            public static final int MINI_KIND = 1;
            public static final String THUMB_DATA = "thumb_data";
            public static final String WIDTH = "width";
            public Thumbnails() {}

        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://" + MediaStore.AUTHORITY + "/" + volumeName + "/images/thumbnails");
        }

        public static void cancelThumbnailRequest(ContentResolver cr, long origId) {}

        public static void cancelThumbnailRequest(ContentResolver cr, long origId, long groupId) {}

        public static android.graphics.Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
                android.graphics.BitmapFactory.Options options) {
            return null;
        }

        public static android.graphics.Bitmap getThumbnail(ContentResolver cr, long origId, long groupId, int kind,
                android.graphics.BitmapFactory.Options options) {
            return null;
        }

        public static android.util.Size getKindSize(int kind) { return null; }

        public static Cursor query(ContentResolver cr, Uri uri, String[] projection) {
            return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
        }

        public static Cursor queryMiniThumbnails(ContentResolver cr, Uri uri, int kind, String[] projection) {
            return cr.query(uri, projection, "kind = " + kind, null, DEFAULT_SORT_ORDER);
        }

        public static Cursor queryMiniThumbnail(ContentResolver cr, long origId, int kind, String[] projection) {
            return cr.query(EXTERNAL_CONTENT_URI, projection,
                    IMAGE_ID + " = " + origId + " AND " + KIND + " = " + kind, null, null);
        }
        }
    }
    public interface MediaColumns extends BaseColumns {
        public static final String ALBUM = "album";
        public static final String ALBUM_ARTIST = "album_artist";
        public static final String ARTIST = "artist";
        public static final String AUTHOR = "author";
        public static final String BITRATE = "bitrate";
        public static final String BUCKET_DISPLAY_NAME = "bucket_display_name";
        public static final String BUCKET_ID = "bucket_id";
        public static final String CAPTURE_FRAMERATE = "capture_framerate";
        public static final String CD_TRACK_NUMBER = "cd_track_number";
        public static final String COMPILATION = "compilation";
        public static final String COMPOSER = "composer";
        public static final String DATA = "_data";
        public static final String DATE_ADDED = "date_added";
        public static final String DATE_EXPIRES = "date_expires";
        public static final String DATE_MODIFIED = "date_modified";
        public static final String DATE_TAKEN = "datetaken";
        public static final String DISC_NUMBER = "disc_number";
        public static final String DISPLAY_NAME = "_display_name";
        public static final String DOCUMENT_ID = "document_id";
        public static final String DURATION = "duration";
        public static final String GENERATION_ADDED = "generation_added";
        public static final String GENERATION_MODIFIED = "generation_modified";
        public static final String GENRE = "genre";
        public static final String HEIGHT = "height";
        public static final String INSTANCE_ID = "instance_id";
        public static final String IS_DOWNLOAD = "is_download";
        public static final String IS_DRM = "is_drm";
        public static final String IS_FAVORITE = "is_favorite";
        public static final String IS_PENDING = "is_pending";
        public static final String IS_TRASHED = "is_trashed";
        public static final String MIME_TYPE = "mime_type";
        public static final String NUM_TRACKS = "num_tracks";
        public static final String ORIENTATION = "orientation";
        public static final String ORIGINAL_DOCUMENT_ID = "original_document_id";
        public static final String OWNER_PACKAGE_NAME = "owner_package_name";
        public static final String RELATIVE_PATH = "relative_path";
        public static final String RESOLUTION = "resolution";
        public static final String SIZE = "_size";
        public static final String TITLE = "title";
        public static final String VOLUME_NAME = "volume_name";
        public static final String WIDTH = "width";
        public static final String WRITER = "writer";
        public static final String XMP = "xmp";
        public static final String YEAR = "year";
    }
    public static class PickerMediaColumns {
        public static final String DATA = "_data";
        public static final String DATE_TAKEN = "datetaken";
        public static final String DISPLAY_NAME = "_display_name";
        public static final String DURATION_MILLIS = "duration";
        public static final String HEIGHT = "height";
        public static final String MIME_TYPE = "mime_type";
        public static final String ORIENTATION = "orientation";
        public static final String SIZE = "_size";
        public static final String WIDTH = "width";
        public PickerMediaColumns() {}
    }
    public static final class Video {
        public static final String DEFAULT_SORT_ORDER = "_display_name";
        public Video() {}
        public static final class Media implements VideoColumns {
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/video";
            public static final String DEFAULT_SORT_ORDER = "title";
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/video/media");
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/video/media");
            public Media() {}

        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://" + MediaStore.AUTHORITY + "/" + volumeName + "/video/media");
        }

        public static Uri getContentUri(String volumeName, long id) {
            return Uri.withAppendedPath(getContentUri(volumeName), String.valueOf(id));
        }
        }
        public static class Thumbnails implements BaseColumns {
            public static final String DATA = "_data";
            public static final String DEFAULT_SORT_ORDER = "video_id ASC";
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/video/thumbnails");
            public static final int FULL_SCREEN_KIND = 2;
            public static final String HEIGHT = "height";
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/video/thumbnails");
            public static final String KIND = "kind";
            public static final int MICRO_KIND = 3;
            public static final int MINI_KIND = 1;
            public static final String VIDEO_ID = "video_id";
            public static final String WIDTH = "width";
            public Thumbnails() {}

        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://" + MediaStore.AUTHORITY + "/" + volumeName + "/video/thumbnails");
        }

        public static void cancelThumbnailRequest(ContentResolver cr, long origId) {}

        public static void cancelThumbnailRequest(ContentResolver cr, long origId, long groupId) {}

        public static android.graphics.Bitmap getThumbnail(ContentResolver cr, long origId, int kind,
                android.graphics.BitmapFactory.Options options) {
            return null;
        }

        public static android.graphics.Bitmap getThumbnail(ContentResolver cr, long origId, long groupId, int kind,
                android.graphics.BitmapFactory.Options options) {
            return null;
        }

        public static android.util.Size getKindSize(int kind) { return null; }
        }
        public interface VideoColumns extends MediaColumns {
            public static final String BOOKMARK = "bookmark";
            public static final String CATEGORY = "category";
            public static final String COLOR_RANGE = "color_range";
            public static final String COLOR_STANDARD = "color_standard";
            public static final String COLOR_TRANSFER = "color_transfer";
            public static final String DESCRIPTION = "description";
            public static final String IS_PRIVATE = "isprivate";
            public static final String LANGUAGE = "language";
            public static final String LATITUDE = "latitude";
            public static final String LONGITUDE = "longitude";
            public static final String MINI_THUMB_MAGIC = "mini_thumb_magic";
            public static final String TAGS = "tags";
        }
    }
}
