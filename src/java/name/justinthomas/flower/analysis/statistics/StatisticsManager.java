package name.justinthomas.flower.analysis.statistics;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.StoreConfig;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpSession;
import name.justinthomas.flower.analysis.element.DefaultNode;
import name.justinthomas.flower.analysis.element.Flow;
import name.justinthomas.flower.analysis.element.InetNetwork;
import name.justinthomas.flower.analysis.element.ManagedNetworks;
import name.justinthomas.flower.analysis.element.Network;
import name.justinthomas.flower.analysis.element.Node;
import name.justinthomas.flower.analysis.persistence.ConfigurationManager;
import name.justinthomas.flower.analysis.persistence.Constraints;
import name.justinthomas.flower.utility.AddressAnalysis;

/**
 *
 * @author justin
 */
public class StatisticsManager {

    private static final Integer DEBUG = 1;
    ConfigurationManager configurationManager = ConfigurationManager.getConfigurationManager();

    private Environment setupEnvironment() {
        File environmentHome = new File(configurationManager.getBaseDirectory() + "/" + configurationManager.getStatisticsDirectory());

        try {
            if (!environmentHome.exists()) {
                if (!environmentHome.mkdirs()) {
                    throw new Exception("Could not open or create base statistics directory.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }

        EnvironmentConfig environmentConfig = new EnvironmentConfig();

        environmentConfig.setAllowCreate(true);
        environmentConfig.setReadOnly(false);
        environmentConfig.setLockTimeout(15, TimeUnit.SECONDS);

        Environment environment = new Environment(environmentHome, environmentConfig);
        return environment;
    }

    private void closeEnvironment(Environment environment) {
        if (environment != null) {
            try {
                environment.close();
            } catch (DatabaseException e) {
                System.err.println("Error closing environment: " + e.toString());
            } catch (IllegalStateException e) {
                System.err.println("Error closing environment: " + e.toString());
            }
        }
    }

    private StoreConfig getStoreConfig(Boolean readOnly) {
        StoreConfig storeConfig = new StoreConfig();
        storeConfig.setAllowCreate(true);
        if(readOnly) {
            storeConfig.setReadOnly(true);
        } else {
            storeConfig.setReadOnly(false);
        }
        storeConfig.setDeferredWrite(true);

        return storeConfig;
    }

    private void closeStore(EntityStore store) {
        try {
            store.close();
        } catch (DatabaseException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<IntervalKey> identifyExpiredIntervals() {
        System.out.println("Identifying expired intervals...");
        Environment environment;
        EntityStore entityStore = new EntityStore(environment = setupEnvironment(), "Statistics", this.getStoreConfig(true));
        StatisticsAccessor dataAccessor = new StatisticsAccessor(entityStore);

        long second = 1000;
        long minute = 60 * second;
        long hour = 60 * minute;
        long day = 24 * hour;
        long week = 7 * day;
        long year = 365 * day;
        long month = year / 12;

        long statisticsRetention = month * 2;

        Date now = new Date();
        Date startDate = new Date();
        startDate.setTime(now.getTime() - (year * 1));

        Date endDate = new Date();
        endDate.setTime(now.getTime() - statisticsRetention);

        Long start = startDate.getTime() / configurationManager.getResolution().get("High");
        IntervalKey startKey = new IntervalKey(start, configurationManager.getResolution().get("High"));
        Long end = endDate.getTime() / configurationManager.getResolution().get("High");
        IntervalKey endKey = new IntervalKey(end, configurationManager.getResolution().get("High"));

        PrimaryIndex<IntervalKey, StatisticalInterval> startIndex = dataAccessor.intervalByKey;
        EntityCursor<StatisticalInterval> cursor = startIndex.entities(startKey, true, endKey, true);

        ArrayList<IntervalKey> expiredIntervals = new ArrayList();
        while(cursor.next() != null) {
            expiredIntervals.add(cursor.current().getSecond());
        }

        printEnvironmentStatistics(environment);
        closeStore(entityStore);
        closeEnvironment(environment);

        return expiredIntervals;
    }

    public void cleanStatisticalIntervals() {
        ArrayList<IntervalKey> keys = identifyExpiredIntervals();

        System.out.println("Deleting expired intervals...");

        Environment environment;
        EntityStore entityStore = new EntityStore(environment = setupEnvironment(), "Statistics", this.getStoreConfig(false));
        StatisticsAccessor dataAccessor = new StatisticsAccessor(entityStore);

        for(IntervalKey key : keys) {
            dataAccessor.intervalByKey.delete(key);
        }

        printEnvironmentStatistics(environment);
        closeStore(entityStore);
        closeEnvironment(environment);
    }

    private void printEnvironmentStatistics(Environment environment) {
        StatsConfig config = new StatsConfig();
        config.setClear(true);
        System.err.println(environment.getStats(config));
    }

    private HashMap<IntervalKey, StatisticalInterval> flowToInterval(Flow flow, Long interval) {
        HashMap<IntervalKey, StatisticalInterval> normalized = new HashMap<IntervalKey, StatisticalInterval>();

        Long startSecond = (flow.startTimeStamp.getTime() / interval);
        Long endSecond = (flow.lastTimeStamp.getTime() / interval);
        Long spread = endSecond - startSecond;

        for (long l = startSecond; l <= endSecond; l++) {
            StatisticalInterval sInterval = new StatisticalInterval();
            sInterval.key = new IntervalKey(l, interval);
            normalized.put(new IntervalKey(l, interval), sInterval.addFlow(new StatisticalFlow(spread, flow)));
        }

        return normalized;
    }

    public void addStatisticalSeconds(Flow flow) {
        EntityStore entityStore = null;
        EntityStore entityStoreRO = null;
        Environment environment = null;

        try {
            environment = setupEnvironment();

            entityStore = new EntityStore(environment, "Statistics", this.getStoreConfig(false));
            entityStoreRO = new EntityStore(environment, "Statistics", this.getStoreConfig(true));

            try {
                StatisticsAccessor dataAccessor = new StatisticsAccessor(entityStore);
                StatisticsAccessor dataAccessorRO = new StatisticsAccessor(entityStoreRO);

                for (Long interval : configurationManager.getResolution().values()) {
                    HashMap<IntervalKey, StatisticalInterval> normalized = flowToInterval(flow, interval);

                    for (Entry<IntervalKey, StatisticalInterval> entry : normalized.entrySet()) {
                        if (dataAccessorRO.intervalByKey.get(entry.getKey()) != null) {
                            StatisticalInterval stored = dataAccessorRO.intervalByKey.get(entry.getKey());
                            stored = stored.addSecond(normalized.get(entry.getKey()));
                            dataAccessor.intervalByKey.put(stored);
                        } else {
                            dataAccessor.intervalByKey.put(normalized.get(entry.getKey()));
                        }
                    }
                }
            } catch (DatabaseException e) {
                System.err.println("addStatisticalSeconds Failed: " + e.getMessage());
            } finally {
                closeStore(entityStoreRO);
                closeStore(entityStore);
            }
        } catch (DatabaseException e) {
            System.err.println("Database Error: " + e.getMessage());
        } finally {
            closeEnvironment(environment);
        }
    }

    private Long getResolution(long duration, Integer bins) {
        List<Long> resolutions = new ArrayList(configurationManager.getResolution().values());

        // This sorts the resolutions from highest to lowest (smallest number to largest number)
        Collections.sort(resolutions);

        Long returnValue = null;

        for (Long resolution : resolutions) {
            if ((bins != null) && (bins > 0)) {
                if ((duration / resolution) >= bins) {
                    returnValue = resolution;
                }
            } else {
                if (resolution == null) {
                    // This is only triggered on the first evaluation of 'resolutions' (i.e., the smallest number,
                    // aka. highest resolution)
                    if (duration <= (resolution * 3)) {
                        // Example: if duration is 10 seconds, the (default) highest resolution is 5(*3) seconds;
                        // we just select the first (smallest number, highest resolution) and break out.
                        returnValue = resolution;
                        break;
                    }
                }

                if (duration > (resolution * 3)) {
                    // Using 3 as a multiplier is arbitrary; a different number might prove to be more efficient/effective.
                    returnValue = resolution;
                }
            }
        }

        System.out.println("Setting resolution to: " + returnValue);
        return returnValue;
    }

    public LinkedHashMap<Date, HashMap<String, Long>> getVolumeByTime(HttpSession session, Constraints constraints, Integer bins)
            throws ClassNotFoundException {

        LinkedHashMap<Date, HashMap<String, Long>> consolidated = new LinkedHashMap<Date, HashMap<String, Long>>();

        if ((constraints.startTime == null) || (constraints.endTime == null) || (bins == null) || (constraints.startTime.getTime() >= constraints.endTime.getTime())) {
            return consolidated;
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        if (StatisticsManager.DEBUG >= 1) {
            System.out.println("Requesting volume data from " + dateFormat.format(constraints.startTime) + " to " + dateFormat.format(constraints.endTime));
        }

        if (StatisticsManager.DEBUG >= 1) {
            System.out.println("Populating consolidated map");
        }
        Long duration = constraints.endTime.getTime() - constraints.startTime.getTime();
        Long interval = duration / bins;

        // Populate consolidated with an entry for each minute that we want to graph
        int j;
        for (j = 0; j < bins;) {
            HashMap<String, Long> interim = new HashMap<String, Long>();
            interim.put("total", 0l);
            interim.put("tcp", 0l);
            interim.put("udp", 0l);
            interim.put("icmp", 0l);
            interim.put("ipsec", 0l);
            interim.put("ipv4", 0l);
            interim.put("ipv6", 0l);

            consolidated.put(new Date(constraints.startTime.getTime() + (j++ * interval)), interim);
        }

        if (StatisticsManager.DEBUG >= 1) {
            System.out.println("Iterating over flow volumes in database");
        }

        Environment environment;
        Long resolution = getResolution(duration, bins);

        EntityStore store = new EntityStore(environment = setupEnvironment(), "Statistics", this.getStoreConfig(true));
        StatisticsAccessor accessor = new StatisticsAccessor(store);
        Long start = constraints.startTime.getTime() / resolution;
        IntervalKey startKey = new IntervalKey(start, resolution);
        Long end = constraints.endTime.getTime() / resolution;
        IntervalKey endKey = new IntervalKey(end, resolution);
        //System.out.println("Start: " + start + ", End: " + end);
        EntityCursor<StatisticalInterval> cursor = accessor.intervalByKey.entities(startKey, true, endKey, true);

        // We only want to do this query once, so make it count.
        if (StatisticsManager.DEBUG >= 1) {
            System.out.println("Iterating over query results.");
        }

        Integer flowsProcessed = 0;
        try {
            try {
                try {
                    for (StatisticalInterval second : cursor) {
                        Long totalVolume = 0l;
                        Long tcpVolume = 0l;
                        Long udpVolume = 0l;
                        for (StatisticalFlow entry : second.flows.values()) {
                            for (Entry<StatisticalFlowDetail, Long> volume : entry.getCount().entrySet()) {
                                if (volume.getKey().getType().equals(StatisticalFlowDetail.Count.BYTE)) {
                                    totalVolume += volume.getValue();
                                    if (volume.getKey().getProtocol().equals(6)) {
                                        tcpVolume += volume.getValue();
                                    }
                                    if (volume.getKey().getProtocol().equals(17)) {
                                        udpVolume += volume.getValue();
                                    }
                                }
                            }
                        }

                        // Iterate over the bins in consolidated
                        for (Date bin : consolidated.keySet()) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new InterruptedException();
                            }

                            if (new Date(second.getSecond().interval * resolution).after(new Date(bin.getTime())) && new Date(second.getSecond().interval * resolution).before(new Date(bin.getTime() + interval))) {
                                consolidated.get(bin).put("total", consolidated.get(bin).get("total") + totalVolume);
                                consolidated.get(bin).put("tcp", consolidated.get(bin).get("tcp") + tcpVolume);
                                consolidated.get(bin).put("udp", consolidated.get(bin).get("udp") + udpVolume);
                            }
                        }

                        if (DEBUG > 0) {
                            if (++flowsProcessed % 1000 == 0) {
                                System.out.println("StatisticalSeconds processed: " + flowsProcessed);
                            }
                        }

                        if (Thread.currentThread().isInterrupted()) {
                            System.out.println("StatisticsManager was interrupted");
                            throw new InterruptedException();
                        }
                    }
                } catch (DatabaseException e) {
                    e.printStackTrace();
                } finally {
                    cursor.close();
                }
            } catch (DatabaseException e) {
                e.printStackTrace();
            } finally {
                closeStore(store);
            }
        } catch (InterruptedException ie) {
            System.err.println("Stopping StatisticsManager during Volume build...");
        } finally {
            closeEnvironment(environment);
        }

        //System.out.println("Returning from FlowManager:getVolumeByTime");
        return consolidated;
    }

    public StatisticalFlow setDefault(ArrayList<Network> networks, StatisticalFlow flow) throws UnknownHostException {
        Boolean sourceManaged = false;
        Boolean destinationManaged = false;

        for (Network network : networks) {
            if (!sourceManaged && AddressAnalysis.isMember(InetAddress.getByName(flow.getSource()), network.getNetwork())) {
                sourceManaged = true;
            }
            if (!destinationManaged && AddressAnalysis.isMember(InetAddress.getByName(flow.getDestination()), network.getNetwork())) {
                destinationManaged = true;
            }
            if (sourceManaged && destinationManaged) {
                break;
            }
        }

        if (!sourceManaged) {
            flow.setSource("0.0.0.0");
        }

        if (!destinationManaged) {
            flow.setDestination("0.0.0.0");
        }

        return flow;
    }

    public ArrayList<Network> getNetworks(HttpSession session, Constraints constraints) {
        LinkedHashMap<String, InetNetwork> managedNetworks = new ManagedNetworks().getNetworks();

        ArrayList<Network> networks = new ArrayList<Network>();

        for (InetNetwork network : managedNetworks.values()) {
            networks.add(new Network(network));
        }

        Integer g = 0, n = 0;

        Network defaultNetwork = null;

        try {
            defaultNetwork = new Network(InetAddress.getByName("0.0.0.0"), 0, "DEFAULT");
        } catch (UnknownHostException uhe) {
            System.err.println("Could not parse network for DEFAULT: " + uhe.getMessage());
        }

        DefaultNode defaultNode = new DefaultNode();

        long duration = constraints.endTime.getTime() - constraints.startTime.getTime();
        long resolution = getResolution(duration, null);
        Environment environment;
        EntityStore readOnlyEntityStore = new EntityStore(environment = setupEnvironment(), "Statistics", this.getStoreConfig(true));
        StatisticsAccessor accessor = new StatisticsAccessor(readOnlyEntityStore);
        IntervalKey startKey = new IntervalKey(constraints.startTime.getTime() / resolution, resolution);
        IntervalKey endKey = new IntervalKey(constraints.endTime.getTime() / resolution, resolution);
        EntityCursor<StatisticalInterval> cursor = accessor.intervalByKey.entities(startKey, true, endKey, true);

        // We only want to do this query once, so make it count.

        System.out.println("Iterating over query results.");

        Integer secondsProcessed = 0;
        try {
            try {
                try {

                    for (StatisticalInterval second : cursor) {
                        for (StatisticalFlow flow : second.flows.values()) {
                            flow = setDefault(networks, flow);

                            if (flow.getSource() == null) {
                                n++;
                            } else {
                                InetAddress sourceAddress = InetAddress.getByName(flow.getSource());
                                InetAddress destinationAddress = InetAddress.getByName(flow.getDestination());
                                Node sourceNode = new Node(flow.getSource());
                                Node destinationNode = new Node(flow.getDestination());

                                //System.out.println(".");
                                // Associate the flow with the source (flows are never associated with the destination)
                                sourceNode.addFlow(flow);
                                // On that last point, just kidding
                                destinationNode.addFlow(flow);

                                Boolean sourceCaptured = false;
                                Boolean destinationCaptured = false;
                                // Iterate through managed networks and add the Nodes (complete with Flows)
                                // as they are encountered
                                for (Network network : networks) {
                                    if (!sourceCaptured || !destinationCaptured) {
                                        // If the source address belongs to a managed network, add it to the map
                                        // De-duplication is handled by the "addNode" method in the Network object
                                        if (AddressAnalysis.isMember(sourceAddress, network.getNetwork())) {
                                            network.addNode(sourceNode);
                                            sourceCaptured = true;
                                        }

                                        if (AddressAnalysis.isMember(destinationAddress, network.getNetwork())) {
                                            network.addNode(destinationNode);
                                            destinationCaptured = true;
                                        }

                                        if (Thread.currentThread().isInterrupted()) {
                                            throw new InterruptedException();
                                        }
                                    }
                                }

                                if (!sourceCaptured) {
                                    if (!flow.getDestination().equals("0.0.0.0")) {
                                        defaultNode.addFlow(flow);
                                    }
                                } else if (!destinationCaptured) {
                                    defaultNode.addFlow(flow);
                                }

                                // If the flow was not captured, add it to the default node
                                //if (!sourceCaptured || !destinationCaptured) {
                                //    defaultNode.addFlow(flow);
                                //}
                            }

                            if (Thread.currentThread().isInterrupted()) {
                                //System.out.println("Stopping FlowManager...");
                                throw new InterruptedException();
                            }
                        }

                        if (++secondsProcessed % 10000 == 0) {
                            System.out.println(secondsProcessed + " StatisticalSeconds processed.");
                        }
                    }
                } catch (DatabaseException e) {
                    e.printStackTrace();
                } finally {
                    cursor.close();
                }
            } catch (DatabaseException e) {
                e.printStackTrace();
            } finally {
                closeStore(readOnlyEntityStore);
            }
        } catch (UnknownHostException e) {
            System.err.println("UnknownHostException caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        } catch (InterruptedException ie) {
            System.err.println("Stopped FlowManager during network build");
        } catch (Exception e) {
            System.err.println("Exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeEnvironment(environment);
        }

        System.out.println(secondsProcessed + " total StatisticalSeconds analyzed.");
        System.out.println("Found " + n + " null flows.");
        System.out.println("Found " + g + " non-IP flows.");
        defaultNetwork.addNode(defaultNode);

        // Add the default network to the bottom of the network list
        networks.add(defaultNetwork);

        //createTraceFile(networks);

        return networks;
    }

    private void createTraceFile(ArrayList<Network> networks) {
        try {
            FileWriter writer = new FileWriter("/traces/analysis_out.txt");
            for (Network network : networks) {
                writer.append("Monitored Network: " + network.getNetwork().toString() + " has " + network.getAllNodes().size() + " entries.\n");
                for (Node node : network.getAllNodes()) {
                    writer.append("n:" + node.toString() + ", receivedBytes: " + node.getBytesReceived() + ", sentBytes: " + node.getBytesSent() + "\n");
                    for (Flow flow : node.getFlowsOriginated()) {
                        writer.append("s:" + flow.toString() + "\n");
                    }

                    for (Flow flow : node.getFlowsReceived()) {
                        writer.append("d:" + flow.toString() + "\n");
                    }
                }
            }        // Add the default node (with newly acquired flows) to the default network
            writer.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}