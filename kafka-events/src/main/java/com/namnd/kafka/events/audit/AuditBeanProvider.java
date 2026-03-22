package com.namnd.kafka.events.audit;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Static holder for Spring ApplicationContext.
 * Allows JPA EntityListeners (which can't use constructor injection)
 * to access Spring beans like ApplicationEventPublisher.
 */
public class AuditBeanProvider implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        applicationContext = ctx;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
