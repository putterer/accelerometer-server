package de.putterer.accelerometer_server;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

	private static final int CALIBRATION_MEASUREMENTS = 10;

	private Button calibrateButton;
	private TextView statusTextView;

	private Sensor sensor;

	private float[] acceleration = new float[3];

	public static volatile float[] calibration = new float[3];

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		calibrateButton = findViewById(R.id.calibrateButton);
		statusTextView = findViewById(R.id.statusTextView);

		SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
//		sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		if(sensor == null) {
			Toast.makeText(this, "No linear acceleration sensor found", Toast.LENGTH_LONG).show();
			return;
		}

		sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);// delay could be reduced

		Server.init();
	}

	public void onCalibrateClicked(View view) {
		new Thread(() -> {
			try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
			calibration[0] = 0.0f;
			calibration[1] = 0.0f;
			calibration[2] = 0.0f;
			for(int i = 0;i < CALIBRATION_MEASUREMENTS;i++) {
				try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }
				calibration[0] += acceleration[0];
				calibration[1] += acceleration[1];
				calibration[2] += acceleration[2];
			}
			calibration[0] /= (float)CALIBRATION_MEASUREMENTS;
			calibration[1] /= (float)CALIBRATION_MEASUREMENTS;
			calibration[2] /= (float)CALIBRATION_MEASUREMENTS;

			runOnUiThread(() -> statusTextView.setVisibility(View.VISIBLE));
		}).start();

		calibrateButton.setVisibility(View.GONE);
	}


	@Override
	public void onSensorChanged(SensorEvent event) {
		acceleration[0] = event.values[0];
		acceleration[1] = event.values[1];
		acceleration[2] = event.values[2];
		updateStatus();

		Server.onAcceleration(acceleration);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	@SuppressLint("DefaultLocale")
	private void updateStatus() {
		statusTextView.setText(String.format("X: %.3f\nY: %.3f\nZ: %.3f\n\nCalibration\nX: %.3f\nY: %.3f\nZ: %.3f\n",
				acceleration[0],
				acceleration[1],
				acceleration[2],
				calibration[0],
				calibration[1],
				calibration[2]));
	}
}
