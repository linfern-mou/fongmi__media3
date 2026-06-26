package androidx.media3.mpvplayer;

import static androidx.media3.common.Player.COMMAND_SEEK_TO_DEFAULT_POSITION;

import android.content.Context;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.BasePlayer;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ListenerSet;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import is.xyz.mpv.MPVLib;

@UnstableApi
public final class MpvPlayer extends BasePlayer {

    private static final String PROP_PAUSE = "pause";
    private static final String PROP_TIME_POS = "time-pos";
    private static final String PROP_DURATION = "duration";

    public static boolean isAvailable() {
        try {
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private final Context context;
    private final MpvPlayerConfig config;
    private final ListenerSet<Listener> listeners;
    private final CopyOnWriteArrayList<MediaItem> mediaItems;
    private final Timeline.Period period;

    private int decode;
    private int currentMediaItemIndex;
    private boolean playWhenReady;
    @Nullable private PlaybackException playerError;
    private @State int playbackState;
    private Timeline timeline;
    private PlaybackParameters playbackParameters;
    private @RepeatMode int repeatMode;
    private boolean shuffleModeEnabled;
    @Nullable private MediaMetadata playlistMetadata;
    private VideoSize videoSize;
    @Nullable private Surface surface;
    private float volume;
    private boolean deviceMuted;
    private int deviceVolume;
    @Nullable private AudioAttributes audioAttributes;
    private TrackSelectionParameters trackSelectionParameters;
    private Tracks currentTracks;
    private CueGroup currentCueGroup;
    private Commands availableCommands;
    private long contentPositionMs;
    private long totalBufferedDurationMs;
    private long bufferedPositionMs;

    private MpvPlayer(Builder builder) {
        this.context = builder.context;
        this.config = builder.config;
        this.decode = builder.decode;
        this.listeners = new ListenerSet<>(Util.getCurrentOrMainLooper(), Clock.DEFAULT,
                (listener, flags) -> {});
        this.mediaItems = new CopyOnWriteArrayList<>();
        this.period = new Timeline.Period();
        this.currentMediaItemIndex = 0;
        this.playbackState = STATE_IDLE;
        this.timeline = Timeline.EMPTY;
        this.playbackParameters = PlaybackParameters.DEFAULT;
        this.repeatMode = REPEAT_MODE_OFF;
        this.shuffleModeEnabled = false;
        this.videoSize = VideoSize.UNKNOWN;
        this.volume = 1.0f;
        this.deviceMuted = false;
        this.deviceVolume = 0;
        this.trackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
        this.currentTracks = Tracks.EMPTY;
        this.currentCueGroup = CueGroup.EMPTY_TIME_ZERO;
        this.availableCommands = new Commands.Builder()
                .addAll(COMMAND_SEEK_TO_DEFAULT_POSITION,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD,
                        Player.COMMAND_SEEK_TO_PREVIOUS)
                .build();
        this.contentPositionMs = C.TIME_UNSET;
        this.totalBufferedDurationMs = C.TIME_UNSET;
        this.bufferedPositionMs = C.TIME_UNSET;
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public Looper getApplicationLooper() {
        return context.getMainLooper();
    }

    @Override
    public Commands getAvailableCommands() {
        return availableCommands;
    }

    @Override
    public @State int getPlaybackState() {
        return playbackState;
    }

    @Override
    public int getPlaybackSuppressionReason() {
        return PLAYBACK_SUPPRESSION_REASON_NONE;
    }

    @Override
    public @RepeatMode int getRepeatMode() {
        return repeatMode;
    }

    @Override
    public void setRepeatMode(@RepeatMode int repeatMode) {
        this.repeatMode = repeatMode;
    }

    @Override
    public boolean getShuffleModeEnabled() {
        return shuffleModeEnabled;
    }

    @Override
    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
        this.shuffleModeEnabled = shuffleModeEnabled;
    }

    @Override
    public boolean getPlayWhenReady() {
        return playWhenReady;
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        this.playWhenReady = playWhenReady;
        if (playWhenReady) {
            MPVLib.setPropertyString(PROP_PAUSE, "no");
        } else {
            MPVLib.setPropertyString(PROP_PAUSE, "yes");
        }
    }

    @Override
    public void prepare() {
        if (mediaItems.isEmpty()) return;
        playbackState = STATE_BUFFERING;
        listeners.queueEvent(Player.EVENT_PLAYBACK_STATE_CHANGED, listener ->
                listener.onPlaybackStateChanged(playbackState));
        listeners.flushEvents();
    }

    @Override
    public void stop() {
        playbackState = STATE_IDLE;
        listeners.queueEvent(Player.EVENT_PLAYBACK_STATE_CHANGED, listener ->
                listener.onPlaybackStateChanged(playbackState));
        listeners.flushEvents();
    }

    @Override
    public void release() {
        try {
            MPVLib.destroy();
        } catch (Throwable ignored) {
        }
        playbackState = STATE_IDLE;
    }

    @Override
    public Timeline getCurrentTimeline() {
        return timeline;
    }

    @Override
    public int getCurrentPeriodIndex() {
        return 0;
    }

    @Override
    public int getCurrentMediaItemIndex() {
        return currentMediaItemIndex;
    }

    @Override
    public long getDuration() {
        Double d = MPVLib.getPropertyDouble(PROP_DURATION);
        if (d != null && d > 0) {
            return (long) (d * 1000);
        }
        return C.TIME_UNSET;
    }

    @Override
    public long getCurrentPosition() {
        Double d = MPVLib.getPropertyDouble(PROP_TIME_POS);
        if (d != null) {
            return (long) (d * 1000);
        }
        return 0;
    }

    @Override
    public long getBufferedPosition() {
        return bufferedPositionMs;
    }

    @Override
    public long getTotalBufferedDuration() {
        return totalBufferedDurationMs;
    }

    @Override
    public boolean isPlayingAd() {
        return false;
    }

    @Override
    public int getCurrentAdGroupIndex() {
        return C.INDEX_UNSET;
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
        return C.INDEX_UNSET;
    }

    @Override
    public long getContentPosition() {
        return contentPositionMs;
    }

    @Override
    public long getContentBufferedPosition() {
        return bufferedPositionMs;
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        return playbackParameters;
    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
    }

    @Override
    public TrackSelectionParameters getTrackSelectionParameters() {
        return trackSelectionParameters;
    }

    @Override
    public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
        this.trackSelectionParameters = parameters;
    }

    @Override
    public Tracks getCurrentTracks() {
        return currentTracks;
    }

    @Override
    public MediaMetadata getMediaMetadata() {
        return MediaMetadata.EMPTY;
    }

    @Override
    public MediaMetadata getPlaylistMetadata() {
        return playlistMetadata != null ? playlistMetadata : MediaMetadata.EMPTY;
    }

    @Override
    public void setPlaylistMetadata(MediaMetadata mediaMetadata) {
        this.playlistMetadata = mediaMetadata;
    }

    @Override
    public CueGroup getCurrentCues() {
        return currentCueGroup;
    }

    @Override
    public VideoSize getVideoSize() {
        return videoSize;
    }

    @Override
    public Size getSurfaceSize() {
        return Size.UNKNOWN;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public void setVolume(float volume) {
        this.volume = volume;
    }

    @Override
    public void mute() {
        setVolume(0f);
    }

    @Override
    public void unmute() {
        setVolume(1.0f);
    }

    @Override
    public void setVideoSurface(@Nullable Surface surface) {
        this.surface = surface;
        if (surface != null) {
            MPVLib.attachSurface(surface);
        } else {
            MPVLib.detachSurface();
        }
    }

    @Override
    public void clearVideoSurface() {
        setVideoSurface(null);
    }

    @Override
    public void clearVideoSurface(@Nullable Surface surface) {
        if (this.surface == surface) {
            setVideoSurface(null);
        }
    }

    @Override
    public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
        setVideoSurface(surfaceHolder != null ? surfaceHolder.getSurface() : null);
    }

    @Override
    public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
        clearVideoSurface(surfaceHolder != null ? surfaceHolder.getSurface() : null);
    }

