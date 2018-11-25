import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StarNode{

    static Map<String, MyNode> knownNodes = new HashMap<String, MyNode>();
    static MyNode hub = new MyNode(null, null, 0);
    static Map<String, Long> rttVector = new HashMap<>();
    static Map<String, Long> rttSums = new HashMap<>();
    static ArrayList<String> eventLog = new ArrayList<>();
    static ConcurrentLinkedQueue<DatagramPacket> sendBuffer = new ConcurrentLinkedQueue<>();
    static ConcurrentLinkedQueue<DatagramPacket> receiveBuffer = new ConcurrentLinkedQueue<>();
    static DatagramPacket ackMessage;


    public static void main(String[] args) throws Exception{

        try {


            System.out.println(InetAddress.getLocalHost().toString());
//          read in command line arguments
            String nodeName = args[0];
            int localPort = Integer.parseInt(args[1]);
            String pocIPAddress = InetAddress.getByName(args[2]).getHostAddress();
            int pocPort = Integer.parseInt(args[3]);
            int maxNodes = Integer.parseInt(args[4]);

//          make currentNode and put in knownNodes map
            String localIPAddress = InetAddress.getLocalHost().getHostAddress();
            MyNode currentNode = new MyNode(nodeName, localIPAddress, localPort);
            knownNodes.put(nodeName, currentNode);

            DatagramSocket socket = new DatagramSocket(localPort, InetAddress.getLocalHost());

            if(pocPort != 0 && !pocIPAddress.equals("0")) {
                connectToPOC(currentNode, pocIPAddress, pocPort, socket, maxNodes);
            }

            //Receiving Messages Thread - Omega
            Thread receiveThread = new Thread(new IncomingPackets(nodeName, socket, knownNodes, hub, rttVector, eventLog, rttSums, maxNodes, receiveBuffer));
            receiveThread.start();

            Thread taskHandler = new Thread(new TaskHandler(nodeName, socket, knownNodes, hub, rttVector, eventLog, receiveBuffer, rttSums, maxNodes, sendBuffer, ackMessage));
            taskHandler.start();

            Thread sendContent = new Thread(new SendContent(nodeName, socket, knownNodes, hub, rttVector, eventLog, rttSums, sendBuffer));
            sendContent.start();

            Thread sendThread = new Thread(new OutgoingPackets(socket, knownNodes, sendBuffer, ackMessage));
            sendThread.start();


//            Thread.sleep(10000);
////          Sending content Thread
//            boolean started = false;
//            while(!started) {
//                if (hub.getName() != null) {
//                    Thread sendContent = new Thread(new SendContent(nodeName, socket, knownNodes, hub, rttVector, eventLog, maxNodes));
//                    sendContent.start();
//                    started = true;
//                }
//            }

        } catch (UnknownHostException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        } catch (SocketException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }
    }

    public static void connectToPOC(MyNode currentNode, String pocIPAddress, int pocPort, DatagramSocket socket, int maxNodes) {

        try {

            byte[] message = prepareHeader(currentNode.getName(), "no name", "POCr");

//          Put source IP and Port in body
            byte[] sourceIP = convertIPtoByteArr(currentNode.getIP());
            int index = 62;
            for (int i = 0; i < sourceIP.length; i++) {
                message[index++] = sourceIP[i];
            }
            byte[] sourcePort = ByteBuffer.allocate(4).putInt(currentNode.getPort()).array();
            for (int i = 0; i < sourcePort.length; i++) {
                message[index++] = sourcePort[i];
            }



            byte[] ipAsByteArr = convertIPtoByteArr(pocIPAddress);
            InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, ipAddress, pocPort);
            socket.setSoTimeout(5000);
            byte[] response = new byte[64000];
            DatagramPacket receivePacket = new DatagramPacket(response, response.length);


//          keep sending every 5 seconds until PCr packet received
            int sendAttempts = 0;
            while (true) {

//              there are 24 "5-sec periods" in 2 minutes, so quit at 25th send attempt
                if (sendAttempts == 24) {
                    System.out.println("POC did not come alive in time");
                    System.exit(0);
                }
                socket.send(sendPacket);
                System.out.println("POC connect request sent to " + ipAddress + " at port " + pocPort);

                try {
                    socket.receive(receivePacket);
                    //System.out.println("received response");
                    byte[] receivedData = receivePacket.getData();
                    String msgType = new String(Arrays.copyOfRange(receivedData, 0, 4));
                    if (msgType.equals("POCc")) {
                        System.out.println("message type received: POCc");
//                      Add pocNode to knownNodes map
                        String name = new String(trim(Arrays.copyOfRange(receivedData, 30, 46)));
                        MyNode pocNode = new MyNode(name, pocIPAddress, pocPort);
                        knownNodes.put(name, pocNode);
                        //System.out.println("poc connected");


                        //pack knownNodes into proper format
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutputStream out;
                        try {
                            out = new ObjectOutputStream(bos);
                            out.writeObject(knownNodes);
                            out.flush();
                            byte[] knownNodesAsByteArray = bos.toByteArray();

                            //update every node with new knownNodes set
                            for (String neighborName : knownNodes.keySet()) {
                                if (!neighborName.equals(currentNode.getName())) {
                                    MyNode neighbor = knownNodes.get(neighborName);
                                    byte[] dataToSend = prepareHeader(currentNode.getName(), neighbor.getName(), "Pdis");

                                    //format of packet = 62 header bytes + 4 byte for object length + the objstream
                                    int length = knownNodesAsByteArray.length;
                                    byte[] lengthBytes = ByteBuffer.allocate(4).putInt(length).array();
                                    dataToSend[62] = lengthBytes[0];
                                    dataToSend[63] = lengthBytes[1];
                                    dataToSend[64] = lengthBytes[2];
                                    dataToSend[65] = lengthBytes[3];


                                    int ind = 66;
                                    for (int i = 0; i < knownNodesAsByteArray.length; i++) {
                                        dataToSend[ind++] = knownNodesAsByteArray[i];
                                    }

                                    DatagramPacket sendPacket2 = new DatagramPacket(dataToSend, dataToSend.length, ipAddress, neighbor.getPort());
                                    socket.send(sendPacket2);
                                    System.out.println(currentNode.getName() + " sent Pdis packet to " + neighbor.getName());
                                }
                            }

                        } finally {
                            try {
                                bos.close();
                            } catch (IOException ex) {
                                // ignore close exception
                            }
                        }

                        if (knownNodes.size() == maxNodes) {
                            Thread sendRTT = new Thread(new SendRTT(currentNode.getName(), socket, knownNodes, eventLog, rttVector, rttSums, sendBuffer));
                            sendRTT.start();
                        }

                        //System.out.println("break");
                        socket.setSoTimeout(0);
                        break;
                    }
                } catch (SocketTimeoutException e) {

                }
                sendAttempts++;
            }

        } catch (UnknownHostException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }


    }

    public static byte[] prepareHeader(String thisNode, String destNode, String msgtype) {

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

    public static byte[] convertIPtoByteArr(String ipAddress) {
        String[] ip = ipAddress.split("\\.");
        byte[] ipAsByteArr = new byte[4];
        int temp;
        for (int i = 0; i < 4; i++) {
            temp = Integer.parseInt(ip[i]);
            ipAsByteArr[i] = (byte) temp;
        }
        return ipAsByteArr;
    }


    public static byte[] trim(byte[] bytes) {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0)
        {
            --i;
        }

        return Arrays.copyOf(bytes, i + 1);
    }



}