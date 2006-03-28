/**
 *  Copyright 2003-2006 Greg Luck
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.sf.ehcache.distribution;

import net.sf.ehcache.CacheManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.rmi.RemoteException;
import java.util.StringTokenizer;

/**
 * Receives heartbeats from any {@link MulticastKeepaliveHeartbeatSender}s out there.
 * <p/>
 * Our own multicast heartbeats are ignored.
 *
 * @author Greg Luck
 * @version $Id: MulticastKeepaliveHeartbeatReceiver.java,v 1.1 2006/03/09 06:38:19 gregluck Exp $
 */
public class MulticastKeepaliveHeartbeatReceiver {

    private static final Log LOG = LogFactory.getLog(MulticastKeepaliveHeartbeatReceiver.class.getName());

    private InetAddress groupMulticastAddress;
    private Integer groupMulticastPort;
    private MulticastReceiverThread receiverThread;
    private MulticastSocket socket;
    private boolean stopped;
    private MulticastRMICacheManagerPeerProvider peerProvider;

    /**
     * Constructor
     *
     * @param peerProvider
     * @param multicastAddress
     * @param multicastPort
     */
    public MulticastKeepaliveHeartbeatReceiver(
            MulticastRMICacheManagerPeerProvider peerProvider, InetAddress multicastAddress, Integer multicastPort) {
        this.peerProvider = peerProvider;
        this.groupMulticastAddress = multicastAddress;
        this.groupMulticastPort = multicastPort;
    }

    /**
     * Start
     * @throws IOException
     */
    void init() throws IOException {

        socket = new MulticastSocket(groupMulticastPort.intValue());
        socket.joinGroup(groupMulticastAddress);
        receiverThread = new MulticastReceiverThread();
        receiverThread.start();
    }

    /**
     * Shutdown the heartbeat
     */
    public void dispose() {
        stopped = true;
        receiverThread.interrupt();
    }


    /**
     * A multicast receiver which continously receives heartbeats.
     */
    private class MulticastReceiverThread extends Thread {



        public void run() {
            byte[] buf = new byte[PayloadUtil.MTU];
            while (!stopped) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    byte[] payload = packet.getData();
                    processPayload(payload);


                } catch (IOException e) {
                    if (!stopped) {
                        LOG.error("Error receiving heartbeat. " + e.getMessage() + ". Error was " + e.getMessage());
                    }
                }
            }
        }

        private void processPayload(byte[] compressedPayload) {
            byte[] payload = PayloadUtil.ungzip(compressedPayload);
            String rmiUrls = new String(payload);
            if (self(rmiUrls)) {
                return;
            }
            rmiUrls = rmiUrls.trim();
            if (LOG.isDebugEnabled()) {
                LOG.debug("rmiUrls received " + rmiUrls);
            }
            for (StringTokenizer stringTokenizer = new StringTokenizer(rmiUrls,
                    PayloadUtil.URL_DELIMITER); stringTokenizer.hasMoreTokens();) {
                String rmiUrl = stringTokenizer.nextToken();
                registerNotification(rmiUrl);
            }
        }


        /**
         * @param rmiUrls
         * @return true if our own hostname and listener port are found in the list. This then means we have
         * caught our onw multicast, and should be ignored.
         */
        private boolean self(String rmiUrls) {
            CacheManager cacheManager = peerProvider.getCacheManager();
            CachePeer peer = (CachePeer) cacheManager.getCachePeerListener().getBoundCachePeers().get(0);
            String cacheManagerUrlBase = null;
            try {
                cacheManagerUrlBase = peer.getUrlBase();
            } catch (RemoteException e) {
                LOG.error("Error geting url base");
            }
            int baseUrlMatch = rmiUrls.indexOf(cacheManagerUrlBase);
            return baseUrlMatch != -1;
        }

        private void registerNotification(String rmiUrl) {
            peerProvider.registerPeer(rmiUrl);
        }


        /**
         * {@inheritDoc}
         */
        public void interrupt() {
            try {
                socket.leaveGroup(groupMulticastAddress);
            } catch (IOException e) {
                LOG.error("Error leaving group");
            }
            socket.close();
            super.interrupt();
        }
    }


}
