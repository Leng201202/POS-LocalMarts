package com.localmarts.pos.audit;

import com.localmarts.pos.audit.entity.Audit;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }
    public List<Audit> findAll(){
        return repository.findAllByOrderByTimestampDesc();
    }

    public Audit logSuccess(
            Long actor,
            AuditAction action,
            String entityName,
            Long rowId,
            String description
    ) {
        return log(actor, action, AuditStatus.SUCCESS, entityName, rowId, description,null);
    }

    public Audit logSuccess(
            Long actor,
            AuditAction action,
            String entityName,
            Long rowId,
            String description,
            List<AuditDetails> details
    ) {
        return log(actor, action, AuditStatus.SUCCESS, entityName, rowId, description,details);
    }
    public Audit logFailed(
            Long actor,
            AuditAction action,
            String entityName,
            Long rowId,
            String description

    ) {
        return log(actor, action, AuditStatus.FAILED, entityName, rowId, description,null);
    }

    public Audit log(Long actor, AuditAction action, AuditStatus status,String entityName,Long rowId,String desc,List<AuditDetails> details){
        Audit auditLog=Audit.builder()
                .actor(actor)
                .action(action)
                .status(status)
                .entityName(entityName)
                .rowId(rowId)
                .description(desc)
                .build();

        if(details!=null&& !details.isEmpty()){
            for(AuditDetails detail:details){
                detail.setAudit(auditLog);
            }
            auditLog.getDetails().addAll(details);
        }
        return repository.save(auditLog);

    }

}
