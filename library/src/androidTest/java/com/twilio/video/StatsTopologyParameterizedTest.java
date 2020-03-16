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

import static com.twilio.video.util.VideoAssert.assertIsTrackSid;
import static com.twilio.video.util.VideoAssert.assertNotNullOrEmpty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import com.twilio.video.base.BaseStatsTest;
import com.twilio.video.helper.CallbackHelper;
import com.twilio.video.testcategories.StatsTest;
import com.twilio.video.util.FakeVideoCapturer;
import com.twilio.video.util.Topology;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@StatsTest
@RunWith(Parameterized.class)
@LargeTest
public class StatsTopologyParameterizedTest extends BaseStatsTest {
    private static final int MAX_RETRIES = 5;

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {{Topology.P2P}, {Topology.GROUP}});
    }

    public StatsTopologyParameterizedTest(Topology topology) {
        this.topology = topology;
    }

    @Before
    public void setup() throws InterruptedException {
        super.baseSetup(topology);
    }

    @After
    @Override
    public void teardown() throws InterruptedException {
        super.teardown();
        assertTrue(MediaFactory.isReleased());
    }

    @Test
    public void shouldReceiveStatsForParticipantTracks() throws InterruptedException {
        // Connect Alice to room with local audio track only
        aliceLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        aliceRoom =
                createRoom(
                        aliceToken,
                        aliceListener,
                        roomName,
                        Collections.singletonList(aliceLocalAudioTrack));
        aliceListener.onParticipantConnectedLatch = new CountDownLatch(1);

        // Connect Bob to room with audio and video track
        bobLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        bobLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        bobRoom =
                createRoom(
                        bobToken,
                        bobListener,
                        roomName,
                        Collections.singletonList(bobLocalAudioTrack),
                        Collections.singletonList(bobLocalVideoTrack));
        assertTrue(
                aliceListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, aliceRoom.getRemoteParticipants().size());

        expectStatsReportTracksSize(1, 0, 1, 1);
    }

    @Test
    public void shouldReceiveStatsWhenParticipanAddsOrRemovesTrack() throws InterruptedException {
        // Connect Alice to room with local audio track only
        aliceLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        aliceRoom =
                createRoom(
                        aliceToken,
                        aliceListener,
                        roomName,
                        Collections.singletonList(aliceLocalAudioTrack));
        aliceListener.onParticipantConnectedLatch = new CountDownLatch(1);

        // Connect Bob without media
        bobRoom = createRoom(bobToken, bobListener, roomName);
        assertTrue(
                aliceListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, aliceRoom.getRemoteParticipants().size());

        // Add audio track to Bob and check stats
        CallbackHelper.FakeRemoteParticipantListener remoteParticipantListener =
                new CallbackHelper.FakeRemoteParticipantListener();
        remoteParticipantListener.onSubscribedToAudioTrackLatch = new CountDownLatch(1);
        remoteParticipantListener.onSubscribedToVideoTrackLatch = new CountDownLatch(1);
        remoteParticipantListener.onUnsubscribedFromVideoTrackLatch = new CountDownLatch(1);
        RemoteParticipant bob = aliceRoom.getRemoteParticipants().get(0);
        bob.setListener(remoteParticipantListener);

        LocalParticipant bobLocalParticipant = bobRoom.getLocalParticipant();
        bobLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        assertTrue(bobLocalParticipant.publishTrack(bobLocalAudioTrack));
        assertTrue(
                remoteParticipantListener.onSubscribedToAudioTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        expectStatsReportTracksSize(1, 0, 1, 0);

        // Add video track to bob and check stats
        bobLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        assertTrue(bobLocalParticipant.publishTrack(bobLocalVideoTrack));
        assertTrue(
                remoteParticipantListener.onSubscribedToVideoTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        expectStatsReportTracksSize(1, 0, 1, 1);

        // Remove Bob's video track and check the stats
        bobLocalParticipant.unpublishTrack(bobLocalVideoTrack);
        assertTrue(
                remoteParticipantListener.onUnsubscribedFromVideoTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        expectStatsReportTracksSize(1, 0, 1, 0);
    }

    @Test
    public void shouldReceiveStatsWhenLocalTrackIsAdded() throws InterruptedException {
        // Connect Alice to room without media
        aliceRoom = createRoom(aliceToken, aliceListener, roomName);
        aliceListener.onParticipantConnectedLatch = new CountDownLatch(1);

        // Connect Bob without tracks
        bobRoom = createRoom(bobToken, bobListener, roomName);
        assertTrue(
                aliceListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, aliceRoom.getRemoteParticipants().size());

        // Get alice local bobRemoteParticipant
        LocalParticipant aliceLocalParticipant = aliceRoom.getLocalParticipant();

        // Add audio and video track to alice
        aliceLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        assertTrue(aliceLocalParticipant.publishTrack(aliceLocalVideoTrack));

        aliceLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        assertTrue(aliceLocalParticipant.publishTrack(aliceLocalAudioTrack));

        expectStatsReportTracksSize(1, 1, 0, 0);
    }

    @Test
    public void shouldGetStatsForMultipleRequests() throws InterruptedException {
        final int numberOfRequests = 4;

        // Connect Alice to room with local audio track only
        aliceLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        aliceRoom =
                createRoom(
                        aliceToken,
                        aliceListener,
                        roomName,
                        Collections.singletonList(aliceLocalAudioTrack));
        aliceListener.onParticipantConnectedLatch = new CountDownLatch(1);

        // Connect Bob to room with audio and video track
        bobLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        bobRoom =
                createRoom(
                        bobToken,
                        bobListener,
                        roomName,
                        Collections.singletonList(bobLocalAudioTrack));
        assertTrue(
                aliceListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, aliceRoom.getRemoteParticipants().size());

        // Call getStats multiple times
        CallbackHelper.FakeStatsListener aliceStatsListener =
                new CallbackHelper.FakeStatsListener();
        aliceStatsListener.onStatsLatch = new CountDownLatch(numberOfRequests);
        for (int i = 0; i < numberOfRequests; i++) {
            aliceRoom.getStats(aliceStatsListener);
        }

        // Validate all callbacks received
        assertTrue(
                aliceStatsListener.onStatsLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Validate size
        expectStatsReportTracksSize(1, 0, 1, 0);
    }

    @Test
    public void iceCandidatePairStatsShouldNotBeEmpty() throws InterruptedException {
        // Connect Alice to room with both video and audio track
        aliceLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        aliceLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        aliceRoom =
                createRoom(
                        aliceToken,
                        aliceListener,
                        roomName,
                        Collections.singletonList(aliceLocalAudioTrack),
                        Collections.singletonList(aliceLocalVideoTrack));
        aliceListener.onParticipantConnectedLatch = new CountDownLatch(1);

        // Connect Bob to room with both video and audio track
        bobLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        bobLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        bobRoom =
                createRoom(
                        bobToken,
                        bobListener,
                        roomName,
                        Collections.singletonList(bobLocalAudioTrack),
                        Collections.singletonList(bobLocalVideoTrack));
        assertTrue(
                aliceListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        StatsReport statsReport = expectStatsReportTracksSize(1, 1, 1, 1);

        assertNotEquals("", statsReport.getPeerConnectionId());
        // Check IceCandidatePairStats
        IceCandidatePairStats iceCandidatePairStats = statsReport.getIceCandidatePairStats().get(0);
        checkIceCandidatePairStats(iceCandidatePairStats);

        // Check IceCandidateStats
        IceCandidateStats iceCandidateStats = statsReport.getIceCandidateStats().get(0);
        checkIceCandidateStats(iceCandidateStats);
    }

    @Test
    @Ignore("GSDK-1983")
    public void reportShouldHaveNonEmptyValues() throws InterruptedException {
        // Connect Alice to room with both video and audio track
        aliceLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        aliceLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        aliceRoom =
                createRoom(
                        aliceToken,
                        aliceListener,
                        roomName,
                        Collections.singletonList(aliceLocalAudioTrack),
                        Collections.singletonList(aliceLocalVideoTrack));
        aliceListener.onParticipantConnectedLatch = new CountDownLatch(1);

        // Connect Bob to room with both video and audio track
        bobLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        bobLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        bobRoom =
                createRoom(
                        bobToken,
                        bobListener,
                        roomName,
                        Collections.singletonList(bobLocalAudioTrack),
                        Collections.singletonList(bobLocalVideoTrack));
        assertTrue(
                aliceListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        StatsReport statsReport = expectStatsReportTracksSize(1, 1, 1, 1);

        assertNotEquals("", statsReport.getPeerConnectionId());

        // Check LocalAudioTrackStats
        LocalAudioTrackStats localAudioTrackStats = statsReport.getLocalAudioTrackStats().get(0);
        checkBaseTrackStats(localAudioTrackStats);
        checkLocalTrackStats(localAudioTrackStats);
        assertTrue(0 < localAudioTrackStats.audioLevel);
        assertTrue(0 < localAudioTrackStats.jitter);
        assertTrue(0 < localAudioTrackStats.roundTripTime);

        // Check LocalVideoTrackStats
        LocalVideoTrackStats localVideoTrackStats = statsReport.getLocalVideoTrackStats().get(0);
        checkBaseTrackStats(localVideoTrackStats);
        checkLocalTrackStats(localVideoTrackStats);
        assertTrue(0 < localVideoTrackStats.capturedFrameRate);
        assertTrue(0 < localVideoTrackStats.captureDimensions.width);
        assertTrue(0 < localVideoTrackStats.captureDimensions.height);
        assertTrue(0 < localVideoTrackStats.frameRate);
        assertTrue(0 < localVideoTrackStats.dimensions.width);
        assertTrue(0 < localVideoTrackStats.dimensions.height);
        assertTrue(0 < localVideoTrackStats.roundTripTime);

        // Check RemoteAudioTrackStats
        RemoteAudioTrackStats remoteAudioTrackStats = statsReport.getRemoteAudioTrackStats().get(0);
        checkBaseTrackStats(remoteAudioTrackStats);
        checkTrackStats(remoteAudioTrackStats);
        assertTrue(0 < remoteAudioTrackStats.audioLevel);
        assertTrue(0 < remoteAudioTrackStats.jitter);

        // Check RemoteVideoTrackStats
        RemoteVideoTrackStats remoteVideoTrackStats = statsReport.getRemoteVideoTrackStats().get(0);
        checkBaseTrackStats(remoteVideoTrackStats);
        checkTrackStats(remoteVideoTrackStats);
        assertTrue(0 < remoteVideoTrackStats.frameRate);
        assertTrue(0 < remoteVideoTrackStats.dimensions.width);
        assertTrue(0 < remoteVideoTrackStats.dimensions.height);
    }

    @Test(expected = NullPointerException.class)
    public void shouldFailWithNullListener() throws InterruptedException {
        aliceRoom = createRoom(aliceToken, aliceListener, roomName);
        aliceRoom.getStats(null);
    }

    @Test
    public void shouldReceiveStatsInEmptyRoom() throws InterruptedException {
        aliceRoom = createRoom(aliceToken, aliceListener, roomName);

        CallbackHelper.FakeStatsListener aliceStatsListener =
                new CallbackHelper.FakeStatsListener();
        aliceStatsListener.onStatsLatch = new CountDownLatch(1);
        aliceRoom.getStats(aliceStatsListener);
        assertTrue(
                aliceStatsListener.onStatsLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertTrue(1 >= aliceStatsListener.getStatsReports().size());
    }

    @Test
    public void shouldInvokeListenerOnCallingThread() throws InterruptedException {
        // Connect Alice to room with local audio track only
        aliceLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        aliceRoom =
                createRoom(
                        aliceToken,
                        aliceListener,
                        roomName,
                        Collections.singletonList(aliceLocalAudioTrack));
        aliceListener.onParticipantConnectedLatch = new CountDownLatch(1);
        final CountDownLatch statsCallback = new CountDownLatch(1);

        /*
         * Run on UI thread to avoid thread hopping between the test runner thread and the UI
         * thread.
         */
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            final long callingThreadId = Thread.currentThread().getId();
                            StatsListener statsListener =
                                    statsReports -> {
                                        assertEquals(
                                                callingThreadId, Thread.currentThread().getId());
                                        statsCallback.countDown();
                                    };
                            aliceRoom.getStats(statsListener);
                        });

        assertTrue(statsCallback.await(TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
    }

    @Test
    @Ignore("GSDK-1983")
    public void shouldReceiveEmptyReportsIfRoomGetsDisconnected() throws InterruptedException {
        final int numberOfRequests = 10;

        // Connect Alice to room with local audio track only
        aliceLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        aliceRoom =
                createRoom(
                        aliceToken,
                        aliceListener,
                        roomName,
                        Collections.singletonList(aliceLocalAudioTrack));
        aliceListener.onParticipantConnectedLatch = new CountDownLatch(1);

        // Connect Bob to room with audio and video track
        bobLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        bobRoom =
                createRoom(
                        bobToken,
                        bobListener,
                        roomName,
                        Collections.singletonList(bobLocalAudioTrack));
        assertTrue(
                aliceListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, aliceRoom.getRemoteParticipants().size());

        // let's give peer connection some time to get media flowing
        TestUtils.blockingWait(TestUtils.ONE_SECOND);

        // send getStats() requests
        CallbackHelper.FakeStatsListener aliceStatsListener =
                new CallbackHelper.FakeStatsListener();
        aliceStatsListener.onStatsLatch = new CountDownLatch(numberOfRequests);
        for (int i = 0; i < numberOfRequests; i++) {
            aliceRoom.getStats(aliceStatsListener);
        }

        // disconnect from room
        aliceListener.onDisconnectedLatch = new CountDownLatch(1);
        aliceRoom.disconnect();
        assertTrue(
                aliceListener.onDisconnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        assertTrue(
                aliceStatsListener.onStatsLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        List<StatsReport> statsReportList = aliceStatsListener.getStatsReports();
        assertEquals(0, statsReportList.size());
    }

    @Test
    public void shouldNotReceiveReportAfterRoomIsDisconnected() throws InterruptedException {
        // Connect Alice to room with both video and audio track
        aliceLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        aliceLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        aliceRoom =
                createRoom(
                        aliceToken,
                        aliceListener,
                        roomName,
                        Collections.singletonList(aliceLocalAudioTrack),
                        Collections.singletonList(aliceLocalVideoTrack));
        aliceListener.onParticipantConnectedLatch = new CountDownLatch(1);

        // Connect Bob to room with both video and audio track
        bobLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        bobLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        bobRoom =
                createRoom(
                        bobToken,
                        bobListener,
                        roomName,
                        Collections.singletonList(bobLocalAudioTrack),
                        Collections.singletonList(bobLocalVideoTrack));
        assertTrue(
                aliceListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        CallbackHelper.FakeStatsListener aliceStatsListener =
                new CallbackHelper.FakeStatsListener();
        aliceStatsListener.onStatsLatch = new CountDownLatch(1);
        aliceListener.onDisconnectedLatch = new CountDownLatch(1);
        aliceRoom.disconnect();

        // wait for disconnect
        assertTrue(
                aliceListener.onDisconnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // call get stats after room has been disconnected
        aliceStatsListener = new CallbackHelper.FakeStatsListener();
        aliceStatsListener.onStatsLatch = new CountDownLatch(1);
        aliceRoom.getStats(aliceStatsListener);
        assertFalse(
                aliceStatsListener.onStatsLatch.await(TestUtils.SMALL_WAIT, TimeUnit.MILLISECONDS));
    }

    private StatsReport expectStatsReportTracksSize(
            int localAudioTrackSize,
            int localVideoTrackSize,
            int audioTrackSize,
            int videoTrackSize)
            throws InterruptedException {
        boolean expectedStatsReportTrackSizesMet;
        int retries = 0;
        StatsReport statsReport;

        do {
            // Give peer connection some time to get media flowing
            TestUtils.blockingWait(TestUtils.ONE_SECOND);

            CallbackHelper.FakeStatsListener aliceStatsListener =
                    new CallbackHelper.FakeStatsListener();
            aliceStatsListener.onStatsLatch = new CountDownLatch(1);
            aliceRoom.getStats(aliceStatsListener);
            assertTrue(
                    aliceStatsListener.onStatsLatch.await(
                            TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

            List<StatsReport> statsReportList = aliceStatsListener.getStatsReports();
            assertEquals(1, statsReportList.size());
            statsReport = statsReportList.get(0);

            expectedStatsReportTrackSizesMet =
                    localAudioTrackSize == statsReport.getLocalAudioTrackStats().size()
                            && localVideoTrackSize == statsReport.getLocalVideoTrackStats().size()
                            && audioTrackSize == statsReport.getRemoteAudioTrackStats().size()
                            && videoTrackSize == statsReport.getRemoteVideoTrackStats().size();
        } while (!expectedStatsReportTrackSizesMet && retries++ < MAX_RETRIES);

        assertTrue(expectedStatsReportTrackSizesMet);

        return statsReport;
    }

    private void checkBaseTrackStats(BaseTrackStats stats) {
        assertNotNullOrEmpty(stats.codec);
        assertNotNullOrEmpty(stats.ssrc);
        assertNotNullOrEmpty(stats.trackSid);
        assertIsTrackSid(stats.trackSid);
        // TODO: Packets lost is always 0. Find a way to make test that will exercise this
        assertTrue(0 <= stats.packetsLost);
        assertTrue(0.0 < stats.timestamp);
    }

    private void checkLocalTrackStats(LocalTrackStats stats) {
        assertTrue(0 < stats.bytesSent);
        assertTrue(0 < stats.packetsSent);
        assertTrue(0 < stats.roundTripTime);
    }

    private void checkTrackStats(RemoteTrackStats stats) {
        assertTrue(0 < stats.bytesReceived);
        assertTrue(0 < stats.packetsReceived);
    }

    private void checkIceCandidateStats(IceCandidateStats stats) {
        assertNotNullOrEmpty(stats.candidateType);
        assertNotNullOrEmpty(stats.ip);
        assertNotNullOrEmpty(stats.protocol);
        assertNotNullOrEmpty(stats.transportId);
    }

    private void checkIceCandidatePairStats(IceCandidatePairStats stats) {
        assertNotNullOrEmpty(stats.localCandidateId);
        assertNotNullOrEmpty(stats.remoteCandidateId);
        assertNotNullOrEmpty(stats.localCandidateIp);
        assertNotNullOrEmpty(stats.remoteCandidateIp);
        assertNotNullOrEmpty(stats.transportId);
        assertNotNullOrEmpty(stats.relayProtocol);
    }
}
