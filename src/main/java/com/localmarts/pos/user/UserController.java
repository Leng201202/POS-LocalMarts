package com.localmarts.pos.user;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserController {

    @GetMapping()
    public String home(){
        return "index";
    }
    @GetMapping("/admin")
    public String adminHome() {
        return "admin/index";
    }
    @GetMapping("/cashier")
    public String cashierHome() {
        return "cashier/index";
    }
}
