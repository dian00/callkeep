package io.wazo.callkeep.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;

import android.telecom.Connection;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Calendar;

import io.wazo.callkeep.R;
import io.wazo.callkeep.VoiceConnection;
import io.wazo.callkeep.VoiceConnectionService;
import io.wazo.callkeep.activity.listener.DebouncedOnClickListener;

public class OutgoingCallActivity extends Activity {
    public static final String EXTRA_KEY_PHONE_NUMBER = "extra_key_phone_number";
    public static final String EXTRA_KEY_USER_ID = "extra_key_user_id";
    public static final String EXTRA_KEY_USER_SIM_IMSI = "extra_key_user_sim_imsi";


    private static final int REQUEST_PERMISSION = 19;

    private AudioManager mAudioManager;
    private String mProductName = "";

    private TextView mTextName;
    private TextView mTextPhoneNumber;
    private TextView mTextTimer;

    private View mTextWaitingBigMsg;
    private View mTextWaitingMsg;

    private View mContainerCallingBtn;
    private View mContainerWaitingBtn;

    private Button mBtnSpeak;
    private Button mBtnBluetooth;
    private String mPhoneNumber;
    private PowerManager.WakeLock mProximityWakeLock;

    private long mStartTime;
    private Handler mHandler = new Handler();

    private BluetoothAdapter mBluetoothAdapter;

    // junseo2
    private boolean timeRunning = false;


    private String mUserId;
    private String mUserSimImsi;

    @SuppressLint("DefaultLocale")
    private Runnable mTimerRunnable = new Runnable() {
        @Override
        public void run() { // todo junseo2 여기가 시간을 보여주곳 같은데... 문제가 있나...
            long now = Calendar.getInstance().getTimeInMillis();
            Log.d("junseo2", "start: "+mStartTime+", now: "+now);

            long time = (now - mStartTime) / 1000;
            Log.d("junseo2", "time: "+time);

            long min = time / 60;
            long sec = time % 60;
            long hour = min / 60;


            String strTime = String.format("%02d : %02d : %02d", hour, min, sec);
            mTextTimer.setText(strTime);

            mHandler.postDelayed(mTimerRunnable, 1000L);
        }
    };

    boolean noInsert = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_outgoing_call);
        init();

        Connection connection = VoiceConnectionService.getConnection("UUID");

    }

    private void init() {
        initWakeLock();

        mTextName = findViewById(R.id.text_name);
        mTextPhoneNumber = findViewById(R.id.text_phone_number);
        mTextTimer = findViewById(R.id.text_timer);

        mTextWaitingBigMsg = findViewById(R.id.text_waiting_big_msg);
        mTextWaitingMsg = findViewById(R.id.text_waiting_message);

        mContainerCallingBtn = findViewById(R.id.container_calling);
        mContainerWaitingBtn = findViewById(R.id.container_waiting);
        mBtnSpeak = findViewById(R.id.btn_speak);
        mBtnBluetooth = findViewById(R.id.btn_blue_tooth);

        switchCallingView(false);

        initAudioManager();

        initListener();

        timeRunning = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Please allow permissions.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
            }

            init();
        }
    }

    private void initWindowFlag() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
