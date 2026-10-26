package io.github.ilyaslabs.moduleswitch.it.shop.reports.impl;

import io.github.ilyaslabs.moduleswitch.it.shop.reports.api.ReportsApi;

import org.springframework.stereotype.Service;

/** No test enables reports, so this class must never be initialized. */
@Service
class ReportsService implements ReportsApi {

    static final String LOADED_PROPERTY = "module-switch.it.reports-loaded";

    static {
        System.setProperty(LOADED_PROPERTY, "true");
    }

    @Override
    public String daily() {
        return "report";
    }
}
