/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.durableexecutor;

import com.hazelcast.config.Config;
import com.hazelcast.config.DurableExecutorConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ManagedContext;
import com.hazelcast.core.PartitionAware;
import com.hazelcast.executor.ExecutorServiceTestSupport;
import com.hazelcast.spi.properties.GroupProperty;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class DurableSpecificSetupTest extends ExecutorServiceTestSupport {

    @Test
    public void managedContext_mustInitializeRunnable() throws Exception {
        final AtomicBoolean initialized = new AtomicBoolean();
        final Config config = new Config()
                .addDurableExecutorConfig(new DurableExecutorConfig("test").setPoolSize(1))
                .setManagedContext(new ManagedContext() {
                    @Override
                    public Object initialize(Object obj) {
                        if (obj instanceof RunnableWithManagedContext) {
                            initialized.set(true);
                        }
                        return obj;
                    }
                });
        DurableExecutorService executor = createHazelcastInstance(config).getDurableExecutorService("test");
        executor.submit(new RunnableWithManagedContext()).get();
        assertTrue("The task should have been initialized by the ManagedContext", initialized.get());
    }

    @Test
    public void operationTimeoutConfigProp() throws Exception {
        TestHazelcastInstanceFactory factory = createHazelcastInstanceFactory(2);
        Config config = new Config();
        int timeoutSeconds = 3;
        config.setProperty(GroupProperty.OPERATION_CALL_TIMEOUT_MILLIS.getName(), String.valueOf(SECONDS.toMillis(timeoutSeconds)));
        HazelcastInstance hz1 = factory.newHazelcastInstance(config);
        HazelcastInstance hz2 = factory.newHazelcastInstance(config);
        String key = generateKeyOwnedBy(hz2);
        DurableExecutorService executor = hz1.getDurableExecutorService(randomString());
        Future<Boolean> f = executor.submitToKeyOwner(new SleepingTask(3 * timeoutSeconds), key);
        Boolean result = f.get(1, MINUTES);
        assertTrue(result);
    }

    private static class SleepLatchRunnable implements Runnable, Serializable {
        static CountDownLatch startLatch;
        static CountDownLatch sleepLatch;

        SleepLatchRunnable() {
            startLatch = new CountDownLatch(1);
            sleepLatch = new CountDownLatch(1);
        }

        @Override
        public void run() {
            startLatch.countDown();
            assertOpenEventually(sleepLatch);
        }
    }

    static class RunnableWithManagedContext implements Runnable, Serializable {
        @Override
        public void run() {
        }
    }

    static class EmptyRunnable implements Runnable, Serializable, PartitionAware {
        @Override
        public void run() {
        }

        @Override
        public Object getPartitionKey() {
            return "key";
        }
    }
}
