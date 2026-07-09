package com.localmarts.pos.audit;

import com.localmarts.pos.audit.entity.Audit;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("audit")
public class AuditController {
    private final AuditService auditService;


    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping()
    public String findAll(Model model){
        List<Audit> audits=auditService.findAll();
        model.addAttribute("audits",audits);
        return "admin/audit/index";
    }
}
