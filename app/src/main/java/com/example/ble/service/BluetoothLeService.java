package com.example.ble.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * BluetoothLeService:管理蓝牙连接和数据接收的服务类
 */
@SuppressLint("NewApi")
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    HashMap<String, String> mDeviceInfo = new HashMap<String, String>();//存储搜索到的蓝牙设备
    boolean mScanning = false;
    private Runnable mScanBleRun = null;
    public static final int SCANNING_TIME = 5000;


    private BluetoothManager mBluetoothManager;
    /*
     * BluetoothAdapter是Android系统中所有蓝牙操作都需要的，它对应本地Android设备的蓝牙模块，
     * 在整个系统中BluetoothAdapter是单例的。当你获取到它的示例之后，就能进行相关的蓝牙操作了
     */
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;// 蓝牙mac地址
    private BluetoothGatt mBluetoothGatt;// BluetoothGatt作为中央来使用和处理数据

    // 可以理解为在BLE设备之间的数据传输时靠的就是BluetoothGattCharacteristic，类似于一个中转站
    /**
     * 写入命令的BluetoothGattCharacteristic
     */
    private BluetoothGattCharacteristic mWriteCharacteristic;
    /**
     * 读取数据的BluetoothGattCharacteristic
     */
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private Runnable mReadCharacteristicRun = null;
    private Handler mHandler = new Handler();

    private int mConnectionState = STATE_DISCONNECTED;// 默认未连接状态

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public static final String ACTION_DEVICE_AVAILABLE = "com.example.bluetooth.le.ACTION_DEVICE_AVAILABLE";
    public final static String ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED";// 蓝牙已连接
    public final static String ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";// 蓝牙断开
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";// 蓝牙gatt服务发现
    public final static String ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";// 蓝牙收到数据可用
    public final static String EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA";// 收到的详细数据

    public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID
            .fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    /**
     * 返回周边的状态，检测回调接口，包括蓝牙的连接状态信息改变、返回数据信息更新
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        // 当连接上设备或者失去连接时会回调该函数
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:"
                        + mBluetoothGatt.discoverServices());
                // 连接成功后就去找出该设备中的服务 private BluetoothGatt mBluetoothGatt;
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        /**
         * 当设备是否找到服务时，会回调该函数
         * 此处status会报129或者133错误，具体解决方案可断开系统蓝牙避免
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {// 在这里可以对服务进行解析，寻找到你需要的服务
                if (mWriteCharacteristic == null) {
                    findGattServices();// 初始化获取读写服务
                    if (mWriteCharacteristic != null)
                        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);//发送服务获取成功的广播
                }

            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        // 当读取设备时会回调该函数
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 读取到的数据存在characteristic当中，可以通过characteristic.getValue();函数取出。然后再进行解析操作。
                // int charaProp = characteristic.getProperties();
                // if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY)
                // > 0)表示可发出通知。 判断该Characteristic属性
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        // 当向设备Descriptor中写数据时，会回调该函数
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            System.out.println("onDescriptorWriteonDescriptorWrite = " + status
                    + ", descriptor =" + descriptor.getUuid().toString());
        }

        @Override
        // 设备发出通知时会调用到该接口
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    /**
     * 发送状态广播
     *
     * @param action
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /**
     * 发送数据广播
     *
     * @param action
     * @param characteristic
     */
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        if (mNotifyCharacteristic != null
                && mNotifyCharacteristic.getUuid().equals(
                characteristic.getUuid())) {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                intent.putExtra(EXTRA_DATA, data);
                sendBroadcast(intent);
            }
        }

    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * 初始化蓝牙各参数
     *
     * @return 返回参数为初始化是否成功
     */
    public boolean initialize() {
        //初始化stop搜索
        if (mScanBleRun == null) {
            mScanBleRun = new Runnable() {
                @Override
                public void run() {
                    if (mScanning) {
                        mScanning = false;
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    }
                }
            };
        }
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        // 开启runnable读取数据，没100ms刷新一次
        if (mReadCharacteristicRun == null) {
            mReadCharacteristicRun = new Runnable() {
                @Override
                public void run() {
                    if (mNotifyCharacteristic != null) {
                        setCharacteristicNotification(mNotifyCharacteristic,
                                true);
                    }
                }
            };
        }
        //初始化時停止搜索
        if (!mBluetoothAdapter.isEnabled())
            return false;
        mBluetoothAdapter.cancelDiscovery();
        return true;
    }

    /**
     * 蓝牙连接
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG,
                    "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        if (mBluetoothDeviceAddress != null
                && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG,
                    "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter
                .getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);// 该函数才是真正的去进行连接
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * 蓝牙断开
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * app退出前，应调用close来关闭中央
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }


    /**
     * -------------------------蓝牙搜索start-------------------------------
     * TODO 蓝牙搜索start
     */

    public void cancelDiscovery() {
        if (mBluetoothAdapter == null)
            return;
        mScanning = false;
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mHandler.removeCallbacks(mScanBleRun);
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }

    public boolean startScanDevice() {
        if (mBluetoothAdapter == null || mScanning == true)
            return false;
        if (!mBluetoothAdapter.isEnabled())
            return false;
        mDeviceInfo.clear();
        if (mScanning)
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        cancelDiscovery();
        mBluetoothAdapter.startLeScan(mLeScanCallback);
        mHandler.removeCallbacks(mScanBleRun);
        mHandler.postDelayed(mScanBleRun, SCANNING_TIME);
        mScanning = true;

        return true;
    }

    public List<String> getScanDeviceInfo() {
        List<String> info = new ArrayList<String>();
        Iterator iter = mDeviceInfo.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            if (entry.getValue().toString().trim().contains("BPUMP")) {
                info.add(entry.getValue() + "\t" + "Mac="
                        + (String) entry.getKey());
            }
        }
        return info;
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi,
                             byte[] scanRecord) {

            if (device.getAddress() != null
                    && device.getAddress().length() != 0
                    && device.getName() != null) {
                String adds = device.getAddress();
                if (mDeviceInfo.get(adds) == null) {
                    mDeviceInfo.put(adds, device.getName());
                    broadcastUpdate(ACTION_DEVICE_AVAILABLE);
                }
            }
        }
    };
    /**-------------------------蓝牙搜索end-------------------------------*/
    /**
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * 写入数据，可以将其当做数据传输的封装，通过它来组合数据---->例如下面的byte[]数组
     *
     * @param characteristic
     */
    public void wirteCharacteristic(BluetoothGattCharacteristic characteristic) {

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * 写入命令（设置byte数组，将数组放到BluetoothGattCharacteristic中）
     *
     * @param bytes
     */
    public void writeValue(byte[] bytes) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null
                || mWriteCharacteristic == null) {
            Log.w("BLeService", "BluetoothAdapter not initialized");
            return;
        }
        mWriteCharacteristic.setValue(bytes);
        boolean ret = mBluetoothGatt.writeCharacteristic(mWriteCharacteristic);
    }

    public BluetoothGattCharacteristic getNotifyCharacteristic() {
        return mNotifyCharacteristic;
    }

    /**
     * 设置当指定characteristic值变化时，发出通知。
     */
    public void setCharacteristicNotification(
            BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w("BLeService", "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);// 设置当指定characteristic值变化时，发出通知。

        try {
            // BluetoothGattDescriptor：描述符，对Characteristic的描述，包括范围、计量单位等
            BluetoothGattDescriptor descriptor = characteristic
                    .getDescriptor(UUID_HEART_RATE_MEASUREMENT);
            if (descriptor != null) {
                descriptor
                        .setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); // BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                mBluetoothGatt.writeDescriptor(descriptor);
            } else
                Log.e("BLeService",
                        "setCharacteristicNotification descriptor null");
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    /**
     * 因findGattServices（）方法中直接使用了mBluetoothGatt.getServices()，此处暂时无效
     *
     * @return
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null)
            return null;
        return mBluetoothGatt.getServices();
    }

    /**
     * 截取字符串
     *
     * @param paramUUID uuid
     * @return
     */
    private static String castUuid16Value(UUID paramUUID) {
        return paramUUID.toString().substring(4, 8);
    }

    /**
     * 查找相应读写服务，只有通过uuid找到两个设备对应的服务后才能进行数据的收发，否则数据监测无任何效果
     */
    private void findGattServices() {
        List<BluetoothGattService> gattServices = mBluetoothGatt.getServices();
        int isReadFind = 0;// 是否查找到读取数据服务
        int isWriteFind = 0;// 是否查找到写入数据服务
        String str;
        BluetoothGattCharacteristic wCharacteristic, rCharacteristic;// 定义读写BluetoothGattCharacteristic
        if (gattServices == null)
            return;
        /**
         * 针对体脂秤产品读取相应的读写uuid---------start-----------------------------
         */
        wCharacteristic = null;
        rCharacteristic = null;
        for (BluetoothGattService gattService : gattServices) {
            str = castUuid16Value(gattService.getUuid());
            // ffe0||ffe5位读取服务
            if (str.equalsIgnoreCase("ffe0") || str.equalsIgnoreCase("ffe5")) {
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService
                        .getCharacteristics();
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    if (gattCharacteristic.getProperties() == 0x10) {
                        for (BluetoothGattDescriptor descriptor : gattCharacteristic
                                .getDescriptors()) {
                            if (UUID_HEART_RATE_MEASUREMENT.equals(descriptor
                                    .getUuid())) {
                                rCharacteristic = gattCharacteristic;
                                break;
                            }
                        }
                    }
                    // ffe9写入服务
                    if ((gattCharacteristic.getProperties() & 0xC) != 0
                            && castUuid16Value(gattCharacteristic.getUuid())
                            .equalsIgnoreCase("ffe9")) {
                        wCharacteristic = gattCharacteristic;
                    }
                }
            }
        }
        if (rCharacteristic != null && wCharacteristic != null) {
            mNotifyCharacteristic = rCharacteristic;
            mWriteCharacteristic = wCharacteristic;
            mHandler.postDelayed(mReadCharacteristicRun, 100);// 延迟100ms执行，关闭方法：mHandler.removeCallbacks(runnable);
            return;
        }
        /**
         * 针对体脂秤产品读取相应的读写uuid---------end-----------------------------------------
         */
        /**
         * 针对血压计产品读取相应的读写uuid++++++++++start++++++++++++++++++++++++++++++++++
         */
        wCharacteristic = null;
        rCharacteristic = null;
        for (BluetoothGattService gattService : gattServices) {
            str = castUuid16Value(gattService.getUuid());// fbb0
            if (str.charAt(0) < '3')
                continue;//结束本次循环，执行下一次
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService
                    .getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                if ((gattCharacteristic.getProperties() & 0x10) != 0) {// 18
                    for (BluetoothGattDescriptor descriptor : gattCharacteristic// 00002902-0000-1000-8000-00805f9b34fb
                            .getDescriptors()) {
                        if (UUID_HEART_RATE_MEASUREMENT.equals(descriptor
                                .getUuid())) {// 读取数据的UUID相同
                            rCharacteristic = gattCharacteristic;
                            isReadFind = 1;
                            break;
                        }
                    }
                }
                if (isReadFind != 0)
                    break;
            }
            if (isReadFind != 0) {
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    if (UUID_HEART_RATE_MEASUREMENT.equals(gattCharacteristic // UUID_HEART_RATE_MEASUREMENT=00002902-0000-1000-8000-00805f9b34fb
                            .getUuid())) // gattCharacteristic.getUuid() = 0000fbb1-494c-4f47-4943-544543480000
                        continue;
                    Log.d("BLeService",
                            "getProperties: "
                                    + gattCharacteristic.getProperties());
                    if (gattCharacteristic.getProperties() == 0xa // 10 = A
                            || gattCharacteristic.getProperties() == 0x8
                            || gattCharacteristic.getProperties() == 0xc) {
                        wCharacteristic = gattCharacteristic;
                        isWriteFind = 1;
                        break;
                    }
                }

            }
            if (isReadFind != 0 && isWriteFind != 0) {
                mNotifyCharacteristic = rCharacteristic;
                mWriteCharacteristic = wCharacteristic;
                mHandler.postDelayed(mReadCharacteristicRun, 100);
                break;
            }
            isReadFind = 0;
            isWriteFind = 0;
        }
        /**
         * 针对血压计产品读取相应的读写uuid++++++++++++++end++++++++++++++++++++++++++++++++++
         */
    }

}
