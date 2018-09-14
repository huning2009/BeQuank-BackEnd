package com.belfry.bequank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

import java.util.LinkedList;
import java.util.List;


@SpringBootApplication
@EnableCaching
public class BequankApplication {

    public static void main(String[] args) {

        System.out.println(" 4".split(" ")[0].equals(""));
        SpringApplication.run(BequankApplication.class, args);
    }
}
