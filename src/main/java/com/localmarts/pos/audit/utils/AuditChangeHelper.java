package com.localmarts.pos.audit.utils;

import com.localmarts.pos.audit.entity.AuditDetails;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class AuditChangeHelper {
    private final List<AuditDetails> detailsList=new ArrayList<>();

    public AuditChangeHelper addChange(String fieldName,Object oldValue,Object newValue){
        if(!Objects.equals(oldValue,newValue)){
            detailsList.add(AuditDetails.builder()
                    .fieldName(fieldName)
                    .oldData(String.valueOf(oldValue))
                    .newData(String.valueOf(newValue))
                    .build()
            );
        }
        return this;
    }


    public List<AuditDetails> build(){
        return detailsList;
    }
}
