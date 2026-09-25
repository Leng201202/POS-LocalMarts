package com.localmarts.pos.customer;


import com.localmarts.pos.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer,Long> {
    List<Customer> findByDeletedAtIsNull();
    Optional<Customer> findByPhoneNumberAndDeletedAtIsNull(String phone);
    Optional<Customer> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByPhoneNumberAndActiveTrueAndDeletedAtIsNull(String phone);
    Optional<Customer> findByPhoneNumberAndActiveTrueAndDeletedAtIsNull(String phone);
    Optional<Customer> findByIdAndActiveTrueAndDeletedAtIsNull(Long id);

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE Customer c
        SET c.pointBalance = COALESCE(c.pointBalance, 0) - :points
        WHERE c.id = :customerId
          AND c.active = true
          AND c.deletedAt IS NULL
          AND COALESCE(c.pointBalance, 0) >= :points
    """)
    int deductPointsIfAvailable(
            @Param("customerId") Long customerId,
            @Param("points") Long points
    );

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE Customer c
        SET c.pointBalance = COALESCE(c.pointBalance, 0) + :pointChange
        WHERE c.id = :customerId
    """)
    int applyPointMovement(
            @Param("customerId") Long customerId,
            @Param("pointChange") Long pointChange
    );

    @Query("SELECT c.pointBalance FROM Customer c WHERE c.id = :customerId")
    Optional<Long> findPointBalanceValueById(@Param("customerId") Long customerId);

    @Query("""
        SELECT c
        FROM Customer c
        WHERE c.active = true
          AND c.deletedAt IS NULL
          AND (
                LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR c.phoneNumber LIKE CONCAT('%', :keyword, '%')
          )
        ORDER BY c.name ASC
    """)
    List<Customer> searchActiveForCashier(@Param("keyword") String keyword);

    @Query("""
        SELECT c
        FROM Customer c
        WHERE c.deletedAt IS NULL
          AND (
                :keyword IS NULL
                OR TRIM(:keyword) = ''
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(c.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY c.createdAt DESC
    """)
    List<Customer> filter(@Param("keyword") String keyword);

    @Query(value = """
        SELECT c
        FROM Customer c
        WHERE c.deletedAt IS NULL
          AND (
                :keyword IS NULL
                OR TRIM(:keyword) = ''
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(c.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY c.createdAt DESC, c.id DESC
    """, countQuery = """
        SELECT COUNT(c)
        FROM Customer c
        WHERE c.deletedAt IS NULL
          AND (
                :keyword IS NULL
                OR TRIM(:keyword) = ''
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(c.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
    """)
    Page<Customer> filter(
            @Param("keyword") String keyword,
            Pageable pageable
    );

}
