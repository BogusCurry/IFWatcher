package com.valxp.app.infiniteflightwatcher;

import android.content.Context;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * Created by ValXp on 2/20/15.
 */
public class ForeFlightClient extends Thread {
    public static final int FOREFLIGHT_PORT = 49002;
    public static final int PACKET_SIZE = 512;
    private Context mContext;
    private boolean mKeeprunning;
    private GPSListener mListener;

    public interface GPSListener {
        void OnGPSFixReceived(GPSData data);
    }

    public ForeFlightClient(Context context, GPSListener listener) {
        super();
        mContext = context;
        mListener = listener;
    }

    public void stopClient() {
        mKeeprunning = false;
        interrupt();
    }

    @Override
    public void run() {
        mKeeprunning = true;
        byte[] buffer = new byte[PACKET_SIZE];
        DatagramSocket socket;
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setReceiveBufferSize(PACKET_SIZE);
            socket.bind(new InetSocketAddress(FOREFLIGHT_PORT));
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (mKeeprunning) {
            try {
                socket.receive(packet);
            } catch (Exception e) {
                Log.e("ForeFlightClient", "Socket receive interrupted" + e.getMessage());
            }
            if (mListener != null) {
                GPSData data = parseData(packet);
                if (data != null)
                    mListener.OnGPSFixReceived(data);
            }
        }
        socket.close();
    }

    public static class ATTData {
        public double yaw; // heading
        public double pitch;
        public double roll;
    }
    public static class GPSData {
        public double lat;
        public double lon;
        public double altitude; // meters
        public double heading;
        public double groundSpeed; // meters per second
        public long timestamp;
        public String ip;
    }
    public static class TrafficGPSData extends GPSData {
        public int ICAO;
        public boolean isAirborne;
        public float verticalSpeed;
        public String callsign;
    }

    private GPSData parseData(DatagramPacket packet) {
        String data = new String(packet.getData()).substring(0, packet.getLength());

        if (data.length() < 4)
            return null;
        String[] dataList = data.split(",");
        if (data.startsWith("XATT")) {
            ATTData att = new ATTData();
            int counter = 0;
            for (String str : dataList) {
                switch (counter) {
                    case 1:
                        att.yaw = Double.parseDouble(str);
                    break;
                    case 2:
                        att.pitch = Double.parseDouble(str);
                    break;
                    case 3:
                        att.roll = Double.parseDouble(str);
                    break;
                }
                ++counter;
            }
            return null;

        } else if (data.startsWith("XGPS")) {
            GPSData gps = new GPSData();
            gps.timestamp = (((TimeProvider.getTime() / 1000) + 11644473600l) * 10000000);
            int counter = 0;
            for (String str : dataList) {
                switch (counter) {
                    case 1:
                        gps.lat = Double.parseDouble(str);
                    break;
                    case 2:
                        gps.lon = Double.parseDouble(str);
                    break;
                    case 3:
                        gps.altitude = Double.parseDouble(str) * 3.28084;
                    break;
                    case 4:
                        gps.heading = Double.parseDouble(str);
                    break;
                    case 5:
                        gps.groundSpeed = Double.parseDouble(str) * 1.943844;
                    break;
                }
                ++counter;
            }
            return gps;
        } else if (data.startsWith("XTRAFFIC")) {
            TrafficGPSData gps = new TrafficGPSData();
            gps.timestamp = (((TimeProvider.getTime() / 1000) + 11644473600l) * 10000000);
            int counter = 0;
            for (String str : dataList) {
                switch (counter) {
                    case 1:
                        gps.ICAO = Integer.parseInt(str);
                        break;
                    case 2:
                        gps.lat = Double.parseDouble(str);
                        break;
                    case 3:
                        gps.lon = Double.parseDouble(str);
                        break;
                    case 4:
                        gps.altitude = Double.parseDouble(str); // Already in feet
                        break;
                    case 5:
                        gps.verticalSpeed = Float.parseFloat(str);
                        break;
                    case 6:
                        gps.isAirborne = Boolean.parseBoolean(str);
                        break;
                    case 7:
                        gps.heading = Double.parseDouble(str);
                        break;
                    case 8:
                        gps.groundSpeed = Double.parseDouble(str); // Already in kts
                        break;
                    case 9:
                        gps.callsign = str;
                        break;
                }
                ++counter;
            }
            return gps;
        }
        return null;
    }
}
