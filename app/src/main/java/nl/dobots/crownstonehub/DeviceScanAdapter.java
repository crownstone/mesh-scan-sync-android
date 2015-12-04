package nl.dobots.crownstonehub;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import nl.dobots.bluenet.ble.base.structs.mesh.BleMeshScanData;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;

/**
 * This is just a simple list adapter to show the list of scanned devices. It checks the
 * type of device, and displays additional information for iBeacon devices (such as minor,
 * major, proximity UUID, and estimated distance). The devices are color coded to show
 * what type of device it is:
 * 		* Green: Crownstone
 * 		* Yellow: DoBeacon
 * 		* Blue: iBeacon
 * 		* Black: any other BLE device
 *
 * Created on 1-10-15
 * @author Dominik Egger
 */
public class DeviceScanAdapter extends BaseAdapter {

	private static final String TAG = DeviceScanAdapter.class.getCanonicalName();

	private Context _context;

	private ArrayList<BleMeshScanData> _list;

	private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

//	private BleDeviceList _arrayList;
//	private String _selection = "";

	public DeviceScanAdapter(Context context, ArrayList<BleMeshScanData> list) {
		_context = context;
		_list = list;
	}

//	public void setSelection(String address) {
//		_selection = address;
//	}

	// How many items are in the data set represented by this Adapter.
	@Override
	public int getCount() {
		return _list.size();
	}

	// Get the data item associated with the specified position in the data set.
	@Override
	public Object getItem(int position) {
		Log.i(TAG, String.valueOf(_list.get(position)));
		return _list.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	public void updateList(ArrayList<BleMeshScanData> list) {
		_list = list;
	}

	private class ViewHolder {

		protected TextView devTime;
		protected TextView devSource;
		protected TextView devScan;

	}

	// Get a View that displays the data at the specified position in the data set.
	// You can either create a View manually or inflate it from an XML layout file.
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		if(convertView == null){
			// LayoutInflater class is used to instantiate layout XML file into its corresponding View objects.
			LayoutInflater layoutInflater = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = layoutInflater.inflate(R.layout.device_scan, null);
			final ViewHolder viewHolder = new ViewHolder();

			viewHolder.devTime = (TextView) convertView.findViewById(R.id.devTime);
			viewHolder.devSource = (TextView) convertView.findViewById(R.id.devSource);
			viewHolder.devScan = (TextView) convertView.findViewById(R.id.devScan);

			convertView.setTag(viewHolder);
		}

		ViewHolder viewHolder = (ViewHolder) convertView.getTag();

		if (!_list.isEmpty()) {
			BleMeshScanData device = _list.get(position);
			viewHolder.devTime.setText(sdf.format(device.getTimeStamp()));
			viewHolder.devSource.setText(device.getSourceAddress());

//			String scan = String.format("%17s   %4s   %4s\r\n", "Address", "RSSI", "Occ");
			String scan = "";
			BleMeshScanData.ScannedDevice[] devices = device.getDevices();
			for (BleMeshScanData.ScannedDevice dev : devices) {
				scan += String.format("%s   %4d   %4d\r\n", dev.getAddress(), dev.getRssi(), dev.getOccurrences());
			}
			viewHolder.devScan.setText(scan);
		}

		return convertView;
	}

}