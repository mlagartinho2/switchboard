package com.llizzard.switchboard.actuate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.util.ClassUtils;

import com.llizzard.switchboard.SwitchboardRegistry;
import com.llizzard.switchboard.SwitchboardResolverChain;
import com.llizzard.switchboard.SwitchboardStrategy;
import com.llizzard.switchboard.actuate.SwitchboardReport.CaseReport;
import com.llizzard.switchboard.actuate.SwitchboardReport.GroupReport;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties;
import com.llizzard.switchboard.autoconfigure.SwitchboardProperties.CaseSettings;

/**
 * Read-only actuator endpoint ({@code /actuator/switchboard}) that reports, per group, the
 * effective resolver order and every registered or configured use case (FR-025).
 *
 * <p>The report can reveal routing internals. Like {@code /actuator/env}, restrict access to
 * operators. It is not exposed over HTTP until included in
 * {@code management.endpoints.web.exposure.include}.
 *
 * <p>Public and non-final so a consuming application can replace it (FR-029).
 */
@Endpoint(id = "switchboard")
public class SwitchboardEndpoint {

    private final SwitchboardRegistry registry;
    private final SwitchboardResolverChain chain;
    private final SwitchboardProperties properties;

    /**
     * @param registry the strategy registry
     * @param chain the resolver chain
     * @param properties the bound configuration
     * @throws NullPointerException if an argument is null
     */
    public SwitchboardEndpoint(SwitchboardRegistry registry, SwitchboardResolverChain chain,
            SwitchboardProperties properties) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.chain = Objects.requireNonNull(chain, "chain must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** @return every group with at least one strategy or resolver, sorted by name */
    @ReadOperation
    public SwitchboardReport report() {
        Set<String> groups = new TreeSet<>(registry.groups());
        groups.addAll(chain.groups());
        Map<String, GroupReport> reports = new HashMap<>();
        for (String group : groups) {
            reports.put(group, new GroupReport(chain.resolutionOrder(group), cases(group)));
        }
        return new SwitchboardReport(reports);
    }

    private List<CaseReport> cases(String group) {
        Map<String, SwitchboardStrategy<?, ?>> registered = registry.registered(group);
        Map<String, CaseSettings> configured = properties.group(group).cases();
        Set<String> keys = new TreeSet<>(registered.keySet());
        keys.addAll(configured.keySet());
        return keys.stream().map(key -> {
            SwitchboardStrategy<?, ?> strategy = registered.get(key);
            CaseSettings settings = configured.get(key);
            return new CaseReport(key,
                    registry.isEnabled(group, key),
                    strategy != null,
                    (strategy != null) ? ClassUtils.getUserClass(strategy).getName() : null,
                    (settings != null) ? settings.owner() : null,
                    (settings != null) ? settings.version() : null,
                    (settings != null) ? settings.description() : null);
        }).toList();
    }
}
