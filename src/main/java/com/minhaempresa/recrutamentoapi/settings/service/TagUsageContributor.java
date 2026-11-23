package com.minhaempresa.recrutamentoapi.settings.service;

import java.util.Map;
import java.util.UUID;

/**
 * Implemented by modules that tag records (jobs, candidates, applications) so the settings screen can
 * show how many records use each tag before it is deleted.
 */
public interface TagUsageContributor {

    /** Number of records of this module per tag id (tags without records may be absent). */
    Map<UUID, Long> countByTag();
}
