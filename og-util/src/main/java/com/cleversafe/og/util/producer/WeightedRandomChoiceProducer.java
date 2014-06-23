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

import com.cleversafe.og.api.Producer;
import com.cleversafe.og.util.WeightedRandomChoice;

public class WeightedRandomChoiceProducer<T> implements Producer<T>
{
   private final WeightedRandomChoice<T> wrc;

   private WeightedRandomChoiceProducer(final WeightedRandomChoice<T> wrc)
   {
      this.wrc = checkNotNull(wrc, "wrc must not be null");
   }

   public static <K> WeightedRandomChoiceProducer<K> of(final WeightedRandomChoice<K> item)
   {
      return new WeightedRandomChoiceProducer<K>(item);
   }

   @Override
   public T produce()
   {
      return this.wrc.nextChoice();
   }
}