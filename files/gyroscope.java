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
    private GyroscopeDataDAO gyroDAO;
    private static final String TAG = "Gyroscope-Service";
    public static boolean ableToNotify = true;
    public float last_gyro_reading = 0f;
    public static final float kFilteringFactor = 0.1f;

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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mSensorManager.registerListener(this, mSensor, 50000);
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
        //high-pass filter to eliminate gravity
        last_gyro_reading = event.values[1] * kFilteringFactor + last_gyro_reading * (1.0f - kFilteringFactor);
        gyroValue = abs(event.values[1] - last_gyro_reading);

        Log.i(TAG, "Valor do giroscopio: " + String.valueOf(gyroValue));
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
}
