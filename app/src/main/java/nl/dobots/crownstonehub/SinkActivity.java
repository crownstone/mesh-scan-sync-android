package nl.dobots.crownstonehub;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.strongloop.android.loopback.RestAdapter;
import com.strongloop.android.loopback.callbacks.ObjectCallback;
import com.strongloop.android.loopback.callbacks.VoidCallback;
import com.strongloop.android.remoting.JsonUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.base.callbacks.IMeshDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.mesh.BleMeshHubData;
import nl.dobots.bluenet.ble.base.structs.mesh.BleMeshScanData;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.crownstonehub.cfg.Config;
import nl.dobots.loopback.CrownstoneRestAPI;
import nl.dobots.loopback.loopback.Beacon;
import nl.dobots.loopback.loopback.BeaconRepository;
import nl.dobots.loopback.loopback.Scan;

public class SinkActivity extends AppCompatActivity implements IMeshDataCallback {

	private static final String TAG = SinkActivity.class.getCanonicalName();

	private String _address;
	private BleExt _ble;

	private boolean _connected;

	private ArrayList<BleMeshScanData> _scanList = new ArrayList<>();
	private ListView _deviceList;

	private RestAdapter _restAdapter;
	private BeaconRepository _beaconRepository;
	private DeviceScanAdapter _deviceScanAdapter;

	private Handler _uiHandler = new Handler();

	private boolean _logFileOpen;
	private File _scanBackupFile;
	private OutputStream _scanBackupDos;
	private BufferedWriter _bufferedWriter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sink);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		initUI();

		_address = getIntent().getStringExtra("address");

		if (!Config.OFFLINE) {
			_restAdapter = CrownstoneRestAPI.getRestAdapter(this);
			_beaconRepository = CrownstoneRestAPI.getBeaconRepository();
		}

		// create our access point to the library, and make sure it is initialized (if it
		// wasn't already)
		_ble = new BleExt();
		_ble.init(this, new IStatusCallback() {
			@Override
			public void onSuccess() {
				Log.v(TAG, "onSuccess");
			}

			@Override
			public void onError(int error) {
				Log.e(TAG, "onError: " + error);
				showErrorAlert("BLE Error. Please check your bluetooth and try again.");
			}
		});

		final ProgressDialog dlg = ProgressDialog.show(this, "Connecting", "Please wait...", true);

		// first we have to connect to the device and discover the available characteristics.
		_ble.connectAndDiscover(_address, new IDiscoveryCallback() {
			@Override
			public void onDiscovery(String serviceUuid, String characteristicUuid) {
				// this function is called for every detected characteristic with the
				// characteristic's UUID and the UUID of the service it belongs.
				// you can keep track of what functions are available on the device,
				// but you don't have to, the library does that for you.
			}

			@Override
			public void onSuccess() {
				// once discovery is completed, this function will be called. we can now execute
				// the functions on the device. in this case, we want to know what the current
				// PWM state is
				_connected = true;

				// so first we check if the PWM characteristic is available on this device
				if (_ble.hasCharacteristic(BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID, null)) {

					_ble.subscribeMeshData(SinkActivity.this);
//					_ble.readMeshData(SinkActivity.this);

					dlg.dismiss();

					_logFileOpen = openBackupFile(SinkActivity.this);
				} else {
					// return an error and exit if the PWM characteristic is not available
//					runOnUiThread(new Runnable() {
//						@Override
//						public void run() {
//							Toast.makeText(SinkActivity.this, "No PWM Characteristic found for this device!", Toast.LENGTH_LONG).show();
//						}
//					});
//					finish();
					showErrorAlert("No Mesh Characteristic found for this device!");
				}
			}

			@Override
			public void onError(int error) {
				// an error occurred during connect/discover
				Log.e(TAG, "failed to connect/discover: " + error);
				dlg.dismiss();
//				runOnUiThread(new Runnable() {
//					@Override
//					public void run() {
//						Toast.makeText(SinkActivity.this, "Error during discovery! Please try again", Toast.LENGTH_LONG).show();
//					}
//				});
//				finish();
				showErrorAlert("Error during discovery! Please try again");
			}
		});

//		FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//		fab.setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View view) {
//				Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//						.setAction("Action", null).show();
//			}
//		});

		_uiHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				while (_scanList.size() > Config.MAX_DISPLAY_RESULTS) {
					_scanList.remove(_scanList.size() - 1);
				}
				_deviceScanAdapter.notifyDataSetChanged();

