package cc.koumakan.spaceplayer.service;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;

import cc.koumakan.spaceplayer.R;
import cc.koumakan.spaceplayer.entity.Music;
import cc.koumakan.spaceplayer.view.MainActivity;
import cc.koumakan.spaceplayer.view.PlayerNotification;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerService extends Service {
    private PlayerNotification playerNotification;
    private IBinder binder = new PlayerService.LocalBinder();

    public class LocalBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }

        public Map<String, Vector<Music>> getPlayList() {
            return playList;
        }
    }

    public IBinder onBind(Intent intent) {
        playerNotification = new PlayerNotification(this);
        playerNotification.init();
        return binder;
    }

    public void onCreate() {
        super.onCreate();
        playList = new ConcurrentHashMap<String, Vector<Music>>();
        if (mediaDecoder == null) {
            mediaDecoder = new MediaDecoder();
            mediaDecoder.setOnCompletionListener(new mOnComplationListener());
        }
        currentID = 0;
        timer.scheduleAtFixedRate(timerTask, 0, 50);
        timerTask.run();
    }


    private Handler mainHandler = null;

    public void setMainHandler(Handler mainHandler) {
        this.mainHandler = mainHandler;
    }

    /**
     * 定义计时器
     */
    Timer timer = new Timer();
    TimerTask timerTask = new TimerTask() {
        private int count = 0;
        @Override
        public void run() {
            if (mainHandler != null && currentList != null) {
                Message msg = new Message();
                count = (count+1)%20;
                if(count == 0) {

                    Bundle data = new Bundle();

                    Music music = currentList.elementAt(currentID);
                    data.putString("TITLE", music.title);
                    data.putString("ARTIST", music.artist);
                    data.putString("ALBUM", music.album);
                    data.putString("ALBUM_IMAGE", music.albumImage);
                    int time = 0;
                    if (!isIdle) {
                        time = mediaDecoder.getCurrentPosition();
                    }
                    data.putInt("TIME", time);
                    data.putInt("DURATION", music.duration);
                    data.putBoolean("PLAYING", isPlaying);

                    msg.what = 1;
                    msg.setData(data);
                }
                else{
                    msg.what = 2;
                }
                mainHandler.sendMessage(msg);
            }
        }
    };

    private MediaDecoder mediaDecoder = null;//解码器
    private Map<String, Vector<Music>> playList;//播放列表

    private Vector<Music> currentList = null;//当前播放列表
    private int currentID = 0;//当前播放歌曲ID
    /**
     * Idle 为 new 或 reset 后的初始状态
     * End 为 release 后的结束状态
     * 二者中间为 MediaPlayer 的生命周期
     */
    private boolean isIdle = true;
    private boolean isPlaying = false;

    public Boolean getIsPlaying() {
        return isPlaying;
    }

    public void setCurrentList(Vector<Music> currentList) {
        this.currentList = currentList;
    }

    public Music getCurrentMusic(){
        if(currentList == null) return null;
        return currentList.get(currentID);
    }

    /**
     * 解码器载入文件
     */
    public void load(String source) throws IOException {
        mediaDecoder.setDataSource(source);
    }

    /**
     * 播放
     **/
    public void play() throws IOException {
        if (isIdle) {
            load(currentList.elementAt(currentID).data);
            mediaDecoder.prepare();
            isIdle = false;
        }
        mediaDecoder.start();
        isPlaying = true;
        timerTask.run();
    }

    /**
     * 暂停
     */
    public void pause() {
        if (isPlaying) {
            mediaDecoder.pause();
            isPlaying = false;
        }
        timerTask.run();
    }

    /**
     * 跳转至某时刻
     */
    public void seek(double rate) throws IOException {
        if (isIdle) {
            play();
            mediaDecoder.seekTo((int) (rate * mediaDecoder.getDuration()));
            pause();
        } else {
            mediaDecoder.seekTo((int) (rate * mediaDecoder.getDuration()));
        }
        timerTask.run();
    }

    /**
     * 下一首
     */
    public void next() throws IOException {
        if (!isIdle) {
            mediaDecoder.stop();
            mediaDecoder.reset();
            isIdle = true;
        }
        currentID = (int) (Math.random() * Integer.MAX_VALUE) % currentList.size();
//        currentID++;
        play();
    }

    /**
     * 上一首
     */
    public void previous() throws IOException {
        if (!isIdle) {
            mediaDecoder.stop();
            mediaDecoder.reset();
            isIdle = true;
        }
        currentID = (int) (Math.random() * Integer.MAX_VALUE) % currentList.size();
//        currentID--;
        play();
    }

    /**
     * 播放结束监听器
     */
    class mOnComplationListener implements MediaPlayer.OnCompletionListener {

        public void onCompletion(MediaPlayer mediaPlayer) {
            if (mediaPlayer.equals(mediaDecoder)) {
                try {
                    next();
                } catch (IOException e) {
                    mediaDecoder.start();
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        mediaDecoder.release();
        mediaDecoder = null;
        timer.cancel();
        timerTask.cancel();
        playerNotification.cancel();
        super.onDestroy();
    }
}
