import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Created by yuguanxu on 4/11/17.
 */
public class Client {
    private DatagramSocket clientSocket;
    private int N;
    private int MSS;
    private String fileName;
    private int winLow;
    private int winHigh;
    private int timerCounter;
    private int lastPackatNum;

    private TreeMap<Integer, DatagramPacket> unsendPackets = new TreeMap<>();
    private TreeMap<Integer, DatagramPacket> unAckPackets = new TreeMap<>();

    private Object lock = new Object();
    private Object timer_lock = new Object();
    private Timer receivertimer;

    private PackageCreator pkCreator;

    private Set<Integer> postPkSet = new HashSet<>();


    public Client(int N_num, int MSS_num, String fileName_str, int timer_time, int portNum, String serverHostName) throws IOException {
        //clientSocket = new DatagramSocket(9999); // sender socket set port number
        pkCreator = new PackageCreator(serverHostName);
        clientSocket = new DatagramSocket();
        N = N_num;
        MSS = MSS_num;
        fileName = fileName_str;
        winLow = 0;
        winHigh = winLow + N - 1;
        short pkType = 0b0101010101010101;
        InputStream filedata = new FileInputStream(fileName);
        int sequenceNum = 1;
        receivertimer = new Timer();
        timerCounter = timer_time;
        byte[] b = new byte[MSS];

        byte[] MSSnum = ByteBuffer.allocate(4).putInt(MSS).array();
        unsendPackets.put(0, pkCreator.createPacket(0, MSSnum, pkType, portNum));

        while (filedata.read(b) != -1){
            unsendPackets.put(sequenceNum, pkCreator.createPacket(sequenceNum, b, pkType, portNum));
            sequenceNum++;
            b = new byte[MSS];
        }
        lastPackatNum = sequenceNum - 1;

        short fin = 0b0111111111111111;
        unsendPackets.put(lastPackatNum+1, pkCreator.createPacket(lastPackatNum+1, b, fin, portNum));

    }

    private void rdt_send(int sequenceNumber, byte[] data, short pkType, DatagramSocket clientSocket, int port) throws IOException {
        DatagramPacket pk = pkCreator.createPacket(sequenceNumber, data, pkType, port);
        clientSocket.send(pk);
    }

    private class TimerTaskTest01 extends TimerTask {
        Timer timer;
        public TimerTaskTest01(Timer t){
            timer = t;
        }

        public void run() {
            synchronized (lock){
                int flag = 0;
                for(Map.Entry<Integer, DatagramPacket> entry : unAckPackets.entrySet()){
                    try {
                        System.out.println("Packet loss, sequence number = " + entry.getKey());
                        clientSocket.send(entry.getValue());
                        System.out.println("Start to retransmit packet: " + entry.getKey());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                synchronized (timer_lock) {
                    timer.cancel();
                    timer = new Timer();
                    timer.schedule(new TimerTaskTest01(timer), timerCounter);
                    receivertimer = timer;
                }
            }
        }
    }

    public void sender() throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        while(true){
            synchronized (lock){
                if(unsendPackets.size() == 0 && unAckPackets.size() == 0){
                    long end = System.currentTimeMillis();
                    System.out.println();
                    System.out.println("The running time is " + (end - start));
                    System.exit(0);
                }
                int currentUnAckedNum = unAckPackets.size();
                for(int i = winLow; i <= winHigh; i++){
                    if(i > lastPackatNum){
                        break;
                    }
                    if(unsendPackets.containsKey(i)) {
                        DatagramPacket nextPacket = unsendPackets.remove(i); // delete
                        unAckPackets.put(i, nextPacket);  // put into unacknowledged map
                        clientSocket.send(nextPacket);// retrieve packet from unAcked packets
                        //System.out.println("Start sending packet " + i);
                        if (i == winLow) { // only the first window need sender to set timer
                            synchronized (timer_lock) {
                                receivertimer.schedule(new TimerTaskTest01(receivertimer), timerCounter);
                            }
                        }
                    }
                }
                lock.wait();
            }
        }


        /*while(filedata.read(b) != -1){
            rdt_send(sequenceNum, b, pkType, clientSocket);
            b = new byte[MSS];
            sequenceNum++;
        }*/
    }

    public void receiver() throws IOException, InterruptedException {
        int firstPacket = 0;
        byte[] data = new byte[4];
        DatagramPacket pk = new DatagramPacket(data, data.length);
        int expectAckNum = 0;


        while (true){
            clientSocket.receive(pk); // equal to wait
            int sequenceNumber = ByteBuffer.wrap(data).getInt();
            //System.out.println("Receive ack " + sequenceNumber);

            /*if(sequenceNumber >= expectAckNum){
                expectAckNum = sequenceNumber + 1;
                synchronized (lock){
                    int firstUnAckKey = unAckPackets.firstEntry().getKey();
                    for(int k = firstUnAckKey; k <= sequenceNumber; k++){
                        unAckPackets.remove(k);
                    }
                    winLow = sequenceNumber + 1;
                    winHigh = winLow + N - 1;
                    synchronized (timer_lock){
                        receivertimer.cancel();
                        if(sequenceNumber != lastPackatNum) {
                            receivertimer = new Timer();
                            receivertimer.schedule(new TimerTaskTest01(receivertimer), timerCounter);
                        }else {
                            lock.notify();
                            System.exit(0);
                        }
                    }
                    lock.notify();
                }
            }*/
            if(sequenceNumber >= expectAckNum){
                synchronized (lock) {
                    unAckPackets.remove(sequenceNumber);
                    if (sequenceNumber == expectAckNum) {
                        expectAckNum++;
                        while(postPkSet.contains(expectAckNum)){
                            postPkSet.remove(expectAckNum);
                            expectAckNum++;
                        }

                        winLow = expectAckNum;
                        winHigh = winLow + N - 1;

                        synchronized (timer_lock){
                            receivertimer.cancel();
                            if(sequenceNumber != lastPackatNum) {
                                receivertimer = new Timer();
                                receivertimer.schedule(new TimerTaskTest01(receivertimer), timerCounter);
                            }else {
                                lock.notify();
                                //System.exit(0);
                            }
                        }
                    }else{
                        postPkSet.add(sequenceNumber);
                    }
                    lock.notify();
                }
            }
        }
    }
}
