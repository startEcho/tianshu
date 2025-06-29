package com.chenluo.vulndeserializeccapp.service;

import com.chenluo.vulndeserializeccapp.enitity.User;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class VulnerableServer {
    public static void main(String[] args) {
        int port = 9999;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("VulnerableServer listening on port " + port + "...");
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream())) {

                    System.out.println("Client connected: " + clientSocket.getInetAddress());
                    System.out.println("Attempting to read object...");

                    // 反序列化对象
                    Object receivedObject = ois.readObject(); // 漏洞触发点

                    System.out.println("Object received and deserialized: " + receivedObject);

                    if (receivedObject instanceof User) {
                        User user = (User) receivedObject;
                        // 此时 User 的 readObject 可能已经执行了恶意代码
                        System.out.println("User details: " + user.name + ", " + user.age + ", role: " + user.role);
                    }

                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("Error during client interaction: " + e.getMessage());
                    e.printStackTrace(); // 打印完整堆栈，方便调试
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server on port " + port + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}