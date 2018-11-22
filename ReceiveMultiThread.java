import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class ReceiveMultiThread implements Runnable {

    private String thisNode;
    private Map<String, MyNode> knownNodes;
    private DatagramSocket socket;
    private MyNode hub;
    private Map<String, Long> rttVector;
    private Map<String, Long> rttSums = new HashMap<>();
    private ArrayList<String> eventLog;




    public ReceiveMultiThread(String thisNode, DatagramSocket socket, Map<String, MyNode> knownNodes, MyNode hub,
                              Map<String, Long> rttVector, ArrayList<String> eventLog) {
        this.thisNode = thisNode;
        this.socket = socket;
        this.knownNodes = knownNodes;
        this.hub = hub;
        this.rttVector = rttVector;
        this.eventLog = eventLog;
    }

    public void run() {

        Stack<DatagramPacket> buffer = new Stack<>();


        Thread taskHandler = new Thread(new TaskHandler(thisNode, socket, knownNodes, hub, rttVector, eventLog, buffer));
        taskHandler.start();

        try {

            //System.out.println("receive thread started");
            while (true) {

                //receive packet
                byte[] message1 = new byte[64000];
                DatagramPacket receivePacket = new DatagramPacket(message1, message1.length);
                System.out.println("waiting to receive at port: " + socket.getLocalPort());
                socket.receive(receivePacket);

                buffer.push(receivePacket);


            }

        } catch (Exception e) {

        }
    }

}