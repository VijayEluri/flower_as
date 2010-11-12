package name.justinthomas.flower.analysis.services;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.ws.WebServiceContext;
import name.justinthomas.flower.analysis.persistence.AlertManager;
import name.justinthomas.flower.analysis.persistence.ConfigurationManager;
import name.justinthomas.flower.analysis.persistence.PersistentAlert;

/**
 *
 * @author justin
 */
@WebService()
public class Alerts {

    @Resource
    private WebServiceContext serviceContext;
    @EJB
    ConfigurationManager configurationManager;

    @WebMethod(operationName = "addAlert")
    public Boolean addAlert(
            @WebParam(name = "date") Long date,
            @WebParam(name = "usec") Long usec,
            @WebParam(name = "sourceAddress") String sourceAddress,
            @WebParam(name = "destinationAddress") String destinationAddress,
            @WebParam(name = "sourcePort") Integer sourcePort,
            @WebParam(name = "destinationPort") Integer destinationPort,
            @WebParam(name = "alert") String alert,
            @WebParam(name = "packet") String packet) {


        AlertManager alertManager = new AlertManager();
        PersistentAlert palert = new PersistentAlert(date, usec, sourceAddress, destinationAddress, sourcePort, destinationPort, alert, packet);
        alertManager.addAlert(palert);

        System.out.println(palert);
        
        return true;
    }

}
