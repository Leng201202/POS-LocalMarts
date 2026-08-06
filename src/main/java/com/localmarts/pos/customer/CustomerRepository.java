package com.localmarts.pos.customer;


import com.localmarts.pos.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer,Long> {
    List<Customer> findByDeletedAtIsNull();
    Optional<Customer> findByPhoneNumberAndDeletedAtIsNull(String phone);
    Optional<Customer> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByPhoneNumberAndActiveTrueAndDeletedAtIsNull(String phone);

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
        ORDER BY c.created_at DESC
    """)
    List<Customer> filter(@Param("keyword") String keyword);

}
