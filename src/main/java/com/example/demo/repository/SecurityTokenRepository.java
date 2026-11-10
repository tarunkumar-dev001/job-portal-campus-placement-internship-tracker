package com.example.demo.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.example.demo.entity.SecurityToken;
public interface SecurityTokenRepository extends JpaRepository<SecurityToken, Long> {
    Optional<SecurityToken> findByTokenHashAndType(String tokenHash, SecurityToken.Type type);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SecurityToken t where t.tokenHash = :hash and t.type = :type")
    Optional<SecurityToken> findByTokenHashAndTypeForUpdate(@Param("hash") String hash,
                                                             @Param("type") SecurityToken.Type type);
}
