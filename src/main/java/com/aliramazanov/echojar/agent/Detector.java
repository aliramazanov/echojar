package com.aliramazanov.echojar.agent;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.aliramazanov.echojar.bootstrap.EchoSink;
import com.aliramazanov.echojar.bootstrap.findings.CallSite;
import com.aliramazanov.echojar.bootstrap.findings.Echoes;
import com.aliramazanov.echojar.bootstrap.findings.Lease;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.bootstrap.findings.OpenLeases;
import com.aliramazanov.echojar.bootstrap.findings.SqlTemplate;
import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;
import com.aliramazanov.echojar.bootstrap.watch.EchoEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Detector implements EchoSink {

    private static final int WALKS_PER_TEMPLATE = 5;

    private final Templates templates;
    private final CallSites sites;
    private final ConcurrentMap<Integer, Attribution> attributions = new ConcurrentHashMap<>();
    private final int threshold;

    Detector(@NotNull EchoConfig config) {
        this.templates = new Templates(config.templateCacheLimit(), config.suppressNoise());

        this.sites = new CallSites(
                config.frameworkPrefixes(),
                config.applicationPrefixes(),
                config.stackDepth()
        );

        this.threshold = config.threshold();
    }

    long overflowed() {
        return templates.overflowed();
    }

    long ambiguous() {
        return attributions
                .values()
                .stream()
                .filter(attribution -> attribution.ambiguous)
                .count();
    }

    @Override
    public @Nullable SqlTemplate template(String rawSql) {
        SqlTemplate template = templates.of(rawSql);

        if (template.noise()) {
            return null;
        }

        if (attributions.get(template.id()) == null) {
            attributions.computeIfAbsent(
                    template.id(), _ -> {
                        Attribution first = new Attribution();
                        first.walks.incrementAndGet();
                        Diagnostics.stackWalk();
                        first.prepared = sites.resolve();
                        return first;
                    }
            );
        }

        return template;
    }

    @Override
    public void executed(Lease lease, @NotNull Echoes echoes) {
        if (echoes.executions() < threshold || echoes.site() != null) {
            return;
        }

        Attribution attribution = attributions.computeIfAbsent(
                echoes.template().id(),
                _ -> new Attribution()
        );

        CallSite resolved = attribute(attribution);
        echoes.site(resolved);

        EchoEvent.record(
                echoes.template().text(),
                echoes.executions(),
                String.valueOf(resolved)
        );
    }

    private CallSite attribute(@NotNull Attribution attribution) {
        if (attribution.walks.get() >= WALKS_PER_TEMPLATE) {
            return attribution.site;
        }

        attribution.walks.incrementAndGet();
        Diagnostics.stackWalk();
        CallSite found = sites.resolve();

        if (found == null) {
            return attribution.site;
        }

        synchronized (attribution) {
            if (attribution.site == null) {
                attribution.site = found;
            } else if (!attribution.site.equals(found)) {
                attribution.ambiguous = true;
            }
        }

        return found;
    }

    @Override
    public void leaseClosed(@NotNull Lease lease) {
        List<Echoes> closing = lease.echoes();
        attribute(closing);
        OpenLeases.closed(lease);
        Ledger.record(closing);
    }

    void resolve(@NotNull List<Lease> pending) {
        for (Lease lease : pending) {
            attribute(lease.echoes());
        }
    }

    private void attribute(@NotNull List<Echoes> echoing) {
        for (Echoes echoes : echoing) {
            if (echoes.site() == null) {
                Attribution known = attributions.get(echoes.template().id());
                if (known != null) {
                    echoes.site(known.site != null ? known.site : known.prepared);
                }
            }
        }
    }

    private static final class Attribution {

        private final AtomicInteger walks = new AtomicInteger();
        private volatile CallSite site;
        private volatile CallSite prepared;
        private volatile boolean ambiguous;
    }
}
