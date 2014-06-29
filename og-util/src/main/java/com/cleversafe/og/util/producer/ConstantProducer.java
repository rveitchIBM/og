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
// Date: Mar 19, 2014
// ---------------------

package com.cleversafe.og.util.producer;

import static com.google.common.base.Preconditions.checkNotNull;
public class ConstantProducer<T> implements Producer<T>
{
   private final T item;

   public ConstantProducer(final T item)
   {
      this.item = checkNotNull(item);
   }

   @Override
   public T produce()
   {
      return this.item;
   }
}
