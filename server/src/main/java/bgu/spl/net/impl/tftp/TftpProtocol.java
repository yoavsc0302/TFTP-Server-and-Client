package bgu.spl.net.impl.tftp;


//import java.io.FileInputStream; // Yoav: I added this import because I need to read the file from the server
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;


public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    static private ConcurrentHashMap<String, Boolean> loggedInUsers = new ConcurrentHashMap<String, Boolean>();
    static private ConcurrentHashMap<Integer, String> connectionIdToUsername = new ConcurrentHashMap<Integer, String>();

    private int connectionId;
    private Connections<byte[]> connections;
    private String username;
    private boolean isLoggedIn;
    private List<byte[]> dataPackets = new LinkedList<>();
    private String filenameToCreate;
    private String fileFolder = "Files";
    private byte[] file;

    private boolean shouldTerminate;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.isLoggedIn = false;
        this.shouldTerminate = false;
        this.filenameToCreate = "";
    }

    @Override
    public void process(byte[] message) {

        short opcode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff); // decode the opcode

        if (!isLoggedIn && opcode != 7) {
            connections.send(connectionId, createErrorPacket(6, "User not logged in")); 
        }

        else {
            switch(opcode){
                case 1:
                    // RRQ
                    processRRQ(message); // Yoav: OK?
                    break;
                case 2:
                    // WRQ
                    processWRQ(message); // Yoav: OK?
                    break;
                case 3:
                    // DATA
                    processData(message); // Yoav: OK?
                    break;
                case 4:
                    // ACK
                    processACK(message); // Yoav: OK?
                    break;
                case 5:
                    // ERROR
                    processERROR(message); // Yoav: NO NEED TO IMPLEMENT?
                    break;
                case 6:
                    // DIRQ
                    processDIRQ(message); // Yoav: OK?
                    break;
                case 7:
                    // LOGRQ
                    processLOGRQ(message); // Yoav: OK?
                    break;
                case 8:
                    // DELRQ
                    processDELRQ(message); // Nave: OK? Syncronize? do i need to bicast now?
                    break;
                case 9:
                    // BCAST
                    processBCAST(message); // Nave: What if the filename doesnt exist or never existed?
                    break;
                case 10:
                    // DISC
                    processDISC(); // Nave: OK?
                    break;
            }
        }


    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

    private void processRRQ(byte[] message) {
        String filename = new String(message, 2, message.length - 3, StandardCharsets.UTF_8); // Yoav: OK?
        File fileToSend = new File(fileFolder + "/" + filename);
        System.out.println("RRQ Filename: " + filename);
        if(fileToSend.exists()){
            try {
                file = Files.readAllBytes(fileToSend.toPath());
                fileToDataPackets();

                 // send the first data packet to the client, and if there are more data packets to send, they will be sent in the processACK method.
                if(dataPackets.size() > 0){
                    byte[] dataPacket = dataPackets.remove(0);
                    connections.send(connectionId, dataPacket);
                }
            }
            catch (Exception e){
                e.printStackTrace();
                connections.send(connectionId, createErrorPacket(0, "Not defined, see error message (if any)"));
                return;
            }
        }
        else{
            connections.send(connectionId, createErrorPacket(1, "File not found"));
        }
    }

    private void processWRQ(byte[] message) {

        //Check if file exists, if it does send an error packet, if not send an ACK that informs the client that the server is ready to receive the file

        String filename = new String(message, 2, message.length - 3, StandardCharsets.UTF_8); // Yoav: OK?
        File fileThatMightExist = new File(fileFolder + "/" + filename); 

        if (!fileThatMightExist.exists()){
            file = new byte[0];
            filenameToCreate = filename;
            connections.send(connectionId, createACKPacket((short) 0));
            processBCAST(createBCASTPacket(filename, true));
        }
        else{
            connections.send(connectionId, createErrorPacket(5, "File already exists"));
        }
    }

    private void addDataToFile(byte[] dataPacket) {
        byte[] newFile = new byte[file.length + dataPacket.length];
        System.arraycopy(file, 0, newFile, 0, file.length);
        System.arraycopy(dataPacket, 0, newFile, file.length, dataPacket.length);
        file = newFile;
    }

    private void processData(byte[] message) {
        //We receive a data packet from the client, we need to write the data to the file and send the client an ACK

        short blockNumber = (short) (((short) message[4]) << 8 | (short) (message[5]) & 0x00ff); // decode the block number (is it the third and forth byte or the fifth and the sixth byte??
        byte[] data = new byte[message.length - 6];
        System.arraycopy(message, 6, data, 0, message.length - 6); // Yoav: extract the data itself from the message into the data array
        boolean isLastBlock = data.length < 512;
        addDataToFile(data);
       
        if(isLastBlock){
            // create the file from the dataPackets list and send the client an ACK
            Path filePath = Paths.get(fileFolder, filenameToCreate);

            try{
                Files.write(filePath, file);
            }
            catch (Exception e){
                e.printStackTrace();
                connections.send(connectionId, createErrorPacket(0, "Not defined, see error message (if any)"));
                filenameToCreate = "";
                return;
            }
            
            connections.send(connectionId, createACKPacket(blockNumber));
            processBCAST(createBCASTPacket(filenameToCreate, true));
            filenameToCreate = "";
            
        }

        else{
            // send the client an ACK with the block number of the data packet
            connections.send(connectionId, createACKPacket((short) blockNumber));
        }
    }

    private void processACK(byte[] message) {
        if(dataPackets.size() > 0){
            byte[] dataPacket = dataPackets.remove(0);
            connections.send(connectionId, dataPacket);
        }
        else{
            // if there are no more data packets to send, the file transfer is complete
            filenameToCreate = "";
        }
    }

    private void processERROR(byte[] message) {
        return;
    }

    private void processDIRQ(byte[] message) {

        File folder = new File(fileFolder);
        File[] listOfFiles = folder.listFiles();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        for (File file : listOfFiles) {
            if (file.isFile()) {
                try {
                    baos.write((file.getName()+"\n").getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                baos.write((byte)0); // Append byte 0 as separator
            }
        }

        byte[] filesInBytes = baos.toByteArray();

        // data packets are sent to the client in chunks of 512 bytes, and the last data packet is less than 512 bytes
        // take the files, and create data packets from them, and add them to the dataPackets list

        int blockNumber = 1;
        int bytesRead;
        byte[] buffer = new byte[512];
        int i = 0;
        while (i < filesInBytes.length) {
            bytesRead = Math.min(512, filesInBytes.length - i);
            System.arraycopy(filesInBytes, i, buffer, 0, bytesRead);
            byte[] dataPacket = createDataPacket(buffer, bytesRead, blockNumber);
            dataPackets.add(dataPacket);
            blockNumber++;
            i += bytesRead;
        }

        // send the first data packet to the client, and if there are more data packets to send, they will be sent in the processACK method.
        if(dataPackets.size() > 0){
            byte[] dataPacket = dataPackets.remove(0);
            connections.send(connectionId, dataPacket);
        } 
    }

    private void processLOGRQ(byte[] message) { 

        if (isLoggedIn) {
            connections.send(connectionId, createErrorPacket(7, "User already logged in"));
        }

        else {
            username = new String(message, 2, message.length - 3, StandardCharsets.UTF_8); 

            if(loggedInUsers.get(username) == null || loggedInUsers.get(username) == false){ // Yoav: null means the user has never logged in before, and false means the user have logged in before but is not logged in now
                loggedInUsers.put(username, true); 
                connectionIdToUsername.put(connectionId, username);
                isLoggedIn = true;
                connections.send(connectionId, createACKPacket((short)0));
            }
            else{ // Yoav: if the user is in the map, then he is logged in, so we can't log him in
                connections.send(connectionId, createErrorPacket(7, "User already logged in"));
            }
        }
    }

    private void processDELRQ(byte[] message) {
        String filename = new String(message, 2, message.length - 3, StandardCharsets.UTF_8); // Yoav: OK?
        System.out.println("Filename: " + filename);
        File file = new File(fileFolder + "/" + filename);
        if (file.exists()){
            file.delete(); // Yoav: OK?
            connections.send(connectionId, createACKPacket((short)0));
            processBCAST(createBCASTPacket(filename, false));
        }
        else{
            connections.send(connectionId, createErrorPacket(1, "Filename not found"));
        }
    }

    private void processBCAST(byte[] message) {
        for (Integer conId : connectionIdToUsername.keySet()) {
            connections.send(conId, message);
        }
    }

    private void processDISC() {
        connections.send(connectionId, createACKPacket((short) 0));
        loggedInUsers.put(username, false);
        connectionIdToUsername.remove(connectionId);
        isLoggedIn = false;
        shouldTerminate = true;

    }

    private byte[] createACKPacket(short blockNumber) {
        byte[] ackPacket = new byte[4];
        ackPacket[0] = 0; // First byte of the opcode
        ackPacket[1] = 4; // Second byte of the opcode (ACK opcode is 04)
        ackPacket[2] = (byte) (blockNumber >> 8); // First byte of the block number (big endian)
        ackPacket[3] = (byte) (blockNumber & 0xFF); // Second byte of the block number (big endian)
        return ackPacket;
    }

    // Yoav this method is responsible for creating a data packet from a buffer, bytesRead, and blockNumber
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

    private byte[] createErrorPacket(int errorCode, String errMsg) { 
        byte[] errMsgBytes = errMsg.getBytes();
        byte[] errorPacket = new byte[errMsgBytes.length + 5];
        errorPacket[0] = 0;
        errorPacket[1] = 5; // Opcode for ERROR
        errorPacket[2] = 0; // Error code
        errorPacket[3] = (byte) errorCode;
        System.arraycopy(errMsgBytes, 0, errorPacket, 4, errMsgBytes.length);
        errorPacket[errMsgBytes.length + 4] = 0;
        return errorPacket;
    }

    private byte[] createBCASTPacket(String fileName, boolean isAdded) { // 0 FOR DELETE, 1 FOR ADDED
        byte[] bcastPacket = new byte[fileName.length() + 4];
        bcastPacket[0] = 0;
        bcastPacket[1] = 9; // Opcode for BCAST
        bcastPacket[2] = (byte) (isAdded ? 1 : 0);
        System.arraycopy(fileName.getBytes(), 0, bcastPacket, 3, fileName.length());
        bcastPacket[fileName.length() + 3] = 0;
        return bcastPacket;
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


}
