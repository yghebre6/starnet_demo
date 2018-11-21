import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

public class ConnectToPOC implements Runnable{

    private MyNode thisNode;
    private Map<String, MyNode> knownNodes;
    private String pocIP;
    private int pocPort;
    DatagramSocket socket;


    public ConnectToPOC(MyNode thisNode, Map<String, MyNode> knownNodes, String pocIP, int pocPort, DatagramSocket socket) {
        this.thisNode = thisNode;
        this.knownNodes = knownNodes;
        this.pocIP = pocIP;
        this.pocPort = pocPort;
        this.socket = socket;
    }

    public void run() {

        connectToPOC(thisNode, knownNodes, pocIP, pocPort, socket);


    }

    public void connectToPOC(MyNode thisNode, Map<String, MyNode> knownNodes, String pocIP, int pocPort, DatagramSocket socket) {

        try {

            byte[] message = prepareHeader(thisNode.getName(), "no name", "POCr");

//          Put source IP and Port in body
            byte[] sourceIP = convertIPtoByteArr(thisNode.getIP());
            int index = 62;
            for (int i = 0; i < sourceIP.length; i++) {
                message[index++] = sourceIP[i];
            }
            byte[] sourcePort = ByteBuffer.allocate(4).putInt(thisNode.getPort()).array();
            for (int i = 0; i < sourcePort.length; i++) {
                message[index++] = sourcePort[i];
            }



            byte[] ipAsByteArr = convertIPtoByteArr(pocIP);
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
                    System.out.println("received response");
                    byte[] receivedData = receivePacket.getData();
                    String msgType = new String(Arrays.copyOfRange(receivedData, 0, 4));
                    if (msgType.equals("POCc")) {
                        System.out.println("POC confirmation received");
//                      Add pocNode to knownNodes map
                        String name = new String(trim(Arrays.copyOfRange(receivedData, 30, 46)));
                        MyNode pocNode = new MyNode(name, pocIP, pocPort);
                        knownNodes.put(name, pocNode);
                        System.out.println("poc connected");


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
                                MyNode neighbor = knownNodes.get(neighborName);
                                byte[] dataToSend = prepareHeader(thisNode.getName(), neighbor.getName(), "Pdis");

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
                                System.out.println("peer discovery packet sent");
                            }

                        } finally {
                            try {
                                bos.close();
                            } catch (IOException ex) {
                                // ignore close exception
                            }
                        }


                        System.out.println("break");
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

    public byte[] prepareHeader(String thisNode, String destNode, String msgtype) {

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
