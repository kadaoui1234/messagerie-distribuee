package com.mailsystem.smtp;
import com.mailsystem.rmi.AuthService;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
public class SMTPServer {
    private static final Logger logger = Logger.getLogger(SMTPServer.class.getName());
    private static final int PORT = 25; // Port SMTP par défaut
    private static final int THREAD_POOL_SIZE = 10; // Taille du pool de threads
    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE); // Créer un pool de threads avec une taille fixe
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("SMTP Server started on port " + PORT + "...");// Connexion au service RMI
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            AuthService authService = (AuthService) registry.lookup("AuthService");
            logger.info("Connected to RMI AuthService.");// Ajouter un hook pour arrêter proprement le serveur
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down SMTP server...");
                threadPool.shutdown(); // Arrêter le pool de threads
                logger.info("SMTP server stopped.");
            }));
            while (true) {
                // Accepter une nouvelle connexion client
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connected: " + clientSocket.getInetAddress());// Soumettre la tâche de gestion du client au pool de threads
                threadPool.execute(new SMTPClientHandler(clientSocket, authService));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An error occurred in the SMTP server: " + e.getMessage(), e);
        } finally {
            threadPool.shutdown(); // Arrêter le pool de threads
            logger.info("SMTP server stopped.");
        }
    }
}