package name.justinthomas.flower.analysis.statistics;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.bind.annotation.XmlType;
import name.justinthomas.flower.analysis.statistics.AnomalyEvent.Anomaly;

/**
 *
 * @author justin
 */

@Entity(version = 104)
@XmlType
public class StatisticalInterval {

    @PrimaryKey
    public IntervalKey key;
    public Map<StatisticalFlowIdentifier, StatisticalFlow> flows = new HashMap();
    public ArrayList<Long> flowIDs = new ArrayList();
    public ArrayList<AnomalyEvent> anomalies = new ArrayList();

    public void clear() {
        key = null;
        flows.clear();
    }
    
    public StatisticalInterval addAnomaly(StatisticalFlowIdentifier id, Anomaly anomaly, Integer basis) {
        anomalies.add(new AnomalyEvent(id.getSource(), id.getDestination(), anomaly, basis));
        return this;
    }

    public StatisticalInterval addFlow(StatisticalFlow flow, Long flowID) {
        if (flows.containsKey(flow.id())) {
            flows.put(flow.id(), flow.addFlow(flow));
        } else {
            flows.put(flow.id(), flow);
        }

        flowIDs.add(flowID);

        return this;
    }

    public StatisticalInterval addInterval(StatisticalInterval statisticalInterval) {
        for (Entry<StatisticalFlowIdentifier, StatisticalFlow> entry : statisticalInterval.flows.entrySet()) {
            if (flows.containsKey(entry.getKey())) {
                flows.put(entry.getKey(), flows.get(entry.getKey()).addFlow(entry.getValue()));
            } else {
                flows.put(entry.getKey(), entry.getValue());
            }

            for(Long flowID : statisticalInterval.flowIDs) {
                flowIDs.add(flowID);
            }
        }

        return this;
    }

    public IntervalKey getSecond() {
        return key;
    }

    public void setSecond(IntervalKey second) {
        this.key = second;
    }

    public Map<StatisticalFlowIdentifier, StatisticalFlow> getFlows() {
        return flows;
    }
}
