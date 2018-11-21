import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class StarNode{

    static Map<String, MyNode> knownNodes = new HashMap<String, MyNode>();
    static MyNode hub = new MyNode(null, null, 0);
    static Map<String, Long> rttVector = new HashMap<>();
    static ArrayList<String> eventLog = new ArrayList<>();

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

            if(args.length == 5) {
                //POC Connect Thread
                Thread pocConnect = new Thread(new ConnectToPOC(currentNode, knownNodes, pocIPAddress, pocPort, socket));
                pocConnect.start();
            }

            //Receiving Messages Thread - Omega
            Thread receiveThread = new Thread(new ReceiveMultiThread(nodeName, socket, knownNodes, hub, rttVector, eventLog));
            receiveThread.start();


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



}