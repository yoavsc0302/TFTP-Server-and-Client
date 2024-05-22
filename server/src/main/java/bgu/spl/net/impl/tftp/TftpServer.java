package bgu.spl.net.impl.tftp;
import bgu.spl.net.srv.Server;

public class TftpServer {
    //TODO: Implement this

    public static void main(String[] args) {

        if(args.length == 1){
                Server.threadPerClient( //
                    Integer.parseInt(args[0]), //port
                    () -> new TftpProtocol(), //protocol factory
                    TftpEncoderDecoder::new //message encoder decoder factory
                ).serve();
        }
        else {
            System.out.println("Invalid port number. Exiting...");
        }
    }
}