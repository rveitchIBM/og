/* Copyright (c) IBM Corporation 2016. All Rights Reserved.
 * Project name: Object Generator
 * This project is licensed under the Apache License 2.0, see LICENSE.
 */

package com.ibm.og.test.condition;

import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkArgument;

public class LoadTestResult {
  public final long timestampStart;
  public final long timestampFinish;
  public final boolean success;
  public final ImmutableList<String> messages;

  public LoadTestResult(final long timestampStart, final long timestampFinish,
      final boolean success, final ImmutableList<String> messages) {
    checkArgument(timestampStart >= 0, "timestampStart must be >= 0 [%s]", timestampStart);
    checkArgument(timestampStart <= timestampFinish,
        "timestampStart must be <= timestampFinish [%s, %s]", timestampStart, timestampFinish);
    this.timestampStart = timestampStart;
    this.timestampFinish = timestampFinish;
    this.success = success;
    this.messages = messages;

  }
}
