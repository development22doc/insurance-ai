package com.claimassist.platform.customer_service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyControllerSecurityAnnotationTest {

    @Test
    void deleteMethodIsAdminOrOperationsOnly() throws NoSuchMethodException {
        Method m = PolicyController.class.getMethod("deletePolicy", Long.class);
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertThat(pa).isNotNull();
        assertThat(pa.value()).contains("ADMIN").contains("OPERATIONS");
    }
}
