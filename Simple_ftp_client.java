import java.io.IOException;
import java.net.SocketException;

/**
 * Created by yuguanxu on 4/11/17.
 */
public class Simple_ftp_client {
    public static void main(String[] args) throws IOException {
        String serverHostName = args[0];
        int serverPortNum = Integer.parseInt(args[1]);
        String fileName = args[2];
        int N = Integer.parseInt(args[3]);
        int MSS = Integer.parseInt(args[4]);

        Client clientEntity = new Client(N, MSS, fileName,100, serverPortNum, serverHostName);

        Thread clientReceiver = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    clientEntity.receiver();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        Thread clientSender = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    clientEntity.sender();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        clientReceiver.start();
        clientSender.start();
        //System.out.println("This client is established");
    }
}
