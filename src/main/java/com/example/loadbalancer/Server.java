package com.example.loadbalancer;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@AllArgsConstructor
public class Server {
    private String url;
    private int weight;
    private AtomicInteger activeConnections;
    private boolean healthy = true;
    private long lastHealthCheck;

    public Server(String url, int weight) {
        this.url = url;
        this.weight = weight;
        this.activeConnections = new AtomicInteger(0);
    }
}
