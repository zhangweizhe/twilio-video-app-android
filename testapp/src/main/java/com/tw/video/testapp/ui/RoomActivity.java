package com.tw.video.testapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tw.video.testapp.R;
import com.tw.video.testapp.util.AccessManagerHelper;
import com.tw.video.testapp.util.SimpleSignalingUtils;
import com.twilio.common.AccessManager;
import com.twilio.video.AudioTrack;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalMedia;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.Media;
import com.twilio.video.Participant;
import com.twilio.video.Room;
import com.twilio.video.VideoClient;
import com.twilio.video.VideoException;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

public class RoomActivity extends AppCompatActivity {
    @BindView(R.id.exit_room_fab) FloatingActionButton exitRoomFab;
    @BindView(R.id.media_status_textview) TextView mediaStatusTextview;
    @BindView(R.id.room_status_textview) TextView roomStatusTextview;
    @BindView(R.id.primary_video_view) VideoView primaryVideoView;
    //@BindView(R.id.thumbnail_video_view) VideoView thumbnailVideoView;
    @BindView(R.id.thumbnail_linear_layout) LinearLayout thumbnailLinearLayout;
    @BindView(R.id.switch_camera_action_fab) FloatingActionButton switchCameraActionFab;
    @BindView(R.id.local_video_action_fab) FloatingActionButton localVideoActionFab;
    @BindView(R.id.local_video_pause_fab) FloatingActionButton localVideoPauseFab;
    @BindView(R.id.local_audio_action_fab) FloatingActionButton localAudioActionFab;
    @BindView(R.id.local_audio_enable_fab) FloatingActionButton localAudioEnableFab;
    @BindView(R.id.speaker_action_fab) FloatingActionButton speakerActionFab;

    private String username;
    private String capabilityToken;
    private String realm;
    private AccessManager accessManager;
    private VideoClient videoClient;
    private Room room;
    private String roomName;
    private LocalMedia localMedia;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;
    private VideoView localVideoView;
    private CameraCapturer cameraCapturer;
    private Map<Participant, VideoView> videoViewMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // So calls can be answered when screen is locked
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_room);

        ButterKnife.bind(this);

        processActivityIntent(savedInstanceState);
        localMedia = LocalMedia.create(this);
        localAudioTrack = localMedia.addAudioTrack(true);
        cameraCapturer = new CameraCapturer(this,
                CameraCapturer.CameraSource.CAMERA_SOURCE_FRONT_CAMERA, null);
        localVideoTrack = localMedia.addVideoTrack(true, cameraCapturer);
        primaryVideoView.setMirror(true);
        localVideoTrack.addRenderer(primaryVideoView);
        createVideoClient();
        connectToRoom();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (primaryVideoView != null) {
            primaryVideoView.release();
            primaryVideoView = null;
        }
