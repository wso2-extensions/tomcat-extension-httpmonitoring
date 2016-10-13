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

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

import java.io.IOException;
import java.util.Map;

/**
 * Data publisher interface. Implement this to provide custom implementation of data publishing
 */
public interface DataPublisher {

    /**
     *
     * @param request request reached to tomcat.
     * @param response response generated for client.
     * @param additionalData data to be published which cannot retrieved from request or response object.
     * @throws IOException can be thrown IO exception while publishing data.
     */
    void publish(Request request, Response response, Map additionalData) throws IOException;
}
