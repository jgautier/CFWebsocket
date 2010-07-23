package com.juliangautier.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;



/**
 * <tt>WebSocketServer</tt> is an abstract class that only takes care of the
 * HTTP handshake portion of WebSockets. It's up to a subclass to add
 * functionality/purpose to the server.
 * @author Nathan Rajlich
 */
public abstract class WebSocketServer implements Runnable, WebSocketListener {
    // CONSTANTS ///////////////////////////////////////////////////////////////
    /**
     * If the nullary constructor is used, the DEFAULT_PORT will be the port
     * the WebSocketServer is binded to.
     */
    public static final int DEFAULT_PORT = 80;
    /**
     * The value of <var>handshake</var> when a Flash client requests a policy
     * file on this server.
     */
    public static final String FLASH_POLICY_REQUEST = "<policy-file-request/>\0";

    
    // INSTANCE PROPERTIES /////////////////////////////////////////////////////
    /**
     * Holds the list of active WebSocket connections. "Active" means WebSocket
     * handshake is complete and socket can be written to, or read from.
     */
    private final CopyOnWriteArraySet<WebSocket> connections;
    /**
     * The port number that this WebSocket server should listen on. Default is
     * 80 (HTTP).
     */
    private int port;
    /**
     * The socket channel for this WebSocket server.
     */
    private ServerSocketChannel server;
    /**
     * The Draft the Server is running on
     */
    private Draft draft;
    // CONSTRUCTOR /////////////////////////////////////////////////////////////
    /**
     * Nullary constructor. Creates a WebSocketServer that will attempt to
     * listen on port DEFAULT_PORT.
     */
    public WebSocketServer() {
        this(DEFAULT_PORT,Draft.AUTO);
    }

	/**
     * Creates a WebSocketServer that will attempt to listen on port
     * <var>port</var>.
     * @param port The port number this server should listen on.
     */
    public WebSocketServer(int port,Draft draft) {
        this.connections = new CopyOnWriteArraySet<WebSocket>();
        this.draft=draft;
        setPort(port);
    }

    /**
     * Starts the server thread that binds to the currently set port number and
     * listeners for WebSocket connection requests.
     */
    public void start() {
        (new Thread(this)).start();
    }

    /**
     * Closes all connected clients sockets, then closes the underlying
     * ServerSocketChannel, effectively killing the server socket thread and
     * freeing the port the server was bound to.
     * @throws IOException When socket related I/O errors occur.
     */
    public void stop() throws IOException {
        for (WebSocket ws : connections)
            ws.close();
        this.server.close();
    }

    /**
     * Sends <var>text</var> to all currently connected WebSocket clients.
     * @param text The String to send across the network.
     * @throws IOException When socket related I/O errors occur.
     */
    public void sendToAll(String text) throws IOException {
        for (WebSocket c : this.connections)
            c.send(text);
    }

    /**
     * Sends <var>text</var> to all currently connected WebSocket clients,
     * except for the specified <var>connection</var>.
     * @param connection The {@link WebSocket} connection to ignore.
     * @param text The String to send to every connection except <var>connection</var>.
     * @throws IOException When socket related I/O errors occur.
     */
    public void sendToAllExcept(WebSocket connection, String text) throws IOException {
        if (connection == null) throw new NullPointerException("'connection' cannot be null");
        for (WebSocket c : this.connections)
            if (!connection.equals(c))
                c.send(text);
    }

    /**
     * Sends <var>text</var> to all currently connected WebSocket clients,
     * except for those found in the Set <var>connections</var>.
     * @param connections
     * @param text
     * @throws IOException When socket related I/O errors occur.
     */
    public void sendToAllExcept(Set<WebSocket> connections, String text) throws IOException {
        if (connections == null) throw new NullPointerException("'connections' cannot be null");
        for (WebSocket c : this.connections)
            if (!connections.contains(c))
                c.send(text);
    }

    /**
     * Returns a WebSocket[] of currently connected clients.
     * @return The currently connected clients in a WebSocket[].
     */
    public WebSocket[] connections() {
        return this.connections.toArray(new WebSocket[0]);
    }

