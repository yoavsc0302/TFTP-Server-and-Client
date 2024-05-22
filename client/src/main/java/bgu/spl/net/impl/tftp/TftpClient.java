package bgu.spl.net.impl.tftp;

import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

public class TftpClient {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Wrong usuage: missing ip and/or port");
            return;
        }
        try {
            Socket socket = new Socket(InetAddress.getByName(args[0]), Integer.parseInt(args[1]));
            socket.getOutputStream();
            socket.getInputStream();
            LinkedBlockingQueue<Integer> listeningMessages = new LinkedBlockingQueue<>();
            TftpEncoderDecoder encoder = new TftpEncoderDecoder();
            TftpProtocol protocol = new TftpProtocol(listeningMessages);
            Thread listening = new Thread(new listeningThread(socket, encoder, protocol));
            Thread sending = new Thread(new sendingThread(socket, encoder, protocol, listeningMessages));
            listening.start();
            sending.start();
            System.out.println("Client started");
            listening.join();
            sending.join();
            socket.close();
        } catch (Exception e) {
            System.out.println("Error connecting to server!");
            System.out.println("Extra information: " + e.getMessage());
        }
    }
}
