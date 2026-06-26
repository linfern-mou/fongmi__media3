package is.xyz.mpv;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Surface;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for libmpv native library. Forked from mpv-android.
 * See: https://github.com/FongMi/mpv-android
 */
@SuppressWarnings("unused")
public final class MPVLib {

    static {
        try {
            System.loadLibrary("mpv");
            System.loadLibrary("player");
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    public static native void create(Context appctx);
    public static native void init();
    public static native void destroy();
    public static native void attachSurface(Surface surface);
    public static native void replaceSurface(Surface surface);
    public static native void detachSurface();

    public static native void command(String[] cmd);

    public static native int setOptionString(String name, String value);

    public static native Bitmap grabThumbnail(int dimension);

    public static native Integer getPropertyInt(String property);
    public static native void setPropertyInt(String property, int value);
    public static native Double getPropertyDouble(String property);
    public static native void setPropertyDouble(String property, double value);
    public static native Boolean getPropertyBoolean(String property);
    public static native void setPropertyBoolean(String property, boolean value);
    public static native String getPropertyString(String property);
    public static native void setPropertyString(String property, String value);
    public static native byte[] getPropertyByteArray(String property);

    public static native void observeProperty(String property, int format);

    private static final List<EventObserver> observers = new ArrayList<>();

    public static void addObserver(EventObserver o) {
        synchronized (observers) {
            observers.add(o);
        }
    }

    public static void removeObserver(EventObserver o) {
        synchronized (observers) {
            observers.remove(o);
        }
    }

    public static void eventProperty(String property, long value) {
        synchronized (observers) {
            for (EventObserver o : observers) {
                o.eventProperty(property, value);
            }
        }
    }

    public static void eventProperty(String property, boolean value) {
        synchronized (observers) {
            for (EventObserver o : observers) {
                o.eventProperty(property, value);
            }
        }
    }

    public static void eventProperty(String property, double value) {
        synchronized (observers) {
            for (EventObserver o : observers) {
                o.eventProperty(property, value);
            }
        }
    }

    public static void eventProperty(String property, String value) {
        synchronized (observers) {
            for (EventObserver o : observers) {
                o.eventProperty(property, value);
            }
        }
    }

    public static void eventProperty(String property) {
        synchronized (observers) {
            for (EventObserver o : observers) {
                o.eventProperty(property);
            }
        }
    }

    public static void event(int eventId) {
        synchronized (observers) {
            for (EventObserver o : observers) {
                o.event(eventId);
            }
        }
    }

    public static void eventEndFile(int reason, int error, String errorString) {
        synchronized (observers) {
            for (EventObserver o : observers) {
                o.eventEndFile(reason, error, errorString);
            }
        }
    }

    private static final List<LogObserver> logObservers = new ArrayList<>();

    public static void addLogObserver(LogObserver o) {
        synchronized (logObservers) {
            logObservers.add(o);
        }
    }

    public static void removeLogObserver(LogObserver o) {
        synchronized (logObservers) {
            logObservers.remove(o);
        }
    }

    public static void logMessage(String prefix, int level, String text) {
        synchronized (logObservers) {
            for (LogObserver o : logObservers) {
                o.logMessage(prefix, level, text);
            }
        }
    }

    public interface EventObserver {
        void eventProperty(String property);
        void eventProperty(String property, long value);
        void eventProperty(String property, boolean value);
        void eventProperty(String property, String value);
        void eventProperty(String property, double value);
        void event(int eventId);
        default void eventEndFile(int reason, int error, String errorString) {
            event(MpvEvent.MPV_EVENT_END_FILE);
        }
    }

    public interface LogObserver {
        void logMessage(String prefix, int level, String text);
    }

    public static final class MpvFormat {
        public static final int MPV_FORMAT_NONE = 0;
        public static final int MPV_FORMAT_STRING = 1;
        public static final int MPV_FORMAT_OSD_STRING = 2;
        public static final int MPV_FORMAT_FLAG = 3;
        public static final int MPV_FORMAT_INT64 = 4;
        public static final int MPV_FORMAT_DOUBLE = 5;
        public static final int MPV_FORMAT_NODE = 6;
        public static final int MPV_FORMAT_NODE_ARRAY = 7;
        public static final int MPV_FORMAT_NODE_MAP = 8;
        public static final int MPV_FORMAT_BYTE_ARRAY = 9;
    }

    public static final class MpvEvent {
        public static final int MPV_EVENT_NONE = 0;
        public static final int MPV_EVENT_SHUTDOWN = 1;
        public static final int MPV_EVENT_LOG_MESSAGE = 2;
        public static final int MPV_EVENT_GET_PROPERTY_REPLY = 3;
        public static final int MPV_EVENT_SET_PROPERTY_REPLY = 4;
        public static final int MPV_EVENT_COMMAND_REPLY = 5;
        public static final int MPV_EVENT_START_FILE = 6;
        public static final int MPV_EVENT_END_FILE = 7;
        public static final int MPV_EVENT_FILE_LOADED = 8;
        @Deprecated public static final int MPV_EVENT_IDLE = 11;
        @Deprecated public static final int MPV_EVENT_TICK = 14;
        public static final int MPV_EVENT_CLIENT_MESSAGE = 16;
        public static final int MPV_EVENT_VIDEO_RECONFIG = 17;
        public static final int MPV_EVENT_AUDIO_RECONFIG = 18;
        public static final int MPV_EVENT_SEEK = 20;
        public static final int MPV_EVENT_PLAYBACK_RESTART = 21;
        public static final int MPV_EVENT_PROPERTY_CHANGE = 22;
        public static final int MPV_EVENT_QUEUE_OVERFLOW = 24;
        public static final int MPV_EVENT_HOOK = 25;
    }

    public static final class MpvEndFileReason {
        public static final int MPV_END_FILE_REASON_EOF = 0;
        public static final int MPV_END_FILE_REASON_STOP = 2;
        public static final int MPV_END_FILE_REASON_QUIT = 3;
        public static final int MPV_END_FILE_REASON_ERROR = 4;
        public static final int MPV_END_FILE_REASON_REDIRECT = 5;
    }

    public static final class MpvError {
        public static final int MPV_ERROR_SUCCESS = 0;
        public static final int MPV_ERROR_EVENT_QUEUE_FULL = -1;
        public static final int MPV_ERROR_NOMEM = -2;
        public static final int MPV_ERROR_UNINITIALIZED = -3;
        public static final int MPV_ERROR_INVALID_PARAMETER = -4;
        public static final int MPV_ERROR_OPTION_NOT_FOUND = -5;
        public static final int MPV_ERROR_OPTION_FORMAT = -6;
        public static final int MPV_ERROR_OPTION_ERROR = -7;
        public static final int MPV_ERROR_PROPERTY_NOT_FOUND = -8;
        public static final int MPV_ERROR_PROPERTY_FORMAT = -9;
        public static final int MPV_ERROR_PROPERTY_UNAVAILABLE = -10;
        public static final int MPV_ERROR_PROPERTY_ERROR = -11;
        public static final int MPV_ERROR_COMMAND = -12;
        public static final int MPV_ERROR_LOADING_FAILED = -13;
        public static final int MPV_ERROR_AO_INIT_FAILED = -14;
        public static final int MPV_ERROR_VO_INIT_FAILED = -15;
        public static final int MPV_ERROR_NOTHING_TO_PLAY = -16;
        public static final int MPV_ERROR_UNKNOWN_FORMAT = -17;
        public static final int MPV_ERROR_UNSUPPORTED = -18;
        public static final int MPV_ERROR_NOT_IMPLEMENTED = -19;
        public static final int MPV_ERROR_GENERIC = -20;
    }

    public static final class MpvLogLevel {
        public static final int MPV_LOG_LEVEL_NONE = 0;
        public static final int MPV_LOG_LEVEL_FATAL = 10;
        public static final int MPV_LOG_LEVEL_ERROR = 20;
        public static final int MPV_LOG_LEVEL_WARN = 30;
        public static final int MPV_LOG_LEVEL_INFO = 40;
        public static final int MPV_LOG_LEVEL_V = 50;
        public static final int MPV_LOG_LEVEL_DEBUG = 60;
        public static final int MPV_LOG_LEVEL_TRACE = 70;
    }
}
