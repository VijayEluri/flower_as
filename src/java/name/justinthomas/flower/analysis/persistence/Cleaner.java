package name.justinthomas.flower.analysis.persistence;

import java.util.ArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import name.justinthomas.flower.analysis.statistics.StatisticsManager;

/**
 *
 * @author justin
 */
@Singleton
@Startup
@DependsOn("ConfigurationManager")
public class Cleaner implements Runnable {

    private static Cleaner instance;
    private static ScheduledThreadPoolExecutor executor;

    @PostConstruct
    protected void setup() {
        System.out.println("Setting Cleaner to run in 3 minutes and every 4 hours.");

        instance = new Cleaner();
        executor = new ScheduledThreadPoolExecutor(1);
        executor.scheduleAtFixedRate(instance, 3, 240, TimeUnit.MINUTES);
    }
    private static Integer DEBUG = 1;

    private void clean() {
        if (DEBUG >= 1) {
            System.out.println("Cleaning databases...");
        }

        System.out.println("Cleaning statistics...");
        StatisticsManager statisticsManager = new StatisticsManager();
        ArrayList<Long> flowIDs = statisticsManager.cleanStatisticalIntervals();

        System.out.println("Cleaning flows...");
        FlowManager flowManager = new FlowManager();
        flowManager.cleanFlows(flowIDs);

        if (DEBUG >= 1) {
            System.out.println("Cleaning completed.");
        }
    }

    @Override
    public void run() {
        clean();
    }
}
