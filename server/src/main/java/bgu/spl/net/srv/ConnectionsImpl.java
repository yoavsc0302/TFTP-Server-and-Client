package bgu.spl.net.srv;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl <T> implements Connections <T> {

    private ConcurrentHashMap<Integer, ConnectionHandler<T>> idToConnectionHandler; // Yoav: idToConnectionHandler is a map from connectionId to ConnectionHandler, its purpose is to keep track of the ConnectionHandlers. it is concurrent because it is accessed by multiple threads.
    // the generic stuctur of Hash map is: <Key, Value> where the key is the connectionId and the value is the ConnectionHandler.
    // so if i want to check if a certain value exists in the map, i can use the contains method, and if i want to get a value from the map, i can use the get method.
    // and if i want to check if a certain key exists in the map, i can use the containsKey method, and if i want to add a new key value pair to the map, i can use the put method.

    public ConnectionsImpl() {
        idToConnectionHandler = new ConcurrentHashMap<>();
    }
    
    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) { // Yoav: this method is called by the server to add a new connection to the map.
        idToConnectionHandler.putIfAbsent(connectionId, handler); // Does it matter if its put or putIfAbsent? I think it does not matter because the server should not add a connection that already exists, but it doesnt harm to use putIfAbsent.
    }

    @Override
    public boolean send(int connectionId, T msg) { // Yoav: this method is called by the server to send a message to a client, it returns true if the message was sent successfully, and false if the message was not sent successfully.
        if (idToConnectionHandler.containsKey(connectionId)) { // Yoav: we need to check if the connectionId exists in the map, because if it does not exist, we should not send the message.
            idToConnectionHandler.get(connectionId).send(msg); // Yoav: we need to use the get method to get the ConnectionHandler from the map, and then we can use the send method of the ConnectionHandler to send the message.
            return true;
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) { // Yoav: this method is called by the server to disconnect a client.
        if (idToConnectionHandler.containsKey(connectionId)) { // 
            try {
                // get the connection handler by the connection id and close it
                idToConnectionHandler.get(connectionId).close();
            } catch (IOException e) {e.printStackTrace();} 
            idToConnectionHandler.remove(connectionId); // Yoav: remove a pair from the map by its key.
        }
    }
}
