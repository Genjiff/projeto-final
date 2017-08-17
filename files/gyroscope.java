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
            timestart = currentTimeMillis() / 1000.0;
            if (ableToNotify) {
                GyroscopeDataVO gyroData = new GyroscopeDataVO();
                SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");
                String date = sdfDate.format(timestart * 1000.0);

                gyroData.setTime(date);
                gyroData.setAbleToNotify("Nao");
                gyroData.setMoment("Inicio de curva");
                gyroDAO.insert(gyroData);
            }
            state = "one-bump";
            founddeltaH = false;
            ableToNotify = false;
            //fReadingView.setText(String.valueOf(timestart));
        } else if ((state == "one-bump" || state == "second-bump") && gyroValue < deltas) {
            timeend = currentTimeMillis() / 1000.0;
            //sReadingView.setText(String.valueOf(timeend));
            //diffView.setText(String.valueOf(timeend - timestart));
            if (timeend - timestart > tbump && founddeltaH) {
                if (state == "one-bump") {
                    //gyroTextView.append("Waiting Bump\n");
                    if (ableToNotify) {
                        GyroscopeDataVO gyroData = new GyroscopeDataVO();
                        SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");
                        String date = sdfDate.format(timeend * 1000.0);

                        gyroData.setTime(date);
                        gyroData.setAbleToNotify("Nao");
                        gyroData.setMoment("Esperando pelo inicio da mudanca de faixa");
                        gyroDAO.insert(gyroData);
                    }
                    state = "waiting-bump";
                    timestart = currentTimeMillis() / 1000.0;
                    ableToNotify = false;
                } else {
                    //gyroTextView.append("Lane Change\n");
                    if (!ableToNotify) {
                        GyroscopeDataVO gyroData = new GyroscopeDataVO();
                        SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");
                        String date = sdfDate.format(timeend * 1000.0);

                        gyroData.setTime(date);
                        gyroData.setAbleToNotify("Sim");
                        gyroData.setMoment("Fim da mudanca de faixa");
                        gyroDAO.insert(gyroData);
                    }
                    state = "no-bump";
                    timestart = 0;
                    timeend = 0;
                    ableToNotify = true;
                }
            } else {
                if (state == "one-bump") {
                    //gyroTextView.append("No Turn\n");
                    if (!ableToNotify) {
                        GyroscopeDataVO gyroData = new GyroscopeDataVO();
                        SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");
                        String date = sdfDate.format(timeend * 1000.0);

                        gyroData.setTime(date);
                        gyroData.setAbleToNotify("Sim");
                        gyroData.setMoment("Nao houve curva");
                        gyroDAO.insert(gyroData);
                    }
                } else {
                    //gyroTextView.append("Turn\n");
                    if (!ableToNotify) {
                        GyroscopeDataVO gyroData = new GyroscopeDataVO();
                        SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");
                        String date = sdfDate.format(timeend * 1000.0);

                        gyroData.setTime(date);
                        gyroData.setAbleToNotify("Sim");
                        gyroData.setMoment("Fim da curva");
                        gyroDAO.insert(gyroData);
                    }
                }
                state = "no-bump";
                timestart = 0;
                timeend = 0;
                ableToNotify = true;
            }
        } else if (state == "waiting-bump") {
            timeend = currentTimeMillis() / 1000.0;
            if (timeend - timestart < tnextdelay && gyroValue > deltas) {
                GyroscopeDataVO gyroData = new GyroscopeDataVO();
                SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");
                String date = sdfDate.format(timeend * 1000.0);

                gyroData.setTime(date);
                gyroData.setAbleToNotify("Nao");
                gyroData.setMoment("Inicio da mudanca de faixa");
                gyroDAO.insert(gyroData);
                state = "second-bump";
                timestart = currentTimeMillis() / 1000.0;
                timeend = 0;
                founddeltaH = false;
                ableToNotify = false;
                //fReadingView.setText(String.valueOf(timestart));
            } else if (timeend - timestart > tnextdelay) {
                //gyroTextView.append("Turn\n");
                if (!ableToNotify) {
                    GyroscopeDataVO gyroData = new GyroscopeDataVO();
                    SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");
                    String date = sdfDate.format(timeend * 1000.0);

                    gyroData.setTime(date);
                    gyroData.setAbleToNotify("Nao");
                    gyroData.setMoment("Fim da Curva");
                    gyroDAO.insert(gyroData);
                }
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
