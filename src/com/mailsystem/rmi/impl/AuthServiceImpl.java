package com.mailsystem.rmi.impl;

import com.mailsystem.rmi.AuthService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {
    public AuthServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public boolean authenticate(String username, String password) throws RemoteException {
        // Logique d'authentification (exemple simple)
        return "admin".equals(username) && "password".equals(password);
    }
}