package com.anvritai.abhay.repository;

import com.anvritai.abhay.domain.Company;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    @Query("select cm.company from CompanyMember cm where cm.user.id = :userId order by cm.company.legalName")
    List<Company> findAllForUser(@Param("userId") UUID userId);
}
