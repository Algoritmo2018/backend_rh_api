package com.minhaempresa.recrutamentoapi.shared.config;

/** Names of the application caches (catalogs, settings and public job data). */
public final class CacheNames {

    /** Featured jobs of the home page (key: limit). Evicted on every job change. */
    public static final String PUBLIC_JOBS_FEATURED = "public-jobs-featured";

    /** Open jobs per area (home page). Evicted on every job change. */
    public static final String PUBLIC_JOBS_AREA_COUNTS = "public-jobs-area-counts";

    public static final String PIPELINE_STAGES = "pipeline-stages";
    public static final String REJECTION_REASONS = "rejection-reasons";
    public static final String TAGS = "tags";
    public static final String MESSAGE_TEMPLATES = "message-templates";
    public static final String OFFER_TEMPLATES = "offer-templates";
    public static final String COMPANY_SETTINGS = "company-settings";

    private CacheNames() {}
}
