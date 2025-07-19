package com.example.loadbalancer.service.impl;

import com.example.loadbalancer.service.LoadBalancingStrategy;
import com.example.loadbalancer.Server;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component("roundRobinStrategy")
public class RoundRobinStrategy implements LoadBalancingStrategy {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Server selectServer(List<Server> servers, String clientIp) {
        List<Server> healthyServers = servers.stream()
                .filter(Server::isHealthy)
                .toList();

        if (healthyServers.isEmpty()) {
            throw new RuntimeException("No healthy servers available");
        }

        int index = counter.getAndIncrement() % healthyServers.size();
        return healthyServers.get(index);
    }
}
