package com.xconns.peerdevicenet.samples.connector_wifi_idl;

import java.util.HashMap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.Router;
import com.xconns.peerdevicenet.RouterConnectionClient;
import com.xconns.peerdevicenet.core.RouterService;

public class ConnectorByWifiIdl extends ActionBarActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_connector_by_wifi_idl);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
	}

	/**
	 * main fragment containing all GUI.
	 */
	public static class PlaceholderFragment extends Fragment {

		private static final String TAG = "ByeBye";

		private Activity activity = null;

		private TextView mNetMsg = null;
		private Button mConnButton = null;
		private Button mDoneButton = null;

		private CharSequence setupNetText = null;
		private CharSequence stopSearchText = null;
		private CharSequence searchConnectText = null;
		private CharSequence onNetText = null;
		private CharSequence missNetText = null;
		
		private ArrayAdapter<String> mPeerListAdapter;
		private ListView mPeerListView;

		private HashMap<String, DeviceInfo> discoveredDevices = new HashMap<String, DeviceInfo>();
		private DeviceInfo mDevice = null; // my own device info
		private NetInfo mNet = null; // network my device connect to
		// peer connection parameters
		private String securityToken = ""; // dont check conn security token
		private int connTimeout = 5000; // 5 seconds for socket conn timeout
		private int searchTimeout = 30000; // 30 seconds timeout for searching
											// peers

		// interface to router connection service
		RouterConnectionClient connClient = null;

		public PlaceholderFragment() {
		}

		@Override
		public void onAttach(Activity act) {
			// TODO Auto-generated method stub
			super.onAttach(activity);
			activity = act;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_connector_by_wifi_idl,
					container, false);

			setupNetText = getResources().getText(R.string.setup_net);
			stopSearchText = getResources().getText(R.string.stop_search);
			searchConnectText = getResources().getText(R.string.search_connect);
			onNetText = getResources().getText(R.string.on_net);
			missNetText = getResources().getText(R.string.miss_net);

			mNetMsg = (TextView) rootView.findViewById(R.id.net_msg);
			mConnButton = (Button) rootView.findViewById(R.id.button_conn);
			mConnButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					if (mNet == null) {
						configWifi();
					} else {
						Log.d(TAG, "start peer search");
						connClient.startPeerSearch(null, searchTimeout);
					}
				}
			});
			mDoneButton = (Button) rootView.findViewById(R.id.button_done);
			mDoneButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					//shutdown router by calling stopService
					Intent intent = new Intent(activity,
							com.xconns.peerdevicenet.core.RouterService.class);
					activity.stopService(intent);
					//
					activity.finish();
				}
			});
			
			// Initialize the array adapter for the conversation thread
			mPeerListAdapter = new ArrayAdapter<String>(activity, R.layout.peer_name);
			mPeerListView = (ListView) rootView.findViewById(R.id.peers_list);
			mPeerListView.setAdapter(mPeerListAdapter);

			//start router service by intent
			//so it can keep running at background
			//even when client unbind
			//must be stopped by calling stopService after all clients unbind
			Intent intent = new Intent(Router.ACTION_CONNECTION_SERVICE);
			activity.startService(intent);

			// bind to router connection service, listening to incoming
			// connections
			connClient = new RouterConnectionClient(activity, connHandler);
			connClient.bindService();

			// setup my device name known to peers
			String myDeviceName = android.os.Build.MODEL;
			if (myDeviceName == null || myDeviceName.length() == 0) {
				myDeviceName = "MyDeviceName";
			}
			connClient.setConnectionInfo(
					myDeviceName, 
					false/* useSSL */,
					0/*default liveTimeout*/, 
					0/* connTimeout */, 
					0/* searchTimeout */
			);

			// start by checking if device is connected to any networks
			connClient.getNetworks();

			return rootView;
		}

		@Override
		public void onResume() {
			// TODO Auto-generated method stub
			super.onResume();

		}

		@Override
		public void onDestroy() {
			// TODO Auto-generated method stub
			super.onDestroy();
			Log.d(TAG, "ConnMgrServ destroyed, unbind connHnadler");
			connClient.unbindService();
		}

		private void configWifi() {
			Intent in = new Intent(Settings.ACTION_WIFI_SETTINGS);
			activity.startActivity(in);
		}

		private void updateGuiNoNet() {
			mNetMsg.setText(missNetText);
			mConnButton.setText(setupNetText);
		}

		private void updateGuiOnNet(NetInfo net) {
			mNetMsg.setText(onNetText+": "+net.name);
			mConnButton.setText(searchConnectText);
		}

		private void updateGuiSearchStart() {
			mConnButton.setText(stopSearchText);
		}

		private void updateGuiSearchComplete() {
			mConnButton.setText(searchConnectText);
		}
		
		private void addDeviceToList(DeviceInfo dev) {
			mPeerListAdapter.add(dev.name+" : "+dev.addr);
		}

		// since connHandler(aidl handler) runs in aidl threadpool, forward msg
		// to mHandler to run
		// in main thread
		RouterConnectionClient.ConnectionHandler connHandler = new RouterConnectionClient.ConnectionHandler() {
			public void onError(String errInfo) {
				Message msg = mHandler.obtainMessage(Router.MsgId.ERROR);
				msg.obj = errInfo;
				mHandler.sendMessage(msg);
			}

			public void onConnected(DeviceInfo dev) {
				Message msg = mHandler.obtainMessage(Router.MsgId.CONNECTED);
				msg.obj = dev;
				mHandler.sendMessage(msg);
			}

			public void onDisconnected(DeviceInfo dev) {
				Message msg = mHandler.obtainMessage(Router.MsgId.DISCONNECTED);
				msg.obj = dev;
				mHandler.sendMessage(msg);
			}

			public void onGetDeviceInfo(DeviceInfo device) {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.GET_DEVICE_INFO);
				msg.obj = device;
				mHandler.sendMessage(msg);
			}

			public void onGetPeerDevices(DeviceInfo[] devices) {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.GET_CONNECTED_PEERS);
				msg.obj = devices;
				mHandler.sendMessage(msg);
			}

			public void onConnecting(DeviceInfo device, byte[] token) {
				Message msg = mHandler.obtainMessage(Router.MsgId.CONNECTING);
				msg.obj = new Object[] { device, token };
				mHandler.sendMessage(msg);
			}

			public void onConnectionFailed(DeviceInfo device, int rejectCode) {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.CONNECTION_FAILED);
				msg.obj = new Object[] { device, rejectCode };
				mHandler.sendMessage(msg);
			}

			@Override
			public void onSearchStart(DeviceInfo groupLeader) {
				Message msg = mHandler.obtainMessage(Router.MsgId.SEARCH_START);
				msg.obj = groupLeader;
				mHandler.sendMessage(msg);
			}

			public void onSearchFoundDevice(DeviceInfo dev, boolean useSSL) {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.SEARCH_FOUND_DEVICE);
				msg.obj = new Object[] { dev, useSSL };
				mHandler.sendMessage(msg);
			}

			public void onSearchComplete() {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.SEARCH_COMPLETE);
				mHandler.sendMessage(msg);
			}

			@Override
			public void onGetNetworks(NetInfo[] nets) {
				Message msg = mHandler.obtainMessage(Router.MsgId.GET_NETWORKS);
				msg.obj = nets;
				mHandler.sendMessage(msg);
			}

			@Override
			public void onGetActiveNetwork(NetInfo net) {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.GET_ACTIVE_NETWORK);
				msg.obj = net;
				mHandler.sendMessage(msg);
			}

			@Override
			public void onNetworkConnected(NetInfo net) {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.NETWORK_CONNECTED);
				msg.obj = net;
				mHandler.sendMessage(msg);
			}

			@Override
			public void onNetworkDisconnected(NetInfo net) {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.NETWORK_DISCONNECTED);
				msg.obj = net;
				mHandler.sendMessage(msg);
			}

			@Override
			public void onNetworkActivated(NetInfo net) {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.ACTIVATE_NETWORK);
				msg.obj = net;
				mHandler.sendMessage(msg);
			}

			@Override
			public void onSetConnectionInfo() {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.SET_CONNECTION_INFO);
				mHandler.sendMessage(msg);
			}

			@Override
			public void onGetConnectionInfo(String devName, boolean uSSL,
					int liveTime, int connTime, int searchTime) {
				// do nothing
			}

		};

		/**
		 * Handler of incoming messages from service.
		 */
		Handler mHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				DeviceInfo device = null;
				Object[] params = null;
				NetInfo net = null;
				switch (msg.what) {
				// handle msgs for scan
				case Router.MsgId.SEARCH_FOUND_DEVICE:
					params = (Object[]) msg.obj;
					device = (DeviceInfo) params[0];
					boolean uSSL = (Boolean) params[1];
					Log.d(TAG, "onSearchFoundDevice: " + device);
					if (discoveredDevices.containsKey(device.addr)) {
						Log.d(TAG, "already discovered, drop it");
						return;
					}
					discoveredDevices.put(device.addr, device);

					Log.d(TAG, "---a2");
					// after find devices
					// auto connect to them
					// connect from device with small ip to device with large ip
					if (mDevice.addr.compareTo(device.addr) < 0) {
						Log.d(TAG, "connect to client: " + device.addr);
						connClient.connect(device, securityToken.getBytes(),
								connTimeout);
					}

					break;
				case Router.MsgId.SEARCH_COMPLETE:
					Log.d(TAG, "search complete");
					updateGuiSearchComplete();
					break;
				case Router.MsgId.SEARCH_START:
					DeviceInfo groupLeader = (DeviceInfo) msg.obj;
					Log.d(TAG, "onSearchStart: " + groupLeader);
					updateGuiSearchStart();
					break;
				// handle msgs for connections
				case Router.MsgId.CONNECTED:
					device = (DeviceInfo) msg.obj;
					addDeviceToList(device);
					Log.d(TAG, "a device connected");
					break;
				case Router.MsgId.DISCONNECTED:
					device = (DeviceInfo) msg.obj;
					Log.d(TAG, "a device disconnected: " + device.addr);
					break;
				case Router.MsgId.GET_CONNECTED_PEERS:
					DeviceInfo[] devices = (DeviceInfo[]) msg.obj;
					if (devices == null) {
						return;
					}
					Log.d(TAG, "get_connected_peers: " + devices.length);
					break;
				case Router.MsgId.CONNECTING:
					params = (Object[]) msg.obj;
					device = (DeviceInfo) params[0];
					byte[] token = (byte[]) params[1];
					Log.d(TAG, "peer " + device.addr
							+ " sends connecting to me");

					// check if trying to conn to self
					if (device.addr != null && device.addr.equals(mDevice.addr)) {
						Log.d(TAG, "CONN_TO_SELF: deny self connection");
						connClient.denyConnection(device,
								Router.ConnFailureCode.FAIL_CONN_SELF);
						return;
					}

					// auto accept connection from peer
					Log.d(TAG, "accept peer's connection attempt from: "
							+ device.addr);
					connClient.acceptConnection(device);
					break;
				case Router.MsgId.CONNECTION_FAILED:
					params = (Object[]) msg.obj;
					device = (DeviceInfo) params[0];
					int rejectCode = ((Integer) params[1]);
					Log.d(TAG, "connection_failed: " + device.toString());
					break;
				case Router.MsgId.GET_DEVICE_INFO:
					device = (DeviceInfo) msg.obj;
					mDevice = device;
					Log.d(TAG, "onGetDeviceInfo: " + device.toString());
					// my device connect to net and got deviceinfo, 
					//start search for peers
					Log.d(TAG, "start peer search");
					connClient.startPeerSearch(null, searchTimeout);
					break;

				case Router.MsgId.ERROR:
					String errInfo = (String) msg.obj;
					Log.d(TAG, "Error msg: " + errInfo);

					break;
				case Router.MsgId.GET_NETWORKS:
					NetInfo[] nets = (NetInfo[]) msg.obj;
					Log.d(TAG, "onGetNetworks: "
							+ (nets != null ? nets.length : "null"));
					if (nets == null || nets.length == 0) {
						updateGuiNoNet();
					} else {
						mNet = nets[0]; // by default activate the first network
						// first search for current active network
						connClient.getActiveNetwork();
					}
					break;
				case Router.MsgId.GET_ACTIVE_NETWORK:
					net = (NetInfo) msg.obj;
					Log.d(TAG, "onGetActiveNetwork");
					if (net != null) {
						mNet = net;
						// update GUI
						updateGuiOnNet(net);
						// get my device info at active network
						connClient.getDeviceInfo();
					} else {// no active network
						if (mNet != null) {
							connClient.activateNetwork(mNet);
						} else {
							Log.e(TAG, "mNet is null");
						}
					}
					break;
				case Router.MsgId.ACTIVATE_NETWORK:
					net = (NetInfo) msg.obj;
					Log.d(TAG, "onNetworkActivated: " + net.toString());
					mNet = net;
					// update GUI
					updateGuiOnNet(net);
					// get my device info at active network
					connClient.getDeviceInfo();
					break;
				case Router.MsgId.NETWORK_CONNECTED:
					net = (NetInfo) msg.obj;
					Log.d(TAG, "onNetworkConnected: "/* +net.toString() */);
					// by default activate newly connected network
					connClient.activateNetwork(net);
					break;
				case Router.MsgId.NETWORK_DISCONNECTED:
					net = (NetInfo) msg.obj;
					Log.d(TAG, "onNetworkDisconnected: " + net.toString());
					mNet = null;
					updateGuiNoNet();
					break;
				case Router.MsgId.SET_CONNECTION_INFO:
					Log.d(TAG, "finish SetConnectionInfo()");

					break;
				case Router.MsgId.GET_CONNECTION_INFO:
					Log.d(TAG, "onGetConnectionInfo()");
					break;
				default:
					Log.d(TAG, "unhandled msg: " + Router.MsgName(msg.what));
					super.handleMessage(msg);
				}
			}
		};

	}

}
