package com.example.loadbalancer.controller;

import com.example.loadbalancer.Server;
import com.example.loadbalancer.service.LoadBalancingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
public class LoadBalancerController {

    @Autowired
    private Map<String, LoadBalancingStrategy> strategies;

    @Value("${loadBalancer.strategy}")
    private String strategyName;

    private final WebClient webClient = WebClient.builder().build();

    private final List<Server> servers = Arrays.asList(
            new Server("http://localhost:8081", 5),
            new Server("http://localhost:8082", 3),
            new Server("http://localhost:8083", 2)
    );

    @GetMapping("/**")
    public Mono<ResponseEntity<String>> proxy(
            HttpServletRequest request,
            @RequestHeader HttpHeaders headers) {

        // Get client IP from HttpServletRequest
        String clientIp = request.getRemoteAddr();

        LoadBalancingStrategy strategy = strategies.get(strategyName);
        Server selectedServer = strategy.selectServer(servers, clientIp);

        selectedServer.getActiveConnections().incrementAndGet();
        log.info("Routing to {} using {} strategy", selectedServer.getUrl(), strategyName);

        // Get path from HttpServletRequest
        String path = request.getRequestURI();
        String queryString = request.getQueryString();
        String targetUrl = selectedServer.getUrl() + path +
                           (queryString != null ? "?" + queryString : "");

        return webClient
                .method(org.springframework.http.HttpMethod.valueOf(request.getMethod()))
                .uri(targetUrl)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .toEntity(String.class)
                .doFinally(signal -> {
                    selectedServer.getActiveConnections().decrementAndGet();
                    log.info("Request completed for {}", selectedServer.getUrl());
                })
                .onErrorResume(error -> {
                    log.error("Error proxying request: ", error);
                    return Mono.just(ResponseEntity.status(502).body("Bad Gateway"));
                });
    }
}