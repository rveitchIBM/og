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
// Date: Apr 6, 2014
// ---------------------

package com.cleversafe.oom.object.manager;

import java.util.UUID;

import com.cleversafe.oom.api.Producer;
import com.cleversafe.oom.operation.RequestContext;

public class UUIDObjectNameProducer implements Producer<String>
{
   @Override
   public String produce(final RequestContext context)
   {
      return UUID.randomUUID().toString().replace("-", "") + "0000";
   }
}