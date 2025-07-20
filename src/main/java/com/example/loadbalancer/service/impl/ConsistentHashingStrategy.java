package com.example.loadbalancer.service.impl;

import com.example.loadbalancer.Server;
import com.example.loadbalancer.service.LoadBalancingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Consistent Hashing implementation for load balancing.
 * Uses virtual nodes to ensure better distribution.
 */
@Component("consistentHashing")
@Slf4j
public class ConsistentHashingStrategy implements LoadBalancingStrategy {

    // Number of virtual nodes per physical server
    private static final int VIRTUAL_NODES = 150;

    // The hash ring - using TreeMap for efficient lookup
    private final NavigableMap<Long, Server> hashRing = new ConcurrentSkipListMap<>();

    // Track virtual nodes for each server
    private final Map<Server, Set<Long>> serverVirtualNodes = new HashMap<>();

    private final MessageDigest md5;

    public ConsistentHashingStrategy() {
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    @Override
    public Server selectServer(List<Server> servers, String clientIp) {
        // Update hash ring if servers have changed
        updateHashRing(servers);

        if (hashRing.isEmpty()) {
            throw new RuntimeException("No servers available in hash ring");
        }

        // Get hash of client IP
        long hash = hash(clientIp);

        // Find the first server clockwise from the hash
        Map.Entry<Long, Server> entry = hashRing.ceilingEntry(hash);

        // If no server found, wrap around to the first server
        if (entry == null) {
            entry = hashRing.firstEntry();
        }

        Server selected = entry.getValue();

        // Skip unhealthy servers
        if (!selected.isHealthy()) {
            return selectNextHealthyServer(hash, servers);
        }

        log.debug("Consistent hashing selected {} for client {}", selected.getUrl(), clientIp);
        return selected;
    }

    /**
     * Update the hash ring when servers change
     */
    private void updateHashRing(List<Server> currentServers) {
        Set<Server> currentSet = new HashSet<>(currentServers);
        Set<Server> existingSet = new HashSet<>(serverVirtualNodes.keySet());

        // Remove servers that are no longer in the list
        existingSet.stream()
                .filter(server -> !currentSet.contains(server))
                .forEach(this::removeServer);

        // Add new servers
        currentSet.stream()
                .filter(server -> !existingSet.contains(server))
                .forEach(this::addServer);
    }

    /**
     * Add a server to the hash ring with virtual nodes
     */
    public void addServer(Server server) {
        Set<Long> virtualNodes = new HashSet<>();

        for (int i = 0; i < VIRTUAL_NODES; i++) {
            String virtualNodeName = server.getUrl() + "#" + i;
            long hash = hash(virtualNodeName);
            hashRing.put(hash, server);
            virtualNodes.add(hash);
        }

        serverVirtualNodes.put(server, virtualNodes);
        log.info("Added server {} with {} virtual nodes to hash ring",
                server.getUrl(), VIRTUAL_NODES);
    }

    /**
     * Remove a server from the hash ring
     */
    public void removeServer(Server server) {
        Set<Long> virtualNodes = serverVirtualNodes.remove(server);

        if (virtualNodes != null) {
            virtualNodes.forEach(hashRing::remove);
            log.info("Removed server {} with {} virtual nodes from hash ring",
                    server.getUrl(), virtualNodes.size());
        }
    }

    /**
     * Find next healthy server in the ring
     */
    private Server selectNextHealthyServer(long startHash, List<Server> servers) {
        Long currentHash = startHash;
        Set<Server> attempted = new HashSet<>();

        while (attempted.size() < servers.size()) {
            Map.Entry<Long, Server> entry = hashRing.higherEntry(currentHash);

            if (entry == null) {
                // Wrap around
                entry = hashRing.firstEntry();
            }

            if (entry == null) {
                break;
            }

            Server server = entry.getValue();
            attempted.add(server);

            if (server.isHealthy()) {
                return server;
            }

            currentHash = entry.getKey();
        }

        throw new RuntimeException("No healthy servers available");
    }

    /**
     * Hash function using MD5
     */
    private long hash(String key) {
        synchronized (md5) {
            md5.reset();
            byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));

            // Convert first 8 bytes to long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }

            return hash;
        }
    }

    /**
     * Get current state of the hash ring (for monitoring/debugging)
     */
    public Map<String, Object> getRingState() {
        Map<String, Object> state = new HashMap<>();
        state.put("totalNodes", hashRing.size());
        state.put("servers", serverVirtualNodes.size());
        state.put("virtualNodesPerServer", VIRTUAL_NODES);

        // Calculate distribution
        Map<Server, Integer> distribution = new HashMap<>();
        hashRing.values().forEach(server ->
                distribution.merge(server, 1, Integer::sum)
        );

        state.put("distribution", distribution);
        return state;
    }

    /**
     * Analyze key distribution (useful for testing)
     */
    public Map<Server, Integer> analyzeDistribution(List<String> keys) {
        Map<Server, Integer> distribution = new HashMap<>();

        for (String key : keys) {
            Server server = selectServer(new ArrayList<>(serverVirtualNodes.keySet()), key);
            distribution.merge(server, 1, Integer::sum);
        }
        return distribution;
    }
}
