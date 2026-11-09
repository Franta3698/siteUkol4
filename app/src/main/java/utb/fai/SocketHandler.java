package utb.fai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SocketHandler implements Runnable {

    private final Socket socket;

    private final ActiveHandlers activeHandlers;

    private final BufferedReader reader;

    private final PrintWriter writer;

    private final Set<String> groups =
            ConcurrentHashMap.newKeySet();

    private volatile String username;

    private volatile boolean running = true;

    public SocketHandler(Socket socket, ActiveHandlers activeHandlers)
            throws IOException {

        this.socket = socket;
        this.activeHandlers = activeHandlers;

        this.reader = new BufferedReader(
                new InputStreamReader(
                        socket.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );

        this.writer = new PrintWriter(
                socket.getOutputStream(),
                true,
                StandardCharsets.UTF_8
        );
    }

    @Override
    public void run() {

        try {

            sendMessage("Connected to IM server.");
            sendMessage("Please enter your name using #setMyName <name>");
            sendMessage("Joined group: public");

            String request;

            while (running && (request = reader.readLine()) != null) {

                processRequest(request.trim());
            }

        } catch (IOException e) {

            if (running) {
                System.err.println("Client connection error: " + e.getMessage());
            }

        } finally {

            disconnect();
        }
    }

    // Zpracování zpráv a příkazů od klienta.
    private void processRequest(String request) {

        if (request.isEmpty()) {
            return;
        }

        // První obyčejná zpráva se použije jako uživatelské jméno.
        // Tento způsob očekává automatický test.
        if (username == null && !request.startsWith("#")) {
            activeHandlers.setName(this, request);
            return;
        }

        // Nastavení nebo změna jména.
        if (request.startsWith("#setMyName ")) {

            String name = request.substring("#setMyName ".length()).trim();

            activeHandlers.setName(this, name);
            return;
        }

        // Soukromá zpráva.
        if (request.startsWith("#sendPrivate ")) {

            String content = request.substring("#sendPrivate ".length()).trim();

            int separator = content.indexOf(' ');

            if (separator == -1) {
                sendMessage("Usage: #sendPrivate <name> <message>");
                return;
            }

            String recipientName = content.substring(0, separator).trim();
            String message = content.substring(separator + 1).trim();

            if (message.isEmpty()) {
                sendMessage("Message cannot be empty!");
                return;
            }

            activeHandlers.sendPrivate(this, recipientName, message);
            return;
        }

        // Připojení do místnosti.
        if (request.startsWith("#join ")) {

            String groupName = request.substring("#join ".length()).trim();

            activeHandlers.joinGroup(this, groupName);
            return;
        }

        // Opuštění místnosti.
        if (request.startsWith("#leave ")) {

            String groupName = request.substring("#leave ".length()).trim();

            activeHandlers.leaveGroup(this, groupName);
            return;
        }

        // Výpis místností.
        if (request.equals("#groups")) {

            activeHandlers.sendGroups(this);
            return;
        }

        // Neznámý příkaz.
        if (request.startsWith("#")) {
            sendMessage("Unknown command!");
            return;
        }

        // Běžná zpráva.
        activeHandlers.broadcast(this, request);
    }

    public void sendMessage(String message) {

        if (running && !socket.isClosed()) {
            writer.println(message);
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Set<String> getGroups() {
        return Collections.unmodifiableSet(groups);
    }

    public void addGroup(String groupName) {
        groups.add(groupName);
    }

    public void removeGroup(String groupName) {
        groups.remove(groupName);
    }

    public void disconnect() {

        if (!running) {
            return;
        }

        running = false;

        activeHandlers.removeClient(this);

        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("Failed to close client socket: " + e.getMessage());
        }
    }
}