    /**
     * Sets the port that this WebSocketServer should listen on.
     * @param port The port number to listen on.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Gets the port number that this server listens on.
     * @return The port number.
     */
    public int getPort() {
        return port;
    }
    
    public Draft getDraft() {
		return draft;
	}

    
    // Runnable IMPLEMENTATION /////////////////////////////////////////////////
    public void run() {
        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.socket().bind(new java.net.InetSocketAddress(port));

            Selector selector = Selector.open();
            server.register(selector, server.validOps());

            while(true) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator i = keys.iterator();

                while(i.hasNext()) {
                    SelectionKey key = (SelectionKey) i.next();

                    // Remove the current key
                    i.remove();

                    // if isAccetable == true
                    // then a client required a connection
                    if (key.isAcceptable()) {
                        SocketChannel client = server.accept();
                        client.configureBlocking(false);
                        WebSocket c = new WebSocket(client, this);
                        client.register(selector, SelectionKey.OP_READ, c);
                    }

                    // if isReadable == true
                    // then the server is ready to read
                    if (key.isReadable()) {
                        WebSocket conn = (WebSocket)key.attachment();
                        conn.handleRead();
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    /**
     * Gets the XML string that should be returned if a client requests a Flash
     * security policy.
     *
     * The default implementation allows access from all remote domains, but
     * only on the port that this WebSocketServer is listening on.
     *
     * This is specifically implemented for gitime's WebSocket client for Flash:
     *     http://github.com/gimite/web-socket-js
     *
     * @return An XML String that comforms to Flash's security policy. You MUST
     *         not need to include the null char at the end, it is appended
     *         automatically.
     */
    protected String getFlashSecurityPolicy() {
        return "<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\""
               + getPort() + "\" /></cross-domain-policy>";
    }


    // WebSocketListener IMPLEMENTATION ////////////////////////////////////////
    /**
     * Called by a {@link WebSocket} instance when a client connection has
     * finished sending a handshake. This method verifies that the handshake is
     * a valid WebSocket cliend request. Then sends a WebSocket server handshake
     * if it is valid, or closes the connection if it is not.
     * @param conn The {@link WebSocket} instance who's handshake has been recieved.
     * @param handshake The entire UTF-8 decoded handshake from the connection.
     * @return True if the client sent a valid WebSocket handshake and this server
     *         successfully sent a WebSocket server handshake, false otherwise.
     * @throws IOException When socket related I/O errors occur.
     * @throws NoSuchAlgorithmException 
     */
    public boolean onHandshakeRecieved(WebSocket conn, String handshake,byte[] key3) throws IOException, NoSuchAlgorithmException {
        if (FLASH_POLICY_REQUEST.equals(handshake)) {
            String policy = getFlashSecurityPolicy() + "\0";
            conn.socketChannel().write(ByteBuffer.wrap(policy.getBytes(WebSocket.UTF8_CHARSET)));
            return false;
        }
        System.out.println(handshake);
        String[] requestLines = handshake.split("\r\n");
        boolean isWebSocketRequest = true;
        String line = requestLines[0].trim();
        String path = null;
        if (!(line.startsWith("GET") && line.endsWith("HTTP/1.1"))) {
            isWebSocketRequest = false;
        } else {
            String[] firstLineTokens = line.split(" ");
            path = firstLineTokens[1];
        }
        Properties p = new Properties();
        for (int i=1; i<requestLines.length; i++) {
            line = requestLines[i];
            int firstColon = line.indexOf(":");
            if(firstColon!=-1){
            	p.setProperty(line.substring(0, firstColon).trim(), line.substring(firstColon+1).trim());
            }
        }
        String prop = p.getProperty("Upgrade");
        if (prop == null || !prop.equals("WebSocket")) {
            isWebSocketRequest = false;
        }
        prop = p.getProperty("Connection");
        if (prop == null || !prop.equals("Upgrade")) {
            isWebSocketRequest = false;
        }
        String key1=p.getProperty("Sec-WebSocket-Key1");
        String key2=p.getProperty("Sec-WebSocket-Key2");
        String headerPrefix="";
        String response="";
        switch (this.draft){
        	case DRAFT75:
        		if(key1!=null||key2!=null||key3!=null){
        			isWebSocketRequest=false;
        		}
        		break;
        	case DRAFT76:
        		if(key1==null||key2==null||key3==null){
        			isWebSocketRequest=false;
        		}
        		break;	
        }
        if (isWebSocketRequest) {
        	if(key1!=null&&key2!=null&&key3!=null){
    			headerPrefix="Sec-";
    			byte[] part1=this.getPart(key1);
    			byte[] part2=this.getPart(key2);
    			byte[] challenge=new byte[16];
    			challenge[0]=part1[0];
    			challenge[1]=part1[1];
    			challenge[2]=part1[2];
    			challenge[3]=part1[3];
    			challenge[4]=part2[0];
    			challenge[5]=part2[1];
    			challenge[6]=part2[2];
    			challenge[7]=part2[3];
    			challenge[8]=key3[0];
    			challenge[9]=key3[1];
    			challenge[10]=key3[2];
    			challenge[11]=key3[3];
    			challenge[12]=key3[4];
    			challenge[13]=key3[5];
    			challenge[14]=key3[6];
    			challenge[15]=key3[7];
    			MessageDigest md5=MessageDigest.getInstance("MD5");
            	response=new String(md5.digest(challenge));
    		}
            String responseHandshake = "HTTP/1.1 101 Web Socket Protocol Handshake\r\n" +
                                       "Upgrade: WebSocket\r\n" +
                                       "Connection: Upgrade\r\n";
            responseHandshake += headerPrefix+"WebSocket-Origin: " + p.getProperty("Origin") + "\r\n";
            responseHandshake += headerPrefix+"WebSocket-Location: ws://" + p.getProperty("Host") + path + "\r\n";
            if (p.containsKey(headerPrefix+"WebSocket-Protocol")) {
                responseHandshake += headerPrefix+"WebSocket-Protocol: " + p.getProperty("WebSocket-Protocol") + "\r\n";
            }
            if(p.containsKey("Cookie")){
            	responseHandshake += "Cookie: " + p.getProperty("Cookie")+"\r\n";
            }
            responseHandshake += "\r\n"; // Signifies end of handshake
            //only set if its Draft 76
            responseHandshake += response;
            //Can not use UTF-8 here because we might lose bytes in response during conversion
            System.out.println(responseHandshake);
            conn.socketChannel().write(ByteBuffer.wrap(responseHandshake.getBytes()));
            return true;
        }

        // If we got to here, then the client sent an invalid handshake, and we
        // return false to make the WebSocket object close the connection.
        return false;
    }

    public void onMessage(WebSocket conn, String message) {
        onClientMessage(conn, message);
    }

    public void onOpen(WebSocket conn) {
        if (this.connections.add(conn)){}
            onClientOpen(conn);
    }
    
    public void onClose(WebSocket conn) {
        if (this.connections.remove(conn))
            onClientClose(conn);
    }
    
    private byte[] getPart(String key){
     	long keyNumber=Long.parseLong(key.replaceAll("[^0-9]",""));
    	long keySpace=key.split("\u0020").length-1;
    	int part=new Long(keyNumber/keySpace).intValue();
    	byte[] bytes=new byte[4];
    	bytes[0] =(byte)( part >> 24 );
    	bytes[1] =(byte)( (part << 8) >> 24 );
    	bytes[2] =(byte)( (part << 16) >> 24 );
    	bytes[3] =(byte)( (part << 24) >> 24 );
    	return bytes;
    }
    private void appendBytesToArray(byte[] sourceArray,byte[] destinationArray){
    	for(int i=0;i<sourceArray.length;i++){
    		destinationArray[destinationArray.length-(sourceArray.length+i)]=sourceArray[i];
    	}
    }
    // ABTRACT METHODS /////////////////////////////////////////////////////////
    public abstract void onClientOpen(WebSocket conn);
    public abstract void onClientClose(WebSocket conn);
    public abstract void onClientMessage(WebSocket conn, String message);
}