    @Override
    public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
        setVideoSurfaceHolder(surfaceView != null ? surfaceView.getHolder() : null);
    }

    @Override
    public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
        setVideoSurface(null);
    }

    @Override
    public void setVideoTextureView(@Nullable TextureView textureView) {
        setVideoSurface(textureView != null && textureView.isAvailable()
                ? new Surface(textureView.getSurfaceTexture()) : null);
    }

    @Override
    public void clearVideoTextureView(@Nullable TextureView textureView) {
        setVideoSurface(null);
    }

    @Override
    public AudioAttributes getAudioAttributes() {
        return audioAttributes != null ? audioAttributes : AudioAttributes.DEFAULT;
    }

    @Override
    public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
        this.audioAttributes = audioAttributes;
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        return DeviceInfo.UNKNOWN;
    }

    @Override
    public int getDeviceVolume() {
        return deviceVolume;
    }

    @Override
    public boolean isDeviceMuted() {
        return deviceMuted;
    }

    @Override
    public void setDeviceVolume(int volume) {
        this.deviceVolume = volume;
    }

    @Override
    public void setDeviceVolume(int volume, @C.VolumeFlags int flags) {
        this.deviceVolume = volume;
    }

    @Override
    public void increaseDeviceVolume() {
        deviceVolume = Math.min(deviceVolume + 1, 100);
    }

    @Override
    public void increaseDeviceVolume(@C.VolumeFlags int flags) {
        deviceVolume = Math.min(deviceVolume + 1, 100);
    }

    @Override
    public void decreaseDeviceVolume() {
        deviceVolume = Math.max(deviceVolume - 1, 0);
    }

    @Override
    public void decreaseDeviceVolume(@C.VolumeFlags int flags) {
        deviceVolume = Math.max(deviceVolume - 1, 0);
    }

    @Override
    public void setDeviceMuted(boolean muted) {
        this.deviceMuted = muted;
    }

    @Override
    public void setDeviceMuted(boolean muted, @C.VolumeFlags int flags) {
        this.deviceMuted = muted;
    }

    @Override
    @Nullable
    public PlaybackException getPlayerError() {
        return playerError;
    }

    @Override
    public long getSeekBackIncrement() {
        return 10000;
    }

    @Override
    public long getSeekForwardIncrement() {
        return 10000;
    }

    @Override
    public long getMaxSeekToPreviousPosition() {
        return 3000;
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
        setMediaItems(mediaItems, resetPosition ? 0 : getCurrentMediaItemIndex(), C.TIME_UNSET);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        this.mediaItems.clear();
        this.mediaItems.addAll(mediaItems);
        this.currentMediaItemIndex = startIndex >= 0 && startIndex < mediaItems.size()
                ? startIndex : 0;
        this.timeline = buildTimeline(mediaItems);
        this.playbackState = STATE_IDLE;
    }

    @Override
    public void addMediaItems(int index, List<MediaItem> mediaItems) {
        this.mediaItems.addAll(index, mediaItems);
        this.timeline = buildTimeline(this.mediaItems);
    }

    @Override
    public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
        Util.moveItems(this.mediaItems, fromIndex, toIndex, newIndex);
        this.timeline = buildTimeline(this.mediaItems);
    }

    @Override
    public void removeMediaItems(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > mediaItems.size() || fromIndex >= toIndex) return;
        mediaItems.subList(fromIndex, toIndex).clear();
        this.timeline = buildTimeline(this.mediaItems);
    }

    @Override
    public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        int size = this.mediaItems.size();
        if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) return;
        List<MediaItem> newList = new ArrayList<>();
        if (fromIndex > 0) newList.addAll(this.mediaItems.subList(0, fromIndex));
        newList.addAll(mediaItems);
        if (toIndex < size) newList.addAll(this.mediaItems.subList(toIndex, size));
        this.mediaItems.clear();
        this.mediaItems.addAll(newList);
        this.timeline = buildTimeline(this.mediaItems);
    }

    @Override
    protected void seekTo(int mediaItemIndex, long positionMs, @Command int seekCommand,
            boolean isRepeatingCurrentItem) {
        if (mediaItemIndex != C.INDEX_UNSET
                && mediaItemIndex >= 0
                && mediaItemIndex < mediaItems.size()) {
            currentMediaItemIndex = mediaItemIndex;
        }
        if (positionMs != C.TIME_UNSET) {
            double seconds = positionMs / 1000.0;
            MPVLib.command(new String[]{"seek", String.valueOf(seconds), "absolute"});
        }
    }

    public void setDecode(int decode) {
        this.decode = decode;
    }

    public void addSubtitle(MediaItem.SubtitleConfiguration subtitle) {
        if (subtitle == null || subtitle.uri == null) return;
        MPVLib.command(new String[]{"sub-add", subtitle.uri.toString()});
        String title = subtitle.label != null ? subtitle.label : subtitle.language;
        if (title != null) {
            MPVLib.command(new String[]{"sub-add", subtitle.uri.toString(), "select", title});
        }
    }

    public void setSubtitleOptions(MpvPlayerConfig subtitleConfig) {
    }

    public boolean toggleGeneralStats() {
        return true;
    }

    private Timeline buildTimeline(List<MediaItem> items) {
        if (items.isEmpty()) return Timeline.EMPTY;
        Timeline.Window[] windows = new Timeline.Window[items.size()];
        for (int i = 0; i < items.size(); i++) {
            windows[i] = new Timeline.Window();
            windows[i].set(i, C.TIME_UNSET, C.TIME_UNSET, 0, false, true,
                    false, false, C.TIME_UNSET, MediaItem.EMPTY, null);
        }
        return new PlaylistTimeline(windows);
    }

    private static class PlaylistTimeline extends Timeline {
        private final Timeline.Window[] windows;

        PlaylistTimeline(Timeline.Window[] windows) {
            this.windows = windows;
        }

        @Override
        public int getWindowCount() {
            return windows.length;
        }

        @Override
        public int getNextWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
            return windowIndex + 1 < windows.length ? windowIndex + 1 : C.INDEX_UNSET;
        }

        @Override
        public int getPreviousWindowIndex(int windowIndex, int repeatMode,
                boolean shuffleModeEnabled) {
            return windowIndex > 0 ? windowIndex - 1 : C.INDEX_UNSET;
        }

        @Override
        public int getLastWindowIndex(boolean shuffleModeEnabled) {
            return windows.length - 1;
        }

        @Override
        public int getFirstWindowIndex(boolean shuffleModeEnabled) {
            return 0;
        }

        @Override
        public Timeline.Window getWindow(int windowIndex, Timeline.Window window,
                long defaultPositionProjectionUs) {
            window.set(windows[windowIndex]);
            return window;
        }

        @Override
        public Timeline.Period getPeriod(int periodIndex, Timeline.Period period, boolean setIds) {
            period.set(periodIndex, null, 0, C.TIME_UNSET, 0);
            return period;
        }

        @Override
        public int getIndexOfPeriod(Object uid) {
            return C.INDEX_UNSET;
        }

        @Override
        public Object getUidOfPeriod(int periodIndex) {
            return periodIndex;
        }
    }

    public static final class Builder {

        private final Context context;
        private MpvPlayerConfig config;
        private int decode;

        public Builder(Context context) {
            this.context = context.getApplicationContext();
            this.config = new MpvPlayerConfig.Builder().build();
        }

        public Builder setDecode(int decode) {
            this.decode = decode;
            return this;
        }

        public Builder setConfig(MpvPlayerConfig config) {
            this.config = config;
            return this;
        }

        public MpvPlayer build() {
            return new MpvPlayer(this);
        }
    }
}
