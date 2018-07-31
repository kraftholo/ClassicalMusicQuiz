/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*  	http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.example.android.classicalmusicquiz;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;

public class QuizActivity extends AppCompatActivity implements View.OnClickListener,ExoPlayer.EventListener {

    private static final int CORRECT_ANSWER_DELAY_MILLIS = 1000;
    private static final String REMAINING_SONGS_KEY = "remaining_songs";
    private static final String TAG = QuizActivity.class.getSimpleName();
    private int[] mButtonIDs = {R.id.buttonA, R.id.buttonB, R.id.buttonC, R.id.buttonD};
    private ArrayList<Integer> mRemainingSampleIDs;
    private ArrayList<Integer> mQuestionSampleIDs;
    private int mAnswerSampleID;
    private int mCurrentScore;
    private int mHighScore;
    private Button[] mButtons;

    private NotificationManager mNotificationManager;
    private SimpleExoPlayerView mPlayerView;
    private SimpleExoPlayer mExoplayer;
    private static MediaSessionCompat mMediaSession;
    private PlaybackStateCompat.Builder mStateBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        mPlayerView = (SimpleExoPlayerView) findViewById(R.id.playerView);

        boolean isNewGame = !getIntent().hasExtra(REMAINING_SONGS_KEY);

        // If it's a new game, set the current score to 0 and load all samples.
        if (isNewGame) {
            QuizUtils.setCurrentScore(this, 0);
            mRemainingSampleIDs = Sample.getAllSampleIDs(this);
            // Otherwise, get the remaining songs from the Intent.
        } else {
            mRemainingSampleIDs = getIntent().getIntegerArrayListExtra(REMAINING_SONGS_KEY);
        }

        // Get current and high scores.
        mCurrentScore = QuizUtils.getCurrentScore(this);
        mHighScore = QuizUtils.getHighScore(this);

        // Generate a question and get the correct answer.
        mQuestionSampleIDs = QuizUtils.generateQuestion(mRemainingSampleIDs);
        mAnswerSampleID = QuizUtils.getCorrectAnswerID(mQuestionSampleIDs);

        // Load the image of the composer for the answer into the ImageView.
//        composerView.setImageBitmap(Sample.getComposerArtBySampleID(this, mAnswerSampleID));
        mPlayerView.setDefaultArtwork(BitmapFactory.decodeResource(this.getResources(),R.drawable.question_mark));

        // If there is only one answer left, end the game.
        if (mQuestionSampleIDs.size() < 2) {
            QuizUtils.endGame(this);
            finish();
        }

        // Initialize the buttons with the composers names.
        mButtons = initializeButtons(mQuestionSampleIDs);

        //Creating a MediaSession[6-7 Steps]
        mMediaSession = new MediaSessionCompat(this,TAG);
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS|MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setMediaButtonReceiver(null);

        mStateBuilder = new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY
                                                                    |PlaybackStateCompat.ACTION_PAUSE
                                                                    |PlaybackStateCompat.ACTION_PLAY_PAUSE
                                                                    |PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        mMediaSession.setPlaybackState(mStateBuilder.build());

        mMediaSession.setCallback(new mySessionCallback());

        mMediaSession.setActive(true);


        Sample answerSample = Sample.getSampleByID(this,mAnswerSampleID);

