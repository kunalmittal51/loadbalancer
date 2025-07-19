package com.example.loadbalancer.service.impl;

import com.example.loadbalancer.service.LoadBalancingStrategy;
import com.example.loadbalancer.Server;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("leastConnectionStrategy")
public class LeastConnectionStrategy implements LoadBalancingStrategy {

    @Override
    public Server selectServer(List<Server> servers, String clientIp) {
        List<Server> healthyServers = servers.stream()
                .filter(Server::isHealthy)
                .toList();

        if (healthyServers.isEmpty()) {
            throw new RuntimeException("No healthy servers available");
        }

        // Find the server with the least active connections
        Server leastConnectedServer = healthyServers.get(0);
        for (Server server : healthyServers) {
            if (server.getActiveConnections().get() < leastConnectedServer.getActiveConnections().get()) {
                leastConnectedServer = server;
            }
        }

        // Increment the active connection count for the selected server
        leastConnectedServer.getActiveConnections().incrementAndGet();
        return leastConnectedServer;
    }
}
