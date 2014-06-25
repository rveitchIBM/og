//
// Copyright (C) 2005-2011 Cleversafe, Inc. All rights reserved.
//
// Contact Information:
// Cleversafe, Inc.
// 222 South Riverside Plaza
// Suite 1700
// Chicago, IL 60606, USA
//
// licensing@cleversafe.com
//
// END-OF-HEADER
//
// -----------------------
// @author: rveitch
//
// Date: Jun 5, 2014
// ---------------------

package com.cleversafe.og.client;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpRequestExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cleversafe.og.http.HttpResponse;
import com.cleversafe.og.http.auth.HttpAuth;
import com.cleversafe.og.operation.EntityType;
import com.cleversafe.og.operation.MetaDataConstants;
import com.cleversafe.og.operation.Request;
import com.cleversafe.og.operation.Response;
import com.cleversafe.og.util.Entities;
import com.cleversafe.og.util.Pair;
import com.cleversafe.og.util.Version;
import com.cleversafe.og.util.consumer.ByteBufferConsumer;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.LongSerializationPolicy;

public class ApacheClient implements Client
{
   private static Logger _logger = LoggerFactory.getLogger(ApacheClient.class);
   private static Logger _requestLogger = LoggerFactory.getLogger("RequestLogger");
   private final CloseableHttpClient client;
   private final Function<String, ByteBufferConsumer> byteBufferConsumers;
   private final ListeningExecutorService executorService;
   private final Gson gson;
   private final HttpAuth auth;
   private final boolean chunkedEncoding;

   private ApacheClient(
         final HttpAuth auth,
         final int connectTimeout,
         final int soTimeout,
         final boolean soReuseAddress,
         final int soLinger,
         final boolean soKeepAlive,
         final boolean tcpNoDelay,
         final boolean chunkedEncoding,
         final boolean expectContinue,
         final int waitForContinue,
         final Function<String, ByteBufferConsumer> byteBufferConsumers)
   {
      this.auth = auth; // optional
      checkArgument(connectTimeout >= 0, "connectTimeout must be >= 0 [%s]", connectTimeout);
      checkArgument(soTimeout >= 0, "soTimeout must be >= 0 [%s]", soTimeout);
      checkArgument(soLinger >= -1, "soLinger must be >= -1 [%s]", soLinger);
      checkArgument(waitForContinue >= 0, "waitForContinue must be >= 0 [%s]", waitForContinue);
      this.byteBufferConsumers = checkNotNull(byteBufferConsumers);

      this.client = HttpClients.custom()
            // TODO HTTPS: setHostnameVerifier, setSslcontext, and SetSSLSocketFactory methods
            // TODO investigate ConnectionConfig, particularly bufferSize and fragmentSizeHint
            // TODO defaultCredentialsProvider and defaultAuthSchemeRegistry for pre/passive auth?
            .setUserAgent(Version.displayVersion())
            .setRequestExecutor(new HttpRequestExecutor(waitForContinue))
            .setMaxConnTotal(Integer.MAX_VALUE)
            .setMaxConnPerRoute(Integer.MAX_VALUE)
            .setDefaultSocketConfig(SocketConfig.custom()
                  .setSoTimeout(soTimeout)
                  .setSoReuseAddress(soReuseAddress)
                  .setSoLinger(soLinger)
                  .setSoKeepAlive(soKeepAlive)
                  .setTcpNoDelay(tcpNoDelay)
                  .build())
            .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE)
            .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
            .disableConnectionState()
            .disableCookieManagement()
            .disableContentCompression()
            .disableAuthCaching()
            .disableAutomaticRetries()
            // TODO need to implement a redirectStrategy that will redirect PUT and POST
            .setRedirectStrategy(new LaxRedirectStrategy())
            .setDefaultRequestConfig(RequestConfig.custom()
                  .setExpectContinueEnabled(expectContinue)
                  // TODO investigate performance impact of stale check (30ms reported)
                  .setStaleConnectionCheckEnabled(true)
                  .setRedirectsEnabled(true)
                  .setRelativeRedirectsAllowed(true)
                  .setConnectTimeout(connectTimeout)
                  .setSocketTimeout(soTimeout)
                  // TODO should this be infinite? length of time allowed to request a connection
                  // from the pool
                  .setConnectionRequestTimeout(0)
                  .build())
            .build();

