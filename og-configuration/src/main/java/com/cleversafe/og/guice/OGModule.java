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
// Date: Mar 27, 2014
// ---------------------

package com.cleversafe.og.guice;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.cleversafe.og.consumer.ObjectNameConsumer;
import com.cleversafe.og.consumer.ReadObjectNameConsumer;
import com.cleversafe.og.consumer.WriteObjectNameConsumer;
import com.cleversafe.og.guice.annotation.Delete;
import com.cleversafe.og.guice.annotation.DeleteObjectName;
import com.cleversafe.og.guice.annotation.DeleteWeight;
import com.cleversafe.og.guice.annotation.ObjectFileLocation;
import com.cleversafe.og.guice.annotation.ObjectFileName;
import com.cleversafe.og.guice.annotation.Read;
import com.cleversafe.og.guice.annotation.ReadObjectName;
import com.cleversafe.og.guice.annotation.ReadWeight;
import com.cleversafe.og.guice.annotation.Write;
import com.cleversafe.og.guice.annotation.WriteObjectName;
import com.cleversafe.og.guice.annotation.WriteWeight;
import com.cleversafe.og.http.Api;
import com.cleversafe.og.http.HttpUtil;
import com.cleversafe.og.json.StoppingConditionsConfig;
import com.cleversafe.og.object.ObjectManager;
import com.cleversafe.og.object.RandomObjectPopulator;
import com.cleversafe.og.operation.OperationManager;
import com.cleversafe.og.operation.Request;
import com.cleversafe.og.producer.CachingProducer;
import com.cleversafe.og.producer.DeleteObjectNameProducer;
import com.cleversafe.og.producer.Producer;
import com.cleversafe.og.producer.RandomChoiceProducer;
import com.cleversafe.og.producer.ReadObjectNameProducer;
import com.cleversafe.og.producer.UUIDObjectNameProducer;
import com.cleversafe.og.statistic.Counter;
import com.cleversafe.og.statistic.Statistics;
import com.cleversafe.og.test.LoadTest;
import com.cleversafe.og.test.condition.CounterCondition;
import com.cleversafe.og.test.condition.RuntimeCondition;
import com.cleversafe.og.test.condition.StatusCodeCondition;
import com.cleversafe.og.test.condition.TestCondition;
import com.cleversafe.og.test.operation.manager.SimpleOperationManager;
import com.cleversafe.og.util.Operation;
import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.google.common.math.DoubleMath;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class OGModule extends AbstractModule
{
   private static final double ERR = Math.pow(0.1, 6);

   @Override
   protected void configure()
   {
      bind(OperationManager.class).to(SimpleOperationManager.class).in(Singleton.class);
      bind(EventBus.class).in(Singleton.class);
      bind(LoadTest.class).in(Singleton.class);
   }

   @Provides
   @Singleton
   public Statistics provideStatistics(final EventBus eventBus)
   {
      checkNotNull(eventBus);
      final Statistics stats = new Statistics();
      eventBus.register(stats);
      return stats;
   }

   @Provides
   @Singleton
   public List<TestCondition> provideTestConditions(
         final LoadTest test,
         final EventBus eventBus,
         final Statistics stats,
         final StoppingConditionsConfig config)
   {
      checkNotNull(test);
      checkNotNull(eventBus);
      checkNotNull(stats);
      checkNotNull(config);

      final List<TestCondition> conditions = Lists.newArrayList();

      if (config.getOperations() > 0)
         conditions.add(new CounterCondition(Operation.ALL, Counter.OPERATIONS,
               config.getOperations(), test, stats));

      if (config.getAborts() > 0)
         conditions.add(new CounterCondition(Operation.ALL, Counter.ABORTS, config.getAborts(),
               test, stats));

      final Map<Integer, Integer> scMap = config.getStatusCodes();
      for (final Entry<Integer, Integer> sc : scMap.entrySet())
      {
         if (sc.getValue() > 0)
            conditions.add(new StatusCodeCondition(Operation.ALL, sc.getKey(), sc.getValue(), test,
                  stats));
      }

      if (config.getRuntime() > 0)
         conditions.add(new RuntimeCondition(test, config.getRuntime(), config.getRuntimeUnit()));

      for (final TestCondition condition : conditions)
      {
         eventBus.register(condition);
      }

      return conditions;
   }

   @Provides
   @Singleton
   public Producer<Producer<Request>> provideRequestProducer(
         @Write final Producer<Request> write,
         @Read final Producer<Request> read,
         @Delete final Producer<Request> delete,
         @WriteWeight final double writeWeight,
         @ReadWeight final double readWeight,
         @DeleteWeight final double deleteWeight)
   {
      checkNotNull(write);
      checkNotNull(read);
      checkNotNull(delete);
      final double sum = readWeight + writeWeight + deleteWeight;
      checkArgument(DoubleMath.fuzzyEquals(sum, 100.0, ERR),
            "Sum of percentages must be 100.0 [%s]", sum);

      final RandomChoiceProducer.Builder<Producer<Request>> wrc =
            new RandomChoiceProducer.Builder<Producer<Request>>();
      if (writeWeight > 0.0)
         wrc.withChoice(write, writeWeight);
      if (readWeight > 0.0)
         wrc.withChoice(read, readWeight);
      if (deleteWeight > 0.0)
         wrc.withChoice(delete, deleteWeight);

      return wrc.build();
   }

   @Provides
   @Singleton
   public ObjectManager provideObjectManager(
         @ObjectFileLocation final String objectFileLocation,
         @ObjectFileName final String objectFileName)
   {
      return new RandomObjectPopulator(UUID.randomUUID(), objectFileLocation, objectFileName);
   }

   @Provides
   @Singleton
   @WriteObjectName
   public CachingProducer<String> provideWriteObjectName(final Api api)
   {
      if (Api.SOH == checkNotNull(api))
         return null;
      return new CachingProducer<String>(new UUIDObjectNameProducer());
   }

   @Provides
   @Singleton
   @ReadObjectName
   public CachingProducer<String> provideReadObjectName(final ObjectManager objectManager)
   {
      return new CachingProducer<String>(new ReadObjectNameProducer(objectManager));
   }

   @Provides
   @Singleton
   @DeleteObjectName
   public CachingProducer<String> provideDeleteObjectName(final ObjectManager objectManager)
   {
      return new CachingProducer<String>(new DeleteObjectNameProducer(objectManager));
   }

   @Provides
   @Singleton
   public List<ObjectNameConsumer> provideObjectNameConsumers(
         final ObjectManager objectManager,
         final EventBus eventBus)
   {
      final List<Integer> sc = HttpUtil.SUCCESS_STATUS_CODES;
      final List<ObjectNameConsumer> consumers = Lists.newArrayList();
      consumers.add(new WriteObjectNameConsumer(objectManager, sc));
      consumers.add(new ReadObjectNameConsumer(objectManager, sc));

      for (final ObjectNameConsumer consumer : consumers)
      {
         eventBus.register(consumer);
      }
      return consumers;
   }
}
