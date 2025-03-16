package com.mailsystem.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {
    public static void saveEmail(String user, String content) {
        // Nettoyer l'adresse email en supprimant les caractères '<' et '>'
        String cleanedUser = user.replaceAll("[<>]", "");

        // Valider l'adresse email (optionnel)
        if (!isValidEmail(cleanedUser)) {
            System.err.println("Invalid email address: " + cleanedUser);
            return;
        }

        String directory = "mailserver/" + cleanedUser + "/";
        String filename = System.currentTimeMillis() + ".txt";
        try {
            // Créer le répertoire de l'utilisateur s'il n'existe pas
            Files.createDirectories(Path.of(directory));
            // Sauvegarder l'email dans un fichier
            Files.write(Path.of(directory + filename), content.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isValidEmail(String email) {
        String regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return email.matches(regex);
    }
}