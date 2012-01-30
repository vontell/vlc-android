/*****************************************************************************
 * AudioServiceController.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.android;

import java.util.ArrayList;
import java.util.List;

import org.videolan.vlc.android.AudioPlayer.AudioPlayerControl;
import org.videolan.vlc.android.AudioPlayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

public class AudioServiceController implements AudioPlayerControl {
    public static final String TAG = "VLC/AudioServiceContoller";

    private static AudioServiceController mInstance;
    private static boolean mIsBound = false;
    private Context mContext;
    private IAudioService mAudioServiceBinder;
    private ServiceConnection mAudioServiceConnection;
    private ArrayList<AudioPlayer> mAudioPlayer;
    private IAudioServiceCallback mCallback = new IAudioServiceCallback.Stub() {
        @Override
        public void update() throws RemoteException {
            updateAudioPlayer();
        }
    };

    private AudioServiceController() {

        // Get context from MainActivity
        mContext = MainActivity.getInstance();

        mAudioPlayer = new ArrayList<AudioPlayer>();

        // Setup audio service connection
        mAudioServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "Service Disconnected");
                mAudioServiceBinder = null;
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "Service Connected");
                mAudioServiceBinder = IAudioService.Stub.asInterface(service);

                // Register controller to the service
                try {
                    mAudioServiceBinder.addAudioCallback(mCallback);
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.getInstance());
                    boolean enableHS = prefs.getBoolean("enable_headset_detection", true);
                    AudioServiceController.getInstance().detectHeadset(enableHS);
                } catch (RemoteException e) {
                    Log.e(TAG, "remote procedure call failed: addAudioCallback()");
                }
                updateAudioPlayer();
            }
        };
    }

    public static AudioServiceController getInstance() {
        if (mInstance == null) {
            mInstance = new AudioServiceController();
        }
        if (!mIsBound) {
            mInstance.bindAudioService();
        }
        return mInstance;
    }

    public void load(List<String> mediaPathList, int position) {
        try {
            mAudioServiceBinder.load(mediaPathList, position);
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: load()");
        }
    }

    /**
     * Bind to audio service if it is running
     */
    public void bindAudioService() {
        if (mAudioServiceBinder == null) {
            Intent service = new Intent(mContext, AudioService.class);
            mContext.startService(service);
            mIsBound = mContext.bindService(service, mAudioServiceConnection, Context.BIND_AUTO_CREATE);
        } else {
            // Register controller to the service
            try {
                mAudioServiceBinder.addAudioCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "remote procedure call failed: addAudioCallback()");
            }
        }
    }

    public void unbindAudioService() {
        if (mAudioServiceBinder != null) {
            try {
                mAudioServiceBinder.removeAudioCallback(mCallback);
                if (mIsBound) {
                    mContext.unbindService(mAudioServiceConnection);
                    mIsBound = false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "remote procedure call failed: removeAudioCallback()");
            }

        }
    }

    /**
     * Add a AudioPlayer
     * @param ap
     */
    public void addAudioPlayer(AudioPlayer ap) {
        mAudioPlayer.add(ap);
    }

    /**
     * Remove AudioPlayer from list
     * @param ap
     */
    public void removeAudioPlayer(AudioPlayer ap) {
        if (mAudioPlayer.contains(ap)) {
            mAudioPlayer.remove(ap);
        }
    }

    /**
     * Update all AudioPlayer
     */
    private void updateAudioPlayer() {
        for (int i = 0; i < mAudioPlayer.size(); i++)
            mAudioPlayer.get(i).update();
    }

    public void stop() {
        if (mAudioServiceBinder == null)
            return;
        try {
            mAudioServiceBinder.stop();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: stop()");
        }
        updateAudioPlayer();
    }

    @Override
    public String getAlbum() {
        String album = null;
        try {
            album = mAudioServiceBinder.getAlbum();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: getAlbum()");
        }
        return album;
    }

    @Override
    public String getArtist() {
        String artist = null;
        try {
            artist = mAudioServiceBinder.getArtist();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: getArtist()");
        }
        return artist;
    }

    @Override
    public String getTitle() {
        String title = null;
        try {
            title = mAudioServiceBinder.getTitle();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: getTitle()");
        }
        return title;
    }

    @Override
    public boolean isPlaying() {
        boolean playing = false;
        if (mAudioServiceBinder != null) {
            try {
                playing = (hasMedia() && mAudioServiceBinder.isPlaying());

            } catch (RemoteException e) {
                Log.e(TAG, "remote procedure call failed: isPlaying()");
            }
        }
        return playing;
    }

    @Override
    public void pause() {
        try {
            mAudioServiceBinder.pause();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: pause()");
        }
        updateAudioPlayer();
    }

    @Override
    public void play() {
        try {
            mAudioServiceBinder.play();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: play()");
        }
        updateAudioPlayer();
    }

    @Override
    public boolean hasMedia() {
        if (mAudioServiceBinder != null) {
            try {
                return mAudioServiceBinder.hasMedia();
            } catch (RemoteException e) {
                Log.e(TAG, "remote procedure call failed: hasMedia()");
            }
        }
        return false;
    }

    @Override
    public int getLength() {
        try {
            return mAudioServiceBinder.getLength();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: getLength()");
        }
        return 0;
    }

    @Override
    public int getTime() {
        try {
            return mAudioServiceBinder.getTime();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: getTime()");
        }
        return 0;
    }

    @Override
    public Bitmap getCover() {
        try {
            return mAudioServiceBinder.getCover();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: getCover()");
            return null;
        }
    }

    @Override
    public void next() {
        try {
            mAudioServiceBinder.next();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: next()");
        }
    }

    @Override
    public void previous() {
        try {
            mAudioServiceBinder.previous();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: previous()");
        }
    }

    public void setTime(long time) {
        try {
            mAudioServiceBinder.setTime(time);
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: setTime()");
        }
    }

    @Override
    public boolean hasNext() {
        try {
            return mAudioServiceBinder.hasNext();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: hasNext()");
        }
        return false;
    }

    @Override
    public boolean hasPrevious() {
        try {
            return mAudioServiceBinder.hasPrevious();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: hasPrevious()");
        }
        return false;
    }

    @Override
    public void shuffle() {
        try {
            mAudioServiceBinder.shuffle();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: shuffle()");
        }
    }

    @Override
    public void setRepeatType(RepeatType t) {
        try {
            mAudioServiceBinder.setRepeatType(t.ordinal());
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: repeat()");
        }
    }

    @Override
    public boolean isShuffling() {
        try {
            return mAudioServiceBinder.isShuffling();
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: isShuffling()");
            return false;
        }
    }

    @Override
    public RepeatType getRepeatType() {
        try {
            return RepeatType.values()[mAudioServiceBinder.getRepeatType()];
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: isRepeating()");
            return RepeatType.None;
        }
    }

    @Override
    public void detectHeadset(boolean enable) {
        try {
            mAudioServiceBinder.detectHeadset(enable);
        } catch (RemoteException e) {
            Log.e(TAG, "remote procedure call failed: detectHeadset()");
        }
    }
}
