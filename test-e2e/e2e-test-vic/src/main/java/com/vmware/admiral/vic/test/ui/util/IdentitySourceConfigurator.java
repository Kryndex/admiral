/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.vic.test.ui.util;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class IdentitySourceConfigurator {

    private final String target;
    private static final String ADD_AD_ENDPOINT = "/psc/mutation/add?propertyObjectType=com.vmware.vsphere.client.sso.admin.model.IdentitySourceLdapSpec";
    private final List<Header> authHeaders;
    private final HttpClient httpClient;

    public IdentitySourceConfigurator(String vcTarget, String username, String password) {
        this.target = vcTarget;
        authHeaders = getAuthHeaders(target, username, password);
        CookieStore cookies = new BasicCookieStore();
        httpClient = createUnsecureHttpClient(cookies, authHeaders);
    }

    public void addIdentitySource(String specBody) {
        try {
            StringEntity entity = new StringEntity(specBody);
            HttpPost post = new HttpPost(target + ADD_AD_ENDPOINT);
            post.setEntity(entity);
            HttpResponse response = httpClient.execute(post);
            EntityUtils.consume(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Could not add identity source, response code was: "
                        + response.getStatusLine().getStatusCode());
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not add identity source: ", e);
        }
    }

    private HttpClient createUnsecureHttpClient(CookieStore cookieStore,
            Collection<? extends Header> defaultHeaders) {
        return HttpClientBuilder.create().setSSLContext(newUnsecureSSLContext())
                .setSSLHostnameVerifier(allowAllHostNameVeririer())
                .setDefaultCookieStore(cookieStore)
                .setDefaultHeaders(defaultHeaders)
                .setDefaultRequestConfig(
                        RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
                .disableRedirectHandling().build();
    }

    private List<Header> getAuthHeaders(String target, String username,
            String password) {
        String loginTarget = target + "/psc/login";
        CookieStore cookies = new BasicCookieStore();
        HttpClient client = createUnsecureHttpClient(cookies, null);
        HttpUriRequest get = new HttpGet(loginTarget);
        try {
            HttpResponse response = client.execute(get);
            String location = response.getFirstHeader("Location").getValue();
            EntityUtils.consume(response.getEntity());
            get = new HttpGet(loginTarget);
            response = client.execute(get);
            EntityUtils.consume(response.getEntity());
            String upa = "Basic "
                    + Base64.encodeBase64String((username + ":" + password).getBytes());
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("CastleAuthorization", upa));
            HttpPost post = new HttpPost(location);
            post.setEntity(new UrlEncodedFormEntity(params));
            response = client.execute(post);
            Document doc = Jsoup
                    .parse(IOUtils.toString(response.getEntity().getContent(), "UTF-8"));
            EntityUtils.consume(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Acquiring token failed due to invalid credentials");
            }

            Element samlPostForm = doc.getElementById("SamlPostForm");
            String postTarget = samlPostForm.attr("action");
            String samlResponse = doc.getElementsByAttributeValue("name", "SAMLResponse").get(0)
                    .attr("value");
            params = new ArrayList<>();
            params.add(new BasicNameValuePair("SAMLResponse", samlResponse));
            params.add(new BasicNameValuePair("RelayState", "SessionId"));
            post = new HttpPost(postTarget);
            post.setEntity(new UrlEncodedFormEntity(params));
            response = client.execute(post);
            EntityUtils.consume(response.getEntity());

            get = new HttpGet(target + "/psc/");
            response = client.execute(get);
            EntityUtils.consume(response.getEntity());

            String jSessionId = null;
            String xsrfToken = null;
            for (Cookie c : cookies.getCookies()) {
                if (c.getName().equalsIgnoreCase("JSESSIONID")) {
                    jSessionId = c.getValue();
                } else if (c.getName().equalsIgnoreCase("XSRF-TOKEN")) {
                    xsrfToken = c.getValue();
                }
            }
            if (Objects.isNull(jSessionId)) {
                throw new RuntimeException("Could not get session cookie");
            }
            if (Objects.isNull(xsrfToken)) {
                throw new RuntimeException("Could not get token header");
            }
            List<Header> headers = new ArrayList<>();
            headers.add(new BasicHeader("X-XSRF-TOKEN", xsrfToken));
            headers.add(new BasicHeader("Cookie", "JSESSIONID=" + jSessionId));
            return headers;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static SSLContext newUnsecureSSLContext() {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, new TrustManager[] { new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }
            } }, new java.security.SecureRandom());
            return sslcontext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Cannot create SSL Context.");
        }
    }

    private static HostnameVerifier allowAllHostNameVeririer() {
        return new HostnameVerifier() {

            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
    }

}
