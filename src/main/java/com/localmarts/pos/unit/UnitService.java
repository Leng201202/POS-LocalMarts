package com.localmarts.pos.unit;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.audit.utils.AuditChangeHelper;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.unit.entity.Unit;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UnitService {

    private final String entity = "units";
    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;

    public UnitService(
            UnitRepository unitRepository,
            ProductRepository productRepository,
            AuditService auditService) {
        this.unitRepository = unitRepository;
        this.productRepository = productRepository;
        this.auditService = auditService;
    }

    public List<Unit> findAll() {
        return unitRepository.findByDeletedAtIsNullOrderByNameAsc();
    }

    public Unit findById(Long id) {
        return unitRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Unit not found"));
    }

    public Unit create(Unit unit, Long createdBy) {
        unit.setName(unit.getName().trim());
        unit.setAbbreviation(unit.getAbbreviation().trim());
        validateUniqueUnit(unit, createdBy, AuditAction.CREATE, null);

        unit.setCreatedBy(createdBy);
        unit.setActive(true);
        Unit savedUnit = unitRepository.save(unit);
        auditService.logSuccess(createdBy, AuditAction.CREATE, entity, savedUnit.getId(), "Created unit: " + savedUnit.getName());
        return savedUnit;
    }

    public void update(Long id, Unit formUnit, Long updatedBy) {
        formUnit.setName(formUnit.getName().trim());
        formUnit.setAbbreviation(formUnit.getAbbreviation().trim());
        Unit existingUnit = findById(id);

        if (!existingUnit.getName().equalsIgnoreCase(formUnit.getName())
                && unitRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(formUnit.getName())) {
            auditService.logFailed(updatedBy, AuditAction.UPDATE, entity, id, "Failed to update unit. Name already exists: " + formUnit.getName());
            throw new RuntimeException("Unit name already exists");
        }

        if (!existingUnit.getAbbreviation().equalsIgnoreCase(formUnit.getAbbreviation())
                && unitRepository.existsByAbbreviationIgnoreCaseAndDeletedAtIsNull(formUnit.getAbbreviation())) {
            auditService.logFailed(updatedBy, AuditAction.UPDATE, entity, id, "Failed to update unit. Abbreviation already exists: " + formUnit.getAbbreviation());
            throw new RuntimeException("Unit abbreviation already exists");
        }

        List<AuditDetails> details = new AuditChangeHelper()
                .addChange("name", existingUnit.getName(), formUnit.getName())
                .addChange("abbreviation", existingUnit.getAbbreviation(), formUnit.getAbbreviation())
                .addChange("active", existingUnit.getActive(), formUnit.getActive())
                .build();

        existingUnit.setName(formUnit.getName());
        existingUnit.setAbbreviation(formUnit.getAbbreviation());
        existingUnit.setActive(formUnit.getActive());
        existingUnit.setUpdatedBy(updatedBy);

        unitRepository.save(existingUnit);
        auditService.logSuccess(updatedBy, AuditAction.UPDATE, entity, id, "Updated unit: " + existingUnit.getName(), details);
    }

    public void softDelete(Long id, Long deletedBy) {
        Unit unit = findById(id);

        if (productRepository.existsByUnitId(id)) {
            auditService.logFailed(
                    deletedBy,
                    AuditAction.DELETE,
                    entity,
                    id,
                    "Failed to delete unit because it is used by a product: "
                            + unit.getName()
            );
            throw new RuntimeException(
                    "Unit cannot be deleted because it is used by a product"
            );
        }

        unit.setDeletedBy(deletedBy);
        unit.setDeletedAt(LocalDateTime.now());
        unit.setActive(false);

        unitRepository.save(unit);
        auditService.logSuccess(deletedBy, AuditAction.DELETE, entity, id, "Deleted unit: " + unit.getName(), null);
    }

    private void validateUniqueUnit(Unit unit, Long actor, AuditAction action, Long rowId) {
        if (unitRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(unit.getName())) {
            auditService.logFailed(actor, action, entity, rowId, "Failed to create unit. Name already exists: " + unit.getName());
            throw new RuntimeException("Unit name already exists");
        }

        if (unitRepository.existsByAbbreviationIgnoreCaseAndDeletedAtIsNull(unit.getAbbreviation())) {
            auditService.logFailed(actor, action, entity, rowId, "Failed to create unit. Abbreviation already exists: " + unit.getAbbreviation());
            throw new RuntimeException("Unit abbreviation already exists");
        }
    }
}
