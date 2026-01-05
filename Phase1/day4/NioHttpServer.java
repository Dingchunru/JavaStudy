package Phase1.day4;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class NioHttpServer {
    private final int port;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private final ByteBuffer buffer =ByteBuffer.allocate(1024);
    private final Charset charset =StandardCharsets.UTF_8;

    public NioHttpServer(int port){
        this.port=port;
    }

    public void start() throws IOException {
        selector=Selector.open();
        serverChannel=ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Nio HTTP Server started on port "+ port);

        while(true){
            selector.select();
            Iterator<SelectionKey> keys=selector.selectedKeys().iterator();
            while(keys.hasNext()){
                SelectionKey key=keys.next();
                keys.remove();
                if(!key.isValid()){
                    continue;
                }
                if(key.isAcceptable()){
                    acceptConnection(key);
                }
                else if(key.isReadable()){
                    readRequest(key);
                }
                else if(key.isWritable()){
                    writeResponse(key);
                }
            }
        }
    }

    public void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel=(ServerSocketChannel) key.channel();
        SocketChannel clientChannel=serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        System.out.println("Accept connection from "+ clientChannel.getRemoteAddress());
    }   


    private void readRequest(SelectionKey key) throws IOException {
        SocketChannel clientChannel=(SocketChannel) key.channel();
        buffer.clear();

        int bytesRead;
        try{
            bytesRead=clientChannel.read(buffer);
        }
        catch(IOException e){
            key.cancel();
            clientChannel.close();
            return ;
        }

        if(bytesRead==-1){
            key.cancel();
            clientChannel.close();
            return ;
        }

        buffer.flip();
        String request=charset.decode(buffer).toString();

        String requestLine=request.split("\r\n")[0];
        System.out.println("NIO Server recived: "+ requestLine);

        String[] parts=requestLine.split("");
        if(parts.length>=2){
            RequestData data=new RequestData(parts[0],parts[1]);
            key.attach(data);
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    private void writeResponse(SelectionKey key) throws IOException {
        SocketChannel clientChannel=(SocketChannel) key.channel();
        RequestData data = (RequestData) key.attachment();

        String response = generateResponse(data.method, data.path);
        ByteBuffer responseBuffer = charset.encode(response);

        while (responseBuffer.hasRemaining()) {
            clientChannel.write(responseBuffer);
        }

        // 保持连接，准备读取下一个请求
        key.interestOps(SelectionKey.OP_READ);
    }

    private String generateResponse(String method, String path) {
        String body = "<html><body><h1>NIO HTTP Server</h1>" +
                     "<p>Method: " + method + "</p>" +
                     "<p>Path: " + path + "</p>" +
                     "<p>Thread: " + Thread.currentThread().getName() + "</p>" +
                     "</body></html>";
        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: text/html\r\n" +
               "Content-Length: " + body.length() + "\r\n" +
               "Connection: keep-alive\r\n" +
               "\r\n" + body;
    }

    static class RequestData {
        String method;
        String path;
        RequestData(String method, String path) {
            this.method = method;
            this.path = path;
        }
    }

    public static void main(String[] args) throws IOException {
        NioHttpServer server = new NioHttpServer(8081);
        try{
            server.start();
        }
        catch (IOException e) {
            System.err.println("Failed to start server : "+ e.getMessage());
        }
    }
}