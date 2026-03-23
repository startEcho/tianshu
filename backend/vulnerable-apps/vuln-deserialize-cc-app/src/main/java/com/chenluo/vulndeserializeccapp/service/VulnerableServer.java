package com.chenluo.vulndeserializeccapp.service;

import com.chenluo.vulndeserializeccapp.enitity.User;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Demo socket server that deserializes untrusted input.
 *
 * <p>This class exists only for security lab demonstration.
 */
public class VulnerableServer {

    /**
     * Starts a blocking TCP server and deserializes received objects.
     *
     * @param args process arguments
     */
    public static void main(String[] args) {
        int port = 9999;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("VulnerableServer listening on port " + port + "...");
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream())) {

                    System.out.println("Client connected: " + clientSocket.getInetAddress());
                    System.out.println("Attempting to read object...");

                    Object receivedObject = ois.readObject();
                    System.out.println("Object received and deserialized: " + receivedObject);

                    if (receivedObject instanceof User) {
                        User user = (User) receivedObject;
                        System.out.println("User details: " + user.name + ", " + user.age + ", role: " + user.role);
                    }

                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("Error during client interaction: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server on port " + port + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
