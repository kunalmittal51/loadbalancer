package com.example.loadbalancer.service.impl;

import com.example.loadbalancer.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashingStrategyTest {

    private ConsistentHashingStrategy strategy;
    private List<Server> servers;

    @BeforeEach
    void setUp() {
        strategy = new ConsistentHashingStrategy();
        servers = Arrays.asList(
                new Server("http://localhost:8081", 1),
                new Server("http://localhost:8082", 1),
                new Server("http://localhost:8083", 1)
        );
    }

    @Test
    @DisplayName("Should distribute requests across servers")
    void testDistribution() {
        Map<Server, Integer> distribution = new HashMap<>();

        // Generate 10000 random client IPs
        for (int i = 0; i < 10000; i++) {
            String clientIp = generateRandomIp();
            Server selected = strategy.selectServer(servers, clientIp);
            distribution.merge(selected, 1, Integer::sum);
        }

        // Print distribution
        System.out.println("Distribution with " + servers.size() + " servers:");
        distribution.forEach((server, count) ->
                System.out.printf("%s: %d (%.2f%%)\n",
                        server.getUrl(), count, (count * 100.0) / 10000)
        );

        // Check that all servers got some requests
        assertEquals(servers.size(), distribution.size());

        // Check distribution is relatively even (within 20% of ideal)
        int ideal = 10000 / servers.size();
        distribution.values().forEach(count ->
                assertTrue(Math.abs(count - ideal) < ideal * 0.5,
                        "Distribution should be relatively even")
        );
    }

    @Test
    @DisplayName("Should maintain consistency - same IP always goes to same server")
    void testConsistency() {
        String clientIp = "192.168.1.100";

        // Make 100 requests with same IP
        Server firstServer = strategy.selectServer(servers, clientIp);

        for (int i = 0; i < 100; i++) {
            Server selected = strategy.selectServer(servers, clientIp);
            assertEquals(firstServer, selected,
                    "Same client IP should always go to same server");
        }
    }

    @Test
    @DisplayName("Should handle server addition with minimal redistribution")
    void testServerAddition() {
        // Track initial mappings
        Map<String, Server> initialMappings = new HashMap<>();
        List<String> testIps = generateTestIps(1000);

        for (String ip : testIps) {
            initialMappings.put(ip, strategy.selectServer(servers, ip));
        }

        // Add a new server
        Server newServer = new Server("http://localhost:8084", 1);
        List<Server> updatedServers = new ArrayList<>(servers);
        updatedServers.add(newServer);

        // Track how many mappings changed
        int changed = 0;
        Map<Server, Integer> newDistribution = new HashMap<>();

        for (String ip : testIps) {
            Server newMapping = strategy.selectServer(updatedServers, ip);
            newDistribution.merge(newMapping, 1, Integer::sum);

            if (!newMapping.equals(initialMappings.get(ip))) {
                changed++;
            }
        }

        double changePercentage = (changed * 100.0) / testIps.size();
        System.out.printf("Server addition: %.2f%% of keys remapped\n", changePercentage);
        System.out.println("New distribution: " + newDistribution);

        // In consistent hashing, roughly 1/n keys should be remapped when adding a server
        double expectedChange = 100.0 / updatedServers.size();
        assertTrue(changePercentage < expectedChange * 1.5,
                "Should minimize redistribution on server addition");
    }

    @Test
    @DisplayName("Should handle server removal with minimal redistribution")
    void testServerRemoval() {
        // Track initial mappings
        Map<String, Server> initialMappings = new HashMap<>();
        List<String> testIps = generateTestIps(1000);

        for (String ip : testIps) {
            initialMappings.put(ip, strategy.selectServer(servers, ip));
        }

        // Remove a server
        Server removedServer = servers.get(0);
        List<Server> updatedServers = new ArrayList<>(servers);
        updatedServers.remove(removedServer);

        // Track changes
        int changed = 0;
        int affectedByRemoval = 0;

        for (String ip : testIps) {
            Server oldMapping = initialMappings.get(ip);
            Server newMapping = strategy.selectServer(updatedServers, ip);

            if (oldMapping.equals(removedServer)) {
                affectedByRemoval++;
            }

            if (!oldMapping.equals(newMapping) && !oldMapping.equals(removedServer)) {
                changed++;
            }
        }

        System.out.printf("Server removal: %d keys affected, %d additional keys changed\n",
                affectedByRemoval, changed);

        // Only keys mapped to removed server should change
        assertEquals(0, changed,
                "Only keys from removed server should be redistributed");
    }

    @Test
    @DisplayName("Should skip unhealthy servers")
    void testUnhealthyServers() {
        // Mark first server as unhealthy
        servers.get(0).setHealthy(false);

        // Requests that would go to unhealthy server should go to next healthy one
        Set<Server> selectedServers = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            Server selected = strategy.selectServer(servers, generateRandomIp());
            assertTrue(selected.isHealthy(), "Should not select unhealthy server");
            selectedServers.add(selected);
        }

        // Should use remaining healthy servers
        assertEquals(2, selectedServers.size());
    }

    @Test
    @DisplayName("Virtual nodes should improve distribution")
    void testVirtualNodeDistribution() {
        // Test with just 2 servers to see virtual nodes effect
        List<Server> twoServers = Arrays.asList(
                new Server("http://localhost:8081", 1),
                new Server("http://localhost:8082", 1)
        );

        Map<Server, Integer> distribution = new HashMap<>();

        for (int i = 0; i < 10000; i++) {
            Server selected = strategy.selectServer(twoServers, generateRandomIp());
            distribution.merge(selected, 1, Integer::sum);
        }

        // Calculate standard deviation
        double mean = 10000.0 / twoServers.size();
        double variance = distribution.values().stream()
                .mapToDouble(count -> Math.pow(count - mean, 2))
                .average()
                .orElse(0);
        double stdDev = Math.sqrt(variance);
        double coefficientOfVariation = (stdDev / mean) * 100;

        System.out.printf("Distribution variance: %.2f%% (lower is better)\n",
                coefficientOfVariation);

        // With virtual nodes, distribution should be quite even
        assertTrue(coefficientOfVariation < 10,
                "Virtual nodes should provide even distribution");
    }

    // Helper methods
    private String generateRandomIp() {
        Random rand = new Random();
        return String.format("%d.%d.%d.%d",
                rand.nextInt(256), rand.nextInt(256),
                rand.nextInt(256), rand.nextInt(256));
    }

    private List<String> generateTestIps(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> String.format("192.168.%d.%d", i / 256, i % 256))
                .toList();
    }
}