//        if (thumbnailVideoView != null) {
//            thumbnailVideoView.release();
//            thumbnailVideoView = null;
//        }
        if (localMedia != null) {
            localMedia.removeVideoTrack(localVideoTrack);
            localMedia.removeAudioTrack(localAudioTrack);
            localMedia.release();
            localMedia = null;
        }
        if (accessManager != null) {
            accessManager.dispose();
            accessManager = null;
        }
        if (videoClient != null) {
            videoClient.release();
            videoClient = null;
        }
    }

    private void processActivityIntent(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Timber.d("Restoring client activity state");
            username = savedInstanceState.getString(SimpleSignalingUtils.USERNAME);
            capabilityToken = savedInstanceState.getString(SimpleSignalingUtils.CAPABILITY_TOKEN);
            realm = savedInstanceState.getString(SimpleSignalingUtils.REALM);
            roomName = savedInstanceState.getString(SimpleSignalingUtils.ROOM_NAME);
        } else {
            Bundle extras = getIntent().getExtras();
            username = extras.getString(SimpleSignalingUtils.USERNAME);
            capabilityToken = extras.getString(SimpleSignalingUtils.CAPABILITY_TOKEN);
            realm = extras.getString(SimpleSignalingUtils.REALM);
            roomName = extras.getString(SimpleSignalingUtils.ROOM_NAME);
        }
    }

    private void createVideoClient() {
        accessManager = AccessManagerHelper.createAccessManager(this, capabilityToken);
        videoClient = new VideoClient(this, accessManager);
    }

    private void connectToRoom() {
        roomStatusTextview.setText("Connecting to room...");
        ConnectOptions connectOptions = new ConnectOptions.Builder()
                .name(roomName)
                .localMedia(localMedia)
                .build();

        room = videoClient.connect(connectOptions, createRoomListener());
    }

    @OnClick(R.id.exit_room_fab)
    public void exitRoom(View view) {
        if (room != null) {
            Timber.i("Exiting room");
            room.disconnect();
        } else {
            returnToVideoClientLogin();
        }
    }

    @OnClick(R.id.switch_camera_action_fab)
    public void switchCamera(View view) {
        if (cameraCapturer != null) {
            cameraCapturer.switchCamera();
        }
    }

    @OnClick(R.id.local_audio_enable_fab)
    public void enableLocalAudio(View view) {
        if (localAudioTrack != null) {
            boolean enable = !localAudioTrack.isEnabled();
            if (!localAudioTrack.enable(enable)) {
                Snackbar.make(roomStatusTextview,
                        "Audio track "+ (enable  ? "enable" : "diable") + " action failed",
                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
                return;
            }
            int icon = enable ? R.drawable.ic_mic_green_24px : R.drawable.ic_mic_red_24px;
            localAudioEnableFab.setImageDrawable(ContextCompat.getDrawable(RoomActivity.this, icon));
        }
    }

    @OnClick(R.id.local_audio_action_fab)
    public void toggleLocalAudio(View view) {
        int icon = 0;
        if (localAudioTrack == null) {
            localAudioTrack = localMedia.addAudioTrack(true);
            icon = R.drawable.ic_mic_white_24px;
            localAudioEnableFab.show();
        } else {
            if (!localMedia.removeAudioTrack(localAudioTrack)) {
                Snackbar.make(roomStatusTextview,
                        "Audio track remove action failed",
                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
            localAudioTrack = null;
            icon = R.drawable.ic_mic_off_gray_24px;
            localAudioEnableFab.hide();
        }
        localAudioActionFab.setImageDrawable(ContextCompat.getDrawable(RoomActivity.this, icon));
    }

    @OnClick(R.id.local_video_pause_fab)
    public void pauseVideo(View view) {
        if (localVideoTrack != null) {
            boolean enable = !localVideoTrack.isEnabled();
            if (!localVideoTrack.enable(enable)) {
                Snackbar.make(roomStatusTextview,
                        "Video track "+ (enable  ? "enable" : "diable") + " action failed",
                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
                return;
            }
            int icon = 0;
            if (enable) {
                icon = R.drawable.ic_pause_green_24px;
                switchCameraActionFab.show();
            } else {
                icon = R.drawable.ic_pause_red_24px;
                switchCameraActionFab.hide();
            }
            localVideoPauseFab.setImageDrawable(
                    ContextCompat.getDrawable(RoomActivity.this, icon));
        }
    }

    @OnClick(R.id.local_video_action_fab)
    public void toggleLocalVideo(View view) {
        int icon = 0;
//        if (localVideoTrack == null) {
//            localVideoTrack = localMedia.addVideoTrack(true, cameraCapturer);
//            if (room != null && room.getParticipants().size() > 0) {
//                thumbnailVideoView.setVisibility(View.VISIBLE);
//                localVideoTrack.addRenderer(thumbnailVideoView);
//            } else {
//                localVideoTrack.addRenderer(primaryVideoView);
//            }
//            switchCameraActionFab.show();
//            localVideoPauseFab.show();
//            icon = R.drawable.ic_videocam_white_24px;
//        } else {
//            if (!localMedia.removeVideoTrack(localVideoTrack)) {
//                Snackbar.make(roomStatusTextview,
//                        "Video track remove action failed",
//                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
//            }
//            localVideoTrack = null;
//            thumbnailVideoView.setVisibility(View.INVISIBLE);
//            if (room != null && room.getParticipants().size() == 0) {
//                // TODO: do something with primaryVideoView to show empty pic
//            }
//            switchCameraActionFab.hide();
//            localVideoPauseFab.hide();
//            icon = R.drawable.ic_videocam_off_gray_24px;
//        }
        localVideoActionFab.setImageDrawable(
                ContextCompat.getDrawable(RoomActivity.this, icon));
    }

    private void returnToVideoClientLogin(){
        Intent registrationIntent = new Intent(RoomActivity.this, VideoClientLoginActivity.class);
        startActivity(registrationIntent);
        finish();
    }

    private VideoView createVideoView() {
        VideoView videoView = new VideoView(this);
        videoView.setMirror(true);
        videoView.setZOrderMediaOverlay(true);
        return videoView;
    }

    private synchronized void addParticipant(Participant participant) {
        roomStatusTextview.setText("Participant "+participant.getIdentity()+ " joined");
        VideoView videoView = null;
        if (videoViewMap.isEmpty()) {
            localVideoTrack.removeRenderer(primaryVideoView);
            localVideoView = createVideoView();
            localVideoTrack.addRenderer(localVideoView);
            thumbnailLinearLayout.addView(localVideoView);
            thumbnailLinearLayout.setVisibility(View.VISIBLE);
            // TODO: set blank picture for the participant on primaryVideoView,
            // because we don't have participant video track yet
            videoView = primaryVideoView;
            primaryVideoView.setMirror(false);
        } else {
            videoView = createVideoView();
            thumbnailLinearLayout.addView(videoView);
        }
        videoViewMap.put(participant, videoView);
        participant.getMedia().setListener(new ParticipantMediaListener(participant));
    }

    private synchronized void removeParticipant(Participant participant) {
        roomStatusTextview.setText("Participant "+participant.getIdentity()+ " left.");
        VideoView videoView = videoViewMap.remove(participant);
        if (videoView == null) {
            // TODO: handle error
            return;
        }
        List<VideoTrack> participantVideoTracks = participant.getMedia().getVideoTracks();
        if (participantVideoTracks.size() > 0) {
            participantVideoTracks.get(0).removeRenderer(videoView);
        }
        if (videoView == primaryVideoView) {
            // Pick next view from thumbnail layout
            if (videoViewMap.size() != 0) {
                Map.Entry<Participant, VideoView> entry = videoViewMap.entrySet().iterator().next();
                thumbnailLinearLayout.removeView(entry.getValue());
                Participant nextParticipant = entry.getKey();
                // TODO: participant might not have track, fix it
                nextParticipant.getMedia().getVideoTracks().get(0).removeRenderer(entry.getValue());
                nextParticipant.getMedia().getVideoTracks().get(0).addRenderer(primaryVideoView);
                videoViewMap.put(nextParticipant, primaryVideoView);
            } else {
                // set local view as primary view
                localVideoTrack.removeRenderer(localVideoView);
                primaryVideoView.setMirror(true);
                localVideoTrack.addRenderer(primaryVideoView);
                localVideoView = primaryVideoView;
                thumbnailLinearLayout.setVisibility(View.GONE);
            }

        } else {
            thumbnailLinearLayout.removeView(videoView);
        }
    }

    private Room.Listener createRoomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                Timber.i("onConnected: "+room.getName() + " sid:"+
                        room.getSid()+" state:"+room.getState());
                roomStatusTextview.setText("Connected to "+room.getName());

                for (Map.Entry<String, Participant> entry : room.getParticipants().entrySet()) {
                    addParticipant(entry.getValue());
                    // TODO just grabbing first participant...need to support multiple participants
                    //break;
                }
            }

            @Override
            public void onConnectFailure(VideoException error) {
                Timber.i("onConnectFailure");
                roomStatusTextview.setText("Failed to connect to "+roomName);
            }

            @Override
            public void onDisconnected(Room room, VideoException error) {
                Timber.i("onDisconnected");
                roomStatusTextview.setText("Disconnected from "+roomName);
                RoomActivity.this.room = null;
                returnToVideoClientLogin();
            }

            @Override
            public void onParticipantConnected(Room room, Participant participant) {
                Timber.i("onParticipantConnected: "+participant.getIdentity());
                addParticipant(participant);
            }

            @Override
            public void onParticipantDisconnected(Room room, Participant participant) {
                Timber.i("onParticipantDisconnected "+participant.getIdentity());
                removeParticipant(participant);
            }
        };
    }

    private class ParticipantMediaListener implements Media.Listener {
        private Participant participant;

        ParticipantMediaListener(Participant participant) {
            this.participant = participant;
        }

        @Override
        public void onAudioTrackAdded(Media media, AudioTrack audioTrack) {
            Timber.i("onAudioTrackAdded");
            mediaStatusTextview.setText(participant.getIdentity() + ": onAudioTrackAdded");
        }

        @Override
        public void onAudioTrackRemoved(Media media, AudioTrack audioTrack) {
            Timber.i(participant.getIdentity() + ": onAudioTrackRemoved for ");
        }

        @Override
        public void onVideoTrackAdded(Media media, VideoTrack videoTrack) {
            Timber.i(participant.getIdentity() + ": onVideoTrackAdded");
            /*
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT);
            RelativeLayout layout = new RelativeLayout(RoomActivity.this);
            layout.setClickable(false);
            layout.setLayoutParams(layoutParams);*/
            //primaryVideoView.setMirror(false);
            //videoTrack.addRenderer(primaryVideoView);
            VideoView videoView = videoViewMap.get(participant);
            videoTrack.addRenderer(videoView);
        }

        @Override
        public void onVideoTrackRemoved(Media media, VideoTrack videoTrack) {
            Timber.i(participant.getIdentity() + ": onVideoTrackRemoved");
            videoTrack.removeRenderer(videoViewMap.get(participant));
        }

        @Override
        public void onAudioTrackEnabled(Media media, AudioTrack audioTrack) {
            Timber.i(participant.getIdentity() + ": onAudioTrackEnabled");
        }

        @Override
        public void onAudioTrackDisabled(Media media, AudioTrack audioTrack) {
            Timber.i(participant.getIdentity() + ": onAudioTrackDisabled");
        }

        @Override
        public void onVideoTrackEnabled(Media media, VideoTrack videoTrack) {
            Timber.i(participant.getIdentity() + ": onVideoTrackEnabled");
        }

        @Override
        public void onVideoTrackDisabled(Media media, VideoTrack videoTrack) {
            Timber.i(participant.getIdentity() + ": onVideoTrackDisabled");
        }
    }
}
