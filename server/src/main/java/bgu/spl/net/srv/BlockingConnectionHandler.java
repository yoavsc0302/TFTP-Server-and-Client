package bgu.spl.net.srv;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> { // Yoav: This class is responsible for handling the connection with the client. It is responsible for reading the messages from the client, and sending messages to the client. It is also responsible for running the protocol on the messages that it reads from the client, and sending the messages that the protocol creates to the client.

    private final BidiMessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;
    private Object lockWritingToSocket = new Object();

    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, BidiMessagingProtocol<T> protocol) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());

            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                T nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    protocol.process(nextMessage);
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(T msg) {
        synchronized (lockWritingToSocket) {
            if (msg != null) { // Yoav: we need try catch here because we are using the out.write method, and it throws an IOException when it fails to write the message to the output stream for some reason, for example if the connection is closed or if the output stream is closed.
                if(out != null){ // Yoav: out might not be initialized yet, because it is initialized in the run method, and the run method might not have been called yet. So we need to check if out is not null before we call the flush method on it.
                    try{
                        out.write(encdec.encode(msg));
                        out.flush(); 
                    }
                    catch (IOException e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public BidiMessagingProtocol<T> getProtocol() {
        return protocol;
    }
}

