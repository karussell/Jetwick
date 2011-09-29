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
import de.jetwick.data.JTweet;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class RMIServer implements CommunicationService {

    public static void main(String args[]) throws Exception {
        new RMIServer(new Configuration()).createThread().run();
    }
    private static Logger logger = LoggerFactory.getLogger(RMIServer.class);
    private BlockingQueue<JTweet> tweetQueue;
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
                    CommunicationService stub = (CommunicationService) UnicastRemoteObject.
                            exportObject(RMIServer.this, config.getRMIPort());
                    Registry registry = LocateRegistry.createRegistry(config.getRMIPort());
                    registry.rebind(config.getRMIService(), stub);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
    }
    int counter = 1;

    @Override
    public void send(JTweet tws) {
        if (tweetQueue == null) {
            logger.error("Queue not online");
            return;
        }

        // prevent us from OOMs
        if (tweetQueue.size() > 10000) {
            if (counter++ % 100 == 0) {
                logger.warn("Skipped " + counter + " tweets - queue is full: " + tweetQueue.size());
                counter = 1;
            }
            return;
        }

        tweetQueue.add(tws);
//        logger.info("queued " + tws.getFeedSource());
    }

    @Override
    public void send(Collection<JTweet> tweets) throws RemoteException {
        if (tweets.isEmpty())
            return;

        if (tweetQueue == null) {
            logger.error("Queue not online");
            return;
        }

        // prevent us from OOMs
        if (tweetQueue.size() > 10000) {
            logger.warn("Skipped " + tweets.size() + " tweets - queue is full: " + tweetQueue.size());
            return;
        }

        tweetQueue.addAll(tweets);
        logger.info("queued " + tweets.size() + " tweets. First name " + tweets.iterator().next().getFeedSource());
    }

    public void setQueue(BlockingQueue<JTweet> tweets) {
        this.tweetQueue = tweets;
    }
}
