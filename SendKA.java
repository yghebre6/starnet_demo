import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SendKA implements Runnable {

    private String thisNode;
    private Map<String, MyNode> knownNodes;
    DatagramSocket socket;
    private ArrayList<String> eventLog;
    HashMap<String, Boolean> keepAliveMap;
    private Map<String, Long> rttVector;
    private Map<String, Long> rttSums;
    private MyNode hub;
    private ConcurrentLinkedQueue<DatagramPacket> sendBuffer;

    public SendKA(String thisNode, DatagramSocket socket, Map<String, MyNode> knownNodes, ArrayList<String> eventLog,
                  HashMap<String, Boolean> keepAliveMap, Map<String, Long> rttVector, Map<String, Long> rttSums,
                  MyNode hub, ConcurrentLinkedQueue<DatagramPacket> sendBuffer) {

        this.thisNode = thisNode;
        this.socket = socket;
        this.knownNodes = knownNodes;
        this.eventLog = eventLog;
        this.keepAliveMap = keepAliveMap;
        this.rttVector = rttVector;
        this.rttSums = rttSums;
        this.hub = hub;
        this.sendBuffer = sendBuffer;
    }

    public void run(){

        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                probe();
            }
        }, 0, 30, TimeUnit.SECONDS);
    }



    private void probe() {
        //send packet
        try {
            for (String neighborName : knownNodes.keySet()) {
                if (!neighborName.equals(thisNode)) {
                    MyNode neighborNode = knownNodes.get(neighborName);

                    byte[] ipAsByteArr = convertIPtoByteArr(neighborNode.getIP());
                    InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                    byte[] message = preparePacket(neighborNode);
                    DatagramPacket sendPacket = new DatagramPacket(message, message.length, ipAddress, neighborNode.getPort());
                    sendBuffer.add(sendPacket);
                    System.out.println(thisNode + " sent Keep Alive packet to " + neighborNode.getName());
                }
            }
            try {
                TimeUnit.SECONDS.sleep(14);
            }  catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

        } catch (UnknownHostException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }

        //check if all responded
        for (String nodeName : keepAliveMap.keySet()) {
            System.out.println(nodeName + (keepAliveMap.get(nodeName)? " is alive" : " is not alive"));
            if (!keepAliveMap.get(nodeName)){
                if (hub.getName().equals(nodeName)){
                    removeHubNode(nodeName);
                } else {
                    removeRegularNode(nodeName);
                }
            } else {
                //if the keep alive confirmation was received, set value back to false to reset for next round
                keepAliveMap.put(nodeName, false);
            }
        }


    }




    public void removeRegularNode(String nodeName) {
        eventLog.add(String.valueOf(System.currentTimeMillis()) + ": A non-hub node has disconnected");
        knownNodes.remove(nodeName);
        rttVector.remove(nodeName);
        rttSums.remove(nodeName);
        keepAliveMap.remove(nodeName);
    }


    public void removeHubNode(String nodeName){
        eventLog.add(String.valueOf(System.currentTimeMillis()) + ": The hub node has disconnected");
        knownNodes.remove(nodeName);
        rttVector.remove(nodeName);
        rttSums.remove(nodeName);
        keepAliveMap.remove(nodeName);
        hub.setIp("null");
        hub.setName("null");
        hub.setPort(0);

        Thread sendRTT = new Thread(new SendRTT(thisNode, socket, knownNodes, eventLog, rttVector, rttSums, sendBuffer));
        sendRTT.start();
    }



    public byte[] preparePacket(MyNode myNode) {

        byte[] packetType = "Kpro".getBytes();
        byte[] sourceName = thisNode.getBytes();
        byte[] destName = myNode.getName().getBytes();
        byte[] message = new byte[64000];

//      first 30 bytes
        for(int i = 0; i < packetType.length; i++) {
            message[i] = packetType[i];
        }

//      next 16 bytes (starNode name is max 16 characters)
        int index = 30;
        for(int i = 0; i < sourceName.length; i++) {
            message[index++] = sourceName[i];
        }

//      next 16 bytes (starNode name is max 16 characters)
        index = 46;
        for(int i = 0; i < destName.length; i++) {
            message[index++] = destName[i];
        }

        long timeSent = System.currentTimeMillis();
        byte[] timeSentBytes = ByteBuffer.allocate(8).putLong(timeSent).array();

//      start of body
        index = 62;
        for (int i = 0; i < timeSentBytes.length; i++) {
            message[index++] = timeSentBytes[i];
        }

        return message;
    }

    public byte[] convertIPtoByteArr(String ipAddress) {
        String[] ip = ipAddress.split("\\.");
        byte[] ipAsByteArr = new byte[4];
        int temp;
        for (int i = 0; i < 4; i++) {
            temp = Integer.parseInt(ip[i]);
            ipAsByteArr[i] = (byte) temp;
        }
        return ipAsByteArr;
    }

    public byte[] prepareHeader(String destNode, String msgtype) {

        byte[] packetType = msgtype.getBytes();
        byte[] sourceName = thisNode.getBytes();
        byte[] destName = destNode.getBytes();
        byte[] message = new byte[64000];

//      first 30 bytes
        for(int i = 0; i < packetType.length; i++) {
            message[i] = packetType[i];
        }

//      next 16 bytes (starNode name is max 16 characters)
        int index = 30;
        for(int i = 0; i < sourceName.length; i++) {
            message[index++] = sourceName[i];
        }

//      next 16 bytes (starNode name is max 16 characters)
        index = 46;
        for(int i = 0; i < destName.length; i++) {
            message[index++] = destName[i];
        }

        return message;
    }
}
