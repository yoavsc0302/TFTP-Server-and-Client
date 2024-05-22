package bgu.spl.net.impl.tftp;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;

//sends to the server (reads a line, encodes and then sends)
public class sendingThread implements Runnable {
    private List<byte[]> dataPackets = new LinkedList<>();
    private Socket socket;
    private TftpEncoderDecoder encoder;
    private TftpProtocol protocol;
    private LinkedBlockingQueue<Integer> listeningMessages;
    private String fileFolder = "./";
    private byte[] file;
    private boolean isWRQ;
    public sendingThread(Socket socket, TftpEncoderDecoder encoder, TftpProtocol protocol, LinkedBlockingQueue<Integer> listeningMessages) {
                this.socket = socket;
                this.encoder = encoder;
                this.protocol = protocol;
                this.listeningMessages = listeningMessages;
                isWRQ = false;
    }

    @Override
    public void run() {
        BufferedOutputStream out;
        Scanner scn = new Scanner(System.in);
        try {
            out = new BufferedOutputStream(socket.getOutputStream());

            while (!protocol.shouldTerminate()) {
                System.out.println("Waiting for input: ");
                String input = scn.nextLine();
                byte[] nextMessage = encoder.encode(input.getBytes());
                if(input.equals("DISC"))
                    protocol.isDisc(true);
                if(input.equals("DIRQ"))
                    protocol.isDirq(true);
                if(input.startsWith("RRQ"))
                    if(!protocol.prepareRRQ(input)){
                        continue;
                    }
                if(input.startsWith("WRQ")){
                    if(!processRRQ(input.substring(4))){
                        System.out.println("File doesn't exists!");
                        continue;
                    }
                    isWRQ = true;
                }
                if (nextMessage != null) {
                    out.write(nextMessage);
                    out.flush();
                    try{
                        if(listeningMessages.take() == -1)
                            isWRQ = false;
                        if(isWRQ){
                            finishWRQ(out);
                        }
                    } catch(Exception ignored){}
                } else {
                    System.out.println("Invalid input!");
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        scn.close();
    }

    private void finishWRQ(BufferedOutputStream out) {
        while(!dataPackets.isEmpty()){
            try{
                out.write(dataPackets.remove(0));
                out.flush();
                listeningMessages.take();
            } catch(Exception ignored){}
        }
    }

    private void fileToDataPackets() {
        dataPackets.clear();
        int blockNumber = 1;
        int bytesRead;
        byte[] buffer = new byte[512];
        int i = 0;
        while (i < file.length) {
            bytesRead = Math.min(512, file.length - i);
            System.arraycopy(file, i, buffer, 0, bytesRead);
            byte[] dataPacket = createDataPacket(buffer, bytesRead, blockNumber);
            dataPackets.add(dataPacket);
            blockNumber++;
            i += bytesRead;
        }
    }
    private byte[] createDataPacket(byte[] buffer, int bytesRead, int blockNumber) {
        byte[] dataPacket = new byte[bytesRead + 6];
        short packetSize = (short) bytesRead;
        dataPacket[0] = 0;
        dataPacket[1] = 3; // Opcode for DATA
        dataPacket[2] = (byte) (packetSize >> 8);
        dataPacket[3] = (byte) (packetSize & 0xFF);
        dataPacket[4] = (byte) (blockNumber >> 8);
        dataPacket[5] = (byte) (blockNumber & 0xFF);
        System.arraycopy(buffer, 0, dataPacket, 6, bytesRead);
        return dataPacket;
    }

    private boolean processRRQ(String filename) {
        File fileToSend = new File(fileFolder + "/" + filename);

        if (fileToSend.exists()) {
            try {
                file = Files.readAllBytes(fileToSend.toPath());
                fileToDataPackets();
            } catch (Exception e) {}
            return true;
        }
        return false;
    }
}
