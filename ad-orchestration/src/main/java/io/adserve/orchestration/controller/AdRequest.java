package io.adserve.orchestration.controller;

import java.util.List;

public record AdRequest(
        // Impression
        String tagId,
        List<AdSize> sizes,
        Double bidFloor,
        Boolean secure,
        Boolean interstitial,
        Boolean rewarded,
        Integer pos,
        List<Integer> btype,
        List<Integer> battr,

        // Site context (null if app)
        SiteContext site,
        // App context (null if site)
        AppContext app,

        // Device
        DeviceContext device,

        // User
        String userId,
        String consent,
        Integer yob,
        String gender,

        // Regulations
        RegsContext regs,

        // Auction
        Integer tmax,
        List<String> blockedCategories,
        List<String> blockedAdvertisers,
        Boolean test
) {
    public record AdSize(int w, int h) {}
    public record SiteContext(String id, String name, String domain, String page, String ref, List<String> cat) {}
    public record AppContext(String id, String name, String bundle, String storeurl, String domain,
                             List<String> cat, String ver) {}
    public record DeviceContext(Integer type, String make, String model, String os, String osv,
                                String language, Integer w, Integer h, Integer dnt, Integer lmt,
                                Integer js, String ifa, GeoContext geo) {}
    public record GeoContext(Double lat, Double lon, String city, Integer accuracy) {}
    public record RegsContext(Integer coppa, Integer gdpr, String usPrivacy, String gpp) {}
}
