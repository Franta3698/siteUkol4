package utb.fai;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class App {

    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("Usage: java -jar app.jar <port> <max_connections>");
            return;
        }

        int port;
        int maxConnections;

        try {
            port = Integer.parseInt(args[0]);
            maxConnections = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Port and maximum connections must be numbers.");
            return;
        }

        if (port < 1 || port > 65535 || maxConnections < 1) {
            System.out.println("Invalid port or maximum connections.");
            return;
        }

        ActiveHandlers activeHandlers = new ActiveHandlers(maxConnections);

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            System.out.println(
                    "IM server listening on port " + port
                            + ", maximum nr. of connections=" + maxConnections
            );

            while (true) {

                Socket clientSocket = serverSocket.accept();

                if (activeHandlers.getClientCount() >= maxConnections) {
                    clientSocket.close();
                    continue;
                }

                try {
                    SocketHandler handler =
                            new SocketHandler(clientSocket, activeHandlers);

                    activeHandlers.addClient(handler);

                    Thread clientThread = new Thread(handler);
                    clientThread.start();

                } catch (IOException e) {
                    clientSocket.close();
                    System.err.println("Failed to initialize client: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}