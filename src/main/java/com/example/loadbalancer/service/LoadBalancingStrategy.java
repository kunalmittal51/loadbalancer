package com.example.loadbalancer.service;

import com.example.loadbalancer.Server;

import java.util.List;

public interface LoadBalancingStrategy {
    Server selectServer(List<Server> servers, String clientIp);
}
