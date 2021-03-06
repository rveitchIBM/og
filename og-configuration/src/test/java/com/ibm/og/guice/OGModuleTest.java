/* Copyright (c) IBM Corporation 2016. All Rights Reserved.
 * Project name: Object Generator
 * This project is licensed under the Apache License 2.0, see LICENSE.
 */

package com.ibm.og.guice;

import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.ibm.og.json.FailingConditionsConfig;
import com.ibm.og.json.ConcurrencyConfig;
import com.ibm.og.json.OGConfig;
import com.ibm.og.statistic.Statistics;
import com.ibm.og.test.LoadTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import com.ibm.og.json.StoppingConditionsConfig;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

@RunWith(DataProviderRunner.class)
public class OGModuleTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private LoadTest test;
  private EventBus eventBus;
  private ConcurrencyConfig concurrency;
  private Statistics stats;
  private OGConfig config;

  @Before
  public void before() {
    this.test = mock(LoadTest.class);
    this.eventBus = mock(EventBus.class);
    this.concurrency = new ConcurrencyConfig();
    this.stats = mock(Statistics.class);
    this.config = mock(OGConfig.class);
  }

  @DataProvider
  public static Object[][] provideInvalidStoppingConditions() {
    final Map<Integer, Integer> statusCodes = ImmutableMap.of();

    return new Object[][] {
        {-1, 0.0, TimeUnit.SECONDS, 1, statusCodes, IllegalArgumentException.class},
        {1, -1.0, TimeUnit.SECONDS, 1, statusCodes, IllegalArgumentException.class},
        {1, 0.0, null, 1, statusCodes, NullPointerException.class},
        {1, 0.0, TimeUnit.SECONDS, -1, statusCodes, IllegalArgumentException.class},
        {1, 0.0, TimeUnit.SECONDS, 1, null, NullPointerException.class},
        {1, 0.0, TimeUnit.SECONDS, 1, ImmutableMap.of(201, -1), IllegalArgumentException.class}};
  }

  @Test
  @UseDataProvider("provideInvalidStoppingConditions")
  public void invalidStoppingConditions(final long operations, final double runtime,
      final TimeUnit runtimeUnit, final long concurrentRequests,
      final Map<Integer, Integer> statusCodes, final Class<Exception> expectedException) {

    final OGModule module = new OGModule(this.config);
    final StoppingConditionsConfig stoppingConditions = new StoppingConditionsConfig();
    stoppingConditions.operations = operations;
    stoppingConditions.runtime = runtime;
    stoppingConditions.runtimeUnit = runtimeUnit;
    stoppingConditions.statusCodes = statusCodes;

    final FailingConditionsConfig failingConditions = new FailingConditionsConfig();
    failingConditions.operations = operations;
    failingConditions.runtime = runtime;
    failingConditions.runtimeUnit = runtimeUnit;
    failingConditions.concurrentRequests = concurrentRequests;
    failingConditions.statusCodes = statusCodes;

    this.thrown.expect(expectedException);
    module.provideTestConditions(this.test, this.eventBus, this.stats, this.concurrency,
        stoppingConditions, failingConditions);
  }
}
