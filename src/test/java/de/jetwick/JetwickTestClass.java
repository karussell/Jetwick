/**
 * Copyright (C) 2010 Peter Karich <jetwick_@_pannous_._info>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.jetwick;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import de.jetwick.config.DefaultModule;
import java.util.Map;
import java.util.Map.Entry;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JetwickTestClass {

    private Injector injector;

    public void setUp() throws Exception {
        // for every test case create a new db => update providers        
        injector = Guice.createInjector(createModule());
        injector.injectMembers(getInjectObject());
    }

    public <T> T getInstance(Class<T> cl) {
        return injector.getInstance(cl);
    }

    public Object getInjectObject() {
        return this;
    }

    public void tearDown() throws Exception {
    }

    public Module createModule() {
        return new DefaultModule();
    }

    public static Thread.UncaughtExceptionHandler createExceptionMapHandler(final Map<Thread, Throwable> exceptionMap) {
        return new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                exceptionMap.put(t, e);
            }
        };
    }

    public static void checkExceptions(Map<Thread, Throwable> exceptionMap) {
        if (!exceptionMap.isEmpty()) {
            for (Entry<Thread, Throwable> e : exceptionMap.entrySet()) {
                e.getValue().printStackTrace();
            }
            fail("See the stacktraces above. " + exceptionMap.size() + " exceptions detected ");
        }
    }
}
