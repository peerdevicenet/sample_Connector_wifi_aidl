Connector_wifi_aidl
===================

This sample connector using peerdevicenet-router's ConnectionService aidl api to discover and connect to peer devices. It communicates thru either external wifi switch or WifiDirect network setup among a group of WifiDirect enabled devices.

It doesn't embed Router directly, instead invokes an external router's APIs embedded in another app such as ConnectionSettings app. 

1. In AndroidManifest.xml, add the following permission to enable access router APIs:

		<uses-permission android:name="com.xconns.peerdevicenet.permission.REMOTE_MESSAGING" />


2. To access router's api, add peerdevicenet-api.jar in one of two ways:
             
        * download peerdevicenet-api.jar from MavenCentral(http://search.maven.org/#search|ga|1|peerdevicenet) and copy to project's "libs/" directory.
        * if you are using android's new gradle build system, you can import it as 'com.xconns.peerdevicenet:peerdevicenet-api:1.1.6'.


3. This connector has a single activity ConnectorByWifiIdl with a simple GUI:

		* a TextView showing which network is connected
		* a button to start/stop peer search
		* a ListView showing found and connected peer devices
		* a button to shutdown and disconnect router


4. The major components for talking to ConnectionService:
	
		* connClient: the wrapper object to talk to ConnectionService aidl api.
		* connHandler: callback handler registered in connClient constructor.
		* mHandler: an os.Handler object; since connHandler methods run inside aidl threadpool and we can only update GUI inside main thread, so connHandler methods will forward messages to mHandler to perform real job.

5. These components are created and destroyed following normal life cycle conventions:

		* in activity's onCreate() or fragment's onCreateView(), start and bind to Router's ConnectionService (connClient/connHandler created). 
		* in onDestroy(), unbind from ConnectionService.
		* Please note we call startService() explicitly to start ConnectionService and then bind to it. Without calling startService(), when connector activity finishes and unbind from ConnectionService inside onDestroy(), RouterService will be also destroyed since nobody bind to it anymore. Connectors setup router's connections so that other apps (Chat, Rotate) can communicate, so we keep router alive by startService(). Router must be explicitly killed by first unbinding all clients and call stopService() with intent ACTION_ROUTER_SHUTDOWN.


6. Typical interaction with ConnectionService involves a oneway call to API and router call back at connHandler to reply. Typical workflow for network detection, peer search and device connection consist of the following steps:

	6.1. optionally set connection parameters by calling connClient.setConnInfo(), such as the name this device will show to other peers, require SSL connection or not, etc.

	6.2. find all networks this device is connected to, by calling connClient.getNetworks(). In callback connHandler.onGetNetwork() and then mHandler GET_NEWORKS message, if no network found, update GUI showing a message about it. If networks attached, go on to next step.

	6.3. detect which network is active in use for PeerDeviceNet communication, by calling connClient.getActiveNetwork. When handling callback message GET_ACTIVE_NETWORK, if active network is found, goto 6.5.; otherwise goto 6.4..

	6.4. activate one of the attached networks, by calling connClient.activateNetwork(). When handling callbakc message ACTIVATE_NETWORK, update GUI showing active network info and go to 6.5.

	6.5. retrieve my device info in active network (ip addr, port), by calling connClient.getDeviceInfo(). When handling callback message GET_DEVICE_INFO, we can save device info and start search at 6.6.

	6.6. start searching for peer devices, by calling connClient.startPeerSearch(). There are 3 possible callbacks for this call:

		* SEARCH_START: search started, we can update GUI about it.

		* SEARCH_FOUND_DEVICE: a new peer device is found, we can perform authentication or connect to this device at step 6.7.

		* SEARCH_COMPLETE: either search time out or terminated by users.

	6.7. connect to found peer device, by calling connClient.connect(). There are 2 possible callbacks:

		* CONNECTED: add the connected device to list view
		
		* CONNECTION_FAILED: the message will contain an error code showing why connection failed (such as rejected by peer). We can show it in GUI or log it.


