package com.localmarts.pos.unit;

import com.localmarts.pos.unit.entity.Unit;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UnitService {

    private final UnitRepository unitRepository;

    public UnitService(UnitRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    public List<Unit> findAllActive() {
        return unitRepository.findByActiveTrueOrderByNameAsc();
    }

    public Unit findById(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Unit not found"));
    }

    public void save(Unit unit) {
        unitRepository.save(unit);
    }

    public void update(Long id, Unit formUnit) {
        Unit existingUnit = findById(id);

        existingUnit.setName(formUnit.getName());
        existingUnit.setAbbreviation(formUnit.getAbbreviation());

        unitRepository.save(existingUnit);
    }

    public void softDelete(Long id) {
        Unit unit = findById(id);
        unit.setActive(false);
        unitRepository.save(unit);
    }
}