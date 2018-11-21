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
        try {

            System.out.println("receive thread started");
            while (true) {

                //receive packet
                byte[] message1 = new byte[64000];
                DatagramPacket receivePacket = new DatagramPacket(message1, message1.length);
                System.out.println("waiting to receive at port: " + socket.getLocalPort());
                socket.receive(receivePacket);
                System.out.println("packet received");
                byte[] receivedData = receivePacket.getData();

                String msgType = new String(Arrays.copyOfRange(receivedData, 0, 4));

                byte[] senderNameBytes = trim(Arrays.copyOfRange(receivedData, 30, 46));
                String senderName = new String(senderNameBytes);

                byte[] destNameBytes = trim(Arrays.copyOfRange(receivedData, 46, 62));
                String destName = new String(destNameBytes);

                System.out.println("message type received: " + msgType);

                if (msgType.equals("Pdis")) {

                    try {
                        //read knownNodes list from object input stream
                        int length = ByteBuffer.wrap(Arrays.copyOfRange(receivedData, 62, 66)).getInt();
                        ByteArrayInputStream in = new ByteArrayInputStream(Arrays.copyOfRange(receivedData, 66, 66 + length));
                        ObjectInputStream is = new ObjectInputStream(in);

                        Map<String, MyNode> nodesToAppend = (Map<String, MyNode>) is.readObject();

                        //add unknown nodes to knownNodes map
                        int sizeBefore = knownNodes.size();
                        for (String nameOfNodeToAppend: nodesToAppend.keySet()) {
                            if (!knownNodes.containsKey(nameOfNodeToAppend)) {
                                knownNodes.put(nameOfNodeToAppend, nodesToAppend.get(nameOfNodeToAppend));
                                eventLog.add(String.valueOf(System.currentTimeMillis()) + ": A new node has been discovered");
                            }
                        }
                        int sizeAfter = knownNodes.size();

                        //if knownNodes was already up to date, no need to continue
                        if (sizeBefore != sizeAfter) {

                            //pack knownNodes into proper format
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            ObjectOutputStream out;
                            try {
                                out = new ObjectOutputStream(bos);
                                out.writeObject(knownNodes);
                                out.flush();
                                byte[] knownNodesAsByteArray = bos.toByteArray();

                                //update every node with new knownNodes set
                                for (String name : knownNodes.keySet()) {
                                    if (!thisNode.equals(name)) {
                                        MyNode neighbor = knownNodes.get(name);
                                        byte[] dataToSend = prepareHeader(neighbor.getName(), "Pdis");

                                        //format of packet = 62 header bytes + 4 byte for object length + the objstream
                                        int lengthOfKnownNodes = knownNodesAsByteArray.length;
                                        byte[] lengthBytes = ByteBuffer.allocate(4).putInt(lengthOfKnownNodes).array();
                                        dataToSend[62] = lengthBytes[0];
                                        dataToSend[63] = lengthBytes[1];
                                        dataToSend[64] = lengthBytes[2];
                                        dataToSend[65] = lengthBytes[3];


                                        int index = 66;
                                        for (int i = 0; i < knownNodesAsByteArray.length; i++) {
                                            dataToSend[index++] = knownNodesAsByteArray[i];
                                        }


                                        byte[] ipAsByteArr = convertIPtoByteArr(neighbor.getIP());
                                        InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                                        DatagramPacket sendPacket = new DatagramPacket(dataToSend, dataToSend.length, ipAddress, neighbor.getPort());
                                        socket.send(sendPacket);
                                    }
                                }

                            } finally {
                                try {
                                    bos.close();
                                } catch (IOException ex) {
                                    // ignore close exception
                                }
                            }
                        }

                        Thread sendRTT = new Thread(new SendRTT(thisNode, socket, knownNodes, eventLog));
                        sendRTT.start();


                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        System.out.println(e.toString());
                    }





                } else if (msgType.equals("RTTm")) {
                    eventLog.add(String.valueOf(System.currentTimeMillis()) + ": An RTT request has been received");

//                  change RTTm to RTTr
                    receivedData[3] = 'r';

//                  read star node name from messageBytes to get IP and port
                    byte[] ipAsByteArr = convertIPtoByteArr(knownNodes.get(senderName).getIP());
                    InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                    int port = knownNodes.get(senderName).getPort();

                    DatagramPacket sendPacket = new DatagramPacket(receivedData, receivedData.length, ipAddress, port);
                    socket.send(sendPacket);


                } else if (msgType.equals("RTTr")) {
                    eventLog.add(String.valueOf(System.currentTimeMillis()) + ": An RTT response has been received");

                    long timeReceived = System.currentTimeMillis();
                    long timeSent = ByteBuffer.wrap(Arrays.copyOfRange(receivedData, 62, 70)).getLong();
                    long rtt = timeReceived - timeSent;
                    rttVector.put(destName, rtt);

//                  if rtt is received from every node in knownNodes list minus itself
                    if (rttVector.size() == knownNodes.size() - 1) {

//                      find rttSum of this node
                        long rttSum = 0;
                        for (String name : rttVector.keySet()) {
                            rttSum += rttVector.get(name);
                        }
                        rttSums.put(thisNode, rttSum);

//                      Send rttSum to all nodes
                        for (String name : knownNodes.keySet()) {
                            if (!name.equals(thisNode)) {
                                MyNode node = knownNodes.get(name);
                                byte[] ipAsByteArr = convertIPtoByteArr(node.getIP());
                                InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                                byte[] message = prepareHeader(node.getName(), "RTTs");

//                              Put rttSum in body of packet
                                byte[] rttSumBytes = ByteBuffer.allocate(8).putLong(rttSum).array();
                                int index = 62;
                                for (int i = 0; i < rttSumBytes.length; i++) {
                                    message[index++] = rttSumBytes[i];
                                }
                                DatagramPacket sendPacket = new DatagramPacket(message, message.length, ipAddress, node.getPort());
                                socket.send(sendPacket);
                            }
                        }
                    }

//              RTTs = RTT Sum Packet
                } else if (msgType.equals("RTTs")) {

                    long sum = ByteBuffer.wrap(Arrays.copyOfRange(receivedData, 62, 70)).getLong();

                    if(!senderName.equals(thisNode)) {
                        rttSums.put(senderName, sum);
                    }

//                  find hub if node has N rtt sums
                    if (rttSums.size() == knownNodes.size()) {
//                      find the node with the smallest rtt sum
                        long min = Long.MAX_VALUE;
                        MyNode minNode = null;
                        for (String nodeName : knownNodes.keySet()) {
                            if (rttSums.get(nodeName) < min) {
                                min = rttSums.get(nodeName);
                                minNode = knownNodes.get(nodeName);
                            }
                        }
                        hub.setName(minNode.getName());
                        hub.setIp(minNode.getIP());
                        hub.setPort(minNode.getPort());

                        Thread sendContent = new Thread(new SendContent(thisNode, socket, knownNodes, hub, rttVector, eventLog));
                        sendContent.start();

                    }
                } else if (msgType.equals("Mfil")) {
                    eventLog.add(String.valueOf(System.currentTimeMillis()) + ": A file has been received");

                    int fileNameLength = ByteBuffer.wrap(Arrays.copyOfRange(receivedData, 62, 66)).getInt();
                    String fileName = new String(Arrays.copyOfRange(receivedData, 66, 66 + fileNameLength));

                    int fileContentLength = ByteBuffer.wrap(Arrays.copyOfRange(receivedData, 66 + fileNameLength, 66 + fileNameLength + 4)).getInt();

                    byte[] fileContent = Arrays.copyOfRange(receivedData, 66 + fileNameLength + 4, 66 + fileNameLength + 4 + fileContentLength);
                    try {
                        File targetFile = new File(fileName);
                        FileOutputStream outStream = new FileOutputStream(targetFile);
                        outStream.write(fileContent);
                        System.out.println(fileName + " file received from " + senderName);
                    } catch (Exception e) {
                        System.out.println("file failed :(");
                    }

                    //if hub, forwards message to all other nodes except sender and hub itself
                    if (thisNode.equals(hub.getName())){
                        for (String neighborName: knownNodes.keySet()) {
                            if (!neighborName.equals(hub.getName()) && !neighborName.equals(senderName)) {
                                MyNode neighbor = knownNodes.get(neighborName);
                                byte[] ipAsByteArr = convertIPtoByteArr(neighbor.getIP());
                                InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                                DatagramPacket sendPacket = new DatagramPacket(receivedData, receivedData.length, ipAddress, neighbor.getPort());
                                socket.send(sendPacket);
                            }
                        }
                    }

                } else if (msgType.equals("Masc")) {
                    eventLog.add(String.valueOf(System.currentTimeMillis()) + ": An ASCII message has been received");

                    //format of packet = 62 header bytes + 1 byte for text length + the body of the text
                    int bodyLength = ByteBuffer.wrap(Arrays.copyOfRange(receivedData, 62, 66)).getInt();
                    String asciiMessageBody = new String(Arrays.copyOfRange(receivedData, 66, 66 + bodyLength));

                    System.out.println("Node " + senderName + " says: " + asciiMessageBody);

                    //if hub, forwards message to all other nodes except sender and hub itself
                    if (thisNode.equals(hub.getName())){
                        for (String neighborName: knownNodes.keySet()) {
                            if (!neighborName.equals(hub.getName()) && !neighborName.equals(senderName)) {
                                MyNode neighbor = knownNodes.get(neighborName);
                                byte[] ipAsByteArr = convertIPtoByteArr(neighbor.getIP());
                                InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                                DatagramPacket sendPacket = new DatagramPacket(receivedData, receivedData.length, ipAddress, neighbor.getPort());
                                socket.send(sendPacket);
                            }
                        }
                    }

                    System.out.println("enter command");

                } else if (msgType.equals("POCr")) {

                    System.out.println("POC connect message received");

//                  read source star node name from messageBytes
                    byte[] message = prepareHeader(senderName, "POCc");

//                  read source star node ip and port from messageBytes
                    InetAddress ipAddress = InetAddress.getByAddress(Arrays.copyOfRange(receivedData, 62, 66));
                    int port = ByteBuffer.wrap(Arrays.copyOfRange(receivedData, 66, 70)).getInt();

                    DatagramPacket sendPacket = new DatagramPacket(message, message.length, ipAddress, port);
                    socket.send(sendPacket);
                    System.out.println("POC confirmation sent");

                } else if (msgType.equals("Dhub")) {
                    eventLog.add(String.valueOf(System.currentTimeMillis()) + ": The hub node has disconnected");
                    knownNodes.remove(senderName);
                    rttVector.remove(senderName);
                    rttSums.remove(senderName);

                    //recalculate RTT by sending RTTm msg to all knownNodes
                    for (String name : knownNodes.keySet()) {
                        MyNode myNode = knownNodes.get(name);

                        byte[] ipAsByteArr = convertIPtoByteArr(myNode.getIP());
                        InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                        byte[] message = prepareHeader(myNode.getName(), "RTTm");
                        DatagramPacket sendPacket = new DatagramPacket(message, message.length, ipAddress, myNode.getPort());
                        socket.send(sendPacket);
                    }

                } else if (msgType.equals("Dreg")) {
                    eventLog.add(String.valueOf(System.currentTimeMillis()) + ": A non-hub node has disconnected");
                    //remove from knownNodes, rttVector, and rttSums
                    knownNodes.remove(senderName);
                    rttVector.remove(senderName);
                    rttSums.remove(senderName);
                }
            }

        } catch (Exception e) {

        }
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