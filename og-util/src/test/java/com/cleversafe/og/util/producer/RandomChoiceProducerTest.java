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
// Date: Jun 28, 2014
// ---------------------

package com.cleversafe.og.util.producer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomChoiceProducerTest
{
   private static Logger _logger = LoggerFactory.getLogger(RandomChoiceProducerTest.class);

   @Test(expected = IllegalArgumentException.class)
   public void testNoChoice()
   {
      RandomChoiceProducer.custom().build();
   }

   @Test(expected = NullPointerException.class)
   public void testNullChoice()
   {
      RandomChoiceProducer.<Integer> custom().withChoice(null).build();
   }

   @Test(expected = NullPointerException.class)
   public void testNullRandom()
   {
      RandomChoiceProducer.<Integer> custom().withChoice(1).withRandom(null).build();
   }

   @Test(expected = IllegalArgumentException.class)
   public void testNegativeWeight()
   {
      RandomChoiceProducer.<Integer> custom().withChoice(1, -1.0).build();
   }

   @Test(expected = IllegalArgumentException.class)
   public void testZeroWeight()
   {
      RandomChoiceProducer.<Integer> custom().withChoice(1, 0.0).build();
   }

   @Test
   public void testOneChoice()
   {
      final Producer<Integer> p = RandomChoiceProducer.<Integer> custom().withChoice(1).build();
      for (int i = 0; i < 10; i++)
      {
         Assert.assertEquals(Integer.valueOf(1), p.produce());
      }
   }

   @Test
   public void testNChoices()
   {
      final RandomChoiceProducer.Builder<Integer> b = RandomChoiceProducer.custom();
      b.withChoice(1, 33);
      b.withChoice(2, Producers.of(33.5));
      b.withChoice(3, Producers.of(33));
      b.withRandom(new Random());
      final Producer<Integer> p = b.build();

      final Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
      counts.put(1, 0);
      counts.put(2, 0);
      counts.put(3, 0);

      for (int i = 0; i < 100; i++)
      {
         final Integer nextInt = p.produce();
         counts.put(nextInt, counts.get(nextInt) + 1);
      }

      for (final int count : counts.values())
      {
         Assert.assertTrue(count > 0);
      }
   }
}