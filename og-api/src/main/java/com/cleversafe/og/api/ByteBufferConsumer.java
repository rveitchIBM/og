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
// Date: Mar 12, 2014
// ---------------------

package com.cleversafe.og.api;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map.Entry;

public interface ByteBufferConsumer extends Consumer<ByteBuffer>
{
   Iterator<Entry<String, String>> metaData();
}