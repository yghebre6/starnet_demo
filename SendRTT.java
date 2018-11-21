import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

public class SendRTT implements  Runnable{

    private String thisNode;
    private Map<String, MyNode> knownNodes;
    DatagramSocket socket;
    private ArrayList<String> eventLog;

    public SendRTT(String thisNode, DatagramSocket socket, Map<String, MyNode> knownNodes, ArrayList<String> eventLog) {
        this.thisNode = thisNode;
        this.socket = socket;
        this.knownNodes = knownNodes;
        this.eventLog = eventLog;
    }

    public void run() {

        try {
            for (String name : knownNodes.keySet()) {
                if (!name.equals(thisNode)) {
                    MyNode myNode = knownNodes.get(name);
                    byte[] ipAsByteArr = convertIPtoByteArr(myNode.getIP());
                    InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                    byte[] message = preparePacket(myNode);
                    DatagramPacket sendPacket = new DatagramPacket(message, message.length, ipAddress, myNode.getPort());
                    socket.send(sendPacket);
                }
            }
            try {
                Thread.sleep(5000);
            }  catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

        } catch (UnknownHostException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }
    }

    public byte[] preparePacket(MyNode myNode) {

        byte[] packetType = "RTTm".getBytes();
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

}
