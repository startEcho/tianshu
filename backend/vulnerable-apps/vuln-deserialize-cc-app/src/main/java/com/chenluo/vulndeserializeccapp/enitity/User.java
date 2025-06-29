package com.chenluo.vulndeserializeccapp.enitity;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 123456789L; // 固定 serialVersionUID

    public String name;
    public int age;
    public String role = "user"; // 默认角色

    // 注意：为了演示，我们暂时不把 command 声明为 transient
    // 在实际漏洞利用中，攻击者会控制这个字段
    public String command; // 潜在的危险字段

    public User(String name, int age) {
        this.name = name;
        this.age = age;
        System.out.println("User constructor called: " + name + ", " + age);
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", role='" + role + '\'' +
                (command != null ? ", command='" + command + '\'' : "") +
                '}';
    }

    // 自定义的 readObject 方法，这是很多漏洞的入口
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        System.out.println("[User] Custom readObject() called.");
        // 默认的反序列化行为，会填充非 static 和非 transient 字段
        ois.defaultReadObject();
        System.out.println("[User] After defaultReadObject(): " + this);

        // ****************************************************************
        // * 这是一个简单的“漏洞”：如果 command 字段不为空，就执行它 *
        // ****************************************************************
        if (command != null && !command.isEmpty()) {
            System.out.println("[User] DANGER! Executing command: " + command);
            try {
                // 在 Windows 上打开计算器
                // 在 macOS 上可以是 "open -a Calculator"
                // 在 Linux 上可以是 "gnome-calculator" 或 "kcalc"
                Runtime.getRuntime().exec(command);
            } catch (IOException e) {
                System.err.println("[User] Failed to execute command: " + e.getMessage());
            }
        }
    }

    // 自定义的 writeObject 方法 (可选，通常不需要特别做什么)
    private void writeObject(ObjectOutputStream oos) throws IOException {
        System.out.println("[User] Custom writeObject() called for: " + this.name);
        oos.defaultWriteObject(); // 默认的序列化行为
    }
}