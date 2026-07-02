package com.anvritai.abhay.repository.gst;

import com.anvritai.abhay.domain.gst.GstHsnSac;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GstHsnSacRepository extends JpaRepository<GstHsnSac, UUID> {
    @Query("select code from GstHsnSac code where code.company.id = :companyId "
            + "and (:search is null or lower(code.code) like lower(concat('%', :search, '%')) "
            + "or lower(code.description) like lower(concat('%', :search, '%'))) order by code.code")
    List<GstHsnSac> search(@Param("companyId") UUID companyId, @Param("search") String search);
    Optional<GstHsnSac> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<GstHsnSac> findByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
}
