package com.example.wangyu892449346.bluetoothserver.BlueTooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.example.wangyu892449346.bluetoothserver.BlueTooth.receiver.BluetoothReceiver;
import com.example.wangyu892449346.bluetoothserver.GPS.GPSActivity;
import com.example.wangyu892449346.bluetoothserver.R;
import com.example.wangyu892449346.bluetoothserver.util.DataUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public class BluetoothActivity extends GPSActivity implements BluetoothReceiver.BRInteraction {
    /**
     * 数据操作类
     */
    public DataUtil dataUtil = new DataUtil().getInstance();
    /*
    * 这些是从蓝牙获取的参数,这条是蓝牙串口通用的UUID，不要更改
    * */
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    /**
     * 目标蓝牙设备的地址
     * */
    public String TargetDeviceAddr = null;
    /**
     * 当接受到蓝牙消息
     */
    private static final int ReceiveMsg = 1;
    /**
     * 当蓝牙匹配成功并获取到了目标设备的地址
     * */
    private static final int ReceiveAddress = 10086;
    /**
     * 接收到的字符串
     */
    private String ReceiveData = "";
    private MyHandler handler;
    /**
     * device var
     */
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;
    private InputStream inStream = null;
    private ReceiveThread rThread = null;  //数据接收线程
    private Toast mToast;
    private OnChangeText onChangeText;
    private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothReceiver bluetoothReceiver;

    public void setOnChangeText(OnChangeText onChangeText) {
        this.onChangeText = onChangeText;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initListener();
        InitBluetooth();
        initReceive();
        handler = new MyHandler();
    }

    private void initReceive() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
        intentFilter.addAction("android.bluetooth.device.action.FOUND");
        intentFilter.setPriority(10086);
        bluetoothReceiver = new BluetoothReceiver();
        registerReceiver(bluetoothReceiver, intentFilter);
        bluetoothReceiver.setBRInteractionListener(this);
    }

    private void initListener() {
        if (bluetoothAdapter == null) {
            Toast.makeText(BluetoothActivity.this, R.string.no_support, Toast.LENGTH_SHORT).show();
        }
    }

    public String InitBluetooth() {
        //得到一个蓝牙适配器
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            showToast(getString(R.string.no_support));
            finish();
            return getResources().getString(R.string.no_support);
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (rThread != null) {
                btSocket.close();
                btSocket = null;
                rThread.join();
            }
            unregisterReceiver(bluetoothReceiver);
            this.finish();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void connect() {
        //判断蓝牙是否打开
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }
        mBluetoothAdapter.startDiscovery();
        //创建连接
        if (!TextUtils.isEmpty(TargetDeviceAddr) && btSocket == null) {
            mayRequestLocation();
            onChangeText.changeText(getString(R.string.loading));
            new ConnectTask().execute(TargetDeviceAddr);
        } else if (TextUtils.isEmpty(TargetDeviceAddr)){
            onChangeText.changeText(getString(R.string.finding));
            showToast(getString(R.string.finding));
        }
    }

    @Nullable
    public String disConnect() {
        if (btSocket != null) {
            try {
                btSocket.close();
                btSocket = null;
                if (rThread != null) {
                    rThread.join();
                }
                onChangeText.changeText(getString(R.string.close));
                return getResources().getString(R.string.close);
            } catch (InterruptedException e) {
                e.printStackTrace();
                onChangeText.changeText(getString(R.string.close_fail));
                return getResources().getString(R.string.close_fail);
            } catch (IOException e) {
                e.printStackTrace();
                onChangeText.changeText(getString(R.string.close_fail));
                return getResources().getString(R.string.close_fail);
            }
        }
        return null;
    }

    /**
     * 显示Toast
     *
     * @param text 显示Toast的内容
     */
    public void showToast(String text) {
        if (mToast == null) {
            mToast = Toast.makeText(this, text, Toast.LENGTH_LONG);
        } else {
            mToast.setText(text);
        }
        mToast.show();
    }

    @Override
    public void OnFoundTargetDevice(String targetDeviceAdr) {
        TargetDeviceAddr = targetDeviceAdr;
        onChangeText.changeText("找到设备了，可以进行连接了～");
        Log.i("FoundDevice",targetDeviceAdr);
    }

    /**
     * 请求获取用户粗略定位的权限
     * android 6.0及其以上使用
     */
    private void mayRequestLocation() {
        if (Build.VERSION.SDK_INT >= 23) {
            int check = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (check != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, 0);
            }
        }
    }

    /**
     * 连接蓝牙设备的异步任务
     */
    private class ConnectTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... params) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(params[0]);
            Log.e("Notzuonotdied", getString(R.string.start) + device.toString());
            try {
                btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                btSocket.connect();
                Log.e("error", "ON RESUME: BT connection established, data transfer link open.");
            } catch (IOException e) {
                try {
                    btSocket.close();
                    e.printStackTrace();
                    return getString(R.string.socket_fail);
                } catch (IOException e2) {
                    e.printStackTrace();
                    Log.e("error", "ON RESUME: Unable to close socket during connection failure", e2);
                    return getString(R.string.close_fail);
                }
            }
            //取消搜索
            mBluetoothAdapter.cancelDiscovery();
            try {
                outStream = btSocket.getOutputStream();
            } catch (IOException e) {
                Log.e("error", "ON RESUME: Output stream creation failed.", e);
                return getString(R.string.steam_fail);
            }
            return getString(R.string.socket_success);
        }

        @Override    //这个方法是在主线程中运行的，所以可以更新界面
        protected void onPostExecute(String result) {
            if (TextUtils.equals(result, getString(R.string.socket_success))) {
                //连接成功则启动监听
                rThread = new ReceiveThread();
                rThread.start();
            }
            onChangeText.changeText(result);
            super.onPostExecute(result);
        }
    }

    /**
     * 从蓝牙接收信息的线程
     */
    private class ReceiveThread extends Thread {
        @Override
        public void run() {
            while (btSocket != null) {
                // 缓冲区大小,这个缓冲区是为了拼接蓝牙发送过来的字符
                // 在本项目中硬件设备每次发送的字节在18~50之间
                // 示例字符串：{x:.+0.+0.+0,y:.+123.+33.-66,z:.+678.-1223.+0}
                int count = 55;
                //定义一个存储空间buff
                byte[] buff = new byte[count];
                try {
                    inStream = btSocket.getInputStream();
                    int readCount = 0; // 已经成功读取的字节的个数
                    while (readCount < count) {
                        //读取数据存储在buff数组中
                        if (btSocket.isConnected()) {
                            readCount += inStream.read(buff, readCount, count - readCount);
                        }
                    }
                    processBuffer(buff, count);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * 处理流
         *
         * @param buff 缓冲区
         * @param size 缓冲区大小
         * */
        private void processBuffer(byte[] buff, int size) {
            byte[] newBuff = new byte[size];
            int length = 0;
            for (int i = 0; i < size; i++) {
                if (buff[i] >= '\0') {// 当读取到的字符位'\0'长度加1
                    length++;
                }
            }
            System.arraycopy(buff, 0, newBuff, 0, length);
            final List<String> list = dataUtil.getList4Array(new String(newBuff).trim());
            int count = 0;
            while (count < list.size()) {
                Message msg = Message.obtain();
                msg.what = ReceiveMsg;
                ReceiveData = list.get(count++);
                handler.sendMessage(msg);  //发送消息:系统会自动调用handleMessage( )方法来处理消息
            }
        }
    }

    /**
     * 更新界面的Handler类
     */
    private class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ReceiveMsg:
                    Log.i("Data:", ReceiveData);//Data:: x:.+ 0.+ 0.+ 0,y:.+ 0.+ 0.+ 0,z:.+ 0.+ 0
                    if (!TextUtils.isEmpty(ReceiveData) && !"".equals(ReceiveData.trim())) {
                        List<String> list = dataUtil.getList(ReceiveData);
                        Log.d("Notzuonotdied", "开始处理数据了～: " + list);
                        if (null != list && list.size() != 0) {
                            onChangeText.handleMsg(list, 'N');
                        }
                    }
                    break;
                case ReceiveAddress:

                    break;
            }
        }
    }
}
