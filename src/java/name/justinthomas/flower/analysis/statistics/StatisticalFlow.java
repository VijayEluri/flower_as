package name.justinthomas.flower.analysis.statistics;

import java.io.IOException;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.HashMap;
import java.util.Map.Entry;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.bind.annotation.XmlType;
import name.justinthomas.flower.analysis.element.Flow;
import name.justinthomas.flower.global.GlobalConfigurationManager;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

/**
 *
 * @author justin
 */
@XmlType
public class StatisticalFlow implements Serializable {

    private static GlobalConfigurationManager globalConfigurationManager;
    private static final Logger log = Logger.getLogger(StatisticalFlow.class.getName());
    private static FileAppender fileAppender;
    
    String source;
    String destination;
    HashMap<StatisticalFlowDetail, Long> count = new HashMap<StatisticalFlowDetail, Long>();
   
    public StatisticalFlowIdentifier id() {
        return new StatisticalFlowIdentifier(source, destination);
    }

    public StatisticalFlow() {
        if (globalConfigurationManager == null) {
            try {
                globalConfigurationManager = (GlobalConfigurationManager) InitialContext.doLookup("java:global/Analysis/GlobalConfigurationManager");
            } catch (NamingException e) {
                log.error(e.getMessage());
            }
        }
        
        if (fileAppender == null) {
            try {
                fileAppender = new FileAppender(new SimpleLayout(), globalConfigurationManager.getBaseDirectory() + "/statistics.log");
                log.addAppender(fileAppender);
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
    }

    public StatisticalFlow(Long spread, Flow flow) {  // Flow should be oriented to its original direction before it arrives here - I'M RETHINKING THIS
        if(spread == 0) spread = 1l;

        try {
            source = flow.getUnfixedSourceAddress().getHostAddress();
            destination = flow.getUnfixedDestinationAddress().getHostAddress();

            StatisticalFlowDetail.Version version = null;
            if(flow.getUnfixedSourceAddress() instanceof Inet4Address) {
                version = StatisticalFlowDetail.Version.IPV4;
            } else if(flow.getUnfixedSourceAddress() instanceof Inet6Address) {
                version = StatisticalFlowDetail.Version.IPV6;
            }

            StatisticalFlowDetail bytes = new StatisticalFlowDetail(StatisticalFlowDetail.Count.BYTE, version, flow.protocol, flow.getUnfixedSourcePort(), flow.getUnfixedDestinationPort());
            StatisticalFlowDetail packets = new StatisticalFlowDetail(StatisticalFlowDetail.Count.PACKET, version, flow.protocol, flow.getUnfixedSourcePort(), flow.getUnfixedDestinationPort());

            if (count.containsKey(bytes)) {
                count.put(bytes, (flow.bytesSent.longValue() / spread) + count.get(bytes));
            } else {
                count.put(bytes, (flow.bytesSent.longValue() / spread));
            }

            if (count.containsKey(packets)) {
                count.put(packets, (flow.packetsSent.longValue() / spread) + count.get(packets));
            } else {
                count.put(packets, (flow.packetsSent.longValue() / spread));
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public StatisticalFlow addFlow(StatisticalFlow flow) {
        if(this.destination.equals(flow.destination) && this.source.equals(flow.source)) {
            for(Entry<StatisticalFlowDetail, Long> entry : flow.count.entrySet()) {
                if(this.count.containsKey(entry.getKey())) {
                    this.count.put(entry.getKey(), entry.getValue() + this.count.get(entry.getKey()));
                } else {
                    this.count.put(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }
        return null;
    }

    public String getDestination() {
        return destination;
    }

    public String getSource() {
        return source;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public HashMap<StatisticalFlowDetail, Long> getCount() {
        return count;
    }

    public void setCount(HashMap<StatisticalFlowDetail, Long> count) {
        this.count = count;
    }
    
    @Override
    public String toString() {
        return("StatisticalFlow: " + this.source + "->" + this.destination);
    }
}
