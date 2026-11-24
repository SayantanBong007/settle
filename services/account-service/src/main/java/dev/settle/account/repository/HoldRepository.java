package dev.settle.account.repository;

import dev.settle.account.entity.HoldEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HoldRepository extends JpaRepository<HoldEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HoldEntity h WHERE h.id = :id")
    Optional<HoldEntity> findByIdForUpdate(@Param("id") String id);
}
