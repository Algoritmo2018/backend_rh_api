package com.minhaempresa.recrutamentoapi.client.repository;

import com.minhaempresa.recrutamentoapi.client.entity.Client;
import com.minhaempresa.recrutamentoapi.client.entity.ClientStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    /**
     * Ids of the clients whose name contains the term, ignoring case and accents. The term must already
     * have {@code \}, {@code %} and {@code _} escaped with {@code \}.
     */
    @Query(value = """
            SELECT id FROM clients
            WHERE unaccent(lower(name)) LIKE '%' || unaccent(lower(:term)) || '%' ESCAPE '\\'""",
            nativeQuery = true)
    List<UUID> findIdsByNameContaining(@Param("term") String escapedTerm);

    /** Every client with its contacts (one query). */
    @EntityGraph(attributePaths = "contacts")
    @Query("SELECT c FROM Client c")
    List<Client> findAllWithContacts(Sort sort);

    boolean existsByNif(String nif);

    boolean existsByNifAndIdNot(String nif, UUID id);

    boolean existsByIdAndStatus(UUID id, ClientStatus status);

    List<Client> findByIdIn(Collection<UUID> ids);
}