        if(answerSample==null){
            Toast.makeText(this,getString(R.string.sample_not_found_error),Toast.LENGTH_SHORT).show();
        }else{

            Uri sampleUri = Uri.parse(answerSample.getUri());
            initializePlayer(sampleUri);
        }



    }

    //to show MediaStyle notification ..called in the PlayerStateChanged method
    public void showNotification(PlaybackStateCompat state){

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        int icon;
        String play_pause;

        //if playing..i want action button to say pause
        if(state.getState()==PlaybackStateCompat.STATE_PLAYING){
            icon = R.drawable.exo_controls_pause;
            play_pause = getString(R.string.exo_controls_pause_description);
        }else{
            icon = R.drawable.exo_controls_play;
            play_pause= getString(R.string.exo_controls_play_description);
        }

        NotificationCompat.Action playPauseAction = new NotificationCompat.Action(icon,play_pause,
                            MediaButtonReceiver.buildMediaButtonPendingIntent(this,PlaybackStateCompat.ACTION_PLAY_PAUSE));

        NotificationCompat.Action restartAction = new NotificationCompat.Action(R.drawable.exo_controls_previous,
                                                        getString(R.string.restart),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));

        PendingIntent contentPendingIntent = PendingIntent.getActivity(this,0,new Intent(this,QuizActivity.class),0);

        builder.setContentTitle(getString(R.string.guess))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(contentPendingIntent)
                .setSmallIcon(R.drawable.question_mark)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(playPauseAction)
                .addAction(restartAction)
                .setStyle(new android.support.v7.app.NotificationCompat.MediaStyle()
                                                                            .setMediaSession(mMediaSession.getSessionToken())
                                                                            .setShowActionsInCompactView(0,1));


        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationManager.notify(0,builder.build());

    }


    public void initializePlayer(Uri mediaUri){
        if(mExoplayer==null){

            mExoplayer = ExoPlayerFactory.newSimpleInstance(this,
                    new DefaultTrackSelector()
                    ,new DefaultLoadControl());

            mExoplayer.addListener(this);
            mPlayerView.setPlayer(mExoplayer);

            //preparing media source
            String userAgent = Util.getUserAgent(this,"ClassicalMusicQuiz");
            MediaSource mediaSource = new ExtractorMediaSource(mediaUri,
                    new DefaultDataSourceFactory(this,userAgent),
                    new DefaultExtractorsFactory(),
                    null,
                    null);
            mExoplayer.prepare(mediaSource);
            mExoplayer.setPlayWhenReady(true);

        }
    }


    /**
     * Initializes the button to the correct views, and sets the text to the composers names,
     * and set's the OnClick listener to the buttons.
     *
     * @param answerSampleIDs The IDs of the possible answers to the question.
     * @return The Array of initialized buttons.
     */
    private Button[] initializeButtons(ArrayList<Integer> answerSampleIDs) {
        Button[] buttons = new Button[mButtonIDs.length];
        for (int i = 0; i < answerSampleIDs.size(); i++) {
            Button currentButton = (Button) findViewById(mButtonIDs[i]);
            Sample currentSample = Sample.getSampleByID(this, answerSampleIDs.get(i));
            buttons[i] = currentButton;
            currentButton.setOnClickListener(this);
            if (currentSample != null) {
                currentButton.setText(currentSample.getComposer());
            }
        }
        return buttons;
    }


    /**
     * The OnClick method for all of the answer buttons. The method uses the index of the button
     * in button array to to get the ID of the sample from the array of question IDs. It also
     * toggles the UI to show the correct answer.
     *
     * @param v The button that was clicked.
     */
    @Override
    public void onClick(View v) {

        // Show the correct answer.
        showCorrectAnswer();

        // Get the button that was pressed.
        Button pressedButton = (Button) v;

        // Get the index of the pressed button
        int userAnswerIndex = -1;
        for (int i = 0; i < mButtons.length; i++) {
            if (pressedButton.getId() == mButtonIDs[i]) {
                userAnswerIndex = i;
            }
        }

        // Get the ID of the sample that the user selected.
        int userAnswerSampleID = mQuestionSampleIDs.get(userAnswerIndex);

        // If the user is correct, increase there score and update high score.
        if (QuizUtils.userCorrect(mAnswerSampleID, userAnswerSampleID)) {
            mCurrentScore++;
            QuizUtils.setCurrentScore(this, mCurrentScore);
            if (mCurrentScore > mHighScore) {
                mHighScore = mCurrentScore;
                QuizUtils.setHighScore(this, mHighScore);
            }
        }

        // Remove the answer sample from the list of all samples, so it doesn't get asked again.
        mRemainingSampleIDs.remove(Integer.valueOf(mAnswerSampleID));

        // Wait some time so the user can see the correct answer, then go to the next question.
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                mExoplayer.stop();

                Intent nextQuestionIntent = new Intent(QuizActivity.this, QuizActivity.class);
                nextQuestionIntent.putExtra(REMAINING_SONGS_KEY, mRemainingSampleIDs);
                finish();
                startActivity(nextQuestionIntent);
            }
        }, CORRECT_ANSWER_DELAY_MILLIS);

    }

    /**
     * Disables the buttons and changes the background colors to show the correct answer.
     */
    private void showCorrectAnswer() {
        for (int i = 0; i < mQuestionSampleIDs.size(); i++) {
            int buttonSampleID = mQuestionSampleIDs.get(i);

            //changing default question mark to the picture of the composer after answering
            mPlayerView.setDefaultArtwork(Sample.getComposerArtBySampleID(this,mAnswerSampleID));

            mButtons[i].setEnabled(false);
            if (buttonSampleID == mAnswerSampleID) {
                mButtons[i].getBackground().setColorFilter(ContextCompat.getColor
                                (this, android.R.color.holo_green_light),
                        PorterDuff.Mode.MULTIPLY);
                mButtons[i].setTextColor(Color.WHITE);
            } else {
                mButtons[i].getBackground().setColorFilter(ContextCompat.getColor
                                (this, android.R.color.holo_red_light),
                        PorterDuff.Mode.MULTIPLY);
                mButtons[i].setTextColor(Color.WHITE);

            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mNotificationManager.cancelAll();
        mMediaSession.setActive(false);
        mExoplayer.removeListener(this);
        mExoplayer.release();
        mExoplayer = null;
    }
    
    //~~~~~~~~~~~~~~~ExoPlayer.EventListener Implementations

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

        //keeping the media session in sync
        if((playbackState==ExoPlayer.STATE_READY) && playWhenReady){
            mStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,mExoplayer.getCurrentPosition(),1f);

        }else if(playbackState==ExoPlayer.STATE_READY){
            mStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,mExoplayer.getCurrentPosition(),1f);
        }

        mMediaSession.setPlaybackState(mStateBuilder.build());
        showNotification(mStateBuilder.build());
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }

    @Override
    public void onPositionDiscontinuity() {

    }






    //Used in setting callbacks of MediaSession
    private class mySessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            super.onPlay();
            mExoplayer.setPlayWhenReady(true);
        }

        @Override
        public void onPause() {
            super.onPause();
            mExoplayer.setPlayWhenReady(false);
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            mExoplayer.seekTo(0);
        }
    }

    public static class MediaReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            MediaButtonReceiver.handleIntent(mMediaSession,intent);
        }
    }
}
