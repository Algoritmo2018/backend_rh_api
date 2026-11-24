package com.minhaempresa.recrutamentoapi.job.listener;

import com.minhaempresa.recrutamentoapi.client.event.ClientUpdatedEvent;
import com.minhaempresa.recrutamentoapi.shared.config.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The public job caches show client names (featured jobs) and depend on the client being active: a
 * client change evicts them after the commit, so a renamed company never shows its old name.
 */
@Component
public class PublicJobCacheListener {

    @TransactionalEventListener(fallbackExecution = true)
    @CacheEvict(cacheNames = {CacheNames.PUBLIC_JOBS_FEATURED, CacheNames.PUBLIC_JOBS_AREA_COUNTS}, allEntries = true)
    public void onClientUpdated(ClientUpdatedEvent event) {
        // Eviction only (annotation)
    }
}
