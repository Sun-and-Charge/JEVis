/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.commons.cli.AbstractCliApp;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Service "JEDemo": keeps the public demo organisation current (see docs/DEMO.md).
 * <ul>
 * <li>{@code -sm service}: one cycle per "Cycle Time" while the service object is enabled;</li>
 * <li>{@code -sm complete}: one cycle for the configured organisations;</li>
 * <li>{@code -sm single -jid <organisation ids>}: one cycle for the given organisations (settings from the service
 * object).</li>
 * </ul>
 * {@code -o dryrun=true} only logs what would be written. {@code -o invoicesFrom=2025-01} also bills the invoice cases of
 * the months from then on (backfill, existing invoices are skipped), {@code -o invoicesOnly=true} skips the replay and
 * the date rolling.
 */
public class Launcher extends AbstractCliApp {

    private static final Logger logger = LogManager.getLogger(Launcher.class);
    private static final String APP_INFO = "JEDemo";
    static final String OPTION_DRY_RUN = "dryrun";
    static final String OPTION_INVOICES_FROM = "invoicesFrom";
    static final String OPTION_INVOICES_ONLY = "invoicesOnly";
    private static final int DEFAULT_CYCLE_TIME = 3600000;

    private final Command commands = new Command();
    private DemoJob job;
    private boolean firstCycle = true;

    private Launcher(String[] args, String appname) {
        super(args, appname);
    }

    public static void main(String[] args) {
        logger.info("-------Start JEDemo-------");
        System.setProperty("user.timezone", "UTC");
        Launcher app = new Launcher(args, APP_INFO);
        app.execute();
    }

    @Override
    protected void addCommands() {
        comm.addObject(commands);
    }

    @Override
    protected void handleAdditionalCommands() {
        APP_SERVICE_CLASS_NAME = DemoConfig.CLASS_NAME;
        cycleTime = DEFAULT_CYCLE_TIME;
        boolean dryRun = Boolean.parseBoolean(settings.options.getOrDefault(OPTION_DRY_RUN, "false").trim());
        if (dryRun) logger.info("Dry run: nothing is written");
        YearMonth invoicesFrom = null;
        String from = settings.options.get(OPTION_INVOICES_FROM);
        if (from != null && !from.trim().isEmpty()) {
            try {
                invoicesFrom = YearMonth.parse(from.trim());
                logger.info("Invoice cases are billed from {} on", invoicesFrom);
            } catch (DateTimeParseException e) {
                logger.error("Invalid option {}={} (expected yyyy-MM), ignored", OPTION_INVOICES_FROM, from);
            }
        }
        boolean invoicesOnly = Boolean.parseBoolean(settings.options.getOrDefault(OPTION_INVOICES_ONLY, "false").trim());
        job = new DemoJob(ds, dryRun, Clock.systemUTC(), invoicesFrom, invoicesOnly);
    }

    private JEVisObject serviceObject() throws JEVisException {
        JEVisClass serviceClass = ds.getJEVisClass(APP_SERVICE_CLASS_NAME);
        if (serviceClass == null) throw new JEVisException("Class " + APP_SERVICE_CLASS_NAME + " missing", 404);
        List<JEVisObject> services = ds.getObjects(serviceClass, false);
        if (services.isEmpty()) throw new JEVisException("No " + APP_SERVICE_CLASS_NAME + " service object", 404);
        if (services.size() > 1) logger.warn("{} service objects found, using {}", services.size(), services.get(0).getID());
        return services.get(0);
    }

    private void cycle(List<Long> organisations) {
        try {
            if (!firstCycle) ds.reloadObjects();
            firstCycle = false;
            job.runCycle(serviceObject(), organisations);
        } catch (Exception e) {
            logger.error("JEDemo cycle failed", e);
        }
    }

    @Override
    protected void runSingle(List<Long> ids) {
        cycle(ids);
    }

    @Override
    protected void runComplete() {
        cycle(null);
    }

    @Override
    protected void runServiceHelp() {
        if (checkConnection()) {
            getCycleTimeFromService(APP_SERVICE_CLASS_NAME);
            if (checkServiceStatus(APP_SERVICE_CLASS_NAME)) {
                setServiceStatus(APP_SERVICE_CLASS_NAME, 2L);
                cycle(null);
                setServiceStatus(APP_SERVICE_CLASS_NAME, 1L);
            } else {
                logger.info("Service is disabled.");
            }
        }
        sleep();
    }
}
