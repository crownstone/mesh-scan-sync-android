package nl.dobots.crownstonehub;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;

import nl.dobots.bluenet.ble.base.callbacks.IDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IMeshDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.mesh.BleMeshData;
import nl.dobots.bluenet.ble.base.structs.mesh.BleMeshScanData;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;

public class SinkActivity extends AppCompatActivity implements IMeshDataCallback {

	private static final String TAG = SinkActivity.class.getCanonicalName();

	private String _address;
	private BleExt _ble;

	private boolean _connected;
	private DeviceListAdapter _adapter;
	private BleDeviceList _bleDeviceList;

	private ArrayList<BleMeshScanData> _scanList = new ArrayList<>();
	private ListView _deviceList;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sink);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		initUI();

		_address = getIntent().getStringExtra("address");

		_bleDeviceList = new BleDeviceList();
		_adapter = new DeviceListAdapter(this, _bleDeviceList);

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

				// so first we check if the PWM characteristic is available on this device
				if (_ble.hasCharacteristic(BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID, null)) {
					_connected = true;

					_ble.subscribeMeshData(SinkActivity.this);

					dlg.dismiss();
				} else {
					// return an error and exit if the PWM characteristic is not available
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(SinkActivity.this, "No PWM Characteristic found for this device!", Toast.LENGTH_LONG).show();
						}
					});
					finish();
				}
			}

			@Override
			public void onError(int error) {
				// an error occurred during connect/discover
				Log.e(TAG, "failed to connect/discover: " + error);
				dlg.dismiss();
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
	}

	private void initUI() {

		DeviceScanAdapter adapter = new DeviceScanAdapter(this, _scanList);

		_deviceList = (ListView) findViewById(R.id.lvDeviceList);
		_deviceList.setAdapter(adapter);

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
	}

	@Override
	public void onError(int error) {
		Log.e(TAG, String.format("Error: %d", error));
		finish();
	}

	@Override
	public void onData(BleMeshData data) {
		if (data != null) {
			Log.i(TAG, data.toString());
			_scanList.add((BleMeshScanData) data);
			_deviceList.post(new Runnable() {
				@Override
				public void run() {
					_adapter.notifyDataSetChanged();
					setListViewHeightBasedOnChildren(_deviceList);
				}
			});
		}
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
