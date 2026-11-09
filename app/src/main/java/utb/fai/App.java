package utb.fai;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class App {

    public static void main(String[] args) {

        int port = 33000;
        int max_conn = 2;

        if (args.length > 0) {

            if (args[0].equals("--help")) {
                System.out.println(
                        "Usage: Server [PORT] [MAX_CONNECTIONS]\n" +
                        "Default port: 33000\n" +
                        "Default max connections: 2");
                return;
            }

            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port!");
                return;
            }
        }

        if (args.length > 1) {

            try {
                max_conn = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid maximum connections!");
                return;
            }
        }

        if (port < 1 || port > 65535 || max_conn < 1) {
            System.err.println("Invalid server parameters!");
            return;
        }

        System.out.printf(
                "IM server listening on port %d, maximum nr. of connections=%d...\n",
                port, max_conn);

        ExecutorService pool = Executors.newFixedThreadPool(2 * max_conn);

        ActiveHandlers activeHandlers = new ActiveHandlers();

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (!pool.isShutdown()) {

                Socket clientSocket = serverSocket.accept();

                if (activeHandlers.getClientCount() >= max_conn) {
                    clientSocket.close();
                    continue;
                }

                clientSocket.setKeepAlive(true);

                SocketHandler handler = new SocketHandler(
                        clientSocket, activeHandlers);

                pool.execute(handler.inputHandler);
                pool.execute(handler.outputHandler);
            }

        } catch (IOException e) {

            System.err.println("Server error: " + e.getMessage());

        } finally {

            pool.shutdownNow();

            try {
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("Thread pool did not terminate.");
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}