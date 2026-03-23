package com.chenluo.vulndeserializeccapp.enitity;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Serializable object used by deserialization lab examples.
 *
 * <p>The custom {@code readObject} method intentionally contains dangerous behavior for security
 * demonstration only.
 */
public class User implements Serializable {

    private static final long serialVersionUID = 123456789L;

    public String name;
    public int age;
    public String role = "user";

    /**
     * Intentionally dangerous field. If populated by attacker input, readObject may execute it.
     */
    public String command;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
        System.out.println("User constructor called: " + name + ", " + age);
    }

    @Override
    public String toString() {
        return "User{"
                + "name='" + name + '\''
                + ", age=" + age
                + ", role='" + role + '\''
                + (command != null ? ", command='" + command + '\'' : "")
                + '}';
    }

    /**
     * Called during deserialization.
     *
     * <p>Lab vulnerability point: executes system command when {@code command} is provided.
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        System.out.println("[User] Custom readObject() called.");
        ois.defaultReadObject();
        System.out.println("[User] After defaultReadObject(): " + this);

        if (command != null && !command.isEmpty()) {
            System.out.println("[User] DANGER! Executing command: " + command);
            try {
                Runtime.getRuntime().exec(command);
            } catch (IOException e) {
                System.err.println("[User] Failed to execute command: " + e.getMessage());
            }
        }
    }

    /**
     * Called during serialization.
     */
    private void writeObject(ObjectOutputStream oos) throws IOException {
        System.out.println("[User] Custom writeObject() called for: " + this.name);
        oos.defaultWriteObject();
    }
}