/**
 * Visual demonstration of consistent hashing
 */
class ConsistentHashingDemo {

    public static void main(String[] args) {
        ConsistentHashingStrategy strategy = new ConsistentHashingStrategy();

        // Initial servers
        List<Server> servers = new ArrayList<>(Arrays.asList(
                new Server("Server-A", 1),
                new Server("Server-B", 1),
                new Server("Server-C", 1)
        ));

        // Test IPs
        List<String> testIps = Arrays.asList(
                "10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5",
                "10.0.0.6", "10.0.0.7", "10.0.0.8", "10.0.0.9", "10.0.0.10"
        );

        System.out.println("=== Initial State (3 servers) ===");
        printMappings(strategy, servers, testIps);

        // Add a server
        System.out.println("\n=== After adding Server-D ===");
        servers.add(new Server("Server-D", 1));
        printMappings(strategy, servers, testIps);

        // Remove a server
        System.out.println("\n=== After removing Server-B ===");
        servers.removeIf(s -> s.getUrl().equals("Server-B"));
        printMappings(strategy, servers, testIps);

        // Show hash ring state
        System.out.println("\n=== Hash Ring State ===");
        System.out.println(strategy.getRingState());
    }

    private static void printMappings(ConsistentHashingStrategy strategy,
                                      List<Server> servers, List<String> ips) {
        Map<String, String> mappings = new TreeMap<>();
        Map<String, Integer> distribution = new HashMap<>();

        for (String ip : ips) {
            Server server = strategy.selectServer(servers, ip);
            mappings.put(ip, server.getUrl());
            distribution.merge(server.getUrl(), 1, Integer::sum);
        }

        mappings.forEach((ip, server) ->
                System.out.printf("%s -> %s\n", ip, server));

        System.out.println("\nDistribution: " + distribution);
    }
}