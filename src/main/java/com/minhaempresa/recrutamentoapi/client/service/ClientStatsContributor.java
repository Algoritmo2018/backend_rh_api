package com.minhaempresa.recrutamentoapi.client.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Implemented by the modules that know about the work done for clients (jobs in stage 5, hires in stage
 * 8), so the client list can show the numbers without the clients module reading their tables.
 */
public interface ClientStatsContributor {

    /** Counts of this module per client id (clients without records may be absent). */
    Map<UUID, ClientCounts> countsByClient(Collection<UUID> clientIds);

    /** Partial counts; contributors fill the ones they know and leave the others at 0. */
    record ClientCounts(long openJobs, long totalJobs, long hires) {

        public static final ClientCounts NONE = new ClientCounts(0, 0, 0);

        public ClientCounts plus(ClientCounts other) {
            return new ClientCounts(openJobs + other.openJobs, totalJobs + other.totalJobs, hires + other.hires);
        }
    }
}