      this.executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
      this.gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setLongSerializationPolicy(LongSerializationPolicy.STRING)
            .create();
      this.chunkedEncoding = chunkedEncoding;
   }

   @Override
   public ListenableFuture<Response> execute(final Request request)
   {
      checkNotNull(request);
      final ByteBufferConsumer consumer =
            this.byteBufferConsumers.apply(request.getMetaDataEntry(MetaDataConstants.RESPONSE_BODY_PROCESSOR.toString()));
      return this.executorService.submit(new BlockingHttpOperation(this.client, this.auth, request,
            consumer, this.gson, this.chunkedEncoding));
   }

   @Override
   public ListenableFuture<Boolean> shutdown(final boolean graceful)
   {
      final SettableFuture<Boolean> future = SettableFuture.create();
      if (!graceful)
      {
         this.executorService.shutdownNow();
         future.set(true);
      }
      // TODO implement graceful shutdown
      return future;
   }

   private static class BlockingHttpOperation implements Callable<Response>
   {
      private final CloseableHttpClient client;
      private final HttpAuth auth;
      private final Request request;
      private final ByteBufferConsumer consumer;

      private final byte[] buf;
      private final ByteBuffer byteBuf;
      final Gson gson;
      private static final Joiner joiner = Joiner.on(',').skipNulls();
      private final boolean chunkedEncoding;

      public BlockingHttpOperation(
            final CloseableHttpClient client,
            final HttpAuth auth,
            final Request request,
            final ByteBufferConsumer consumer,
            final Gson gson,
            final boolean chunkedEncoding)
      {
         this.client = client;
         this.auth = auth;
         this.request = request;
         this.consumer = consumer;
         // TODO inject buf size from config
         this.buf = new byte[4096];
         this.byteBuf = ByteBuffer.allocate(4096);
         this.gson = gson;
         this.chunkedEncoding = chunkedEncoding;
      }

      @Override
      public Response call()
      {
         final long timestampStart = System.currentTimeMillis();
         final RequestBuilder requestBuilder =
               RequestBuilder.create(this.request.getMethod().toString());
         setRequestURI(requestBuilder);
         setRequestHeaders(requestBuilder);
         setRequestContent(requestBuilder);
         Response response;
         try
         {
            response = sendRequest(requestBuilder.build());
         }
         catch (final Exception e)
         {
            _logger.error("Exception executing request:", e);
            response = HttpResponse.custom()
                  .withRequestId(this.request.getId())
                  .withMetaDataEntry("exception", "1")
                  .build();
         }
         final long timestampFinish = System.currentTimeMillis();

         _requestLogger.info(this.gson.toJson(new RequestLogEntry(this.request, response,
               timestampStart, timestampFinish)));

         return response;
      }

      private void setRequestURI(final RequestBuilder requestBuilder)
      {
         requestBuilder.setUri(this.request.getURI());
      }

      private void setRequestHeaders(final RequestBuilder requestBuilder)
      {
         setAuthHeader(requestBuilder);
         final Iterator<Entry<String, String>> headers = this.request.headers();
         while (headers.hasNext())
         {
            final Entry<String, String> header = headers.next();
            requestBuilder.addHeader(header.getKey(), header.getValue());
         }
      }

      private void setAuthHeader(final RequestBuilder requestBuilder)
      {
         if (this.auth != null)
         {
            final Pair<String, String> authHeader = this.auth.nextAuthorizationHeader(this.request);
            requestBuilder.addHeader(authHeader.getKey(), authHeader.getValue());
         }

      }

      private void setRequestContent(final RequestBuilder requestBuilder)
      {
         if (EntityType.NONE != this.request.getEntity().getType())
            requestBuilder.setEntity(createEntity());
      }

      private HttpEntity createEntity()
      {
         // TODO verify httpclient consumes request entity correctly automatically
         // TODO may need to implement a custom HttpEntity that returns false for isStreaming call,
         // if this makes a performance difference
         final InputStream stream = Entities.createInputStream(this.request.getEntity());
         final InputStreamEntity entity =
               new InputStreamEntity(stream, this.request.getEntity().getSize());
         // TODO chunk size for chunked encoding is hardcoded to 2048 bytes. Can only be overridden
         // by implementing a custom connection factory
         entity.setChunked(this.chunkedEncoding);
         return entity;
      }

      private Response sendRequest(final HttpUriRequest _request)
            throws ClientProtocolException, IOException
      {
         return this.client.execute(_request, new ResponseHandler<Response>()
         {
            @Override
            public Response handleResponse(final org.apache.http.HttpResponse response)
                  throws ClientProtocolException, IOException
            {
               final HttpResponse.Builder responseBuilder = HttpResponse.custom()
                     .withRequestId(BlockingHttpOperation.this.request.getId());
               setResponseStatusCode(responseBuilder, response);
               setResponseHeaders(responseBuilder, response);
               receiveResponseContent(responseBuilder, response);
               return responseBuilder.build();
            }
         });
      }

      private void setResponseStatusCode(
            final HttpResponse.Builder responseBuilder,
            final org.apache.http.HttpResponse response)
      {
         responseBuilder.withStatusCode(response.getStatusLine().getStatusCode());
      }

      private void setResponseHeaders(
            final HttpResponse.Builder responseBuilder,
            final org.apache.http.HttpResponse response)
      {
         final HeaderIterator headers = response.headerIterator();
         while (headers.hasNext())
         {
            final Header header = headers.nextHeader();
            // TODO header value may be null, is this acceptable?
            responseBuilder.withHeader(header.getName(), header.getValue());
         }
      }

      // TODO handle IllegalStateException via logging, etc
      private void receiveResponseContent(
            final HttpResponse.Builder responseBuilder,
            final org.apache.http.HttpResponse response) throws IllegalStateException, IOException
      {
         final HttpEntity entity = response.getEntity();
         if (entity != null)
            receiveBytes(responseBuilder, entity.getContent());
      }

      private void receiveBytes(
            final HttpResponse.Builder responseBuilder,
            final InputStream responseContent) throws IOException
      {
         int bytesRead;
         while ((bytesRead = responseContent.read(this.buf)) > 0)
         {
            processReceivedBytes(bytesRead);
         }
         final Iterator<Entry<String, String>> it = this.consumer.metaData();
         while (it.hasNext())
         {
            final Entry<String, String> e = it.next();
            responseBuilder.withMetaDataEntry(e.getKey(), e.getValue());
         }
      }

      private void processReceivedBytes(final int bytesRead)
      {
         this.byteBuf.put(this.buf, 0, bytesRead);
         this.byteBuf.flip();
         this.consumer.consume(this.byteBuf);
         this.byteBuf.clear();
      }
   }

   public static Builder custom()
   {
      return new Builder();
   }

   public static class Builder
   {
      private HttpAuth auth;
      private int connectTimeout;
      private int soTimeout;
      private boolean soReuseAddress;
      private int soLinger;
      private boolean soKeepAlive;
      private boolean tcpNoDelay;
      private boolean chunkedEncoding;
      private boolean expectContinue;
      private int waitForContinue;
      private Function<String, ByteBufferConsumer> byteBufferConsumers;

      private Builder()
      {}

      public Builder withAuth(final HttpAuth auth)
      {
         this.auth = auth;
         return this;
      }

      public Builder withConnectTimeout(final int connectTimeout)
      {
         this.connectTimeout = connectTimeout;
         return this;
      }

      public Builder withSoTimeout(final int soTimeout)
      {
         this.soTimeout = soTimeout;
         return this;
      }

      public Builder usingSoReuseAddress(final boolean soReuseAddress)
      {
         this.soReuseAddress = soReuseAddress;
         return this;
      }

      public Builder withSoLinger(final int soLinger)
      {
         this.soLinger = soLinger;
         return this;
      }

      public Builder usingSoKeepAlive(final boolean soKeepAlive)
      {
         this.soKeepAlive = soKeepAlive;
         return this;
      }

      public Builder usingTcpNoDelay(final boolean tcpNoDelay)
      {
         this.tcpNoDelay = tcpNoDelay;
         return this;
      }

      public Builder usingChunkedEncoding(final boolean chunkedEncoding)
      {
         this.chunkedEncoding = chunkedEncoding;
         return this;
      }

      public Builder usingExpectContinue(final boolean expectContinue)
      {
         this.expectContinue = expectContinue;
         return this;
      }

      public Builder withWaitForContinue(final int waitForContinue)
      {
         this.waitForContinue = waitForContinue;
         return this;
      }

      public Builder withByteBufferConsumers(
            final Function<String, ByteBufferConsumer> byteBufferConsumers)
      {
         this.byteBufferConsumers = byteBufferConsumers;
         return this;
      }

      public ApacheClient build()
      {
         return new ApacheClient(this.auth, this.connectTimeout, this.soTimeout,
               this.soReuseAddress, this.soLinger, this.soKeepAlive, this.tcpNoDelay,
               this.chunkedEncoding, this.expectContinue, this.waitForContinue,
               this.byteBufferConsumers);
      }
   }
}
