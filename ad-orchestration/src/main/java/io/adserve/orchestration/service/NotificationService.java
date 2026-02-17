package io.adserve.orchestration.service;

import io.adserve.orchestration.config.HttpClientProperties;
import io.adserve.orchestration.metrics.AdMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class NotificationService {

    private final HttpClient httpClient;
    private final Duration readTimeout;
    private final AdMetrics adMetrics;

    public NotificationService(HttpClient notificationHttpClient,
                               HttpClientProperties properties,
                               AdMetrics adMetrics) {
        this.httpClient = notificationHttpClient;
        this.readTimeout = properties.notification().readTimeout();
        this.adMetrics = adMetrics;
    }

    public void notifyWin(String nurl, String auctionId, double winPrice,
            String bidId, String currency) {
        var url = AuctionMacros.substituteWin(nurl, auctionId, winPrice, bidId, currency);
        fireAsync(url, "win", auctionId);
    }

    public void notifyLoss(String lurl, String auctionId, double bidPrice, int lossReason) {
        var url = AuctionMacros.substituteLoss(lurl, auctionId, bidPrice, lossReason);
        fireAsync(url, "loss", auctionId);
    }

    private void fireAsync(String url, String type, String auctionId) {
        if (url == null || url.isEmpty()) return;
        Thread.startVirtualThread(() -> {
            try {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(readTimeout)
                        .GET()
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                adMetrics.recordNotification(type, true);
            } catch (Exception e) {
                log.debug("Notification failed | type={} | auctionId={} | url={} | error={}",
                        type, auctionId, url, e.getMessage());
                adMetrics.recordNotification(type, false);
            }
        });
    }
}
