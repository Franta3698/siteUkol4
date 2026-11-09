package utb.fai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ActiveHandlers {

    private final int maxConnections;

    private final Set<SocketHandler> clients = new HashSet<>();

    private final Map<String, SocketHandler> users = new HashMap<>();

    private final Map<String, Set<SocketHandler>> groups = new HashMap<>();

    public ActiveHandlers(int maxConnections) {
        this.maxConnections = maxConnections;

        groups.put("public", new HashSet<>());
    }

    public synchronized int getClientCount() {
        return clients.size();
    }

    public synchronized void addClient(SocketHandler handler) {

        if (clients.size() >= maxConnections) {
            handler.disconnect();
            return;
        }

        clients.add(handler);

        handler.addGroup("public");
        groups.get("public").add(handler);
    }

    public synchronized void removeClient(SocketHandler handler) {

        clients.remove(handler);

        if (handler.getUsername() != null) {
            users.remove(handler.getUsername(), handler);
        }

        for (String groupName : new HashSet<>(handler.getGroups())) {

            Set<SocketHandler> group = groups.get(groupName);

            if (group != null) {
                group.remove(handler);

                if (group.isEmpty() && !groupName.equals("public")) {
                    groups.remove(groupName);
                }
            }
        }
    }

    // Nastavení nebo změna uživatelského jména.
    public synchronized boolean setName(SocketHandler handler, String name) {

        if (name == null || name.trim().isEmpty()) {
            handler.sendMessage("Username cannot be empty!");
            return false;
        }

        name = name.trim();

        if (name.contains(" ")) {
            handler.sendMessage("Username cannot contain spaces!");
            return false;
        }

        SocketHandler existingUser = users.get(name);

        if (existingUser != null && existingUser != handler) {
            handler.sendMessage("Username is already taken!");
            return false;
        }

        String oldName = handler.getUsername();

        if (oldName != null) {
            users.remove(oldName, handler);
        }

        handler.setUsername(name);
        users.put(name, handler);

        handler.sendMessage("Your name is now " + name);

        return true;
    }

    // Odeslání běžné zprávy ostatním klientům ve společné místnosti.
    public synchronized void broadcast(SocketHandler sender, String message) {

        if (sender.getUsername() == null) {
            sender.sendMessage("Set your name first!");
            return;
        }

        String formattedMessage =
                "[" + sender.getUsername() + "] >> " + message;

        for (SocketHandler client : clients) {

            if (client == sender) {
                continue;
            }

            if (sharesGroup(sender, client)) {
                client.sendMessage(formattedMessage);
            }
        }
    }

    // Kontrola, zda dva klienti sdílejí alespoň jednu místnost.
    private boolean sharesGroup(SocketHandler first, SocketHandler second) {

        for (String groupName : first.getGroups()) {

            if (second.getGroups().contains(groupName)) {
                return true;
            }
        }

        return false;
    }

    // Soukromá zpráva konkrétnímu uživateli.
    public synchronized void sendPrivate(
            SocketHandler sender,
            String recipientName,
            String message
    ) {

        if (sender.getUsername() == null) {
            sender.sendMessage("Set your name first!");
            return;
        }

        SocketHandler recipient = users.get(recipientName);

        if (recipient == null) {
            sender.sendMessage("User not found!");
            return;
        }

        recipient.sendMessage(
                "[" + sender.getUsername() + "] >> " + message
        );
    }

    // Připojení klienta do místnosti.
    public synchronized void joinGroup(SocketHandler handler, String groupName) {

        if (groupName == null || groupName.trim().isEmpty()) {
            handler.sendMessage("Group name cannot be empty!");
            return;
        }

        groupName = groupName.trim();

        if (groupName.contains(" ")) {
            handler.sendMessage("Group name cannot contain spaces!");
            return;
        }

        groups.putIfAbsent(groupName, new HashSet<>());

        groups.get(groupName).add(handler);
        handler.addGroup(groupName);

        handler.sendMessage("Joined group: " + groupName);
    }

    // Odpojení klienta z místnosti.
    public synchronized void leaveGroup(SocketHandler handler, String groupName) {

        if (groupName == null || groupName.trim().isEmpty()) {
            handler.sendMessage("Group name cannot be empty!");
            return;
        }

        groupName = groupName.trim();

        Set<SocketHandler> group = groups.get(groupName);

        if (group == null || !group.contains(handler)) {
            handler.sendMessage("You are not in this group!");
            return;
        }

        group.remove(handler);
        handler.removeGroup(groupName);

        if (group.isEmpty() && !groupName.equals("public")) {
            groups.remove(groupName);
        }

        handler.sendMessage("Left group: " + groupName);
    }

    // Vrácení seznamu místností, ve kterých je klient připojen.
    public synchronized void sendGroups(SocketHandler handler) {

        List<String> groupNames = new ArrayList<>(handler.getGroups());

        handler.sendMessage(String.join(",", groupNames));
    }
}