package name.justinthomas.flower.analysis.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import javax.annotation.Resource;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import name.justinthomas.flower.analysis.authentication.UserAction;
import name.justinthomas.flower.analysis.element.Flow;
import name.justinthomas.flower.analysis.persistence.FlowManager;
import name.justinthomas.flower.analysis.persistence.FlowReceiver;
import name.justinthomas.flower.analysis.persistence.PersistentFlow;
import name.justinthomas.flower.analysis.statistics.StatisticsManager;
import name.justinthomas.flower.manager.services.CustomerAdministration.Customer;
import name.justinthomas.flower.manager.services.CustomerAdministration.Customer.Collectors;

/**
 *
 * @author justin
 */
@WebService()
public class FlowInsert {

    @Resource
    WebServiceContext context;
    
    @WebMethod(operationName = "rebuildStatistics")
    public Boolean rebuidStatistics(
            @WebParam(name = "customerID") String customerID) {
        
        MessageContext messageContext = context.getMessageContext();
        HttpServletRequest request = (HttpServletRequest) messageContext.get(MessageContext.SERVLET_REQUEST);
        
        String address = request.getRemoteAddr();
        System.out.println("Rebuild request received from: " + address);
        
        Customer customer = Utility.getCustomer(customerID);
        
        Thread thread = new Thread(new RebuildThread(customer, address));
        thread.start();
        
        return true;
    }

    @WebMethod(operationName = "addFlows")
    public Integer addFlows(
            @WebParam(name = "customerID") String customerID,
            @WebParam(name = "flows") List<PersistentFlow> flowSet) {

        MessageContext messageContext = context.getMessageContext();
        HttpServletRequest request = (HttpServletRequest) messageContext.get(MessageContext.SERVLET_REQUEST);

        String address = request.getRemoteAddr();
        System.out.println("Request received from: " + address);

        Customer customer = Utility.getCustomer(customerID);

        if (customer != null) {
            Boolean found = false;
            for (Collectors.Entry entry : customer.getCollectors().getEntry()) {
                if (entry.getKey().equals(address)) {
                    found = true;
                    break;
                }
            }

            if (found) {
                try {
                    InetAddress collector = InetAddress.getByName(address);
                    Thread thread = new Thread(new InsertThread(customer, flowSet, collector));
                    thread.start();
                } catch (UnknownHostException e) {
                    System.err.println("Could not resolve remote address: " + address);
                    return 1;
                }
            } else {
                return 1;
            }
        } else {
            System.err.println("Could not locate customer for request from: " + address);
            return 1;
        }

        return 0;
    }
    
    class RebuildThread implements Runnable {
        Customer customer;
        String collector;
        
        public RebuildThread(Customer customer, String collector) {
            this.customer = customer;
            this.collector = collector;
        }
        
        @Override
        public void run() {
            FlowManager flowManager = new FlowManager(customer);
            Long end = 0l;
            
            while(end != null) {
                end = flowManager.rebuildStatistics(collector, null);
                
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException ie) {
                    System.err.println("RebuildThread interrupted.");
                    return;
                }
            }
        }       
    }

    class InsertThread implements Runnable {

        Customer customer;
        InetAddress collector;
        List<PersistentFlow> flowSet;
        HashMap<Long, Flow> flows = new HashMap();

        public InsertThread(Customer customer, List<PersistentFlow> flowSet, InetAddress collector) {
            this.flowSet = flowSet;
            this.collector = collector;
            this.customer = customer;
        }

        @Override
        public void run() {
            System.out.println("Beginning to store " + flowSet.size() + " flows...");
            LinkedList<Flow> converted = new LinkedList();

            for (PersistentFlow xflow : flowSet) {
                try {
                    if (xflow.size.longValue() < 0) {
                        throw new Exception("Negative bytesSent value (" + xflow.size.longValue() + ") in XMLFlow received.");
                    }
                    converted.add(new Flow(customer, xflow));
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }

            FlowReceiver receiver = new FlowReceiver(customer);
            LinkedList<Long> flowIDs = receiver.addFlows(collector.getHostAddress(), converted, null);

            Iterator<Long> idIterator = flowIDs.iterator();
            Iterator<Flow> flowIterator = converted.iterator();

            while (idIterator.hasNext()) {
                flows.put(idIterator.next(), flowIterator.next());
            }

            System.out.println("Completed storing and beginning to process statistics for " + flows.size() + " flows.");

            for (Entry<Long, Flow> flow : flows.entrySet()) {
                StatisticsManager statisticsManager = new StatisticsManager(customer);
                statisticsManager.addStatisticalSeconds(flow.getValue(), flow.getKey(), collector);
            }

            //System.out.println("Completed processing statistics for " + flows.size() + " flows.");
        }
    }
}
