package com.example.ble.view;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ble.R;
import com.example.ble.service.BluetoothLeService;

import java.util.List;

public class BLEDataControlClass extends Activity {
	private String mDeviceAddress = "78:A5:04:53:B6:D0";// 蓝牙mac地址
	private BluetoothLeService mBluetoothLeService;// 蓝牙service
	private boolean mConnected = false;// 蓝牙是否连接
	StringBuffer sb = null;
	
	
	private static final int DATA_REQUEST_SUCCESS = 0x01;// 数据接收成功的handler flag

	private static final int DEVICE_SCAN_SUCCESS = 0x02;// 藍牙搜索成功
	// 创建服务管理来连接蓝牙
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				finish();
			}
//			mBluetoothLeService.connect(mDeviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	// 创建广播实时监听蓝牙状态
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				mConnected = true;
				Toast.makeText(BLEDataControlClass.this, "连接成功！", 2000).show();
				// updateConnectionState(R.string.connected);
				invalidateOptionsMenu();
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
					.equals(action)) {
				mConnected = false;
				Toast.makeText(BLEDataControlClass.this, "蓝牙断开！", 2000).show();
				invalidateOptionsMenu();
				// clearUI();
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
			}else if (BluetoothLeService.ACTION_DEVICE_AVAILABLE
					.equals(action)) {
				Message msg= handler.obtainMessage();
				msg.what = DEVICE_SCAN_SUCCESS;
				msg.sendToTarget();
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				// 发送message上传数据到Pc端
				Message m = handler.obtainMessage();
				m.what = DATA_REQUEST_SUCCESS;
				m.obj = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
				m.sendToTarget();
			}
		}
	};

	private static byte STOP_MEASURE[] = new byte[] { 0x68, 0x02, 0x00, 0x02,
			0x00, 0x22, 0x31,(byte) 0xBF, 0x16 };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.data_control_view);
		TextView send = (TextView) findViewById(R.id.send);
		send.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
//				mBluetoothLeService.writeValue(STOP_MEASURE);//测试发送指令
				mBluetoothLeService.startScanDevice();
			}
		});
		// 绑定服务
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	private Handler handler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
						case DATA_REQUEST_SUCCESS:
					//这里可以将字节数组发送到Pc端，此方法将byte[]转换为stringbuffer,以-隔开
					ControlByteArray((byte[]) msg.obj);
					break;
				case DEVICE_SCAN_SUCCESS:
					//这里可以将字节数组发送到Pc端，此方法将byte[]转换为stringbuffer,以-隔开
					List<String> list = mBluetoothLeService.getScanDeviceInfo();
					break;

			default:
				break;
			}
		};
	};

	/**
	 * 将接收到的byte字节数组转换为可见的字符串
	 * @param bytes
	 */
	private void ControlByteArray(byte[] bytes) {
		sb = new StringBuffer();
		for (int i = 0; i < bytes.length; i++) {
			sb.append(bytes[i] + "-");
		}
	}

	@Override
	protected void onResume() {// 注册广播
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
	}

	@Override
	protected void onDestroy() {// 界面销毁时解除服务与广播的绑定
		// TODO Auto-generated method stub
		super.onDestroy();
		unbindService(mServiceConnection);
		unregisterReceiver(mGattUpdateReceiver);
		mBluetoothLeService = null;
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter
				.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		intentFilter.addAction(BluetoothLeService.ACTION_DEVICE_AVAILABLE);
		return intentFilter;
	}
}
