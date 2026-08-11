package com.localmarts.pos.category;

import com.localmarts.pos.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category,Long> {

    List<Category>  findByActiveTrueAndDeletedAtIsNull();
    Optional<Category> findByIdAndActiveTrueAndDeletedAtIsNull(Long id);
    boolean existsByNameIgnoreCaseAndActiveTrueAndDeletedAtIsNullAndDeletedAtIsNull(String name);

}
