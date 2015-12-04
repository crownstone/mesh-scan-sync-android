package nl.dobots.crownstonehub;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

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
public class DeviceListAdapter extends BaseAdapter {

	private static final String TAG = DeviceListAdapter.class.getCanonicalName();

	private Context _context;
	private BleDeviceList _arrayList;
//	private String _selection = "";

	public DeviceListAdapter(Context context, BleDeviceList array) {
		_context = context;
		_arrayList = array;
	}

//	public void setSelection(String address) {
//		_selection = address;
//	}

	// How many items are in the data set represented by this Adapter.
	@Override
	public int getCount() {
		return _arrayList.size();
	}

	// Get the data item associated with the specified position in the data set.
	@Override
	public Object getItem(int position) {
		Log.i(TAG, String.valueOf(_arrayList.get(position)));
		return _arrayList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	public void updateList(BleDeviceList list) {
		_arrayList = list;
	}

	private class ViewHolder {

		protected TextView devName;
		protected TextView devAddress;
		protected TextView devRssi;
		protected TextView devUUID;
		protected TextView devMajor;
		protected TextView devMinor;
		protected LinearLayout layIBeacon;
		protected TextView devDistance;

	}

	// Get a View that displays the data at the specified position in the data set.
	// You can either create a View manually or inflate it from an XML layout file.
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		if(convertView == null){
			// LayoutInflater class is used to instantiate layout XML file into its corresponding View objects.
			LayoutInflater layoutInflater = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = layoutInflater.inflate(R.layout.list_row, null);
			final ViewHolder viewHolder = new ViewHolder();

			viewHolder.devName = (TextView) convertView.findViewById(R.id.devName);
			viewHolder.devAddress = (TextView) convertView.findViewById(R.id.devAddress);
			viewHolder.devRssi = (TextView) convertView.findViewById(R.id.devRssi);
			viewHolder.devUUID = (TextView) convertView.findViewById(R.id.devUUID);
			viewHolder.devMajor = (TextView) convertView.findViewById(R.id.devMajor);
			viewHolder.devMinor = (TextView) convertView.findViewById(R.id.devMinor);
			viewHolder.layIBeacon = (LinearLayout) convertView.findViewById(R.id.layIBeacon);
			viewHolder.devDistance = (TextView) convertView.findViewById(R.id.devDistance);

			convertView.setTag(viewHolder);
		}

		ViewHolder viewHolder = (ViewHolder) convertView.getTag();

		if (!_arrayList.isEmpty()) {
			BleDevice device = _arrayList.get(position);
			viewHolder.devName.setText(device.getName());
			viewHolder.devRssi.setText(String.valueOf(device.getAverageRssi()));
			viewHolder.devAddress.setText("[" + device.getAddress() + "]");

			if (device.isIBeacon()) {
				// if the device is an iBeacon, show additional information
				viewHolder.layIBeacon.setVisibility(View.VISIBLE);

				viewHolder.devUUID.setText("UUID: " + device.getProximityUuid());
				viewHolder.devMajor.setText("Major: " + String.valueOf(device.getMajor()));
				viewHolder.devMinor.setText("Minor: " + String.valueOf(device.getMinor()));
				viewHolder.devDistance.setText("Distance: " + String.valueOf(device.getDistance()));

				// a doBeacon is also an iBeacon
//				if (device.isDoBeacon()) {
//					convertView.setBackgroundColor(0x66FFFF00);
//				} else {
//					convertView.setBackgroundColor(0x660000FF);
//				}
			} else {
				viewHolder.layIBeacon.setVisibility(View.GONE);
				// is it a crownstone?
//				if (device.isCrownstone()) {
//					convertView.setBackgroundColor(0x6600FF00);
//				} else {
//					convertView.setBackgroundColor(0x00000000);
//				}
			}
//			if (device.getAddress() == _selection) {
//				convertView.setBackgroundColor(0x66FF0000);
//			}
		}

		return convertView;
	}

}