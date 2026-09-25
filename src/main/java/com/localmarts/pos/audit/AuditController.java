package com.localmarts.pos.audit;

import com.localmarts.pos.audit.entity.Audit;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("audit")
public class AuditController {
    private static final int PAGE_SIZE = 20;
    private final AuditService auditService;


    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping()
    public String findAll(
            @RequestParam(defaultValue = "0") int page,
            Model model
    ){
        Page<Audit> auditPage = auditService.findAll(
                PageRequest.of(Math.max(page, 0), PAGE_SIZE)
        );
        model.addAttribute("audits", auditPage.getContent());
        model.addAttribute("page", auditPage);
        return "admin/audit/index";
    }
}
