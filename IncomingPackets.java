import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class IncomingPackets implements Runnable {

    private String thisNode;
    private Map<String, MyNode> knownNodes;
    private DatagramSocket socket;
    private MyNode hub;
    private Map<String, Long> rttVector;
    private Map<String, Long> rttSums = new HashMap<>();
    private ArrayList<String> eventLog;
    private int maxNodes;
    private ConcurrentLinkedQueue<DatagramPacket> receiveBuffer;



    public IncomingPackets(String thisNode, DatagramSocket socket, Map<String, MyNode> knownNodes, MyNode hub,
                           Map<String, Long> rttVector, ArrayList<String> eventLog, Map<String, Long> rttSums,
                           int maxNodes, ConcurrentLinkedQueue<DatagramPacket> receiveBuffer) {
        this.thisNode = thisNode;
        this.socket = socket;
        this.knownNodes = knownNodes;
        this.hub = hub;
        this.rttVector = rttVector;
        this.eventLog = eventLog;
        this.rttSums = rttSums;
        this.maxNodes = maxNodes;
        this.receiveBuffer = receiveBuffer;
    }

    public void run() {


        try {

            //System.out.println("receive thread started");
            while (true) {

                //receive packet
                byte[] message1 = new byte[64000];
                DatagramPacket receivePacket = new DatagramPacket(message1, message1.length);
                System.out.println("waiting to receive at port: " + socket.getLocalPort());
                socket.receive(receivePacket);

                byte[] receivedData = receivePacket.getData();
                String msgType = new String(Arrays.copyOfRange(receivedData, 0, 4));
                byte[] senderNameBytes = trim(Arrays.copyOfRange(receivedData, 30, 46));
                String senderName = new String(senderNameBytes);
                byte[] destNameBytes = trim(Arrays.copyOfRange(receivedData, 46, 62));
                String destName = new String(destNameBytes);
                System.out.println("message type received: " + msgType + " from " + senderName);

                receiveBuffer.add(receivePacket);

            }

        } catch (Exception e) {

        }
    }


    public byte[] trim(byte[] bytes)
    {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0)
        {
            --i;
        }

        return Arrays.copyOf(bytes, i + 1);
    }

}