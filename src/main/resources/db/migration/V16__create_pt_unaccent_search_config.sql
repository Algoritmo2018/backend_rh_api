-- Full-text search in Portuguese that ignores accents ("logistica" finds "Logística"):
-- a copy of the "portuguese" configuration with the unaccent dictionary before the stemmer.
CREATE TEXT SEARCH CONFIGURATION pt_unaccent (COPY = portuguese);

ALTER TEXT SEARCH CONFIGURATION pt_unaccent
    ALTER MAPPING FOR hword, hword_part, word WITH unaccent, portuguese_stem;
