package Phase1.day4;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class BioHttpServer {
    private final int port;
    private final ExecutorService threadPool;
    public BioHttpServer(int port){
        this.port=port;
        this.threadPool=Executors.newFixedThreadPool(50);
    }
    public void start() throws IOException {
        ServerSocket serverSocket =new ServerSocket(port);
        System.out.println("Bio HHTTP Server started on port "+ port);

        while(true){
            Socket clienSocket = serverSocket.accept();
            threadPool.submit(()->handleClient(clienSocket));
        }
    }

    public void handleClient(Socket clientSocket){
        try(BufferedReader in=new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));PrintWriter out=new PrintWriter(clientSocket.getOutputStream(), true)){
            String requestLine = in.readLine();
            if(requestLine==null)
                return ;
            System.out.println("BIO Server recevied："+requestLine);

            String[] requestParts=requestLine.split("");
            String method=requestParts[0];
            String path=requestParts[1];

            String response=generateResponse(method, path);
            out.println(response);
        }
        catch(IOException e){
            e.printStackTrace();
        }
        finally{
            try{
                clientSocket.close();
            }
            catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    private String generateResponse(String method, String path){
        String body= "<html><body><h1>BIO HTTP Server</h1>" +
                     "<p>Method: " + method + "</p>" +
                     "<p>Path: " + path + "</p>" +
                     "<p>Thread: " + Thread.currentThread().getName() + "</p>" +
                     "</body></html>";

        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: text/html\r\n" +
               "Content-Length: " + body.length() + "\r\n" +
               "Connection: close\r\n" +
               "\r\n" + body;
    }


    public static void main(String[] args) {
        BioHttpServer server=new BioHttpServer(8080);
        try{
            server.start();
        }
        catch(IOException e){
            System.err.println("Failed to start server : "+ e.getMessage());
        }
    }
}