//            window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    private void initWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mProximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, getLocalClassName());
        } else {
            try {
                int proximityScreenOffWakeLock = PowerManager.class.getClass().getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").getInt(null);
                if (proximityScreenOffWakeLock != 0x0) {
                    mProximityWakeLock = powerManager.newWakeLock(proximityScreenOffWakeLock, getLocalClassName());
                    mProximityWakeLock.setReferenceCounted(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initAudioManager() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mAudioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        if (isBluetoothAvailable()) {
            mAudioManager.startBluetoothSco();
            mAudioManager.setBluetoothScoOn(true);
        }

        mBtnBluetooth.setBackgroundResource(mAudioManager.isBluetoothScoOn() ?
                R.drawable.call_btn_bluetooth_on :
                R.drawable.call_btn_bluetooth_off);
    }


    private boolean isBluetoothAvailable() {
        if (mAudioManager != null) {
            if (mBluetoothAdapter != null &&
                    mBluetoothAdapter.isEnabled() &&
                    mBluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED &&
                    mBluetoothAdapter.getBondedDevices() != null &&
                    mBluetoothAdapter.getBondedDevices().size() > 0) {
                return true;
            }
        }
        return false;
    }

    private void initListener() {
        mBtnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeToSpeakMode();
            }
        });

        findViewById(R.id.btn_cancel_calling).setOnClickListener(new DebouncedOnClickListener() {
            @Override
            public void onDebouncedClick(View view) {
            }
        });

        findViewById(R.id.btn_cancel_waiting).setOnClickListener(new DebouncedOnClickListener() {
            @Override
            public void onDebouncedClick(View view) {
            }
        });
//
        mBtnBluetooth.setOnClickListener(new DebouncedOnClickListener() {
            @Override
            public void onDebouncedClick(View v) {
                if (isBluetoothAvailable()) {
                    changeToBlueTooth();
                } else {
                    Toast.makeText(getApplicationContext(), "R.string.msg_there_is_no_paired_bluetooth", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void changeToSpeakMode() {
        if (mAudioManager != null) {
            if (mAudioManager.isSpeakerphoneOn()) {
                mAudioManager.setSpeakerphoneOn(false);
                mAudioManager.stopBluetoothSco();
                mAudioManager.setBluetoothScoOn(false);

                mBtnSpeak.setBackgroundResource(R.drawable.call_btn_call_speaker_off);
            } else {
                mAudioManager.setSpeakerphoneOn(true);
                mBtnSpeak.setBackgroundResource(R.drawable.call_btn_call_speaker_on);
            }

            mBtnBluetooth.setBackgroundResource(mAudioManager.isBluetoothScoOn() ?
                    R.drawable.call_btn_bluetooth_on :
                    R.drawable.call_btn_bluetooth_off);
        }
    }

    private void changeToBlueTooth() {
        if (mAudioManager != null) {
            if (isBluetoothAvailable()) {
                if (mAudioManager.isBluetoothScoOn()) {
                    mAudioManager.setSpeakerphoneOn(false);
                    mAudioManager.stopBluetoothSco();
                    mAudioManager.setBluetoothScoOn(false);

                    mBtnBluetooth.setBackgroundResource(R.drawable.call_btn_bluetooth_off);
                } else {
                    mAudioManager.setSpeakerphoneOn(false);
                    mAudioManager.startBluetoothSco();
                    mAudioManager.setBluetoothScoOn(true);

                    mBtnBluetooth.setBackgroundResource(R.drawable.call_btn_bluetooth_on);
                }
            } else {
                Toast.makeText(getApplicationContext(), "R.string.msg_there_is_no_paired_bluetooth", Toast.LENGTH_SHORT).show();
            }

            mBtnSpeak.setBackgroundResource(mAudioManager.isSpeakerphoneOn() ?
                    R.drawable.call_btn_call_speaker_on :
                    R.drawable.call_btn_call_speaker_off);
        }
    }


    private void switchCallingView(boolean hasToSwitch) {
        if (hasToSwitch) {
            mContainerCallingBtn.setVisibility(View.VISIBLE);
            mTextName.setVisibility(View.VISIBLE);
            mTextPhoneNumber.setVisibility(View.VISIBLE);
            mTextTimer.setVisibility(View.VISIBLE);

            mContainerWaitingBtn.setVisibility(View.GONE);
            mTextWaitingBigMsg.setVisibility(View.GONE);
            mTextWaitingMsg.setVisibility(View.GONE);

            startTimer();
        } else {
            mContainerCallingBtn.setVisibility(View.VISIBLE);
            mTextName.setVisibility(View.GONE);
            mTextPhoneNumber.setVisibility(View.GONE);
            mTextTimer.setVisibility(View.GONE);

            mContainerWaitingBtn.setVisibility(View.GONE);
            mTextWaitingBigMsg.setVisibility(View.VISIBLE);
            mTextWaitingMsg.setVisibility(View.VISIBLE);
        }
    }

    private void startTimer() {
        if(timeRunning == false) {
            String strTime = String.format("%02d : %02d : %02d", 0, 0, 0);
            mTextTimer.setText(strTime);

            mStartTime = Calendar.getInstance().getTimeInMillis();
            mHandler.postDelayed(mTimerRunnable, 1000L);
            timeRunning = true;
        }
    }

    private void stopTimer() {
        try {
            mHandler.removeCallbacks(mTimerRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        Log.i(getClass().getSimpleName(), "moveTaskToBack!");

        moveTaskToBack(true);
    }

}