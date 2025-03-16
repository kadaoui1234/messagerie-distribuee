package com.mailsystem.pop3;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class POP3ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private String user;
    private boolean authenticated;
    private List<File> emails;
    private List<File> markedForDeletion;
    private String timestamp; // Pour APOP
    private Set<File> readMessages = new HashSet<>(); // Pour stocker les messages lus
    private Set<File> recentMessages = new HashSet<>(); // Pour stocker les messages récents

    public POP3ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.authenticated = false;
        this.emails = new ArrayList<>();
        this.markedForDeletion = new ArrayList<>();
        this.timestamp = "<" + System.currentTimeMillis() + "@mailsystem>"; // Timestamp pour APOP
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Envoyer une réponse initiale au client avec le timestamp pour APOP
            out.println("+OK POP3 server ready " + timestamp);

            // Lire les commandes du client
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received: " + inputLine);

                // Normaliser la commande pour être insensible à la casse
                String command = inputLine.toUpperCase();

                if (command.startsWith("USER")) {
                    handleUser(inputLine);
                } else if (command.startsWith("PASS")) {
                    handlePass(inputLine);
                } else if (command.startsWith("APOP")) {
                    handleApop(inputLine);
                } else if (command.startsWith("STAT")) {
                    handleStat();
                } else if (command.startsWith("LIST")) {
                    handleList(inputLine);
                } else if (command.startsWith("RETR")) {
                    handleRetr(inputLine);
                } else if (command.startsWith("DELE")) {
                    handleDele(inputLine);
                } else if (command.startsWith("NOOP")) {
                    handleNoop();
                } else if (command.startsWith("RSET")) {
                    handleRset();
                } else if (command.startsWith("QUIT")) {
                    handleQuit();
                    break;
                } else if (command.startsWith("TOP")) {
                    handleTop(inputLine);
                } else if (command.startsWith("UIDL")) {
                    handleUidl(inputLine);
                } else {
                    out.println("-ERR Command not recognized");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleUser(String inputLine) {
        user = inputLine.substring(5).trim();
        File userDir = new File("mailserver/" + user);
        if (userDir.exists() && userDir.isDirectory()) {
            out.println("+OK User accepted");
            loadEmails(userDir); // Charger les emails de l'utilisateur
            initializeRecentMessages(); // Initialiser les messages récents
        } else {
            out.println("-ERR User not found");
        }
    }

    private void handlePass(String inputLine) {
        if (user != null) {
            // Pour simplifier, on accepte n'importe quel mot de passe
            authenticated = true;
            out.println("+OK User authenticated");
        } else {
            out.println("-ERR User not specified");
        }
    }

    private void handleApop(String inputLine) {
        String[] parts = inputLine.split(" ");
        if (parts.length < 3) {
            out.println("-ERR Invalid APOP command");
            return;
        }

        String username = parts[1];
        String digest = parts[2];

        // Simuler une vérification de digest (pour l'exemple, le secret est "password")
        String secret = "password";
        String computedDigest = md5(timestamp + secret);

        if (computedDigest.equalsIgnoreCase(digest)) {
            authenticated = true;
            out.println("+OK User authenticated");
        } else {
            out.println("-ERR Authentication failed");
        }
    }

    private void handleStat() {
        if (!authenticated) {
            out.println("-ERR Not authenticated");
            return;
        }

        int unreadMessageCount = 0;
        long unreadMaildropSize = 0;

        // Parcourir les emails et ignorer ceux marqués pour suppression ou déjà lus
        for (int i = 0; i < emails.size(); i++) {
            File emailFile = emails.get(i);
            if (!markedForDeletion.contains(emailFile) && !readMessages.contains(emailFile)) {
                unreadMessageCount++;
                unreadMaildropSize += emailFile.length();
            }
        }

        // Réponse au format "+OK <nombre de messages non lus> <taille du maildrop non lu>"
        out.println("+OK " + unreadMessageCount + " " + unreadMaildropSize);
    }

    private void handleList(String inputLine) {
        if (!authenticated) {
            out.println("-ERR Not authenticated");
            return;
        }

        String[] parts = inputLine.split(" ");
        if (parts.length == 1) {
            // Lister tous les messages récents
            out.println("+OK"); // Début de la réponse
            for (int i = 0; i < emails.size(); i++) {
                File emailFile = emails.get(i);
                if (recentMessages.contains(emailFile) && !markedForDeletion.contains(emailFile)) {
                    out.println((i + 1) + " " + emailFile.length());
                }
            }
            out.println("."); // Fin de la réponse
        } else if (parts.length == 2) {
            // Taille d'un message spécifique (s'il est récent)
            try {
                int messageNumber = Integer.parseInt(parts[1]);
                if (messageNumber < 1 || messageNumber > emails.size()) {
                    out.println("-ERR No such message");
                    return;
                }

                File emailFile = emails.get(messageNumber - 1);
                if (!recentMessages.contains(emailFile)) {
                    out.println("-ERR Message is not recent");
                } else if (markedForDeletion.contains(emailFile)) {
                    out.println("-ERR Message marked for deletion");
                } else {
                    out.println("+OK " + messageNumber + " " + emailFile.length());
                }
            } catch (NumberFormatException e) {
                out.println("-ERR Invalid message number");
            }
        } else {
            out.println("-ERR Invalid syntax");
        }
    }

    private void handleRetr(String inputLine) {
        if (!authenticated) {
            out.println("-ERR Not authenticated");
            return;
        }

        try {
            int messageNumber = Integer.parseInt(inputLine.substring(5).trim());
            if (messageNumber < 1 || messageNumber > emails.size()) {
                out.println("-ERR No such message");
                return;
            }

            File emailFile = emails.get(messageNumber - 1);
            if (markedForDeletion.contains(emailFile)) {
                out.println("-ERR Message marked for deletion");
                return;
            }

            // Marquer le message comme non récent
            recentMessages.remove(emailFile);

            // Envoyer le contenu du message
            out.println("+OK");
            try (BufferedReader reader = new BufferedReader(new FileReader(emailFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.println(line);
                }
            }
            out.println(".");
        } catch (NumberFormatException e) {
            out.println("-ERR Invalid message number");
        } catch (IOException e) {
            out.println("-ERR Error reading message");
        }
    }

    private void handleDele(String inputLine) {
        if (!authenticated) {
            out.println("-ERR Not authenticated");
            return;
        }

        try {
            int messageNumber = Integer.parseInt(inputLine.substring(5).trim());
            if (messageNumber < 1 || messageNumber > emails.size()) {
                out.println("-ERR No such message");
                return;
            }

            File emailFile = emails.get(messageNumber - 1);
            if (markedForDeletion.contains(emailFile)) {
                out.println("-ERR Message already marked for deletion");
                return;
            }

            // Marquer le message pour suppression et le retirer des messages récents
            markedForDeletion.add(emailFile);
            recentMessages.remove(emailFile);
            out.println("+OK Message marked for deletion");
        } catch (NumberFormatException e) {
            out.println("-ERR Invalid message number");
        }
    }

    private void handleNoop() {
        if (!authenticated) {
            out.println("-ERR Not authenticated");
            return;
        }

        out.println("+OK");
    }

    private void handleRset() {
        if (!authenticated) {
            out.println("-ERR Not authenticated");
            return;
        }

        // Réinitialiser les messages marqués pour suppression
        markedForDeletion.clear();

        // Réinitialiser les messages récents
        initializeRecentMessages();

        out.println("+OK All deletions reset and recent messages restored");
    }

    private void handleQuit() {
        if (!authenticated) {
            out.println("-ERR Not authenticated");
            return;
        }

        // Supprimer les messages marqués
        for (File emailFile : markedForDeletion) {
            emailFile.delete();
        }
        out.println("+OK POP3 server signing off");
    }

    private void handleTop(String inputLine) {
        if (!authenticated) {
            out.println("-ERR Not authenticated");
            return;
        }

        String[] parts = inputLine.split(" ");
        if (parts.length < 3) {
            out.println("-ERR Invalid TOP command");
            return;
        }

        try {
            int messageNumber = Integer.parseInt(parts[1]);
            int lines = Integer.parseInt(parts[2]);

            if (messageNumber < 1 || messageNumber > emails.size()) {
                out.println("-ERR No such message");
                return;
            }

            File emailFile = emails.get(messageNumber - 1);
            out.println("+OK");
            try (BufferedReader reader = new BufferedReader(new FileReader(emailFile))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < lines) {
                    out.println(line);
                    lineCount++;
                }
            }
            out.println(".");
        } catch (NumberFormatException | IOException e) {
            out.println("-ERR Invalid TOP command");
        }
    }

    private void handleUidl(String inputLine) {
        if (!authenticated) {
            out.println("-ERR Not authenticated");
            return;
        }

        String[] parts = inputLine.split(" ");
        if (parts.length == 1) {
            // UIDL sans argument : liste tous les messages
            out.println("+OK");
            for (int i = 0; i < emails.size(); i++) {
                out.println((i + 1) + " " + emails.get(i).getName());
            }
            out.println(".");
        } else {
            // UIDL avec un numéro de message spécifique
            try {
                int messageNumber = Integer.parseInt(parts[1]);
                if (messageNumber < 1 || messageNumber > emails.size()) {
                    out.println("-ERR No such message");
                } else {
                    out.println("+OK " + messageNumber + " " + emails.get(messageNumber - 1).getName());
                }
            } catch (NumberFormatException e) {
                out.println("-ERR Invalid message number");
            }
        }
    }

    private void loadEmails(File userDir) {
        emails.clear();
        File[] files = userDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    emails.add(file);
                }
            }
        }
    }

    private void initializeRecentMessages() {
        recentMessages.clear();
        for (File emailFile : emails) {
            if (!readMessages.contains(emailFile) && !markedForDeletion.contains(emailFile)) {
                recentMessages.add(emailFile);
            }
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }
}