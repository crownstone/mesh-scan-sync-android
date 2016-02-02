package nl.dobots.crownstonehub;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.strongloop.android.loopback.AccessToken;
import com.strongloop.android.loopback.RestAdapter;
import com.strongloop.android.loopback.callbacks.VoidCallback;

import java.util.HashMap;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;
import nl.dobots.crownstonehub.cfg.Config;
import nl.dobots.crownstonehub.cfg.Settings;
import nl.dobots.loopback.CrownstoneRestAPI;
import nl.dobots.loopback.loopback.Beacon;
import nl.dobots.loopback.loopback.BeaconRepository;
import nl.dobots.loopback.loopback.User;
import nl.dobots.loopback.loopback.UserRepository;

/**
 * This example activity shows the use of the bluenet library. The library is first initialized,
 * which enables the bluetooth adapter. It shows the following steps:
 *
 * 1. How to initialize the library
 * 2. Scan for devices, and setting a scan device filter
 * 3. How to get the list of devices from the library, sorted by RSSI.
 *
 * For an example of how to read the current PWM state and how to power On, power Off, or toggle
 * the device switch, see ControlActivity.java
 * For an example of how to use the library together with a service, see MainActivityService.java
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class MainActivity extends AppCompatActivity {

	private static final String TAG = MainActivity.class.getCanonicalName();

	private BleExt _ble;

	private Button _btnScan;
	private ListView _lvScanList;
	private TextView _txtClosest;
	private Spinner _spFilter;

	private boolean _scanning = false;
	private BleDeviceList _bleDeviceList;
	private String _address;

	private static final int GUI_UPDATE_INTERVAL = 500;
	private long _lastUpdate;

	private RestAdapter _restAdapter;

	private UserRepository _userRepository;

	private Settings _settings;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		initUI();

		// create access point to the library and initialize the Bluetooth adapter.
		_ble = new BleExt();
		initializeBle();

		_settings = Settings.getInstance(getApplicationContext());

		if (!Config.OFFLINE) {
			_restAdapter = CrownstoneRestAPI.initializeApi(this);

			// TODO: this should not be necessary once the cloud is running correctly, but because
			// the heroku app is shutting down if it is idle for some time, the cloud loses the
			// access tokens, so to be save we login every time we start the app
			String username = _settings.getUsername();
			String password = _settings.getPassword();
			if (!(username.isEmpty() && password.isEmpty())) {
				final UserRepository userRepo = _restAdapter.createRepository(UserRepository.class);
				userRepo.getCurrentUserId();
				userRepo.loginUser(username, password, new UserRepository.LoginCallback() {

					@Override
					public void onSuccess(AccessToken token, User currentUser) {
						Log.i(TAG, token.getUserId() + ":" + currentUser.getId());
					}

					@Override
					public void onError(Throwable t) {
						Log.i(TAG, "error: ", t);
						startActivity(new Intent(MainActivity.this, LoginActivity.class));
						return;
					}
				});
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// finish has to be called on the library to release the objects if the library
		// is not used anymore
		_ble.destroy();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (!Config.OFFLINE) {
			_userRepository = CrownstoneRestAPI.getUserRepository();

			if (!_userRepository.isLoggedIn()) {
				startActivity(new Intent(this, LoginActivity.class));
				return;
			}
		}

	}

	private void initUI() {

		_btnScan = (Button) findViewById(R.id.btnScan);
		_btnScan.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				// using the scan filter, we can tell the library to return only specific device
				// types. we are currently distinguish between Crownstones, DoBeacons, iBeacons,
				// and FridgeBeacons
				BleDeviceFilter selectedItem = (BleDeviceFilter) _spFilter.getSelectedItem();
				_ble.setScanFilter(selectedItem);

				if (!_scanning) {
					startScan();
				} else {
					stopScan();
				}
			}
		});
		_btnScan.setEnabled(false);

		// create a spinner element with the device filter options
		_spFilter = (Spinner) findViewById(R.id.spFilter);
		_spFilter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BleDeviceFilter.values()));
		_spFilter.setSelection(1);

		// create an empty list to assign to the list view. this will be updated whenever a
		// device is scanned
		_bleDeviceList = new BleDeviceList();
		DeviceListAdapter adapter = new DeviceListAdapter(this, _bleDeviceList);

		_lvScanList = (ListView) findViewById(R.id.lvScanList);
		_lvScanList.setAdapter(adapter);
		_lvScanList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// stop scanning for devices. We can't scan and connect to a device at the same time.
				if (_scanning) {
					stopScan();
				}

				BleDevice device = _bleDeviceList.get(position);
				_address = device.getAddress();

				// start the control activity to switch the device
				Intent intent = new Intent(MainActivity.this, SinkActivity.class);
				intent.putExtra("address", _address);
				startActivity(intent);
			}
		});

		_txtClosest = (TextView) findViewById(R.id.txtClosest);
	}

	private void stopScan() {
		_btnScan.setText(getString(R.string.main_scan));
		// stop scanning for devices
		_ble.stopScan(new IStatusCallback() {
			@Override
			public void onSuccess() {
				_scanning = false;
			}

			@Override
			public void onError(int error) {
				// nada
			}
		});
	}

	private void startScan() {
		_btnScan.setText(getString(R.string.main_stop_scan));
		// start scanning for devices. the scan will run at the highest frequency until stopScan
		// is called again. results are coming in as fast as possible. If you're concerned about
		// battery consumption, use the example with the BleScanService instead.
		_scanning = _ble.startScan(new IBleDeviceCallback() {
			@Override
			public void onDeviceScanned(BleDevice device) {
				// called whenever a device was scanned. the library keeps track of the scanned devices
				// and updates average rssi and distance measurements. the device received here as a
				// parameter already has the updated values.

				// but for this example we are only interested in the list of scanned devices, so
				// we ignore the parameter and get the updated device list, sorted by rssi from the
				// library
				if (System.currentTimeMillis() > _lastUpdate + GUI_UPDATE_INTERVAL) {
					_bleDeviceList = _ble.getDeviceMap().getRssiSortedList();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							// the closest device is the first device in the list (because we asked for the
							// rssi sorted list)
							_txtClosest.setText(getString(R.string.main_closest_device, _bleDeviceList.get(0).getName()));

							// update the list view
							DeviceListAdapter adapter = ((DeviceListAdapter) _lvScanList.getAdapter());
							adapter.updateList(_bleDeviceList);
							adapter.notifyDataSetChanged();
						}
					});
					_lastUpdate = System.currentTimeMillis();
				}
			}

			@Override
			public void onError(int error) {
				Log.e(TAG, "Scan error: " + error);
			}
		});
	}

	private void onBleEnabled() {
		_btnScan.setEnabled(true);
	}

	private void onBleDisabled() {
		_btnScan.setEnabled(false);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		switch(id) {
			case R.id.action_login: {
				if (!Config.OFFLINE) {
					startActivity(new Intent(this, LoginActivity.class));
					break;
				}
			}
		}

		return super.onOptionsItemSelected(item);
	}

	private void initializeBle() {
		_ble.init(this, new IStatusCallback() {
			@Override
			public void onSuccess() {
				// on success is called whenever bluetooth is enabled
				Log.i(TAG, "BLE enabled");
				onBleEnabled();
			}

			@Override
			public void onError(int error) {
				// on error is (also) called whenever bluetooth is disabled
				Log.e(TAG, "Error: " + error);
				onBleDisabled();

				if (error == BleErrors.ERROR_BLE_PERMISSION_DENIED) {
					_ble.requestPermissions(MainActivity.this);
				}
			}
		});
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (!_ble.handlePermissionResult(requestCode, permissions, grantResults,
				new IStatusCallback() {

			@Override
			public void onError(int error) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
						builder.setTitle("Fatal Error")
								.setMessage("Cannot scan for devices without permissions. Please " +
										"grant permissions or uninstall the app again!")
								.setNeutralButton("OK", new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										MainActivity.this.finish();
									}
								});
						builder.create().show();
					}
				});
			}

			@Override
			public void onSuccess() {
				initializeBle();
			}
		})) {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}
}