//				setListViewHeightBasedOnChildren(_deviceList);

				_uiHandler.postDelayed(this, 1000);
			}
		}, 1000);

	}

	private boolean openBackupFile(Context context) {

		SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss");
		String fileName = "scan_backup_" + sdf.format(new Date()) + ".txt";

		File path = context.getExternalFilesDir(null);
//		File path = new File(Environment.getExternalStorageDirectory().getPath() + "/dobots/crownstone");
		_scanBackupFile = new File(path, fileName);

//		Log.i(TAG, "external storage path: " + path.getAbsolutePath());

//		_scanBackupFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
		try {
			path.mkdirs();
			_scanBackupDos = new DataOutputStream(new FileOutputStream(_scanBackupFile));
		} catch (IOException e) {
			Log.e(TAG, "Error creating " + _scanBackupFile, e);

			return false;
		}

		return true;
	}

	private void initUI() {

		_deviceScanAdapter = new DeviceScanAdapter(this, _scanList);

		_deviceList = (ListView) findViewById(R.id.lvDeviceList);
		_deviceList.setAdapter(_deviceScanAdapter);

	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (_connected) {
			_ble.disconnectAndClose(false, new IStatusCallback() {
				@Override
				public void onSuccess() {

				}

				@Override
				public void onError(int error) {

				}
			});
		}
		_ble.destroy();

		try {
			if (_logFileOpen) {
				_scanBackupDos.flush();
				_scanBackupDos.close();
				_logFileOpen = false;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onError(int error) {
		Log.e(TAG, String.format("Error: %d", error));
		finish();
	}

	HashMap<String, Beacon> _beaconDbCache = new HashMap<>();

	@Override
	public void onData(final BleMeshHubData data) {
		if (data != null) {
			Log.i(TAG, data.toString());
			final BleMeshScanData meshScanData = (BleMeshScanData) data;

//			_ble.unsubscribeMeshData(SinkActivity.this);

			final Scan scan = new Scan();
			scan.setTimestamp(meshScanData.getTimeStamp());

			BleMeshScanData.ScannedDevice[] devices = meshScanData.getDevices();
			for (BleMeshScanData.ScannedDevice dev : devices) {
				scan.addScannedDevice(dev.getAddress(), dev.getRssi(), dev.getOccurrences());
			}

			final String address = meshScanData.getSourceAddress();

			if (!Config.OFFLINE) {
				CrownstoneRestAPI.post(new Runnable() {
					@Override
					public void run() {
						if (_beaconDbCache.containsKey(address)) {
							uploadScan(_beaconDbCache.get(address), scan);
						} else {
							_beaconRepository.findByAddress(address, new ObjectCallback<Beacon>() {
								@Override
								public void onSuccess(Beacon beacon) {
									_beaconDbCache.put(address, beacon);
									uploadScan(beacon, scan);
								}

								@Override
								public void onError(Throwable t) {
									Log.i(TAG, "error: ", t);
								}
							});
						}
					}
				});
			}

			_uiHandler.post(new Runnable() {
				@Override
				public void run() {
					_scanList.add(0, meshScanData);

					writeToBackupFile(address, scan);

//					setListViewHeightBasedOnChildren(_deviceList);
				}
			});
		}
	}

	private void writeToBackupFile(String address, Scan scan) {
		if (_logFileOpen) {
			try {
				JSONObject jsonScan = (JSONObject)JsonUtil.toJson(scan.toMap());
				// adding source address separately, since it's not a part of the scan
				// message (only the id is necessary for the cloud part, which is a reference
				// to the beacon object)
				jsonScan.put("source_address", address);
				_scanBackupDos.write(jsonScan.toString().getBytes());
				_scanBackupDos.write("\n".getBytes());
				_scanBackupDos.flush();

				if (_scanBackupFile.length() > Config.MAX_BACKUP_FILE_SIZE) {
					_scanBackupDos.close();
					_logFileOpen = openBackupFile(SinkActivity.this);
				}

				if (_logFileOpen && _scanBackupFile.getFreeSpace() < Config.MIN_FREE_SPACE) {
					_scanBackupDos.close();
					_logFileOpen = false;
				}

			} catch (IOException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	private void uploadScan(Beacon beacon, Scan scan) {

		beacon.addScan(beacon.getId(), scan, new VoidCallback() {
			@Override
			public void onSuccess() {
				Log.i(TAG, "success");
			}

			@Override
			public void onError(Throwable t) {
				Log.i(TAG, "error: ", t);
			}
		});
	}

	private void showErrorAlert(final String text) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(SinkActivity.this);
				builder.setTitle("Error")
						.setMessage(text)
						.setNeutralButton("OK", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								SinkActivity.this.finish();
							}
						});
				builder.create().show();
			}
		});
	}

	public static void setListViewHeightBasedOnChildren(ListView listView) {
		ListAdapter listAdapter = listView.getAdapter();
		if (listAdapter == null) {
			// pre-condition
			return;
		}

		int totalHeight = 0;
		int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.AT_MOST);
		for (int i = 0; i < listAdapter.getCount(); i++) {
			View listItem = listAdapter.getView(i, null, listView);
			listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
			totalHeight += listItem.getMeasuredHeight();
		}

		ViewGroup.LayoutParams params = listView.getLayoutParams();
		params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
		listView.setLayoutParams(params);
		listView.requestLayout();
	}

}
