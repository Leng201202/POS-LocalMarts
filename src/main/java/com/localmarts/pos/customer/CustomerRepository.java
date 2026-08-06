package com.localmarts.pos.customer;


import com.localmarts.pos.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer,Long> {
    List<Customer> findByDeletedAtIsNull();
    Optional<Customer> findByPhoneNumberAndDeletedAtIsNull(String phone);
    Optional<Customer> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByPhoneNumberAndActiveTrueAndDeletedAtIsNull(String phone);

}
