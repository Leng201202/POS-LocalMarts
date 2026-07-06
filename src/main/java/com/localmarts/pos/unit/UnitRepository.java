package com.localmarts.pos.unit;

import com.localmarts.pos.unit.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByActiveTrueOrderByNameAsc();

    Optional<Unit> findByNameIgnoreCase(String name);

    Optional<Unit> findByAbbreviationIgnoreCase(String abbreviation);
}