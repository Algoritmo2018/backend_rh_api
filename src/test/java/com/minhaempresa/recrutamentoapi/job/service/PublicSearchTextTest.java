package com.minhaempresa.recrutamentoapi.job.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.recrutamentoapi.shared.text.Slugs;
import org.junit.jupiter.api.Test;

class PublicSearchTextTest {

    @Test
    void searchTextBecomesPrefixTermsOfLettersAndDigitsOnly() {
        assertThat(PublicJobService.toTsQuery("Técnico  HSE")).isEqualTo("técnico:* & hse:*");
        assertThat(PublicJobService.toTsQuery("C++ / 'drop' | x:*")).isEqualTo("c:* & drop:* & x:*");
        assertThat(PublicJobService.toTsQuery("!!! &")).isNull();
        assertThat(PublicJobService.toTsQuery("  ")).isNull();
        assertThat(PublicJobService.toTsQuery("a b c d e f g h i j")).isEqualTo("a:* & b:* & c:* & d:* & e:* & f:* & g:* & h:*");
    }

    @Test
    void slugsLikeTheFrontend() {
        assertThat(Slugs.slugify("Técnico de HSE – Soyo", 70)).isEqualTo("tecnico-de-hse-soyo");
        // NFD, like the frontend: "ª" has no canonical decomposition and is dropped
        assertThat(Slugs.slugify("  Eng.ª Civil (Sénior) ", 70)).isEqualTo("eng-civil-senior");
        assertThat(Slugs.slugify("aaaa bbbb", 6)).isEqualTo("aaaa-b");
        assertThat(Slugs.slugify("aaaa bbbb", 5)).isEqualTo("aaaa");
        assertThat(Slugs.slugify("!!!", 70)).isEmpty();
    }
}
