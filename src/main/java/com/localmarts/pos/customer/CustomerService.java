package com.localmarts.pos.customer;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.audit.utils.AuditChangeHelper;
import com.localmarts.pos.category.entity.Category;
import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.customer.entity.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {
    private final String entity="Customers";
    private final CustomerRepository customerRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public List<Customer> findAll(String keyword){
        return customerRepository.filter(
                keyword == null ? null : keyword.trim()
        );
    }

    public List<Customer> filter(String keyword) {
        return customerRepository.filter(
                keyword == null ? null : keyword.trim()
        );

    }

    @Transactional
    public Customer create(Customer customer, Long createdBy) {

        String phoneNumber = customer.getPhoneNumber().trim();

        boolean phoneExists =
                customerRepository
                        .existsByPhoneNumberAndActiveTrueAndDeletedAtIsNull(phoneNumber);

        if (phoneExists) {
            auditService.logFailed(
                    createdBy,
                    AuditAction.CREATE,
                    entity,
                    null,
                    "Failed to create customer member: phone number already exists"
            );

            throw new IllegalArgumentException(
                    "Phone number already exists."
            );
        }

        customer.setName(customer.getName().trim());
        customer.setPhoneNumber(phoneNumber);
        customer.setPointBalance(0L);
        customer.setTotalSpending(0);
        customer.setActive(true);
        customer.setCreatedBy(createdBy);
        customer.setUpdatedBy(createdBy);

        Customer savedCustomer =
                customerRepository.save(customer);

        auditService.logSuccess(
                createdBy,
                AuditAction.CREATE,
                entity,
                savedCustomer.getId(),
                "Created customer: " + savedCustomer.getName()
        );

        return savedCustomer;
    }
    @Transactional
    public void update(
            Long id,
            Customer updatedCustomer,
            Long updatedBy
    ){
        Customer customer=findById(id);

        boolean phoneChanged=!customer.getPhoneNumber()
                .equalsIgnoreCase(updatedCustomer.getPhoneNumber());

        if(phoneChanged && customerRepository.existsByPhoneNumberAndActiveTrueAndDeletedAtIsNull(
                updatedCustomer.getPhoneNumber()
        )){
            auditService.logFailed(updatedBy,AuditAction.UPDATE,entity,id,"Failed to update customer phone number,this phone number already existed"+ updatedCustomer.getPhoneNumber());
            throw new RuntimeException("Customer phone no. already exists");
        }
        List<AuditDetails> details=new AuditChangeHelper()
                .addChange(
                        "name",
                        customer.getName(),
                        updatedCustomer.getName()
                )
                .addChange(
                        "phone-number",
                        customer.getPhoneNumber(),
                        updatedCustomer.getPhoneNumber()
                )
                .addChange(
                        "active",
                        customer.getActive(),
                        updatedCustomer.getActive()
                )
                .build();
        customer.setName(updatedCustomer.getName());
        customer.setPhoneNumber(updatedCustomer.getPhoneNumber());
        customer.setActive(updatedCustomer.getActive());
        customer.setUpdatedBy(updatedBy);

        Customer savedCustomer=customerRepository.save(customer);

        auditService.logSuccess(updatedBy,AuditAction.UPDATE,entity,savedCustomer.getId(),"Updated Customer : "+savedCustomer.getName(),details);
    }
    @Transactional
    public void softDelete(Long id,Long deletedBy){
        Customer customer=customerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(()->new RuntimeException("Customer is not found"));
        customer.setDeletedBy(deletedBy);
        customer.setDeletedAt(LocalDateTime.now());
        customer.setActive(false);

        customerRepository.save(customer);
        auditService.logSuccess(deletedBy,AuditAction.DELETE,entity,id,"Deleted customer: "+customer.getName(),null);
    }


    public Customer findById(Long id){
        return customerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(()->new RuntimeException("Customer is not found"));
    }
}
