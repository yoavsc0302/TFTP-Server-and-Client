package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder

    /*
     * We added: the bytes array which will hold the bytes of the message, and the len variable which will hold the current length of the message.
     */
    private byte[] bytes = new byte[1 << 10]; // number of bytes can be at most 1024 (2^10)
    private int len = 0;
    private short opcode = -1;
    private short dataSize = -1;


    @Override
    public byte[] decodeNextByte(byte nextByte) {
        // TODO: implement this

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
    public byte[] encode(byte[] message) { // Yoav: Thid method is responsible for taking a message that the server wants to send to the client, and the message was created by the server in the process method of the protocol, and it is responsible for encoding the message to bytes that can be sent to the client.
        return message;
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