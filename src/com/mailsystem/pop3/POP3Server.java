package com.mailsystem.pop3;

import java.net.ServerSocket;
import java.net.Socket;

public class POP3Server {
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(110)) {
            System.out.println("POP3 Server started on port 110...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                // Créer un thread pour gérer la connexion client
                new Thread(new POP3ClientHandler(clientSocket)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
