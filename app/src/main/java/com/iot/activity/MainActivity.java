package com.iot.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.iot.gmartin.alarmaiot_soa.R;
import com.google.gson.Gson;
import com.iot.common.Constants;
import com.iot.common.UrlBuilder;
import com.iot.rest.Callback;
import com.iot.rest.NetworkTask;
import com.iot.dto.TemperatureLimits;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Pantalla principal
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private RelativeLayout rl;
    private Switch switchOnOff;
    private Button btnConfig;
    private TextView textTemperature;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    private Timer timer;

    private boolean alarmOn;
    private boolean alreadyVibrated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        this.rl = (RelativeLayout) findViewById(R.id.layout_main);
        this.mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        this.mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        this.btnConfig = (Button) findViewById(R.id.btn_config);
        this.textTemperature = (TextView) findViewById(R.id.text_temperature);
        this.switchOnOff = (Switch) findViewById(R.id.switch_on_off);

        initButtons();
        initTimer();

        reset();
    }

    /**
     * Configura los botones de navegación y encendido / apagado de la alarma
     */
    private void initButtons() {
        this.switchOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                turnAlarm(isChecked);
            }
        });
        this.btnConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, ConfigActivity.class);
                startActivity(intent);
            }
        });
    }

    /**
     * Inicia el timer que consulta la temperatura cada 1500 milisegundos
     */
    private void initTimer() {
        this.timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                execute();
            }
        };
        this.timer.schedule(timerTask, 0, 1500);
    }

    /**
     * Ejecuta la consulta y actualización segun datos en la alarma
     */
    private void execute() {
        new NetworkTask(new Callback() {
            @Override
            public void run(String result) {
                boolean hasResult = result != null;
                if (!hasResult) {
                    if (alarmOn) {
                        reset();
                    }
                } else {
                    boolean turning = Boolean.valueOf(result);
                    if (alarmOn != turning) {
                        switchOnOff.setChecked(turning);
                        switchOnOff.setText(Constants.ALARM_ON_TEXT);
                    }
                    alarmOn = turning;
                    updateTemperature();
                }
                switchOnOff.setEnabled(hasResult);
                btnConfig.setEnabled(hasResult);
            }
        }).execute(UrlBuilder.build("alarm/status"));
    }

    /**
     * Obtiene la temperatura actual y la procesa
     */
    private void updateTemperature() {
        if (alarmOn) {
            new NetworkTask(new Callback() {
                @Override
                public void run(String temp) {
                    processTemperature(temp);
                }
            }).execute(UrlBuilder.build("temperature/read"));
        } else {
            reset();
        }
    }

    /**
     * Dada una temperatura, obtiene los limites establecidos y procesa ambos datos
     * @param temp
     */
    private void processTemperature(String temp) {
        final Double temperature = Double.valueOf(temp.toString());
        NumberFormat formatter = new DecimalFormat("#0.00");
        textTemperature.setText(formatter.format(temperature) + Constants.TEMPERATURE_CELSIUS);

        new NetworkTask(new Callback() {
            @Override
            public void run(String limits) {
                processLimitsAndTemperature(temperature, limits);
            }
        }).execute(UrlBuilder.build("temperature/limits"));
    }

    /**
     * Dados los límites y la temperatura, verifica en que rango se encuentra e invoca al método correspondiente
     * - cold() - FRIO
     * - warm() - TIBIO
     * - hot() - CALIENTE
     * @param temperature
     * @param limits
     */
    private void processLimitsAndTemperature(Double temperature, String limits) {
        TemperatureLimits temperatureLimits = new Gson().fromJson(limits, TemperatureLimits.class);
        if (temperature < temperatureLimits.getMin()) {
            cold();
        } else if (temperature >= temperatureLimits.getMin()
                && temperature <= temperatureLimits.getMax()) {
            warm();
        } else {
            hot();
        }
    }

    /**
     * Fondo: AZUL
     * Buzzer: ENCENDIDO
     * LED RGB: AZUL
     * LED Rojo: Intensidad BAJA
     * Vibración: CORTA
     */
    private void cold() {
        rl.setBackgroundColor(Color.rgb(0, 128, 255));
        if (!alreadyVibrated) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(500);
            alreadyVibrated = true;
        }
    }

    /**
     * Fondo: VERDE
     * Buzzer: APAGADO
     * LED RGB: VERDE
     * LED Rojo: Intensidad MEDIA
     * Vibración: -
     */
    private void warm() {
        rl.setBackgroundColor(Color.rgb(127, 255, 0));
        alreadyVibrated = false;
    }

    /**
     * Fondo: ROJO
     * Buzzer: ENCENDIDO
     * LED RGB: ROJO
     * LED Rojo: Intensidad ALTA
     * Vibración: LARGA
     */
    private void hot() {
        rl.setBackgroundColor(Color.rgb(250, 128, 114));
        if (!alreadyVibrated) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(1500);
            alreadyVibrated = true;
        }
    }

    /**
     * Actualiza el estado de la alarma (encendiendola o apagandola) dependiendo del parametro recibido
     * @param isOn
     */
    private void turnAlarm(final boolean isOn) {
        if(!isOn) {
            reset();
        }
        new NetworkTask().execute(UrlBuilder.build("alarm/" + (isOn == true ? "on" : "off")));
    }

    private void reset() {
        this.alarmOn = false;
        this.switchOnOff.setChecked(false);
        this.switchOnOff.setText(Constants.ALARM_OFF_TEXT);
        this.rl.setBackgroundColor(Color.WHITE);
        this.textTemperature.setText(Constants.TEMPERATURE_NONE);
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    /**
     * En caso de ser detectado un movimiento en el sensor de proximidad, se apaga la alarma.
     * @param event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values[0] == 0) {
            turnAlarm(false);
            switchOnOff.setChecked(false);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
