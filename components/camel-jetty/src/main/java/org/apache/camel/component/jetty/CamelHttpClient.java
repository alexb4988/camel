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
package org.apache.camel.component.jetty;

import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;

import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;

public class CamelHttpClient extends HttpClient {
    
    private boolean supportRedirect;
    
    public CamelHttpClient() {
        super();
        setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
    }
    
    public CamelHttpClient(SslContextFactory sslContextFactory) {
        super(sslContextFactory);
        setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
    }
    
    @Deprecated
    /**
     * It does nothing here, please setup SslContextFactory directly, it will be removed in Camel 2.16.0
     * @param context
     */
    public void setSSLContext(SSLContext context) {
        // do nothing here, please setup SslContextFactory directly.
    }
    
    @Override
    protected void doStart() throws Exception {
        if (getThreadPool() == null) {
            // if there is no thread pool then create a default thread pool using daemon threads
            QueuedThreadPool qtp = new QueuedThreadPool();
            // 16 max threads is the default in the http client
            qtp.setMaxThreads(16);
            qtp.setDaemon(true);
            // let the thread names indicate they are from the client
            qtp.setName("CamelJettyClient(" + ObjectHelper.getIdentityHashCode(this) + ")");
            setThreadPool(qtp);
        }
        if (isSupportRedirect()) {
            // setup the listener for it
            this.registerListener(CamelRedirectListener.class.getName());
        }
        super.doStart();
    }

    void setThreadPoolOrExecutor(Executor pool) {
        try {
            getClass().getMethod("setExecutor", Executor.class).invoke(this, pool);
        } catch (Exception ex) {
            try {
                getClass().getMethod("setThreadPool", ThreadPool.class).invoke(this, pool);
            } catch (Exception ex2) {
                throw new RuntimeException(ex);
            }
        }
    }
    
    public void setProxy(String host, int port) {
        setProxy(new org.eclipse.jetty.client.Address(host, port));        
    }
    
    public boolean isSupportRedirect() {
        return supportRedirect;
    }

    public void setSupportRedirect(boolean supportRedirect) {
        this.supportRedirect = supportRedirect;
    }

}
