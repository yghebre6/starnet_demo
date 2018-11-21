import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

public class SendContent implements Runnable{

    private String thisNode;
    private Map<String, MyNode> knownNodes;
    DatagramSocket socket;
    private MyNode hub;
    private Map<String, Long> rttVector;
    private ArrayList<String> eventLog;
    private int maxNodes;



    public SendContent(String thisNode, DatagramSocket socket, Map<String, MyNode> knownNodes, MyNode hub,
                       Map<String, Long> rttVector, ArrayList<String> eventLog) {
        this.thisNode = thisNode;
        this.socket = socket;
        this.knownNodes = knownNodes;
        this.hub = hub;
        this.rttVector = rttVector;
        this.eventLog = eventLog;
        this.maxNodes = maxNodes;
    }

    public void run() {

        while(true) {
            try {
                while (true) {
                    System.out.println("enter command");
                    Scanner scanner = new Scanner(System.in);
                    String request = scanner.nextLine();
                    if (request.contains("send")) {

                        //if ASCII message
                        if (request.contains("\"")) {
                            byte[] message = prepareHeader(thisNode, hub.getName(), "Masc");

                            //Put text in body of packet
                            byte[] text = request.substring(5, request.length()).getBytes();
                            //format of packet = 62 header bytes + 4 bytes for text length + the body of the text
                            int length = text.length;
                            byte[] lengthBytes = ByteBuffer.allocate(4).putInt(length).array();
                            message[62] = lengthBytes[0];
                            message[63] = lengthBytes[1];
                            message[64] = lengthBytes[2];
                            message[65] = lengthBytes[3];


                            int index = 66;
                            for (int i = 0; i < text.length; i++) {
                                message[index++] = text[i];
                            }
                            byte[] ipAsByteArr = convertIPtoByteArr(hub.getIP());
                            InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                            DatagramPacket sendPacket = new DatagramPacket(message, message.length, ipAddress, hub.getPort());
                            socket.send(sendPacket);
                        }
                        //if file message
                        else {
                            //convert file into byte array
                            String filename = request.substring(5, request.length());
                            File file = new File(filename);
                            byte[] fileAsByteArr = new byte[(int) file.length()];

                            try {
                                //put file into fileinputstream
                                FileInputStream fileInputStream = new FileInputStream(file);
                                fileInputStream.read(fileAsByteArr);
                                byte[] message = prepareHeader(thisNode, hub.getName(), "Mfil");

                                //format of packet = 62 bytes header + 4 bytes filename length + filename + 4 byte file length + file

                                //1 byte filename length

                                int length = filename.length();
                                byte[] lengthBytes = ByteBuffer.allocate(4).putInt(length).array();
                                message[62] = lengthBytes[0];
                                message[63] = lengthBytes[1];
                                message[64] = lengthBytes[2];
                                message[65] = lengthBytes[3];

                                //filename
                                int index = 66;
                                for (int i = 0; i < filename.length(); i++) {
                                    message[index++] = (byte) filename.charAt(i);
                                }

                                //4 byte file length
                                int startingIndex = index;
                                int lengthFile = (int)file.length();
                                byte[] lengthFileBytes = ByteBuffer.allocate(4).putInt(lengthFile).array();
                                message[index++] = lengthFileBytes[0];
                                message[index++] = lengthFileBytes[1];
                                message[index++] = lengthFileBytes[2];
                                message[index++] = lengthFileBytes[3];

                                //file
//                                index = startingIndex;
                                for (int i = 0; i < fileAsByteArr.length; i++) {
                                    message[index++] = fileAsByteArr[i];
                                }

                                byte[] ipAsByteArr = convertIPtoByteArr(hub.getIP());
                                InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                                DatagramPacket sendPacket = new DatagramPacket(message, message.length, ipAddress, hub.getPort());
                                socket.send(sendPacket);

                            } catch (FileNotFoundException e) {
                                System.out.println("File Not Found.");
                                e.printStackTrace();
                            } catch (IOException e1) {
                                System.out.println("Error Reading The File.");
                                e1.printStackTrace();
                            }
                        }

                        eventLog.add(String.valueOf(System.currentTimeMillis()) + ": Sent message");

                    } else if (request.contains("show-status")) {

                        System.out.println("Active Nodes in the network: ");

                        for (String nodeName : knownNodes.keySet()) {
                            if(!thisNode.equals(nodeName)) {
                                System.out.println(nodeName + " is " + rttVector.get(nodeName) + " seconds away");
                            }
                        }
                        System.out.println("\n" + hub.getName() + " is the hub.");

                    } else if (request.contains("disconnect")) {
                        if (hub.getName().equals(thisNode)) {
                            for (String neighborName : knownNodes.keySet()) {
                                //send Delete Hub msg
                                if (!thisNode.equals(neighborName)) {
                                    MyNode neighbor = knownNodes.get(neighborName);
                                    byte[] message = prepareHeader(hub.getName(), neighborName, "Dhub");
                                    byte[] ipAsByteArr = convertIPtoByteArr(neighbor.getIP());
                                    InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                                    DatagramPacket sendPacket = new DatagramPacket(message, message.length, ipAddress, neighbor.getPort());
                                    socket.send(sendPacket);
                                }
                            }
                        } else {
                            for (String neighborName : knownNodes.keySet()) {
                                //send Delete Regular msg
                                if (!thisNode.equals(neighborName)) {
                                    MyNode neighbor = knownNodes.get(neighborName);
                                    byte[] message = prepareHeader(thisNode, neighborName, "Dreg");
                                    byte[] ipAsByteArr = convertIPtoByteArr(neighbor.getIP());
                                    InetAddress ipAddress = InetAddress.getByAddress(ipAsByteArr);
                                    DatagramPacket sendPacket = new DatagramPacket(message, message.length, ipAddress, neighbor.getPort());
                                    socket.send(sendPacket);
                                }
                            }
                        }
                        System.exit(0);

                    } else if (request.contains("show-log")) {
                        for (String event : eventLog) {
                            System.out.println(event);
                        }
                    }
                }

            } catch (UnknownHostException e) {
                System.out.println(e.getMessage());
                System.exit(0);
            } catch (IOException e) {
                System.out.println(e.getMessage());
                System.exit(0);
            }
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
}
