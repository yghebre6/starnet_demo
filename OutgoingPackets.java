import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OutgoingPackets implements Runnable{

    private DatagramSocket socket;
    private ConcurrentLinkedQueue<DatagramPacket> sendBuffer;
    private DatagramPacket ackMessageFromReceiever;
    private Map<String, MyNode> knownNodes;



    public OutgoingPackets(DatagramSocket socket, Map<String, MyNode> knownNodes, ConcurrentLinkedQueue<DatagramPacket> sendBuffer,
                           DatagramPacket ackMessageFromReceiever) {
        this.socket = socket;
        this.knownNodes = knownNodes;
        this.sendBuffer = sendBuffer;
        this.ackMessageFromReceiever = ackMessageFromReceiever;
    }

    public void run() {
            DatagramPacket packetToSend;

            while (true) {
                if (!sendBuffer.isEmpty()) {

                    packetToSend = sendBuffer.remove();
                    sendPacket(packetToSend);
                }
            }


    }


    public void sendPacket(DatagramPacket packetToSend){
        byte[] receivedData = packetToSend.getData();
        String msgType = new String(Arrays.copyOfRange(receivedData, 0, 4));
        byte[] senderNameBytes = trim(Arrays.copyOfRange(receivedData, 30, 46));
        String senderName = new String(senderNameBytes);
        byte[] destNameBytes = trim(Arrays.copyOfRange(receivedData, 46, 62));
        String destName = new String(destNameBytes);

        try {
            socket.send(packetToSend);
            System.out.println(senderName + " sent " + msgType + " packet to " + destName);
        } catch(IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public byte[] prepareHeader(String destNode, String msgtype) {

        byte[] packetType = msgtype.getBytes();
        byte[] sourceName = new byte[0];
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
