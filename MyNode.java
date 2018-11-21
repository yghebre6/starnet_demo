import java.io.Serializable;

public class MyNode implements Serializable {

    private String name;
    private String ip;
    private int port;
    private static final long serialVersionUID = 1113799434508676095L;

    public void setPort(int port) {
        this.port = port;
    }

    public void setIp(String ip) {

        this.ip = ip;
    }

    public void setName(String name) {

        this.name = name;
    }

    public MyNode(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public String getIP() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}