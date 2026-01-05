package Phase1.day4;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.concurrent.*;

public class AioHttpServer {
    private final int port;
    private AsynchronousServerSocketChannel serverChannel;
    private final Charset charset = StandardCharsets.UTF_8;
    private final ExecutorService workerPool = Executors.newCachedThreadPool();

    public AioHttpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        serverChannel = AsynchronousServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        
        System.out.println("AIO HTTP Server started on port " + port);

        // 开始接受连接
        serverChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel clientChannel, Void attachment) {
                // 继续接受下一个连接
                serverChannel.accept(null, this);
                
                // 处理当前连接
                handleClient(clientChannel);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                System.err.println("Failed to accept connection: " + exc.getMessage());
            }
        });

        // 保持主线程运行
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleClient(AsynchronousSocketChannel clientChannel) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // 异步读取请求
        clientChannel.read(buffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer bytesRead, Void attachment) {
                if (bytesRead == -1) {
                    closeClient(clientChannel);
                    return;
                }

                buffer.flip();
                String request = charset.decode(buffer).toString();
                
                // 提取请求行
                String requestLine = request.split("\r\n")[0];
                System.out.println("AIO Server received: " + requestLine);

                // 解析请求
                String[] parts = requestLine.split(" ");
                if (parts.length >= 2) {
                    String method = parts[0];
                    String path = parts[1];
                    
                    // 在工作线程中处理请求并生成响应
                    workerPool.submit(() -> {
                        String response = generateResponse(method, path);
                        ByteBuffer responseBuffer = charset.encode(response);
                        
                        // 异步写入响应
                        clientChannel.write(responseBuffer, null, new CompletionHandler<Integer, Void>() {
                            @Override
                            public void completed(Integer result, Void attachment) {
                                // 如果是keep-alive，可以继续读取，否则关闭
                                try {
                                    if (request.contains("Connection: close")) {
                                        closeClient(clientChannel);
                                    } else {
                                        // 准备读取下一个请求
                                        buffer.clear();
                                        clientChannel.read(buffer, null, this);
                                    }
                                } catch (Exception e) {
                                    closeClient(clientChannel);
                                }
                            }

                            @Override
                            public void failed(Throwable exc, Void attachment) {
                                closeClient(clientChannel);
                            }
                        });
                    });
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                closeClient(clientChannel);
            }
        });
    }

    private String generateResponse(String method, String path) {
        String body = "<html><body><h1>AIO HTTP Server</h1>" +
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

    private void closeClient(AsynchronousSocketChannel clientChannel) {
        try {
            clientChannel.close();
        } catch (IOException e) {
            // 忽略关闭异常
        }
    }

    public static void main(String[] args) throws IOException {
        AioHttpServer server = new AioHttpServer(8082);
        try{
            server.start();
        }
        catch (IOException e) {
            System.err.println("Failed to start server : "+ e.getMessage());
        }
    }
}