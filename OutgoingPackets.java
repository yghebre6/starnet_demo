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
        try {
            DatagramPacket sendPacket;

            while (true) {
                if (!sendBuffer.isEmpty()) {

                    sendPacket = sendBuffer.remove();
                    byte[] receivedData = sendPacket.getData();
                    String msgType = new String(Arrays.copyOfRange(receivedData, 0, 4));
                    byte[] senderNameBytes = trim(Arrays.copyOfRange(receivedData, 30, 46));
                    String senderName = new String(senderNameBytes);
                    byte[] destNameBytes = trim(Arrays.copyOfRange(receivedData, 46, 62));
                    String destName = new String(destNameBytes);

                    socket.send(sendPacket);
                    System.out.println(senderName + " sent " + msgType + " packet to " + destName);
                }
            }



//            DatagramPacket nextPacketInBuffer;
//            DatagramPacket currentPacket;
//            DatagramPacket lastPacketSent = new DatagramPacket(null, 0, null, 0);;
//            byte[] sendData;
//            boolean firstPacket = false;
//
////      Beginner ACK packet so first packet can be sent
//            byte[] byteArray = new byte[6400];
//            byteArray[4] = 0;
//            ackMessageFromReceiever = new DatagramPacket(byteArray, byteArray.length, null, 0);
//
//            byte expectedSeqNum = 0;
//
//
//            while (true) {
//
//
//                while (!sendBuffer.isEmpty()) {
//                    currentPacket = sendBuffer.peek();
//                    byte[] copyOfCurrentPacketData = currentPacket.getData();
//                    String msgType = new String(Arrays.copyOfRange(copyOfCurrentPacketData, 0, 4));
//
//                    if (!msgType.equals("ACKm")) {
//
//                        //hold until receive ack or timer expires
//                        while (ackMessageFromReceiever == null /*&& timer not expired yet*/) {}
//
//                        if (ackMessageFromReceiever == null || ackMessageFromReceiever.getData()[4] != expectedSeqNum) {
//                            socket.send(lastPacketSent);
//                        } else if (ackMessageFromReceiever != null && ackMessageFromReceiever.getData()[4] == expectedSeqNum) {
//                                copyOfCurrentPacketData[4] = expectedSeqNum;
//                                expectedSeqNum = (expectedSeqNum == 0) ? (byte) 1 : 0;
//                                currentPacket.setData(copyOfCurrentPacketData);
//                                socket.send(currentPacket);
//                                lastPacketSent = currentPacket;
//                                sendBuffer.remove();
//                                ackMessageFromReceiever = null;
//                        }
//                    }
//                }
//            }
        }catch(Exception e){

        }






















//        while (true) {
//
//            if (!sendBuffer.isEmpty()) {
//                sendPacket = sendBuffer.peek();
//                sendData = sendPacket.getData();
//
//                byte[] receivedData = sendPacket.getData();
//                String msgType = new String(Arrays.copyOfRange(receivedData, 0, 4));
//
//
//                if (msgType.equals("ACKm")) {
//
//
//                    socket.send(sendPacket);
//
//                } else {
//
//
//                    while (ackMessageFromReceiever == null) { }
//
//                    byte ackSequenceNumber = ackMessageFromReceiever.getData()[4];
//
//
//                    if (ackSequenceNumber == expectedSeqNum) {
//
//                        sendData[4] = expectedSeqNum;
//                        sendPacket.setData(sendData);
//                        socket.send(sendPacket);
//                        expectedSeqNum = (expectedSeqNum == 0) ? (byte) 1 : 0;
//                        sendBuffer.remove();
//                        ackMessageFromReceiever = null;
//
//
//                    }
//
//                }
//
//                }
//            } else {
//                while (ackMessageFromReceiever == null) { }
//
//                byte ackSequenceNumber = ackMessageFromReceiever.getData()[4];
//                if (ackSequenceNumber == expectedSeqNum) {
//                    expectedSeqNum = (expectedSeqNum == 0) ? (byte) 1 : 0;
//                    ackMessageFromReceiever = null;
//                }
//
//
//            }
//
//        }









//        //                      If wrong sequence number (ack got dropped) send ack again
//        if (ackSequenceNumber != sequenceNumber) {
//            byte[] receivedData = ackMessage.getData();
//            byte[] senderNameBytes = trim(Arrays.copyOfRange(receivedData, 30, 46));
//            String senderName = new String(senderNameBytes);
//            MyNode destNode = knownNodes.get(senderName);
//            byte[] destIP = convertIPtoByteArr(destNode.getIP());
//            InetAddress destIPAddress = InetAddress.getByAddress(destIP);
//            byte[] ackMsg = prepareHeader(destNode.getName(), "ACKm");
//            //Set sequence number
//            ackMsg[4] = receivedData[4];
//            DatagramPacket ackPacket = new DatagramPacket(ackMsg, ackMsg.length, destIPAddress, destNode.getPort());
//            sendBuffer.add(ackPacket);
//            ackMessage = null;
//            break;
//        } else {
//            sendData[4] = sequenceNumber;
//            sendPacket.setData(sendData);
//            socket.send(sendPacket);
//            sendBuffer.remove();
//            sequenceNumber = (sequenceNumber == 0) ? (byte) 1 : 0;
//            ackMessage = null;
//            break;
//        }















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
