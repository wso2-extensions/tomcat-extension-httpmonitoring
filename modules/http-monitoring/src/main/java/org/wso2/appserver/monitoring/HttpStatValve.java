/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.appserver.monitoring;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.wso2.appserver.configuration.context.WebAppStatsPublishing;
import org.wso2.appserver.configuration.listeners.ContextConfigurationLoader;
import org.wso2.appserver.monitoring.exceptions.StatPublisherException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of {@code ValveBase} that publishes HTTP statistics of the requests to WSO2 Data Analytics Server.
 *
 * @since 1.0.0
 */
public class HttpStatValve extends ValveBase {
    private static final Log LOG = LogFactory.getLog(HttpStatValve.class);
    private DataPublisher dataPublisher;
    private WebAppStatsPublishing statsPublisherConfiguration;

    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        LOG.debug("The HttpStatValve initialized.");
        setTrustStorePath();
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        statsPublisherConfiguration =
                ContextConfigurationLoader.getContextConfiguration(request.getContext()).get()
                                          .getStatsPublisherConfiguration();

        try {
            dataPublisher = getDataPublisher(statsPublisherConfiguration);
        } catch (StatPublisherException e) {
            LOG.error("Initializing DataPublisher failed: ", e);
            throw new ServletException("Initializing DataPublisher failed: " + e);
        } catch (ClassNotFoundException e) {
            LOG.error("Data publisher implementation class not found: ", e);
            throw new ServletException("Data publisher implementation class not found: " + e);
        } catch (InstantiationException e) {
            LOG.error("Error occurred while instantiating Data publisher: ", e);
            throw new ServletException("Error occurred while instantiating Data publisher: " + e);
        } catch (IllegalAccessException e) {
            LOG.error("Data publisher implementation class cannot access: ", e);
            throw new ServletException("Data publisher implementation class cannot access: " + e);
        }
        Long startTime = System.currentTimeMillis();
        getNext().invoke(request, response);
        long responseTime = System.currentTimeMillis() - startTime;

        Map additionalData = new HashMap<>();
        additionalData.put("startTime", startTime);
        additionalData.put("responseTime", responseTime);

        dataPublisher.publish(request, response, additionalData);
    }

    /**
     * Instantiates a data publisher to be used to publish data.
     *
     * @return DataPublisher object initialized with configurations
     * @throws StatPublisherException
     */
    private DataPublisher getDataPublisher(WebAppStatsPublishing statsPublisherConfiguration)
            throws StatPublisherException, ClassNotFoundException, IllegalAccessException,
                   InstantiationException {
        Class statsPublisherImpl = Class.forName(statsPublisherConfiguration.getPublisherImplementation());
        return (DataPublisher) statsPublisherImpl.newInstance();
    }

    /**
     * Setting the system property for the trust store.
     */
    private void setTrustStorePath() {
        String pathToBeReplaced = System.getProperty("javax.net.ssl.trustStore");
        System.setProperty("javax.net.ssl.trustStore", pathToBeReplaced);
    }
}
