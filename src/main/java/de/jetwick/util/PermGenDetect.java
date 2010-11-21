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

package de.jetwick.util;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class PermGenDetect {

    String name;

    public static void main(String[] args) throws Exception {
        PermGenDetect app = new PermGenDetect("SimpleMain");

        // add to catalina.sh
// -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9347 -Dcom.sun.management.jmxremote.authenticate=true \
// -Dcom.sun.management.jmxremote.ssl=false \
// -Dcom.sun.management.jmxremote.password.file=tomcatinstall/conf/jmxremote.password -Dcom.sun.management.jmxremote.access.file=tomcatinstall/conf/jmxremote.access"

        if (args.length == 0)
            app.memoryUsage("service:jmx:rmi:///jndi/rmi://81.169.187.238:9347/jmxrmi", false);
        else
            app.memoryUsage(args[0], Boolean.parseBoolean(args[1]));
    }

    public PermGenDetect(String name) {
        this.name = name;
    }

    public void memoryUsage(String url, boolean details) throws Exception {
        memoryUsage(new JMXServiceURL(url), details);
    }

    // find jmx connection http://blogs.sun.com/jmxetc/entry/how_to_retrieve_remote_jvm
    public void memoryUsage(JMXServiceURL target, boolean details) throws Exception {
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        String[] creds = {"monitorRole", "diff4%&pw"};
        env.put(JMXConnector.CREDENTIALS, creds);

        final JMXConnector connector = JMXConnectorFactory.connect(target, env);
        final MBeanServerConnection remote = connector.getMBeanServerConnection();

        final MemoryMXBean memoryBean = ManagementFactory.newPlatformMXBeanProxy(
                remote, ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);

        long MB = 1024 * 1024;
        long free = (memoryBean.getNonHeapMemoryUsage().getMax() - memoryBean.getNonHeapMemoryUsage().getUsed()) / MB;

        System.out.println(free);
        if (details) {
            RuntimeMXBean remoteRuntime = ManagementFactory.newPlatformMXBeanProxy(
                    remote,
                    ManagementFactory.RUNTIME_MXBEAN_NAME,
                    RuntimeMXBean.class);

            System.out.println("Target VM is: " + remoteRuntime.getName());
            System.out.println("VM version: " + remoteRuntime.getVmVersion());
            System.out.println("VM vendor: " + remoteRuntime.getVmVendor());
            System.out.println("Started since: " + remoteRuntime.getUptime());
            System.out.println("With Classpath: " + remoteRuntime.getClassPath());
            System.out.println("And args: " + remoteRuntime.getInputArguments() + "\n");

            System.out.println("---Memory Usage--- " + new Date());

            System.out.println("Committed Perm Gen:" + memoryBean.getNonHeapMemoryUsage().getCommitted());
            System.out.println("init Perm Gen     :" + memoryBean.getNonHeapMemoryUsage().getInit());
            System.out.println("max Perm Gen      :" + memoryBean.getNonHeapMemoryUsage().getMax());
            System.out.println("Used Perm Gen     :" + memoryBean.getNonHeapMemoryUsage().getUsed() + "\n");

            for (MemoryPoolMXBean mb : ManagementFactory.getMemoryPoolMXBeans()) {
                System.out.println("\n" + mb.getName());
                System.out.println(mb.getType().toString());
                if (mb.getCollectionUsage() != null)
                    System.out.println("coll max:" + mb.getCollectionUsage().getMax());

                System.out.println("committed:" + mb.getUsage().getCommitted());
                System.out.println("init:" + mb.getUsage().getInit());
                System.out.println("max :" + mb.getUsage().getMax());
                System.out.println("used:" + mb.getUsage().getUsed());
            }
        }
        connector.close();

    }
}
