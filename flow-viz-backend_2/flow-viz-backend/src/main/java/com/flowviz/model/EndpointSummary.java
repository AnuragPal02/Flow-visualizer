package com.flowviz.model;

import java.util.ArrayList;
import java.util.List;

/** "At a glance" facts for one endpoint, derived from its flow. */
public class EndpointSummary {

    public int steps;
    public List<String> layers = new ArrayList<>();   // node types in the order the flow reaches them
    public List<String> reads = new ArrayList<>();    // entities read through repositories
    public List<String> writes = new ArrayList<>();   // entities written through repositories
    public List<String> touches = new ArrayList<>();  // repository access that is neither clearly read nor write
    public boolean transactional;
    public List<String> security = new ArrayList<>(); // e.g. "PreAuthorize: hasRole('USER')"
    public List<IntegrationCount> integrations = new ArrayList<>();

    public static class IntegrationCount {
        public final String kind;
        public final String client;
        public int calls;

        public IntegrationCount(String kind, String client) {
            this.kind = kind;
            this.client = client;
        }
    }
}
