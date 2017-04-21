package com.twilio.video;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents options when connecting to a {@link Room}.
 */
public class ConnectOptions {
    private final String accessToken;
    private final String roomName;
    private final List<LocalAudioTrack> audioTracks;
    private final List<LocalVideoTrack> videoTracks;
    private final IceOptions iceOptions;
    private final boolean enableInsights;

    private ConnectOptions(Builder builder) {
        this.accessToken = builder.accessToken;
        this.roomName = builder.roomName;
        this.audioTracks = builder.audioTracks;
        this.videoTracks = builder.videoTracks;
        this.iceOptions = builder.iceOptions;
        this.enableInsights = builder.enableInsights;
    }

    String getAccessToken() {
        return accessToken;
    }

    String getRoomName() {
        return roomName;
    }

    List<LocalAudioTrack> getAudioTracks() {
        return audioTracks;
    }

    List<LocalVideoTrack> getVideoTracks() {
        return videoTracks;
    }

    IceOptions getIceOptions() {
        return iceOptions;
    }

    boolean isInsightsEnabled() {
        return enableInsights;
    }

    private LocalAudioTrack[] getLocalAudioTracksArray() {
        LocalAudioTrack[] audioTracksArray = new LocalAudioTrack[0];
        if (audioTracks != null && audioTracks.size() > 0) {
            audioTracksArray = new LocalAudioTrack[audioTracks.size()];
            audioTracksArray = audioTracks.toArray(audioTracksArray);
        }
        return audioTracksArray;
    }

    private LocalVideoTrack[] getLocalVideoTracksArray() {
        LocalVideoTrack[] videoTracksArray = new LocalVideoTrack[0];
        if (videoTracks != null && videoTracks.size() > 0) {
            videoTracksArray = new LocalVideoTrack[videoTracks.size()];
            videoTracksArray = videoTracks.toArray(videoTracksArray);
        }
        return videoTracksArray;
    }

    private long createNativeObject(long mediaFactoryNativeHandle) {
        return nativeCreate(accessToken,
                roomName,
                getLocalAudioTracksArray(),
                getLocalVideoTracksArray(),
                iceOptions,
                enableInsights,
                PlatformInfo.getNativeHandle(),
                mediaFactoryNativeHandle);
    }

    private native long nativeCreate(String accessToken,
                                     String roomName,
                                     LocalAudioTrack[] audioTracks,
                                     LocalVideoTrack[] videoTracks,
                                     IceOptions iceOptions,
                                     boolean enableInsights,
                                     long platformInfoNativeHandle,
                                     long mediaFactoryNativeHandle);
    /**
     * Build new {@link ConnectOptions}.
     *
     * <p>All methods are optional.</p>
     */
    public static class Builder {
        private String accessToken = "";
        private String roomName = "";
        private IceOptions iceOptions;
        private List<LocalAudioTrack> audioTracks;
        private List<LocalVideoTrack> videoTracks;
        private boolean enableInsights;

        public Builder(String accessToken) {
            this.accessToken = accessToken;
        }

        /**
         * The name of the room.
         */
        public Builder roomName(String roomName) {
            this.roomName = roomName;
            return this;
        }

        /**
         * Audio tracks that will be published upon connection.
         */
        public Builder audioTracks(List<LocalAudioTrack> audioTracks) {
            Preconditions.checkNotNull(audioTracks, "LocalAudioTrack List must not be null");
            this.audioTracks = new ArrayList<>(audioTracks);
            return this;
        }

        /**
         * Video tracks that will be published upon connection.
         */
        public Builder videoTracks(List<LocalVideoTrack> videoTracks) {
            Preconditions.checkNotNull(videoTracks, "LocalVideoTrack List must not be null");
            this.videoTracks = new ArrayList<>(videoTracks);
            return this;
        }

        /**
         * Custom ICE configuration used to connect to a Room.
         */
        public Builder iceOptions(IceOptions iceOptions) {
            this.iceOptions = iceOptions;
            return this;
        }

        /**
         * Enable sending stats data to Insights
         */
        public Builder enableInsights(boolean enable) {
            this.enableInsights = enable;
            return this;
        }

        /**
         * Builds {@link ConnectOptions} object.
         * @throws Exception if accessToken is null or empty.
         */
        public ConnectOptions build() {
            Preconditions.checkNotNull(accessToken, "Token must not be null.");
            Preconditions.checkArgument(!accessToken.equals(""), "Token must not be empty.");

            return new ConnectOptions(this);
        }
    }
}