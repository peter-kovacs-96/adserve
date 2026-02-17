package io.adserve.orchestration.service;

import io.adserve.orchestration.controller.AdRequest;
import io.adserve.orchestration.openrtb.*;
import io.adserve.segment.grpc.GetSegmentsResponse;
import io.adserve.user.grpc.GetUserResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BidRequestBuilder {

    private static final Publisher PUBLISHER = new Publisher("adserve", "AdServe", "adserve.io");

    public BidRequest build(String requestId, String traceId, AdRequest adRequest,
                            String userAgent, String ip,
                            GetUserResponse userResp, GetSegmentsResponse segResp) {
        var user = userResp.getUser();
        var demographics = user.getDemographics();
        var dc = adRequest.device();
        var gc = dc.geo();

        var format = adRequest.sizes().stream().map(s -> new Format(s.w(), s.h())).toList();

        var banner = new Banner(
                adRequest.sizes().getFirst().w(),
                adRequest.sizes().getFirst().h(),
                format,
                adRequest.btype(),
                adRequest.battr(),
                adRequest.pos());

        var imp = new Imp(
                "imp-1",
                banner,
                adRequest.bidFloor(),
                "USD",
                adRequest.secure() ? 1 : 0,
                adRequest.rewarded() ? 1 : 0,
                adRequest.tagId(),
                adRequest.interstitial() ? 1 : 0);

        var geo = new Geo(
                gc.lat(),
                gc.lon(),
                demographics.getCountry(),
                demographics.getRegion(),
                gc.city(),
                OpenRtb.GeoType.IP,
                gc.accuracy());

        var device = new Device(
                userAgent,
                geo,
                ip,
                dc.type(),
                dc.make(),
                dc.model(),
                dc.os(),
                dc.osv(),
                dc.language(),
                dc.js(),
                dc.w(),
                dc.h(),
                dc.dnt(),
                dc.lmt());

        var segments = segResp.getSegmentsList().stream()
                .map(s -> new Segment(s.getId(), s.getName(), String.valueOf(s.getScore())))
                .toList();

        var ortbUser = new User(
                user.getUserId(),
                List.of(new Data("adserve", "AdServe", segments)),
                adRequest.consent(),
                adRequest.yob(),
                adRequest.gender());

        var source = new Source(
                OpenRtb.FinalDecision.EXCHANGE,
                new Schain(1, List.of(new SchainNode("adserve.io", "direct", 1, requestId)), "1.0"),
                traceId,
                "adserve:" + requestId);

        var regs = new Regs(adRequest.regs().coppa(), adRequest.regs().gdpr(),
                adRequest.regs().usPrivacy(), adRequest.regs().gpp());

        Site site = null;
        App app = null;
        if (adRequest.site() != null) {
            var sc = adRequest.site();
            site = new Site(sc.id(), sc.name(), sc.domain(), sc.page(), sc.ref(), sc.cat(), PUBLISHER);
        } else {
            var ac = adRequest.app();
            app = new App(ac.id(), ac.name(), ac.bundle(), ac.storeurl(), ac.domain(), ac.cat(), ac.ver(), PUBLISHER);
        }

        return new BidRequest(
                requestId,
                List.of(imp),
                site,
                app,
                device,
                ortbUser,
                source,
                regs,
                OpenRtb.AuctionType.FIRST_PRICE,
                adRequest.tmax(),
                List.of("USD"),
                adRequest.blockedCategories(),
                adRequest.blockedAdvertisers(),
                adRequest.test() ? 1 : 0);
    }
}
