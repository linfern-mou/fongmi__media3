package androidx.media3.mpvplayer;

import android.content.Context;
import java.io.File;

/**
 * Configuration for {@link MpvPlayer}, wrapping libmpv options.
 */
public final class MpvPlayerConfig {

    public static final String VIDEO_OUTPUT_GPU_NEXT = "gpu-next";

    private MpvPlayerConfig() {
    }

    public boolean isAvailable() {
        return true;
    }

    /**
     * Builder for {@link MpvPlayerConfig}.
     */
    public static final class Builder {

        private String defaultUserAgent;
        private boolean hlsHttpPersistent;
        private File configDir;
        private File fontConfigDir;
        private File fontCacheDir;
        private String videoOutputDriver;
        private File androidDefaultsCacheDir;
        private String tlsCaAsset;
        private Context tlsCaContext;
        private String preInitGpuApi;
        private String preInitGpuContext;
        private String postInitSubLang;
        private File diskCacheDir;
        private int diskCacheMaxTimeSeconds;
        private int diskCacheMaxSizeMb;
        private Context subtitleContext;
        private boolean subtitleCaption;
        private double subtitlePosition;
        private double subtitleScale;

        public Builder() {
        }

        public Builder setDefaultUserAgent(String defaultUserAgent) {
            this.defaultUserAgent = defaultUserAgent;
            return this;
        }

        public Builder setHlsHttpPersistent(boolean hlsHttpPersistent) {
            this.hlsHttpPersistent = hlsHttpPersistent;
            return this;
        }

        public Builder addConfigDirectory(File configDir) {
            this.configDir = configDir;
            return this;
        }

        public Builder addAndroidFontConfig(File configDir, File cacheDir) {
            this.fontConfigDir = configDir;
            this.fontCacheDir = cacheDir;
            return this;
        }

        public Builder addAndroidDefaults(String videoOutput, File cacheDir) {
            this.videoOutputDriver = videoOutput;
            this.androidDefaultsCacheDir = cacheDir;
            return this;
        }

        public Builder addTlsCaFileFromAsset(Context context, String assetFile, String fallbackPath) {
            this.tlsCaContext = context;
            this.tlsCaAsset = assetFile;
            return this;
        }

        public Builder addPreInitStringOption(String key, String value) {
            if ("gpu-api".equals(key)) {
                this.preInitGpuApi = value;
            } else if ("gpu-context".equals(key)) {
                this.preInitGpuContext = value;
            }
            return this;
        }

        public Builder addPostInitStringOption(String key, String value) {
            if ("slang".equals(key)) {
                this.postInitSubLang = value;
            }
            return this;
        }

        public Builder addDiskCacheOptions(File cacheDir, int maxTimeSeconds, int maxSizeMb) {
            this.diskCacheDir = cacheDir;
            this.diskCacheMaxTimeSeconds = maxTimeSeconds;
            this.diskCacheMaxSizeMb = maxSizeMb;
            return this;
        }

        public Builder addAndroidSubtitleOptions(Context context, boolean caption, double position, double scale) {
            this.subtitleContext = context;
            this.subtitleCaption = caption;
            this.subtitlePosition = position;
            this.subtitleScale = scale;
            return this;
        }

        public MpvPlayerConfig build() {
            return new MpvPlayerConfig();
        }
    }
}
