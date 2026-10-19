package com.crawler.distributed_crawler;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/search")
    public String searchPage() {
        return "forward:/index.html";
    }
}
