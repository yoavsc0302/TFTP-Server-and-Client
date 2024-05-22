package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    /*
     * We added: the bytes array which will hold the bytes of the message, and the len variable which will hold the current length of the message.
     */
    private byte[] bytes = new byte[1 << 10]; // number of bytes can be at most 1024 (2^10)
    private int len = 0;
    private short opcode = -1;
    private short dataSize = -1;


    @Override
    public byte[] decodeNextByte(byte nextByte) {

        bytes[len] = nextByte;
        len++;

        // if we have read 2 bytes, we can decode the opcode
        if(len == 2){
            opcode = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0x00ff);
        }
        if(len >= 2){
            return handleMessage();
        }

        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        String str = new String(message, StandardCharsets.UTF_8);
        String[] arguments = str.split(" ");
        if(arguments.length == 0){
            return null;
        }
        return handleEncodeMessage(arguments, str);
    }
    private byte[] handleEncodeMessage(String[] arguments, String str){
        switch(arguments[0]){
            case "LOGRQ":
                // LOGRQ
                return encodeLOGRQ(arguments);
            case "DELRQ":
                // DELRQ
                return encodeDELRQ(arguments);
            case "RRQ":
                // RRQ
                return encodeRRQ(arguments, str);
            case "WRQ":
                // WRQ
                return encodeWRQ(arguments, str);
            case "DIRQ":
                // DIRQ
                return encodeDIRQ(arguments);
            case "DISC":
                // DISC
                return encodeDISC(arguments);
        }
        return null;
    }

    private byte[] encodeDISC(String[] arguments) {
        byte[] encoded = {0, 10};
        return encoded;
    }

    private byte[] encodeDIRQ(String[] arguments) {
        byte[] encoded = {0, 6};
        return encoded;
    }

    private byte[] encodeWRQ(String[] arguments, String str) {
        if(arguments.length < 2){
            System.out.println("Wrong arguments count!");
            return null;
        }
        String fileName = str.substring(4);
        byte[] encoded = new byte[2 + arguments[1].length() + 1];
        encoded[0] = 0;
        encoded[1] = 2;
        System.arraycopy(fileName.getBytes(), 0, encoded, 2, fileName.getBytes().length);
        return encoded;
    }

    private byte[] encodeRRQ(String[] arguments, String str) {
        if(arguments.length != 2){
            System.out.println("Wrong arguments count!");
            return null;
        }
        String fileName = str.substring(4);
        byte[] encoded = new byte[2 + arguments[1].length() + 1];
        encoded[0] = 0;
        encoded[1] = 1;
        System.arraycopy(fileName.getBytes(), 0, encoded, 2, fileName.getBytes().length);
        return encoded;
    }

    private byte[] encodeDELRQ(String[] arguments) {
        if(arguments.length != 2){
            System.out.println("Wrong arguments count!");
            return null;
        }
        byte[] encoded = new byte[2 + arguments[1].length() + 1];
        encoded[0] = 0;
        encoded[1] = 8;
        System.arraycopy(arguments[1].getBytes(StandardCharsets.UTF_8), 0, encoded, 2, arguments[1].getBytes(StandardCharsets.UTF_8).length);
        System.out.println(Arrays.toString(encoded));
        return encoded;
    }

    private byte[] encodeLOGRQ(String[] arguments) {
        if(arguments.length != 2){
            System.out.println("Wrong arguments count!");
            return null;
        }
        byte[] encoded = new byte[2 + arguments[1].length() + 1];
        encoded[0] = 0;
        encoded[1] = 7;
        System.arraycopy(arguments[1].getBytes(), 0, encoded, 2, arguments[1].getBytes().length);
        return encoded;
    }

    private byte[] handleMessage(){
        switch(opcode){
            case 1:
                // RRQ
                return decodeRRQ();
            case 2:
                // WRQ
                return decodeWRQ();
            case 3:
                // DATA
                return decodeDATA();
            case 4:
                // ACK
                return decodeACK();
            case 5:
                // ERROR
                return decodeERROR();
            case 6:
                // DIRQ
                return decodeDIRQ();
            case 7:
                // LOGRQ
                return decodeLOGRQ();
            case 8:
                // DELRQ
                return decodeDELRQ();
            case 9:
                // BCAST
                return decodeBCAST();
            case 10:
                // DISC
                return decodeDISC();
        }
        return null;
    }

    private byte[] decodeRRQ(){
        if (bytes[len-1] == 0) {
            return popBytes();
        }
        return null;
    }

    private byte[] decodeWRQ(){
        if (bytes[len-1] == 0) {
            return popBytes();
        }
        return null;
    }

    private byte[] decodeDATA(){
        if (len == 4) {
            dataSize = (short) (((short) bytes[2]) << 8 | (short) (bytes[3]) & 0x00ff); // decode the packet size
        }
        if (len == (6 + dataSize)) {
            return popBytes();
        }
        return null;
    }

    private byte[] decodeACK(){
        if (len == 4) {
            return popBytes();
        }
        return null;
    }

    private byte[] decodeERROR(){
        if(len > 4 && bytes[len-1] == 0){
            return popBytes();
        }
        return null;
    }

    private byte[] decodeDIRQ(){
        if (len == 2) {
            return popBytes();
        }
        return null;
    }

    private byte[] decodeLOGRQ(){
        if (bytes[len-1] == 0) {
            return popBytes();
        }
        return null;
    }

    private byte[] decodeDELRQ(){
        if (bytes[len-1] == 0) {
            return popBytes();
        }
        return null;
    }
    
    private byte[] decodeBCAST(){
        if (len > 3 && bytes[len-1] == 0) {
            return popBytes();
        }
        return null;
    }

    private byte[] decodeDISC(){
        if (len == 2) {
            return popBytes();
        }
        return null;
    }

    private byte[] popBytes() {
        // copy the bytes array to a new array in the length of the message
        byte[] bytesArrToReturn = new byte[len];
        System.arraycopy(bytes, 0, bytesArrToReturn, 0, len);

        // reset the bytes array and the len variable
        opcode = -1;
        dataSize = -1;
        len = 0;

        return bytesArrToReturn;
    }
}