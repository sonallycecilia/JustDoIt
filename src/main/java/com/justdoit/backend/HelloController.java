package com.justdoit.backend; // Confirme se o pacote está igual ao seu

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String sayHello() {
        return "Hello World! O JustDoIt está online!";
    }
}