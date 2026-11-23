package com.minhaempresa.recrutamentoapi.user.repository;

import com.minhaempresa.recrutamentoapi.user.entity.User;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** {@code email} must already be lower case. */
    Optional<User> findByEmail(String email);

    /** {@code phone} must already be in E.164. */
    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    boolean existsByRoleAndPasswordHashIsNotNull(UserRole role);

    List<User> findByRole(UserRole role);

    List<User> findByRoleIn(Collection<UserRole> roles, Sort sort);
}
