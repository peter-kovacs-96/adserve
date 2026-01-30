package io.adserve.orchestration.client.partner;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Registry providing access to partner clients by their ID.
 */
@Component
public class PartnerClientRegistry {

    private final Map<String, PartnerBidClient> clients;

    public PartnerClientRegistry(
            NikeClient nikeClient,
            AdidasClient adidasClient,
            PumaClient pumaClient,
            UnderArmourClient underArmourClient,
            NewBalanceClient newBalanceClient,
            AsicsClient asicsClient,
            ReebokClient reebokClient,
            SkechersClient skechersClient,
            ColumbiaClient columbiaClient,
            NorthFaceClient northFaceClient
    ) {
        this.clients = Map.of(
                "nike", nikeClient,
                "adidas", adidasClient,
                "puma", pumaClient,
                "underarmour", underArmourClient,
                "newbalance", newBalanceClient,
                "asics", asicsClient,
                "reebok", reebokClient,
                "skechers", skechersClient,
                "columbia", columbiaClient,
                "northface", northFaceClient
        );
    }

    public PartnerBidClient get(String partnerId) {
        return clients.get(partnerId);
    }

    public Set<String> getPartnerIds() {
        return clients.keySet();
    }
}
