/*
 * Copyright (C) 2017 Twilio, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twilio.video;

import android.support.annotation.NonNull;

/**
 * Represents options when creating {@link LocalDataTrack}s.
 */
public class DataTrackOptions {
    /**
     * Default value for max packet life time.
     */
    public static final int DEFAULT_MAX_PACKET_LIFE_TIME = -1;

    /**
     * Default value for max retransmits
     */
    public static final int DEFAULT_MAX_RETRANSMITS = -1;

    /**
     * Default data track options.
     */
    @NonNull public static final DataTrackOptions DEFAULT_DATA_TRACK_OPTIONS = new Builder()
            .ordered(true)
            .maxPacketLifeTime(DEFAULT_MAX_PACKET_LIFE_TIME)
            .maxRetransmits(DEFAULT_MAX_RETRANSMITS)
            .build();

    final boolean ordered;
    final int maxPacketLifeTime;
    final int maxRetransmits;
    final String name;

    private DataTrackOptions(Builder builder) {
        this.ordered = builder.ordered;
        this.maxPacketLifeTime = builder.maxPacketLifeTime;
        this.maxRetransmits = builder.maxRetransmits;
        this.name = builder.name;
    }

    /**
     * Build new {@link DataTrackOptions}.
     *
     * <p>All methods are optional.</p>
     */
    public static class Builder {
        private boolean ordered = true;
        private int maxPacketLifeTime = DEFAULT_MAX_PACKET_LIFE_TIME;
        private int maxRetransmits = DEFAULT_MAX_RETRANSMITS;
        private String name = null;

        public Builder() {}

        /**
         * Ordered transmission of messages. Default is {@code true}.
         */
        @NonNull public Builder ordered(boolean ordered) {
            this.ordered = ordered;
            return this;
        }

        /**
         * Maximum retransmit time in milliseconds.
         */
        @NonNull public Builder maxPacketLifeTime(int maxPacketLifeTime) {
            this.maxPacketLifeTime = maxPacketLifeTime;
            return this;
        }

        /**
         * Maximum number of retransmitted messages.
         */
        @NonNull public Builder maxRetransmits(int maxRetransmits) {
            this.maxRetransmits = maxRetransmits;
            return this;
        }

        /**
         * Data track name.
         */
        @NonNull public Builder name(@NonNull String name) {
            this.name = name;
            return this;
        }

        /**
         * Builds the data track options.
         *
         * <p>Max packet life time and max retransmits are mutually exclusive. This means
         * that only one of these values can be set to a non default value at a time otherwise
         * a {@link IllegalStateException} occurs.</p>
         */
        @NonNull
        public DataTrackOptions build() {
            Preconditions.checkArgument(maxPacketLifeTime >= DEFAULT_MAX_PACKET_LIFE_TIME);
            Preconditions.checkArgument(maxPacketLifeTime <= 65535);
            Preconditions.checkArgument(maxRetransmits >= DEFAULT_MAX_RETRANSMITS);
            Preconditions.checkArgument(maxRetransmits <= 65535);
            Preconditions.checkState(maxRetransmits == DEFAULT_MAX_RETRANSMITS ||
                    maxPacketLifeTime == DEFAULT_MAX_PACKET_LIFE_TIME);
            return new DataTrackOptions(this);
        }
    }
}
