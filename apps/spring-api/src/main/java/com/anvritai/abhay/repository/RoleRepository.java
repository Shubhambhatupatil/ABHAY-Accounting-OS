package com.anvritai.abhay.repository;

import com.anvritai.abhay.domain.Role;
import com.anvritai.abhay.domain.RoleCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);

    default Optional<Role> findByCode(RoleCode code) {
        return findByCode(code.name());
    }
}
