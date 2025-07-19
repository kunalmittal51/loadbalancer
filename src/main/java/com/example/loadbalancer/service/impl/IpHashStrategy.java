package com.example.loadbalancer.service.impl;

import com.example.loadbalancer.service.LoadBalancingStrategy;
import com.example.loadbalancer.Server;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("ipHashStrategy")
public class IpHashStrategy implements LoadBalancingStrategy {

    @Override
    public Server selectServer(List<Server> servers, String clientIp) {
        List<Server> healthyServers = servers.stream()
                .filter(Server::isHealthy)
                .toList();

        if (healthyServers.isEmpty()) {
            throw new RuntimeException("No healthy servers available");
        }

        // Hash the client IP to determine the server index
        int index = Math.abs(clientIp.hashCode()) % healthyServers.size();
        return healthyServers.get(index);
    }
}
