/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.commands;

import java.io.PrintStream;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.util.JsonSchemaHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * List the Camel endpoints available in the JVM.
 */
public class EndpointListCommand extends AbstractCamelCommand {

    private static final String CONTEXT_COLUMN_LABEL = "Context";
    private static final String URI_COLUMN_LABEL = "Uri";
    private static final String STATUS_COLUMN_LABEL = "Status";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    // endpoint uris can be very long so clip by default after 120 chars
    private static final int MAX_COLUMN_WIDTH = 120;
    private static final int MIN_COLUMN_WIDTH = 12;

    String name;
    boolean decode = true;
    boolean verbose;
    boolean explain;

    public EndpointListCommand(String name, boolean decode, boolean verbose, boolean explain) {
        this.name = name;
        this.decode = decode;
        this.verbose = verbose;
        this.explain = explain;
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        List<Endpoint> endpoints = camelController.getEndpoints(name);

        final Map<String, Integer> columnWidths = computeColumnWidths(endpoints);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);

        if (endpoints.size() > 0) {
            out.println(String.format(headerFormat, CONTEXT_COLUMN_LABEL, URI_COLUMN_LABEL, STATUS_COLUMN_LABEL));
            out.println(String.format(headerFormat, "-------", "---", "------"));
            for (final Endpoint endpoint : endpoints) {
                String contextId = endpoint.getCamelContext().getName();
                String uri = endpoint.getEndpointUri();
                if (decode) {
                    // decode uri so its more human readable
                    uri = URLDecoder.decode(uri, "UTF-8");
                }
                // sanitize and mask uri so we dont see passwords
                uri = URISupport.sanitizeUri(uri);
                String state = getEndpointState(endpoint);
                out.println(String.format(rowFormat, contextId, uri, state));

                if (explain) {
                    boolean first = true;
                    String json = camelController.explainEndpoint(endpoint.getCamelContext().getName(), endpoint.getEndpointUri(), verbose);
                    // use a basic json parser
                    List<Map<String, String>> options = JsonSchemaHelper.parseJsonSchema("properties", json, true);

                    // lets sort the options by name
                    Collections.sort(options, new Comparator<Map<String, String>>() {
                        @Override
                        public int compare(Map<String, String> o1, Map<String, String> o2) {
                            // sort by kind first (need to -1 as we want path on top), then name
                            int answer = -1 * o1.get("kind").compareTo(o2.get("kind"));
                            if (answer == 0) {
                                answer = o1.get("name").compareTo(o2.get("name"));
                            }
                            return answer;
                        }
                    });

                    for (Map<String, String> option : options) {
                        String key = option.get("name");
                        String kind = option.get("kind");
                        String type = option.get("type");
                        String javaType = option.get("javaType");
                        String value = option.get("value");
                        if (ObjectHelper.isEmpty(value)) {
                            value = option.get("defaultValue");
                        }
                        String desc = option.get("description");
                        if (key != null && value != null) {
                            if (first) {
                                out.println();
                                first = false;
                            }
                            String line;
                            if ("path".equals(kind)) {
                                line = "\t" + key + " (endpoint path) = " + value;
                            } else {
                                line = "\t" + key + " = " + value;
                            }
                            out.println(String.format(rowFormat, "", line, ""));

                            if (type != null) {
                                String displayType = type;
                                if (javaType != null && !displayType.equals(javaType)) {
                                    displayType = type + " (" + javaType + ")";
                                }
                                out.println(String.format(rowFormat, "", "\t" + displayType, ""));
                            }
                            if (desc != null) {
                                // TODO: split desc in multi lines so it does not overflow
                                out.println(String.format(rowFormat, "", "\t" + desc, ""));
                            }
                            out.println();
                        }
                    }
                }
            }
        }

        return null;
    }

    private Map<String, Integer> computeColumnWidths(final Iterable<Endpoint> endpoints) throws Exception {
        if (endpoints == null) {
            throw new IllegalArgumentException("Unable to determine column widths from null Iterable<Endpoint>");
        } else {
            int maxContextLen = 0;
            int maxUriLen = 0;
            int maxStatusLen = 0;

            for (final Endpoint endpoint : endpoints) {
                final String name = endpoint.getCamelContext().getName();
                maxContextLen = java.lang.Math.max(maxContextLen, name == null ? 0 : name.length());

                String uri = endpoint.getEndpointUri();
                if (decode) {
                    // decode uri so its more human readable
                    uri = URLDecoder.decode(uri, "UTF-8");
                }
                // sanitize and mask uri so we dont see passwords
                uri = URISupport.sanitizeUri(uri);

                maxUriLen = java.lang.Math.max(maxUriLen, uri == null ? 0 : uri.length());

                final String status = getEndpointState(endpoint);
                maxStatusLen = java.lang.Math.max(maxStatusLen, status == null ? 0 : status.length());
            }

            final Map<String, Integer> retval = new Hashtable<String, Integer>(3);
            retval.put(CONTEXT_COLUMN_LABEL, maxContextLen);
            retval.put(URI_COLUMN_LABEL, maxUriLen);
            retval.put(STATUS_COLUMN_LABEL, maxStatusLen);

            return retval;
        }
    }

    private String buildFormatString(final Map<String, Integer> columnWidths, final boolean isHeader) {
        final String fieldPreamble;
        final String fieldPostamble;
        final int columnWidthIncrement;

        if (isHeader) {
            fieldPreamble = DEFAULT_HEADER_PREAMBLE;
            fieldPostamble = DEFAULT_HEADER_POSTAMBLE;
        } else {
            fieldPreamble = DEFAULT_FIELD_PREAMBLE;
            fieldPostamble = DEFAULT_FIELD_POSTAMBLE;
        }
        columnWidthIncrement = DEFAULT_COLUMN_WIDTH_INCREMENT;

        int contextLen = java.lang.Math.min(columnWidths.get(CONTEXT_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        int uriLen = java.lang.Math.min(columnWidths.get(URI_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        int statusLen = java.lang.Math.min(columnWidths.get(STATUS_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        contextLen = Math.max(MIN_COLUMN_WIDTH, contextLen);
        uriLen = Math.max(MIN_COLUMN_WIDTH, uriLen);
        // last row does not have min width

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        retval.append(fieldPreamble).append("%-").append(contextLen).append('.').append(contextLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(uriLen).append('.').append(uriLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(statusLen).append('.').append(statusLen).append('s').append(fieldPostamble).append(' ');

        return retval.toString();
    }

    private int getMaxColumnWidth() {
        if (verbose) {
            return Integer.MAX_VALUE;
        } else {
            return MAX_COLUMN_WIDTH;
        }
    }

    private static String getEndpointState(Endpoint endpoint) {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (endpoint instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) endpoint).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }
}
