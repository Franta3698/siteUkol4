package utb.fai;

import java.util.*;
import java.util.concurrent.*;

public class ActiveHandlers {

    private final Set<SocketHandler> activeHandlers = new HashSet<>();

    private final Map<String, SocketHandler> users = new HashMap<>();

    private final Map<String, Set<SocketHandler>> groups = new HashMap<>();

    public ActiveHandlers() {
        groups.put("public", new HashSet<>());
    }

    // Přidání nového klienta
    public synchronized void add(SocketHandler handler) {
        activeHandlers.add(handler);
        join(handler, "public");
    }

    // Odstranění klienta
    public synchronized void remove(SocketHandler handler) {
        activeHandlers.remove(handler);

        if (handler.username != null) {
            users.remove(handler.username);
        }

        for (Set<SocketHandler> members : groups.values()) {
            members.remove(handler);
        }
    }

    // Nastavení uživatelského jména
    public synchronized boolean setName(SocketHandler handler, String name) {

        if (name == null || name.isEmpty() || name.contains(" ")) {
            handler.sendMessage("Invalid username!");
            return false;
        }

        if (users.containsKey(name)) {
            handler.sendMessage("Username is already taken!");
            return false;
        }

        if (handler.username != null) {
            users.remove(handler.username);
        }

        handler.username = name;
        users.put(name, handler);

        handler.sendMessage("Your name is now " + name);

        return true;
    }

    // Soukromá zpráva
    public synchronized void sendPrivate(SocketHandler sender, String targetName, String message) {

        if (sender.username == null) {
            sender.sendMessage("Set your name first!");
            return;
        }

        SocketHandler target = users.get(targetName);

        if (target == null) {
            sender.sendMessage("User not found!");
            return;
        }

        target.sendMessage("[private][" + sender.username + "] >> " + message);
    }

    // Připojení do místnosti
    public synchronized void join(SocketHandler handler, String groupName) {

        if (groupName == null || groupName.isEmpty()) {
            handler.sendMessage("Invalid group name!");
            return;
        }

        groups.putIfAbsent(groupName, new HashSet<>());

        Set<SocketHandler> members = groups.get(groupName);

        if (members.add(handler)) {
            handler.groups.add(groupName);
            handler.sendMessage("Joined group: " + groupName);
        } else {
            handler.sendMessage("You are already in this group!");
        }
    }

    // Opuštění místnosti
    public synchronized void leave(SocketHandler handler, String groupName) {

        Set<SocketHandler> members = groups.get(groupName);

        if (members == null || !members.remove(handler)) {
            handler.sendMessage("You are not in this group!");
            return;
        }

        handler.groups.remove(groupName);
        handler.sendMessage("Left group: " + groupName);
    }

    // Výpis místností uživatele
    public synchronized void listGroups(SocketHandler handler) {

        String result = String.join(",", handler.groups);
        handler.sendMessage(result);
    }

    // Rozeslání běžné zprávy členům společné místnosti
    public synchronized void sendMessageToGroups(SocketHandler sender, String message) {

        if (sender.username == null) {
            sender.sendMessage("Set your name first!");
            return;
        }

        String formattedMessage = "[" + sender.username + "] >> " + message;

        for (String groupName : sender.groups) {

            Set<SocketHandler> members = groups.get(groupName);

            if (members == null) {
                continue;
            }

            for (SocketHandler handler : members) {

                if (handler != sender) {
                    handler.sendMessage(formattedMessage);
                }
            }
        }
    }

    public synchronized int getClientCount() {
        return activeHandlers.size();
    }
}