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
package org.wso2.appserver.monitoring.utils;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.commons.lang3.StringUtils;
import org.wso2.appserver.monitoring.Constants;
import org.wso2.appserver.monitoring.exceptions.StatPublisherException;
import org.wso2.carbon.databridge.commons.Event;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.http.HttpSession;

/**
 * Utility class to create an Event to be published by the DataPublisher.
 *
 * @since 1.0.0
 */
public class EventBuilder {

    private static Map<String, String> arbitraryData;

    /**
     * Creates an Event to be published by the DataPublisher.
     *
     * @param streamId     unique ID of the event stream definition deployed in DAS
     * @param request      the Request object of client
     * @param response     the Response object of client
     * @param startTime    the time at which the valve is invoked
     * @param responseTime the time that is taken for the client to receive a response
     * @return an Event object populated with data to be published
     * @throws StatPublisherException
     */
    public static Event buildEvent(String streamId, Request request, Response response, long startTime,
            long responseTime) throws StatPublisherException {
        List<Object> payload = buildPayloadData(request, response, startTime, responseTime);

        return new Event(streamId, startTime,
                new ArrayList<>(Arrays.asList(request.getServerName(), request.getLocalName())).toArray(), null,
                payload.toArray(), getArbitraryData());
    }

    /**
     * Returns the arbitrary data map.
     * If the map is null, reads the system variables, populates the map and returns it.
     *
     * @return arbitrary data map
     */
    private static Map<String, String> getArbitraryData() {
        if (arbitraryData == null) {
            readArbitraryData(Constants.ARBITRARY_FIELD_PREFIX);
        }
        return arbitraryData;
    }

    /**
     * Reads the system variables with the defined prefix and populates the arbitrary data map.
     */
    private static void readArbitraryData(String prefix) {
        arbitraryData = System.getenv().entrySet()
                .stream()
                .filter(varName -> varName.getKey().startsWith(prefix))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Creates the payload.
     *
     * @param request      the Request object of client
     * @param response     the Response object of client
     * @param startTime    the time at which the valve is invoked
     * @param responseTime the time that is taken for the client to receive a response
     * @return a list containing all payload data that were extracted from the request and response
     */
    private static List<Object> buildPayloadData(Request request, Response response, long startTime,
            long responseTime) {
        List<Object> payload = new ArrayList<>();
        final String forwardSlash = "/";

        Optional.ofNullable(request.getRequestURI())
                .map(String::trim)
                .ifPresent(requestedURI -> {
                    String[] requestedUriParts = requestedURI.split(forwardSlash);
                    if (!forwardSlash.equals(requestedURI)) {
                        payload.add((requestedUriParts[1]));
                    } else {
                        payload.add((forwardSlash));
                    }
                });

        String webappServletVersion = request.getContext().getEffectiveMajorVersion() + "." +
                request.getContext().getEffectiveMinorVersion();

        payload.add(webappServletVersion);
        payload.add(extractUsername(request));
        payload.add(request.getRequestURI());
        payload.add(startTime);
        payload.add(request.getPathInfo());
        payload.add(Constants.APP_TYPE);
        payload.add(request.getContext().getDisplayName());
        payload.add(extractSessionId(request));
        payload.add(request.getMethod());
        payload.add(request.getContentType());
        payload.add(response.getContentType());
        payload.add((long) response.getStatus());
        payload.add(getClientIpAddress(request));
        payload.add(request.getHeader(Constants.REFERRER));
        payload.add(request.getHeader(Constants.USER_AGENT));
        payload.add(request.getHeader(Constants.HOST));
        payload.add(request.getRemoteUser());
        payload.add(request.getAuthType());
        payload.add(responseTime);
        payload.add((long) request.getContentLength());
        payload.add((long) response.getContentLength());
        payload.add(getRequestHeaders(request));
        payload.add(getResponseHeaders(response));
        payload.add(request.getLocale().getLanguage());

        return payload;
    }

    /**
     * Gets all request headers and their corresponding values.
     *
     * @param request the Request object of client
     * @return a {@link String} containing all request headers and their values
     */
    private static String getRequestHeaders(Request request) {
        List<String> requestHeaders = new ArrayList<>();
        Collections.list(request.getHeaderNames())
                .forEach(header -> {
                    List<String> values = new ArrayList<>();
                    values.add(request.getHeader(header));
                    String tmpString = "(" + StringUtils.join(values, ",") + ")";
                    requestHeaders.add(header + ":" + tmpString);
                });
        return StringUtils.join(requestHeaders, ";");
    }

    /**
     * Gets all response headers and their corresponding values.
     *
     * @param response the Response object of client
     * @return a {@link String} containing all response headers and their values
     */
    private static String getResponseHeaders(Response response) {
        List<String> responseHeaders = new ArrayList<>();
        response.getHeaderNames()
                .forEach(header -> {
                    List<String> values = new ArrayList<>();
                    values.add(response.getHeader(header));
                    String tmpString = "(" + StringUtils.join(values, ",") + ")";
                    responseHeaders.add(header + ":" + tmpString);
                });
        return StringUtils.join(responseHeaders, ",");
    }

    /**
     * Extracts the session ID of the current session associated with the request.
     *
     * @param request the Request object of client
     * @return the session ID of client
     */
    private static String extractSessionId(Request request) {
        HttpSession session = request.getSession(false);
        //  CXF web services does not have a session id, because they are stateless
        return (session != null && session.getId() != null) ? session.getId() : "-";
    }

    /**
     * Extracts the name of the current authenticated user for the request.
     *
     * @param request the Request object of client
     * @return the username of the current authenticated user
     */
    private static String extractUsername(Request request) {
        String consumerName;
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            consumerName = principal.getName();
        } else {
            consumerName = Constants.ANONYMOUS_USER;
        }
        return consumerName;
    }

    /**
     * Gets the original client IP address.
     *
     * @param request the Request object of client
     * @return the original IP address of the client
     */
    private static String getClientIpAddress(Request request) {
        List<String> headers = Arrays.
                asList(Constants.X_FORWARDED_FOR, Constants.PROXY_CLIENT_IP, Constants.WL_PROXY_CLIENT_IP,
                        Constants.HTTP_CLIENT_IP, Constants.HTTP_X_FORWARDED_FOR);

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && ip.length() != 0 && !Constants.UNKNOWN.equalsIgnoreCase(ip)) {
                return ip;
            } else {
                return request.getRemoteAddr();
            }
        }

        return request.getRemoteAddr();
    }
}
