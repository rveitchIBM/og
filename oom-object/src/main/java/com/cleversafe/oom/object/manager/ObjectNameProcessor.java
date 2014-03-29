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
// Date: Mar 29, 2014
// ---------------------

package com.cleversafe.oom.object.manager;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URL;
import java.util.List;
import java.util.Map;

import com.cleversafe.oom.api.Consumer;
import com.cleversafe.oom.api.Producer;
import com.cleversafe.oom.object.LegacyObjectName;
import com.cleversafe.oom.object.ObjectName;
import com.cleversafe.oom.operation.Request;
import com.cleversafe.oom.operation.RequestContext;
import com.cleversafe.oom.operation.Response;
import com.google.common.base.Splitter;

public class ObjectNameProcessor implements Producer<ObjectName>, Consumer<Response>
{
   private final ObjectManager objectManager;
   private final Map<Long, Request> pendingRequests;
   private static final Splitter urlSplitter = Splitter.on("/");

   public ObjectNameProcessor(
         final ObjectManager objectManager,
         final Map<Long, Request> pendingRequests)
   {
      this.objectManager = checkNotNull(objectManager, "objectManager must not be null");
      this.pendingRequests = checkNotNull(pendingRequests, "pendingRequests must not be null");
   }

   @Override
   public ObjectName produce(final RequestContext context)
   {
      try
      {
         switch (context.getMethod())
         {
            case GET :
               return this.objectManager.acquireNameForRead();
            case DELETE :
               return this.objectManager.getNameForDelete();
            default :
               throw new RuntimeException(String.format("http method unsupported [%s]",
                     context.getMethod()));
         }
      }
      catch (final ObjectManagerException e)
      {
         // TODO ObjectManager should not throw checked exceptions?
         return null;
      }
   }

   @Override
   public void consume(final Response response)
   {
      // must be non-null
      final Request request = this.pendingRequests.get(response.getRequestId());
      // TODO metadata constants?
      final String responseObjectName = response.getMetaDataEntry("object_name");
      // TODO move processing for SOH write object name response somewhere else
      if (responseObjectName != null)
      {
         // SOH writes
         try
         {
            // TODO fix ObjectManager interface to take strings?
            this.objectManager.writeNameComplete(LegacyObjectName.forBytes(responseObjectName.getBytes()));
         }
         catch (final ObjectManagerException e)
         {
            // TODO again, ObjectManagerException is annoying and no reasonable recovery from this
         }
      }
      else
      {
         final ObjectName objectName = objectNameFromURL(request.getURL());
         if (objectName != null)
         {
            switch (request.getMethod())
            {
               case GET :
                  this.objectManager.releaseNameFromRead(objectName);
                  break;
               default :
                  throw new RuntimeException(String.format("http method unsupported [%s]",
                        request.getMethod()));
            }
         }
      }
   }

   private static ObjectName objectNameFromURL(final URL url)
   {
      final List<String> parts = urlSplitter.splitToList(url.getPath());
      if (parts.size() == 2)
         return LegacyObjectName.forBytes(parts.get(1).getBytes());
      return null;
   }
}
