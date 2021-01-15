package com.dreamfish.audiorecord;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.dreamfish.record.AudioRecorder;
import com.dreamfish.record.RecordStreamListener;
import com.mobisys.asr.AsrModel;
import com.mobisys.asr.AudioProcess;

import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import jaygoo.widget.wlv.WaveLineView;


public class MainActivity extends Activity implements View.OnClickListener {
    WaveLineView wave;
    AudioRecorder audioRecorder;
    RecordStreamListener listener;
    ImageView ivStart, ivPause;
    TextView asrText;
    AsrModel model;
    Thread asrThread;
    long place = 0;
    final ArrayList<Short> audioData = new ArrayList<>();
    final int ADD_TEXT = 0;
    final int RESET_TEXT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        addListener();
        verifyPermissions(this);
    }

    //申请录音权限
    private static final int GET_RECODE_AUDIO = 1;
    private static String[] PERMISSION_ALL = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };

    /**
     * 申请录音权限
     */
    public static void verifyPermissions(Activity activity) {
        boolean permission = (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                || (ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED);
        if (permission) {
            ActivityCompat.requestPermissions(activity, PERMISSION_ALL,
                    GET_RECODE_AUDIO);
        }
    }

    private void addListener() {
        ivStart.setOnClickListener(this);
        ivPause.setOnClickListener(this);

        listener = new RecordStreamListener() {
            @Override
            public void recordOfByte(byte[] data, int begin, int end) {
                //TODO 获取到的byte数组的数据在这里使用
                //存储接收到的数据到audioData
                short[] audio = Utils.bytesToShorts(data);
                for (short value : audio) {
                    audioData.add(value);
                }
            }
        };
    }

    private void init() {
        ivStart = findViewById(R.id.iv_start);
        ivPause = findViewById(R.id.iv_pause);
        asrText = findViewById(R.id.asr_text);
        wave = findViewById(R.id.waveLineView);
        wave.setBackGroundColor(Color.TRANSPARENT);
        wave.setLineColor(getColor(R.color.colorPrimaryDark));
        wave.setVolume(75);

        model = new AsrModel();
        model.initModel(this);
        asrThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    synchronized (audioData) {
                        long length = audioData.size();
                        if (length - place > 32000   //暂定每2秒处理一次
                                || length > 0 && audioRecorder.getStatus() == AudioRecorder.Status.STATUS_PAUSE) {  // 录音结束，长度不够
                            short[] audio = AsrModel.clipArrayToListShort(audioData, place, length);
                            String str = model.recognize(audio);
                            sendMsg(ADD_TEXT, str);
                            place = length;
                        }
                    }
                }
            }
        });
        asrThread.start();

        audioRecorder = AudioRecorder.getInstance();
    }

    private void resetPara() {
        place = 0;
        synchronized (audioData) {  //避免上次的还在识别，这里就给清空了，会有卡顿(上次录制太长，会卡很久，主要原因，处理速度太慢)，但不至于闪退
            audioData.clear();
        }
        asrText.setText("");
    }

    private void sendMsg(int what, String text) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.obj = text;
        handler.sendMessage(msg);
    }

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case ADD_TEXT:
                    String content = asrText.getText().toString() + msg.obj;
                    asrText.setText(content);
                    break;
                case RESET_TEXT:
                    asrText.setText(msg.obj.toString());
                    break;
            }
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_start:
                try {
                    if (audioRecorder.getStatus() == AudioRecorder.Status.STATUS_NO_READY) {
                        // 重置参数
                        resetPara();
                        //初始化录音
                        String fileName = new SimpleDateFormat("yyyyMMddhhmmss").format(new Date());
                        wave.startAnim();
                        audioRecorder.createDefaultAudio(fileName);
                        audioRecorder.startRecord(listener);

                        ivStart.setImageDrawable(getDrawable(R.drawable.ic_media_stop));
                        ivPause.setVisibility(View.VISIBLE);
                    } else {
                        //停止录音
                        wave.stopAnim();
                        audioRecorder.stopRecord();
                        ivStart.setImageDrawable(getDrawable(R.drawable.ic_media_start));
                        ivPause.setImageDrawable(getDrawable(R.drawable.ic_media_pause));
                        ivPause.setVisibility(View.GONE);
                    }
                } catch (IllegalStateException e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.iv_pause:
                try {
                    if (audioRecorder.getStatus() == AudioRecorder.Status.STATUS_START) {
                        //暂停录音
                        wave.stopAnim();
                        audioRecorder.pauseRecord();
                        ivPause.setImageDrawable(getDrawable(R.drawable.ic_media_cotinue));
//                        pause.setText("继续录音");
                        break;

                    } else {
                        wave.startAnim();
                        audioRecorder.startRecord(listener);
                        ivPause.setImageDrawable(getDrawable(R.drawable.ic_media_pause));
//                        pause.setText("暂停录音");
                    }
                } catch (IllegalStateException e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        wave.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        wave.onPause();
        if (audioRecorder.getStatus() == AudioRecorder.Status.STATUS_START) {
            audioRecorder.pauseRecord();
            ivPause.setImageDrawable(getDrawable(R.drawable.ic_media_cotinue));
        }

    }

    @Override
    protected void onDestroy() {
        audioRecorder.release();
        wave.release();
        super.onDestroy();
    }
}
