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

package de.jetwick.rmi;

import de.jetwick.config.Configuration;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Tweet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class RMIServer implements CommunicationService {

    public static void main(String args[]) throws Exception {
        new RMIServer(new Configuration()).createThread().run();
    }
    private static Logger logger = LoggerFactory.getLogger(RMIServer.class);
    private Queue<Tweet> tweetQueue;
    private Configuration config;

    public RMIServer(Configuration config) {
        this.config = config;
    }

    public Thread createThread() {
        return new Thread("rmi-server") {

            @Override
            public void run() {
                try {
                    // get the address of this host.
                    String host = (InetAddress.getLocalHost()).toString();
                    logger.info("address=" + host + " port=" + config.getRMIPort());
                    CommunicationService stub = (CommunicationService) UnicastRemoteObject.exportObject(RMIServer.this, 0);
                    Registry registry = LocateRegistry.createRegistry(config.getRMIPort());
                    registry.rebind(config.getRMIService(), stub);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
    }

    @Override
    public int send(Collection<? extends Tweet> tws) {
        if (tweetQueue == null)
            return 0;

        // prevent us from OOMs
        if (tweetQueue.size() > 50000) {
            logger.error("didn't prozessed " + tws.size() + " tweets. queue is full: " + tweetQueue.size());
            return 0;
        }

        tweetQueue.addAll(tws);
        Tweet tw = tws.iterator().next();
        String user = "First ist from: " + tw.getFromUser() + " source:" + tw.getSource();
        logger.info("received: " + tws.size() + " tweets." + user);
        return tws.size();
    }

    public void setTweets(Queue<Tweet> tweets) {
        this.tweetQueue = tweets;
    }
}
