package lucas.com.meupossante;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateFormat;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.Queue;

import lucas.com.meupossante.DAO.GyroscopeDataDAO;
import lucas.com.meupossante.VO.GyroscopeDataVO;

import static java.lang.Math.abs;
import static java.lang.System.currentTimeMillis;

/**
 * Created by Lucas on 31/07/2017.
 */

public class Gyroscope extends IntentService implements SensorEventListener {
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Boolean founddeltaH = false;
    private double timestart;
    private double timeend;
    private String state = "no-bump";
    private float deltas = 0.05f;
    private float deltah = 0.07f;
    private float tbump = 1.5f;
    private float tnextdelay = 3.0f;
    public float gyroValue;
    private static final String TAG = "Gyroscope-Service";
    public static boolean ableToNotify = true;
    public float last_gyro_reading = 0f;
    public static final float kFilteringFactor = 0.1f;
    private float gyro_timestamp = 0;
    public Queue<Float> gyroQueue;

    public Gyroscope() {
        super("Gyroscope");
    }

    public static boolean isAbleToNotify() {
        return ableToNotify;
    }

    @Override
    public void onCreate(){
        Log.i(TAG, "Gyroscope create");
        super.onCreate();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        gyroDAO = new GyroscopeDataDAO(this);
        gyroQueue = new LinkedList<>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mSensorManager.registerListener(this, mSensor, 850);

        Log.i(TAG, "start command");
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy(){
        Log.i(TAG, "Destroy");
        mSensorManager.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Moving average filter
        if (gyroQueue.size() >= 60) {
            gyroQueue.poll();
        }
        gyroQueue.add(event.values[2]);

        gyroValue = average(gyroQueue);

        Log.i(TAG, "Valor do giroscópio: " + String.valueOf(gyroValue));
        if (gyroValue > deltah) {
            founddeltaH = true;
        }
        if (state == "no-bump" && gyroValue > deltas) {
            // Inicio da primeira alteracao
            timestart = currentTimeMillis() / 1000.0;
            state = "one-bump";
            founddeltaH = false;
            ableToNotify = false;
        } else if ((state == "one-bump" || state == "second-bump") && gyroValue < deltas) {
            timeend = currentTimeMillis() / 1000.0;
            if (timeend - timestart > tbump && founddeltaH) {
                if (state == "one-bump") {
                    // Fim da primeira alteracao. Aguardando outra alteracao.
                    state = "waiting-bump";
                    timestart = currentTimeMillis() / 1000.0;
                    ableToNotify = false;
                } else {
                    // Fim da segunda alteracao. Detectou mudanca de faixa.
                    state = "no-bump";
                    timestart = 0;
                    timeend = 0;
                    ableToNotify = true;
                }
            } else {
                if (state == "one-bump") {
                    // Nao houve alteracao. Volta ao estado inicial.
                } else {
                    // Segunda alteracao foi invalida. Detectou curva.
                }
                state = "no-bump";
                timestart = 0;
                timeend = 0;
                ableToNotify = true;
            }
        } else if (state == "waiting-bump") {
            timeend = currentTimeMillis() / 1000.0;
            if (timeend - timestart < tnextdelay && gyroValue > deltas) {
                // Inicio da segunda alteracao. Possivel mudanca de faixa.
                state = "second-bump";
                timestart = currentTimeMillis() / 1000.0;
                timeend = 0;
                founddeltaH = false;
                ableToNotify = false;
            } else if (timeend - timestart > tnextdelay) {
                // Nao houve segunda alteracao. Detectou curva.
                state = "no-bump";
                timestart = 0;
                timeend = 0;
                ableToNotify = true;
            }
            Log.i(TAG, "Intervalo de tempo: " + String.valueOf(timeend - timestart));
        }
        Log.i(TAG, "State: " + String.valueOf(state));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public float average(Queue<Float> queue) {
        float average, sum = 0f;

        for(float element : queue){
            sum += element;
        }
        average = sum / queue.size();
        return average;
    }
}
