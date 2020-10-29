package com.hijacker;

/*
    Copyright (C) 2019  Christos Kyriakopoulos

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

import android.os.FileObserver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.hijacker.AP.getAPByMac;
import static com.hijacker.MainActivity.BAND_2;
import static com.hijacker.MainActivity.BAND_5;
import static com.hijacker.MainActivity.BAND_BOTH;
import static com.hijacker.MainActivity.MAX_READLINE_SIZE;
import static com.hijacker.MainActivity.aircrack_dir;
import static com.hijacker.MainActivity.airodump_dir;
import static com.hijacker.MainActivity.always_cap;
import static com.hijacker.MainActivity.band;
import static com.hijacker.MainActivity.busybox;
import static com.hijacker.MainActivity.cap_path;
import static com.hijacker.MainActivity.cap_tmp_path;
import static com.hijacker.MainActivity.debug;
import static com.hijacker.MainActivity.enable_monMode;
import static com.hijacker.MainActivity.enable_on_airodump;
import static com.hijacker.MainActivity.iface;
import static com.hijacker.MainActivity.last_action;
import static com.hijacker.MainActivity.last_airodump;
import static com.hijacker.MainActivity.notification;
import static com.hijacker.MainActivity.prefix;
import static com.hijacker.MainActivity.refreshState;
import static com.hijacker.MainActivity.runInHandler;
import static com.hijacker.MainActivity.menu;
import static com.hijacker.ST.getSTByMac;
import static com.hijacker.Shell.enableMonMode;
import static com.hijacker.Shell.exitShell;
import static com.hijacker.Shell.getFreeShell;
import static com.hijacker.Shell.runOne;

class Airodump{
    static final String TAG = "HIJACKER/Airodump";
    private static int channel = 0;
    private static boolean handshake;
    private static boolean forWEP = false, forWPA = false, running = false, isIsolated = false;
    private static String mac = null;
    private static String capFile = null;
    static CapFileObserver capFileObserver = null;
    private static Shell sudo = null;

    static void reset(){
        stop();
        channel = 0;
        forWEP = false;
        forWPA = false;
        mac = null;
        capFile = null;
        handshake = false;
    }
    static void setChannel(int ch){
        if(isRunning()){
            Log.e(TAG, "Can't change settings while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        channel = ch;
    }
    static void setMac(String new_mac){
        if(isRunning()){
            Log.e(TAG, "Can't change settings while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        mac = new_mac;
    }

    static void setForWEP(boolean bool){
        if(isRunning()){
            Log.e(TAG, "Can't change setting while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        if(forWPA){
            Log.e(TAG, "Can't change setting while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        forWEP = bool;
    }

    static void setForWPA(boolean bool){
        if(isRunning()){
            Log.e(TAG, "Can't change setting while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        if(forWEP){
            Log.e(TAG, "Can't change setting while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        forWPA = bool;
    }

    static void setAP(AP ap){
        if(isRunning()){
            Log.e(TAG, "Can't change setting while airodump is running");
            throw new IllegalStateException("Airodump is still running");
        }
        mac = ap.mac;
        channel = ap.ch;
    }
    static int getChannel(){ return channel; }
    static String getMac(){ return mac; }
    static String getCapFile(){
        if(capFileObserver.found_cap_file() && writingToFile())
            return capFile;
        else return null;
    }
    static boolean writingToFile(){ return (forWEP || forWPA || always_cap) && isRunning(); }
    static void startClean(){
        reset();
        start();
    }
    static void startClean(AP ap){
        reset();
        setAP(ap);
        if (ap.sec == AP.WEP) setForWEP(true);
        else if (ap.sec == AP.WPA) setForWPA(true);
        start();
    }
    static void startClean(int ch){
        reset();
        setChannel(ch);
        start();
    }
    static void start(){
        // Stop any current airodump instances
        stop();

        sudo = getFreeShell();

        // Construct the command
        String cmd = "nohup " + airodump_dir + " --update 1 --berlin 1 --band ";

        if(band==BAND_5 || band==BAND_BOTH || channel>20) cmd += "a";
        if((band==BAND_2 || band==BAND_BOTH) && channel<=20) cmd += "bg";

        cmd += " -w " + cap_tmp_path + "/cap --output-format csv";

        if((always_cap && !forWEP) || forWPA) cmd += ",pcap ";
        else cmd += " ";

        // If we are starting for WEP capture, capture only IVs
        if(forWEP) cmd += "--ivs ";

        // If we have a valid channel, select it (airodump does not recognize 5ghz channels here)
        if(channel>0 && channel<20) {
            cmd += "--channel " + channel + " ";
            // manually configure the channel since airodump is incapable - maybe not anymore -
            //sudo.run(String.format(Locale.getDefault(), "iw %s set channel %d", iface, channel));
        }

        // If we have a specific MAC, listen for it
        if(mac!=null) {
            cmd += "--bssid " + mac + " ";
            isIsolated = true;
        }

        cmd += iface;

        // cap with redirects so no hang ups
        cmd += " </dev/null &>/dev/null &";

        // Enable monitor mode
        if(enable_on_airodump) enableMonMode(iface);

        capFile = null;
        running = true;
        capFileObserver.startWatching();

        if(debug) Log.d("HIJACKER/Airodump.start", cmd);

        sudo.run(cmd);
        last_action = System.currentTimeMillis();
        last_airodump = cmd;

        runInHandler(new Runnable(){
            @Override
            public void run(){
                if(menu!=null){
                    menu.getItem(1).setIcon(R.drawable.stop_drawable);
                    menu.getItem(1).setTitle(R.string.stop);
                }
                refreshState();
                notification();
            }
        });
    }
    static void stop(){
        last_action = System.currentTimeMillis();
        running = false;
        isIsolated = false;
        capFileObserver.stopWatching();
        runInHandler(new Runnable(){
            @Override
            public void run(){
                if(menu!=null){
                    menu.getItem(1).setIcon(R.drawable.start_drawable);
                    menu.getItem(1).setTitle(R.string.start);
                }
            }
        });
        if(debug) Log.d("HIJACKER/Airodump.stop", "Killing airodump-ng");
        runOne(busybox + " kill $(" + busybox + " pidof airodump-ng)");
        AP.saveAll();
        ST.saveAll();
        if (sudo != null) {
            exitShell(sudo);
            sudo = null;
        }
        runInHandler(new Runnable(){
            @Override
            public void run(){
                refreshState();
                notification();
            }
        });
    }
    static boolean isRunning(){
        return running;
    }
    public static void addAP(String essid, String mac, String enc, String cipher, String auth,
                             int pwr, int beacons, int data, int ivs, int ch){
        AP temp = getAPByMac(mac);

        if(temp==null) new AP(essid, mac, enc, cipher, auth, pwr, beacons, data, ivs, ch);
        else temp.update(essid, enc, cipher, auth, pwr, beacons, data, ivs, ch, handshake);
    }
    public static void addST(String mac, String bssid, String probes, int pwr, int lost, int frames){
        ST temp = getSTByMac(mac);

        if (temp == null) new ST(mac, bssid, pwr, lost, frames, probes);
        else temp.update(bssid, pwr, lost, frames, probes);
    }

    static class CapFileObserver extends FileObserver{
        static String TAG = "HIJACKER/CapFileObs";
        String master_path;
        Shell shell = null;
        boolean found_cap_file = false;
        public CapFileObserver(String path, int mask) {
            super(path, mask);
            master_path = path;
        }
        @Override
        public void onEvent(int event, @Nullable String path){
            if(path==null){
                Log.e(TAG, "Received event " + event + " for null path");
                return;
            }

            if(debug) Log.d(TAG, "Received normal event for " + path);

            boolean isPcap = path.endsWith(".cap");
            boolean isIvs = path.endsWith(".ivs");

            switch(event){
                case FileObserver.CREATE:
                    // Airodump started, pcap or csv file was just created

                    if(isPcap || isIvs){
                        capFile = master_path + '/' + path;
                        found_cap_file = true;
                    }
                    break;

                case FileObserver.MODIFY:
                    // Airodump just updated pcap or csv
                    if(isPcap && isIsolated){
                        readCap(master_path + '/' + path);
                    }
                    else {
                        readCsv(master_path + '/' + path, shell);
                    }
                    break;

                default:
                    // Unknown event received (should never happen)
                    Log.e(TAG, "Unknown event received: " + event);
                    Log.e(TAG, "for file " + path);
                    break;
            }
        }
        @Override
        public void startWatching(){
            super.startWatching();
            shell = getFreeShell();

            if(debug) Log.d(TAG, "CapFileObserver starting");

            found_cap_file = false;
        }
        @Override
        public void stopWatching(){
            super.stopWatching();

            if(debug) Log.d(TAG, "CapFileObserver stopping");

            if(shell!=null) {
                if (writingToFile()) {
                    shell.run(busybox + " mv " + capFile + " " + cap_path + '/');
                }
                shell.run(busybox + " rm " + cap_tmp_path + "/*");
                shell.done();
                shell = null;
            }
        }
        boolean found_cap_file(){
            return found_cap_file;
        }
        void readCap(String cap_path){
            Shell sudo = getFreeShell();
            sudo.run(aircrack_dir + " " + cap_path + " | grep -Eo \"[0-9][0-9]? handshake\"");
            BufferedReader out = sudo.getShell_out();
            try {
                String result = out.readLine();
                if (result.charAt(0) != '0') handshake = true;
            }catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
            finally{
                sudo.done();
            }
        }
        void readCsv(String csv_path, @NonNull Shell shell){
            shell.clearOutput();
            shell.run(busybox + " cat " + csv_path + "; echo ENDOFCAT");
            BufferedReader out = shell.getShell_out();
            try {

                int type = 0;           // 0 = AP, 1 = ST
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                while(true){
                    String line = out.readLine();
                    if(debug) Log.d(TAG, line);
                    if(line.equals("ENDOFCAT"))
                        break;

                    if(line.equals(""))
                        continue;
                    if(line.startsWith("BSSID")) {
                        type = 0;
                        continue;
                    }else if(line.startsWith("Station")) {
                        type = 1;
                        continue;
                    }

                    line = line.replace(", ", ",");
                    String[] fields = line.split(",");
                    if(debug) Log.i(TAG, line);
                    if(type == 0){
                        // Parse AP
                        // BSSID, First time seen, Last time seen, channel, Speed, Privacy,Cipher,
                        // Authentication, Power, # beacons, # IVs (or data??), LAN IP, ID-length, ESSID, Key

                        String bssid = fields[0];
                        try {
                            Date first_seen = sdf.parse(fields[1]);
                            Date last_seen = sdf.parse(fields[2]);
                        }catch(ParseException e){
                            e.printStackTrace();
                            Log.e(TAG, e.toString());
                        }
                        int ch = Integer.parseInt(fields[3].replace(" ", ""));
                        int speed = Integer.parseInt(fields[4].replace(" ", ""));
                        String enc = fields[5].trim();
                        String cipher = fields[6].trim();
                        String auth = fields[7].trim();
                        int pwr = Integer.parseInt(fields[8].replace(" ", ""));
                        int beacons = Integer.parseInt(fields[9].replace(" ", ""));
                        int data = Integer.parseInt(fields[10].replace(" ", ""));
                        String lan_ip = fields[11].replace(" ", "");
                        int id_length = Integer.parseInt(fields[12].replace(" ", ""));
                        String essid = id_length > 0 ? fields[13] : null;

                        String key = fields.length>14 ? fields[14] : null;

                        addAP(essid, bssid, enc, cipher, auth, pwr, beacons, data, 0, ch);
                    }else{
                        // Parse ST
                        //Station MAC, First time seen, Last time seen, Power, # packets, BSSID, Probed ESSIDs

                        String mac = fields[0];
                        try {
                            Date first_seen = sdf.parse(fields[1]);
                            Date last_seen = sdf.parse(fields[2]);
                        }catch(ParseException e){
                            e.printStackTrace();
                            Log.e(TAG, e.toString());
                        }
                        int pwr = Integer.parseInt(fields[3].replace(" ", ""));
                        int packets = Integer.parseInt(fields[4].replace(" ", ""));
                        String bssid = fields[5];
                        if(bssid.charAt(0)=='(') bssid = null;

                        String probes = "";
                        if(fields.length==7) {
                            probes = fields[6];
                        }else if(fields.length>7){
                            // Multiple probes are separated by comma, so concatenate them
                            probes = "";
                            for(int i=6; i<fields.length; i++){
                                probes += fields[i] + ", ";
                            }
                            probes = probes.substring(0, probes.length()-2);
                        }

                        addST(mac, bssid, probes, pwr, 0, packets);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        }
    }
}
