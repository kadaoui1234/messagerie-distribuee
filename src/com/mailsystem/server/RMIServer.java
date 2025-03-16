package com.mailsystem.server;

import com.mailsystem.rmi.AuthService;
import com.mailsystem.rmi.impl.AuthServiceImpl;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIServer {
    public static void main(String[] args) {
        try {
            AuthService authService = new AuthServiceImpl();
            Registry registry = LocateRegistry.createRegistry(1099); // Port par défaut pour RMI
            registry.rebind("AuthService", authService);
            System.out.println("AuthService is running...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}