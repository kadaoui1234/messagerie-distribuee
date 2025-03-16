package com.mailsystem.smtp;

import com.mailsystem.rmi.AuthService;
import com.mailsystem.utils.FileUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
public class SMTPClientHandler implements Runnable {
    private static final int MAX_MESSAGE_SIZE = 10485760; // 10 Mo

    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private AuthService authService;
    private boolean isAuthenticated = false;
    private boolean heloReceived = false;
    private boolean mailFromReceived = false;
    private boolean rcptToReceived = false;
    private String from = null;
    private List<String> recipients = new ArrayList<>();
    private StringBuilder emailContent = new StringBuilder();

    public SMTPClientHandler(Socket socket, AuthService authService) {
        this.clientSocket = socket;
        this.authService = authService;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.println("220 " + clientSocket.getLocalAddress().getHostName() + " SMTP Service Ready");

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received: " + inputLine);
                String normalizedInput = inputLine.trim().toUpperCase();

                if (normalizedInput.startsWith("AUTH")) {
                    handleAuth(inputLine);
                } else if (normalizedInput.startsWith("QUIT")) {
                    handleQuit();
                    break;
                } else if (!heloReceived && !normalizedInput.startsWith("HELO") && !normalizedInput.startsWith("EHLO")) {
                    out.println("503 Bad sequence of commands: HELO/EHLO required first");
                } else if (!isAuthenticated && !normalizedInput.startsWith("HELO") && !normalizedInput.startsWith("EHLO")) {
                    out.println("530 Authentication required");
                } else if (normalizedInput.startsWith("HELO") || normalizedInput.startsWith("EHLO"))
                    handleHelo(inputLine);
                else if (normalizedInput.startsWith("MAIL FROM")) {
                    handleMailFrom(inputLine);
                } else if (normalizedInput.startsWith("RCPT TO")) {
                    handleRcptTo(inputLine);
                } else if (normalizedInput.equals("DATA")) {
                    handleData();
                } else {
                    out.println("500 Command not recognized");
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
    private void handleAuth(String inputLine) {
        String[] parts = inputLine.split(" ");
        if (parts.length >= 2) {
            String method = parts[1].toUpperCase();
            if (method.equals("PLAIN") || method.equals("LOGIN")) {
                try {
                    if (parts.length == 3) {
                        // Authentification en une étape (PLAIN)
                        String credentials = parts[2];
                        String[] authParts = credentials.split("\0");
                        if (authParts.length == 3) {
                            isAuthenticated = authService.authenticate(authParts[1], authParts[2]);
                        }
                    } else {
                        // Authentification en deux étapes (LOGIN)
                        out.println("334 Username:");
                        String username = in.readLine();
                        out.println("334 Password:");
                        String password = in.readLine();
                        isAuthenticated = authService.authenticate(username, password);
                    }
                    out.println(isAuthenticated ? "235 Authentication successful" : "535 Authentication failed");
                } catch (IOException | IllegalArgumentException e) {
                    out.println("501 Syntax error in parameters or arguments");
                }
            } else {
                out.println("504 Unsupported authentication mechanism");
            }
        } else {
            out.println("501 Syntax error in parameters or arguments");
        }
    }


    private void handleHelo(String inputLine) {
        heloReceived = true;
        if (inputLine.trim().toUpperCase().startsWith("EHLO")) {
            out.println("250-" + clientSocket.getLocalAddress().getHostName() + " Hello " + inputLine.substring(5).trim());
            out.println("250-8BITMIME");
            out.println("250-SIZE " + MAX_MESSAGE_SIZE);
            out.println("250-AUTH PLAIN LOGIN");
            out.println("250 HELP");
        } else {
            out.println("250 Hello " + inputLine.substring(5).trim());
        }
    }

    private void handleMailFrom(String inputLine) {
        if (!heloReceived) {
            out.println("503 Bad sequence of commands: HELO/EHLO first");
            return;
        }
        String email = extractEmail(inputLine);
        if (isValidEmail(email)) {
            mailFromReceived = true;
            from = email;
            out.println("250 Sender OK");
        } else {
            out.println("501 Syntax error in parameters or arguments");
        }
    }

    private void handleRcptTo(String inputLine) {
        if (!mailFromReceived) {
            out.println("503 Bad sequence of commands: MAIL FROM required first");
            return;
        }
        String email = extractEmail(inputLine);
        if (isValidEmail(email)) {
            rcptToReceived = true;
            recipients.add(email);
            out.println("250 Recipient OK");
        } else {
            out.println("550 Invalid recipient address");
        }
    }

    private void handleData() throws IOException {
        if (!rcptToReceived) {
            out.println("503 Bad sequence of commands: RCPT TO required first");
            return;
        }
        out.println("354 Start mail input; end with <CRLF>.<CRLF>");
        String inputLine;
        try {
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.equals(".")) {
                    break;
                }
                emailContent.append(inputLine).append("\n");
            }
        } catch (IOException e) {
            // Handle connection interruption
            out.println("421 Connection interrupted, email not saved");
            resetState();
            return;
        }
        if (emailContent.length() > MAX_MESSAGE_SIZE) {
            out.println("552 Message size exceeds fixed maximum message size");
            return;
        }
        if (from != null && !recipients.isEmpty()) {
            for (String recipient : recipients) {
                FileUtils.saveEmail(recipient, emailContent.toString());
            }
            out.println("250 Email received and saved");
        } else {
            out.println("550 Invalid sender or recipient");
        }
        resetState();
    }
    private void handleQuit() {
        out.println("221 Bye");
        try {
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String extractEmail(String inputLine) {
        if (inputLine == null || !inputLine.contains("<") || !inputLine.contains(">")) {
            return null;
        }
        int start = inputLine.indexOf('<');
        int end = inputLine.indexOf('>');
        return (start != -1 && end != -1 && start < end) ? inputLine.substring(start + 1, end).trim() : null;
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private void resetState() {
        mailFromReceived = false;
        rcptToReceived = false;
        recipients.clear();
        emailContent.setLength(0);
    }
}