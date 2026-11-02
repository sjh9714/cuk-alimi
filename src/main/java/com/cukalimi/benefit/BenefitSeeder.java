package com.cukalimi.benefit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 앱이 뜰 때 benefits.yml을 읽어 BenefitStore에 넘긴다. 파일이 규칙을 어기면 앱이 뜨지 않는다. */
@Component
@Order(0)
public class BenefitSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenefitSeeder.class);

    private final BenefitStore store;

    public BenefitSeeder(BenefitStore store) {
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        BenefitStore.Result r = store.seed(BenefitSeedFile.load());
        log.info("benefits total={} inserted={} updated={} deactivated={}", r.total(), r.inserted(), r.updated(), r.deactivated());
    }
}
