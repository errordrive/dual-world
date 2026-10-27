package com.example.dualworld.core;

/**
 * Represents a virtual multi-account user partition (e.g., User 0, User 1, User 2).
 */
public class VirtualUser {
    public final int userId;
    public final String name;

    public VirtualUser(int userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    @Override
    public String toString() {
        return "VirtualUser{userId=" + userId + ", name='" + name + "'}";
    }
}
