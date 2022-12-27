package org.bako.bluetooth_connect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    static final int REQUEST_ENABLE_BT = 10;
    int mPairedDeviceCount = 0;
    Set<BluetoothDevice> mDevices;
    BluetoothAdapter mBluetoothAdapter;

    BluetoothDevice mRemoteDevice;
    BluetoothSocket mSocket = null;
    OutputStream mOutputStream = null;
    InputStream mInputStream = null;

    Thread mWorkerThread = null;

    String mStrDelimiter = "\n";
    char mCharDelimiter = '\n';

    byte[] readBuffer;
    int readBufferPosition;

    String act_on, act_off;

    String url;
    String values;
    //xml
    TextView illumi, temp, humi, ground_humi, ground_temp;
    Switch fever, pump, ventilator, humidifier;
    ImageView logo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkBluetooth();

        illumi = (TextView) findViewById(R.id.illumi);
        temp = (TextView) findViewById(R.id.temp);
        humi = (TextView) findViewById(R.id.humi);
        ground_humi = (TextView) findViewById(R.id.ground_humi);
        ground_temp = (TextView) findViewById(R.id.ground_temp);

        fever = (Switch) findViewById(R.id.fever);
        fever.setOnClickListener(onClickListener);
        pump = (Switch) findViewById(R.id.pump);
        pump.setOnClickListener(onClickListener);
        ventilator = (Switch) findViewById(R.id.ventilator);
        ventilator.setOnClickListener(onClickListener);
        humidifier = (Switch) findViewById(R.id.humidifier);
        humidifier.setOnClickListener(onClickListener);

        logo = (ImageView) findViewById(R.id.logo);
        logo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, WebViewPage.class);
                startActivity(intent);
            }

        });

        //url 설정
        url = "http://70.12.109.145:6060/SmartFARM/dataWrite.do";
        values = null;

    }


    //네트워킹
    public class NetworkTask extends AsyncTask<Void, Void, String> {

        private String url;
        private String values;

        public NetworkTask(String url, String values) {

            this.url = url;
            this.values = values;
        }

        @Override
        protected String doInBackground(Void... params) {

            URL obj = null;
            try {
                obj = new URL(url+values);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();

                con.setRequestMethod("GET");

                int responseCode = con.getResponseCode();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                //print result
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
        }
    }


    //스위치 클릭 이벤트
    View.OnClickListener onClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.fever:
                    act_on = "fon";
                    act_off = "foff";
                    CheckState(fever, act_on, act_off);
                    break;
                case R.id.pump:
                    act_on = "pon";
                    act_off = "poff";
                    CheckState(pump, act_on, act_off);
                    break;
                case R.id.ventilator:
                    act_on = "von";
                    act_off = "voff";
                    CheckState(ventilator, act_on, act_off);
                    break;
                case R.id.humidifier:
                    act_on = "hon";
                    act_off = "hoff";
                    CheckState(humidifier, act_on, act_off);
                    break;
            }
        }
    };

    void CheckState(Switch sw, String on, String off) {
        //스위치 켜질 때
        if (sw.isChecked()) {
            //Toast.makeText(getApplicationContext(), "켜짐", Toast.LENGTH_LONG).show();
            sendData(on);
        }
        //스위치 꺼질 때
        else {
            //Toast.makeText(getApplicationContext(), "꺼짐", Toast.LENGTH_LONG).show();
            sendData(off);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            mWorkerThread.interrupt();
            mInputStream.close();
            mOutputStream.close();
            mSocket.close();
        } catch (Exception e) {
        }
        super.onDestroy();
    }

    //페어링된 기기를 이름으로 찾기
    BluetoothDevice getDeviceFromBondedList(String name) {
        BluetoothDevice selectedDevice = null;
        for (BluetoothDevice device : mDevices) {
            if (name.equals(device.getName())) {
                selectedDevice = device;
                break;
            }
        }
        return selectedDevice;
    }

    //데이터 송신 함수
    void sendData(String msg) {
        //문자열 종료 표시
        msg += mStrDelimiter;

        try {
            // 문자열 전송
            mOutputStream.write(msg.getBytes());
        } catch (Exception e) {
            // 문자열 전송 도중 오류가 발생한 경우
            Toast.makeText(getApplicationContext(), "데이터 송신 함수", Toast.LENGTH_LONG).show();
            //finish();
            //어플리케이션 종료
        }
    }

    //원격 블루투스 장치와의 연결
    void connectToSelectedDevice(String selectedDeviceName) {
        mRemoteDevice = getDeviceFromBondedList(selectedDeviceName);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        try {
            // 소켓 생성
            mSocket = mRemoteDevice.createRfcommSocketToServiceRecord(uuid);
            //RFCOMM 채널을 통한 연결
            mSocket.connect();

            // 데이터 송수신을 위한 스트림 얻기
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();

            //데이터 수신 준비
            beginListenForData();
        } catch (Exception e) {
            //오류 발생시 종료
            Toast.makeText(getApplicationContext(), "장치 연결 실패", Toast.LENGTH_LONG).show();
            //finish();
        }
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        mWorkerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        //수신 데이터 확인
                        int bytesAvailable = mInputStream.available();
                        //데이터가 수신 된 경우
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mInputStream.read(packetBytes);

                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == mCharDelimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0,
                                            encodedBytes, 0, encodedBytes.length);
                                    //인코딩설정
                                    final String data = new String(encodedBytes, "UTF-8");
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        public void run() {
                                            // mEditReceive.setText(mEditReceive.getText().toString()+ data + mStrDelimiter);
                                            splitSensorData(data);
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        Toast.makeText(getApplicationContext(), "데이터 수신 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
                        //finish();
                    }
                }
            }
        });
        mWorkerThread.start();
    }

    //수신된 문자를 ,를 기준으로 스플릿
    void splitSensorData(String data) {
        String[] arr = data.split(",");

        illumi.setText(arr[0]);
        temp.setText(arr[1]);
        humi.setText(arr[2]);
        ground_temp.setText(arr[3]);
        ground_humi.setText(arr[4]);

        String values = "?li=" + arr[0] + "&it=" + arr[1] + "&ih=" + arr[2] + "&st=" +
                arr[3] + "&sh=" + arr[4] + "&pu=" + arr[5] +
                "&hu=" + arr[6] + "&he=" + arr[7] + "&co=" + arr[8];

        //AsyncTask를 통해 HttpURLConnection 수행
        NetworkTask networkTask = new NetworkTask(url, values);
        networkTask.execute();
    }

    //페어링된 기기 목록 및 기기 선택
    void selectDevice() {
        mDevices = mBluetoothAdapter.getBondedDevices();
        mPairedDeviceCount = mDevices.size();
        if (mPairedDeviceCount == 0) {
            //페어링 된 장치 없을 때
            Toast.makeText(getApplicationContext(), "페어링된 장치가 없습니다.", Toast.LENGTH_LONG).show();
            //finish();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("블루투스 장치 선택");

        //페어링 목록 작성
        List<String> listItems = new ArrayList<String>();
        for (BluetoothDevice device : mDevices) {
            listItems.add(device.getName());
        }
        listItems.add("취소");
        final CharSequence[] items =
                listItems.toArray(new CharSequence[listItems.size()]);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                if (item == mPairedDeviceCount) {
                    //연결않고 취소 버튼
                    Toast.makeText(getApplicationContext(), "연결할 장치를 선택하지 않았습니다.", Toast.LENGTH_LONG).show();
                    //finish();
                } else {
                    //디바이스 선택후 연결 시도
                    connectToSelectedDevice(items[item].toString());
                }
            }
        });
        //뒤로가기 버튼 비활성화
        builder.setCancelable(false);
        AlertDialog alert = builder.create();
        alert.show();
    }

    //블루투스 상태 확인
    void checkBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            //블루투스 미지원
            Toast.makeText(getApplicationContext(), "기기가 블루투스를 지원하지 않습니다.", Toast.LENGTH_LONG).show();
            //finish();
        } else {
            //블루투스 지원
            if (!mBluetoothAdapter.isEnabled()) {
                //블루투스 비활성 상태일 경우 사용자에게 사용요청
                Toast.makeText(getApplicationContext(), "현재 블루투스가 비활성 상태입니다.", Toast.LENGTH_LONG).show();
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else
                //블루투스 활성 상태인경우
                //페어링 된 기기목록을 보여주고 연결할 장치를 선택
                selectDevice();
        }
    }

    //블루투스가 비활성 상태인 경우 처리
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    //블루투스 활성 상태로 변경
                    selectDevice();
                } else if (resultCode == RESULT_CANCELED) {
                    //블루투스 비활성 상태면 종료
                    Toast.makeText(getApplicationContext(), "블루투스를 사용할 수 없어 프로그램을 종료합니다.",
                            Toast.LENGTH_LONG).show();
                    //finish();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}







