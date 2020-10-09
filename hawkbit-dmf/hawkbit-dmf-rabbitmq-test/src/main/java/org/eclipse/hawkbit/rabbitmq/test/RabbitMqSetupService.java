/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.rabbitmq.test;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.UUID;

import javax.annotation.PreDestroy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.domain.UserPermissions;

import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.util.StringUtils;

/**
 * Creates and deletes a new virtual host if the rabbit mq management api is
 * available.
 * 
 */
// exception squid:S2068 - Test instance passwd
@SuppressWarnings("squid:S2068")
public class RabbitMqSetupService {

    private static final String GUEST = "guest";
    private static final String DEFAULT_USER = GUEST;
    private static final String DEFAULT_PASSWORD = GUEST;

    private Client rabbitmqHttpClient;

    private String virtualHost;

    private final String hostname;

    private String username;

    private String password;

    public RabbitMqSetupService(final RabbitProperties properties) {
        hostname = properties.getHost();
        username = properties.getUsername();
        if (StringUtils.isEmpty(username)) {
            username = DEFAULT_USER;
        }

        password = properties.getPassword();
        if (StringUtils.isEmpty(password)) {
            password = DEFAULT_PASSWORD;
        }

    }

    private synchronized Client getRabbitmqHttpClient() {
        if (rabbitmqHttpClient == null) {
            try {
                rabbitmqHttpClient = new Client(getHttpApiUrl(), getUsername(), getPassword());
            } catch (MalformedURLException | URISyntaxException e) {
                throw Throwables.propagate(e);
            }
        }
        return rabbitmqHttpClient;
    }

    public String getHttpApiUrl() {
        return "http://" + getHostname() + ":15672/api/";
    }

    public String createVirtualHost() {
        if (!getRabbitmqHttpClient().alivenessTest("/")) {
            throw new AlivenessException(getHostname());

        }

        try {
            virtualHost = UUID.randomUUID().toString();
            getRabbitmqHttpClient().createVhost(virtualHost);
            getRabbitmqHttpClient().updatePermissions(virtualHost, getUsername(), createUserPermissionsFullAccess());

            return virtualHost;

        } catch (final JsonProcessingException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @PreDestroy
    public void deleteVirtualHost() {
        if (StringUtils.isEmpty(virtualHost)) {
            return;
        }
        getRabbitmqHttpClient().deleteVhost(virtualHost);
    }

    public String getHostname() {
        return hostname;
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }

    private UserPermissions createUserPermissionsFullAccess() {
        final UserPermissions permissions = new UserPermissions();
        permissions.setVhost(virtualHost);
        permissions.setRead(".*");
        permissions.setConfigure(".*");
        permissions.setWrite(".*");
        return permissions;
    }

    static class AlivenessException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AlivenessException(final String hostname) {
            super("Aliveness test failed for " + hostname
                    + ":15672 guest/quest; rabbit mq management api not available");
        }
    }

}
