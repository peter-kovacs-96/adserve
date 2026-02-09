package io.adserve.orchestration.client.partner;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class PartnerClientRegistry {

    private final Map<String, PartnerBidClient> clients;

    // Automatically collects all @HttpExchange proxies implementing this interface
    public PartnerClientRegistry(Map<String, PartnerBidClient> partnerClients) {
        this.clients = partnerClients;
    }

    public PartnerBidClient get(String partnerId) {
        return clients.get(partnerId);
    }

    public Set<String> getPartnerIds() {
        return clients.keySet();
    }
}