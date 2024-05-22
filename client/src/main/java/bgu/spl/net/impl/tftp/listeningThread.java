package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

//listens from the server (gets a message, decodes, then prints if needed)
public class listeningThread implements Runnable {
    private Socket socket;
    private TftpEncoderDecoder encoder;
    private TftpProtocol protocol;

    public listeningThread(Socket socket, TftpEncoderDecoder encoder, TftpProtocol protocol) {
        this.socket = socket;
        this.encoder = encoder;
        this.protocol = protocol;
    }

    @Override
    public void run() {
        BufferedInputStream in;
        BufferedOutputStream out;
        int read;
        try {
            in = new BufferedInputStream(socket.getInputStream());
            out = new BufferedOutputStream(socket.getOutputStream());

            while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                byte[] nextMessage = encoder.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    // System.out.println("Received a message: " + Arrays.toString(nextMessage));
                    // System.out.println("Trying to parse: " + new String(nextMessage));
                    byte[] response = protocol.process(nextMessage);
                    if (response != null) {
                        out.write(response);
                        out.flush();
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
