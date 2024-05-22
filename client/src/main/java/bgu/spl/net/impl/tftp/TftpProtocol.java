package bgu.spl.net.impl.tftp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.LinkedBlockingQueue;

import bgu.spl.net.api.MessagingProtocol;

public class TftpProtocol implements MessagingProtocol<byte[]> {
    private String filenameToCreate;
    private String fileFolder = "./";
    private byte[] file;
    private LinkedBlockingQueue<Integer> listeningMessages;
    private boolean shouldTerminate;
    private boolean checkDisc;
    private boolean checkDirq;

    public TftpProtocol(LinkedBlockingQueue<Integer> listeningMessages) {
        this.shouldTerminate = false;
        this.filenameToCreate = "";
        this.listeningMessages = listeningMessages;
        this.checkDisc = false;
        this.checkDirq = false;
    }

    @Override
    public byte[] process(byte[] message) {

        short opcode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff); // decode the opcode
        switch (opcode) {
            case 3:
                // DATA
                return processData(message); // Yoav: OK?
            case 4:
                // ACK
                return processACK(message); // Yoav: OK?
            case 5:
                // ERROR
                return processERROR(message); // Yoav: NO NEED TO IMPLEMENT?
            case 9:
                // BCAST
                return processBCAST(message); // Yoav: What if the filename doesnt exist or never existed?
        }
        return null;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private void addDataToFile(byte[] dataPacket) {
        byte[] newFile = new byte[file.length + dataPacket.length];
        System.arraycopy(file, 0, newFile, 0, file.length);
        System.arraycopy(dataPacket, 0, newFile, file.length, dataPacket.length);
        file = newFile;
    }

    private byte[] processData(byte[] message) {
        if(checkDirq){
            String str = new String(message, 6, message.length - 7, StandardCharsets.UTF_8); // Yoav: OK?
            System.out.println("Files: " + str);
            checkDirq = false;
            try {
                listeningMessages.put(-1);
            } catch (Exception ignored) {}
            return null;
        }
        byte[] data = new byte[message.length - 6];
        System.arraycopy(message, 6, data, 0, message.length - 6);
        boolean isLastBlock = data.length < 512;
        addDataToFile(data);

        if (isLastBlock) {
            Path filePath = Paths.get(fileFolder, filenameToCreate);
            try {
                Files.write(filePath, file);
                System.out.println("RRQ " + filenameToCreate + " Completed");
                listeningMessages.put(-1);
            } catch (Exception e) {}
            filenameToCreate = "";
        }
        byte[] ACKBlockNum ={0, 4, message[4], message[5]};
        return ACKBlockNum;
    }

    private byte[] processACK(byte[] message) {
        int blockNumber = (int) (((short) message[2]) << 8 | (short) (message[3]) & 0x00ff);
        if(checkDisc){
            shouldTerminate = true;
        }
        System.out.println("ACK " + blockNumber);
        try {
            listeningMessages.put((Integer)blockNumber);
        } catch (Exception ignored) {
        }
        return null;
    }

    private byte[] processERROR(byte[] message) {
        if(checkDisc){
            checkDisc = false;
        }
        String str = new String(message, 4, message.length - 5, StandardCharsets.UTF_8); // Yoav: OK?
        System.out.println("Error " + (int)message[3] + " " + str);
        try {
            listeningMessages.put(-1);
        } catch (Exception ignored) {
        }
        return null;
    }
    private byte[] processBCAST(byte[] message) {
        String str = new String(message, 3, message.length - 4, StandardCharsets.UTF_8); // Yoav: OK?
        if (message[2] == 1) {
            System.out.println("BCAST add " + str);
        } else {
            System.out.println("BCAST del " + str);
        }
        return null;
    }

    public void isDisc(boolean b) {
        checkDisc = b;
    }
    public void isDirq(boolean b) {
        System.out.println("Changed");
        checkDirq = b;
    }

    public boolean prepareRRQ(String input) {
        String filename = input.substring(4);
        File fileThatMightExist = new File(fileFolder + "/" + filename);

        if (!fileThatMightExist.exists()) {
            file = new byte[0];
            filenameToCreate = filename;
            return true;
        }
        System.out.println("File exists!");
        return false;
    }
}
