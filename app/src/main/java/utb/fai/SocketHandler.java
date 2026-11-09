package utb.fai;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class SocketHandler {

    Socket mySocket;

    String clientID;

    volatile String username;

    ActiveHandlers activeHandlers;

    ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(100);

    CountDownLatch startSignal = new CountDownLatch(2);

    volatile boolean inputFinished = false;

    Set<String> groups = ConcurrentHashMap.newKeySet();

    OutputHandler outputHandler = new OutputHandler();

    InputHandler inputHandler = new InputHandler();

    private static final String STOP = "__STOP_OUTPUT_HANDLER__";

    public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {

        this.mySocket = mySocket;
        this.activeHandlers = activeHandlers;

        clientID = mySocket.getInetAddress() + ":" + mySocket.getPort();
    }

    // Přidání zprávy do fronty
    public void sendMessage(String message) {

        if (inputFinished) {
            return;
        }

        if (!messages.offer(message)) {
            System.err.println("Message queue is full for " + clientID);
        }
    }

    // Zpracování příkazů
    private void processRequest(String request) {

        if (request.startsWith("#setMyName ")) {

            String name = request.substring("#setMyName ".length()).trim();

            if (name.contains(" ")) {
                sendMessage("Username cannot contain spaces!");
                return;
            }

            activeHandlers.setName(this, name);
            return;
        }

        if (request.startsWith("#sendPrivate ")) {

            if (username == null) {
                sendMessage("Set your name first!");
                return;
            }

            String[] parts = request.split("\\s+", 3);

            if (parts.length < 3 || parts[1].isEmpty() || parts[2].isEmpty()) {
                sendMessage("Usage: #sendPrivate <name> <message>");
                return;
            }

            activeHandlers.sendPrivate(this, parts[1], parts[2]);
            return;
        }

        if (request.startsWith("#join ")) {

            String groupName = request.substring("#join ".length()).trim();

            activeHandlers.join(this, groupName);
            return;
        }

        if (request.startsWith("#leave ")) {

            String groupName = request.substring("#leave ".length()).trim();

            activeHandlers.leave(this, groupName);
            return;
        }

        if (request.equals("#groups")) {

            activeHandlers.listGroups(this);
            return;
        }

        if (request.startsWith("#")) {
            sendMessage("Unknown command!");
            return;
        }

        if (username == null) {
            sendMessage("Set your name first!");
            return;
        }

        if (!request.isEmpty()) {
            activeHandlers.sendMessageToGroups(this, request);
        }
    }

    class OutputHandler implements Runnable {

        @Override
        public void run() {

            try {
                startSignal.countDown();
                startSignal.await();

                OutputStreamWriter writer = new OutputStreamWriter(
                        mySocket.getOutputStream(), "UTF-8");

                writer.write("You are connected from " + clientID + "\r\n");
                writer.write("Please enter your name using #setMyName <name>\r\n");
                writer.flush();

                while (true) {

                    String message = messages.take();

                    if (message.equals(STOP)) {
                        break;
                    }

                    writer.write(message + "\r\n");
                    writer.flush();
                }

            } catch (IOException e) {
                System.err.println("Output error: " + clientID);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    mySocket.close();
                } catch (IOException ignored) {
                }
            }

            System.err.println("Output handler finished: " + clientID);
        }
    }

    class InputHandler implements Runnable {

        @Override
        public void run() {

            try {
                startSignal.countDown();
                startSignal.await();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(mySocket.getInputStream(), "UTF-8"));

                activeHandlers.add(SocketHandler.this);

                String request;

                while ((request = reader.readLine()) != null) {

                    System.out.println("[" + clientID + "] " + request);

                    processRequest(request);
                }

            } catch (IOException e) {
                System.err.println("Input error: " + clientID);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {

                inputFinished = true;

                activeHandlers.remove(SocketHandler.this);

                // Probuzení OutputHandleru
                messages.clear();
                messages.offer(STOP);

                try {
                    mySocket.close();
                } catch (IOException ignored) {
                }
            }

            System.err.println("Input handler finished: " + clientID);
        }
    }
}